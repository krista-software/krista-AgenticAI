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

package app.krista.extensions.hook.agentic.converter;

import app.krista.extension.executor.ExtensionResponse;
import app.krista.extension.executor.impl.InformStepType;
import app.krista.extensions.hook.agentic.AgenticConstants;
import app.krista.extensions.hook.agentic.dto.AgenticCommand;
import app.krista.extensions.hook.agentic.dto.AgenticErrorCode;
import app.krista.extensions.hook.agentic.dto.AgenticResponse;
import app.krista.extensions.hook.agentic.dto.AgenticToolDefinition;
import app.krista.extensions.hook.agentic.logging.ChatMemoryLogger;
import app.krista.extensions.hook.agentic.service.AgenticFlowService;
import app.krista.extensions.hook.agentic.session.AgenticSession;
import app.krista.extensions.hook.agentic.usagecollector.UsageLogger;
import app.krista.extensions.hook.factory.ExtensionResponseFactory;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.langchain4j.memory.ChatMemory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Converts {@link AgenticResponse} objects to {@link ExtensionResponse} objects,
 * applying parent-child session routing logic and triggering usage/memory lifecycle hooks.
 *
 * <p>Parent-child rules:
 * <ul>
 *   <li>When a parent selects an invoker tool, a child session ID is generated and the
 *       tool execution is delegated to a new child agent.</li>
 *   <li>When a child completes or errors, control is returned to the parent session.</li>
 * </ul>
 */
public class AgenticResponseConverter {

    private static final Logger LOGGER = LoggerFactory.getLogger(AgenticResponseConverter.class);

    private final AgenticFlowService agenticFlowService;
    private final Map<Object, ChatMemory> chatMemoryMap;

    public AgenticResponseConverter(AgenticFlowService agenticFlowService,
                                    Map<Object, ChatMemory> chatMemoryMap) {
        this.agenticFlowService = agenticFlowService;
        this.chatMemoryMap = chatMemoryMap;
    }

    /**
     * Converts an {@link AgenticResponse} to an {@link ExtensionResponse}.
     *
     * @param agenticResponse the response from the agentic flow service
     * @param errorCode       the error code from a caught {@code AgenticFlowException},
     *                        or {@code null} on the happy path (EXECUTE_TOOL / COMPLETE)
     */
    public ExtensionResponse toExtensionResponse(AgenticResponse agenticResponse, AgenticErrorCode errorCode) {

        if (agenticResponse == null || agenticResponse.getResponseType() == null) {

            LOGGER.error("Received null or malformed agentic response");
            return ExtensionResponseFactory.createFallbackErrorResponse();
        }

        String sessionId = agenticResponse.getSessionId();
        boolean isChild = isChildSession(sessionId);
        String parentSessionId = isChild ? extractParentSessionId(sessionId) : null;
        Map<String, Object> outputVars = agenticResponse.getOutputVariables();

        switch (agenticResponse.getResponseType()) {

            case EXECUTE_TOOL:
                return handleExecuteTool(agenticResponse, sessionId, isChild, outputVars);

            case COMPLETE:
                return handleComplete(agenticResponse, sessionId, isChild, parentSessionId, outputVars, errorCode);

            case ERROR:
                return handleError(agenticResponse, sessionId, isChild, parentSessionId, outputVars, errorCode);

            default:
                LOGGER.error("Unknown response type: {}", agenticResponse.getResponseType());
                return ExtensionResponseFactory.createFallbackErrorResponse();
        }
    }

