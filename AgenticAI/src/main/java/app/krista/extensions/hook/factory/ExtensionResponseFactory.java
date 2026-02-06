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
import app.krista.extension.executor.impl.ConfirmVariablesAction;
import app.krista.extension.executor.impl.InformAPersonAction;
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
}
