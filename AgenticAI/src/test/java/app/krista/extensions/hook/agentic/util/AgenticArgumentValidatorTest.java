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
import app.krista.extensions.hook.agentic.dto.AIContextVariable;
import app.krista.extensions.hook.agentic.dto.ExpectedOutput;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link AgenticArgumentValidator}.
 *
 * <p>Uses real {@link app.krista.extensions.hook.common.validator.FieldValidatorRegistry}
 * and all production validators — no mocks for the validation framework.
 *
 * <p>Date constants:
 * <ul>
 *   <li>PAST_EPOCH   — 2020-01-01T00:00:00Z, solidly in the past</li>
 *   <li>FUTURE_EPOCH — 2030-01-01T00:00:00Z, solidly in the future</li>
 * </ul>
 */
class AgenticArgumentValidatorTest {

    // ==================== CONSTANTS ====================

    /** 2020-01-01T00:00:00Z — solidly in the past (avoids timing edge-cases). */
    private static final long PAST_EPOCH =
            LocalDate.of(2020, 1, 1).atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli();

    /** 2030-01-01T00:00:00Z — solidly in the future. */
    private static final long FUTURE_EPOCH =
            LocalDate.of(2030, 1, 1).atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli();

    private final AgenticArgumentValidator validator = new AgenticArgumentValidator();

    // ==================== HELPERS ====================

    private static AgenticToolDefinition tool(String name, ParameterDefinition... params) {

        return new AgenticToolDefinition(name, "desc", "catalog_request", List.of(params), false);
    }

    private static AgenticToolDefinition helperTool(String name, ParameterDefinition... params) {

        return new AgenticToolDefinition(name, "desc", "ask_a_person", List.of(params), true);
    }

    private static ParameterDefinition param(String name, String type, Map<String, Object> metadata) {

        return new ParameterDefinition(name, type, "desc", false, metadata);
    }

    private static ParameterDefinition param(String name, String type) {

        return new ParameterDefinition(name, type, "desc", false);
    }

    private static ExpectedOutput expectedOutput(String name, String type, Map<String, Object> metadata) {

        AIContextVariable var = new AIContextVariable(name, type);
        var.setMetaData(metadata);
        return new ExpectedOutput(var, "generate it");
    }

    // ==================== DATE VALIDATION ====================

    @Nested
    @DisplayName("Date validation")
    class DateValidation {

        @Test
        @DisplayName("Should produce error when past date violates allowPast=false")
        void pastDateViolatesAllowPastFalse() {

            // Given
            Map<String, Object> metadata = Map.of("allowPast", false, "allowFuture", true);
            AgenticToolDefinition toolDef = tool("bookAppointment",
                    param("appointmentDate", "com.krista.fields.Date", metadata));
            Map<String, Object> args = Map.of("appointmentDate", PAST_EPOCH);

            // When
            Map<String, Object> errors = validator.validateToolArguments("bookAppointment", args, List.of(toolDef));

            // Then
            assertFalse(errors.isEmpty(),
                    "Validation should produce errors for past date when allowPast=false");
            assertTrue(errors.containsKey("appointmentDate"),
                    "Error map should contain the appointmentDate field key");
            List<?> fieldErrors = (List<?>) errors.get("appointmentDate");
            assertFalse(fieldErrors.isEmpty(), "Field errors list must not be empty");
            assertTrue(fieldErrors.get(0).toString().contains("Past dates are not allowed"),
                    "Error message should mention that past dates are not allowed, was: " + fieldErrors.get(0));
        }

        @Test
        @DisplayName("Should pass when future date satisfies allowPast=false")
        void futureDatePassesWhenPastNotAllowed() {

            // Given
            Map<String, Object> metadata = Map.of("allowPast", false, "allowFuture", true);
            AgenticToolDefinition toolDef = tool("bookAppointment",
                    param("appointmentDate", "com.krista.fields.Date", metadata));
            Map<String, Object> args = Map.of("appointmentDate", FUTURE_EPOCH);

            // When
            Map<String, Object> errors = validator.validateToolArguments("bookAppointment", args, List.of(toolDef));

            // Then
            assertTrue(errors.isEmpty(),
                    "Valid future date should produce no errors when allowPast=false");
        }
    }

