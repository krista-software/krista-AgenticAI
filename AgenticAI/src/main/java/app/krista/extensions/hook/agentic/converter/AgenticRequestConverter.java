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

import app.krista.extensions.hook.agentic.AgenticConstants;
import app.krista.extensions.hook.agentic.dto.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Stateless converter that transforms raw CAM map payloads into typed agentic DTOs.
 * Handles tool definitions, input variables, and expected outputs sent by KristaGrand.
 */
public class AgenticRequestConverter {

    private static final Logger LOGGER = LoggerFactory.getLogger(AgenticRequestConverter.class);

    private AgenticRequestConverter() {
    }

    /**
     * Converts raw CAM tool definition maps to typed {@link AgenticToolDefinition} objects.
     *
     * CAM field names: agenticToolName, description, agenticToolType, agenticToolParams, isHelperTool.
     * agenticToolName is a JSON string that the LLM must return verbatim in command.toolName.
     */
    public static List<AgenticToolDefinition> convertToolDefinitions(List<Map<String, Object>> toolDefinitions) {
        List<AgenticToolDefinition> tools = new ArrayList<>();

        if (toolDefinitions == null || toolDefinitions.isEmpty()) {
            return tools;
        }

        for (Map<String, Object> toolMap : toolDefinitions) {
            String name = (String) toolMap.get("agenticToolName");
            String description = (String) toolMap.get("description");
            String type = (String) toolMap.get("agenticToolType");
            Boolean isHelperTool = (Boolean) toolMap.get("isHelperTool");

            LOGGER.debug("Tool: name={}, type={}, description={}", name, type, description);

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> parametersRaw = (List<Map<String, Object>>) toolMap.get("agenticToolParams");

            List<AgenticToolDefinition.ParameterDefinition> parameters = new ArrayList<>();
            if (parametersRaw != null) {

                for (Map<String, Object> paramMap : parametersRaw) {

                    String paramName = (String) paramMap.get("pDefName");
                    String paramDescription = (String) paramMap.get("description");
                    String paramType = (String) paramMap.get("pDefType");
                    Boolean paramRequired = (Boolean) paramMap.getOrDefault("required", false);

                    @SuppressWarnings("unchecked")
                    Map<String, Object> paramMetadata = paramMap.get("metadata") instanceof Map
                            ? (Map<String, Object>) paramMap.get("metadata") : null;

                    parameters.add(new AgenticToolDefinition.ParameterDefinition(
                        paramName, paramType, paramDescription, paramRequired, paramMetadata));
                }
            }

            tools.add(new AgenticToolDefinition(name, description, type, parameters, isHelperTool));
        }
        return tools;
    }

    /**
     * Converts raw CAM input variable maps to typed {@link InputVariable} objects.
     * Handles both nested aiContextVariable structure and the flat fallback structure.
     */
    public static List<InputVariable> parseInputVariables(List<Map<String, Object>> inputVariablesJson) {

        if (inputVariablesJson == null || inputVariablesJson.isEmpty()) {

            return new ArrayList<>();
        }

        List<InputVariable> inputVariables = new ArrayList<>();
        for (Map<String, Object> varMap : inputVariablesJson) {

            AIContextVariable contextVar;

            Object aiContextVarObj = varMap.get("aiContextVariable");
            if (aiContextVarObj instanceof Map) {

                @SuppressWarnings("unchecked")
                Map<String, Object> aiContextVarMap = (Map<String, Object>) aiContextVarObj;
                contextVar = parseAIContextVariable(aiContextVarMap);
            } else {

                // Fallback to flat structure
                contextVar = parseAIContextVariable(varMap);
            }

            InputVariable inputVar = new InputVariable();
            inputVar.setAiContextVariable(contextVar);
            inputVar.setValue(varMap.get("value"));

            inputVariables.add(inputVar);
        }
        return inputVariables;
    }

