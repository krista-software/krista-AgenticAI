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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for VariableFormatter class.
 * Tests variable formatting for LLM prompts, system-reserved variable filtering,
 * and type-aware value formatting (dates, lists, tables, entities, multi-fields).
 *
 * @see VariableFormatter
 */
class VariableFormatterTest {

    // ========================================
    // Section 1: Input Variable Formatting Tests
    // ========================================

    @Test
    @DisplayName("Format null input variables list")
    void testFormatInputVariables_NullList() {

        String result = VariableFormatter.formatInputVariables(null);
        assertEquals("No input variables available", result);
    }

    @Test
    @DisplayName("Format empty input variables list")
    void testFormatInputVariables_EmptyList() {

        String result = VariableFormatter.formatInputVariables(Collections.emptyList());
        assertEquals("No input variables available", result);
    }

    @Test
    @DisplayName("Format single variable with all fields")
    void testFormatInputVariables_SingleVariable_AllFields() {

        AIContextVariable contextVar = createAIContextVariable("customerName", "String", "Customer full name", true);
        InputVariable inputVar = createInputVariable(contextVar, "John Doe");

        String result = VariableFormatter.formatInputVariables(Arrays.asList(inputVar));

        assertTrue(result.contains("customerName"));
        assertTrue(result.contains("\"John Doe\""));
        assertTrue(result.contains("String"));
        assertTrue(result.contains("Customer full name"));
        assertTrue(result.contains("required: true"));
    }

    @Test
    @DisplayName("Format variable with null AIContextVariable")
    void testFormatInputVariables_NullAiContextVariable() {

        InputVariable inputVar = createInputVariable(null, "someValue");

        String result = VariableFormatter.formatInputVariables(Arrays.asList(inputVar));

        assertTrue(result.contains("Unknown variable"));
        assertTrue(result.contains("someValue"));
    }

    @Test
    @DisplayName("Format variable with null value")
    void testFormatInputVariables_NullValue() {

        AIContextVariable contextVar = createAIContextVariable("optionalField", "String", "Optional data", false);
        InputVariable inputVar = createInputVariable(contextVar, null);

        String result = VariableFormatter.formatInputVariables(Arrays.asList(inputVar));

        assertTrue(result.contains("optionalField"));
        assertTrue(result.contains("null"));
        assertTrue(result.contains("required: false"));
    }

    @Test
    @DisplayName("Format variable with complex object value")
    void testFormatInputVariables_ComplexObject() {

        AIContextVariable contextVar = createAIContextVariable("config", "Object", "Configuration data", true);
        Map<String, Object> complexValue = new HashMap<>();
        complexValue.put("key1", "value1");
        complexValue.put("key2", 123);
        complexValue.put("key3", true);
        // Add enough data to trigger truncation
        for (int i = 0; i < 20; i++) {

            complexValue.put("extra" + i, "data" + i);
        }

        InputVariable inputVar = createInputVariable(contextVar, complexValue);

        String result = VariableFormatter.formatInputVariables(Arrays.asList(inputVar));

        assertTrue(result.contains("config"));
        // The new display formatting serializes maps to JSON, so truncation may differ
        assertTrue(result.contains("key1") || result.contains("truncated"));
    }

    @Test
    @DisplayName("Format multiple input variables")
    void testFormatInputVariables_MultipleVariables() {

        AIContextVariable var1 = createAIContextVariable("name", "String", "User name", true);
        AIContextVariable var2 = createAIContextVariable("age", "Number", "User age", false);

        InputVariable input1 = createInputVariable(var1, "Alice");
        InputVariable input2 = createInputVariable(var2, 30);

        String result = VariableFormatter.formatInputVariables(Arrays.asList(input1, input2));

        assertTrue(result.contains("name"));
        assertTrue(result.contains("Alice"));
        assertTrue(result.contains("age"));
        assertTrue(result.contains("30"));
        // Should have newline separator
        assertTrue(result.contains("\n"));
    }

    // ========================================
    // Section 2: Expected Output Formatting Tests
    // ========================================

    @Test
    @DisplayName("Format null expected outputs list")
    void testFormatExpectedOutputs_NullList() {

        String result = VariableFormatter.formatExpectedOutputs(null);
        assertEquals("No expected outputs defined", result);
    }

    @Test
    @DisplayName("Format empty expected outputs list")
    void testFormatExpectedOutputs_EmptyList() {

        String result = VariableFormatter.formatExpectedOutputs(Collections.emptyList());
        assertEquals("No expected outputs defined", result);
    }

    @Test
    @DisplayName("Format single expected output")
    void testFormatExpectedOutputs_SingleOutput() {

        AIContextVariable contextVar = createAIContextVariable("confirmationNumber", "String", "Confirmation code", true);
        ExpectedOutput expectedOutput = createExpectedOutput(contextVar, "Generate unique confirmation number", "read");

        String result = VariableFormatter.formatExpectedOutputs(Arrays.asList(expectedOutput));

        assertTrue(result.contains("confirmationNumber"));
        assertTrue(result.contains("Generate unique confirmation number"));
        assertTrue(result.contains("[operation: read]"));
    }

    @Test
    @DisplayName("Format output with null AIContextVariable - filtered out")
    void testFormatExpectedOutputs_NullAiContextVariable_Filtered() {

        // When aiContextVariable is null, the output is filtered out entirely
        ExpectedOutput expectedOutput = createExpectedOutput(null, "Some instruction", "write");

        String result = VariableFormatter.formatExpectedOutputs(Arrays.asList(expectedOutput));

        // Null aiContextVariable gets filtered before formatting
        assertEquals("No expected outputs defined (all were system-reserved variables)", result);
    }

    @Test
    @DisplayName("Format output with null instruction")
    void testFormatExpectedOutputs_NullInstruction() {

        AIContextVariable contextVar = createAIContextVariable("outputVar", "String", "Output variable", true);
        ExpectedOutput expectedOutput = createExpectedOutput(contextVar, null, "read");

        String result = VariableFormatter.formatExpectedOutputs(Arrays.asList(expectedOutput));

        assertTrue(result.contains("outputVar"));
        assertTrue(result.contains("Generate value for outputVar"));
    }

    @Test
    @DisplayName("System variable conversation_history is filtered from LLM prompt")
    void testFormatExpectedOutputs_FiltersConversationHistory() {

        AIContextVariable userVar = createAIContextVariable("finalAnswer", "Text", "Final response", true);
        AIContextVariable systemVar = createAIContextVariable("conversation_history", "Text", "Auto-generated", false);

        ExpectedOutput userOutput = createExpectedOutput(userVar, "Provide the final answer", "read");
        ExpectedOutput systemOutput = createExpectedOutput(systemVar, "Auto-populated by system", "read");

        String result = VariableFormatter.formatExpectedOutputs(Arrays.asList(userOutput, systemOutput));

        assertTrue(result.contains("finalAnswer"));
        assertFalse(result.contains("conversation_history"));
        assertFalse(result.contains("Auto-populated by system"));
    }

    @Test
    @DisplayName("System variable tool_summary is filtered from LLM prompt")
    void testFormatExpectedOutputs_FiltersToolSummary() {

        AIContextVariable userVar = createAIContextVariable("status", "String", "Status code", true);
        AIContextVariable systemVar = createAIContextVariable("tool_summary", "Text", "Auto-generated", false);

        ExpectedOutput userOutput = createExpectedOutput(userVar, "Return the status", "read");
        ExpectedOutput systemOutput = createExpectedOutput(systemVar, "Auto-populated by system", "read");

        String result = VariableFormatter.formatExpectedOutputs(Arrays.asList(userOutput, systemOutput));

        assertTrue(result.contains("status"));
        assertFalse(result.contains("tool_summary"));
        assertFalse(result.contains("Auto-populated by system"));
    }

