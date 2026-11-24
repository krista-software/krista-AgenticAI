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

package app.krista.extensions.hook.agentic.util;

import app.krista.extensions.hook.agentic.dto.AIContextVariable;
import app.krista.extensions.hook.agentic.dto.ExpectedOutput;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link OutputVariableConverter}.
 * Tests output variable conversion from LLM-generated values to Krista-typed values,
 * including type normalization, new type converters, and structural handling.
 */
class OutputVariableConverterTest {

    private OutputVariableConverter converter;

    @BeforeEach
    void setUp() {

        converter = new OutputVariableConverter(ZoneId.of("UTC"));
    }

    // ========================================
    // Section 1: Type Normalization Tests
    // ========================================

    @Nested
    @DisplayName("Type normalization")
    class TypeNormalizationTests {

        @Test
        @DisplayName("Full logical name com.krista.fields.Date normalizes to 'date'")
        void testNormalizeFullLogicalName_Date() {

            assertEquals("date", OutputVariableConverter.normalizeType("com.krista.fields.Date"));
        }

        @Test
        @DisplayName("Full logical name com.krista.fields.EntityReference normalizes correctly")
        void testNormalizeFullLogicalName_Entity() {

            assertEquals("entityreference", OutputVariableConverter.normalizeType("com.krista.fields.EntityReference"));
        }

        @Test
        @DisplayName("Full logical name com.krista.fields.Checkboxes normalizes correctly")
        void testNormalizeFullLogicalName_Checkboxes() {

            assertEquals("checkboxes", OutputVariableConverter.normalizeType("com.krista.fields.Checkboxes"));
        }

        @Test
        @DisplayName("Short name 'Date' normalizes to 'date'")
        void testNormalizeShortName() {

            assertEquals("date", OutputVariableConverter.normalizeType("Date"));
        }

        @Test
        @DisplayName("Already lowercase passes through")
        void testNormalizeLowercase() {

            assertEquals("date", OutputVariableConverter.normalizeType("date"));
        }

        @Test
        @DisplayName("Null normalizes to empty string")
        void testNormalizeNull() {

            assertEquals("", OutputVariableConverter.normalizeType(null));
        }

        @Test
        @DisplayName("Full logical name conversion produces same result as short name")
        void testFullLogicalNameProducesSameConversionAsShortName() {

            // Create two expected outputs with the same variable but different type naming
            AIContextVariable fullNameVar = new AIContextVariable();
            fullNameVar.setName("appointmentDate");
            fullNameVar.setType("com.krista.fields.Date");

            AIContextVariable shortNameVar = new AIContextVariable();
            shortNameVar.setName("appointmentDate");
            shortNameVar.setType("Date");

            Map<String, Object> input = Map.of("appointmentDate", "15-01-2024");

            Map<String, Object> fullNameResult = converter.convertOutputVariables(
                    new HashMap<>(input),
                    List.of(new ExpectedOutput(fullNameVar, "date", "read")));

            Map<String, Object> shortNameResult = converter.convertOutputVariables(
                    new HashMap<>(input),
                    List.of(new ExpectedOutput(shortNameVar, "date", "read")));

            assertEquals(fullNameResult.get("appointmentDate"), shortNameResult.get("appointmentDate"));
        }
    }

    // ========================================
    // Section 2: Phone Conversion Tests
    // ========================================

    @Nested
    @DisplayName("Phone conversion")
    class PhoneConversionTests {

        @Test
        @DisplayName("String phone number is converted to structured format")
        void testConvertPhone_String() {

            AIContextVariable phoneVar = createContextVar("phone", "Phone");
            Map<String, Object> input = Map.of("phone", "5551234567");

            Map<String, Object> result = converter.convertOutputVariables(
                    new HashMap<>(input),
                    List.of(new ExpectedOutput(phoneVar, "phone", "read")));

            Object converted = result.get("phone");
            assertInstanceOf(Map.class, converted);
            @SuppressWarnings("unchecked")
            Map<String, Object> phoneMap = (Map<String, Object>) converted;
            assertEquals(0, phoneMap.get("countryCode")); // default country code
            assertEquals(5551234567L, phoneMap.get("nationalNumber"));
        }