    // ==================== DATE RANGE VALIDATION ====================

    @Nested
    @DisplayName("Date range validation via allowedRange")
    class DateRangeValidation {

        @Test
        @DisplayName("Should produce error when date is before allowedRange.from")
        void dateBeforeAllowedRangeFromProducesError() {

            // Given — range covers 2025 only; PAST_EPOCH (2020) is outside
            long rangeFrom = LocalDate.of(2025, 1, 1).atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli();
            long rangeTo   = LocalDate.of(2025, 12, 31).atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli();
            Map<String, Object> metadata = Map.of(
                    "allowedRange", Map.of("from", rangeFrom, "to", rangeTo)
            );
            AgenticToolDefinition toolDef = tool("scheduleEvent",
                    param("eventDate", "com.krista.fields.Date", metadata));
            Map<String, Object> args = Map.of("eventDate", PAST_EPOCH);

            // When
            Map<String, Object> errors = validator.validateToolArguments("scheduleEvent", args, List.of(toolDef));

            // Then
            assertFalse(errors.isEmpty(), "Date before allowedRange.from should fail validation");
            assertTrue(errors.containsKey("eventDate"));
            List<?> fieldErrors = (List<?>) errors.get("eventDate");
            assertTrue(fieldErrors.get(0).toString().contains("before the allowed minimum date"),
                    "Error should mention minimum date, was: " + fieldErrors.get(0));
        }

        @Test
        @DisplayName("Should produce error when date is after allowedRange.to")
        void dateAfterAllowedRangeToProducesError() {

            // Given — range covers 2020 only; FUTURE_EPOCH (2030) is outside
            long rangeFrom = LocalDate.of(2020, 1, 1).atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli();
            long rangeTo   = LocalDate.of(2020, 12, 31).atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli();
            Map<String, Object> metadata = Map.of(
                    "allowedRange", Map.of("from", rangeFrom, "to", rangeTo)
            );
            AgenticToolDefinition toolDef = tool("scheduleEvent",
                    param("eventDate", "com.krista.fields.Date", metadata));
            Map<String, Object> args = Map.of("eventDate", FUTURE_EPOCH);

            // When
            Map<String, Object> errors = validator.validateToolArguments("scheduleEvent", args, List.of(toolDef));

            // Then
            assertFalse(errors.isEmpty(), "Date after allowedRange.to should fail validation");
            assertTrue(errors.containsKey("eventDate"));
            List<?> fieldErrors = (List<?>) errors.get("eventDate");
            assertTrue(fieldErrors.get(0).toString().contains("after the allowed maximum date"),
                    "Error should mention maximum date, was: " + fieldErrors.get(0));
        }

        @Test
        @DisplayName("Should pass when date is within allowedRange boundaries")
        void dateWithinAllowedRangePasses() {

            // Given — range is 2019-2031; PAST_EPOCH (2020) is within it
            long rangeFrom = LocalDate.of(2019, 1, 1).atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli();
            long rangeTo   = LocalDate.of(2031, 1, 1).atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli();
            Map<String, Object> metadata = Map.of(
                    "allowedRange", Map.of("from", rangeFrom, "to", rangeTo)
            );
            AgenticToolDefinition toolDef = tool("scheduleEvent",
                    param("eventDate", "com.krista.fields.Date", metadata));
            Map<String, Object> args = Map.of("eventDate", PAST_EPOCH);

            // When
            Map<String, Object> errors = validator.validateToolArguments("scheduleEvent", args, List.of(toolDef));

            // Then
            assertTrue(errors.isEmpty(), "Date within allowedRange should produce no errors");
        }
    }

