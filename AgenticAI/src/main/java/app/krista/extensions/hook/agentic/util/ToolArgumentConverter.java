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
import app.krista.extensions.hook.util.JsonMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Converts LLM-generated tool arguments to the proper Krista types expected by the platform.
 *
 * <p>When the LLM returns an {@code EXECUTE_TOOL} response, argument values are typically
 * strings (e.g., dates as {@code "01-05-2026 17:00:00"}) whereas the platform expects
 * typed values (e.g., epoch milliseconds for dates). This converter bridges that gap
 * by inspecting the tool's parameter definitions ({@code pDefType}) and converting
 * each argument value accordingly.
 *
 * <p>The conversion logic mirrors {@link OutputVariableConverter} but operates on
 * tool arguments (flat key-value maps with type info from {@link ParameterDefinition})
 * rather than output variables (which use {@code ExpectedOutput} / {@code AIContextVariable}).
 *
 * <p>Thread-safety: instances hold only an immutable {@link ZoneId} and are safe to share.
 */
public class ToolArgumentConverter {

    private static final Logger LOGGER = LoggerFactory.getLogger(ToolArgumentConverter.class);

    private static final Set<String> SIMPLE_DATE_TYPES = Set.of("date", "time");
    private static final Set<String> RANGE_TYPES = Set.of("daterange", "datetimerange");
    private static final Set<String> NUMERIC_TYPES = Set.of(
            "number", "double", "long", "int", "currency", "percentage", "unit", "calculation"
    );
    private static final Set<String> BOOLEAN_TYPES = Set.of("boolean", "switch");
    private static final Set<String> CHECKBOX_TYPES = Set.of("checkboxes", "pickmany");

    private final ZoneId zoneId;

    public ToolArgumentConverter() {

        this(ZoneId.of("UTC"));
    }

    public ToolArgumentConverter(ZoneId zoneId) {

        this.zoneId = zoneId != null ? zoneId : ZoneId.of("UTC");
    }

    // ==================== PUBLIC API ====================

    /**
     * Converts tool arguments from LLM-generated string values to properly typed values.
     *
     * <p>Looks up the tool definition by {@code toolName}, then converts each argument
     * based on its parameter's {@code pDefType}. If the tool is not found, or a specific
     * argument's conversion fails, the original values are preserved (graceful degradation).
     *
     * @param toolName   the tool name from the LLM command (may be a JSON string for entity tools)
     * @param arguments  the raw argument map from the LLM
     * @param tools      the list of available tool definitions for the session
     * @return a new map with properly typed argument values
     */
    public Map<String, Object> convertArguments(
            String toolName,
            Map<String, Object> arguments,
            List<AgenticToolDefinition> tools) {

        if (arguments == null || arguments.isEmpty()) {

            return arguments;
        }

        if (tools == null || tools.isEmpty()) {

            LOGGER.debug("No tool definitions available, skipping argument conversion");
            return arguments;
        }

        AgenticToolDefinition toolDef = findToolDefinition(toolName, tools);
        if (toolDef == null) {

            LOGGER.debug("Tool definition not found for '{}', skipping argument conversion", toolName);
            return arguments;
        }

        if (toolDef.parameters() == null || toolDef.parameters().isEmpty()) {

            LOGGER.debug("Tool '{}' has no parameter definitions, skipping argument conversion", toolName);
            return arguments;
        }

        // Build a lookup map: paramName -> paramDef
        Map<String, ParameterDefinition> paramLookup = new HashMap<>();
        for (ParameterDefinition param : toolDef.parameters()) {

            if (param.pDefName() != null) {

                paramLookup.put(param.pDefName(), param);
            }
        }

        Map<String, Object> converted = new HashMap<>(arguments);

        for (Map.Entry<String, Object> entry : arguments.entrySet()) {

            String argName = entry.getKey();
            Object argValue = entry.getValue();

            ParameterDefinition paramDef = paramLookup.get(argName);
            if (paramDef == null || paramDef.pDefType() == null) {

                // No type info for this argument, keep original
                continue;
            }

            try {

                String normalizedType = normalizeType(paramDef.pDefType());
                Object convertedValue = convertValue(argValue, normalizedType, paramDef);
                converted.put(argName, convertedValue);

                if (convertedValue != argValue) {

                    LOGGER.debug("Converted tool argument '{}' (type={}) from [{}] to [{}]",
                            argName, paramDef.pDefType(),
                            argValue, convertedValue);
                }

            } catch (Exception e) {

                LOGGER.warn("Failed to convert tool argument '{}' (type={}): {}. Keeping original value.",
                        argName, paramDef.pDefType(), e.getMessage());
                // Keep original value on failure (already in converted map)
            }
        }

        return converted;
    }

