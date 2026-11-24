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

import app.krista.extensions.hook.agentic.AgenticConstants;
import app.krista.extensions.hook.agentic.util.DateUtils;
import app.krista.extensions.hook.util.JsonMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import java.util.stream.Collectors;

/**
 * Utility class to format InputVariable and ExpectedOutput lists for LLM prompts.
 * Creates human-readable string representations suitable for LLM context.
 */
public class VariableFormatter {

    private static final Logger LOGGER = LoggerFactory.getLogger(VariableFormatter.class);

    // System-reserved variable names that are auto-populated and should not be shown to LLM
    private static final List<String> RESERVED_SYSTEM_VARIABLES = List.of(
        AgenticConstants.SYSTEM_VAR_CONVERSATION_HISTORY,
        AgenticConstants.SYSTEM_VAR_TOOL_SUMMARY,
        AgenticConstants.SYSTEM_VAR_SESSION_SUMMARY
    );

    /**
     * Checks if a variable name is a system-reserved variable.
     * These variables are auto-populated by the system and should not be shown to the LLM.
     */
    public static boolean isSystemReservedVariable(String variableName) {
        return variableName != null && RESERVED_SYSTEM_VARIABLES.contains(variableName);
    }

    /**
     * Formats a list of InputVariable into a readable string for LLM prompts.
     * Format: "variableName: value (type: description)"
     * 
     * Example output:
     *   customerName: John Doe (type: String, required: true)
     *   bookingId: BK12345 (type: String, required: true)
     */
    public static String formatInputVariables(List<InputVariable> inputVariables) {
        if (inputVariables == null || inputVariables.isEmpty()) {
            return "No input variables available";
        }

        return inputVariables.stream()
                .map(VariableFormatter::formatInputVariable)
                .collect(Collectors.joining("\n"));
    }

    /**
     * Formats a single InputVariable into a readable string.
     */
    private static String formatInputVariable(InputVariable iv) {
        if (iv.getAiContextVariable() == null) {
            return "Unknown variable: " + iv.getValue();
        }

        String name = iv.getAiContextVariable().getName();
        String type = iv.getAiContextVariable().getType();
        String description = iv.getAiContextVariable().getDescription();
        boolean required = iv.getAiContextVariable().isRequired();
        Object value = iv.getValue();

        StringBuilder sb = new StringBuilder();
        sb.append(name).append(": ");
        
        // Format the value with type-aware formatting, then display-format the result
        if (value == null) {

            sb.append("null");
        } else {

            Object formatted = formatValueForLLM(iv.getAiContextVariable(), value);
            sb.append(formatDisplayValue(formatted));
        }

        // Add metadata in parentheses
        sb.append(" (");
        boolean hasMetadata = false;
        
        if (type != null && !type.isEmpty()) {
            sb.append("type: ").append(type);
            hasMetadata = true;
        }
        
        if (description != null && !description.isEmpty()) {
            if (hasMetadata) sb.append(", ");
            sb.append("description: ").append(description);
            hasMetadata = true;
        }
        
        if (hasMetadata) sb.append(", ");
        sb.append("required: ").append(required);
        sb.append(")");

        // Add options for PickOne / PickMany fields
        String options = formatOptionsFromMetaData(iv.getAiContextVariable());
        if (options != null && !options.isEmpty()) {

            sb.append(" ").append(options);
        }

        return sb.toString();
    }

    /**
     * Formats a list of ExpectedOutput into a readable string for LLM prompts.
     * Format: "variableName (type): instruction"
     * 
     * Example output:
     *   confirmationNumber (String): Generate a unique confirmation number
     *   status (String): Return the booking status
     * 
     * Note: System-reserved variables (conversation_history, tool_summary) are filtered out
     * as they are auto-populated by the system and should not be shown to the LLM.
     */
    public static String formatExpectedOutputs(List<ExpectedOutput> expectedOutputs) {
        if (expectedOutputs == null || expectedOutputs.isEmpty()) {
            return "No expected outputs defined";
        }

        // Filter out system-reserved variables and format the rest
        List<ExpectedOutput> filteredOutputs = expectedOutputs.stream()
                .filter(eo -> eo.getAiContextVariable() != null 
                    && !isSystemReservedVariable(eo.getAiContextVariable().getName()))
                .collect(Collectors.toList());
        
        // Log if any system variables were filtered
        int filteredCount = expectedOutputs.size() - filteredOutputs.size();
        if (filteredCount > 0) {
            LOGGER.debug("Filtered {} system-reserved variable(s) from expected outputs (not shown to LLM)", filteredCount);
        }

        if (filteredOutputs.isEmpty()) {
            return "No expected outputs defined (all were system-reserved variables)";
        }

        return filteredOutputs.stream()
                .map(VariableFormatter::formatExpectedOutput)
                .collect(Collectors.joining("\n"));
    }