    // ==================== NUMBER VALIDATION ====================

    @Nested
    @DisplayName("Number validation")
    class NumberValidation {

        @Test
        @DisplayName("Should produce error when number is below minValue")
        void numberBelowMinValueProducesError() {

            // Given
            Map<String, Object> metadata = Map.of("minValue", 100.0, "maxValue", 1000.0);
            AgenticToolDefinition toolDef = tool("createOrder",
                    param("amount", "com.krista.fields.Number", metadata));
            Map<String, Object> args = Map.of("amount", 5.0);

            // When
            Map<String, Object> errors = validator.validateToolArguments("createOrder", args, List.of(toolDef));

            // Then
            assertFalse(errors.isEmpty(), "Value below minValue should fail validation");
            assertTrue(errors.containsKey("amount"));
            List<?> fieldErrors = (List<?>) errors.get("amount");
            assertTrue(fieldErrors.get(0).toString().contains("Value must be at least 100.0"),
                    "Error should mention minimum value, was: " + fieldErrors.get(0));
        }

        @Test
        @DisplayName("Should pass when number is within range")
        void numberWithinRangePasses() {

            // Given
            Map<String, Object> metadata = Map.of("minValue", 100.0, "maxValue", 1000.0);
            AgenticToolDefinition toolDef = tool("createOrder",
                    param("amount", "com.krista.fields.Number", metadata));
            Map<String, Object> args = Map.of("amount", 500.0);

            // When
            Map<String, Object> errors = validator.validateToolArguments("createOrder", args, List.of(toolDef));

            // Then
            assertTrue(errors.isEmpty(), "Valid number within range should produce no errors");
        }
    }

    // ==================== TEXT VALIDATION ====================

    @Nested
    @DisplayName("Text validation")
    class TextValidation {

        @Test
        @DisplayName("Should produce error when string is shorter than minLength")
        void stringShorterThanMinLengthProducesError() {

            // Given
            Map<String, Object> metadata = Map.of("minLength", 5, "maxLength", 50);
            AgenticToolDefinition toolDef = tool("createNote",
                    param("title", "com.krista.fields.Text", metadata));
            Map<String, Object> args = Map.of("title", "Hi");

            // When
            Map<String, Object> errors = validator.validateToolArguments("createNote", args, List.of(toolDef));

            // Then
            assertFalse(errors.isEmpty(), "String shorter than minLength should fail validation");
            assertTrue(errors.containsKey("title"));
            List<?> fieldErrors = (List<?>) errors.get("title");
            assertTrue(fieldErrors.get(0).toString().contains("Minimum length required is 5"),
                    "Error should mention minimum length, was: " + fieldErrors.get(0));
        }

        @Test
        @DisplayName("Should pass when string length is within range")
        void stringWithinLengthRangePasses() {

            // Given
            Map<String, Object> metadata = Map.of("minLength", 5, "maxLength", 50);
            AgenticToolDefinition toolDef = tool("createNote",
                    param("title", "com.krista.fields.Text", metadata));
            Map<String, Object> args = Map.of("title", "Hello World");

            // When
            Map<String, Object> errors = validator.validateToolArguments("createNote", args, List.of(toolDef));

            // Then
            assertTrue(errors.isEmpty(), "String within length range should produce no errors");
        }
    }

    // ==================== PICKONE VALIDATION ====================

    @Nested
    @DisplayName("PickOne validation")
    class PickOneValidation {

        @Test
        @DisplayName("Should produce error when value is not in options list")
        void valueNotInOptionsProducesError() {

            // Given
            Map<String, Object> metadata = Map.of("options", List.of("Low", "Medium", "High"));
            AgenticToolDefinition toolDef = tool("createTicket",
                    param("priority", "com.krista.fields.PickOne", metadata));
            Map<String, Object> args = Map.of("priority", "Critical");

            // When
            Map<String, Object> errors = validator.validateToolArguments("createTicket", args, List.of(toolDef));

            // Then
            assertFalse(errors.isEmpty(), "Value not in options should fail validation");
            assertTrue(errors.containsKey("priority"));
            List<?> fieldErrors = (List<?>) errors.get("priority");
            assertTrue(fieldErrors.get(0).toString().contains("Invalid value: Critical"),
                    "Error should mention the invalid value, was: " + fieldErrors.get(0));
        }