    // ==================== TOOL LOOKUP ====================

    /**
     * Finds a tool definition by name. Matches against the tool definition's {@code name} field.
     */
    private AgenticToolDefinition findToolDefinition(String toolName, List<AgenticToolDefinition> tools) {

        if (toolName == null) {

            return null;
        }

        for (AgenticToolDefinition tool : tools) {

            if (toolName.equals(tool.name())) {

                return tool;
            }
        }

        return null;
    }

    // ==================== TYPE NORMALIZATION ====================

    /**
     * Normalizes full logical names (e.g. "com.krista.fields.Date") to short lowercase form ("date").
     * Reuses the same logic as {@link OutputVariableConverter#normalizeType(String)}.
     */
    static String normalizeType(String type) {

        return OutputVariableConverter.normalizeType(type);
    }

    // ==================== VALUE CONVERSION ====================

    /**
     * Converts a single value based on its normalized field type.
     */
    private Object convertValue(Object value, String fieldType, ParameterDefinition paramDef) {

        if (value == null) {

            return null;
        }

        if (SIMPLE_DATE_TYPES.contains(fieldType)) {

            return convertDate(value);
        }

        if (RANGE_TYPES.contains(fieldType)) {

            return convertDateRange(value);
        }

        if (NUMERIC_TYPES.contains(fieldType)) {

            return convertNumber(value);
        }

        if (BOOLEAN_TYPES.contains(fieldType)) {

            return convertBoolean(value);
        }

        if ("phone".equals(fieldType)) {

            return convertPhone(value, paramDef);
        }

        if ("rating".equals(fieldType)) {

            return convertRating(value);
        }

        if (CHECKBOX_TYPES.contains(fieldType)) {

            return convertCheckboxes(value);
        }

        // Text, PickOne, etc. - pass through
        return value;
    }

    // ==================== DATE CONVERTERS ====================

    /**
     * Converts a date string to epoch milliseconds.
     * Supports epoch numbers, epoch strings, human-readable dates, and time-only strings.
     */
    private Object convertDate(Object value) {

        if (value instanceof Number) {

            return ((Number) value).longValue();
        }

        if (value instanceof String dateStr) {

            if (DateUtils.isEpochTimestamp(dateStr)) {

                try {

                    return Long.parseLong(dateStr);
                } catch (NumberFormatException e) {

                    // Not a valid epoch, continue with parsing
                }
            }

            Long epoch = DateUtils.parseToEpochMillis(dateStr, zoneId);
            if (epoch != null) {

                return epoch;
            }

            // Time-only: "14:30:00" or "14:30" -> epoch with today's date
            try {

                LocalTime time = LocalTime.parse(dateStr);
                return time.atDate(LocalDate.now(zoneId)).atZone(zoneId).toInstant().toEpochMilli();
            } catch (DateTimeParseException ignored) {

                // Not a valid time-only string
            }

            return value;
        }

        return value;
    }