    /**
     * Converts raw CAM expected output maps to typed {@link ExpectedOutput} objects.
     * Handles both nested aiContextVariable structure and the flat fallback structure.
     * Automatically injects system-reserved variables (conversation_history, tool_summary)
     * if not already present.
     */
    public static List<ExpectedOutput> parseExpectedOutputs(List<Map<String, Object>> expectedOutputsJson) {
        List<ExpectedOutput> expectedOutputs = new ArrayList<>();

        if (expectedOutputsJson != null && !expectedOutputsJson.isEmpty()) {

            for (Map<String, Object> outputMap : expectedOutputsJson) {

                AIContextVariable contextVar;

                Object aiContextVarObj = outputMap.get("aiContextVariable");
                if (aiContextVarObj instanceof Map) {

                    @SuppressWarnings("unchecked")
                    Map<String, Object> aiContextVarMap = (Map<String, Object>) aiContextVarObj;
                    contextVar = parseAIContextVariable(aiContextVarMap);
                } else {

                    // Fallback to flat structure
                    contextVar = parseAIContextVariable(outputMap);
                }

                ExpectedOutput expectedOutput = new ExpectedOutput();
                expectedOutput.setAiContextVariable(contextVar);
                expectedOutput.setInstruction((String) outputMap.get("instruction"));
                expectedOutput.setOperationType((String) outputMap.get("operationType"));

                expectedOutputs.add(expectedOutput);
            }
        }

        // Inject system-reserved variables if not already present
        boolean hasConversationHistory = expectedOutputs.stream()
            .anyMatch(eo -> AgenticConstants.SYSTEM_VAR_CONVERSATION_HISTORY.equals(eo.getAiContextVariable().getName()));
        boolean hasToolSummary = expectedOutputs.stream()
            .anyMatch(eo -> AgenticConstants.SYSTEM_VAR_TOOL_SUMMARY.equals(eo.getAiContextVariable().getName()));
        boolean hasSessionSummary = expectedOutputs.stream()
            .anyMatch(eo -> AgenticConstants.SYSTEM_VAR_SESSION_SUMMARY.equals(eo.getAiContextVariable().getName()));

        if (!hasConversationHistory) {
            AIContextVariable historyVar = new AIContextVariable();
            historyVar.setName(AgenticConstants.SYSTEM_VAR_CONVERSATION_HISTORY);
            historyVar.setType("Text");
            historyVar.setDescription("Auto-generated conversation history");
            historyVar.setRequired(false);

            ExpectedOutput historyOutput = new ExpectedOutput();
            historyOutput.setAiContextVariable(historyVar);
            historyOutput.setInstruction("Complete conversation history (auto-populated by system)");
            historyOutput.setOperationType("read");

            expectedOutputs.add(historyOutput);
            LOGGER.debug("Added system variable '{}' to expected outputs", AgenticConstants.SYSTEM_VAR_CONVERSATION_HISTORY);
        }

        if (!hasToolSummary) {
            AIContextVariable summaryVar = new AIContextVariable();
            summaryVar.setName(AgenticConstants.SYSTEM_VAR_TOOL_SUMMARY);
            summaryVar.setType("Text");
            summaryVar.setDescription("Auto-generated tool execution summary");
            summaryVar.setRequired(false);

            ExpectedOutput summaryOutput = new ExpectedOutput();
            summaryOutput.setAiContextVariable(summaryVar);
            summaryOutput.setInstruction("Tool execution summary (auto-populated by system)");
            summaryOutput.setOperationType("read");

            expectedOutputs.add(summaryOutput);
            LOGGER.debug("Added system variable '{}' to expected outputs", AgenticConstants.SYSTEM_VAR_TOOL_SUMMARY);
        }

        if (!hasSessionSummary) {
            AIContextVariable sessionSummaryVar = new AIContextVariable();
            sessionSummaryVar.setName(AgenticConstants.SYSTEM_VAR_SESSION_SUMMARY);
            // Text-typed so authors can declare it in studio without listing sub-fields.
            // The value is the structured summary serialised as a JSON string.
            sessionSummaryVar.setType("Text");
            sessionSummaryVar.setDescription("Auto-generated session summary as JSON string");
            sessionSummaryVar.setRequired(false);

            ExpectedOutput sessionSummaryOutput = new ExpectedOutput();
            sessionSummaryOutput.setAiContextVariable(sessionSummaryVar);
            sessionSummaryOutput.setInstruction("Structured session summary (terminationReason, activityFeed, childSessions, usage) as JSON string (auto-populated by system)");
            sessionSummaryOutput.setOperationType("read");

            expectedOutputs.add(sessionSummaryOutput);
            LOGGER.debug("Added system variable '{}' to expected outputs", AgenticConstants.SYSTEM_VAR_SESSION_SUMMARY);
        }

        return expectedOutputs;
    }

    /**
     * Deserializes a single AIContextVariable from a raw Map, handling the platform's
     * "fieldType" key (with "type" fallback), recursive sub-variables, and metaData.
     *
     * @param map the raw map from JSON deserialization
     * @return a fully populated AIContextVariable, never null
     */
    @SuppressWarnings("unchecked")
    private static AIContextVariable parseAIContextVariable(Map<String, Object> map) {

        AIContextVariable contextVar = new AIContextVariable();

        contextVar.setName((String) map.get("name"));

        // Platform serializes as "fieldType"; older paths or tests may use "type"
        String type = (String) map.get("fieldType");
        if (type == null) {

            type = (String) map.get("type");
        }
        contextVar.setType(type);

        contextVar.setDescription((String) map.get("description"));

        // Recursively deserialize sub-variables
        Object subVarsRaw = map.get("subAIContextVariables");
        if (subVarsRaw instanceof List<?> subVarsList && !subVarsList.isEmpty()) {

            List<AIContextVariable> subVars = new ArrayList<>();
            for (Object item : subVarsList) {

                if (item instanceof Map) {

                    subVars.add(parseAIContextVariable((Map<String, Object>) item));
                }
            }
            contextVar.setSubContextVariables(subVars);
        }

        // Deserialize metaData
        Object metaDataRaw = map.get("metaData");
        if (metaDataRaw instanceof Map) {

            contextVar.setMetaData((Map<String, Object>) metaDataRaw);
        }

        // Read required from top-level (direct field) or metaData (sub-field propagation from platform)
        boolean required = Boolean.TRUE.equals(map.get("required"));
        if (!required && contextVar.getMetaData() != null) {

            required = Boolean.TRUE.equals(contextVar.getMetaData().get("required"));
        }
        contextVar.setRequired(required);

        return contextVar;
    }
}
