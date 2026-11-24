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

package app.krista.extensions.hook.factory;

import app.krista.extension.executor.ExtensionResponse;
import app.krista.extension.executor.RemediationAction;
import app.krista.extension.executor.RemediationActions;
import app.krista.extension.executor.impl.AskAPersonAction;
import app.krista.extension.executor.impl.ChatFormMessageAction;
import app.krista.extension.executor.impl.ConfirmVariablesAction;
import app.krista.extension.executor.impl.ExecuteToolAction;
import app.krista.extension.executor.impl.FlowCompletionAction;
import app.krista.extension.executor.impl.InformAPersonAction;
import app.krista.extension.executor.impl.InformStepType;
import app.krista.extension.executor.impl.MapVariablesAction;
import app.krista.model.base.FreeForm;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Factory class for creating ExtensionResponse objects with different types of actions.
 */
public class ExtensionResponseFactory {

    /**
     * Creates ExtensionResponse for entity extraction with MapVariablesAction and AskAPersonAction.
     */
    public static ExtensionResponse createEntityExtractionResponse(FreeForm updatedVariables, String responseText) {
        List<RemediationAction> actions = new ArrayList<>();
        actions.add(MapVariablesAction.create());
        actions.add(AskAPersonAction.create(responseText, null, null));
        
        return createSuccessResponse(updatedVariables, actions);
    }

    /**
     * Creates ExtensionResponse for confirmation with MapVariablesAction, ConfirmVariablesAction, and AskAPersonAction.
     */
    public static ExtensionResponse createConfirmationResponse(FreeForm updatedVariables) {
        List<RemediationAction> actions = new ArrayList<>();
        actions.add(MapVariablesAction.create());
        actions.add(ConfirmVariablesAction.create());

        return createSuccessResponse(updatedVariables, actions);
    }

    /**
     * Creates ExtensionResponse for asking user questions with AskAPersonAction.
     */
    public static ExtensionResponse createAskUserResponse(FreeForm variables, String responseText) {
        List<RemediationAction> actions = new ArrayList<>();
        actions.add(AskAPersonAction.create(responseText, null, null));
        
        return createSuccessResponse(variables, actions);
    }

    /**
     * Creates ExtensionResponse for informing user with InformAPersonAction.
     */
    public static ExtensionResponse createInformUserResponse(String responseText) {
        List<RemediationAction> actions = new ArrayList<>();
        actions.add(InformAPersonAction.create(responseText, null, null));
        
        return createSuccessResponseWithEmptyVariables(actions);
    }

    /**
     * Creates ExtensionResponse for informing user with current variables.
     */
    public static ExtensionResponse createInformUserResponseWithVariables(FreeForm variables, String responseText) {
        List<RemediationAction> actions = new ArrayList<>();
        actions.add(InformAPersonAction.create(responseText, null, null));
        
        return createSuccessResponse(variables, actions);
    }

    /**
     * Creates ExtensionResponse for error handling with AskAPersonAction.
     */
    public static ExtensionResponse createErrorResponse(String errorMessage, FreeForm currentVariables) {
        List<RemediationAction> actions = new ArrayList<>();
        actions.add(AskAPersonAction.create("Error processing request: " + errorMessage, null, null));
        
        return createSuccessResponse(currentVariables, actions);
    }

    /**
     * Creates ExtensionResponse for field not supported cases with ChatFormMessageAction.
     * This action displays the message in both chat and form modes.
     * Always returns empty FreeForm to avoid serialization issues with unsupported fields.
     */
    public static ExtensionResponse createChatFormMessageResponse(String message) {
        List<RemediationAction> actions = new ArrayList<>();
        actions.add(ChatFormMessageAction.create(message));

        return createSuccessResponse(new FreeForm(), actions);
    }

    /**
     * Creates a successful ExtensionResponse with variables and actions.
     */
    private static ExtensionResponse createSuccessResponse(FreeForm variables, List<RemediationAction> actions) {
        RemediationActions remediationActions = new RemediationActions(actions, null);
        return new ExtensionResponse(
            ExtensionResponse.Result.SUCCESS, 
            Map.of("CurrentVariables", variables), 
            null, 
            remediationActions, 
            null
        );
    }

