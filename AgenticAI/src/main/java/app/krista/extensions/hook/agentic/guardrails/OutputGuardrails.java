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

package app.krista.extensions.hook.agentic.guardrails;

import app.krista.extensions.hook.agentic.AgenticConstants;
import app.krista.extensions.hook.agentic.dto.AgenticCommand;
import app.krista.extensions.hook.agentic.dto.AgenticResponse;
import app.krista.extensions.hook.util.JsonMapper;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Output guardrails to validate LLM responses before processing.
 *
 * These guardrails protect against:
 * - Invalid response types
 * - Missing required fields for each response type
 * - Malformed command structures
 * - Invalid tool names or arguments
 */
public class OutputGuardrails {

    private static final Logger LOGGER = LoggerFactory.getLogger(OutputGuardrails.class);

    // Valid tool types
    private static final Set<String> VALID_TOOL_TYPES = new HashSet<>(Arrays.asList(
            AgenticConstants.TOOL_TYPE_CONVERSATION,
            AgenticConstants.TOOL_TYPE_INVOKER,
            AgenticConstants.TOOL_TYPE_ASK_A_PERSON,
            AgenticConstants.TOOL_TYPE_INFORM_A_PERSON,
            AgenticConstants.TOOL_TYPE_ENTITY,
            AgenticConstants.TOOL_TYPE_CATALOG_REQUEST
    ));

    /**
     * Validate the LLM response structure before token usage collection.
     * This ensures the response is well-formed and contains all required fields.
     */
    public static GuardrailResults validateResponse(AgenticResponse response) {
        if (response == null) {
            return GuardrailResults.blocked("Response is null");
        }

        // ✅ STEP 1: Validate response type is one of the allowed values
        if (response.getResponseType() == null) {
            return GuardrailResults.blocked("Response type is missing");
        }

        AgenticResponse.ResponseType type = response.getResponseType();
        if (type != AgenticResponse.ResponseType.EXECUTE_TOOL &&
            type != AgenticResponse.ResponseType.COMPLETE &&
            type != AgenticResponse.ResponseType.ERROR) {
            return GuardrailResults.blocked("Invalid response type: " + type);
        }

        // ✅ STEP 2: Validate required fields based on response type
        switch (type) {
            case EXECUTE_TOOL:
                return validateExecuteToolResponse(response);

            case COMPLETE:
                return validateCompleteResponse(response);

            case ERROR:
                return validateErrorResponse(response);

            default:
                return GuardrailResults.blocked("Unknown response type: " + type);
        }
    }

    /**
     * Validate EXECUTE_TOOL response structure.
     * Required: command with toolName and arguments
     */
    private static GuardrailResults validateExecuteToolResponse(AgenticResponse response) {
        AgenticCommand command = response.getCommand();

        if (command == null) {
            LOGGER.warn("EXECUTE_TOOL response missing command field");
            return GuardrailResults.blocked("EXECUTE_TOOL response must have a command");
        }

        // Validate toolName is present
        if (command.toolName() == null || command.toolName().trim().isEmpty()) {
            LOGGER.warn("EXECUTE_TOOL command missing toolName");
            return GuardrailResults.blocked("Command must have a toolName");
        }

        // Validate arguments is present (can be empty map, but not null)
        if (command.arguments() == null) {
            LOGGER.warn("EXECUTE_TOOL command missing arguments");
            return GuardrailResults.blocked("Command must have arguments (can be empty map)");
        }

        // Validate toolName is a parseable JSON with correct structure
        GuardrailResults toolNameValidation = validateToolNameJson(command.toolName());
        if (toolNameValidation.isBlocked()) {
            return toolNameValidation;
        }

        LOGGER.debug("EXECUTE_TOOL response validated: toolName={}", command.toolName());
        return GuardrailResults.allowed();
    }

    /**
     * Validate COMPLETE response structure.
     * Required: finalResponse message
     */
    private static GuardrailResults validateCompleteResponse(AgenticResponse response) {
        String finalResponse = response.getFinalResponse();

        if (finalResponse == null || finalResponse.trim().isEmpty()) {
            LOGGER.warn("COMPLETE response missing finalResponse message");
            return GuardrailResults.blocked("COMPLETE response must have a finalResponse message");
        }

        String preview = finalResponse.length() > 50 ? finalResponse.substring(0, 50) + "..." : finalResponse;
        LOGGER.debug("COMPLETE response validated: {}", preview);
        return GuardrailResults.allowed();
    }

    /**
     * Validate ERROR response structure.
     * Required: errorMessage
     */
    private static GuardrailResults validateErrorResponse(AgenticResponse response) {
        String errorMessage = response.getErrorMessage();

        if (errorMessage == null || errorMessage.trim().isEmpty()) {
            LOGGER.warn("ERROR response missing errorMessage");
            return GuardrailResults.blocked("ERROR response must have an errorMessage");
        }

        LOGGER.debug("ERROR response validated: {}", errorMessage);
        return GuardrailResults.allowed();
    }