    /**
     * Formats a single ExpectedOutput into a readable string with type guidance.
     * Includes type information and format specifications for the LLM.
     */
    private static String formatExpectedOutput(ExpectedOutput eo) {
        if (eo.getAiContextVariable() == null) {
            return "Unknown output: " + (eo.getInstruction() != null ? eo.getInstruction() : "");
        }

        String name = eo.getAiContextVariable().getName();
        String type = eo.getAiContextVariable().getType();
        String instruction = eo.getInstruction();
        String operationType = eo.getOperationType();
        String subVariables = formatSubVariables(eo.getAiContextVariable().getSubContextVariables());

        StringBuilder sb = new StringBuilder();
        sb.append(name);

        // Add type if available
        if (type != null && !type.isEmpty()) {
            sb.append(" (type: ").append(type).append(")");
        }
        
        sb.append(": ");
        
        // Add instruction or default text
        if (instruction != null && !instruction.isEmpty()) {
            sb.append(instruction);
        } else {
            sb.append("Generate value for ").append(name);
        }
        
        // Add operation type if available
        if (operationType != null && !operationType.isEmpty()) {
            sb.append(" [operation: ").append(operationType).append("]");
        }
        
        // Add type-specific format guidance
        String typeGuidance = getTypeFormatGuidance(type, eo.getAiContextVariable().getMetaData());
        if (typeGuidance != null) {

            sb.append(" ").append(typeGuidance);
        }

        // Add sub-variable info for complex types
        if (subVariables != null && !subVariables.isEmpty()) {

            sb.append(" ").append(subVariables);
        }

        // Add options for PickOne / PickMany fields
        String options = formatOptionsFromMetaData(eo.getAiContextVariable());
        if (options != null && !options.isEmpty()) {

            sb.append(" ").append(options);
        }

        return sb.toString();
    }
    