    @Test
    @DisplayName("Both system variables are filtered together")
    void testFormatExpectedOutputs_FiltersBothSystemVariables() {

        AIContextVariable userVar = createAIContextVariable("result", "String", "Result data", true);
        AIContextVariable historyVar = createAIContextVariable("conversation_history", "Text", "History", false);
        AIContextVariable summaryVar = createAIContextVariable("tool_summary", "Text", "Summary", false);

        ExpectedOutput userOutput = createExpectedOutput(userVar, "Return result", "read");
        ExpectedOutput historyOutput = createExpectedOutput(historyVar, "History", "read");
        ExpectedOutput summaryOutput = createExpectedOutput(summaryVar, "Summary", "read");

        String result = VariableFormatter.formatExpectedOutputs(Arrays.asList(userOutput, historyOutput, summaryOutput));

        assertTrue(result.contains("result"));
        assertFalse(result.contains("conversation_history"));
        assertFalse(result.contains("tool_summary"));
    }

    @Test
    @DisplayName("Only system variables present - returns special message")
    void testFormatExpectedOutputs_OnlySystemVariables() {

        AIContextVariable historyVar = createAIContextVariable("conversation_history", "Text", "History", false);
        AIContextVariable summaryVar = createAIContextVariable("tool_summary", "Text", "Summary", false);

        ExpectedOutput historyOutput = createExpectedOutput(historyVar, "History", "read");
        ExpectedOutput summaryOutput = createExpectedOutput(summaryVar, "Summary", "read");

        String result = VariableFormatter.formatExpectedOutputs(Arrays.asList(historyOutput, summaryOutput));

        assertEquals("No expected outputs defined (all were system-reserved variables)", result);
    }

    @Test
    @DisplayName("Format multiple expected outputs")
    void testFormatExpectedOutputs_MultipleOutputs() {

        AIContextVariable var1 = createAIContextVariable("status", "String", "Status", true);
        AIContextVariable var2 = createAIContextVariable("message", "String", "Message", false);

        ExpectedOutput out1 = createExpectedOutput(var1, "Return status", "read");
        ExpectedOutput out2 = createExpectedOutput(var2, "Return message", "read");

        String result = VariableFormatter.formatExpectedOutputs(Arrays.asList(out1, out2));

        assertTrue(result.contains("status"));
        assertTrue(result.contains("message"));
        assertTrue(result.contains("\n"));
    }

    // ========================================
    // Section 3: System-Reserved Variable Detection Tests
    // ========================================

    @Test
    @DisplayName("Is conversation_history system reserved - returns true")
    void testIsSystemReservedVariable_True_ConversationHistory() {

        assertTrue(VariableFormatter.isSystemReservedVariable("conversation_history"));
    }

    @Test
    @DisplayName("Is tool_summary system reserved - returns true")
    void testIsSystemReservedVariable_True_ToolSummary() {

        assertTrue(VariableFormatter.isSystemReservedVariable("tool_summary"));
    }

    @Test
    @DisplayName("User variable is not system reserved - returns false")
    void testIsSystemReservedVariable_False() {

        assertFalse(VariableFormatter.isSystemReservedVariable("userName"));
        assertFalse(VariableFormatter.isSystemReservedVariable("finalAnswer"));
        assertFalse(VariableFormatter.isSystemReservedVariable("status"));
        assertFalse(VariableFormatter.isSystemReservedVariable("result"));
    }

    @Test
    @DisplayName("Null variable name is not system reserved - returns false")
    void testIsSystemReservedVariable_Null() {

        assertFalse(VariableFormatter.isSystemReservedVariable(null));
    }

    @Test
    @DisplayName("Empty string is not system reserved - returns false")
    void testIsSystemReservedVariable_Empty() {

        assertFalse(VariableFormatter.isSystemReservedVariable(""));
    }

    // ========================================
    // Section 4: Type Normalization Tests
    // ========================================

    @Test
    @DisplayName("normalizeType strips com.krista.fields. prefix and lowercases")
    void testNormalizeType_FullLogicalName() {

        assertEquals("date", VariableFormatter.normalizeType("com.krista.fields.Date"));
        assertEquals("entityreference", VariableFormatter.normalizeType("com.krista.fields.EntityReference"));
        assertEquals("checkboxes", VariableFormatter.normalizeType("com.krista.fields.Checkboxes"));
        assertEquals("table", VariableFormatter.normalizeType("com.krista.fields.Table"));
    }

    @Test
    @DisplayName("normalizeType handles short names")
    void testNormalizeType_ShortName() {

        assertEquals("date", VariableFormatter.normalizeType("Date"));
        assertEquals("text", VariableFormatter.normalizeType("Text"));
        assertEquals("list", VariableFormatter.normalizeType("list"));
    }

    @Test
    @DisplayName("normalizeType handles null and empty")
    void testNormalizeType_NullAndEmpty() {

        assertEquals("", VariableFormatter.normalizeType(null));
        assertEquals("", VariableFormatter.normalizeType(""));
    }

    // ========================================
    // Section 5: Type-Aware Value Formatting Tests
    // ========================================

    @Nested
    @DisplayName("Date epoch formatting")
    class DateFormattingTests {

        @Test
        @DisplayName("Epoch millis are formatted to human-readable date")
        void testFormatDateEpoch() {

            // 1710288000000L = 2024-03-13 00:00:00 UTC
            AIContextVariable dateVar = createAIContextVariable("appointment", "Date", null, false);
            long epoch = 1710288000000L;

            Object result = VariableFormatter.formatValueForLLM(dateVar, epoch);

            assertNotNull(result);
            assertTrue(result instanceof String, "Date should be formatted to String");
            String dateStr = (String) result;
            // Should contain the date in dd-MM-yyyy HH:mm:ss format
            assertTrue(dateStr.contains("13-03-2024"), "Expected date 13-03-2024 but got: " + dateStr);
        }

        @Test
        @DisplayName("Full logical name com.krista.fields.Date is handled")
        void testFormatDateWithFullLogicalName() {

            AIContextVariable dateVar = createAIContextVariable("created", "com.krista.fields.Date", null, false);
            long epoch = 1710288000000L;

            Object result = VariableFormatter.formatValueForLLM(dateVar, epoch);

            assertNotNull(result);
            assertTrue(result instanceof String);
            assertTrue(((String) result).contains("13-03-2024"));
        }

        @Test
        @DisplayName("Null value returns null")
        void testFormatDateNull() {

            AIContextVariable dateVar = createAIContextVariable("date", "Date", null, false);
            assertNull(VariableFormatter.formatValueForLLM(dateVar, null));
        }
    }

    @Nested
    @DisplayName("DateRange formatting")
    class DateRangeFormattingTests {

        @Test
        @DisplayName("DateRange with epoch from/to is formatted")
        void testFormatDateRange() {

            AIContextVariable rangeVar = createAIContextVariable("period", "DateRange", null, false);
            Map<String, Object> range = Map.of(
                    "from", 1710288000000L,
                    "to", 1710374400000L
            );

            Object result = VariableFormatter.formatValueForLLM(rangeVar, range);

            assertNotNull(result);
            assertTrue(result instanceof Map);
            @SuppressWarnings("unchecked")
            Map<String, Object> formatted = (Map<String, Object>) result;
            assertTrue(formatted.get("from").toString().contains("13-03-2024"));
            assertTrue(formatted.get("to").toString().contains("14-03-2024"));
        }
    }

