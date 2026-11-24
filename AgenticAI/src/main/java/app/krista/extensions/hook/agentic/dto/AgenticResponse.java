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

package app.krista.extensions.hook.agentic.dto;

import java.util.Map;

/**
 * Response from the agentic flow, indicating the next action to take.
 * Can be EXECUTE_TOOL (run a tool), COMPLETE (flow finished), or ERROR (something went wrong).
 */
public class AgenticResponse {
    
    public enum ResponseType {
        EXECUTE_TOOL,
        COMPLETE,
        ERROR
    }
    
    private String sessionId;
    private ResponseType responseType;
    private AgenticCommand command;
    private String finalResponse;
    private Map<String, Object> outputVariables;
    private String errorMessage;
    private Double confidence; // 0.0 to 1.0 (0% to 100%)
    private String statusMessage;

    public AgenticResponse() {
    }

    public AgenticResponse(String sessionId, ResponseType responseType) {
        this.sessionId = sessionId;
        this.responseType = responseType;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public ResponseType getResponseType() {
        return responseType;
    }

    public void setResponseType(ResponseType responseType) {
        this.responseType = responseType;
    }

    public AgenticCommand getCommand() {
        return command;
    }

    public void setCommand(AgenticCommand command) {
        this.command = command;
    }

    public String getFinalResponse() {
        return finalResponse;
    }

    public void setFinalResponse(String finalResponse) {
        this.finalResponse = finalResponse;
    }

    public Map<String, Object> getOutputVariables() {
        return outputVariables;
    }

    public void setOutputVariables(Map<String, Object> outputVariables) {
        this.outputVariables = outputVariables;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public Double getConfidence() {
        return confidence;
    }

    public void setConfidence(Double confidence) {
        this.confidence = confidence;
    }

    public String getStatusMessage() {
        return statusMessage;
    }

    public void setStatusMessage(String statusMessage) {
        this.statusMessage = statusMessage;
    }

    @Override
    public String toString() {
        return "AgenticResponse{" +
                "sessionId='" + sessionId + '\'' +
                ", responseType=" + responseType +
                ", command=" + command +
                ", finalResponse='" + finalResponse + '\'' +
                ", outputVariables=" + outputVariables +
                ", errorMessage='" + errorMessage + '\'' +
                ", confidence=" + confidence +
                ", statusMessage='" + statusMessage + '\'' +
                '}';
    }
}