    /**
     * Returns constraint-aware format guidance for specific types.
     * Reads constraint metadata (min/max values, date restrictions, selection limits, etc.)
     * from the {@code metaData} map and builds rich guidance strings for the LLM.
     * <p>
     * Handles both short names (e.g. "Date") and full logical names (e.g. "com.krista.fields.Date").
     *
     * @param type     the field type string
     * @param metaData constraint metadata from AIContextVariable (may be null)
     * @return a formatted guidance string, or null if no guidance applicable
     */
    static String getTypeFormatGuidance(String type, Map<String, Object> metaData) {

        if (type == null || type.isEmpty()) {

            return null;
        }

        String normalized = normalizeType(type);
        Map<String, Object> meta = metaData != null ? metaData : Collections.emptyMap();
        StringBuilder guidance = new StringBuilder();

        switch (normalized) {

            case "date", "time" -> {

                guidance.append("[Format: DD-MM-YYYY HH:MM:SS");
                appendDateConstraints(guidance, meta);
                guidance.append("]");
            }

            case "daterange", "datetimerange" -> {

                guidance.append("[Format: {from: 'DD-MM-YYYY HH:MM:SS', to: 'DD-MM-YYYY HH:MM:SS'}");
                appendDateConstraints(guidance, meta);
                if (meta.get("maxDurationInDays") != null) {

                    guidance.append(", max duration: ").append(meta.get("maxDurationInDays")).append(" days");
                }
                guidance.append("]");
            }

            case "number", "double", "long", "int", "calculation", "unit" -> {

                guidance.append("[Format: numeric value");
                appendNumericConstraints(guidance, meta);
                guidance.append("]");
            }

            case "currency" -> {

                guidance.append("[Format: numeric value, no currency symbols");
                appendNumericConstraints(guidance, meta);
                guidance.append("]");
            }

            case "percentage" -> {

                guidance.append("[Format: decimal value (0.75 for 75%), no percent sign");
                appendNumericConstraints(guidance, meta);
                guidance.append("]");
            }

            case "boolean", "switch" -> {

                guidance.append("[Format: true/false]");
            }

            case "entity", "entityreference" -> {

                guidance.append("[Format: {key: value} — entity data directly, NOT wrapped in \"fields\"]");
            }

            case "list" -> {

                guidance.append("[Format: JSON array [...]]");
            }

            case "table" -> {

                guidance.append("[Format: JSON array of row objects]");
            }

            case "multifield" -> {

                guidance.append("[Format: JSON object with field values]");
            }

            case "phone" -> {

                guidance.append("[Format: {countryCode: number, nationalNumber: number}");
                if (meta.get("formatPattern") != null) {

                    guidance.append(", pattern: ").append(meta.get("formatPattern"));
                }
                guidance.append("]");
            }

            case "rating" -> {

                Object minR = meta.getOrDefault("minRating", 1);
                Object maxR = meta.getOrDefault("maxRating", 5);
                guidance.append("[Format: integer ").append(minR).append("-").append(maxR).append("]");
            }

            case "checkboxes", "pickmany" -> {

                guidance.append("[Format: JSON array of selected values");
                if (meta.get("minSelection") != null || meta.get("maxSelection") != null) {

                    guidance.append(", select ");
                    if (meta.get("minSelection") != null) {

                        guidance.append("at least ").append(meta.get("minSelection"));
                    }
                    if (meta.get("minSelection") != null && meta.get("maxSelection") != null) {

                        guidance.append(" and ");
                    }
                    if (meta.get("maxSelection") != null) {

                        guidance.append("at most ").append(meta.get("maxSelection"));
                    }
                }
                guidance.append("]");
            }

            case "pickone" -> {

                guidance.append("[Format: single value from the options list]");
            }

            case "address" -> {

                guidance.append("[Format: {street, street2, city, state, country, postalCode}]");
            }

            case "location" -> {

                guidance.append("[Format: {latitude: \"string\", longitude: \"string\", address: \"string\", city: \"string\", state: \"string\", country: \"string\", zip: \"string\", area: \"string\"}]");
            }

            default -> {

                // Text and other types -- include length constraints if present
                if (meta.get("minLength") != null || meta.get("maxLength") != null) {

                    guidance.append("[Length: ");
                    if (meta.get("minLength") != null) {

                        guidance.append("min ").append(meta.get("minLength"));
                    }
                    if (meta.get("minLength") != null && meta.get("maxLength") != null) {

                        guidance.append(", ");
                    }
                    if (meta.get("maxLength") != null) {

                        guidance.append("max ").append(meta.get("maxLength"));
                    }
                    guidance.append("]");
                }
            }
        }

        return guidance.isEmpty() ? null : guidance.toString();
    }

    /**
     * Appends date constraint text (allowPast, allowFuture, allowToday) to the guidance.
     */
    private static void appendDateConstraints(StringBuilder guidance, Map<String, Object> meta) {

        boolean allowPast = Boolean.TRUE.equals(meta.get("allowPast"));
        boolean allowToday = Boolean.TRUE.equals(meta.get("allowToday"));
        boolean allowFuture = Boolean.TRUE.equals(meta.get("allowFuture"));

        // Only add constraint text if at least one restriction is explicitly set
        if (meta.containsKey("allowPast") || meta.containsKey("allowFuture")) {

            List<String> allowed = new ArrayList<>();
            if (allowPast) allowed.add("past");
            if (allowToday) allowed.add("today");
            if (allowFuture) allowed.add("future");

            if (!allowed.isEmpty() && allowed.size() < 3) {

                guidance.append(", allowed: ").append(String.join("/", allowed)).append(" dates only");
            }
        }
    }

    /**
     * Appends numeric constraint text (range, formatPattern) to the guidance.
     */
    private static void appendNumericConstraints(StringBuilder guidance, Map<String, Object> meta) {

        if (meta.get("minValue") != null || meta.get("maxValue") != null) {

            guidance.append(", range: ");
            guidance.append(meta.getOrDefault("minValue", "any"));
            guidance.append(" to ");
            guidance.append(meta.getOrDefault("maxValue", "any"));
        }
        if (meta.get("formatPattern") != null) {

            guidance.append(", pattern: ").append(meta.get("formatPattern"));
        }
    }

