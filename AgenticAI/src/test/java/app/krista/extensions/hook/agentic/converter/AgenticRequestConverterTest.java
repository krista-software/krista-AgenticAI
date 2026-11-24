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

import app.krista.extensions.hook.agentic.dto.AIContextVariable;
import app.krista.extensions.hook.agentic.dto.AgenticToolDefinition;
import app.krista.extensions.hook.agentic.dto.ExpectedOutput;
import app.krista.extensions.hook.agentic.dto.InputVariable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for AgenticRequestConverter — stateless converter that transforms
 * raw CAM map payloads into typed agentic DTOs.
 *
 * No external dependencies required; all tests operate on pure in-memory data.
 */
@DisplayName("AgenticRequestConverter")
class AgenticRequestConverterTest {

    // -------------------------------------------------------------------------
    // convertToolDefinitions
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("convertToolDefinitions: null input returns empty list")
    void convertToolDefinitions_nullInput_returnsEmptyList() {

        List<AgenticToolDefinition> result = AgenticRequestConverter.convertToolDefinitions(null);

        assertNotNull(result, "Result must not be null");
        assertTrue(result.isEmpty(), "Result must be empty for null input");
    }

    @Test
    @DisplayName("convertToolDefinitions: empty list returns empty list")
    void convertToolDefinitions_emptyList_returnsEmptyList() {

        List<AgenticToolDefinition> result = AgenticRequestConverter.convertToolDefinitions(new ArrayList<>());

        assertNotNull(result, "Result must not be null");
        assertTrue(result.isEmpty(), "Result must be empty for empty input");
    }

    @Test
    @DisplayName("convertToolDefinitions: single tool with all fields maps correctly")
    void convertToolDefinitions_singleToolAllFields_mapsCorrectly() {

        // Given
        Map<String, Object> toolMap = new HashMap<>();
        toolMap.put("agenticToolName", "{\"toolId\":\"tool_123\",\"toolType\":\"standard\"}");
        toolMap.put("description", "A test tool");
        toolMap.put("agenticToolType", "BusinessTool");
        toolMap.put("isHelperTool", Boolean.TRUE);

        List<Map<String, Object>> input = List.of(toolMap);

        // When
        List<AgenticToolDefinition> result = AgenticRequestConverter.convertToolDefinitions(input);

        // Then
        assertNotNull(result);
        assertEquals(1, result.size(), "Expected exactly one tool definition");

        AgenticToolDefinition tool = result.get(0);
        assertEquals("{\"toolId\":\"tool_123\",\"toolType\":\"standard\"}", tool.name(),
            "Tool name must equal the raw agenticToolName string");
        assertEquals("A test tool", tool.description(), "Tool description must be mapped correctly");
        assertEquals("BusinessTool", tool.type(), "Tool type must be mapped correctly");
        assertEquals(Boolean.TRUE, tool.isHelperTool(), "isHelperTool must be TRUE");
    }

    @Test
    @DisplayName("convertToolDefinitions: tool with parameters populates ParameterDefinition list")
    void convertToolDefinitions_withParameters_populatesParameterDefinitionList() {

        // Given
        Map<String, Object> paramMap = new HashMap<>();
        paramMap.put("pDefName", "customerId");
        paramMap.put("pDefType", "String");
        paramMap.put("description", "The customer identifier");
        paramMap.put("required", Boolean.TRUE);

        Map<String, Object> toolMap = new HashMap<>();
        toolMap.put("agenticToolName", "lookup_customer");
        toolMap.put("description", "Looks up a customer");
        toolMap.put("agenticToolType", "lookup");
        toolMap.put("isHelperTool", Boolean.FALSE);
        toolMap.put("agenticToolParams", List.of(paramMap));

        // When
        List<AgenticToolDefinition> result = AgenticRequestConverter.convertToolDefinitions(List.of(toolMap));

        // Then
        assertNotNull(result);
        assertEquals(1, result.size());

        List<AgenticToolDefinition.ParameterDefinition> params = result.get(0).parameters();
        assertNotNull(params, "Parameters list must not be null");
        assertEquals(1, params.size(), "Expected exactly one parameter definition");

        AgenticToolDefinition.ParameterDefinition param = params.get(0);
        assertEquals("customerId", param.pDefName(), "Parameter name must match pDefName");
        assertEquals("String", param.pDefType(), "Parameter type must match pDefType");
        assertEquals("The customer identifier", param.description(), "Parameter description must match");
        assertTrue(param.required(), "Parameter required flag must be true");
    }

    @Test
    @DisplayName("convertToolDefinitions: tool with null parameters returns empty parameters list")
    void convertToolDefinitions_nullParameters_returnsEmptyParametersList() {

        // Given
        Map<String, Object> toolMap = new HashMap<>();
        toolMap.put("agenticToolName", "no_param_tool");
        toolMap.put("description", "Tool without params");
        toolMap.put("agenticToolType", "helper");
        toolMap.put("isHelperTool", Boolean.FALSE);
        // agenticToolParams intentionally absent

        // When
        List<AgenticToolDefinition> result = AgenticRequestConverter.convertToolDefinitions(List.of(toolMap));

        // Then
        assertNotNull(result);
        assertEquals(1, result.size());

        List<AgenticToolDefinition.ParameterDefinition> params = result.get(0).parameters();
        assertNotNull(params, "Parameters list must not be null even when raw params are absent");
        assertTrue(params.isEmpty(), "Parameters list must be empty when raw params are absent");
    }

    @Test
    @DisplayName("convertToolDefinitions: isHelperTool=null handled without NullPointerException")
    void convertToolDefinitions_isHelperToolNull_noNullPointerException() {

        // Given
        Map<String, Object> toolMap = new HashMap<>();
        toolMap.put("agenticToolName", "tool_no_helper_flag");
        toolMap.put("description", "Tool with null isHelperTool");
        toolMap.put("agenticToolType", "standard");
        // isHelperTool explicitly absent — will be null from map.get()

        // When / Then: must not throw
        assertDoesNotThrow(() -> {
            List<AgenticToolDefinition> result = AgenticRequestConverter.convertToolDefinitions(List.of(toolMap));
            assertEquals(1, result.size());
            assertNull(result.get(0).isHelperTool(), "isHelperTool should be null when absent");
        }, "convertToolDefinitions must not throw when isHelperTool is null");
    }

