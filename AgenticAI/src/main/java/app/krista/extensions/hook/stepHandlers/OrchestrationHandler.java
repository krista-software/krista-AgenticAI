/*
 * Agenticai Extension for Krista
 * Copyright (C) 2025 Krista Software
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package app.krista.extensions.hook.stepHandlers;

import app.krista.extension.executor.ExtensionResponse;
import app.krista.extension.executor.Invoker;
import app.krista.extension.executor.impl.AgentAction;
import app.krista.extensions.hook.agentic.AgenticConstants;
import app.krista.extensions.hook.agentic.converter.AgenticRequestConverter;
import app.krista.extensions.hook.agentic.converter.AgenticResponseConverter;
import app.krista.extensions.hook.agentic.dto.*;
import app.krista.extensions.hook.agentic.factory.PolicyCheckerFactory;
import app.krista.extensions.hook.agentic.factory.ToolPlannerFactory;
import app.krista.extensions.hook.agentic.guardrails.AgenticFlowException;
import app.krista.extensions.hook.agentic.guardrails.ExecutionLimitExceededException;
import app.krista.extensions.hook.agentic.guardrails.PolicyCheckerFailurePolicy;
import app.krista.extensions.hook.agentic.planner.PolicyChecker;
import app.krista.extensions.hook.agentic.planner.ToolPlanner;
import app.krista.extensions.hook.agentic.service.AgenticFlowService;
import app.krista.extensions.hook.agentic.session.ActivityEvent;
import app.krista.extensions.hook.agentic.session.AgenticSession;
import app.krista.extensions.hook.agentic.session.AgenticSessionStore;
import app.krista.extensions.hook.agentic.session.TerminationReason;
import app.krista.extensions.hook.agentic.session.CacheMetricsLogger;
import app.krista.extensions.hook.agentic.usagecollector.UsageFileWriter;
import app.krista.extensions.hook.agentic.usagecollector.UsageLogger;
import app.krista.extensions.hook.common.FreeFormHelper;
import app.krista.extensions.hook.common.OrchestrationContext;
import app.krista.extensions.hook.common.models.StepMetadata;
import app.krista.extensions.util.KeyValueStore;
import app.krista.model.base.FreeForm;
import dev.langchain4j.memory.ChatMemory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

public class OrchestrationHandler implements StepExecutionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(OrchestrationHandler.class);

    private final AgenticFlowService agenticFlowService;
    private final AgenticResponseConverter responseConverter;

    /**
     * Creates a new OrchestrationHandler with in-memory session storage.
     * KeyValueStore persistence is disabled (KR-20581) — sessions use Caffeine cache only.
     *
     * @param invoker       the invoker for LLM calls
     * @param keyValueStore the KeyValueStore instance (currently unused — persistence disabled)
     */
    public OrchestrationHandler(Invoker invoker, KeyValueStore keyValueStore) {

        // Initialize agentic flow service with singleton session store
        AgenticSessionStore sessionStore = AgenticSessionStore.getInstance();

        // Get singleton ToolPlanner instance from factory
        // The factory creates and manages the chatMemoryMap internally
        ToolPlanner toolPlanner = ToolPlannerFactory.getInstance(invoker);

        // Get the chatMemoryMap from the factory (same instance used by ToolPlanner)
        // This ensures all OrchestrationHandler instances share the same chatMemoryMap
        Map<Object, ChatMemory> chatMemoryMap = ToolPlannerFactory.getChatMemoryMap();

        // Get singleton PolicyChecker instance from factory
        PolicyChecker policyChecker = null;
        try {

            policyChecker = PolicyCheckerFactory.getInstance(invoker);
        } catch (Exception e) {

            LOGGER.warn("PolicyChecker creation failed — policy enforcement disabled: {}", e.getMessage());
        }

        // Keep release's in-memory Caffeine session model, but retain fail-closed policy enforcement.
        this.agenticFlowService = new AgenticFlowService(sessionStore, null, toolPlanner, chatMemoryMap,
                policyChecker, PolicyCheckerFailurePolicy.FAIL_CLOSED);
        LOGGER.info("Session persistence DISABLED — in-memory Caffeine cache only. Sessions will not survive extension restarts. (KR-20581)");

        this.responseConverter = new AgenticResponseConverter(agenticFlowService, chatMemoryMap);

        // Start the session cleanup scheduler (singleton, only starts once)
        CacheMetricsLogger.getInstance().start();
    }

    @Override
    public ExtensionResponse processRequest(AgentAction agentAction, FreeForm currentVariables, String formMessage,
                                            String userMessage, StepMetadata metaData, FreeForm currentVariablesDefault,
                                            String language, OrchestrationContext orchestrationContext) throws Exception {
        if (agentAction != AgentAction.ORCHESTRATE) {
            throw new UnsupportedOperationException("OrchestrationHandler only supports ORCHESTRATE action");
        }

        LOGGER.debug("tlogx: Orchestration entry - action={}, sessionId={}, isNew={}, isContinuation={}, prevTool={}",
            agentAction,
            orchestrationContext.getSessionId(),
            orchestrationContext.isNewRequest(),
            orchestrationContext.isContinuation(),
            orchestrationContext.getPreviousToolName());

        if (!orchestrationContext.isOrchestration()) {
            throw new IllegalStateException("OrchestrationHandler requires orchestration context with tool definitions");
        }

        AgenticResponse agenticResponse;
        AgenticErrorCode errorCode = null;
        String sessionId = orchestrationContext.getSessionId();

        try {

            // Check for first-time child session BEFORE branching.
            // This handles cases where KristaGrand sends isNewRequest=false but it's actually a new child.
            if (responseConverter.isChildSession(sessionId) && !agenticFlowService.hasSession(sessionId)) {

                LOGGER.info("First-time child agent start detected: {}", sessionId);
                agenticResponse = handleChildStart(orchestrationContext, orchestrationContext.getPrompt(), currentVariables);
            } else if (orchestrationContext.isNewRequest()) {

                agenticResponse = handleNewRequest(orchestrationContext, orchestrationContext.getPrompt(), currentVariables, metaData);
            } else {

                agenticResponse = handleContinuation(orchestrationContext);
            }
        } catch (AgenticFlowException e) {

            LOGGER.error("Agentic flow error - session={}, errorCode={}, message={}",
                sessionId, e.getErrorCode(), e.getMessage());

            errorCode = e.getErrorCode();
            agenticResponse = new AgenticResponse(sessionId, AgenticResponse.ResponseType.ERROR);
            agenticResponse.setErrorMessage(e.getMessage());
            agenticResponse.setConfidence(0.0);

            // Attach structured session_summary before cleanup (KR-20450). The specific activity
            // event (execution-limit, guardrail) is already recorded at the throw site, so no event
            // is passed here — avoids a duplicate activity-feed entry.
            agenticFlowService.attachFailureSummary(
                sessionId, agenticResponse, terminationReasonFor(e), null);

            cleanupSessionAfterError(sessionId, e.getMessage(), e.getErrorCode().name());
        } catch (Exception e) {

            LOGGER.error("Unexpected error during orchestration - session={}", sessionId, e);

            errorCode = AgenticErrorCode.UNEXPECTED_ERROR;
            agenticResponse = new AgenticResponse(sessionId, AgenticResponse.ResponseType.ERROR);
            agenticResponse.setErrorMessage("An unexpected error occurred. Please try starting a new conversation.");
            agenticResponse.setConfidence(0.0);

            // Attach structured session_summary before cleanup (KR-20450). UNEXPECTED_ERROR has no
            // throw-site activity entry, so record it here.
            agenticFlowService.attachFailureSummary(
                sessionId,
                agenticResponse,
                TerminationReason.ERROR,
                new ActivityEvent.UnexpectedException(e.getClass().getSimpleName(), e.getMessage()));

            cleanupSessionAfterError(sessionId, e.getMessage(), AgenticErrorCode.UNEXPECTED_ERROR.name());
        }

        return responseConverter.toExtensionResponse(agenticResponse, errorCode);
    }

    /**
     * Handles a new orchestration request (no existing session).
     * Note: First-time child agent starts are handled in processRequest() before this method is called.
     */
    private AgenticResponse handleNewRequest(OrchestrationContext context,
                                             String userMessage, FreeForm currentVariables,
                                             StepMetadata metaData) throws Exception {
        LOGGER.info("Starting new orchestration flow");

        List<AgenticToolDefinition> tools = AgenticRequestConverter.convertToolDefinitions(context.getToolDefinitions());

        Map<String, Object> threadVariables = currentVariables != null
            ? FreeFormHelper.convertFreeFormToFieldValueMap(currentVariables)
            : Map.of();

        List<InputVariable> inputVariables = AgenticRequestConverter.parseInputVariables(context.getInputVariables());
        List<ExpectedOutput> expectedOutputs = AgenticRequestConverter.parseExpectedOutputs(context.getExpectedOutputs());

        AgenticStartRequest startRequest = new AgenticStartRequest(
            tools,
            userMessage,
            threadVariables,
            inputVariables,
            expectedOutputs,
            metaData
        );
        startRequest.setPolicyBrief(context.getPolicyBrief());
        startRequest.setStepGoal(context.getPrompt());

        return agenticFlowService.startFlow(startRequest);
    }

    /**
     * Handles first-time start of a child agent.
     */
    private AgenticResponse handleChildStart(OrchestrationContext context,
                                             String userMessage, FreeForm currentVariables) throws Exception {
        String childSessionId = context.getSessionId();
        String parentSessionId = responseConverter.extractParentSessionId(childSessionId);

        LOGGER.info("Starting child flow for parent: {}", parentSessionId);

        List<AgenticToolDefinition> tools = AgenticRequestConverter.convertToolDefinitions(context.getToolDefinitions());

        Map<String, Object> threadVariables = currentVariables != null
            ? FreeFormHelper.convertFreeFormToFieldValueMap(currentVariables)
            : Map.of();

        List<InputVariable> inputVariables = AgenticRequestConverter.parseInputVariables(context.getInputVariables());
        List<ExpectedOutput> expectedOutputs = AgenticRequestConverter.parseExpectedOutputs(context.getExpectedOutputs());

        AgenticStartRequest startRequest = new AgenticStartRequest(
            tools,
            userMessage,
            threadVariables,
            inputVariables,
            expectedOutputs,
            childSessionId
        );
        startRequest.setPolicyBrief(context.getPolicyBrief());
        startRequest.setStepGoal(context.getPrompt());

        return agenticFlowService.startChildFlow(startRequest, parentSessionId);
    }

    /**
     * Performs session cleanup after an error: logs usage, removes the session
     * from the store, and removes chat memory. Extracted to avoid duplication
     * across catch blocks.
     */
    /**
     * Maps an {@link AgenticFlowException} to the {@link TerminationReason} stamped into the
     * session_summary. Execution-limit breaches preserve their iteration/cost/time distinction;
     * other typed errors map by error code.
     */
    private TerminationReason terminationReasonFor(AgenticFlowException e) {

        if (e instanceof ExecutionLimitExceededException limit) {
            return TerminationReason.fromLimitType(limit.getDetails().limitType());
        }
        return switch (e.getErrorCode()) {
            case GUARDRAIL_BLOCKED -> TerminationReason.GUARDRAIL_VIOLATION;
            case PERSISTENCE_FAILED -> TerminationReason.PERSISTENCE_ERROR;
            default -> TerminationReason.ERROR;
        };
    }

    private void cleanupSessionAfterError(String sessionId, String errorMessage, String errorCode) {

        AgenticSession session = agenticFlowService.getSession(sessionId);
        if (session != null) {

            if (session.getUsageCollector() != null) {

                session.getUsageCollector().markError(errorMessage, errorCode);
                UsageLogger.logSessionUsage(session.getUsageCollector());

                try {

                    UsageFileWriter.writeUsageFile(session.getUsageCollector());
                } catch (Exception e) {

                    LOGGER.error("Failed to write usage file during error cleanup for session {}: {}", sessionId, e.getMessage());
                }
            }

            agenticFlowService.deleteSession(sessionId);
            LOGGER.info("Session cleaned up after error: {}", sessionId);
        }
    }

    /**
     * Handles continuation of an existing orchestration flow.
     */
    private AgenticResponse handleContinuation(OrchestrationContext context) {

        LOGGER.debug("tlogx: Continuation - sessionId={}, prevTool={}, resultType={}, result={}",
            context.getSessionId(),
            context.getPreviousToolName(),
            context.getPreviousToolResult() != null ? context.getPreviousToolResult().getClass().getName() : "null",
            context.getPreviousToolResult());

        String toolName = context.getPreviousToolName();
        Object toolResult = context.getPreviousToolResult();

        if (toolName == null) {
            LOGGER.warn("Previous tool name is null for session {}. Using unresolved fallback.", context.getSessionId());
            toolName = AgenticConstants.UNRESOLVED_TOOL_NAME;
        }

        AgenticContinueRequest continueRequest = new AgenticContinueRequest(
            context.getSessionId(),
            toolName,
            toolResult
        );

        return agenticFlowService.continueFlow(continueRequest);
    }
}
