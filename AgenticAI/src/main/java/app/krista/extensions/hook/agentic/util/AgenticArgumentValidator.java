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
import app.krista.extensions.hook.common.validator.FieldValidator;
import app.krista.extensions.hook.common.validator.FieldValidatorRegistry;
import app.krista.model.field.NamedField;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;

/**
 * Validates LLM-produced tool arguments and output variables against field-level constraints
 * by bridging the agentic type system ({@link ParameterDefinition}, {@link AIContextVariable})
 * to the existing validator framework ({@link FieldValidatorRegistry}, {@link NamedField}).
 *
 * <p>This class is a pure adapter — it contains no validation logic of its own. It constructs
 * {@link NamedField} instances from agentic type carriers and delegates to the registered
 * {@link FieldValidator} for each field type.
 *
 * <p>Excluded field types (Phase 1): {@code phone}, {@code multifield}, {@code list}.
 * These have known type-bridge incompatibilities with their respective validators.
 * See the technical spec (Section 3.6) for details and future fix plans.
 *
 * <p>Thread-safety: all methods are stateless. The static {@link FieldValidatorRegistry}
 * singleton holds stateless validators and is safe to share across threads.
 */
public class AgenticArgumentValidator {

    private static final Logger LOGGER = LoggerFactory.getLogger(AgenticArgumentValidator.class);

    /**
     * Singleton registry — instantiated once and reused. {@code FieldValidatorRegistry.create()}
     * wires ~25 stateless validator objects; recreating it per-call would be wasteful.
     */
    private static final FieldValidatorRegistry VALIDATOR_REGISTRY = FieldValidatorRegistry.create();

    /**
     * Field types excluded from validation in Phase 1 due to type-bridge incompatibilities.
     * Uses normalized (lowercase, short) type names.
     */
    private static final Set<String> EXCLUDED_TYPES = Set.of("phone", "multifield", "list");

    // ==================== PUBLIC API ====================

    /**
     * Validates already-converted tool arguments against the field constraints defined on each
     * {@link ParameterDefinition} in the matching tool definition.
     *
     * <p>The method returns as soon as it has iterated all parameters — it does not short-circuit
     * on the first error, so the LLM receives a complete list of violations to fix in one retry.
     *
     * @param toolName      the tool name from the LLM command
     * @param convertedArgs the argument map produced by {@link ToolArgumentConverter} (typed values)
     * @param tools         the full list of tool definitions available for this session
     * @return a map of {@code fieldName -> List<String> errors}; empty if all arguments are valid
     */
    public Map<String, Object> validateToolArguments(
            String toolName,
            Map<String, Object> convertedArgs,
            List<AgenticToolDefinition> tools) {

        if (toolName == null || convertedArgs == null || convertedArgs.isEmpty()) {

            return Map.of();
        }

        AgenticToolDefinition toolDef = findToolDefinition(toolName, tools);

        if (toolDef == null) {

            LOGGER.debug("Tool definition not found for '{}', skipping argument validation", toolName);
            return Map.of();
        }

        if (Boolean.TRUE.equals(toolDef.isHelperTool())) {

            LOGGER.debug("Skipping argument validation for helper tool '{}'", toolName);
            return Map.of();
        }

        if (toolDef.parameters() == null || toolDef.parameters().isEmpty()) {

            return Map.of();
        }

        Map<String, Object> allErrors = new HashMap<>();

        for (ParameterDefinition paramDef : toolDef.parameters()) {

            if (paramDef.pDefName() == null || paramDef.pDefType() == null) {

                continue;
            }

            String normalizedType = OutputVariableConverter.normalizeType(paramDef.pDefType());
            if (EXCLUDED_TYPES.contains(normalizedType)) {

                LOGGER.debug("Skipping validation for excluded type '{}' on parameter '{}'",
                        paramDef.pDefType(), paramDef.pDefName());
                continue;
            }

            Object value = convertedArgs.get(paramDef.pDefName());
            if (value == null) {

                // Required-ness is enforced by the LLM prompt, not here
                continue;
            }

            Map<String, Object> fieldErrors = validateSingleField(
                    paramDef.pDefName(),
                    paramDef.pDefType(),
                    paramDef.metadata() != null ? paramDef.metadata() : Map.of(),
                    value
            );

            if (fieldErrors != null) {

                allErrors.putAll(fieldErrors);
            }
        }

        return allErrors;
    }

