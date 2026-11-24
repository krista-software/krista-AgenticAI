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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Represents a tool definition that can be used by the agentic AI.
 * Tools are actions the AI can take, such as calling an invoker, asking a person, or searching entities.
 * This is an immutable record - once created, it cannot be modified.
 */
public record AgenticToolDefinition(
    String name,
    String description,
    String type,
    List<ParameterDefinition> parameters,
    Boolean isHelperTool  // true = helper tool (communication), false/null = business tool
) {

    /**
     * Parameter definition for tool parameters.
     * <p>
     * The optional {@code metadata} map carries additional constraints that the LLM
     * should respect when filling in the parameter value. The platform sends up to
     * 12 keys via {@code LLM_PARAM_ATTRIBUTES}:
     * <ul>
     *   <li>{@code options} — allowed values for PickOne / PickMany parameters</li>
     *   <li>{@code minValue}, {@code maxValue} — numeric range</li>
     *   <li>{@code minLength}, {@code maxLength} — text length range</li>
     *   <li>{@code formatPattern} — number/phone format pattern (e.g. {@code ##0.00})</li>
     *   <li>{@code dateFormat} — date format hint (e.g. {@code DD-MM-YYYY})</li>
     *   <li>{@code minSelection}, {@code maxSelection} — PickMany selection range</li>
     *   <li>{@code allowPast}, {@code allowToday}, {@code allowFuture} — date restrictions
     *       (three flags evaluated together to determine which dates are permitted)</li>
     *   <li>{@code maxDurationInDays} — maximum span for a date-range selection</li>
     * </ul>
     */
    public record ParameterDefinition(
            String pDefName,
            String pDefType,
            String description,
            boolean required,
            Map<String, Object> metadata) {

        /**
         * Backward-compatible constructor for callers that do not supply metadata.
         */
        public ParameterDefinition(String pDefName, String pDefType, String description, boolean required) {

            this(pDefName, pDefType, description, required, null);
        }

        /**
         * Metadata keys suppressed from the LLM prompt to avoid confusion.
         * These keys remain in {@code metadata} for server-side use (conversion, validation)
         * but are not rendered into {@code toString()}.
         */
        private static final Set<String> PROMPT_HIDDEN_KEYS = Set.of(
                "dateFormat"   // UI display format (e.g. MM/DD/YY) conflicts with wire format guidance
        );

        /**
         * Custom toString that surfaces metadata constraints so the LLM can see them
         * in the system prompt alongside the basic parameter definition.
         */
        @Override
        public String toString() {

            StringBuilder sb = new StringBuilder();
            sb.append("ParameterDefinition[pDefName=").append(pDefName);
            sb.append(", pDefType=").append(pDefType);
            sb.append(", description=").append(description);
            sb.append(", required=").append(required);

            if (metadata != null && !metadata.isEmpty()) {

                if (metadata.containsKey("options")) {

                    sb.append(", options=").append(metadata.get("options"));
                }
                if (metadata.containsKey("minValue") || metadata.containsKey("maxValue")) {

                    sb.append(", range=").append(metadata.getOrDefault("minValue", "any"));
                    sb.append("-").append(metadata.getOrDefault("maxValue", "any"));
                }
                if (metadata.containsKey("minLength") || metadata.containsKey("maxLength")) {

                    sb.append(", length=").append(metadata.getOrDefault("minLength", "any"));
                    sb.append("-").append(metadata.getOrDefault("maxLength", "any"));
                }
                if (metadata.containsKey("formatPattern")) {

                    sb.append(", pattern=").append(metadata.get("formatPattern"));
                }
                if (metadata.containsKey("dateFormat") && !PROMPT_HIDDEN_KEYS.contains("dateFormat")) {

                    sb.append(", dateFormat=").append(metadata.get("dateFormat"));
                }
                if (metadata.containsKey("minSelection") || metadata.containsKey("maxSelection")) {

                    sb.append(", select=").append(metadata.getOrDefault("minSelection", "any"));
                    sb.append("-").append(metadata.getOrDefault("maxSelection", "any"));
                }
                appendDateConstraints(sb, metadata);
                if (metadata.containsKey("maxDurationInDays")) {

                    sb.append(", maxDuration=").append(metadata.get("maxDurationInDays")).append(" days");
                }
            }

            // Type-aware format guidance (reuses the comprehensive switch in VariableFormatter)
            String typeGuidance = VariableFormatter.getTypeFormatGuidance(
                    pDefType, Map.of());
            if (typeGuidance != null) {

                sb.append(", format=").append(typeGuidance);
            }

            // Sub-field structure from metadata (populated by platform-side builder for complex types)
            Object subFieldsObj = metadata != null ? metadata.get("subFields") : null;
            if (subFieldsObj instanceof List<?> subFieldsList && !subFieldsList.isEmpty()) {

                sb.append(", structure=").append(formatSubFieldsFromMetadata(subFieldsList));
            }

            sb.append("]");
            return sb.toString();
        }

        /**
         * Appends a compact date-constraint fragment built from the three flags
         * {@code allowPast}, {@code allowToday}, and {@code allowFuture}.
         * <p>
         * When at least one date-direction flag is present and the combination does
         * not allow all three periods, a fragment like {@code , dates: today/future only}
         * is appended.
         */
        private static void appendDateConstraints(StringBuilder sb, Map<String, Object> meta) {

            if (!meta.containsKey("allowPast") && !meta.containsKey("allowFuture")) {

                return;
            }

            boolean allowPast = Boolean.TRUE.equals(meta.get("allowPast"));
            boolean allowToday = Boolean.TRUE.equals(meta.get("allowToday"));
            boolean allowFuture = Boolean.TRUE.equals(meta.get("allowFuture"));

            List<String> allowed = new ArrayList<>();
            if (allowPast) allowed.add("past");
            if (allowToday) allowed.add("today");
            if (allowFuture) allowed.add("future");

            if (!allowed.isEmpty() && allowed.size() < 3) {

                sb.append(", dates: ").append(String.join("/", allowed)).append(" only");
            }
        }

        /**
         * Formats sub-field metadata (transported as a {@code List<Map<String, Object>>}
         * inside the {@code "subFields"} metadata key) into a compact structure description
         * like {@code {fieldName: Type, ...}}.
         *
         * @param subFields the list of sub-field maps, each containing {@code "name"} and {@code "type"} keys
         * @return a formatted structure string like {@code {name: Type, name2: Type2}}
         */
        private static String formatSubFieldsFromMetadata(List<?> subFields) {

            StringBuilder sb = new StringBuilder("{");
            boolean first = true;

            for (Object entry : subFields) {

                if (!(entry instanceof Map<?, ?> map)) {

                    continue;
                }

                Object name = map.get("name");
                Object type = map.get("type");

                if (name == null) {

                    continue;
                }

                if (!first) {

                    sb.append(", ");
                }

                sb.append(name);
                if (type != null) {

                    sb.append(": ").append(type);
                }

                Object required = map.get("required");
                if (Boolean.TRUE.equals(required)) {

                    sb.append(" (required)");
                }

                first = false;
            }

            sb.append("}");
            return sb.toString();
        }
    }
}