    // -------------------------------------------------------------------------
    // parseInputVariables
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("parseInputVariables: null input returns empty list")
    void parseInputVariables_nullInput_returnsEmptyList() {

        List<InputVariable> result = AgenticRequestConverter.parseInputVariables(null);

        assertNotNull(result, "Result must not be null");
        assertTrue(result.isEmpty(), "Result must be empty for null input");
    }

    @Test
    @DisplayName("parseInputVariables: empty list returns empty list")
    void parseInputVariables_emptyList_returnsEmptyList() {

        List<InputVariable> result = AgenticRequestConverter.parseInputVariables(new ArrayList<>());

        assertNotNull(result, "Result must not be null");
        assertTrue(result.isEmpty(), "Result must be empty for empty input");
    }

    @Test
    @DisplayName("parseInputVariables: nested aiContextVariable structure maps all fields")
    void parseInputVariables_nestedAiContextVariable_mapsAllFields() {

        // Given
        Map<String, Object> aiContextVar = new HashMap<>();
        aiContextVar.put("name", "accountId");
        aiContextVar.put("type", "Text");
        aiContextVar.put("description", "The account ID");
        aiContextVar.put("required", Boolean.TRUE);

        Map<String, Object> varMap = new HashMap<>();
        varMap.put("aiContextVariable", aiContextVar);
        varMap.put("value", "ACC-001");

        // When
        List<InputVariable> result = AgenticRequestConverter.parseInputVariables(List.of(varMap));

        // Then
        assertNotNull(result);
        assertEquals(1, result.size());

        InputVariable inputVar = result.get(0);
        assertNotNull(inputVar.getAiContextVariable(), "AIContextVariable must not be null");
        assertEquals("accountId", inputVar.getAiContextVariable().getName(), "Name must be mapped from nested structure");
        assertEquals("Text", inputVar.getAiContextVariable().getType(), "Type must be mapped from nested structure");
        assertEquals("The account ID", inputVar.getAiContextVariable().getDescription(), "Description must be mapped");
        assertTrue(inputVar.getAiContextVariable().isRequired(), "Required flag must be true");
        assertEquals("ACC-001", inputVar.getValue(), "Value must be mapped from the top-level map");
    }

    @Test
    @DisplayName("parseInputVariables: flat structure (no aiContextVariable key) maps correctly")
    void parseInputVariables_flatStructure_mapsCorrectly() {

        // Given
        Map<String, Object> varMap = new HashMap<>();
        varMap.put("name", "orderRef");
        varMap.put("type", "Number");
        varMap.put("description", "Order reference");
        varMap.put("required", Boolean.FALSE);
        varMap.put("value", 42);

        // When
        List<InputVariable> result = AgenticRequestConverter.parseInputVariables(List.of(varMap));

        // Then
        assertNotNull(result);
        assertEquals(1, result.size());

        InputVariable inputVar = result.get(0);
        assertNotNull(inputVar.getAiContextVariable(), "AIContextVariable must not be null");
        assertEquals("orderRef", inputVar.getAiContextVariable().getName(), "Name must be mapped from flat structure");
        assertEquals("Number", inputVar.getAiContextVariable().getType(), "Type must be mapped from flat structure");
        assertEquals("Order reference", inputVar.getAiContextVariable().getDescription());
        assertFalse(inputVar.getAiContextVariable().isRequired(), "Required flag must be false");
        assertEquals(42, inputVar.getValue(), "Value must be mapped from flat structure");
    }

    @Test
    @DisplayName("parseInputVariables: value field populated on InputVariable")
    void parseInputVariables_valueFieldPopulated() {

        // Given
        Map<String, Object> varMap = new HashMap<>();
        varMap.put("name", "status");
        varMap.put("type", "Text");
        varMap.put("value", "ACTIVE");

        // When
        List<InputVariable> result = AgenticRequestConverter.parseInputVariables(List.of(varMap));

        // Then
        assertEquals(1, result.size());
        assertEquals("ACTIVE", result.get(0).getValue(),
            "Value field must be populated from the top-level 'value' key");
    }

    // -------------------------------------------------------------------------
    // parseExpectedOutputs
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("parseExpectedOutputs: null input still returns list with three system vars injected")
    void parseExpectedOutputs_nullInput_injectsSystemVars() {

        List<ExpectedOutput> result = AgenticRequestConverter.parseExpectedOutputs(null);

        assertNotNull(result, "Result must not be null");
        assertEquals(3, result.size(), "Expected exactly the three system-injected vars");

        List<String> names = result.stream()
            .map(eo -> eo.getAiContextVariable().getName())
            .toList();
        assertTrue(names.contains("conversation_history"), "conversation_history must be injected");
        assertTrue(names.contains("tool_summary"), "tool_summary must be injected");
        assertTrue(names.contains("session_summary"), "session_summary must be injected (KR-20450)");
    }

    @Test
    @DisplayName("parseExpectedOutputs: user-defined output parsed correctly")
    void parseExpectedOutputs_userDefinedOutput_parsedCorrectly() {

        // Given
        Map<String, Object> outputMap = new HashMap<>();
        outputMap.put("name", "resolvedStatus");
        outputMap.put("type", "Text");
        outputMap.put("description", "Final resolved status");
        outputMap.put("required", Boolean.TRUE);
        outputMap.put("instruction", "Set to RESOLVED or UNRESOLVED");
        outputMap.put("operationType", "write");

        // When
        List<ExpectedOutput> result = AgenticRequestConverter.parseExpectedOutputs(List.of(outputMap));

        // Then: 1 user + 3 system = 4
        assertNotNull(result);
        assertEquals(4, result.size(), "Expected 1 user-defined + 3 system-injected outputs");

        ExpectedOutput userOutput = result.stream()
            .filter(eo -> "resolvedStatus".equals(eo.getAiContextVariable().getName()))
            .findFirst()
            .orElseThrow(() -> new AssertionError("resolvedStatus output not found in result"));

        assertEquals("Text", userOutput.getAiContextVariable().getType());
        assertEquals("Final resolved status", userOutput.getAiContextVariable().getDescription());
        assertTrue(userOutput.getAiContextVariable().isRequired());
        assertEquals("Set to RESOLVED or UNRESOLVED", userOutput.getInstruction());
        assertEquals("write", userOutput.getOperationType());
    }