        @Test
        @DisplayName("Should pass when value is a valid option")
        void validOptionPasses() {

            // Given
            Map<String, Object> metadata = Map.of("options", List.of("Low", "Medium", "High"));
            AgenticToolDefinition toolDef = tool("createTicket",
                    param("priority", "com.krista.fields.PickOne", metadata));
            Map<String, Object> args = Map.of("priority", "High");

            // When
            Map<String, Object> errors = validator.validateToolArguments("createTicket", args, List.of(toolDef));

            // Then
            assertTrue(errors.isEmpty(), "Valid option should produce no errors");
        }
    }

    // ==================== CHECKBOXES VALIDATION ====================

    @Nested
    @DisplayName("Checkboxes validation")
    class CheckboxesValidation {

        @Test
        @DisplayName("Should produce error when selected value is not in options list")
        void invalidOptionInCheckboxesProducesError() {

            // Given
            Map<String, Object> metadata = Map.of(
                    "options", List.of("Email", "SMS", "Push"),
                    "minSelection", 1,
                    "maxSelection", 2
            );
            AgenticToolDefinition toolDef = tool("setNotifications",
                    param("channels", "com.krista.fields.Checkboxes", metadata));
            Map<String, Object> args = Map.of("channels", List.of("Email", "Fax"));

            // When
            Map<String, Object> errors = validator.validateToolArguments("setNotifications", args, List.of(toolDef));

            // Then
            assertFalse(errors.isEmpty(), "Invalid checkbox option should fail validation");
            assertTrue(errors.containsKey("channels"));
            List<?> fieldErrors = (List<?>) errors.get("channels");
            assertTrue(fieldErrors.get(0).toString().contains("Invalid value: Fax"),
                    "Error should mention the invalid option, was: " + fieldErrors.get(0));
        }

        @Test
        @DisplayName("Should produce error when selection count exceeds maxSelection")
        void tooManySelectionsProducesError() {

            // Given
            Map<String, Object> metadata = Map.of(
                    "options", List.of("Email", "SMS", "Push"),
                    "minSelection", 1,
                    "maxSelection", 2
            );
            AgenticToolDefinition toolDef = tool("setNotifications",
                    param("channels", "com.krista.fields.Checkboxes", metadata));
            Map<String, Object> args = Map.of("channels", List.of("Email", "SMS", "Push"));

            // When
            Map<String, Object> errors = validator.validateToolArguments("setNotifications", args, List.of(toolDef));

            // Then
            assertFalse(errors.isEmpty(), "Too many selections should fail validation");
            assertTrue(errors.containsKey("channels"));
            List<?> fieldErrors = (List<?>) errors.get("channels");
            boolean foundMaxError = fieldErrors.stream()
                    .anyMatch(e -> e.toString().contains("At most 2 selections allowed"));
            assertTrue(foundMaxError,
                    "Error should mention max selections, actual errors: " + fieldErrors);
        }

        @Test
        @DisplayName("Should produce error when selection count is below minSelection")
        void tooFewSelectionsProducesError() {

            // Given
            Map<String, Object> metadata = Map.of(
                    "options", List.of("Email", "SMS", "Push"),
                    "minSelection", 2,
                    "maxSelection", 3
            );
            AgenticToolDefinition toolDef = tool("setNotifications",
                    param("channels", "com.krista.fields.Checkboxes", metadata));
            Map<String, Object> args = Map.of("channels", List.of("Email"));

            // When
            Map<String, Object> errors = validator.validateToolArguments("setNotifications", args, List.of(toolDef));

            // Then
            assertFalse(errors.isEmpty(), "Too few selections should fail validation");
            assertTrue(errors.containsKey("channels"));
            List<?> fieldErrors = (List<?>) errors.get("channels");
            boolean foundMinError = fieldErrors.stream()
                    .anyMatch(e -> e.toString().contains("At least 2 selections required"));
            assertTrue(foundMinError,
                    "Error should mention min selections, actual errors: " + fieldErrors);
        }