    /**
     * Validates already-converted output variables against the field constraints defined on each
     * {@link AIContextVariable} in the expected outputs list.
     *
     * <p>Validation failures are informational — callers should log them at WARN level and
     * proceed without blocking the response.
     *
     * @param convertedOutputs the output variable map produced by {@link OutputVariableConverter}
     * @param expectedOutputs  the expected output definitions carrying type and constraint metadata
     * @return a map of {@code fieldName -> List<String> errors}; empty if all outputs are valid
     */
    public Map<String, Object> validateOutputVariables(
            Map<String, Object> convertedOutputs,
            List<ExpectedOutput> expectedOutputs) {

        if (convertedOutputs == null || convertedOutputs.isEmpty()
                || expectedOutputs == null || expectedOutputs.isEmpty()) {

            return Map.of();
        }

        Map<String, Object> allErrors = new HashMap<>();

        for (ExpectedOutput expectedOutput : expectedOutputs) {

            AIContextVariable contextVar = expectedOutput.getAiContextVariable();
            if (contextVar == null || contextVar.getName() == null || contextVar.getType() == null) {

                continue;
            }

            String normalizedType = OutputVariableConverter.normalizeType(contextVar.getType());
            if (EXCLUDED_TYPES.contains(normalizedType)) {

                LOGGER.debug("Skipping validation for excluded type '{}' on output variable '{}'",
                        contextVar.getType(), contextVar.getName());
                continue;
            }

            Object value = convertedOutputs.get(contextVar.getName());
            if (value == null) {

                continue;
            }

            Map<String, Object> fieldErrors = validateSingleField(
                    contextVar.getName(),
                    contextVar.getType(),
                    contextVar.getMetaData() != null ? contextVar.getMetaData() : Map.of(),
                    value
            );

            if (fieldErrors != null) {

                allErrors.putAll(fieldErrors);
            }
        }

        return allErrors;
    }

    /**
     * Formats a validation error map into a concise, human-readable string suitable for
     * inclusion in the LLM retry feedback payload.
     *
     * <p>Example output:
     * {@code "Validation errors: appointmentDate: Past dates are not allowed; amount: Value must be at least 10.0"}
     *
     * @param errors the error map returned by {@link #validateToolArguments} or
     *               {@link #validateOutputVariables}
     * @return a single-line error summary string; empty string if the map is null or empty
     */
    public String formatValidationErrorsForLLM(Map<String, Object> errors) {

        if (errors == null || errors.isEmpty()) {

            return "";
        }

        StringJoiner fieldJoiner = new StringJoiner("; ");

        for (Map.Entry<String, Object> entry : errors.entrySet()) {

            String fieldName = entry.getKey();
            Object errorValue = entry.getValue();

            if (errorValue instanceof List<?> errorList) {

                StringJoiner msgJoiner = new StringJoiner(", ");
                for (Object msg : errorList) {

                    if (msg != null) {

                        msgJoiner.add(msg.toString());
                    }
                }
                fieldJoiner.add(fieldName + ": " + msgJoiner);
            } else {

                fieldJoiner.add(fieldName + ": " + errorValue);
            }
        }

        return "Validation errors: " + fieldJoiner;
    }

    // ==================== INTERNAL HELPERS ====================

    /**
     * Validates a single field value against the constraints held in {@code attributeValues},
     * delegating to the appropriate {@link FieldValidator} from the registry.
     *
     * <p>Each call is wrapped in a try-catch so that a validator throwing an unexpected exception
     * on one field does not prevent validation of the remaining fields (fail-open policy).
     *
     * @param fieldName       the field name (used as the error map key and for NamedField construction)
     * @param fullFieldType   the full type string, e.g., {@code "com.krista.fields.Date"}
     * @param attributeValues the constraint map for the field (metadata / metaData from the DTO)
     * @param value           the already-converted value to validate
     * @return the validator result map, or {@code null} if valid or if validation was skipped
     */
    private Map<String, Object> validateSingleField(
            String fieldName,
            String fullFieldType,
            Map<String, Object> attributeValues,
            Object value) {

        try {

            // CRITICAL: registry is keyed on full type string, e.g., "com.krista.fields.Date".
            // Do NOT use the normalized short form here — it would fall through to DefaultFieldValidator.
            NamedField namedField = new NamedField(
                    fieldName,
                    fullFieldType,
                    attributeValues,
                    Map.of()
            );

            FieldValidator validator = VALIDATOR_REGISTRY.get(fullFieldType);
            return validator.validate(namedField, value);

        } catch (Exception e) {

            LOGGER.error("Unexpected exception validating field '{}' (type={}): {}. Skipping validation for this field.",
                    fieldName, fullFieldType, e.getMessage(), e);
            // Fail-open: do not block the request on an unexpected validator exception
            return null;
        }
    }

    /**
     * Finds a tool definition by exact name match.
     *
     * @param toolName the tool name to search for
     * @param tools    the list of available tool definitions
     * @return the matching {@link AgenticToolDefinition}, or {@code null} if not found
     */
    private AgenticToolDefinition findToolDefinition(String toolName, List<AgenticToolDefinition> tools) {

        if (tools == null) {

            return null;
        }

        for (AgenticToolDefinition tool : tools) {

            if (toolName.equals(tool.name())) {

                return tool;
            }
        }

        return null;
    }
}