    @Test
    @DisplayName("parseExpectedOutputs: nested aiContextVariable structure maps correctly")
    void parseExpectedOutputs_nestedAiContextVariable_mapsCorrectly() {

        // Given
        Map<String, Object> aiContextVar = new HashMap<>();
        aiContextVar.put("name", "summaryText");
        aiContextVar.put("type", "Text");
        aiContextVar.put("description", "Summary of findings");
        aiContextVar.put("required", Boolean.FALSE);

        Map<String, Object> outputMap = new HashMap<>();
        outputMap.put("aiContextVariable", aiContextVar);
        outputMap.put("instruction", "Summarise findings");
        outputMap.put("operationType", "read");

        // When
        List<ExpectedOutput> result = AgenticRequestConverter.parseExpectedOutputs(List.of(outputMap));

        // Then
        ExpectedOutput userOutput = result.stream()
            .filter(eo -> "summaryText".equals(eo.getAiContextVariable().getName()))
            .findFirst()
            .orElseThrow(() -> new AssertionError("summaryText not found"));

        assertEquals("Text", userOutput.getAiContextVariable().getType());
        assertEquals("Summary of findings", userOutput.getAiContextVariable().getDescription());
        assertFalse(userOutput.getAiContextVariable().isRequired());
        assertEquals("Summarise findings", userOutput.getInstruction());
        assertEquals("read", userOutput.getOperationType());
    }

    @Test
    @DisplayName("parseExpectedOutputs: system vars auto-injected when absent from input")
    void parseExpectedOutputs_systemVarsAutoInjectedWhenAbsent() {

        // Given: one user output, no system vars
        Map<String, Object> userOutputMap = new HashMap<>();
        userOutputMap.put("name", "myVar");
        userOutputMap.put("type", "Text");

        // When
        List<ExpectedOutput> result = AgenticRequestConverter.parseExpectedOutputs(List.of(userOutputMap));

        // Then: system vars must be present
        List<String> names = result.stream()
            .map(eo -> eo.getAiContextVariable().getName())
            .toList();

        assertTrue(names.contains("conversation_history"),
            "conversation_history must be auto-injected");
        assertTrue(names.contains("tool_summary"),
            "tool_summary must be auto-injected");
    }

    @Test
    @DisplayName("parseExpectedOutputs: system vars NOT duplicated when already present in input")
    void parseExpectedOutputs_systemVarsNotDuplicatedWhenPresent() {

        // Given: user explicitly provides both system vars
        Map<String, Object> historyMap = new HashMap<>();
        historyMap.put("name", "conversation_history");
        historyMap.put("type", "Text");

        Map<String, Object> summaryMap = new HashMap<>();
        summaryMap.put("name", "tool_summary");
        summaryMap.put("type", "Text");

        // When
        List<ExpectedOutput> result = AgenticRequestConverter.parseExpectedOutputs(
            List.of(historyMap, summaryMap));

        // Then: history + tool_summary present (no duplication) + session_summary auto-injected = 3
        assertEquals(3, result.size(),
            "System vars present in input must not be duplicated; session_summary is auto-injected");

        long historyCount = result.stream()
            .filter(eo -> "conversation_history".equals(eo.getAiContextVariable().getName()))
            .count();
        long summaryCount = result.stream()
            .filter(eo -> "tool_summary".equals(eo.getAiContextVariable().getName()))
            .count();
        long sessionSummaryCount = result.stream()
            .filter(eo -> "session_summary".equals(eo.getAiContextVariable().getName()))
            .count();

        assertEquals(1, historyCount, "conversation_history must appear exactly once");
        assertEquals(1, summaryCount, "tool_summary must appear exactly once");
        assertEquals(1, sessionSummaryCount, "session_summary must be injected exactly once");
    }

    // -------------------------------------------------------------------------
    // fieldType / type key mapping
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("parseInputVariables: platform 'fieldType' key maps to getType()")
    void parseInputVariables_fieldTypeKey_mapsToGetType() {

        // Given — platform sends "fieldType" instead of "type"
        Map<String, Object> aiContextVar = new HashMap<>();
        aiContextVar.put("name", "priority");
        aiContextVar.put("fieldType", "PickOne");
        aiContextVar.put("description", "Ticket priority");
        aiContextVar.put("required", Boolean.TRUE);

        Map<String, Object> varMap = new HashMap<>();
        varMap.put("aiContextVariable", aiContextVar);
        varMap.put("value", "High");

        // When
        List<InputVariable> result = AgenticRequestConverter.parseInputVariables(List.of(varMap));

        // Then
        assertEquals(1, result.size());
        assertEquals("PickOne", result.get(0).getAiContextVariable().getType(),
            "fieldType key must be mapped to getType()");
    }

    @Test
    @DisplayName("parseInputVariables: old-style 'type' key still works as fallback")
    void parseInputVariables_typeKeyFallback_stillWorks() {

        // Given — old-style payload uses "type"
        Map<String, Object> aiContextVar = new HashMap<>();
        aiContextVar.put("name", "region");
        aiContextVar.put("type", "Text");
        aiContextVar.put("description", "Region code");

        Map<String, Object> varMap = new HashMap<>();
        varMap.put("aiContextVariable", aiContextVar);
        varMap.put("value", "US-WEST");

        // When
        List<InputVariable> result = AgenticRequestConverter.parseInputVariables(List.of(varMap));

        // Then
        assertEquals(1, result.size());
        assertEquals("Text", result.get(0).getAiContextVariable().getType(),
            "type key must still work when fieldType is absent");
    }

    @Test
    @DisplayName("parseInputVariables: fieldType takes precedence over type when both present")
    void parseInputVariables_fieldTypeTakesPrecedence() {

        // Given — both keys present (unlikely but defensive)
        Map<String, Object> aiContextVar = new HashMap<>();
        aiContextVar.put("name", "dual");
        aiContextVar.put("fieldType", "Entity");
        aiContextVar.put("type", "Text");

        Map<String, Object> varMap = new HashMap<>();
        varMap.put("aiContextVariable", aiContextVar);

        // When
        List<InputVariable> result = AgenticRequestConverter.parseInputVariables(List.of(varMap));

        // Then
        assertEquals("Entity", result.get(0).getAiContextVariable().getType(),
            "fieldType must take precedence over type");
    }