        @Test
        @DisplayName("Should pass when valid options are selected within min/max bounds")
        void validCheckboxesPass() {

            // Given
            Map<String, Object> metadata = Map.of(
                    "options", List.of("Email", "SMS", "Push"),
                    "minSelection", 1,
                    "maxSelection", 2
            );
            AgenticToolDefinition toolDef = tool("setNotifications",
                    param("channels", "com.krista.fields.Checkboxes", metadata));
            Map<String, Object> args = Map.of("channels", List.of("Email", "SMS"));

            // When
            Map<String, Object> errors = validator.validateToolArguments("setNotifications", args, List.of(toolDef));

            // Then
            assertTrue(errors.isEmpty(), "Valid checkboxes within bounds should produce no errors");
        }
    }

    // ==================== HELPER TOOL SKIP ====================

    @Nested
    @DisplayName("Helper tool bypass")
    class HelperToolBypass {

        @Test
        @DisplayName("Should always return empty errors for helper tools regardless of arguments")
        void helperToolAlwaysReturnsEmptyErrors() {

            // Given — helper tool with date parameter whose constraint would fail
            Map<String, Object> metadata = Map.of("allowPast", false, "allowFuture", true);
            AgenticToolDefinition helpTool = helperTool("ask_a_person",
                    param("responseDate", "com.krista.fields.Date", metadata));
            // Past date would normally fail allowPast=false, but helper tools are skipped
            Map<String, Object> args = Map.of("responseDate", PAST_EPOCH);

            // When
            Map<String, Object> errors = validator.validateToolArguments(
                    "ask_a_person", args, List.of(helpTool));

            // Then
            assertTrue(errors.isEmpty(),
                    "Helper tools must always skip validation and return empty errors");
        }
    }

    // ==================== MISSING METADATA ====================

    @Nested
    @DisplayName("Missing metadata")
    class MissingMetadata {

        @Test
        @DisplayName("Should not error when parameter has null metadata (DefaultFieldValidator is used)")
        void nullMetadataDoesNotError() {

            // Given — no metadata means DefaultFieldValidator returns null = valid
            AgenticToolDefinition toolDef = tool("createRecord",
                    param("description", "com.krista.fields.Text")); // no metadata
            Map<String, Object> args = Map.of("description", "some text");

            // When
            Map<String, Object> errors = validator.validateToolArguments(
                    "createRecord", args, List.of(toolDef));

            // Then
            assertTrue(errors.isEmpty(),
                    "Parameter with null metadata should not produce validation errors");
        }
    }

    // ==================== UNKNOWN TOOL ====================

    @Nested
    @DisplayName("Unknown tool")
    class UnknownTool {

        @Test
        @DisplayName("Should return empty errors when tool name is not in the tools list")
        void unknownToolReturnsEmptyErrors() {

            // Given — tools list does not contain "missingTool"
            AgenticToolDefinition toolDef = tool("realTool",
                    param("amount", "com.krista.fields.Number", Map.of("minValue", 100.0)));
            Map<String, Object> args = Map.of("amount", 1.0); // Would fail if validated

            // When
            Map<String, Object> errors = validator.validateToolArguments(
                    "missingTool", args, List.of(toolDef));

            // Then
            assertTrue(errors.isEmpty(),
                    "Unknown tool not in the tools list should return empty errors (graceful degradation)");
        }
    }

    // ==================== NULL/MISSING VALUES ====================

