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

import app.krista.extensions.hook.agentic.dto.AgenticToolDefinition;
import app.krista.extensions.hook.agentic.dto.AgenticToolDefinition.ParameterDefinition;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ToolArgumentConverterTest {

    private final ToolArgumentConverter converter = new ToolArgumentConverter();

    // ==================== HELPERS ====================

    private static AgenticToolDefinition tool(String name, ParameterDefinition... params) {

        return new AgenticToolDefinition(name, "desc", "entity", List.of(params), false);
    }

    private static ParameterDefinition param(String name, String type) {

        return new ParameterDefinition(name, type, "desc", true);
    }

    private static ParameterDefinition param(String name, String type, Map<String, Object> metadata) {

        return new ParameterDefinition(name, type, "desc", true, metadata);
    }

    // ==================== DATE CONVERSION ====================

    @Nested
    @DisplayName("Date conversion")
    class DateConversion {

        @Test
        @DisplayName("converts human-readable date string to epoch millis")
        void convertsDateStringToEpoch() {

            AgenticToolDefinition toolDef = tool("myTool", param("Date Time", "Date"));
            Map<String, Object> args = Map.of("Date Time", "01-05-2026 17:00:00");

            Map<String, Object> result = converter.convertArguments("myTool", args, List.of(toolDef));

            assertInstanceOf(Long.class, result.get("Date Time"));
            long epoch = (Long) result.get("Date Time");
            // 01-05-2026 17:00:00 UTC = 1777338000000
            assertEquals(1777654800000L, epoch);
        }

        @Test
        @DisplayName("converts date-only string to epoch millis (start of day)")
        void convertsDateOnlyToEpoch() {

            AgenticToolDefinition toolDef = tool("myTool", param("date", "Date"));
            Map<String, Object> args = Map.of("date", "15-01-2024");

            Map<String, Object> result = converter.convertArguments("myTool", args, List.of(toolDef));

            assertInstanceOf(Long.class, result.get("date"));
            long epoch = (Long) result.get("date");
            // 15-01-2024 00:00:00 UTC
            ZonedDateTime expected = LocalDate.of(2024, 1, 15).atStartOfDay(ZoneId.of("UTC"));
            assertEquals(expected.toInstant().toEpochMilli(), epoch);
        }

        @Test
        @DisplayName("passes through epoch number unchanged")
        void passesEpochNumberThrough() {

            AgenticToolDefinition toolDef = tool("myTool", param("date", "Date"));
            Map<String, Object> args = Map.of("date", 1705276800000L);

            Map<String, Object> result = converter.convertArguments("myTool", args, List.of(toolDef));

            assertEquals(1705276800000L, result.get("date"));
        }

        @Test
        @DisplayName("converts time-only string to epoch with today's date")
        void convertsTimeOnlyToEpoch() {

            AgenticToolDefinition toolDef = tool("myTool", param("time", "Time"));
            Map<String, Object> args = Map.of("time", "14:30:00");

            Map<String, Object> result = converter.convertArguments("myTool", args, List.of(toolDef));

            assertInstanceOf(Long.class, result.get("time"));
            long epoch = (Long) result.get("time");
            ZonedDateTime expected = LocalTime.of(14, 30, 0)
                    .atDate(LocalDate.now(ZoneId.of("UTC")))
                    .atZone(ZoneId.of("UTC"));
            assertEquals(expected.toInstant().toEpochMilli(), epoch);
        }

        @Test
        @DisplayName("keeps unparseable date string as-is")
        void keepsUnparseableDateString() {

            AgenticToolDefinition toolDef = tool("myTool", param("date", "Date"));
            Map<String, Object> args = Map.of("date", "not-a-date");

            Map<String, Object> result = converter.convertArguments("myTool", args, List.of(toolDef));

            assertEquals("not-a-date", result.get("date"));
        }

        @Test
        @DisplayName("handles com.krista.fields.Date type name")
        void handlesFullyQualifiedDateType() {

            AgenticToolDefinition toolDef = tool("myTool", param("Date Time", "com.krista.fields.Date"));
            Map<String, Object> args = Map.of("Date Time", "01-05-2026 17:00:00");

            Map<String, Object> result = converter.convertArguments("myTool", args, List.of(toolDef));

            assertInstanceOf(Long.class, result.get("Date Time"));
        }
    }

    // ==================== DATE RANGE CONVERSION ====================

    @Nested
    @DisplayName("Date range conversion")
    class DateRangeConversion {

        @Test
        @DisplayName("converts date range map with string dates to epochs")
        void convertsDateRangeToEpochs() {

            AgenticToolDefinition toolDef = tool("myTool", param("range", "DateRange"));
            Map<String, Object> rangeMap = Map.of(
                    "from", "01-01-2024 00:00:00",
                    "to", "31-01-2024 23:59:59"
            );
            Map<String, Object> args = Map.of("range", rangeMap);

            Map<String, Object> result = converter.convertArguments("myTool", args, List.of(toolDef));

            @SuppressWarnings("unchecked")
            Map<String, Object> convertedRange = (Map<String, Object>) result.get("range");
            assertInstanceOf(Long.class, convertedRange.get("from"));
            assertInstanceOf(Long.class, convertedRange.get("to"));
        }
    }

    // ==================== NUMBER CONVERSION ====================

    @Nested
    @DisplayName("Number conversion")
    class NumberConversion {

        @Test
        @DisplayName("converts integer string to Long")
        void convertsIntegerStringToLong() {

            AgenticToolDefinition toolDef = tool("myTool", param("amount", "Number"));
            Map<String, Object> args = Map.of("amount", "42");

            Map<String, Object> result = converter.convertArguments("myTool", args, List.of(toolDef));

            assertEquals(42L, result.get("amount"));
        }

        @Test
        @DisplayName("converts decimal string to Double")
        void convertsDecimalStringToDouble() {

            AgenticToolDefinition toolDef = tool("myTool", param("amount", "Currency"));
            Map<String, Object> args = Map.of("amount", "34.50");

            Map<String, Object> result = converter.convertArguments("myTool", args, List.of(toolDef));

            assertEquals(34.50, result.get("amount"));
        }

        @Test
        @DisplayName("passes through Number instances unchanged")
        void passesNumberThrough() {

            AgenticToolDefinition toolDef = tool("myTool", param("amount", "Number"));
            Map<String, Object> args = Map.of("amount", 34.0);

            Map<String, Object> result = converter.convertArguments("myTool", args, List.of(toolDef));

            assertEquals(34.0, result.get("amount"));
        }

        @Test
        @DisplayName("keeps non-numeric string as-is")
        void keepsNonNumericString() {

            AgenticToolDefinition toolDef = tool("myTool", param("amount", "Number"));
            Map<String, Object> args = Map.of("amount", "not-a-number");

            Map<String, Object> result = converter.convertArguments("myTool", args, List.of(toolDef));

            assertEquals("not-a-number", result.get("amount"));
        }
    }

    // ==================== BOOLEAN CONVERSION ====================

    @Nested
    @DisplayName("Boolean conversion")
    class BooleanConversion {

        @Test
        @DisplayName("converts string true/false to Boolean")
        void convertsStringTrueFalse() {

            AgenticToolDefinition toolDef = tool("myTool",
                    param("active", "Boolean"),
                    param("disabled", "Switch"));
            Map<String, Object> args = Map.of("active", "true", "disabled", "no");

            Map<String, Object> result = converter.convertArguments("myTool", args, List.of(toolDef));

            assertEquals(Boolean.TRUE, result.get("active"));
            assertEquals(Boolean.FALSE, result.get("disabled"));
        }

        @Test
        @DisplayName("passes through Boolean instances unchanged")
        void passesBooleanThrough() {

            AgenticToolDefinition toolDef = tool("myTool", param("active", "Boolean"));
            Map<String, Object> args = Map.of("active", true);

            Map<String, Object> result = converter.convertArguments("myTool", args, List.of(toolDef));

            assertEquals(true, result.get("active"));
        }
    }

    // ==================== PHONE CONVERSION ====================

    @Nested
    @DisplayName("Phone conversion")
    class PhoneConversion {

        @Test
        @DisplayName("converts phone string to structured format")
        void convertsPhoneString() {

            AgenticToolDefinition toolDef = tool("myTool",
                    param("phone", "Phone", Map.of("countryCode", 91)));
            Map<String, Object> args = Map.of("phone", "9876543210");

            Map<String, Object> result = converter.convertArguments("myTool", args, List.of(toolDef));

            @SuppressWarnings("unchecked")
            Map<String, Object> phone = (Map<String, Object>) result.get("phone");
            assertEquals(91, phone.get("countryCode"));
            assertEquals(9876543210L, phone.get("nationalNumber"));
        }

        @Test
        @DisplayName("passes through Map phone value unchanged")
        void passesMapPhoneThrough() {

            AgenticToolDefinition toolDef = tool("myTool", param("phone", "Phone"));
            Map<String, Object> phoneMap = Map.of("countryCode", 1, "nationalNumber", 5551234567L);
            Map<String, Object> args = Map.of("phone", phoneMap);

            Map<String, Object> result = converter.convertArguments("myTool", args, List.of(toolDef));

            assertEquals(phoneMap, result.get("phone"));
        }
    }

    // ==================== RATING CONVERSION ====================

    @Nested
    @DisplayName("Rating conversion")
    class RatingConversion {

        @Test
        @DisplayName("converts rating string to integer")
        void convertsRatingString() {

            AgenticToolDefinition toolDef = tool("myTool", param("rating", "Rating"));
            Map<String, Object> args = Map.of("rating", "4");

            Map<String, Object> result = converter.convertArguments("myTool", args, List.of(toolDef));

            assertEquals(4, result.get("rating"));
        }
    }

    // ==================== CHECKBOXES CONVERSION ====================

    @Nested
    @DisplayName("Checkboxes conversion")
    class CheckboxesConversion {

        @Test
        @DisplayName("passes through List unchanged")
        void passesListThrough() {

            AgenticToolDefinition toolDef = tool("myTool", param("tags", "Checkboxes"));
            List<String> tags = List.of("A", "B");
            Map<String, Object> args = Map.of("tags", tags);

            Map<String, Object> result = converter.convertArguments("myTool", args, List.of(toolDef));

            assertEquals(tags, result.get("tags"));
        }

        @Test
        @DisplayName("wraps single string value in list")
        void wrapsSingleStringInList() {

            AgenticToolDefinition toolDef = tool("myTool", param("tags", "PickMany"));
            Map<String, Object> args = Map.of("tags", "Option A");

            Map<String, Object> result = converter.convertArguments("myTool", args, List.of(toolDef));

            assertEquals(List.of("Option A"), result.get("tags"));
        }
    }

    // ==================== EDGE CASES ====================

    @Nested
    @DisplayName("Edge cases")
    class EdgeCases {

        @Test
        @DisplayName("returns null/empty arguments as-is")
        void returnsNullArgumentsAsIs() {

            assertNull(converter.convertArguments("tool", null, List.of()));
            assertTrue(converter.convertArguments("tool", Map.of(), List.of()).isEmpty());
        }

        @Test
        @DisplayName("returns arguments unchanged when tool not found")
        void returnsUnchangedWhenToolNotFound() {

            Map<String, Object> args = Map.of("date", "01-05-2026 17:00:00");

            Map<String, Object> result = converter.convertArguments("unknownTool", args, List.of());

            assertEquals("01-05-2026 17:00:00", result.get("date"));
        }

        @Test
        @DisplayName("skips arguments without matching parameter definitions")
        void skipsUnknownParams() {

            AgenticToolDefinition toolDef = tool("myTool", param("known", "Text"));
            Map<String, Object> args = Map.of("known", "hello", "unknown", "world");

            Map<String, Object> result = converter.convertArguments("myTool", args, List.of(toolDef));

            assertEquals("hello", result.get("known"));
            assertEquals("world", result.get("unknown"));
        }

        @Test
        @DisplayName("passes Text and PickOne types through unchanged")
        void passesTextAndPickOneThrough() {

            AgenticToolDefinition toolDef = tool("myTool",
                    param("name", "Text"),
                    param("category", "PickOne"));
            Map<String, Object> args = Map.of("name", "John", "category", "Food");

            Map<String, Object> result = converter.convertArguments("myTool", args, List.of(toolDef));

            assertEquals("John", result.get("name"));
            assertEquals("Food", result.get("category"));
        }
    }

    // ==================== MULTI-ARGUMENT TOOL ====================

    @Nested
    @DisplayName("Multi-argument tool")
    class MultiArgument {

        @Test
        @DisplayName("converts multiple typed arguments in one call")
        void convertsMultipleArguments() {

            AgenticToolDefinition toolDef = tool("addExpense",
                    param("Account", "PickOne"),
                    param("Category", "PickOne"),
                    param("Amount", "Number"),
                    param("Note", "Text"),
                    param("Date Time", "Date"));

            Map<String, Object> args = Map.of(
                    "Account", "HDFC",
                    "Category", "Food",
                    "Amount", 34.0,
                    "Note", "",
                    "Date Time", "01-05-2026 17:00:00"
            );

            Map<String, Object> result = converter.convertArguments("addExpense", args, List.of(toolDef));

            assertEquals("HDFC", result.get("Account"));
            assertEquals("Food", result.get("Category"));
            assertEquals(34.0, result.get("Amount"));
            assertEquals("", result.get("Note"));
            assertInstanceOf(Long.class, result.get("Date Time"));
            assertEquals(1777654800000L, result.get("Date Time"));
        }
    }

    // ==================== TYPE NORMALIZATION ====================

    @Nested
    @DisplayName("Type normalization")
    class TypeNormalization {

        @Test
        @DisplayName("delegates to OutputVariableConverter.normalizeType")
        void delegatesToOutputVariableConverter() {

            assertEquals("date", ToolArgumentConverter.normalizeType("Date"));
            assertEquals("date", ToolArgumentConverter.normalizeType("com.krista.fields.Date"));
            assertEquals("number", ToolArgumentConverter.normalizeType("Number"));
            assertEquals("", ToolArgumentConverter.normalizeType(null));
        }
    }
}