    /**
     * Extracts and formats options from the AIContextVariable metaData.
     * Returns a string like "[Options: Option A, Option B, Option C]" or null if no options.
     */
    private static String formatOptionsFromMetaData(AIContextVariable contextVar) {

        if (contextVar == null || contextVar.getMetaData() == null) {

            return null;
        }

        Object optionsObj = contextVar.getMetaData().get("options");
        if (optionsObj == null) {

            return null;
        }

        List<String> optionsList;
        if (optionsObj instanceof List<?> list) {

            optionsList = list.stream()
                    .map(Object::toString)
                    .collect(Collectors.toList());
        } else if (optionsObj instanceof String str) {

            // Backward compat: handle legacy "[a, b, c]" string format
            String trimmed = str.trim();
            if (trimmed.startsWith("[") && trimmed.endsWith("]")) {

                trimmed = trimmed.substring(1, trimmed.length() - 1);
            }
            if (trimmed.isEmpty()) {

                return null;
            }
            optionsList = List.of(trimmed.split("\\s*,\\s*"));
        } else {

            return null;
        }

        if (optionsList.isEmpty()) {

            return null;
        }

        return "[Options: " + String.join(", ", optionsList) + "]";
    }
    
    /**
     * Formats sub-variable information for complex types into a human-readable structure
     * description. Recursively resolves anonymous wrapper nodes (name is null or empty) by
     * descending into their children, so that the LLM sees the actual named fields and types.
     * <p>
     * Examples:
     * <ul>
     *   <li>List&lt;MultiField&gt; with sub-fields "Docset Name" (Text), "Has documents" (Switch)
     *       &rarr; {@code [Structure: {Docset Name: Text, Has documents: Switch}]}</li>
     *   <li>Named node with its own sub-fields &rarr; {@code Name: Type {sub1: Type1, sub2: Type2}}</li>
     * </ul>
     *
     * @param subVars the sub-context variables to format (may be null or empty)
     * @return a formatted structure string like {@code [Structure: {...}]}, or empty string if no sub-vars
     */
    private static String formatSubVariables(java.util.List<AIContextVariable> subVars) {

        if (subVars == null || subVars.isEmpty()) {

            return "";
        }

        StringBuilder sb = new StringBuilder("[Structure: {");
        appendSubFields(sb, subVars);
        sb.append("}]");
        return sb.toString();
    }

    /**
     * Recursively appends field descriptions for a list of sub-variables.
     * Anonymous wrapper nodes (name null or empty) that have children are transparently
     * expanded — their children are rendered in place.
     * Named leaf nodes render as {@code Name: Type}.
     * Named nodes with their own sub-variables render as {@code Name: Type {child1: T1, child2: T2}}.
     *
     * @param sb      the builder to append to
     * @param subVars the sub-context variables at the current level
     */
    private static void appendSubFields(StringBuilder sb, List<AIContextVariable> subVars) {

        boolean first = true;

        for (AIContextVariable var : subVars) {

            boolean isAnonymous = var.getName() == null || var.getName().isEmpty();
            boolean hasChildren = var.getSubContextVariables() != null
                    && !var.getSubContextVariables().isEmpty();

            if (isAnonymous && hasChildren) {

                // Anonymous wrapper node — recurse into its children directly
                if (!first) {

                    sb.append(", ");
                }
                appendSubFields(sb, var.getSubContextVariables());
                first = false;
            } else if (isAnonymous) {

                // Anonymous leaf with no children — skip, nothing useful to render
                continue;
            } else {

                // Named node
                if (!first) {

                    sb.append(", ");
                }
                sb.append(var.getName());
                if (var.getType() != null) {

                    sb.append(": ").append(shortTypeName(var.getType()));
                }
                if (var.isRequired()) {

                    sb.append(" (required)");
                }
                if (hasChildren) {

                    sb.append(" {");
                    appendSubFields(sb, var.getSubContextVariables());
                    sb.append("}");
                }
                first = false;
            }
        }
    }