    /**
     * Validate that toolName is a parseable JSON string with correct structure.
     * The toolName must be a JSON object with a "toolType" field and type-specific fields.
     */
    private static GuardrailResults validateToolNameJson(String toolName) {

        try {

            JsonNode toolNameJson = JsonMapper.getInstance().readTree(toolName);

            // Must be a JSON object, not array or primitive
            if (!toolNameJson.isObject()) {
                LOGGER.warn("toolName is not a JSON object: {}", toolName);
                return GuardrailResults.blocked("toolName must be a JSON object");
            }

            // Must have toolType field
            if (!toolNameJson.has(AgenticConstants.FIELD_TOOL_TYPE)) {
                LOGGER.warn("toolName missing 'toolType' field: {}", toolName);
                return GuardrailResults.blocked("toolName JSON must have 'toolType' field");
            }

            String toolType = toolNameJson.get(AgenticConstants.FIELD_TOOL_TYPE).asText();

            // Validate toolType is one of the allowed values
            if (!VALID_TOOL_TYPES.contains(toolType)) {
                LOGGER.warn("Invalid toolType '{}'. Valid types: {}", toolType, VALID_TOOL_TYPES);
                return GuardrailResults.blocked(
                    "Invalid toolType '" + toolType + "'. Must be one of: " + VALID_TOOL_TYPES
                );
            }

            // Validate type-specific required fields
            GuardrailResults typeValidation = validateToolTypeSpecificFields(toolNameJson, toolType);
            if (typeValidation.isBlocked()) {
                return typeValidation;
            }

            LOGGER.debug("toolName JSON validated: toolType={}", toolType);
            return GuardrailResults.allowed();

        } catch (Exception e) {
            LOGGER.warn("Failed to parse toolName as JSON: {} - toolName value: {}", e.getMessage(), toolName);
            return GuardrailResults.blocked("toolName must be valid JSON: " + e.getMessage());
        }
    }

    /**
     * Validate type-specific required fields based on toolType.
     */
    private static GuardrailResults validateToolTypeSpecificFields(JsonNode toolNameJson, String toolType) {
        switch (toolType) {
            case AgenticConstants.TOOL_TYPE_CONVERSATION:
                // Conversation type must have channelName and conversationName
                if (!toolNameJson.has(AgenticConstants.FIELD_CHANNEL_NAME)) {
                    LOGGER.warn("conversation toolType missing 'channelName' field");
                    return GuardrailResults.blocked("conversation toolType must have 'channelName' field");
                }
                if (!toolNameJson.has(AgenticConstants.FIELD_CONVERSATION_NAME)) {
                    LOGGER.warn("conversation toolType missing 'conversationName' field");
                    return GuardrailResults.blocked("conversation toolType must have 'conversationName' field");
                }
                LOGGER.debug("conversation fields validated: channelName={}, conversationName={}",
                    toolNameJson.get(AgenticConstants.FIELD_CHANNEL_NAME).asText(),
                    toolNameJson.get(AgenticConstants.FIELD_CONVERSATION_NAME).asText());
                break;

            case AgenticConstants.TOOL_TYPE_INVOKER:
                // For now, no additional validation for invoker
                // Can add invokerId validation here if needed
                break;

            case AgenticConstants.TOOL_TYPE_ASK_A_PERSON:
            case AgenticConstants.TOOL_TYPE_INFORM_A_PERSON:
            case AgenticConstants.TOOL_TYPE_ENTITY:
            case AgenticConstants.TOOL_TYPE_CATALOG_REQUEST:
                // For now, no additional validation for these types
                // Can add type-specific validation here if needed
                break;

            default:
                // Should never reach here due to earlier validation
                return GuardrailResults.blocked("Unknown toolType: " + toolType);
        }

        return GuardrailResults.allowed();
    }

    /**
     * Sanitize response by ensuring all required fields are properly formatted.
     * Since AgenticCommand is now an immutable record, this creates a new sanitized command if needed.
     * This can be used to clean up responses before processing.
     */
    public static AgenticResponse sanitizeResponse(AgenticResponse response) {

        if (response == null) {

            return null;
        }

        // Trim string fields
        if (response.getFinalResponse() != null) {

            response.setFinalResponse(response.getFinalResponse().trim());
        }

        if (response.getErrorMessage() != null) {

            response.setErrorMessage(response.getErrorMessage().trim());
        }

        // Sanitize command if present (create new record with trimmed toolName)
        if (response.getCommand() != null) {

            AgenticCommand command = response.getCommand();
            if (command.toolName() != null) {

                String trimmedToolName = command.toolName().trim();
                // Create new AgenticCommand record with trimmed toolName
                AgenticCommand sanitizedCommand = new AgenticCommand(trimmedToolName, command.arguments());
                response.setCommand(sanitizedCommand);
            }
        }

        return response;
    }
}