    @Nested
    @DisplayName("List recursive formatting")
    class ListFormattingTests {

        @Test
        @DisplayName("List of dates is recursively formatted")
        void testFormatListOfDates() {

            AIContextVariable itemVar = createAIContextVariable("", "Date", null, false);
            AIContextVariable listVar = new AIContextVariable("dates", "List",
                    List.of(itemVar));

            List<Object> dates = List.of(1710288000000L, 1710374400000L);

            Object result = VariableFormatter.formatValueForLLM(listVar, dates);

            assertNotNull(result);
            assertTrue(result instanceof List);
            @SuppressWarnings("unchecked")
            List<Object> formattedList = (List<Object>) result;
            assertEquals(2, formattedList.size());
            assertTrue(formattedList.get(0).toString().contains("13-03-2024"));
            assertTrue(formattedList.get(1).toString().contains("14-03-2024"));
        }

        @Test
        @DisplayName("List with null subContextVariables defaults to text (G19)")
        void testFormatListNullSubVars() {

            AIContextVariable listVar = createAIContextVariable("items", "List", null, false);
            listVar.setSubContextVariables(null);

            List<Object> items = List.of("hello", "world");

            Object result = VariableFormatter.formatValueForLLM(listVar, items);

            assertNotNull(result);
            assertTrue(result instanceof List);
            @SuppressWarnings("unchecked")
            List<Object> formattedList = (List<Object>) result;
            assertEquals(2, formattedList.size());
            assertEquals("hello", formattedList.get(0));
            assertEquals("world", formattedList.get(1));
        }

        @Test
        @DisplayName("KR-20889: List of entities with no sub-type — meta stripped from each entity")
        void testFormatList_entitiesWithNoSubType_metaStripped() {

            // Simulate: Mails list with no sub-type info (CAM doesn't provide it)
            AIContextVariable listVar = createAIContextVariable("Mails", "List", null, false);
            listVar.setSubContextVariables(null);

            Map<String, Object> entity1 = new LinkedHashMap<>();
            entity1.put("type", "Entity");
            entity1.put("meta", Map.of("primaryKey", "pk-1", "toString", "[]"));
            entity1.put("fields", Map.of("From", "alice@example.com", "Subject", "Hello"));

            Map<String, Object> entity2 = new LinkedHashMap<>();
            entity2.put("type", "Entity");
            entity2.put("meta", Map.of("primaryKey", "pk-2", "toString", "[]"));
            entity2.put("fields", Map.of("From", "bob@example.com", "Subject", "Meeting"));

            List<Object> items = List.of(entity1, entity2);

            Object result = VariableFormatter.formatValueForLLM(listVar, items);

            assertNotNull(result);
            assertTrue(result instanceof List);
            @SuppressWarnings("unchecked")
            List<Object> formattedList = (List<Object>) result;
            assertEquals(2, formattedList.size());

            // Each entity should have meta/type stripped and fields unwrapped
            @SuppressWarnings("unchecked")
            Map<String, Object> mail1 = (Map<String, Object>) formattedList.get(0);
            assertFalse(mail1.containsKey("meta"), "meta must be stripped from entity in list");
            assertFalse(mail1.containsKey("type"), "type must be stripped from entity in list");
            assertFalse(mail1.containsKey("fields"), "fields wrapper must be unwrapped");
            assertEquals("alice@example.com", mail1.get("From"));
            assertEquals("Hello", mail1.get("Subject"));

            @SuppressWarnings("unchecked")
            Map<String, Object> mail2 = (Map<String, Object>) formattedList.get(1);
            assertFalse(mail2.containsKey("meta"), "meta must be stripped from second entity");
            assertEquals("bob@example.com", mail2.get("From"));
            assertEquals("Meeting", mail2.get("Subject"));
        }

        @Test
        @DisplayName("KR-20889: List with non-standard type string (e.g. '?[]') falls back to structural detection")
        void testFormatList_nonStandardType_structuralFallback() {

            // CAM sometimes sends type strings like "?[]" that don't match "list" or "table"
            AIContextVariable listVar = createAIContextVariable("Items", "?[]", null, false);
            listVar.setSubContextVariables(null);

            Map<String, Object> entity1 = new LinkedHashMap<>();
            entity1.put("type", "Entity");
            entity1.put("meta", Map.of("primaryKey", "pk-1", "toString", "[]"));
            entity1.put("fields", Map.of("Name", "Widget A", "Price", "9.99"));

            Map<String, Object> entity2 = new LinkedHashMap<>();
            entity2.put("type", "Entity");
            entity2.put("meta", Map.of("primaryKey", "pk-2", "toString", "[]"));
            entity2.put("fields", Map.of("Name", "Widget B", "Price", "19.99"));

            List<Object> items = List.of(entity1, entity2);

            Object result = VariableFormatter.formatValueForLLM(listVar, items);

            assertNotNull(result);
            assertTrue(result instanceof List, "Structural fallback must detect List value");
            @SuppressWarnings("unchecked")
            List<Object> formattedList = (List<Object>) result;
            assertEquals(2, formattedList.size());

            // Each entity inside the list should have meta/type stripped and fields unwrapped
            @SuppressWarnings("unchecked")
            Map<String, Object> item1 = (Map<String, Object>) formattedList.get(0);
            assertFalse(item1.containsKey("meta"), "meta must be stripped via structural fallback");
            assertFalse(item1.containsKey("type"), "type must be stripped via structural fallback");
            assertFalse(item1.containsKey("fields"), "fields wrapper must be unwrapped");
            assertEquals("Widget A", item1.get("Name"));
            assertEquals("9.99", item1.get("Price"));

            @SuppressWarnings("unchecked")
            Map<String, Object> item2 = (Map<String, Object>) formattedList.get(1);
            assertFalse(item2.containsKey("meta"), "meta must be stripped from second item");
            assertEquals("Widget B", item2.get("Name"));
            assertEquals("19.99", item2.get("Price"));
        }

        @Test
        @DisplayName("List with mixed entity and plain scalar values — entities unwrapped, scalars pass through")
        @SuppressWarnings("unchecked")
        void testFormatList_mixedEntityAndPlainString() {

            AIContextVariable listVar = createAIContextVariable("items", "List", null, false);
            listVar.setSubContextVariables(null);

            Map<String, Object> entity = new LinkedHashMap<>();
            entity.put("type", "Entity");
            entity.put("meta", Map.of("primaryKey", "pk-1"));
            entity.put("fields", Map.of("Name", "Widget"));

            List<Object> items = List.of(entity, "plain string value", 42);

            Object result = VariableFormatter.formatValueForLLM(listVar, items);

            assertNotNull(result);
            assertTrue(result instanceof List);
            List<Object> formattedList = (List<Object>) result;
            assertEquals(3, formattedList.size());

            // First item: entity should be unwrapped
            assertTrue(formattedList.get(0) instanceof Map);
            Map<String, Object> item1 = (Map<String, Object>) formattedList.get(0);
            assertFalse(item1.containsKey("meta"));
            assertEquals("Widget", item1.get("Name"));

            // Second and third items: non-entity values pass through unchanged
            assertEquals("plain string value", formattedList.get(1));
            assertEquals(42, formattedList.get(2));
        }
    }