        @Test
        @DisplayName("Numeric phone number is converted to structured format")
        void testConvertPhone_Number() {

            AIContextVariable phoneVar = createContextVar("phone", "Phone");
            Map<String, Object> input = Map.of("phone", 5551234567L);

            Map<String, Object> result = converter.convertOutputVariables(
                    new HashMap<>(input),
                    List.of(new ExpectedOutput(phoneVar, "phone", "read")));

            @SuppressWarnings("unchecked")
            Map<String, Object> phoneMap = (Map<String, Object>) result.get("phone");
            assertEquals(5551234567L, phoneMap.get("nationalNumber"));
        }

        @Test
        @DisplayName("Already structured phone map is returned as-is")
        void testConvertPhone_AlreadyStructured() {

            AIContextVariable phoneVar = createContextVar("phone", "Phone");
            Map<String, Object> phoneData = Map.of("countryCode", 1, "nationalNumber", 5551234567L);
            Map<String, Object> input = Map.of("phone", phoneData);

            Map<String, Object> result = converter.convertOutputVariables(
                    new HashMap<>(input),
                    List.of(new ExpectedOutput(phoneVar, "phone", "read")));

            assertEquals(phoneData, result.get("phone"));
        }

        @Test
        @DisplayName("Phone with country code in metaData")
        void testConvertPhone_WithCountryCodeMetaData() {

            AIContextVariable phoneVar = createContextVar("phone", "Phone");
            phoneVar.setMetaData(Map.of("countryCode", 44));

            Map<String, Object> input = Map.of("phone", "7911123456");

            Map<String, Object> result = converter.convertOutputVariables(
                    new HashMap<>(input),
                    List.of(new ExpectedOutput(phoneVar, "phone", "read")));

            @SuppressWarnings("unchecked")
            Map<String, Object> phoneMap = (Map<String, Object>) result.get("phone");
            assertEquals(44, phoneMap.get("countryCode"));
            assertEquals(7911123456L, phoneMap.get("nationalNumber"));
        }

        @Test
        @DisplayName("Full logical name com.krista.fields.Phone is handled")
        void testConvertPhone_FullLogicalName() {

            AIContextVariable phoneVar = createContextVar("phone", "com.krista.fields.Phone");
            Map<String, Object> input = Map.of("phone", "5551234567");

            Map<String, Object> result = converter.convertOutputVariables(
                    new HashMap<>(input),
                    List.of(new ExpectedOutput(phoneVar, "phone", "read")));

            assertInstanceOf(Map.class, result.get("phone"));
        }
    }

    // ========================================
    // Section 3: Rating Conversion Tests
    // ========================================

    @Nested
    @DisplayName("Rating conversion")
    class RatingConversionTests {

        @Test
        @DisplayName("Numeric rating is returned as int")
        void testConvertRating_Number() {

            AIContextVariable ratingVar = createContextVar("rating", "Rating");
            Map<String, Object> input = Map.of("rating", 4.7);

            Map<String, Object> result = converter.convertOutputVariables(
                    new HashMap<>(input),
                    List.of(new ExpectedOutput(ratingVar, "rating", "read")));

            assertEquals(4, result.get("rating"));
        }

        @Test
        @DisplayName("String rating is parsed to int")
        void testConvertRating_String() {

            AIContextVariable ratingVar = createContextVar("rating", "Rating");
            Map<String, Object> input = Map.of("rating", "3");

            Map<String, Object> result = converter.convertOutputVariables(
                    new HashMap<>(input),
                    List.of(new ExpectedOutput(ratingVar, "rating", "read")));

            assertEquals(3, result.get("rating"));
        }

        @Test
        @DisplayName("Integer rating is returned as-is")
        void testConvertRating_Integer() {

            AIContextVariable ratingVar = createContextVar("rating", "Rating");
            Map<String, Object> input = Map.of("rating", 5);

            Map<String, Object> result = converter.convertOutputVariables(
                    new HashMap<>(input),
                    List.of(new ExpectedOutput(ratingVar, "rating", "read")));

            assertEquals(5, result.get("rating"));
        }
    }

    // ========================================
    // Section 4: Checkboxes Conversion Tests
    // ========================================

    @Nested
    @DisplayName("Checkboxes conversion")
    class CheckboxesConversionTests {

        @Test
        @DisplayName("List value is returned as-is")
        void testConvertCheckboxes_List() {

            AIContextVariable cbVar = createContextVar("options", "Checkboxes");
            List<String> options = List.of("Red", "Blue");
            Map<String, Object> input = Map.of("options", options);

            Map<String, Object> result = converter.convertOutputVariables(
                    new HashMap<>(input),
                    List.of(new ExpectedOutput(cbVar, "options", "read")));

            assertEquals(options, result.get("options"));
        }