    // -------------------------------------------------------------------------
    // subAIContextVariables deserialization
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("parseInputVariables: subAIContextVariables deserialized into getSubContextVariables()")
    void parseInputVariables_subAIContextVariables_deserialized() {

        // Given — Entity with two sub-fields
        Map<String, Object> subVar1 = new HashMap<>();
        subVar1.put("name", "firstName");
        subVar1.put("fieldType", "Text");
        subVar1.put("description", "First name");
        subVar1.put("required", Boolean.TRUE);

        Map<String, Object> subVar2 = new HashMap<>();
        subVar2.put("name", "lastName");
        subVar2.put("fieldType", "Text");
        subVar2.put("description", "Last name");
        subVar2.put("required", Boolean.TRUE);

        Map<String, Object> aiContextVar = new HashMap<>();
        aiContextVar.put("name", "person");
        aiContextVar.put("fieldType", "Entity");
        aiContextVar.put("description", "Person entity");
        aiContextVar.put("required", Boolean.TRUE);
        aiContextVar.put("subAIContextVariables", List.of(subVar1, subVar2));

        Map<String, Object> varMap = new HashMap<>();
        varMap.put("aiContextVariable", aiContextVar);

        // When
        List<InputVariable> result = AgenticRequestConverter.parseInputVariables(List.of(varMap));

        // Then
        assertEquals(1, result.size());
        AIContextVariable personVar = result.get(0).getAiContextVariable();
        assertEquals("Entity", personVar.getType());

        List<AIContextVariable> subVars = personVar.getSubContextVariables();
        assertNotNull(subVars, "subContextVariables must not be null");
        assertEquals(2, subVars.size(), "Expected two sub-variables");

        assertEquals("firstName", subVars.get(0).getName());
        assertEquals("Text", subVars.get(0).getType());
        assertTrue(subVars.get(0).isRequired());

        assertEquals("lastName", subVars.get(1).getName());
        assertEquals("Text", subVars.get(1).getType());
    }

    @Test
    @DisplayName("parseInputVariables: recursive sub-variables (2 levels deep)")
    void parseInputVariables_recursiveSubVariables_twoLevelsDeep() {

        // Given — MultiField -> Date sub-field with its own sub-variables
        Map<String, Object> daySubVar = new HashMap<>();
        daySubVar.put("name", "day");
        daySubVar.put("fieldType", "Number");

        Map<String, Object> monthSubVar = new HashMap<>();
        monthSubVar.put("name", "month");
        monthSubVar.put("fieldType", "Number");

        Map<String, Object> dateSubVar = new HashMap<>();
        dateSubVar.put("name", "startDate");
        dateSubVar.put("fieldType", "Date");
        dateSubVar.put("subAIContextVariables", List.of(daySubVar, monthSubVar));

        Map<String, Object> aiContextVar = new HashMap<>();
        aiContextVar.put("name", "schedule");
        aiContextVar.put("fieldType", "MultiField");
        aiContextVar.put("subAIContextVariables", List.of(dateSubVar));

        Map<String, Object> varMap = new HashMap<>();
        varMap.put("aiContextVariable", aiContextVar);

        // When
        List<InputVariable> result = AgenticRequestConverter.parseInputVariables(List.of(varMap));

        // Then — level 1
        AIContextVariable scheduleVar = result.get(0).getAiContextVariable();
        assertEquals("MultiField", scheduleVar.getType());
        assertEquals(1, scheduleVar.getSubContextVariables().size());

        // level 2
        AIContextVariable dateVar = scheduleVar.getSubContextVariables().get(0);
        assertEquals("startDate", dateVar.getName());
        assertEquals("Date", dateVar.getType());
        assertEquals(2, dateVar.getSubContextVariables().size(), "Date must have 2 sub-fields");

        assertEquals("day", dateVar.getSubContextVariables().get(0).getName());
        assertEquals("month", dateVar.getSubContextVariables().get(1).getName());
    }

    // -------------------------------------------------------------------------
    // metaData deserialization
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("parseInputVariables: metaData with options deserialized into getMetaData()")
    void parseInputVariables_metaData_deserialized() {

        // Given — PickOne with options in metaData
        Map<String, Object> metaData = new HashMap<>();
        metaData.put("options", List.of("Low", "Medium", "High", "Critical"));
        metaData.put("defaultValue", "Medium");

        Map<String, Object> aiContextVar = new HashMap<>();
        aiContextVar.put("name", "severity");
        aiContextVar.put("fieldType", "PickOne");
        aiContextVar.put("description", "Issue severity");
        aiContextVar.put("metaData", metaData);

        Map<String, Object> varMap = new HashMap<>();
        varMap.put("aiContextVariable", aiContextVar);
        varMap.put("value", "High");

        // When
        List<InputVariable> result = AgenticRequestConverter.parseInputVariables(List.of(varMap));

        // Then
        AIContextVariable severityVar = result.get(0).getAiContextVariable();
        assertEquals("PickOne", severityVar.getType());

        Map<String, Object> resultMetaData = severityVar.getMetaData();
        assertNotNull(resultMetaData, "metaData must not be null");
        assertFalse(resultMetaData.isEmpty(), "metaData must not be empty");
        assertTrue(resultMetaData.containsKey("options"), "metaData must contain 'options' key");

        @SuppressWarnings("unchecked")
        List<String> options = (List<String>) resultMetaData.get("options");
        assertEquals(4, options.size());
        assertEquals("Medium", resultMetaData.get("defaultValue"));
    }

    // -------------------------------------------------------------------------
    // Null/absent sub-variables
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("parseInputVariables: simple variable without sub-vars has empty (not null) subContextVariables")
    void parseInputVariables_noSubVars_emptyList() {

        // Given — simple Text variable, no sub-variables, no metaData
        Map<String, Object> aiContextVar = new HashMap<>();
        aiContextVar.put("name", "simpleText");
        aiContextVar.put("fieldType", "Text");
        aiContextVar.put("description", "A simple text field");

        Map<String, Object> varMap = new HashMap<>();
        varMap.put("aiContextVariable", aiContextVar);
        varMap.put("value", "hello");

        // When
        List<InputVariable> result = AgenticRequestConverter.parseInputVariables(List.of(varMap));

        // Then
        AIContextVariable ctxVar = result.get(0).getAiContextVariable();
        assertNotNull(ctxVar.getSubContextVariables(),
            "subContextVariables must not be null (default constructor initializes to empty list)");
        assertTrue(ctxVar.getSubContextVariables().isEmpty(),
            "subContextVariables must be empty for a simple variable");
        assertNotNull(ctxVar.getMetaData(), "metaData must not be null");
        assertTrue(ctxVar.getMetaData().isEmpty(), "metaData must be empty when absent from payload");
    }