    private ExtensionResponse handleExecuteTool(AgenticResponse agenticResponse,
                                                String sessionId,
                                                boolean isChild,
                                                Map<String, Object> outputVars) {

        AgenticCommand command = agenticResponse.getCommand();

        // Helper tools (ask/inform a person) skip status messages entirely
        if (isHelperTool(sessionId, command.toolName())) {

            LOGGER.debug("ExecuteToolAction (helper) - session={}, tool={}",
                sessionId, command.toolName());
            return ExtensionResponseFactory.createExecuteToolResponse(
                sessionId, command.toolName(), command.arguments(), outputVars,
                null, null);
        }

        String statusMessage = agenticResponse.getStatusMessage();
        if (statusMessage == null || statusMessage.isBlank()) {

            statusMessage = deriveToolStatusMessage(command.toolName());
        }

        if (!isChild && isInvokerTool(command.toolName())) {

            // Parent selected invoker - generate child session ID only.
            // Session will be created when KristaGrand sends it back as a "new" request.
            String childSessionId = generateChildSessionId(sessionId);
            LOGGER.info("Parent delegating to child agent: {}", childSessionId);
            return ExtensionResponseFactory.createExecuteToolResponse(
                childSessionId, command.toolName(), command.arguments(), outputVars,
                statusMessage, InformStepType.STATUS);
        } else {

            // Normal execution (child executes tools in its own session)
            LOGGER.debug("ExecuteToolAction - session={}, tool={}, args={}",
                sessionId, command.toolName(), command.arguments());
            return ExtensionResponseFactory.createExecuteToolResponse(
                sessionId, command.toolName(), command.arguments(), outputVars,
                statusMessage, InformStepType.STATUS);
        }
    }

    private ExtensionResponse handleComplete(AgenticResponse agenticResponse,
                                             String sessionId,
                                             boolean isChild,
                                             String parentSessionId,
                                             Map<String, Object> outputVars,
                                             AgenticErrorCode errorCode) {

        String statusMessage = resolveStatusMessage(agenticResponse, errorCode);

        if (isChild) {

            if ("Task encountered an error".equals(statusMessage)) {

                statusMessage = "Sub-task completed";
            }
            statusMessage = composeChildStatusMessage(statusMessage, false);
            logAndCloseChildMemory(sessionId, "COMPLETED");
            // Note: Session cleanup and usage rollup are handled centrally by AgenticFlowService.processResponse()
            LOGGER.info("Child completed, returning to parent: {}", parentSessionId);
            return ExtensionResponseFactory.createChildCompleteResponse(
                parentSessionId, agenticResponse.getFinalResponse(), outputVars,
                statusMessage, InformStepType.STATUS);
        } else {

            if ("Task encountered an error".equals(statusMessage)) {

                statusMessage = "Task completed successfully";
            }
            logParentUsage(sessionId, false, null);
            LOGGER.info("Parent flow completed - response: {}",
                agenticResponse.getFinalResponse() != null
                    ? agenticResponse.getFinalResponse().substring(
                        0, Math.min(100, agenticResponse.getFinalResponse().length()))
                    : "null");
            return ExtensionResponseFactory.createParentCompleteResponse(
                sessionId, agenticResponse.getFinalResponse(), outputVars,
                statusMessage, InformStepType.STATUS);
        }
    }

    private ExtensionResponse handleError(AgenticResponse agenticResponse,
                                          String sessionId,
                                          boolean isChild,
                                          String parentSessionId,
                                          Map<String, Object> outputVars,
                                          AgenticErrorCode errorCode) {

        String statusMessage = resolveStatusMessage(agenticResponse, errorCode);

        if (isChild) {

            statusMessage = composeChildStatusMessage(statusMessage, true);
            logAndCloseChildMemory(sessionId, "ERROR");
            // Note: Session cleanup and usage rollup are handled centrally by AgenticFlowService.processResponse()
            LOGGER.error("Child failed, returning to parent: {}", parentSessionId);
            return ExtensionResponseFactory.createChildErrorResponse(
                parentSessionId, agenticResponse.getErrorMessage(), outputVars,
                statusMessage, InformStepType.STATUS);
        } else {

            logParentUsage(sessionId, true, agenticResponse.getErrorMessage());
            LOGGER.error("Parent flow error: {}", agenticResponse.getErrorMessage());
            return ExtensionResponseFactory.createParentErrorResponse(
                sessionId, agenticResponse.getErrorMessage(), outputVars,
                statusMessage, InformStepType.STANDARD);
        }
    }

