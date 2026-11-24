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

package agentic;

import app.krista.extensions.hook.agentic.dto.AgenticCommand;
import app.krista.extensions.hook.agentic.dto.AgenticContinueRequest;
import app.krista.extensions.hook.agentic.dto.AgenticResponse;
import app.krista.extensions.hook.agentic.dto.AgenticStartRequest;
import app.krista.extensions.hook.agentic.dto.AgenticToolDefinition;
import app.krista.extensions.hook.agentic.dto.PolicyCheckResult;
import app.krista.extensions.hook.agentic.guardrails.PolicyCheckerFailurePolicy;
import app.krista.extensions.hook.agentic.planner.PolicyChecker;
import app.krista.extensions.hook.agentic.planner.ToolPlanner;
import app.krista.extensions.hook.agentic.service.AgenticFlowService;
import app.krista.extensions.hook.agentic.session.AgenticSessionStore;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.service.AiServices;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

/**
 * Central test factory for {@link AgenticFlowService} instances.
 *
 * <p>Provides three construction modes:
 * <ol>
 *   <li><b>Deterministic</b> — scripted planner + fake checker; no API key required.</li>
 *   <li><b>Scripted planner</b> — scripted planner + real LLM-backed PolicyChecker.</li>
 *   <li><b>Fully live</b> — both ToolPlanner and PolicyChecker backed by a real LLM.</li>
 * </ol>
 *
 * <p>Also ships inner test-double implementations ({@link ScriptedToolPlanner},
 * {@link FakePolicyChecker}) and a {@link #runToCompletion} conversation-loop driver.
 */
public final class AgenticTestHarness {

    private AgenticTestHarness() {

    }

    // =========================================================================
    // Inner test doubles
    // =========================================================================

    /**
     * A {@link ToolPlanner} that returns a pre-configured sequence of
     * {@link AgenticResponse} objects on successive {@code planNextStep()} calls.
     */
    public static final class ScriptedToolPlanner implements ToolPlanner {

        private static final String ASK_A_PERSON_TOOL_NAME =
                "{\"toolType\":\"ask_a_person\",\"humanInteractionId\":\"builtin\"}";

        private final Deque<AgenticResponse> queue = new ArrayDeque<>();
        private final AtomicInteger callCount = new AtomicInteger(0);

        /**
         * Enqueues the next response to be returned by {@code planNextStep()}.
         */
        public ScriptedToolPlanner withResponse(AgenticResponse response) {

            queue.add(response);
            return this;
        }

        public int getCallCount() {

            return callCount.get();
        }

        @Override
        public AgenticResponse planNextStep(
                List<AgenticToolDefinition> businessTools,
                List<AgenticToolDefinition> helperTools,
                String prompt,
                String sessionId,
                Map<String, Object> previousToolResult,
                String inputVariablesFormatted,
                String expectedOutputsFormatted) {

            callCount.incrementAndGet();

            if (!queue.isEmpty()) {

                return queue.poll();
            }

            // Default: escalate to ask_a_person when queue is exhausted
            AgenticResponse fallback = new AgenticResponse();
            fallback.setSessionId(sessionId);
            fallback.setResponseType(AgenticResponse.ResponseType.EXECUTE_TOOL);
            fallback.setConfidence(0.9);
            fallback.setCommand(new AgenticCommand(ASK_A_PERSON_TOOL_NAME, new HashMap<>()));
            return fallback;
        }
    }

    /**
     * A {@link PolicyChecker} that returns a pre-configured sequence of
     * {@link PolicyCheckResult} objects on successive {@code evaluate()} calls.
     */
    public static final class FakePolicyChecker implements PolicyChecker {

        private final List<PolicyCheckResult> results = new ArrayList<>();
        private final AtomicInteger callCount = new AtomicInteger(0);
        private RuntimeException exceptionToThrow;

        public FakePolicyChecker withResult(PolicyCheckResult result) {

            results.add(result);
            return this;
        }

        public FakePolicyChecker withException(RuntimeException exception) {

            this.exceptionToThrow = exception;
            return this;
        }

        public int getCallCount() {

            return callCount.get();
        }

        @Override
        public PolicyCheckResult evaluate(String userGoal, String policyBrief,
                                          String plannedTool, String plannedArguments,
                                          String toolHistory) {

            int idx = callCount.getAndIncrement();

            if (exceptionToThrow != null) {

                throw exceptionToThrow;
            }

            if (idx < results.size()) {

                return results.get(idx);
            }

            return PolicyCheckResult.allowed("Default allowed");
        }
    }

    // =========================================================================
    // Factory methods
    // =========================================================================

    /**
     * Mode 1 — fully deterministic, CI-safe, no API key required.
     */
    public static AgenticFlowService deterministicMode(ScriptedToolPlanner planner,
                                                       FakePolicyChecker checker) {

        return new AgenticFlowService(
                AgenticSessionStore.getInstance(),
                null,
                planner,
                new ConcurrentHashMap<>(),
                checker,
                PolicyCheckerFailurePolicy.FAIL_CLOSED
        );
    }

    /**
     * Mode 2 — scripted planner + real LLM-backed PolicyChecker.
     * Requires {@code OPENAI_API_KEY} to be set.
     */
    public static AgenticFlowService scriptedPlannerMode(ScriptedToolPlanner planner,
                                                         ChatLanguageModel model) {

        PolicyChecker realChecker = AiServices.builder(PolicyChecker.class)
                .chatLanguageModel(model)
                .build();

        return new AgenticFlowService(
                AgenticSessionStore.getInstance(),
                null,
                planner,
                new ConcurrentHashMap<>(),
                realChecker,
                PolicyCheckerFailurePolicy.FAIL_CLOSED
        );
    }

