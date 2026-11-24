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
import app.krista.extensions.hook.util.JsonMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Converts LLM output variables to proper Krista types.
 *
 * <p>This class handles the conversion of LLM-generated output values
 * (which are typically strings or JSON objects) to the proper types
 * expected by Krista (epochs for dates, structured objects for entities, etc.)
 *
 * <p>Mirrors the functionality of StudioAI's InputProcessor.convertLlmOutputDatesToEpochs()
 * but adapted for the Agentic step's Map-based output format.
 *
 * <p>Thread-safety: instances hold only an immutable {@link ZoneId} and are safe to share.
 */
public class OutputVariableConverter {

    private static final Logger LOGGER = LoggerFactory.getLogger(OutputVariableConverter.class);

    // Type constants (matching StudioAI)
    private static final String LIST = "list";
    private static final String TABLE = "table";
    private static final String MULTI_FIELD = "multifield";
    private static final Set<String> SIMPLE_DATE_TYPES = Set.of("date", "time");
    private static final Set<String> RANGE_TYPES = Set.of("daterange", "datetimerange");
    private static final Set<String> ENTITY_TYPES = Set.of("entity", "entityreference");

    private final ZoneId zoneId;

    public OutputVariableConverter() {

        this(ZoneId.of("UTC"));
    }

    public OutputVariableConverter(ZoneId zoneId) {

        this.zoneId = zoneId != null ? zoneId : ZoneId.of("UTC");
    }

    // ==================== PUBLIC API ====================

    /**
     * Converts a flat Map of output variables to properly typed values.
     *
     * @param outputVariables The flat map from LLM (e.g., {"appointmentDate": "15-01-2024"})
     * @param expectedOutputs The list of expected outputs with type information
     * @return A map with properly typed values (e.g., {"appointmentDate": 1705276800000})
     */
    public Map<String, Object> convertOutputVariables(
            Map<String, Object> outputVariables,
            List<ExpectedOutput> expectedOutputs) {

        if (outputVariables == null || expectedOutputs == null) {

            return outputVariables;
        }

        Map<String, Object> converted = new HashMap<>();

        for (ExpectedOutput expectedOutput : expectedOutputs) {

            AIContextVariable contextVar = expectedOutput.getAiContextVariable();
            if (contextVar == null || contextVar.getName() == null) {

                continue;
            }

            String varName = contextVar.getName();
            Object originalValue = outputVariables.get(varName);

            if (originalValue == null) {

                // Variable not present in output, skip
                continue;
            }

            try {

                String fieldType = normalizeType(contextVar.getType());

                Object convertedValue = convertValue(originalValue, fieldType, contextVar);
                converted.put(varName, convertedValue);

                LOGGER.debug("Converted {} from {} to {}",
                        varName, originalValue.getClass().getSimpleName(),
                        convertedValue != null ? convertedValue.getClass().getSimpleName() : "null");

            } catch (Exception e) {

                LOGGER.warn("Failed to convert value for variable '{}': {}", varName, e.getMessage());
                // Keep original value if conversion fails
                converted.put(varName, originalValue);
            }
        }

        return converted;
    }

    // ==================== TYPE NORMALIZATION ====================

    /**
     * Normalizes full logical names (e.g. "com.krista.fields.Date") to short lowercase form ("date").
     * Also handles already-short names and null gracefully.
     */
    static String normalizeType(String type) {

        if (type == null) {

            return "";
        }

        String t = type.startsWith("com.krista.fields.")
                ? type.substring("com.krista.fields.".length()) : type;
        return t.toLowerCase();
    }

    // ==================== VALUE CONVERSION ====================