        @Test
        @DisplayName("JSON array string is parsed to list")
        void testConvertCheckboxes_JsonArrayString() {

            AIContextVariable cbVar = createContextVar("options", "Checkboxes");
            Map<String, Object> input = Map.of("options", "[\"Red\",\"Blue\"]");

            Map<String, Object> result = converter.convertOutputVariables(
                    new HashMap<>(input),
                    List.of(new ExpectedOutput(cbVar, "options", "read")));

            Object converted = result.get("options");
            assertInstanceOf(List.class, converted);
            @SuppressWarnings("unchecked")
            List<String> list = (List<String>) converted;
            assertEquals(2, list.size());
            assertTrue(list.contains("Red"));
            assertTrue(list.contains("Blue"));
        }

        @Test
        @DisplayName("Single string value is wrapped in list")
        void testConvertCheckboxes_SingleValue() {

            AIContextVariable cbVar = createContextVar("options", "Checkboxes");
            Map<String, Object> input = Map.of("options", "Red");

            Map<String, Object> result = converter.convertOutputVariables(
                    new HashMap<>(input),
                    List.of(new ExpectedOutput(cbVar, "options", "read")));

            assertEquals(List.of("Red"), result.get("options"));
        }

        @Test
        @DisplayName("Full logical name com.krista.fields.Checkboxes is handled")
        void testConvertCheckboxes_FullLogicalName() {

            AIContextVariable cbVar = createContextVar("options", "com.krista.fields.Checkboxes");
            Map<String, Object> input = Map.of("options", "[\"A\",\"B\"]");

            Map<String, Object> result = converter.convertOutputVariables(
                    new HashMap<>(input),
                    List.of(new ExpectedOutput(cbVar, "options", "read")));

            assertInstanceOf(List.class, result.get("options"));
        }
    }

    // ========================================
    // Section 5: Switch/Boolean Conversion Tests
    // ========================================

    @Nested
    @DisplayName("Switch conversion")
    class SwitchConversionTests {

        @Test
        @DisplayName("Switch type 'true' string converts to Boolean.TRUE")
        void testConvertSwitch_TrueString() {

            AIContextVariable switchVar = createContextVar("enabled", "Switch");
            Map<String, Object> input = Map.of("enabled", "true");

            Map<String, Object> result = converter.convertOutputVariables(
                    new HashMap<>(input),
                    List.of(new ExpectedOutput(switchVar, "enabled", "read")));

            assertEquals(Boolean.TRUE, result.get("enabled"));
        }

        @Test
        @DisplayName("Switch type 'yes' string converts to Boolean.TRUE")
        void testConvertSwitch_YesString() {

            AIContextVariable switchVar = createContextVar("active", "Switch");
            Map<String, Object> input = Map.of("active", "yes");

            Map<String, Object> result = converter.convertOutputVariables(
                    new HashMap<>(input),
                    List.of(new ExpectedOutput(switchVar, "active", "read")));

            assertEquals(Boolean.TRUE, result.get("active"));
        }

        @Test
        @DisplayName("Switch type with full logical name")
        void testConvertSwitch_FullLogicalName() {

            AIContextVariable switchVar = createContextVar("toggle", "com.krista.fields.Switch");
            Map<String, Object> input = Map.of("toggle", "false");

            Map<String, Object> result = converter.convertOutputVariables(
                    new HashMap<>(input),
                    List.of(new ExpectedOutput(switchVar, "toggle", "read")));

            assertEquals(Boolean.FALSE, result.get("toggle"));
        }
    }

    // ========================================
    // Section 6: Entity Structure Tests (G15 fix)
    // ========================================

    @Nested
    @DisplayName("Entity conversion")
    class EntityConversionTests {

        @Test
        @DisplayName("Entity with 'fields' wrapper preserves wrapper")
        void testConvertEntity_WithFieldsWrapper() {

            AIContextVariable dateField = createContextVar("dueDate", "Date");
            AIContextVariable entityVar = createContextVar("task", "Entity");
            entityVar.setSubContextVariables(List.of(dateField));

            Map<String, Object> entity = Map.of(
                    "fields", new HashMap<>(Map.of("dueDate", "15-01-2024"))
            );
            Map<String, Object> input = Map.of("task", entity);

            Map<String, Object> result = converter.convertOutputVariables(
                    new HashMap<>(input),
                    List.of(new ExpectedOutput(entityVar, "task", "read")));

            @SuppressWarnings("unchecked")
            Map<String, Object> convertedEntity = (Map<String, Object>) result.get("task");
            assertTrue(convertedEntity.containsKey("fields"), "Should preserve 'fields' wrapper");

            @SuppressWarnings("unchecked")
            Map<String, Object> fields = (Map<String, Object>) convertedEntity.get("fields");
            assertInstanceOf(Long.class, fields.get("dueDate"), "Date should be converted to epoch");
        }