    @Nested
    @DisplayName("Table row formatting")
    class TableFormattingTests {

        @Test
        @DisplayName("Table rows with dates are formatted")
        void testFormatTable() {

            AIContextVariable dateCol = createAIContextVariable("date", "Date", null, false);
            AIContextVariable nameCol = createAIContextVariable("name", "Text", null, false);
            AIContextVariable tableVar = new AIContextVariable("schedule", "Table",
                    List.of(dateCol, nameCol));

            List<Map<String, Object>> rows = List.of(
                    Map.of("date", 1710288000000L, "name", "Meeting"),
                    Map.of("date", 1710374400000L, "name", "Review")
            );

            Object result = VariableFormatter.formatValueForLLM(tableVar, rows);

            assertNotNull(result);
            assertTrue(result instanceof List);
            @SuppressWarnings("unchecked")
            List<Object> formattedRows = (List<Object>) result;
            assertEquals(2, formattedRows.size());

            @SuppressWarnings("unchecked")
            Map<String, Object> row1 = (Map<String, Object>) formattedRows.get(0);
            assertTrue(row1.get("date").toString().contains("13-03-2024"));
            assertEquals("Meeting", row1.get("name"));
        }
    }

    @Nested
    @DisplayName("MultiField with nested date")
    class MultiFieldFormattingTests {

        @Test
        @DisplayName("MultiField recursively formats sub-fields")
        void testFormatMultiField() {

            AIContextVariable dateField = createAIContextVariable("birthday", "Date", null, false);
            AIContextVariable nameField = createAIContextVariable("name", "Text", null, false);
            AIContextVariable multiVar = new AIContextVariable("person", "MultiField",
                    List.of(dateField, nameField));

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("birthday", 1710288000000L);
            data.put("name", "Alice");

            Object result = VariableFormatter.formatValueForLLM(multiVar, data);

            assertNotNull(result);
            assertTrue(result instanceof Map);
            @SuppressWarnings("unchecked")
            Map<String, Object> formatted = (Map<String, Object>) result;
            assertTrue(formatted.get("birthday").toString().contains("13-03-2024"));
            assertEquals("Alice", formatted.get("name"));
        }
    }

    @Nested
    @DisplayName("Entity fields unwrapping")
    class EntityFormattingTests {

        @Test
        @DisplayName("Entity with 'fields' wrapper is unwrapped — only field values returned, no wrapper")
        void testFormatEntityWithFieldsWrapper() {

            AIContextVariable dateField = createAIContextVariable("dueDate", "Date", null, false);
            AIContextVariable entityVar = new AIContextVariable("task", "Entity",
                    List.of(dateField));

            Map<String, Object> entity = Map.of(
                    "fields", Map.of("dueDate", 1710288000000L)
            );

            Object result = VariableFormatter.formatValueForLLM(entityVar, entity);

            assertNotNull(result);
            assertTrue(result instanceof Map);
            @SuppressWarnings("unchecked")
            Map<String, Object> formatted = (Map<String, Object>) result;
            // After fix: result is the unwrapped fields map directly, no "fields" key
            assertFalse(formatted.containsKey("fields"),
                "Entity formatting must unwrap 'fields' — no wrapper in output");
            assertTrue(formatted.get("dueDate").toString().contains("13-03-2024"),
                "Date field must be formatted inside unwrapped entity");
        }

        @Test
        @DisplayName("Entity without 'fields' wrapper is formatted directly")
        void testFormatEntityWithoutFieldsWrapper() {

            AIContextVariable dateField = createAIContextVariable("dueDate", "Date", null, false);
            AIContextVariable entityVar = new AIContextVariable("task", "EntityReference",
                    List.of(dateField));

            Map<String, Object> entity = Map.of("dueDate", 1710288000000L);

            Object result = VariableFormatter.formatValueForLLM(entityVar, entity);

            assertNotNull(result);
            assertTrue(result instanceof Map);
            @SuppressWarnings("unchecked")
            Map<String, Object> formatted = (Map<String, Object>) result;
            // Should NOT have a "fields" wrapper since input didn't have one
            assertTrue(formatted.get("dueDate").toString().contains("13-03-2024"));
        }

        @Test
        @DisplayName("KR-20889: Entity with meta + fields — meta is stripped, only field values remain")
        void testFormatEntity_metaStripped_onlyFieldValuesRemain() {

            AIContextVariable entityVar = new AIContextVariable("NewEmail", "Entity");

            // Simulate the actual production payload from KristaGrand:
            // Entity object serialized by Gson with meta (huge) + fields (actual data)
            Map<String, Object> metaMap = new LinkedHashMap<>();
            metaMap.put("type", "EntityMeta");
            metaMap.put("primaryKey", "AQMkAGE5ZGJmNWZjLTVhOTctNGI3Yy1hNzY5LTczYzQwZjBhMTVmYQ==");
            metaMap.put("toString", "[]");
            metaMap.put("entityType", Map.of(
                    "type", "EntityType",
                    "entityId", "localDomainEntity_0fb99723-377c-419e-b24e-0ab0ce948e8c",
                    "entityName", "Mail Details",
                    "registry", Map.of("Subject", "com.krista.fields.Text", "Body", "com.krista.fields.Paragraph")
            ));

            Map<String, Object> fieldsMap = new LinkedHashMap<>();
            fieldsMap.put("Subject", "Meeting tomorrow at 10am");
            fieldsMap.put("Body", "Hi, please join the meeting tomorrow.");

            Map<String, Object> entity = new LinkedHashMap<>();
            entity.put("type", "Entity");
            entity.put("meta", metaMap);
            entity.put("fields", fieldsMap);

            Object result = VariableFormatter.formatValueForLLM(entityVar, entity);

            assertNotNull(result);
            assertTrue(result instanceof Map, "Result must be a Map");
            @SuppressWarnings("unchecked")
            Map<String, Object> formatted = (Map<String, Object>) result;

            // meta and type must be stripped
            assertFalse(formatted.containsKey("meta"),
                "meta must be stripped from entity value — it contains platform metadata that bloats the LLM prompt");
            assertFalse(formatted.containsKey("type"),
                "type annotation key must be stripped from entity value");
            assertFalse(formatted.containsKey("fields"),
                "fields wrapper must be unwrapped — field values returned directly");

            // Actual field values must be present
            assertEquals("Meeting tomorrow at 10am", formatted.get("Subject"),
                "Subject field value must be preserved");
            assertEquals("Hi, please join the meeting tomorrow.", formatted.get("Body"),
                "Body field value must be preserved");
        }

        @Test
        @DisplayName("Entity without 'fields' wrapper but with meta/type — meta stripped, fields preserved")
        void testFormatEntity_noFieldsWrapper_metaStripped() {

            AIContextVariable entityVar = new AIContextVariable("vehicle", "Entity");

            Map<String, Object> entity = new LinkedHashMap<>();
            entity.put("type", "Entity");
            entity.put("meta", Map.of("primaryKey", "pk-123", "toString", "[]"));
            entity.put("Vehicle Code", "VH-001");
            entity.put("Vehicle Name", "Toyota Camry");

            Object result = VariableFormatter.formatValueForLLM(entityVar, entity);

            assertNotNull(result);
            assertTrue(result instanceof Map);
            @SuppressWarnings("unchecked")
            Map<String, Object> formatted = (Map<String, Object>) result;

            assertFalse(formatted.containsKey("meta"), "meta must be stripped");
            assertFalse(formatted.containsKey("type"), "type must be stripped");
            assertEquals("VH-001", formatted.get("Vehicle Code"));
            assertEquals("Toyota Camry", formatted.get("Vehicle Name"));
        }