    /**
     * Composes a prefixed status message for child agent responses so that the
     * parent conversation clearly distinguishes sub-task updates from top-level
     * task completions.
     *
     * @param llmStatusMessage the raw status message produced by the LLM (may be null or blank)
     * @param isError          {@code true} when the child ended in error
     * @return a non-null, prefixed status message
     */
    private String composeChildStatusMessage(String llmStatusMessage, boolean isError) {

        String prefix = isError ? "Sub-task issue" : "Sub-task update";
        if (llmStatusMessage == null || llmStatusMessage.isBlank()) {

            return prefix + " \u2014 resuming main task...";
        }
        return prefix + " \u2014 " + llmStatusMessage;
    }

    private void logAndCloseChildMemory(String sessionId, String status) {
        ChatMemory childMemory = chatMemoryMap.get(sessionId);
        if (childMemory != null) {
            ChatMemoryLogger.logChatMemory(sessionId, childMemory, status);
        }
        ChatMemoryLogger.closeSession(sessionId);
    }

    private void logParentUsage(String sessionId, boolean isError, String errorMessage) {
        AgenticSession session = agenticFlowService.getSession(sessionId);
        LOGGER.info("ORCH_USAGE_LOG: Parent session={}{} - attempting to log usage",
            sessionId, isError ? " ERROR" : "");
        if (session != null && session.getUsageCollector() != null) {
            if (isError) {
                session.getUsageCollector().markError(errorMessage);
                LOGGER.info("ORCH_USAGE_LOG: Session marked as ERROR, calling UsageLogger");
            } else {
                session.getUsageCollector().markCompleted();
                LOGGER.info("ORCH_USAGE_LOG: Session marked completed, calling UsageLogger");
            }
            UsageLogger.logSessionUsage(session.getUsageCollector());
            LOGGER.info("ORCH_USAGE_LOG: UsageLogger call completed for session={}", sessionId);
        } else {
            LOGGER.error("ORCH_USAGE_LOG_ERROR: Cannot log usage - session={}, sessionNull={}, usageCollectorNull={}",
                sessionId,
                session == null,
                session != null ? session.getUsageCollector() == null : "N/A");
        }
    }

    /**
     * Resolves a user-facing status message for error responses by checking, in order:
     * <ol>
     *   <li>The LLM-provided {@code statusMessage} (takes priority if non-null/non-blank)</li>
     *   <li>A mapped message from the {@link AgenticErrorCode} parameter</li>
     *   <li>A generic fallback</li>
     * </ol>
     *
     * @param agenticResponse the response from the agentic flow
     * @param errorCode       the error code passed from the catch block, or {@code null} on happy path
     */
    private String resolveStatusMessage(AgenticResponse agenticResponse, AgenticErrorCode errorCode) {

        String llmMessage = agenticResponse.getStatusMessage();
        if (llmMessage != null && !llmMessage.isBlank()) {

            return llmMessage;
        }

        if (errorCode == null) {

            return "Task encountered an error";
        }

        return switch (errorCode) {

            case JSON_PARSE_FAILED -> "Unable to process AI response";
            case TOOL_VALIDATION_FAILED -> "Unable to select a valid tool";
            case ARGUMENT_VALIDATION_FAILED -> "Tool arguments failed validation";
            case OUTPUT_VALIDATION_FAILED -> "Output variables failed validation";
            case MALFORMED_RESPONSE -> "Unable to process AI response";
            case SESSION_NOT_FOUND -> "Session expired or not found";
            case PARENT_SESSION_NOT_FOUND -> "Parent task session not found";
            case PARENT_SESSION_TERMINATED -> "Parent task has ended";
            case PERSISTENCE_FAILED -> "An internal error occurred";
            case GUARDRAIL_BLOCKED -> "Request blocked by security policy";
            case EXECUTION_LIMIT_ITERATION -> "Task exceeded the maximum number of steps";
            case CONTEXT_LENGTH_EXCEEDED -> "The input data is too large for the AI model to process";
            case UNEXPECTED_ERROR -> "Task encountered an error";
        };
    }