    /**
     * Mode 3 — both ToolPlanner and PolicyChecker backed by a real LLM.
     * Requires {@code OPENAI_API_KEY} to be set.
     */
    public static AgenticFlowService liveMode(ChatLanguageModel model,
                                              Map<Object, ChatMemory> chatMemoryMap) {

        ChatMemoryProvider provider = sessionId ->
                chatMemoryMap.computeIfAbsent(sessionId, id ->
                        MessageWindowChatMemory.builder()
                                .id(id)
                                .maxMessages(50)
                                .build()
                );

        ToolPlanner planner = AiServices.builder(ToolPlanner.class)
                .chatLanguageModel(model)
                .chatMemoryProvider(provider)
                .build();

        PolicyChecker checker = AiServices.builder(PolicyChecker.class)
                .chatLanguageModel(model)
                .build();

        return new AgenticFlowService(
                AgenticSessionStore.getInstance(),
                null,
                planner,
                chatMemoryMap,
                checker,
                PolicyCheckerFailurePolicy.FAIL_CLOSED
        );
    }

    // =========================================================================
    // Conversation runner
    // =========================================================================

    /**
     * Functional interface for tool result providers that receive the full {@link AgenticResponse}.
     *
     * <p>Using a named interface rather than {@code Function<AgenticResponse, Object>} avoids the
     * JVM type-erasure collision with the command-level overload that accepts
     * {@code Function<AgenticCommand, Object>}.
     */
    @FunctionalInterface
    public interface ResponseToolResultProvider {

        /**
         * Maps the current {@link AgenticResponse} to a simulated tool result.
         *
         * @param response the full response for the current tool-execution turn
         * @return the object to pass back as the tool result
         */
        Object apply(AgenticResponse response);
    }

    /**
     * Drives the full start → continue loop until the flow reaches
     * {@code COMPLETE}, {@code ERROR}, or {@code maxTurns} is exhausted.
     *
     * <p>This is the primary overload. The {@code toolResultProvider} receives the full
     * {@link AgenticResponse} for each tool-execution turn, giving callers access to
     * response-level metadata (e.g., {@code statusMessage}) in addition to the command.
     *
     * @param service            the service under test
     * @param request            the initial start request
     * @param toolResultProvider maps each full {@link AgenticResponse} to a simulated tool result object
     * @param maxTurns           upper bound on loop iterations (prevents infinite loops in tests)
     * @return the final {@link AgenticResponse}
     */
    public static AgenticResponse runToCompletion(AgenticFlowService service,
                                                  AgenticStartRequest request,
                                                  ResponseToolResultProvider toolResultProvider,
                                                  int maxTurns) {

        AgenticResponse response = service.startFlow(request);
        int turns = 0;

        while (turns < maxTurns
                && response != null
                && response.getResponseType() == AgenticResponse.ResponseType.EXECUTE_TOOL) {

            Object toolResult = toolResultProvider.apply(response);

            AgenticContinueRequest continueRequest = new AgenticContinueRequest(
                    response.getSessionId(),
                    response.getCommand().toolName(),
                    toolResult
            );

            response = service.continueFlow(continueRequest);
            turns++;
        }

        return response;
    }

    /**
     * Convenience overload that accepts a {@code Function<AgenticCommand, Object>} provider.
     *
     * <p>Delegates to {@link #runToCompletion(AgenticFlowService, AgenticStartRequest, ResponseToolResultProvider, int)}
     * by extracting the {@link AgenticCommand} from each response before invoking the provider.
     * Use the primary overload when you need access to response-level fields such as
     * {@code statusMessage} alongside the command.
     *
     * @param service            the service under test
     * @param request            the initial start request
     * @param toolResultProvider maps each {@link AgenticCommand} to a simulated tool result object
     * @param maxTurns           upper bound on loop iterations (prevents infinite loops in tests)
     * @return the final {@link AgenticResponse}
     */
    public static AgenticResponse runToCompletion(AgenticFlowService service,
                                                  AgenticStartRequest request,
                                                  Function<AgenticCommand, Object> toolResultProvider,
                                                  int maxTurns) {

        return runToCompletion(service, request,
                (AgenticResponse response) -> toolResultProvider.apply(response.getCommand()),
                maxTurns);
    }

    // =========================================================================
    // Helper factories (mirrors PolicyEnforcementTest helpers)
    // =========================================================================

    public static AgenticToolDefinition businessTool(String name, String description) {

        return new AgenticToolDefinition(name, description, "catalog_request", new ArrayList<>(), false);
    }

    public static AgenticToolDefinition askAPersonTool() {

        return new AgenticToolDefinition(
                "{\"toolType\":\"ask_a_person\",\"humanInteractionId\":\"builtin\"}",
                "Ask a human for clarification or approval when confidence is low",
                "ask_a_person",
                new ArrayList<>(),
                true
        );
    }

    public static List<AgenticToolDefinition> toolsWithAskAPerson(AgenticToolDefinition... businessTools) {

        List<AgenticToolDefinition> tools = new ArrayList<>(Arrays.asList(businessTools));
        tools.add(askAPersonTool());
        return tools;
    }

    public static AgenticStartRequest startRequest(List<AgenticToolDefinition> tools,
                                                   String message,
                                                   List<String> policyBrief,
                                                   String stepGoal) {

        AgenticStartRequest request = new AgenticStartRequest(
                tools,
                message,
                new HashMap<>(),
                List.of(),
                List.of(),
                "sess_harness_" + System.nanoTime()
        );
        request.setPolicyBrief(policyBrief);
        request.setStepGoal(stepGoal);
        return request;
    }
}