        @Test
        @DisplayName("Entity without 'fields' wrapper does NOT add wrapper (G15)")
        void testConvertEntity_WithoutFieldsWrapper() {

            AIContextVariable nameField = createContextVar("name", "Text");
            AIContextVariable entityVar = createContextVar("person", "Entity");
            entityVar.setSubContextVariables(List.of(nameField));

            Map<String, Object> entity = new HashMap<>(Map.of("name", "Alice"));
            Map<String, Object> input = Map.of("person", entity);

            Map<String, Object> result = converter.convertOutputVariables(
                    new HashMap<>(input),
                    List.of(new ExpectedOutput(entityVar, "person", "read")));

            @SuppressWarnings("unchecked")
            Map<String, Object> convertedEntity = (Map<String, Object>) result.get("person");
            // G15: Should NOT wrap in "fields" since input didn't have it
            assertFalse(convertedEntity.containsKey("fields"),
                    "Should NOT add 'fields' wrapper when input didn't have one");
            assertEquals("Alice", convertedEntity.get("name"));
        }

        @Test
        @DisplayName("EntityReference with full logical name is handled")
        void testConvertEntity_FullLogicalName() {

            AIContextVariable nameField = createContextVar("name", "Text");
            AIContextVariable entityVar = createContextVar("ref", "com.krista.fields.EntityReference");
            entityVar.setSubContextVariables(List.of(nameField));

            Map<String, Object> entity = new HashMap<>(Map.of("name", "Bob"));
            Map<String, Object> input = Map.of("ref", entity);

            Map<String, Object> result = converter.convertOutputVariables(
                    new HashMap<>(input),
                    List.of(new ExpectedOutput(entityVar, "ref", "read")));

            assertNotNull(result.get("ref"));
        }
    }

    // ========================================
    // Section 7: Table with {rows: [...]} Wrapper (G16)
    // ========================================

    @Nested
    @DisplayName("Table conversion")
    class TableConversionTests {

        @Test
        @DisplayName("Table with raw List of rows is converted")
        void testConvertTable_RawList() {

            AIContextVariable dateCol = createContextVar("date", "Date");
            AIContextVariable tableVar = createContextVar("schedule", "Table");
            tableVar.setSubContextVariables(List.of(dateCol));

            List<Map<String, Object>> rows = new ArrayList<>();
            rows.add(new HashMap<>(Map.of("date", "15-01-2024")));
            rows.add(new HashMap<>(Map.of("date", "16-01-2024")));

            Map<String, Object> input = Map.of("schedule", rows);

            Map<String, Object> result = converter.convertOutputVariables(
                    new HashMap<>(input),
                    List.of(new ExpectedOutput(tableVar, "schedule", "read")));

            @SuppressWarnings("unchecked")
            List<Object> convertedRows = (List<Object>) result.get("schedule");
            assertEquals(2, convertedRows.size());
        }

        @Test
        @DisplayName("Table with {rows: [...]} wrapper is handled (G16)")
        void testConvertTable_RowsWrapper() {

            AIContextVariable nameCol = createContextVar("name", "Text");
            AIContextVariable tableVar = createContextVar("people", "Table");
            tableVar.setSubContextVariables(List.of(nameCol));

            List<Map<String, Object>> rowData = new ArrayList<>();
            rowData.add(new HashMap<>(Map.of("name", "Alice")));
            rowData.add(new HashMap<>(Map.of("name", "Bob")));

            Map<String, Object> tableWithRows = Map.of("rows", rowData);
            Map<String, Object> input = Map.of("people", tableWithRows);

            Map<String, Object> result = converter.convertOutputVariables(
                    new HashMap<>(input),
                    List.of(new ExpectedOutput(tableVar, "people", "read")));

            @SuppressWarnings("unchecked")
            List<Object> convertedRows = (List<Object>) result.get("people");
            assertNotNull(convertedRows);
            assertEquals(2, convertedRows.size());

            @SuppressWarnings("unchecked")
            Map<String, Object> row1 = (Map<String, Object>) convertedRows.get(0);
            assertEquals("Alice", row1.get("name"));
        }