    // -------------------------------------------------------------------------
    // Status message helpers
    // -------------------------------------------------------------------------

    /**
     * Derives a user-friendly status message from the JSON-encoded tool name by
     * examining the {@code toolType} and its identifying fields.
     *
     * <p>Fallback messages by tool type:
     * <ul>
     *   <li>{@code conversation} - {@code Calling conversation "<conversationName>"...}</li>
     *   <li>{@code catalog_request} - {@code Executing request "<catalogRequestId>"...}</li>
     *   <li>{@code invoker} - {@code Connecting to invoker <first8chars>***...}</li>
     *   <li>{@code entity} - message varies by {@code operationType} (e.g. Looking up, Adding)</li>
     * </ul>
     *
     * <p>Note: Helper tools ({@code ask_a_person}, {@code inform_a_person}) are short-circuited
     * in {@link #handleExecuteTool} before this method is called.
     *
     * @param toolName the raw tool name JSON string from the agentic command
     * @return a non-null status message suitable for display to end users
     */
    private String deriveToolStatusMessage(String toolName) {

        if (toolName == null || toolName.isBlank()) {

            return "Processing...";
        }

        if (!toolName.startsWith("{")) {

            return "Processing...";
        }

        try {

            JsonObject json = JsonParser.parseString(toolName).getAsJsonObject();
            if (!json.has(AgenticConstants.FIELD_TOOL_TYPE)) {

                return "Processing...";
            }

            String toolType = json.get(AgenticConstants.FIELD_TOOL_TYPE).getAsString();

            return switch (toolType) {

                case AgenticConstants.TOOL_TYPE_CONVERSATION -> {

                    String conversationName = getJsonString(json, AgenticConstants.FIELD_CONVERSATION_NAME);
                    yield conversationName != null
                        ? "Calling conversation \"" + conversationName + "\"..."
                        : "Calling conversation...";
                }

                case AgenticConstants.TOOL_TYPE_CATALOG_REQUEST -> {

                    String catalogRequestId = getJsonString(json, AgenticConstants.FIELD_CATALOG_REQUEST_ID);
                    yield catalogRequestId != null
                        ? "Executing request \"" + catalogRequestId + "\"..."
                        : "Executing request...";
                }

                case AgenticConstants.TOOL_TYPE_INVOKER -> {

                    String invokerId = getJsonString(json, AgenticConstants.FIELD_INVOKER_ID);
                    yield invokerId != null
                        ? "Connecting to invoker " + maskId(invokerId) + "..."
                        : "Connecting to invoker...";
                }

                case AgenticConstants.TOOL_TYPE_ENTITY -> {

                    String entityId = getJsonString(json, AgenticConstants.FIELD_ENTITY_ID);
                    String operationType = getJsonString(json, AgenticConstants.FIELD_OPERATION_TYPE);
                    yield deriveEntityStatusMessage(entityId, operationType);
                }

                default -> "Processing...";
            };

        } catch (Exception e) {

            LOGGER.debug("Failed to parse tool name JSON for status message: {}", toolName, e);
            return "Processing...";
        }
    }

    /**
     * Derives a status message for entity tool operations based on the operation type.
     */
    private String deriveEntityStatusMessage(String entityId, String operationType) {

        String entityLabel = entityId != null ? entityId : "entity";
        if (operationType == null || operationType.isBlank()) {

            return "Looking up " + entityLabel + "...";
        }

        return switch (operationType.toUpperCase()) {

            case "ADD", "CREATE" -> "Adding " + entityLabel + "...";
            case "UPDATE", "EDIT" -> "Updating " + entityLabel + "...";
            case "DELETE", "REMOVE" -> "Deleting " + entityLabel + "...";
            default -> "Looking up " + entityLabel + "...";
        };
    }

    /**
     * Safely extracts a string value from a {@link JsonObject}, returning {@code null}
     * if the field is absent or not a string primitive.
     */
    private String getJsonString(JsonObject json, String fieldName) {

        if (json.has(fieldName) && json.get(fieldName).isJsonPrimitive()) {

            return json.get(fieldName).getAsString();
        }
        return null;
    }