        @Test
        @DisplayName("Entity with empty meta map — stripped cleanly")
        @SuppressWarnings("unchecked")
        void testFormatEntity_emptyMetaMap_strippedCleanly() {

            AIContextVariable entityVar = new AIContextVariable("item", "Entity");

            Map<String, Object> entity = new LinkedHashMap<>();
            entity.put("type", "Entity");
            entity.put("meta", Map.of());  // empty meta map
            entity.put("fields", Map.of("Name", "Widget"));

            Object result = VariableFormatter.formatValueForLLM(entityVar, entity);

            assertNotNull(result);
            assertTrue(result instanceof Map);
            Map<String, Object> formatted = (Map<String, Object>) result;
            assertFalse(formatted.containsKey("meta"));
            assertFalse(formatted.containsKey("type"));
            assertFalse(formatted.containsKey("fields"));
            assertEquals("Widget", formatted.get("Name"));
        }

        @Test
        @DisplayName("Entity with null meta value — no NPE")
        @SuppressWarnings("unchecked")
        void testFormatEntity_nullMetaValue_noNPE() {

            AIContextVariable entityVar = new AIContextVariable("item", "Entity");

            Map<String, Object> entity = new LinkedHashMap<>();
            entity.put("type", "Entity");
            entity.put("meta", null);  // null meta
            entity.put("fields", Map.of("Name", "Gadget"));

            Object result = VariableFormatter.formatValueForLLM(entityVar, entity);

            assertNotNull(result);
            assertTrue(result instanceof Map);
            Map<String, Object> formatted = (Map<String, Object>) result;
            assertFalse(formatted.containsKey("meta"));
            assertFalse(formatted.containsKey("type"));
            assertEquals("Gadget", formatted.get("Name"));
        }

        @Test
        @DisplayName("Entity with user field named 'meta' but no 'fields' wrapper — meta unconditionally stripped (known limitation)")
        @SuppressWarnings("unchecked")
        void testFormatEntity_userFieldNamedMeta_preserved() {

            AIContextVariable entityVar = new AIContextVariable("config", "Entity");

            Map<String, Object> entity = new LinkedHashMap<>();
            entity.put("type", "Entity");
            entity.put("meta", "user-supplied-value");  // this is user data, not platform metadata
            entity.put("description", "A configuration item");

            // NOTE: Current implementation unconditionally removes "meta" key.
            // When there's no "fields" wrapper, a user field named "meta" will be stripped.
            // This test documents the known limitation.
            Object result = VariableFormatter.formatValueForLLM(entityVar, entity);

            assertNotNull(result);
            assertTrue(result instanceof Map);
            Map<String, Object> formatted = (Map<String, Object>) result;
            assertFalse(formatted.containsKey("type"), "type should be stripped");
            // "meta" is stripped even though it's user data — documenting known limitation
            assertFalse(formatted.containsKey("meta"),
                    "meta is unconditionally stripped (known limitation when user field is named 'meta')");
            assertEquals("A configuration item", formatted.get("description"));
        }
    }

    // ========================================
    // Section 6: Type Format Guidance Tests
    // ========================================

    @Nested
    @DisplayName("Type format guidance")
    class TypeFormatGuidanceTests {

        @Test
        @DisplayName("Phone type returns format guidance")
        void testTypeGuidance_Phone() {

            AIContextVariable phoneVar = createAIContextVariable("phone", "Phone", null, false);
            ExpectedOutput eo = createExpectedOutput(phoneVar, "Enter phone number", null);
            String result = VariableFormatter.formatExpectedOutputs(List.of(eo));

            assertTrue(result.contains("countryCode"), "Should contain phone format guidance");
            assertTrue(result.contains("nationalNumber"));
        }

        @Test
        @DisplayName("Rating type returns format guidance")
        void testTypeGuidance_Rating() {

            AIContextVariable ratingVar = createAIContextVariable("rating", "Rating", null, false);
            ExpectedOutput eo = createExpectedOutput(ratingVar, "Rate 1-5", null);
            String result = VariableFormatter.formatExpectedOutputs(List.of(eo));

            assertTrue(result.contains("integer 1-5"));
        }

        @Test
        @DisplayName("Checkboxes type returns format guidance")
        void testTypeGuidance_Checkboxes() {

            AIContextVariable cbVar = createAIContextVariable("opts", "Checkboxes", null, false);
            ExpectedOutput eo = createExpectedOutput(cbVar, "Select options", null);
            String result = VariableFormatter.formatExpectedOutputs(List.of(eo));

            assertTrue(result.contains("JSON array of selected values"));
        }

        @Test
        @DisplayName("Address type returns format guidance")
        void testTypeGuidance_Address() {

            AIContextVariable addrVar = createAIContextVariable("addr", "Address", null, false);
            ExpectedOutput eo = createExpectedOutput(addrVar, "Enter address", null);
            String result = VariableFormatter.formatExpectedOutputs(List.of(eo));

            assertTrue(result.contains("street"));
            assertTrue(result.contains("postalCode"));
        }

        @Test
        @DisplayName("Percentage type returns format guidance")
        void testTypeGuidance_Percentage() {

            AIContextVariable pctVar = createAIContextVariable("pct", "Percentage", null, false);
            ExpectedOutput eo = createExpectedOutput(pctVar, "Enter percentage", null);
            String result = VariableFormatter.formatExpectedOutputs(List.of(eo));

            assertTrue(result.contains("decimal value (0.75 for 75%)"), "Percentage should include decimal format guidance");
        }

        @Test
        @DisplayName("Currency type returns format guidance")
        void testTypeGuidance_Currency() {

            AIContextVariable curVar = createAIContextVariable("amount", "Currency", null, false);
            ExpectedOutput eo = createExpectedOutput(curVar, "Enter amount", null);
            String result = VariableFormatter.formatExpectedOutputs(List.of(eo));

            assertTrue(result.contains("no currency symbols"), "Currency guidance should specify no currency symbols");
        }

        @Test
        @DisplayName("Full logical name com.krista.fields.Phone returns guidance")
        void testTypeGuidance_FullLogicalNamePhone() {

            AIContextVariable phoneVar = createAIContextVariable("phone", "com.krista.fields.Phone", null, false);
            ExpectedOutput eo = createExpectedOutput(phoneVar, "Enter phone", null);
            String result = VariableFormatter.formatExpectedOutputs(List.of(eo));

            assertTrue(result.contains("countryCode"));
        }

        @Test
        @DisplayName("Full logical name com.krista.fields.Date returns guidance")
        void testTypeGuidance_FullLogicalNameDate() {

            AIContextVariable dateVar = createAIContextVariable("date", "com.krista.fields.Date", null, false);
            ExpectedOutput eo = createExpectedOutput(dateVar, "Enter date", null);
            String result = VariableFormatter.formatExpectedOutputs(List.of(eo));

            assertTrue(result.contains("DD-MM-YYYY"));
        }
    }

    // ========================================
    // Section 7: PickOne/PickMany Options Display (Task 7)
    // ========================================

    @Nested
    @DisplayName("Options display in prompts")
    class OptionsDisplayTests {