        @Test
        @DisplayName("Table with full logical name is handled")
        void testConvertTable_FullLogicalName() {

            AIContextVariable nameCol = createContextVar("name", "Text");
            AIContextVariable tableVar = createContextVar("data", "com.krista.fields.Table");
            tableVar.setSubContextVariables(List.of(nameCol));

            List<Map<String, Object>> rows = new ArrayList<>();
            rows.add(new HashMap<>(Map.of("name", "Test")));

            Map<String, Object> input = Map.of("data", rows);

            Map<String, Object> result = converter.convertOutputVariables(
                    new HashMap<>(input),
                    List.of(new ExpectedOutput(tableVar, "data", "read")));

            assertInstanceOf(List.class, result.get("data"));
        }
    }

    // ========================================
    // Section 8: Time-Only Parsing (G22)
    // ========================================

    @Nested
    @DisplayName("Time-only parsing")
    class TimeOnlyParsingTests {

        @Test
        @DisplayName("Time-only string 'HH:mm:ss' is parsed to epoch")
        void testTimeOnlyParsing_WithSeconds() {

            AIContextVariable timeVar = createContextVar("meetingTime", "Time");
            Map<String, Object> input = Map.of("meetingTime", "14:30:00");

            Map<String, Object> result = converter.convertOutputVariables(
                    new HashMap<>(input),
                    List.of(new ExpectedOutput(timeVar, "meetingTime", "read")));

            Object converted = result.get("meetingTime");
            assertInstanceOf(Long.class, converted, "Time-only should be converted to epoch");

            // Verify the epoch corresponds to today at 14:30:00 UTC
            long epoch = (Long) converted;
            long expectedEpoch = LocalTime.of(14, 30, 0)
                    .atDate(LocalDate.now(ZoneId.of("UTC")))
                    .atZone(ZoneId.of("UTC"))
                    .toInstant()
                    .toEpochMilli();
            assertEquals(expectedEpoch, epoch);
        }

        @Test
        @DisplayName("Time-only string 'HH:mm' is parsed to epoch")
        void testTimeOnlyParsing_WithoutSeconds() {

            AIContextVariable timeVar = createContextVar("start", "Time");
            Map<String, Object> input = Map.of("start", "09:15");

            Map<String, Object> result = converter.convertOutputVariables(
                    new HashMap<>(input),
                    List.of(new ExpectedOutput(timeVar, "start", "read")));

            Object converted = result.get("start");
            assertInstanceOf(Long.class, converted, "Time-only should be converted to epoch");
        }
    }

    // ========================================
    // Section 9: Edge Cases
    // ========================================

    @Nested
    @DisplayName("Edge cases")
    class EdgeCaseTests {

        @Test
        @DisplayName("Null output variables returns null")
        void testNullOutputVariables() {

            assertNull(converter.convertOutputVariables(null, List.of()));
        }

        @Test
        @DisplayName("Null expected outputs returns original map")
        void testNullExpectedOutputs() {

            Map<String, Object> input = Map.of("key", "value");
            assertSame(input, converter.convertOutputVariables(input, null));
        }

        @Test
        @DisplayName("Missing variable in output map is skipped")
        void testMissingVariable() {

            AIContextVariable var = createContextVar("missing", "Text");
            Map<String, Object> input = Map.of("other", "value");

            Map<String, Object> result = converter.convertOutputVariables(
                    new HashMap<>(input),
                    List.of(new ExpectedOutput(var, "missing", "read")));

            assertFalse(result.containsKey("missing"));
        }

        @Test
        @DisplayName("Unknown type passes value through")
        void testUnknownType() {

            AIContextVariable var = createContextVar("data", "UnknownType");
            Map<String, Object> input = Map.of("data", "raw-value");

            Map<String, Object> result = converter.convertOutputVariables(
                    new HashMap<>(input),
                    List.of(new ExpectedOutput(var, "data", "read")));

            assertEquals("raw-value", result.get("data"));
        }
    }

    // ========================================
    // Helper Methods
    // ========================================

    private AIContextVariable createContextVar(String name, String type) {

        AIContextVariable var = new AIContextVariable();
        var.setName(name);
        var.setType(type);
        return var;
    }
}