    /**
     * Creates a successful ExtensionResponse with empty variables and actions.
     */
    private static ExtensionResponse createSuccessResponseWithEmptyVariables(List<RemediationAction> actions) {
        RemediationActions remediationActions = new RemediationActions(actions, null);
        return new ExtensionResponse(
            ExtensionResponse.Result.SUCCESS,
            Map.of(),
            null,
            remediationActions,
            null
        );
    }

    // -------------------------------------------------------------------------
    // Orchestration / agentic flow responses
    // -------------------------------------------------------------------------

    /**
     * Creates a fallback ExtensionResponse used when the agentic response is null or malformed.
     */
    public static ExtensionResponse createFallbackErrorResponse() {
        List<RemediationAction> actions = new ArrayList<>();
        actions.add(InformAPersonAction.create(
            "An internal error occurred. Please try again.",
            null,
            null
        ));

        return new ExtensionResponse(
            ExtensionResponse.Result.SUCCESS,
            null,
            null,
            new RemediationActions(actions, null),
            null
        );
    }

    /**
     * Creates an ExtensionResponse that instructs the platform to execute a tool.
     * Used for both normal tool execution (child session) and parent-to-child delegation.
     *
     * @param sessionId    the session ID to associate with this tool execution
     * @param toolName     the name of the tool to execute
     * @param arguments    the tool arguments
     * @param outputVars   the current output variables carried forward
     */
    public static ExtensionResponse createExecuteToolResponse(String sessionId,
                                                              String toolName,
                                                              Map<String, Object> arguments,
                                                              Map<String, Object> outputVars) {

        return createExecuteToolResponse(sessionId, toolName, arguments, outputVars, null, null);
    }

    /**
     * Creates an ExtensionResponse that instructs the platform to execute a tool,
     * with an optional user-facing status message.
     *
     * @param sessionId      the session ID to associate with this tool execution
     * @param toolName       the name of the tool to execute
     * @param arguments      the tool arguments
     * @param outputVars     the current output variables carried forward
     * @param statusMessage  user-facing status text (nullable)
     * @param informStepType the inform step type controlling message persistence (nullable)
     */
    public static ExtensionResponse createExecuteToolResponse(String sessionId,
                                                              String toolName,
                                                              Map<String, Object> arguments,
                                                              Map<String, Object> outputVars,
                                                              String statusMessage,
                                                              InformStepType informStepType) {

        List<RemediationAction> actions = new ArrayList<>();
        actions.add(ExecuteToolAction.create(sessionId, toolName, arguments, statusMessage, informStepType));

        return new ExtensionResponse(
            ExtensionResponse.Result.SUCCESS,
            outputVars,
            null,
            new RemediationActions(actions, null),
            null
        );
    }

    /**
     * Creates an ExtensionResponse for a successfully completed parent agent flow.
     *
     * @param sessionId      the parent session ID
     * @param finalResponse  the final text response from the agent
     * @param outputVars     the output variables produced by the flow
     */
    public static ExtensionResponse createParentCompleteResponse(String sessionId,
                                                                 String finalResponse,
                                                                 Map<String, Object> outputVars) {

        return createParentCompleteResponse(sessionId, finalResponse, outputVars, null, null);
    }

    /**
     * Creates an ExtensionResponse for a successfully completed parent agent flow,
     * with an optional user-facing status message.
     *
     * @param sessionId      the parent session ID
     * @param finalResponse  the final text response from the agent
     * @param outputVars     the output variables produced by the flow
     * @param statusMessage  user-facing status text (nullable)
     * @param informStepType the inform step type controlling message persistence (nullable)
     */
    public static ExtensionResponse createParentCompleteResponse(String sessionId,
                                                                 String finalResponse,
                                                                 Map<String, Object> outputVars,
                                                                 String statusMessage,
                                                                 InformStepType informStepType) {

        List<RemediationAction> actions = new ArrayList<>();
        actions.add(FlowCompletionAction.forParentComplete(
                sessionId, finalResponse, outputVars, statusMessage, informStepType));

        return new ExtensionResponse(
            ExtensionResponse.Result.SUCCESS,
            outputVars,
            null,
            new RemediationActions(actions, null),
            null
        );
    }