    @Nested
    @DisplayName("Null and missing argument values")
    class NullValues {

        @Test
        @DisplayName("Should skip gracefully when argument value is null")
        void nullArgumentValueIsSkipped() {

            // Given — argument map has a null value for the field
            Map<String, Object> metadata = Map.of("minValue", 100.0);
            AgenticToolDefinition toolDef = tool("createOrder",
                    param("amount", "com.krista.fields.Number", metadata));

            Map<String, Object> args = new HashMap<>();
            args.put("amount", null); // null value

            // When — must not throw NullPointerException
            Map<String, Object> errors = validator.validateToolArguments(
                    "createOrder", args, List.of(toolDef));

            // Then
            assertTrue(errors.isEmpty(),
                    "Null argument values should be skipped gracefully, not cause NPE or errors");
        }

        @Test
        @DisplayName("Should return empty errors when convertedArgs map is empty")
        void emptyArgsMapReturnsEmpty() {

            // Given
            AgenticToolDefinition toolDef = tool("createOrder",
                    param("amount", "com.krista.fields.Number", Map.of("minValue", 100.0)));

            // When
            Map<String, Object> errors = validator.validateToolArguments(
                    "createOrder", Map.of(), List.of(toolDef));

            // Then
            assertTrue(errors.isEmpty(), "Empty args map should return empty errors");
        }
    }

    // ==================== OUTPUT VARIABLE VALIDATION ====================

    @Nested
    @DisplayName("Output variable validation")
    class OutputVariableValidation {

        @Test
        @DisplayName("Should produce error for past date when output variable has allowPast=false")
        void outputVariablePastDateViolatesConstraint() {

            // Given
            Map<String, Object> metadata = Map.of("allowPast", false, "allowFuture", true);
            ExpectedOutput output = expectedOutput("completionDate", "com.krista.fields.Date", metadata);
            Map<String, Object> convertedOutputs = Map.of("completionDate", PAST_EPOCH);

            // When
            Map<String, Object> errors = validator.validateOutputVariables(
                    convertedOutputs, List.of(output));

            // Then
            assertFalse(errors.isEmpty(),
                    "Output variable with past date should fail when allowPast=false");
            assertTrue(errors.containsKey("completionDate"));
            List<?> fieldErrors = (List<?>) errors.get("completionDate");
            assertTrue(fieldErrors.get(0).toString().contains("Past dates are not allowed"),
                    "Error should mention past dates not allowed, was: " + fieldErrors.get(0));
        }

        @Test
        @DisplayName("Should pass for future date when output variable has allowPast=false")
        void outputVariableFutureDatePasses() {

            // Given
            Map<String, Object> metadata = Map.of("allowPast", false, "allowFuture", true);
            ExpectedOutput output = expectedOutput("completionDate", "com.krista.fields.Date", metadata);
            Map<String, Object> convertedOutputs = Map.of("completionDate", FUTURE_EPOCH);

            // When
            Map<String, Object> errors = validator.validateOutputVariables(
                    convertedOutputs, List.of(output));

            // Then
            assertTrue(errors.isEmpty(), "Valid future date should produce no errors");
        }

        @Test
        @DisplayName("Should produce error when output number is below minValue")
        void outputVariableNumberBelowMinValue() {

            // Given
            Map<String, Object> metadata = Map.of("minValue", 1.0, "maxValue", 100.0);
            ExpectedOutput output = expectedOutput("score", "com.krista.fields.Number", metadata);
            Map<String, Object> convertedOutputs = Map.of("score", 0.0);

            // When
            Map<String, Object> errors = validator.validateOutputVariables(
                    convertedOutputs, List.of(output));

            // Then
            assertFalse(errors.isEmpty(), "Output number below minValue should fail");
            assertTrue(errors.containsKey("score"));
        }