    /**
     * Returns a human-readable short type name by stripping the {@code com.krista.fields.} prefix
     * and capitalizing the first letter.  For example, {@code "com.krista.fields.Text"} becomes
     * {@code "Text"}, and {@code "text"} becomes {@code "Text"}.
     *
     * @param type the raw type string
     * @return a capitalized short type name
     */
    private static String shortTypeName(String type) {

        if (type == null || type.isEmpty()) {

            return type;
        }

        String stripped = type.startsWith("com.krista.fields.")
                ? type.substring("com.krista.fields.".length())
                : type;

        if (stripped.isEmpty()) {

            return stripped;
        }

        return Character.toUpperCase(stripped.charAt(0)) + stripped.substring(1);
    }

    // ==================== TYPE-AWARE VALUE FORMATTING ====================

    /**
     * Normalizes a type string by stripping the {@code com.krista.fields.} prefix and lowercasing.
     * Handles both short names (e.g. "Date") and full logical names (e.g. "com.krista.fields.Date").
     *
     * @param type the raw type string
     * @return normalized lowercase type name, or empty string if null
     */
    static String normalizeType(String type) {

        if (type == null) {

            return "";
        }

        String t = type.startsWith("com.krista.fields.")
                ? type.substring("com.krista.fields.".length()) : type;
        return t.toLowerCase();
    }

    /**
     * Formats a value for LLM consumption based on its declared type.
     * Converts epoch timestamps to human-readable dates, recursively formats lists/tables/entities,
     * and falls back to the raw value for simple types.
     *
     * @param contextVar the variable metadata (may be null for untyped values)
     * @param value      the raw value to format
     * @return a formatted value suitable for LLM prompt inclusion
     */
    static Object formatValueForLLM(AIContextVariable contextVar, Object value) {

        if (value == null) {

            return null;
        }

        String type = normalizeType(contextVar != null ? contextVar.getType() : null);
        ZoneId zoneId = ZoneId.of("UTC");

        // Type-first detection: Use declared type as primary signal
        try {

            return switch (type) {
                case "date", "time" -> DateUtils.formatFromEpoch(value, zoneId);
                case "daterange", "datetimerange" -> DateUtils.formatRange(asMap(value), zoneId);
                case "list" -> formatList(contextVar, asList(value), zoneId);
                case "table" -> formatTable(contextVar, value, zoneId);
                case "multifield" -> formatMultiField(contextVar, asMap(value), zoneId);
                case "entity", "entityreference" -> formatEntity(asMap(value), contextVar, zoneId);
                default -> formatByStructure(value, contextVar, zoneId);
            };
        } catch (Exception e) {

            LOGGER.warn("Failed to format value for type {}: {}", type, e.getMessage());
            return value;
        }
    }

    /**
     * Fallback formatting by value structure when declared type is unknown or unrecognized.
     * Detects entities by "meta"/"fields" keys and lists by instance type.
     */
    private static Object formatByStructure(Object value, AIContextVariable contextVar, ZoneId zoneId) {

        // Entity detection: Map with both platform metadata keys (KristaGrand entities always have "meta" + "fields")
        if (value instanceof Map<?, ?> mapValue
                && mapValue.containsKey("fields") && mapValue.containsKey("meta")) {

            LOGGER.debug("Structurally detected entity — stripping platform metadata");

            try {

                return formatEntity(asMap(value), contextVar, zoneId);
            } catch (Exception e) {

                LOGGER.warn("Failed to format structurally-detected entity: {}", e.getMessage());
                return value;
            }
        }

        // List detection: Value is a List but type wasn't "list"/"table"
        if (value instanceof List<?>) {

            LOGGER.debug("Structurally detected list — formatting items");

            try {

                return formatList(contextVar, asList(value), zoneId);
            } catch (Exception e) {

                LOGGER.warn("Failed to format structurally-detected list: {}", e.getMessage());
                return value;
            }
        }

        // Unknown structure, return as-is
        return value;
    }

    /**
     * Formats a list by recursively formatting each item according to the sub-type
     * declared in the context variable's sub-context variables.
     */
    private static List<Object> formatList(AIContextVariable contextVar, List<Object> list, ZoneId zoneId) {

        if (list.isEmpty()) {

            return list;
        }

        AIContextVariable itemVar = (contextVar != null
                && contextVar.getSubContextVariables() != null
                && !contextVar.getSubContextVariables().isEmpty())
                ? contextVar.getSubContextVariables().get(0)
                : null;

        // Default to text if no sub-type info available (G19)
        if (itemVar == null) {

            itemVar = new AIContextVariable("", "text");
        }

        List<Object> formatted = new ArrayList<>();
        for (Object item : list) {

            formatted.add(formatValueForLLM(itemVar, item));
        }
        return formatted;
    }