    // -------------------------------------------------------------------------
    // parseExpectedOutputs with rich data
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("parseExpectedOutputs: Entity output with sub-fields and metaData")
    void parseExpectedOutputs_entityWithSubFieldsAndMetaData() {

        // Given — Entity expected output with sub-fields and metaData
        Map<String, Object> subVar1 = new HashMap<>();
        subVar1.put("name", "ticketId");
        subVar1.put("fieldType", "Text");
        subVar1.put("required", Boolean.TRUE);

        Map<String, Object> subVar2 = new HashMap<>();
        subVar2.put("name", "status");
        subVar2.put("fieldType", "PickOne");
        subVar2.put("metaData", Map.of("options", List.of("Open", "Closed")));

        Map<String, Object> aiContextVar = new HashMap<>();
        aiContextVar.put("name", "ticket");
        aiContextVar.put("fieldType", "Entity");
        aiContextVar.put("description", "Support ticket");
        aiContextVar.put("required", Boolean.TRUE);
        aiContextVar.put("subAIContextVariables", List.of(subVar1, subVar2));
        aiContextVar.put("metaData", Map.of("entityType", "SupportTicket"));

        Map<String, Object> outputMap = new HashMap<>();
        outputMap.put("aiContextVariable", aiContextVar);
        outputMap.put("instruction", "Create ticket with given fields");
        outputMap.put("operationType", "write");

        // When
        List<ExpectedOutput> result = AgenticRequestConverter.parseExpectedOutputs(List.of(outputMap));

        // Then — 1 user + 3 system = 4
        assertEquals(4, result.size(), "Expected 1 user-defined + 3 system-injected outputs");

        ExpectedOutput ticketOutput = result.stream()
            .filter(eo -> "ticket".equals(eo.getAiContextVariable().getName()))
            .findFirst()
            .orElseThrow(() -> new AssertionError("ticket output not found"));

        AIContextVariable ticketVar = ticketOutput.getAiContextVariable();
        assertEquals("Entity", ticketVar.getType());
        assertTrue(ticketVar.isRequired());
        assertEquals("Create ticket with given fields", ticketOutput.getInstruction());
        assertEquals("write", ticketOutput.getOperationType());

        // Sub-variables
        assertEquals(2, ticketVar.getSubContextVariables().size());
        assertEquals("ticketId", ticketVar.getSubContextVariables().get(0).getName());
        assertEquals("status", ticketVar.getSubContextVariables().get(1).getName());

        // MetaData on sub-variable
        AIContextVariable statusSubVar = ticketVar.getSubContextVariables().get(1);
        assertNotNull(statusSubVar.getMetaData());
        assertTrue(statusSubVar.getMetaData().containsKey("options"));

        // MetaData on parent
        assertEquals("SupportTicket", ticketVar.getMetaData().get("entityType"));
    }

    // -------------------------------------------------------------------------
    // required flag from metaData fallback
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("parseAIContextVariable: required=true inside metaData sets isRequired() when top-level required is absent")
    void parseInputVariables_requiredInMetaData_setsRequired() {

        // Given — simulates the wire format from the platform where 'required' lives
        // inside metaData (propagated from NamedField.getOptions()) rather than at top level
        Map<String, Object> metaData = new HashMap<>();
        metaData.put("required", Boolean.TRUE);

        Map<String, Object> aiContextVar = new HashMap<>();
        aiContextVar.put("name", "Docset Name");
        aiContextVar.put("fieldType", "Text");
        aiContextVar.put("metaData", metaData);
        // Note: no top-level "required" key

        Map<String, Object> varMap = new HashMap<>();
        varMap.put("aiContextVariable", aiContextVar);
        varMap.put("value", "My Document Set");

        // When
        List<InputVariable> result = AgenticRequestConverter.parseInputVariables(List.of(varMap));

        // Then
        assertEquals(1, result.size());
        AIContextVariable parsed = result.get(0).getAiContextVariable();
        assertTrue(parsed.isRequired(),
            "isRequired() must be true when 'required' is in metaData but absent from top-level map");
    }

    @Test
    @DisplayName("parseAIContextVariable: top-level required=true takes precedence over absent metaData")
    void parseInputVariables_requiredTopLevel_takesPrecedence() {

        // Given — top-level required is true, no metaData
        Map<String, Object> aiContextVar = new HashMap<>();
        aiContextVar.put("name", "Status");
        aiContextVar.put("fieldType", "Text");
        aiContextVar.put("required", Boolean.TRUE);

        Map<String, Object> varMap = new HashMap<>();
        varMap.put("aiContextVariable", aiContextVar);

        // When
        List<InputVariable> result = AgenticRequestConverter.parseInputVariables(List.of(varMap));

        // Then
        assertTrue(result.get(0).getAiContextVariable().isRequired(),
            "isRequired() must be true when set at top level");
    }

    @Test
    @DisplayName("parseAIContextVariable: required=false in metaData does not set isRequired()")
    void parseInputVariables_requiredFalseInMetaData_doesNotSetRequired() {

        // Given — metaData has required=false, no top-level required
        Map<String, Object> metaData = new HashMap<>();
        metaData.put("required", Boolean.FALSE);

        Map<String, Object> aiContextVar = new HashMap<>();
        aiContextVar.put("name", "Notes");
        aiContextVar.put("fieldType", "Text");
        aiContextVar.put("metaData", metaData);

        Map<String, Object> varMap = new HashMap<>();
        varMap.put("aiContextVariable", aiContextVar);

        // When
        List<InputVariable> result = AgenticRequestConverter.parseInputVariables(List.of(varMap));

        // Then
        assertFalse(result.get(0).getAiContextVariable().isRequired(),
            "isRequired() must be false when metaData has required=false and top-level is absent");
    }

    @Test
    @DisplayName("parseAIContextVariable: neither top-level nor metaData required leaves isRequired() false")
    void parseInputVariables_noRequiredAnywhere_defaultsFalse() {

        // Given — no required flag anywhere
        Map<String, Object> aiContextVar = new HashMap<>();
        aiContextVar.put("name", "Optional Field");
        aiContextVar.put("fieldType", "Text");

        Map<String, Object> varMap = new HashMap<>();
        varMap.put("aiContextVariable", aiContextVar);

        // When
        List<InputVariable> result = AgenticRequestConverter.parseInputVariables(List.of(varMap));

        // Then
        assertFalse(result.get(0).getAiContextVariable().isRequired(),
            "isRequired() must default to false when required is absent everywhere");
    }

