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

package app.krista.extensions.hook.helper;

import app.krista.extension.executor.ExtensionResponse;
import app.krista.extensions.hook.util.GsonProvider;
import app.krista.model.base.FreeForm;
import com.google.gson.Gson;

/**
 * Utility class for logging requests and responses
 */
public class RequestLogger {

    private final Gson gson;

    public RequestLogger() {

        this.gson = GsonProvider.getPrettyPrinter();
    }

    /**
     * Logs all request parameters in a structured format
     */
    public void logRequestParameters(FreeForm currentVariables, FreeForm currentVariablesDefault, String action, String formMessage, String userMessage) {

        try {

            String log = "Request parameters:\n" +
                        "Action: " + action + "\n" +
                        "Form Message: " + formMessage + "\n" +
                        "User Message: " + userMessage + "\n" +
                        "CurrentVariables: " +
                        (currentVariables != null ?
                                gson.toJson(currentVariables) : "null") +
                        "\n" +
                        "CurrentVariablesDefault: " +
                        (currentVariablesDefault != null ?
                                gson.toJson(currentVariablesDefault) : "null") +
                        "\n";

            System.out.println(log);
        } catch (Exception e) {

            System.out.println("Failed to log request parameters: " + e.getMessage());
        }
    }

    /**
     * Logs the response in a structured format
     */
    public void logResponse(ExtensionResponse response) {
        try {
            if (response == null) {
                System.out.println("Response: null");
                return;
            }

            // Try to serialize the full response first
            try {

                String fullResponse = gson.toJson(response);
                System.out.println("Response: " + fullResponse);
            } catch (Exception serializationException) {
                // If full serialization fails, log a simplified version
                System.out.println("Response (simplified due to serialization issues):");
                logSimplifiedResponse(response, serializationException);
            }
        } catch (Exception e) {
            System.out.println("Failed to log response: " + e.getMessage());
        }
    }

    /**
     * Logs a simplified version of the response when full serialization fails
     */
    private void logSimplifiedResponse(ExtensionResponse response, Exception originalException) {
        try {
            System.out.println("  Result: " + (response.getResult() != null ? response.getResult().toString() : "null"));

            // Log response value safely
            if (response.getResponseValue() != null) {
                System.out.println("  ResponseValue: " + response.getResponseValue().size() + " entries");
                for (String key : response.getResponseValue().keySet()) {
                    System.out.println("    " + key + ": [" +
                        (response.getResponseValue().get(key) != null ?
                            response.getResponseValue().get(key).getClass().getSimpleName() : "null") + "]");
                }
            } else {
                System.out.println("  ResponseValue: null");
            }

            // Log remediation actions safely
            if (response.getRemediationActions() != null) {
                System.out.println("  RemediationActions: [" +
                    response.getRemediationActions().getClass().getSimpleName() + "]");
                // Try to get action list using common method names
                try {
                    // Try getRemediationActionList() first
                    var actionListMethod = response.getRemediationActions().getClass().getMethod("getRemediationActionList");
                    var actionList = (java.util.List<?>) actionListMethod.invoke(response.getRemediationActions());
                    if (actionList != null) {
                        System.out.println("    Actions count: " + actionList.size());
                        for (int i = 0; i < actionList.size(); i++) {
                            var action = actionList.get(i);
                            System.out.println("      Action " + i + ": " +
                                (action != null ? action.getClass().getSimpleName() : "null"));
                        }
                    }
                } catch (Exception e) {
                    // If that fails, try other common method names
                    try {
                        var actionsMethod = response.getRemediationActions().getClass().getMethod("getActions");
                        var actionList = (java.util.List<?>) actionsMethod.invoke(response.getRemediationActions());
                        if (actionList != null) {
                            System.out.println("    Actions count: " + actionList.size());
                            for (int i = 0; i < actionList.size(); i++) {
                                var action = actionList.get(i);
                                System.out.println("      Action " + i + ": " +
                                    (action != null ? action.getClass().getSimpleName() : "null"));
                            }
                        }
                    } catch (Exception e2) {
                        System.out.println("    Unable to access action list: " + e2.getMessage());
                    }
                }
            } else {
                System.out.println("  RemediationActions: null");
            }

            System.out.println("  Original serialization error: " + originalException.getMessage());

        } catch (Exception e) {
            System.out.println("  Failed to log simplified response: " + e.getMessage());
            System.out.println("  Response object type: " + response.getClass().getSimpleName());
        }
    }
}