    /**
     * Formats a table value. Handles both a raw List of rows and a {@code {rows: [...]}} wrapper.
     * Each row is formatted as a multi-field.
     */
    private static List<Object> formatTable(AIContextVariable contextVar, Object value, ZoneId zoneId) {

        List<Object> rows = extractRows(value);
        if (rows.isEmpty()) {

            return rows;
        }

        // Build a synthetic multi-field context var using the table's sub-context variables (columns)
        AIContextVariable rowVar = new AIContextVariable("", "multifield",
                contextVar != null ? contextVar.getSubContextVariables() : null);

        List<Object> formatted = new ArrayList<>();
        for (Object row : rows) {

            if (row instanceof Map) {

                formatted.add(formatMultiField(rowVar, asMap(row), zoneId));
            } else {

                formatted.add(row);
            }
        }
        return formatted;
    }

    /**
     * Formats a multi-field (map of named fields) by recursively formatting each sub-field
     * according to its declared type in the sub-context variables.
     */
    private static Map<String, Object> formatMultiField(AIContextVariable contextVar,
            Map<String, Object> map, ZoneId zoneId) {

        if (map.isEmpty()) {

            return map;
        }

        List<AIContextVariable> subVars = contextVar != null
                ? contextVar.getSubContextVariables() : null;
        if (subVars == null || subVars.isEmpty()) {

            return map;
        }

        Map<String, Object> formatted = new LinkedHashMap<>(map);
        for (AIContextVariable subVar : subVars) {

            String subName = subVar.getName();
            if (formatted.containsKey(subName)) {

                formatted.put(subName, formatValueForLLM(subVar, formatted.get(subName)));
            }
        }
        return formatted;
    }

    /**
     * Formats an entity value. If the map contains a "fields" key, unwraps and delegates
     * to multi-field formatting.
     */
    private static Object formatEntity(Map<String, Object> map, AIContextVariable contextVar, ZoneId zoneId) {

        if (map.isEmpty()) {

            return map;
        }

        // If the entity has a "fields" wrapper, unwrap and return only the field values
        // (strip "meta", "type", and other platform metadata that bloats the LLM prompt)
        if (map.get("fields") instanceof Map) {

            Map<String, Object> fieldsMap = asMap(map.get("fields"));
            return formatMultiField(contextVar, fieldsMap, zoneId);
        }

        // No "fields" wrapper — filter out platform metadata keys and treat remainder as fields
        Map<String, Object> cleanMap = new LinkedHashMap<>(map);
        cleanMap.remove("meta");
        cleanMap.remove("type");
        return formatMultiField(contextVar, cleanMap, zoneId);
    }

    /**
     * Extracts row data from a table value, handling both List and {rows: [...]} formats.
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
    private static Map<String, Object> asMap(Object value) {

        if (value instanceof Map) {

            return (Map<String, Object>) value;
        }
        return Collections.emptyMap();
    }

    /**
     * Safe cast to {@code List<Object>}. Returns empty list if the value is not a List.
     */
    @SuppressWarnings("unchecked")
    private static List<Object> asList(Object value) {

        if (value instanceof List) {

            return (List<Object>) value;
        }
        return Collections.emptyList();
    }

    // ==================== DISPLAY FORMATTING ====================

    /**
     * Formats a (possibly already type-formatted) value for display in the LLM prompt.
     * Handles string quoting, JSON serialisation for maps/lists, and truncation.
     *
     * @param value the formatted value
     * @return a display-ready string representation
     */
    private static String formatDisplayValue(Object value) {

        if (value == null) {

            return "null";
        }

        if (value instanceof String) {

            return "\"" + value + "\"";
        }

        if (value instanceof Number || value instanceof Boolean) {

            return value.toString();
        }

        // For maps and lists, produce JSON for readability
        if (value instanceof Map || value instanceof List) {

            try {

                return JsonMapper.getInstance().writeValueAsString(value);
            } catch (Exception e) {

                LOGGER.debug("Failed to serialize value to JSON: {}", e.getMessage());
                // Fall through to toString
            }
        }

        return value.toString();
    }

}