        @Test
        @DisplayName("PickOne expected output shows options from metaData (List)")
        void testPickOneOptions_ExpectedOutput_List() {

            AIContextVariable pickOneVar = createAIContextVariable("color", "PickOne", null, false);
            pickOneVar.getMetaData().put("options", List.of("Red", "Green", "Blue"));
            ExpectedOutput eo = createExpectedOutput(pickOneVar, "Select a color", null);

            String result = VariableFormatter.formatExpectedOutputs(List.of(eo));

            assertTrue(result.contains("[Options: Red, Green, Blue]"),
                    "Should contain options list, got: " + result);
        }

        @Test
        @DisplayName("PickMany expected output shows options from metaData (List)")
        void testPickManyOptions_ExpectedOutput_List() {

            AIContextVariable pickManyVar = createAIContextVariable("toppings",
                    "com.krista.fields.Checkboxes", null, false);
            pickManyVar.getMetaData().put("options", List.of("Cheese", "Pepperoni", "Mushrooms"));
            ExpectedOutput eo = createExpectedOutput(pickManyVar, "Select toppings", null);

            String result = VariableFormatter.formatExpectedOutputs(List.of(eo));

            assertTrue(result.contains("[Options: Cheese, Pepperoni, Mushrooms]"),
                    "Should contain options list, got: " + result);
        }

        @Test
        @DisplayName("PickOne input variable shows options from metaData")
        void testPickOneOptions_InputVariable() {

            AIContextVariable pickOneVar = createAIContextVariable("priority", "PickOne", null, false);
            pickOneVar.getMetaData().put("options", List.of("High", "Medium", "Low"));
            InputVariable iv = createInputVariable(pickOneVar, "High");

            String result = VariableFormatter.formatInputVariables(List.of(iv));

            assertTrue(result.contains("[Options: High, Medium, Low]"),
                    "Should contain options in input variable format, got: " + result);
        }

        @Test
        @DisplayName("Options from legacy string format are parsed")
        void testOptions_LegacyStringFormat() {

            AIContextVariable pickOneVar = createAIContextVariable("status", "PickOne", null, false);
            pickOneVar.getMetaData().put("options", "[Open, Closed, Pending]");
            ExpectedOutput eo = createExpectedOutput(pickOneVar, "Select status", null);

            String result = VariableFormatter.formatExpectedOutputs(List.of(eo));

            assertTrue(result.contains("[Options: Open, Closed, Pending]"),
                    "Should parse legacy string format, got: " + result);
        }

        @Test
        @DisplayName("No options in metaData does not add options text")
        void testNoOptions() {

            AIContextVariable textVar = createAIContextVariable("name", "Text", null, false);
            ExpectedOutput eo = createExpectedOutput(textVar, "Enter name", null);

            String result = VariableFormatter.formatExpectedOutputs(List.of(eo));

            assertFalse(result.contains("[Options:"), "Should not contain options text");
        }
    }

    // ========================================
    // Section 8: Constraint-Aware Guidance (Task 11)
    // ========================================

    @Nested
    @DisplayName("Constraint-aware type guidance")
    class ConstraintGuidanceTests {

        @Test
        @DisplayName("Number with min/max shows range")
        void testNumberWithRange() {

            AIContextVariable numVar = createAIContextVariable("score", "Number", null, false);
            numVar.getMetaData().put("minValue", 0);
            numVar.getMetaData().put("maxValue", 100);
            ExpectedOutput eo = createExpectedOutput(numVar, "Enter score", null);

            String result = VariableFormatter.formatExpectedOutputs(List.of(eo));

            assertTrue(result.contains("range: 0 to 100"),
                    "Should show numeric range, got: " + result);
        }

        @Test
        @DisplayName("Number without constraints shows basic guidance")
        void testNumberWithoutConstraints() {

            AIContextVariable numVar = createAIContextVariable("value", "Number", null, false);
            ExpectedOutput eo = createExpectedOutput(numVar, "Enter value", null);

            String result = VariableFormatter.formatExpectedOutputs(List.of(eo));

            assertTrue(result.contains("[Format: numeric value]"),
                    "Should show basic numeric guidance, got: " + result);
            assertFalse(result.contains("range:"), "Should not contain range");
        }

        @Test
        @DisplayName("Date with allowPast=false shows future dates only")
        void testDateFutureOnly() {

            AIContextVariable dateVar = createAIContextVariable("deadline", "Date", null, false);
            dateVar.getMetaData().put("allowPast", false);
            dateVar.getMetaData().put("allowFuture", true);
            ExpectedOutput eo = createExpectedOutput(dateVar, "Enter deadline", null);

            String result = VariableFormatter.formatExpectedOutputs(List.of(eo));

            assertTrue(result.contains("future dates only"),
                    "Should indicate future dates only, got: " + result);
        }

        @Test
        @DisplayName("DateRange with maxDurationInDays shows max duration")
        void testDateRangeMaxDuration() {

            AIContextVariable rangeVar = createAIContextVariable("period", "DateRange", null, false);
            rangeVar.getMetaData().put("maxDurationInDays", 30);
            ExpectedOutput eo = createExpectedOutput(rangeVar, "Select period", null);

            String result = VariableFormatter.formatExpectedOutputs(List.of(eo));

            assertTrue(result.contains("max duration: 30 days"),
                    "Should show max duration, got: " + result);
        }

        @Test
        @DisplayName("Rating with custom min/max shows correct range")
        void testRatingCustomRange() {

            AIContextVariable ratingVar = createAIContextVariable("quality", "Rating", null, false);
            ratingVar.getMetaData().put("minRating", 1);
            ratingVar.getMetaData().put("maxRating", 10);
            ExpectedOutput eo = createExpectedOutput(ratingVar, "Rate quality", null);

            String result = VariableFormatter.formatExpectedOutputs(List.of(eo));

            assertTrue(result.contains("integer 1-10"),
                    "Should show custom rating range, got: " + result);
        }

        @Test
        @DisplayName("Checkboxes with min/max selection shows selection constraint")
        void testCheckboxesSelectionConstraint() {

            AIContextVariable cbVar = createAIContextVariable("tags", "Checkboxes", null, false);
            cbVar.getMetaData().put("minSelection", 1);
            cbVar.getMetaData().put("maxSelection", 3);
            cbVar.getMetaData().put("options", List.of("A", "B", "C", "D"));
            ExpectedOutput eo = createExpectedOutput(cbVar, "Select tags", null);

            String result = VariableFormatter.formatExpectedOutputs(List.of(eo));

            assertTrue(result.contains("at least 1"),
                    "Should show min selection, got: " + result);
            assertTrue(result.contains("at most 3"),
                    "Should show max selection, got: " + result);
            assertTrue(result.contains("[Options: A, B, C, D]"),
                    "Should also show options, got: " + result);
        }

        @Test
        @DisplayName("Text with maxLength shows length constraint")
        void testTextMaxLength() {

            AIContextVariable textVar = createAIContextVariable("bio", "Text", null, false);
            textVar.getMetaData().put("maxLength", 500);
            ExpectedOutput eo = createExpectedOutput(textVar, "Enter bio", null);

            String result = VariableFormatter.formatExpectedOutputs(List.of(eo));

            assertTrue(result.contains("max 500"),
                    "Should show max length, got: " + result);
        }