        @Test
        @DisplayName("Should return empty errors when convertedOutputs is empty")
        void emptyConvertedOutputsReturnsEmpty() {

            // Given
            Map<String, Object> metadata = Map.of("allowPast", false);
            ExpectedOutput output = expectedOutput("aDate", "com.krista.fields.Date", metadata);

            // When
            Map<String, Object> errors = validator.validateOutputVariables(
                    Map.of(), List.of(output));

            // Then
            assertTrue(errors.isEmpty(), "Empty convertedOutputs map should return empty errors");
        }

        @Test
        @DisplayName("Should return empty errors when expectedOutputs list is empty")
        void emptyExpectedOutputsReturnsEmpty() {

            // When
            Map<String, Object> errors = validator.validateOutputVariables(
                    Map.of("someField", PAST_EPOCH), List.of());

            // Then
            assertTrue(errors.isEmpty(), "Empty expectedOutputs list should return empty errors");
        }
    }

    // ==================== MIXED VALID/INVALID ====================

    @Nested
    @DisplayName("Mixed valid and invalid arguments")
    class MixedValidInvalid {

        @Test
        @DisplayName("Should only include errors for invalid fields when some arguments are valid")
        void onlyInvalidFieldsAppearInErrors() {

            // Given — two params: amount (invalid: below min), description (valid)
            Map<String, Object> numberMeta = Map.of("minValue", 100.0, "maxValue", 1000.0);
            Map<String, Object> textMeta   = Map.of("minLength", 3, "maxLength", 50);
            AgenticToolDefinition toolDef = tool("createOrder",
                    param("amount",      "com.krista.fields.Number", numberMeta),
                    param("description", "com.krista.fields.Text",   textMeta));

            Map<String, Object> args = Map.of(
                    "amount",      5.0,        // invalid — below minValue 100
                    "description", "Valid note" // valid — length within range
            );

            // When
            Map<String, Object> errors = validator.validateToolArguments("createOrder", args, List.of(toolDef));

            // Then
            assertTrue(errors.containsKey("amount"),
                    "amount (below minValue) should be in error map");
            assertFalse(errors.containsKey("description"),
                    "description (valid) must NOT appear in error map");
        }
    }

    // ==================== FORMAT VALIDATION ERRORS FOR LLM ====================

    @Nested
    @DisplayName("formatValidationErrorsForLLM")
    class FormatValidationErrorsForLlm {

        @Test
        @DisplayName("Should produce readable string from error map")
        void producesReadableStringFromErrorMap() {

            // Given
            Map<String, Object> errors = Map.of(
                    "appointmentDate", List.of("Past dates are not allowed"),
                    "amount",          List.of("Value must be at least 100.0")
            );

            // When
            String formatted = validator.formatValidationErrorsForLLM(errors);

            // Then
            assertNotNull(formatted, "Formatted string must not be null");
            assertTrue(formatted.startsWith("Validation errors:"),
                    "Formatted string must start with 'Validation errors:', was: " + formatted);
            assertTrue(formatted.contains("appointmentDate"),
                    "Formatted string must contain the field name 'appointmentDate'");
            assertTrue(formatted.contains("amount"),
                    "Formatted string must contain the field name 'amount'");
            assertTrue(formatted.contains("Past dates are not allowed"),
                    "Formatted string must contain the error message");
        }

        @Test
        @DisplayName("Should return empty string when error map is empty")
        void returnsEmptyStringForEmptyErrorMap() {

            // When
            String formatted = validator.formatValidationErrorsForLLM(Map.of());

            // Then
            assertEquals("", formatted, "Empty error map should produce empty string");
        }

        @Test
        @DisplayName("Should return empty string when error map is null")
        void returnsEmptyStringForNullErrorMap() {

            // When
            String formatted = validator.formatValidationErrorsForLLM(null);

            // Then
            assertEquals("", formatted, "Null error map should produce empty string");
        }
    }

    // ==================== PHONE TYPE EXCLUDED ====================

    @Nested
    @DisplayName("Excluded type: Phone")
    class PhoneTypeExcluded {

