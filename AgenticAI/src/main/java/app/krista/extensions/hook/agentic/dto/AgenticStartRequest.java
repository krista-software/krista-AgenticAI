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

import app.krista.extensions.hook.common.models.StepMetadata;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Request to start a new agentic conversation flow.
 * Contains the tools available, system prompt, initial user message, thread variables,
 * input variables, and expected outputs.
 */
public class AgenticStartRequest {
    private List<AgenticToolDefinition> tools;
    private String userMessage;
    private Map<String, Object> threadVariables;
    private String sessionId;
    private List<InputVariable> inputVariables;
    private List<ExpectedOutput> expectedOutputs;
    private List<String> policyBrief;
    private String stepGoal;

    public AgenticStartRequest() {

    }

    public AgenticStartRequest(List<AgenticToolDefinition> tools,
                              String userMessage, Map<String, Object> threadVariables,
                              List<InputVariable> inputVariables,
                              List<ExpectedOutput> expectedOutputs, String sessionId) {
        this.tools = tools;
        this.userMessage = userMessage;
        this.threadVariables = threadVariables;
        this.inputVariables = inputVariables;
        this.expectedOutputs = expectedOutputs;
        this.sessionId = sessionId;
    }

    /**
     * Constructor with StepMetadata for structured session ID generation.
     * Use this when you want traceable session IDs based on execution context.
     */
    public AgenticStartRequest(List<AgenticToolDefinition> tools,
                              String userMessage, Map<String, Object> threadVariables,
                              List<InputVariable> inputVariables,
                              List<ExpectedOutput> expectedOutputs, 
                              StepMetadata stepMetadata) {
        this.tools = tools;
        this.userMessage = userMessage;
        this.threadVariables = threadVariables;
        this.inputVariables = inputVariables;
        this.expectedOutputs = expectedOutputs;
        this.sessionId = generateSessionId(stepMetadata);
    }

    /**
     * Generate session ID from step metadata.
     * Format: sess_workspacename_topicname_conversationtitle_executionid_steptitle_uuid
     */
    private static String generateSessionId(StepMetadata stepMetadata) {
        if (stepMetadata == null) {
            return "sess_" + UUID.randomUUID();
        }

        String workspaceName = sanitize(stepMetadata.toPromptMap().get("WorkspaceName"));
        String topicName = sanitize(stepMetadata.toPromptMap().get("TopicName"));
        String conversationTitle = sanitize(stepMetadata.getConversationTitle());
        String executionId = sanitize(stepMetadata.getExecutionId());
        String stepTitle = sanitize(stepMetadata.getStepTitle());
        String uuid = UUID.randomUUID().toString();

        return String.format("sess_%s_%s_%s_%s_%s_%s",
            workspaceName, topicName, conversationTitle, executionId, stepTitle, uuid);
    }

    /**
     * Sanitize string for use in session ID.
     */
    private static String sanitize(Object value) {
        if (value == null) {
            return "unknown";
        }
        return value.toString()
            .replaceAll("[^a-zA-Z0-9-]", "_")
            .toLowerCase()
            .replaceAll("_+", "_")  // Replace multiple underscores with single
            .replaceAll("^_|_$", "");  // Remove leading/trailing underscores
    }

    public List<AgenticToolDefinition> getTools() {
        return tools;
    }

    public void setTools(List<AgenticToolDefinition> tools) {
        this.tools = tools;
    }

    public String getUserMessage() {
        return userMessage;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setUserMessage(String userMessage) {
        this.userMessage = userMessage;
    }

    public Map<String, Object> getThreadVariables() {
        return threadVariables;
    }

    public void setThreadVariables(Map<String, Object> threadVariables) {
        this.threadVariables = threadVariables;
    }

    public List<InputVariable> getInputVariables() {
        return inputVariables;
    }

    public void setInputVariables(List<InputVariable> inputVariables) {
        this.inputVariables = inputVariables;
    }

    public List<ExpectedOutput> getExpectedOutputs() {
        return expectedOutputs;
    }

    public void setExpectedOutputs(List<ExpectedOutput> expectedOutputs) {

        this.expectedOutputs = expectedOutputs;
    }

    public List<String> getPolicyBrief() {

        return policyBrief;
    }

    public void setPolicyBrief(List<String> policyBrief) {

        this.policyBrief = policyBrief;
    }

    public String getStepGoal() {

        return stepGoal;
    }

    public void setStepGoal(String stepGoal) {

        this.stepGoal = stepGoal;
    }

    @Override
    public String toString() {
        return "AgenticStartRequest{" +
                "tools=" + tools +
                ", userMessage='" + userMessage + '\'' +
                ", threadVariables=" + threadVariables +
                ", inputVariablesCount=" + (inputVariables != null ? inputVariables.size() : 0) +
                ", expectedOutputsCount=" + (expectedOutputs != null ? expectedOutputs.size() : 0) +
                '}';
    }
}