    /**
     * Creates an ExtensionResponse for a successfully completed child agent flow,
     * returning control to the parent session.
     *
     * @param parentSessionId the parent session to return to
     * @param finalResponse   the final text response from the child agent
     * @param outputVars      the output variables produced by the child flow
     */
    public static ExtensionResponse createChildCompleteResponse(String parentSessionId,
                                                                String finalResponse,
                                                                Map<String, Object> outputVars) {

        return createChildCompleteResponse(parentSessionId, finalResponse, outputVars, null, null);
    }

    /**
     * Creates an ExtensionResponse for a successfully completed child agent flow,
     * with an optional user-facing status message.
     *
     * @param parentSessionId the parent session to return to
     * @param finalResponse   the final text response from the child agent
     * @param outputVars      the output variables produced by the child flow
     * @param statusMessage   user-facing status text (nullable)
     * @param informStepType  the inform step type controlling message persistence (nullable)
     */
    public static ExtensionResponse createChildCompleteResponse(String parentSessionId,
                                                                String finalResponse,
                                                                Map<String, Object> outputVars,
                                                                String statusMessage,
                                                                InformStepType informStepType) {

        List<RemediationAction> actions = new ArrayList<>();
        actions.add(FlowCompletionAction.forChildComplete(
                parentSessionId, finalResponse, outputVars, statusMessage, informStepType));

        return new ExtensionResponse(
            ExtensionResponse.Result.SUCCESS,
            outputVars,
            null,
            new RemediationActions(actions, null),
            null
        );
    }

    /**
     * Creates an ExtensionResponse for a failed parent agent flow.
     *
     * @param sessionId    the parent session ID
     * @param errorMessage the error message
     * @param outputVars   the output variables at the time of failure
     */
    public static ExtensionResponse createParentErrorResponse(String sessionId,
                                                              String errorMessage,
                                                              Map<String, Object> outputVars) {

        return createParentErrorResponse(sessionId, errorMessage, outputVars, null, null);
    }

    /**
     * Creates an ExtensionResponse for a failed parent agent flow,
     * with an optional user-facing status message.
     *
     * @param sessionId      the parent session ID
     * @param errorMessage   the error message
     * @param outputVars     the output variables at the time of failure
     * @param statusMessage  user-facing status text (nullable)
     * @param informStepType the inform step type controlling message persistence (nullable)
     */
    public static ExtensionResponse createParentErrorResponse(String sessionId,
                                                              String errorMessage,
                                                              Map<String, Object> outputVars,
                                                              String statusMessage,
                                                              InformStepType informStepType) {

        List<RemediationAction> actions = new ArrayList<>();
        actions.add(FlowCompletionAction.forParentError(
                sessionId, errorMessage, outputVars, statusMessage, informStepType));

        return new ExtensionResponse(
            ExtensionResponse.Result.SUCCESS,
            outputVars,
            null,
            new RemediationActions(actions, null),
            null
        );
    }

    /**
     * Creates an ExtensionResponse for a failed child agent flow,
     * returning the error to the parent session.
     *
     * @param parentSessionId the parent session to return to
     * @param errorMessage    the error message from the child agent
     * @param outputVars      the output variables at the time of failure
     */
    public static ExtensionResponse createChildErrorResponse(String parentSessionId,
                                                             String errorMessage,
                                                             Map<String, Object> outputVars) {

        return createChildErrorResponse(parentSessionId, errorMessage, outputVars, null, null);
    }

    /**
     * Creates an ExtensionResponse for a failed child agent flow,
     * with an optional user-facing status message.
     *
     * @param parentSessionId the parent session to return to
     * @param errorMessage    the error message from the child agent
     * @param outputVars      the output variables at the time of failure
     * @param statusMessage   user-facing status text (nullable)
     * @param informStepType  the inform step type controlling message persistence (nullable)
     */
    public static ExtensionResponse createChildErrorResponse(String parentSessionId,
                                                             String errorMessage,
                                                             Map<String, Object> outputVars,
                                                             String statusMessage,
                                                             InformStepType informStepType) {

        List<RemediationAction> actions = new ArrayList<>();
        actions.add(FlowCompletionAction.forChildError(
                parentSessionId, errorMessage, outputVars, statusMessage, informStepType));

        return new ExtensionResponse(
            ExtensionResponse.Result.SUCCESS,
            outputVars,
            null,
            new RemediationActions(actions, null),
            null
        );
    }
}