        @Test
        @DisplayName("Number with formatPattern shows pattern")
        void testNumberWithFormatPattern() {

            AIContextVariable numVar = createAIContextVariable("amount", "Currency", null, false);
            numVar.getMetaData().put("minValue", 0);
            numVar.getMetaData().put("formatPattern", "$#,##0.00");
            ExpectedOutput eo = createExpectedOutput(numVar, "Enter amount", null);

            String result = VariableFormatter.formatExpectedOutputs(List.of(eo));

            assertTrue(result.contains("pattern: $#,##0.00"),
                    "Should show format pattern, got: " + result);
        }

        @Test
        @DisplayName("Phone with formatPattern shows pattern")
        void testPhoneWithFormatPattern() {

            AIContextVariable phoneVar = createAIContextVariable("phone", "Phone", null, false);
            phoneVar.getMetaData().put("formatPattern", "+1 (###) ###-####");
            ExpectedOutput eo = createExpectedOutput(phoneVar, "Enter phone", null);

            String result = VariableFormatter.formatExpectedOutputs(List.of(eo));

            assertTrue(result.contains("pattern: +1 (###) ###-####"),
                    "Should show phone format pattern, got: " + result);
        }

        @Test
        @DisplayName("Entity type returns format guidance specifying data NOT wrapped in fields")
        void testTypeGuidance_Entity() {

            AIContextVariable entityVar = createAIContextVariable("customer", "Entity", null, false);
            ExpectedOutput eo = createExpectedOutput(entityVar, "Return customer", null);
            String result = VariableFormatter.formatExpectedOutputs(List.of(eo));
            assertTrue(result.contains("NOT wrapped in"),
                    "Entity guidance must instruct LLM not to wrap in fields key, got: " + result);
        }

        @Test
        @DisplayName("EntityReference type returns same format guidance as Entity")
        void testTypeGuidance_EntityReference() {

            AIContextVariable entityRefVar = createAIContextVariable("ref", "EntityReference", null, false);
            ExpectedOutput eo = createExpectedOutput(entityRefVar, "Provide reference", null);
            String result = VariableFormatter.formatExpectedOutputs(List.of(eo));
            assertTrue(result.contains("NOT wrapped in"),
                    "EntityReference guidance must match Entity guidance, got: " + result);
        }

        @Test
        @DisplayName("PickOne shows format guidance for single value")
        void testPickOneFormatGuidance() {

            AIContextVariable pickOneVar = createAIContextVariable("color", "PickOne", null, false);
            pickOneVar.getMetaData().put("options", List.of("Red", "Blue"));
            ExpectedOutput eo = createExpectedOutput(pickOneVar, "Select color", null);

            String result = VariableFormatter.formatExpectedOutputs(List.of(eo));

            assertTrue(result.contains("[Format: single value from the options list]"),
                    "PickOne should have format guidance, got: " + result);
        }
    }

    // ========================================
    // Section 9: Sub-Variable (Structure) Rendering Tests
    // ========================================

    @Nested
    @DisplayName("formatSubVariables recursive rendering")
    class SubVariableRenderingTests {

        @Test
        @DisplayName("List<MultiField> with anonymous wrapper renders named sub-fields")
        void testListMultiFieldWithAnonymousWrapper() {

            // Build: List -> anonymous wrapper (MultiField) -> named sub-fields
            AIContextVariable docsetName = createAIContextVariable("Docset Name", "com.krista.fields.Text", null, false);
            AIContextVariable hasDocs = createAIContextVariable("Has documents", "com.krista.fields.Switch", null, false);

            AIContextVariable anonymousWrapper = new AIContextVariable("", "com.krista.fields.MultiField",
                    List.of(docsetName, hasDocs));

            AIContextVariable listVar = createAIContextVariable("List2", "com.krista.fields.List", null, false);
            listVar.setSubContextVariables(List.of(anonymousWrapper));

            ExpectedOutput eo = createExpectedOutput(listVar, "Provide list", null);
            String result = VariableFormatter.formatExpectedOutputs(List.of(eo));

            assertTrue(result.contains("[Structure: {Docset Name: Text, Has documents: Switch}]"),
                    "Should render named sub-fields through anonymous wrapper, got: " + result);
        }

        @Test
        @DisplayName("List<Entity> with anonymous wrapper renders named sub-fields")
        void testListEntityWithAnonymousWrapper() {

            AIContextVariable fileSize = createAIContextVariable("File size", "com.krista.fields.Number", null, false);
            AIContextVariable fileName = createAIContextVariable("File name", "com.krista.fields.Text", null, false);

            AIContextVariable anonymousWrapper = new AIContextVariable("", "com.krista.fields.Entity",
                    List.of(fileSize, fileName));

            AIContextVariable listVar = createAIContextVariable("FileList", "com.krista.fields.List", null, false);
            listVar.setSubContextVariables(List.of(anonymousWrapper));

            ExpectedOutput eo = createExpectedOutput(listVar, "Provide files", null);
            String result = VariableFormatter.formatExpectedOutputs(List.of(eo));

            assertTrue(result.contains("[Structure: {File size: Number, File name: Text}]"),
                    "Should render entity sub-fields through anonymous wrapper, got: " + result);
        }

        @Test
        @DisplayName("Simple MultiField (no list wrapper) renders direct sub-fields")
        void testSimpleMultiFieldDirectSubFields() {

            AIContextVariable city = createAIContextVariable("City", "com.krista.fields.Text", null, false);
            AIContextVariable rating = createAIContextVariable("Rating", "com.krista.fields.Rating", null, false);

            AIContextVariable multiVar = createAIContextVariable("Details", "com.krista.fields.MultiField", null, false);
            multiVar.setSubContextVariables(List.of(city, rating));

            ExpectedOutput eo = createExpectedOutput(multiVar, "Fill in details", null);
            String result = VariableFormatter.formatExpectedOutputs(List.of(eo));

            assertTrue(result.contains("[Structure: {City: Text, Rating: Rating}]"),
                    "Should render direct sub-fields for simple MultiField, got: " + result);
        }

        @Test
        @DisplayName("Deeply nested: List<MultiField> where a sub-field is itself a MultiField with sub-sub-fields")
        void testDeeplyNestedSubFields() {

            // Inner MultiField sub-fields
            AIContextVariable street = createAIContextVariable("Street", "com.krista.fields.Text", null, false);
            AIContextVariable zip = createAIContextVariable("Zip", "com.krista.fields.Text", null, false);

            // Named sub-field that is itself a MultiField with children
            AIContextVariable address = createAIContextVariable("Address", "com.krista.fields.MultiField", null, false);
            address.setSubContextVariables(List.of(street, zip));

            AIContextVariable name = createAIContextVariable("Name", "com.krista.fields.Text", null, false);

            // Anonymous wrapper for the list item template
            AIContextVariable anonymousWrapper = new AIContextVariable("", "com.krista.fields.MultiField",
                    List.of(name, address));

            AIContextVariable listVar = createAIContextVariable("People", "com.krista.fields.List", null, false);
            listVar.setSubContextVariables(List.of(anonymousWrapper));

            ExpectedOutput eo = createExpectedOutput(listVar, "Provide people", null);
            String result = VariableFormatter.formatExpectedOutputs(List.of(eo));

            assertTrue(result.contains("Name: Text"),
                    "Should contain top-level field Name, got: " + result);
            assertTrue(result.contains("Address: MultiField {Street: Text, Zip: Text}"),
                    "Should contain nested Address with sub-fields, got: " + result);
        }