    // -------------------------------------------------------------------------
    // Mixed payload: some with sub-vars, some without
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("parseInputVariables: mixed payload — some variables with sub-vars, some without")
    void parseInputVariables_mixedPayload_handledCorrectly() {

        // Given — first variable: simple Text
        Map<String, Object> simpleAiCtx = new HashMap<>();
        simpleAiCtx.put("name", "greeting");
        simpleAiCtx.put("fieldType", "Text");

        Map<String, Object> simpleVarMap = new HashMap<>();
        simpleVarMap.put("aiContextVariable", simpleAiCtx);
        simpleVarMap.put("value", "Hello");

        // Second variable: Entity with sub-fields
        Map<String, Object> subField = new HashMap<>();
        subField.put("name", "street");
        subField.put("fieldType", "Text");

        Map<String, Object> entityAiCtx = new HashMap<>();
        entityAiCtx.put("name", "address");
        entityAiCtx.put("fieldType", "Entity");
        entityAiCtx.put("subAIContextVariables", List.of(subField));
        entityAiCtx.put("metaData", Map.of("format", "US"));

        Map<String, Object> entityVarMap = new HashMap<>();
        entityVarMap.put("aiContextVariable", entityAiCtx);

        // Third variable: flat structure (legacy)
        Map<String, Object> flatVarMap = new HashMap<>();
        flatVarMap.put("name", "legacyField");
        flatVarMap.put("type", "Number");
        flatVarMap.put("value", 99);

        // When
        List<InputVariable> result = AgenticRequestConverter.parseInputVariables(
            List.of(simpleVarMap, entityVarMap, flatVarMap));

        // Then
        assertEquals(3, result.size(), "All three input variables must be parsed");

        // Simple Text
        AIContextVariable greetingVar = result.get(0).getAiContextVariable();
        assertEquals("greeting", greetingVar.getName());
        assertEquals("Text", greetingVar.getType());
        assertTrue(greetingVar.getSubContextVariables().isEmpty());

        // Entity with sub-fields
        AIContextVariable addressVar = result.get(1).getAiContextVariable();
        assertEquals("address", addressVar.getName());
        assertEquals("Entity", addressVar.getType());
        assertEquals(1, addressVar.getSubContextVariables().size());
        assertEquals("street", addressVar.getSubContextVariables().get(0).getName());
        assertEquals("US", addressVar.getMetaData().get("format"));

        // Legacy flat structure
        AIContextVariable legacyVar = result.get(2).getAiContextVariable();
        assertEquals("legacyField", legacyVar.getName());
        assertEquals("Number", legacyVar.getType());
        assertEquals(99, result.get(2).getValue());
    }

    // -------------------------------------------------------------------------
    // convertToolDefinitions: parameter metadata deserialization
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("convertToolDefinitions: parameter with metadata options deserialized")
    void convertToolDefinitions_parameterWithMetadata_optionsDeserialized() {

        // Given — PickOne parameter with metadata containing options
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("options", List.of("Low", "Medium", "High"));

        Map<String, Object> paramMap = new HashMap<>();
        paramMap.put("pDefName", "priority");
        paramMap.put("pDefType", "PickOne");
        paramMap.put("description", "Ticket priority");
        paramMap.put("required", Boolean.TRUE);
        paramMap.put("metadata", metadata);

        Map<String, Object> toolMap = new HashMap<>();
        toolMap.put("agenticToolName", "create_ticket");
        toolMap.put("description", "Creates a support ticket");
        toolMap.put("agenticToolType", "standard");
        toolMap.put("isHelperTool", Boolean.FALSE);
        toolMap.put("agenticToolParams", List.of(paramMap));

        // When
        List<AgenticToolDefinition> result = AgenticRequestConverter.convertToolDefinitions(List.of(toolMap));

        // Then
        assertNotNull(result);
        assertEquals(1, result.size());

        AgenticToolDefinition.ParameterDefinition param = result.get(0).parameters().get(0);
        assertEquals("priority", param.pDefName());
        assertEquals("PickOne", param.pDefType());
        assertTrue(param.required());

        assertNotNull(param.metadata(), "metadata must not be null when provided in payload");
        assertTrue(param.metadata().containsKey("options"), "metadata must contain 'options' key");

        @SuppressWarnings("unchecked")
        List<String> options = (List<String>) param.metadata().get("options");
        assertEquals(3, options.size());
        assertEquals("Low", options.get(0));
        assertEquals("Medium", options.get(1));
        assertEquals("High", options.get(2));
    }

    @Test
    @DisplayName("convertToolDefinitions: parameter without metadata has graceful null")
    void convertToolDefinitions_parameterWithoutMetadata_gracefulNull() {

        // Given — old-style parameter without metadata key
        Map<String, Object> paramMap = new HashMap<>();
        paramMap.put("pDefName", "customerId");
        paramMap.put("pDefType", "String");
        paramMap.put("description", "The customer identifier");
        paramMap.put("required", Boolean.TRUE);
        // No "metadata" key at all

        Map<String, Object> toolMap = new HashMap<>();
        toolMap.put("agenticToolName", "lookup_customer");
        toolMap.put("description", "Looks up a customer");
        toolMap.put("agenticToolType", "lookup");
        toolMap.put("isHelperTool", Boolean.FALSE);
        toolMap.put("agenticToolParams", List.of(paramMap));

        // When
        List<AgenticToolDefinition> result = AgenticRequestConverter.convertToolDefinitions(List.of(toolMap));

        // Then
        assertNotNull(result);
        assertEquals(1, result.size());

        AgenticToolDefinition.ParameterDefinition param = result.get(0).parameters().get(0);
        assertEquals("customerId", param.pDefName());
        assertNull(param.metadata(), "metadata must be null when absent from payload");
    }

    @Test
    @DisplayName("convertToolDefinitions: parameter with range metadata preserved")
    void convertToolDefinitions_parameterWithRangeMetadata() {

        // Given — Number parameter with minValue/maxValue
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("minValue", 1);
        metadata.put("maxValue", 13);

        Map<String, Object> paramMap = new HashMap<>();
        paramMap.put("pDefName", "monthNumber");
        paramMap.put("pDefType", "Number");
        paramMap.put("description", "Month of the year");
        paramMap.put("required", Boolean.TRUE);
        paramMap.put("metadata", metadata);

        Map<String, Object> toolMap = new HashMap<>();
        toolMap.put("agenticToolName", "set_month");
        toolMap.put("description", "Sets the month");
        toolMap.put("agenticToolType", "standard");
        toolMap.put("agenticToolParams", List.of(paramMap));

        // When
        List<AgenticToolDefinition> result = AgenticRequestConverter.convertToolDefinitions(List.of(toolMap));

        // Then
        AgenticToolDefinition.ParameterDefinition param = result.get(0).parameters().get(0);
        assertNotNull(param.metadata(), "metadata must not be null");
        assertEquals(1, param.metadata().get("minValue"));
        assertEquals(13, param.metadata().get("maxValue"));
    }