    /**
     * Converts a single value based on its normalized field type.
     */
    private Object convertValue(Object value, String fieldType, AIContextVariable contextVar) {

        if (value == null) {

            return null;
        }

        return switch (fieldType) {
            case String t when SIMPLE_DATE_TYPES.contains(t) -> convertDate(value);
            case String t when RANGE_TYPES.contains(t) -> convertDateRange(value);
            case String t when ENTITY_TYPES.contains(t) -> convertEntity(value, contextVar);
            case LIST -> convertList(value, contextVar);
            case MULTI_FIELD -> convertMultiField(value, contextVar);
            case TABLE -> convertTable(value, contextVar);
            case "number", "double", "long", "int", "currency", "percentage", "unit", "calculation" -> convertNumber(value);
            case "boolean", "switch" -> convertBoolean(value);
            case "phone" -> convertPhone(value, contextVar);
            case "rating" -> convertRating(value);
            case "checkboxes", "pickmany" -> convertCheckboxes(value);
            default -> value; // Text, PickOne, etc. - pass through
        };
    }

    /**
     * Converts a date string to epoch milliseconds.
     * Supports epoch numbers, epoch strings, human-readable dates, and time-only strings.
     */
    private Object convertDate(Object value) {

        if (value instanceof Number) {

            return ((Number) value).longValue();
        }

        if (value instanceof String dateStr) {

            // Check if it's already an epoch string (all digits)
            if (DateUtils.isEpochTimestamp(dateStr)) {

                try {

                    return Long.parseLong(dateStr);
                } catch (NumberFormatException e) {

                    // Not a valid epoch, continue with parsing
                }
            }

            // Parse human-readable date
            Long epoch = DateUtils.parseToEpochMillis(dateStr, zoneId);
            if (epoch != null) {

                return epoch;
            }

            // Time-only: "14:30:00" or "14:30" -> epoch with today's date (G22)
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
     * Converts a date range to epoch milliseconds.
     */
    private Object convertDateRange(Object value) {

        if (!(value instanceof Map)) {

            LOGGER.warn("DateRange value is not a Map: {}", value);
            return value;
        }

        Map<String, Object> rangeMap = asMap(value);
        return DateUtils.parseRangeToEpochs(rangeMap, zoneId);
    }

    /**
     * Converts an entity structure. Preserves the original structure:
     * if the input has a "fields" wrapper, the output will too.
     * If the input is a flat map (no "fields" wrapper), returns processed map directly (G15).
     */
    private Object convertEntity(Object value, AIContextVariable contextVar) {

        if (!(value instanceof Map)) {

            return value;
        }

        Map<String, Object> entityMap = asMap(value);

        // If it has a 'fields' key, process the nested multi-field and preserve wrapper
        if (entityMap.get("fields") instanceof Map) {

            Map<String, Object> fieldsMap = asMap(entityMap.get("fields"));
            @SuppressWarnings("unchecked")
            Map<String, Object> processedFields = (Map<String, Object>) convertMultiField(fieldsMap, contextVar);
            return Map.of("fields", processedFields);
        }

        // No "fields" wrapper - return processed map directly, do NOT wrap (G15 fix)
        return convertMultiField(entityMap, contextVar);
    }

    /**
     * Converts a list of values.
     */
    private Object convertList(Object value, AIContextVariable contextVar) {

        if (!(value instanceof List)) {

            LOGGER.warn("List value is not a List: {}", value);
            return value;
        }

        List<Object> list = asList(value);

        // Get the item type from subContextVariables
        AIContextVariable itemVar = contextVar.getSubContextVariables() != null
                && !contextVar.getSubContextVariables().isEmpty()
                ? contextVar.getSubContextVariables().get(0)
                : null;

        if (itemVar == null) {

            return list; // No type info, return as-is
        }

        String itemType = normalizeType(Optional.ofNullable(itemVar.getType()).orElse("text"));

        List<Object> converted = new ArrayList<>();
        for (Object item : list) {

            converted.add(convertValue(item, itemType, itemVar));
        }

        return converted;
    }

    /**
     * Converts a multi-field object.
     */
    private Object convertMultiField(Object value, AIContextVariable contextVar) {

        if (!(value instanceof Map)) {

            LOGGER.warn("MultiField value is not a Map: {}", value);
            return value;
        }

        Map<String, Object> map = new HashMap<>(asMap(value));

        if (contextVar.getSubContextVariables() == null) {

            return map; // No field definitions, return as-is
        }

        // Process each sub-field
        for (AIContextVariable subVar : contextVar.getSubContextVariables()) {

            String subName = subVar.getName();
            if (map.containsKey(subName)) {

                String subType = normalizeType(Optional.ofNullable(subVar.getType()).orElse("text"));
                Object subValue = map.get(subName);
                map.put(subName, convertValue(subValue, subType, subVar));
            }
        }

        return map;
    }

    /**
     * Converts a table (list of row objects).
     * Handles both a raw List of rows and a {@code {rows: [...]}} wrapper (G16).
     */
    private Object convertTable(Object value, AIContextVariable contextVar) {

        List<Object> rows = extractRows(value);
        if (rows.isEmpty() && !(value instanceof List) && !(value instanceof Map)) {

            LOGGER.warn("Table value is not a List or {rows:[...]}: {}", value);
            return value;
        }

        // Treat each row as a multi-field
        List<Object> converted = new ArrayList<>();
        for (Object row : rows) {

            if (row instanceof Map) {

                converted.add(convertMultiField(row, contextVar));
            } else {

                converted.add(row);
            }
        }

        return converted;
    }

    // ==================== NEW TYPE CONVERTERS ====================

    /**
     * Converts a phone value to the structured {@code {countryCode, nationalNumber}} format.
     * Extracts country code from metaData if available.
     */
    private Object convertPhone(Object value, AIContextVariable contextVar) {

        // Already structured
        if (value instanceof Map) {

            return value;
        }

        int countryCode = 0;
        if (contextVar.getMetaData() != null && contextVar.getMetaData().get("countryCode") != null) {

            try {

                countryCode = ((Number) contextVar.getMetaData().get("countryCode")).intValue();
            } catch (ClassCastException e) {

                LOGGER.debug("Could not extract countryCode from metaData: {}", e.getMessage());
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

                    LOGGER.warn("Could not parse phone number from string: '{}'", str);
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

                    LOGGER.warn("Could not parse rating from string: '{}'", trimmed);
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

            // Try JSON array
            if (trimmed.startsWith("[")) {

                try {

                    return JsonMapper.getInstance().readValue(trimmed, new TypeReference<List<String>>() {
                    });
                } catch (Exception e) {

                    LOGGER.debug("Failed to parse checkboxes JSON array: {}", e.getMessage());
                }
            }

            // Single value -> wrap in list
            return List.of(trimmed);
        }

        return List.of(String.valueOf(value));
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

                LOGGER.warn("Could not parse number from string: '{}'", str);
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

    // ==================== SAFE CAST HELPERS (G24) ====================

    /**
     * Extracts row data from a table value, handling both List and {rows: [...]} formats (G16).
     */
    @SuppressWarnings("unchecked")
    private static List<Object> extractRows(Object value) {

        if (value instanceof List<?> list) {

            return new ArrayList<>((List<Object>) list);
        }

        if (value instanceof Map<?, ?> map && map.get("rows") instanceof List<?> rows) {

            return new ArrayList<>((List<Object>) rows);
        }

        return Collections.emptyList();
    }

    /**
     * Safe cast to {@code Map<String, Object>}. Returns empty map if the value is not a Map.
     */
    @SuppressWarnings("unchecked")
    static Map<String, Object> asMap(Object value) {

        if (value instanceof Map) {

            return (Map<String, Object>) value;
        }
        return Collections.emptyMap();
    }

    /**
     * Safe cast to {@code List<Object>}. Returns empty list if the value is not a List.
     */
    @SuppressWarnings("unchecked")
    static List<Object> asList(Object value) {

        if (value instanceof List) {

            return (List<Object>) value;
        }
        return Collections.emptyList();
    }
}