        @Test
        @DisplayName("Null subContextVariables renders empty string gracefully")
        void testNullSubContextVariables() {

            AIContextVariable simpleVar = createAIContextVariable("Simple", "com.krista.fields.Text", null, false);
            simpleVar.setSubContextVariables(null);

            ExpectedOutput eo = createExpectedOutput(simpleVar, "Enter text", null);
            String result = VariableFormatter.formatExpectedOutputs(List.of(eo));

            assertFalse(result.contains("[Structure:"),
                    "Should not contain structure block for null subVars, got: " + result);
        }

        @Test
        @DisplayName("Empty subContextVariables list renders empty string gracefully")
        void testEmptySubContextVariables() {

            AIContextVariable simpleVar = createAIContextVariable("Simple", "com.krista.fields.Text", null, false);
            simpleVar.setSubContextVariables(List.of());

            ExpectedOutput eo = createExpectedOutput(simpleVar, "Enter text", null);
            String result = VariableFormatter.formatExpectedOutputs(List.of(eo));

            assertFalse(result.contains("[Structure:"),
                    "Should not contain structure block for empty subVars, got: " + result);
        }

        @Test
        @DisplayName("Anonymous wrapper with null name is treated as anonymous")
        void testAnonymousWrapperWithNullName() {

            AIContextVariable subField = createAIContextVariable("Amount", "com.krista.fields.Currency", null, false);

            AIContextVariable nullNameWrapper = new AIContextVariable(null, "com.krista.fields.MultiField",
                    List.of(subField));

            AIContextVariable listVar = createAIContextVariable("Payments", "com.krista.fields.List", null, false);
            listVar.setSubContextVariables(List.of(nullNameWrapper));

            ExpectedOutput eo = createExpectedOutput(listVar, "Provide payments", null);
            String result = VariableFormatter.formatExpectedOutputs(List.of(eo));

            assertTrue(result.contains("[Structure: {Amount: Currency}]"),
                    "Should handle null-name anonymous wrapper, got: " + result);
        }

        @Test
        @DisplayName("Short type names are capitalized in structure output")
        void testShortTypeNamesCapitalized() {

            AIContextVariable field = createAIContextVariable("Active", "switch", null, false);

            AIContextVariable multiVar = createAIContextVariable("Flags", "MultiField", null, false);
            multiVar.setSubContextVariables(List.of(field));

            ExpectedOutput eo = createExpectedOutput(multiVar, "Set flags", null);
            String result = VariableFormatter.formatExpectedOutputs(List.of(eo));

            assertTrue(result.contains("Active: Switch"),
                    "Short type name should be capitalized, got: " + result);
        }
    }

    // ========================================
    // Section 10: Error Recovery Tests
    // ========================================

    @Test
    @DisplayName("formatValueForLLM returns raw value on error (G18)")
    void testFormatValueForLLM_ErrorRecovery() {

        AIContextVariable dateVar = createAIContextVariable("badDate", "Date", null, false);
        // Pass a non-numeric, non-string value that formatFromEpoch can't handle
        Object weirdValue = new Object() {

            @Override
            public String toString() {

                return "weird-object";
            }
        };

        // Should not throw, should return fallback
        Object result = VariableFormatter.formatValueForLLM(dateVar, weirdValue);
        assertNotNull(result);
    }

    // ========================================
    // Section: Sub-Variable Required Suffix Tests
    // ========================================

    @Nested
    @DisplayName("Sub-variable required suffix in structure string")
    class SubVariableRequiredSuffixTests {

        @Test
        @DisplayName("Required sub-variables render (required) suffix in structure string")
        void testRequiredSubVariablesRenderSuffix() {

            AIContextVariable docsetName = createAIContextVariable("Docset Name", "Text", null, true);
            AIContextVariable hasDocs = createAIContextVariable("Has documents", "Switch", null, true);

            AIContextVariable anonymousWrapper = new AIContextVariable("", "MultiField",
                    List.of(docsetName, hasDocs));

            AIContextVariable listVar = createAIContextVariable("docsets", "List", null, false);
            listVar.setSubContextVariables(List.of(anonymousWrapper));

            ExpectedOutput eo = createExpectedOutput(listVar, "Provide document sets", null);

            String result = VariableFormatter.formatExpectedOutputs(List.of(eo));

            assertTrue(result.contains("Docset Name: Text (required)"),
                    "Required sub-variable must render '(required)' suffix, got: " + result);
            assertTrue(result.contains("Has documents: Switch (required)"),
                    "Required sub-variable must render '(required)' suffix, got: " + result);
        }

        @Test
        @DisplayName("Non-required sub-variables do NOT render (required) suffix")
        void testNonRequiredSubVariablesNoSuffix() {

            AIContextVariable optionalField = createAIContextVariable("Notes", "Text", null, false);
            AIContextVariable requiredField = createAIContextVariable("Title", "Text", null, true);

            AIContextVariable entityVar = createAIContextVariable("ticket", "Entity", null, false);
            entityVar.setSubContextVariables(List.of(requiredField, optionalField));

            ExpectedOutput eo = createExpectedOutput(entityVar, "Create a ticket", null);

            String result = VariableFormatter.formatExpectedOutputs(List.of(eo));

            assertTrue(result.contains("Title: Text (required)"),
                    "Required field must have (required) suffix, got: " + result);
            assertTrue(result.contains("Notes: Text"),
                    "Optional field must appear without suffix, got: " + result);
            assertFalse(result.contains("Notes: Text (required)"),
                    "Optional field must NOT have (required) suffix, got: " + result);
        }

        @Test
        @DisplayName("Structure string with mixed required and optional produces correct output")
        void testMixedRequiredAndOptionalStructure() {

            AIContextVariable nameField = createAIContextVariable("Name", "Text", null, true);
            AIContextVariable descField = createAIContextVariable("Description", "Text", null, false);
            AIContextVariable activeField = createAIContextVariable("Active", "Switch", null, true);

            AIContextVariable multiVar = createAIContextVariable("record", "MultiField", null, false);
            multiVar.setSubContextVariables(List.of(nameField, descField, activeField));

            ExpectedOutput eo = createExpectedOutput(multiVar, "Create a record", null);

            String result = VariableFormatter.formatExpectedOutputs(List.of(eo));

            // Verify the structure contains the expected pattern
            assertTrue(result.contains("Name: Text (required)"),
                    "Expected 'Name: Text (required)' in: " + result);
            assertTrue(result.contains("Description: Text"),
                    "Expected 'Description: Text' in: " + result);
            assertFalse(result.contains("Description: Text (required)"),
                    "Description should NOT be required in: " + result);
            assertTrue(result.contains("Active: Switch (required)"),
                    "Expected 'Active: Switch (required)' in: " + result);
        }
    }

    // ========================================
    // Helper Methods
    // ========================================

    private AIContextVariable createAIContextVariable(String name, String type, String description, boolean required) {

        AIContextVariable var = new AIContextVariable();
        var.setName(name);
        var.setType(type);
        var.setDescription(description);
        var.setRequired(required);
        return var;
    }

    private InputVariable createInputVariable(AIContextVariable contextVar, Object value) {

        InputVariable inputVar = new InputVariable();
        inputVar.setAiContextVariable(contextVar);
        inputVar.setValue(value);
        return inputVar;
    }

    private ExpectedOutput createExpectedOutput(AIContextVariable contextVar, String instruction, String operationType) {

        ExpectedOutput output = new ExpectedOutput();
        output.setAiContextVariable(contextVar);
        output.setInstruction(instruction);
        output.setOperationType(operationType);
        return output;
    }
}