        @Test
        @DisplayName("Should return empty errors for Phone parameter regardless of value")
        void phoneParameterIsSkipped() {

            // Given — Phone type is excluded (PhoneValidator expects PhoneNumber object, not Map)
            AgenticToolDefinition toolDef = tool("callCustomer",
                    param("contactNumber", "com.krista.fields.Phone"));
            // Any value — even one that would fail PhoneValidator — should be skipped
            Map<String, Object> args = Map.of("contactNumber", Map.of("countryCode", 1, "nationalNumber", 5551234567L));

            // When
            Map<String, Object> errors = validator.validateToolArguments("callCustomer", args, List.of(toolDef));

            // Then
            assertTrue(errors.isEmpty(),
                    "Phone type is excluded from Phase 1 validation and must produce no errors");
        }
    }

    // ==================== MULTIFIELD TYPE EXCLUDED ====================

    @Nested
    @DisplayName("Excluded type: MultiField")
    class MultiFieldTypeExcluded {

        @Test
        @DisplayName("Should return empty errors for MultiField parameter regardless of value")
        void multiFieldParameterIsSkipped() {

            // Given
            AgenticToolDefinition toolDef = tool("createContact",
                    param("address", "com.krista.fields.MultiField"));
            Map<String, Object> args = Map.of("address", Map.of("street", "123 Main St", "city", "Springfield"));

            // When
            Map<String, Object> errors = validator.validateToolArguments("createContact", args, List.of(toolDef));

            // Then
            assertTrue(errors.isEmpty(),
                    "MultiField type is excluded from Phase 1 validation and must produce no errors");
        }
    }

    // ==================== LIST TYPE EXCLUDED ====================

    @Nested
    @DisplayName("Excluded type: List")
    class ListTypeExcluded {

        @Test
        @DisplayName("Should return empty errors for List parameter regardless of value")
        void listParameterIsSkipped() {

            // Given
            AgenticToolDefinition toolDef = tool("createProject",
                    param("tags", "com.krista.fields.List"));
            Map<String, Object> args = Map.of("tags", List.of("tag1", "tag2"));

            // When
            Map<String, Object> errors = validator.validateToolArguments("createProject", args, List.of(toolDef));

            // Then
            assertTrue(errors.isEmpty(),
                    "List type is excluded from Phase 1 validation and must produce no errors");
        }
    }

    // ==================== CONVERSION FALLBACK INTERACTION ====================

    @Nested
    @DisplayName("Conversion fallback interaction")
    class ConversionFallback {

        @Test
        @DisplayName("Should produce 'Invalid date format' error when value is an unparseable String")
        void unparseableDateStringProducesInvalidDateFormatError() {

            // Given — ToolArgumentConverter leaves unparseable strings as-is.
            // DateValidator receives a String instead of a Long and returns "Invalid date format".
            Map<String, Object> metadata = Map.of("allowPast", false, "allowFuture", true);
            AgenticToolDefinition toolDef = tool("bookAppointment",
                    param("appointmentDate", "com.krista.fields.Date", metadata));
            // Simulating what ToolArgumentConverter.convertArguments returns for an unparseable date
            Map<String, Object> args = Map.of("appointmentDate", "not-a-valid-date-string");

            // When — must not throw; must produce a clear error
            Map<String, Object> errors = validator.validateToolArguments("bookAppointment", args, List.of(toolDef));

            // Then
            assertFalse(errors.isEmpty(),
                    "An unparseable date string should produce a validation error, not a crash");
            assertTrue(errors.containsKey("appointmentDate"),
                    "Error map should contain the appointmentDate field");
            List<?> fieldErrors = (List<?>) errors.get("appointmentDate");
            assertTrue(fieldErrors.get(0).toString().toLowerCase().contains("invalid date format"),
                    "Error should mention 'Invalid date format', was: " + fieldErrors.get(0));
        }
    }
}