    // -------------------------------------------------------------------------
    // ParameterDefinition toString rendering with metadata
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("ParameterDefinition toString includes options when metadata has options")
    void parameterDefinition_toString_includesOptions() {

        // Given
        Map<String, Object> metadata = Map.of("options", List.of("Red", "Green", "Blue"));
        AgenticToolDefinition.ParameterDefinition param =
                new AgenticToolDefinition.ParameterDefinition("color", "PickOne", "Favorite color", true, metadata);

        // When
        String result = param.toString();

        // Then
        assertTrue(result.contains("pDefName=color"), "toString must contain pDefName");
        assertTrue(result.contains("pDefType=PickOne"), "toString must contain pDefType");
        assertTrue(result.contains("required=true"), "toString must contain required");
        assertTrue(result.contains("options="), "toString must contain options key");
        assertTrue(result.contains("Red"), "toString must contain option value Red");
        assertTrue(result.contains("Green"), "toString must contain option value Green");
        assertTrue(result.contains("Blue"), "toString must contain option value Blue");
    }

    @Test
    @DisplayName("ParameterDefinition toString includes range when metadata has minValue/maxValue")
    void parameterDefinition_toString_includesRange() {

        // Given
        Map<String, Object> metadata = Map.of("minValue", 0, "maxValue", 100);
        AgenticToolDefinition.ParameterDefinition param =
                new AgenticToolDefinition.ParameterDefinition("score", "Number", "A score", true, metadata);

        // When
        String result = param.toString();

        // Then
        assertTrue(result.contains("range=0-100"), "toString must contain range=0-100");
    }

    @Test
    @DisplayName("ParameterDefinition toString includes length when metadata has minLength/maxLength")
    void parameterDefinition_toString_includesLength() {

        // Given
        Map<String, Object> metadata = Map.of("minLength", 5, "maxLength", 255);
        AgenticToolDefinition.ParameterDefinition param =
                new AgenticToolDefinition.ParameterDefinition("message", "Text", "A message", false, metadata);

        // When
        String result = param.toString();

        // Then
        assertTrue(result.contains("length=5-255"), "toString must contain length=5-255");
    }

    @Test
    @DisplayName("ParameterDefinition toString includes select range for PickMany")
    void parameterDefinition_toString_includesSelectRange() {

        // Given
        Map<String, Object> metadata = Map.of(
                "options", List.of("A", "B", "C", "D"),
                "minSelection", 1,
                "maxSelection", 3);
        AgenticToolDefinition.ParameterDefinition param =
                new AgenticToolDefinition.ParameterDefinition("tags", "PickMany", "Select tags", true, metadata);

        // When
        String result = param.toString();

        // Then
        assertTrue(result.contains("options="), "toString must contain options");
        assertTrue(result.contains("select=1-3"), "toString must contain select=1-3");
    }

    @Test
    @DisplayName("ParameterDefinition toString shows 'dates: future only' when allowPast=false, allowFuture=true")
    void parameterDefinition_toString_includesFutureDatesOnly() {

        // Given — allowPast=false, allowFuture=true (allowToday absent => defaults false)
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("allowPast", Boolean.FALSE);
        metadata.put("allowFuture", Boolean.TRUE);
        AgenticToolDefinition.ParameterDefinition param =
                new AgenticToolDefinition.ParameterDefinition("dueDate", "Date", "Due date", true, metadata);

        // When
        String result = param.toString();

        // Then
        assertTrue(result.contains("dates: future only"), "toString must contain 'dates: future only'");
        assertFalse(result.contains("past"), "toString must not mention 'past' in the dates fragment");
    }

    @Test
    @DisplayName("ParameterDefinition toString shows 'dates: past only' when allowFuture=false, allowPast=true")
    void parameterDefinition_toString_includesPastDatesOnly() {

        // Given — allowFuture=false, allowPast=true (allowToday absent => defaults false)
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("allowFuture", Boolean.FALSE);
        metadata.put("allowPast", Boolean.TRUE);
        AgenticToolDefinition.ParameterDefinition param =
                new AgenticToolDefinition.ParameterDefinition("birthDate", "Date", "Birth date", false, metadata);

        // When
        String result = param.toString();

        // Then
        assertTrue(result.contains("dates: past only"), "toString must contain 'dates: past only'");
        assertFalse(result.contains("future"), "toString must not mention 'future' in the dates fragment");
    }

    @Test
    @DisplayName("ParameterDefinition toString shows 'dates: today/future only' when allowPast=false, allowToday=true, allowFuture=true (the bug case)")
    void parameterDefinition_toString_allowTodayWithFuture() {

        // Given — the critical bug scenario: today IS allowed but past is not
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("allowPast", Boolean.FALSE);
        metadata.put("allowToday", Boolean.TRUE);
        metadata.put("allowFuture", Boolean.TRUE);
        AgenticToolDefinition.ParameterDefinition param =
                new AgenticToolDefinition.ParameterDefinition("startDate", "Date", "Start date", true, metadata);

        // When
        String result = param.toString();

        // Then — must include today, must NOT say "future only"
        assertTrue(result.contains("dates: today/future only"),
                "toString must contain 'dates: today/future only' but was: " + result);
    }

    @Test
    @DisplayName("ParameterDefinition toString shows 'dates: past/today only' when allowFuture=false, allowToday=true, allowPast=true")
    void parameterDefinition_toString_allowTodayWithPast() {

        // Given
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("allowPast", Boolean.TRUE);
        metadata.put("allowToday", Boolean.TRUE);
        metadata.put("allowFuture", Boolean.FALSE);
        AgenticToolDefinition.ParameterDefinition param =
                new AgenticToolDefinition.ParameterDefinition("endDate", "Date", "End date", false, metadata);

        // When
        String result = param.toString();

        // Then
        assertTrue(result.contains("dates: past/today only"),
                "toString must contain 'dates: past/today only' but was: " + result);
    }

    @Test
    @DisplayName("ParameterDefinition toString omits date constraint when all three flags are true")
    void parameterDefinition_toString_omitsDateConstraintWhenAllAllowed() {

        // Given — all three are allowed, no restriction needed
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("allowPast", Boolean.TRUE);
        metadata.put("allowToday", Boolean.TRUE);
        metadata.put("allowFuture", Boolean.TRUE);
        AgenticToolDefinition.ParameterDefinition param =
                new AgenticToolDefinition.ParameterDefinition("anyDate", "Date", "Any date", false, metadata);

        // When
        String result = param.toString();

        // Then — no "dates:" fragment at all
        assertFalse(result.contains("dates:"),
                "toString must not contain date constraint when all periods allowed, but was: " + result);
    }