    /**
     * Converts a date range value to epoch milliseconds.
     * Expects a Map with "from" and "to" keys containing date strings.
     */
    @SuppressWarnings("unchecked")
    private Object convertDateRange(Object value) {

        if (!(value instanceof Map)) {

            LOGGER.warn("DateRange tool argument is not a Map: {}", value);
            return value;
        }

        Map<String, Object> rangeMap = (Map<String, Object>) value;
        return DateUtils.parseRangeToEpochs(rangeMap, zoneId);
    }

    // ==================== NUMERIC & BOOLEAN CONVERTERS ====================

    /**
     * Converts a string/number to a proper Number type.
     */
    private Object convertNumber(Object value) {

        if (value instanceof Number) {

            return value;
        }

        if (value instanceof String str) {

            try {

                if (str.contains(".")) {

                    return Double.parseDouble(str);
                } else {

                    return Long.parseLong(str);
                }
            } catch (NumberFormatException e) {

                LOGGER.warn("Could not parse number from tool argument string: '{}'", str);
                return value;
            }
        }

        return value;
    }

    /**
     * Converts a string to Boolean.
     */
    private Object convertBoolean(Object value) {

        if (value instanceof Boolean) {

            return value;
        }

        if (value instanceof String str) {

            String lower = str.trim().toLowerCase();
            if (lower.equals("true") || lower.equals("yes") || lower.equals("1")) {

                return Boolean.TRUE;
            } else if (lower.equals("false") || lower.equals("no") || lower.equals("0")) {

                return Boolean.FALSE;
            }
        }

        return value;
    }

    // ==================== PHONE, RATING, CHECKBOXES ====================

    /**
     * Converts a phone value to the structured {@code {countryCode, nationalNumber}} format.
     * Extracts country code from the parameter's metadata if available.
     */
    private Object convertPhone(Object value, ParameterDefinition paramDef) {

        if (value instanceof Map) {

            return value;
        }

        int countryCode = 0;
        if (paramDef.metadata() != null && paramDef.metadata().get("countryCode") != null) {

            try {

                countryCode = ((Number) paramDef.metadata().get("countryCode")).intValue();
            } catch (ClassCastException e) {

                LOGGER.debug("Could not extract countryCode from parameter metadata: {}", e.getMessage());
            }
        }

        long nationalNumber = 0;
        if (value instanceof Number) {

            nationalNumber = ((Number) value).longValue();
        } else if (value instanceof String str) {

            String digitsOnly = str.replaceAll("\\D", "");
            if (!digitsOnly.isEmpty()) {

                try {

                    nationalNumber = Long.parseLong(digitsOnly);
                } catch (NumberFormatException e) {

                    LOGGER.warn("Could not parse phone number from tool argument: '{}'", str);
                    return value;
                }
            }
        }

        return Map.of("countryCode", countryCode, "nationalNumber", nationalNumber);
    }

    /**
     * Converts a rating value to an integer (1-5).
     */
    private Object convertRating(Object value) {

        if (value instanceof Number) {

            return ((Number) value).intValue();
        }

        if (value instanceof String str) {

            String trimmed = str.trim();
            if (!trimmed.isEmpty()) {

                try {

                    return Integer.parseInt(trimmed);
                } catch (NumberFormatException e) {

                    LOGGER.warn("Could not parse rating from tool argument: '{}'", trimmed);
                }
            }
        }

        return value;
    }

    /**
     * Converts a checkboxes/pick-many value to a List of strings.
     */
    private Object convertCheckboxes(Object value) {

        if (value instanceof List) {

            return value;
        }

        if (value instanceof String str) {

            String trimmed = str.trim();

            if (trimmed.startsWith("[")) {

                try {

                    return JsonMapper.getInstance().readValue(trimmed, new TypeReference<List<String>>() {

                    });
                } catch (Exception e) {

                    LOGGER.debug("Failed to parse checkboxes JSON array from tool argument: {}", e.getMessage());
                }
            }

            return List.of(trimmed);
        }

        return List.of(String.valueOf(value));
    }
}
