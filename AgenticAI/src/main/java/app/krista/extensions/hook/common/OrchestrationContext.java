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

package app.krista.extensions.hook.common;

import java.util.List;
import java.util.Map;

// TODO: FUTURE - Switch back to FreeForm when serialization/deserialization is fixed
// import app.krista.model.base.FreeForm;

/**
 * Context object containing orchestration-specific parameters for agentic flow execution.
 * This encapsulates all the data needed for orchestration steps, keeping the generic
 * StepExecutionHandler interface clean while providing type-safe access to orchestration data.
 */
public class OrchestrationContext {

    // CURRENT: Using List<Map<String, Object>> due to serialization issues
    // The array type in @Field.Desc maps to List in Java
    private final List<Map<String, Object>> toolDefinitions;

    // FUTURE: Will use FreeForm when serialization is fixed
    // private final FreeForm toolDefinitions;

    private final String sessionId;
    private final String previousToolName;
    private final Object previousToolResult;
    private final String prompt;
    private final List<Map<String, Object>> inputVariables;
    private final List<Map<String, Object>> expectedOutputs;
    private final List<String> policyBrief;

    // CURRENT: Constructor using List<Map<String, Object>>
    private OrchestrationContext(List<Map<String, Object>> toolDefinitions, String sessionId,
                                 String previousToolName, Object previousToolResult, String prompt,
                                 List<Map<String, Object>> inputVariables,
                                 List<Map<String, Object>> expectedOutputs,
                                 List<String> policyBrief) {

        this.toolDefinitions = toolDefinitions;
        this.sessionId = sessionId;
        this.previousToolName = previousToolName;
        this.previousToolResult = previousToolResult;
        this.prompt = prompt;
        this.inputVariables = inputVariables;
        this.expectedOutputs = expectedOutputs;
        this.policyBrief = policyBrief;
    }

    // FUTURE: Constructor using FreeForm (commented out for now)
    // private OrchestrationContext(FreeForm toolDefinitions, String sessionId,
    //                               String previousToolName, Object previousToolResult) {
    //     this.toolDefinitions = toolDefinitions;
    //     this.sessionId = sessionId;
    //     this.previousToolName = previousToolName;
    //     this.previousToolResult = previousToolResult;
    // }

    /**
     * Creates an empty orchestration context for non-orchestration steps.
     * This is used by handlers that don't need orchestration capabilities.
     */
    public static OrchestrationContext empty() {

        return new OrchestrationContext(null, null, null, null, null, null, null, null);
    }

    /**
     * Creates an orchestration context with the provided parameters.
     * Used for orchestration steps that need tool definitions and session management.
     *
     * CURRENT: Using List<Map<String, Object>> for toolDefinitions
     */
    public static OrchestrationContext of(List<Map<String, Object>> toolDefinitions, String sessionId,
                                          String previousToolName, Object previousToolResult, String prompt,
                                          List<Map<String, Object>> inputVariables,
                                          List<Map<String, Object>> expectedOutputs,
                                          List<String> policyBrief) {

        return new OrchestrationContext(toolDefinitions, sessionId, previousToolName, previousToolResult,
                prompt, inputVariables, expectedOutputs, policyBrief);
    }

    // FUTURE: Factory method using FreeForm (commented out for now)
    // public static OrchestrationContext of(FreeForm toolDefinitions, String sessionId,
    //                                       String previousToolName, Object previousToolResult) {
    //     return new OrchestrationContext(toolDefinitions, sessionId, previousToolName, previousToolResult);
    // }

    /**
     * Checks if this is an orchestration context (has tool definitions).
     */
    public boolean isOrchestration() {
        return toolDefinitions != null;
    }

    /**
     * Checks if this is a new orchestration request (no session ID).
     */
    public boolean isNewRequest() {
        return sessionId == null || sessionId.isEmpty();
    }

    /**
     * Checks if this is a continuation of an existing orchestration session.
     */
    public boolean isContinuation() {
        return sessionId != null && !sessionId.isEmpty();
    }

    // CURRENT: Getter returning List<Map<String, Object>>
    public List<Map<String, Object>> getToolDefinitions() {
        return toolDefinitions;
    }

    // FUTURE: Getter returning FreeForm (commented out for now)
    // public FreeForm getToolDefinitions() {
    //     return toolDefinitions;
    // }

    public String getSessionId() {
        return sessionId;
    }

    public String getPreviousToolName() {
        return previousToolName;
    }

    public Object getPreviousToolResult() {
        return previousToolResult;
    }

    public String getPrompt() {
        return prompt;
    }

    public List<Map<String, Object>> getInputVariables() {
        return inputVariables;
    }

    public List<Map<String, Object>> getExpectedOutputs() {

        return expectedOutputs;
    }

    public List<String> getPolicyBrief() {

        return policyBrief;
    }

    @Override
    public String toString() {

        return "OrchestrationContext{" +
                "isOrchestration=" + isOrchestration() +
                ", sessionId='" + sessionId + '\'' +
                ", previousToolName='" + previousToolName + '\'' +
                ", hasPreviousResult=" + (previousToolResult != null) +
                ", inputVariablesCount=" + (inputVariables != null ? inputVariables.size() : 0) +
                ", expectedOutputsCount=" + (expectedOutputs != null ? expectedOutputs.size() : 0) +
                ", policyBriefCount=" + (policyBrief != null ? policyBrief.size() : 0) +
                '}';
    }
}