    @Test
    @DisplayName("ParameterDefinition toString includes formatPattern")
    void parameterDefinition_toString_includesFormatPattern() {

        // Given
        Map<String, Object> metadata = Map.of("formatPattern", "##0.00");
        AgenticToolDefinition.ParameterDefinition param =
                new AgenticToolDefinition.ParameterDefinition("price", "Number", "Item price", true, metadata);

        // When
        String result = param.toString();

        // Then
        assertTrue(result.contains("pattern=##0.00"),
                "toString must contain 'pattern=##0.00' but was: " + result);
    }

    @Test
    @DisplayName("ParameterDefinition toString suppresses dateFormat (PROMPT_HIDDEN_KEYS)")
    void parameterDefinition_toString_suppressesDateFormat() {

        // Given
        Map<String, Object> metadata = Map.of("dateFormat", "DD-MM-YYYY");
        AgenticToolDefinition.ParameterDefinition param =
                new AgenticToolDefinition.ParameterDefinition("eventDate", "Date", "Event date", true, metadata);

        // When
        String result = param.toString();

        // Then — dateFormat is suppressed from prompt to avoid conflicting with wire format guidance
        assertFalse(result.contains("dateFormat="),
                "toString must NOT contain 'dateFormat=' (hidden from prompt) but was: " + result);
    }

    @Test
    @DisplayName("ParameterDefinition toString includes maxDurationInDays")
    void parameterDefinition_toString_includesMaxDurationInDays() {

        // Given
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("allowPast", Boolean.FALSE);
        metadata.put("allowFuture", Boolean.TRUE);
        metadata.put("maxDurationInDays", 30);
        AgenticToolDefinition.ParameterDefinition param =
                new AgenticToolDefinition.ParameterDefinition("dateRange", "DateRange", "Select range", true, metadata);

        // When
        String result = param.toString();

        // Then
        assertTrue(result.contains("maxDuration=30 days"),
                "toString must contain 'maxDuration=30 days' but was: " + result);
    }

    @Test
    @DisplayName("ParameterDefinition toString renders date constraints and maxDuration but suppresses dateFormat")
    void parameterDefinition_toString_rendersDateMetadataCorrectly() {

        // Given — a date parameter with all date-related keys
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("dateFormat", "DD-MM-YYYY");
        metadata.put("allowPast", Boolean.FALSE);
        metadata.put("allowToday", Boolean.TRUE);
        metadata.put("allowFuture", Boolean.TRUE);
        metadata.put("maxDurationInDays", 14);
        AgenticToolDefinition.ParameterDefinition param =
                new AgenticToolDefinition.ParameterDefinition("tripDate", "Date", "Trip date", true, metadata);

        // When
        String result = param.toString();

        // Then — dateFormat suppressed, date constraints and maxDuration present
        assertFalse(result.contains("dateFormat="), "dateFormat must be hidden from prompt");
        assertTrue(result.contains("dates: today/future only"), "must contain date constraint");
        assertTrue(result.contains("maxDuration=14 days"), "must contain maxDuration");

        // allowPast/allowToday/allowFuture should not appear as raw keys (they're rendered via appendDateConstraints)
        assertFalse(result.contains("allowPast="), "allowPast must not appear as raw key");
        assertFalse(result.contains("allowToday="), "allowToday must not appear as raw key");
        assertFalse(result.contains("allowFuture="), "allowFuture must not appear as raw key");
    }

    @Test
    @DisplayName("ParameterDefinition toString omits metadata section when metadata is null")
    void parameterDefinition_toString_omitsMetadataWhenNull() {

        // Given — backward-compatible constructor (no metadata)
        AgenticToolDefinition.ParameterDefinition param =
                new AgenticToolDefinition.ParameterDefinition("name", "Text", "A name", true);

        // When
        String result = param.toString();

        // Then
        assertTrue(result.contains("pDefName=name"), "toString must contain pDefName");
        assertTrue(result.contains("required=true"), "toString must contain required");
        assertFalse(result.contains("options="), "toString must not contain options when metadata is null");
        assertFalse(result.contains("range="), "toString must not contain range when metadata is null");
        assertFalse(result.contains("length="), "toString must not contain length when metadata is null");
        assertFalse(result.contains("select="), "toString must not contain select when metadata is null");
    }

    @Test
    @DisplayName("ParameterDefinition toString omits metadata section when metadata is empty map")
    void parameterDefinition_toString_omitsMetadataWhenEmpty() {

        // Given
        AgenticToolDefinition.ParameterDefinition param =
                new AgenticToolDefinition.ParameterDefinition("name", "Text", "A name", true, Map.of());

        // When
        String result = param.toString();

        // Then
        assertFalse(result.contains("options="), "toString must not contain options when metadata is empty");
        assertFalse(result.contains("range="), "toString must not contain range when metadata is empty");
    }

    @Test
    @DisplayName("ParameterDefinition toString handles partial range (only minValue, no maxValue)")
    void parameterDefinition_toString_handlesPartialRange() {

        // Given — only minValue, no maxValue
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("minValue", 0);

        AgenticToolDefinition.ParameterDefinition param =
                new AgenticToolDefinition.ParameterDefinition("amount", "Number", "Amount", true, metadata);

        // When
        String result = param.toString();

        // Then
        assertTrue(result.contains("range=0-any"), "toString must show range=0-any for partial range");
    }

    @Test
    @DisplayName("ParameterDefinition toString includes structure when metadata has subFields")
    void parameterDefinition_toString_includesSubFieldStructure() {

        Map<String, Object> subField1 = Map.of("name", "firstName", "type", "Text");
        Map<String, Object> subField2 = Map.of("name", "age", "type", "Number", "required", true);
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("subFields", List.of(subField1, subField2));
        AgenticToolDefinition.ParameterDefinition param =
                new AgenticToolDefinition.ParameterDefinition("person", "Entity", "A person", true, metadata);

        String result = param.toString();

        assertTrue(result.contains("structure="),
                "toString must include structure from subFields, got: " + result);
        assertTrue(result.contains("firstName: Text"),
                "Must include sub-field name and type, got: " + result);
        assertTrue(result.contains("age: Number"),
                "Must include all sub-fields, got: " + result);
    }
}