    /**
     * Masks an ID by truncating to the first 8 characters and appending {@code ***}.
     * If the ID is 8 characters or shorter, it is returned as-is with {@code ***} appended.
     */
    private String maskId(String id) {

        if (id == null) {

            return "***";
        }
        if (id.length() <= 8) {

            return id + "***";
        }
        return id.substring(0, 8) + "***";
    }

    // -------------------------------------------------------------------------
    // Session ID helpers
    // -------------------------------------------------------------------------

    /**
     * Returns true if the session ID represents a child agent session.
     */
    public boolean isChildSession(String sessionId) {
        return sessionId != null && sessionId.contains(AgenticConstants.SESSION_CHILD_PREFIX);
    }

    /**
     * Extracts the parent session ID from a child session ID.
     * Format: {@code <parentSessionId>.child_<uuid>}
     */
    public String extractParentSessionId(String childSessionId) {
        int dotIndex = childSessionId.indexOf(AgenticConstants.SESSION_CHILD_PREFIX);
        return dotIndex > 0 ? childSessionId.substring(0, dotIndex) : childSessionId;
    }

    /**
     * Returns true if the tool name JSON indicates an invoker tool type.
     * Invoker tools trigger delegation to a child agent.
     */
    public boolean isInvokerTool(String toolName) {

        if (toolName == null || !toolName.startsWith("{")) {

            return false;
        }
        try {

            JsonObject json = JsonParser.parseString(toolName).getAsJsonObject();
            return json.has(AgenticConstants.FIELD_TOOL_TYPE)
                && AgenticConstants.TOOL_TYPE_INVOKER.equals(
                    json.get(AgenticConstants.FIELD_TOOL_TYPE).getAsString());
        } catch (Exception e) {

            LOGGER.debug("Failed to parse tool name JSON in isInvokerTool: {}", toolName, e);
            return false;
        }
    }

    /**
     * Returns true if the tool identified by {@code toolName} is a helper tool.
     *
     * <p>Helper-tool status is resolved by looking up the tool definition in the
     * active session rather than re-parsing the JSON tool name. This is necessary
     * because both the built-in {@code ask_a_person} helper and a manager-approval
     * business tool share the same {@code toolType} value in their JSON names; only
     * the {@link AgenticToolDefinition#isHelperTool()} flag set at registration time
     * reliably distinguishes them.
     *
     * @param sessionId the session in which the tool is being executed
     * @param toolName  the raw JSON tool name string from the agentic command
     * @return {@code true} only when the session's tool list contains an entry whose
     *         {@code name} equals {@code toolName} and whose {@code isHelperTool} flag
     *         is explicitly {@code Boolean.TRUE}; {@code false} in all other cases
     *         (session not found, tool not found, flag is {@code null} or {@code false})
     */
    public boolean isHelperTool(String sessionId, String toolName) {

        if (toolName == null) {

            return false;
        }

        AgenticSession session = agenticFlowService.getSession(sessionId);
        if (session == null) {

            LOGGER.debug("isHelperTool: session not found for sessionId={}, defaulting to false", sessionId);
            return false;
        }

        List<AgenticToolDefinition> tools = session.getTools();
        if (tools == null || tools.isEmpty()) {

            return false;
        }

        for (AgenticToolDefinition tool : tools) {

            if (toolName.equals(tool.name())) {

                return Boolean.TRUE.equals(tool.isHelperTool());
            }
        }

        LOGGER.debug("isHelperTool: tool not found in session, sessionId={}, toolName={}, defaulting to false",
            sessionId, toolName);
        return false;
    }

    /**
     * Generates a child session ID. The session itself is created later when
     * KristaGrand sends the request back with this ID.
     */
    public String generateChildSessionId(String parentSessionId) {
        String childSessionId = parentSessionId + AgenticConstants.SESSION_CHILD_SEPARATOR + UUID.randomUUID().toString();
        LOGGER.debug("Generated child session ID: {} for parent: {}", childSessionId, parentSessionId);
        return childSessionId;
    }
}
