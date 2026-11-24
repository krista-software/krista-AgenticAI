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

package app.krista.extensions.hook.common.fieldToLLM;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Global utility for filtering field attributes to only include whitelisted attributes.
 * This filtering logic is applied consistently across all field types with context-aware filtering.
 */
public class FieldAttributeFilter {

    /**
     * Enum representing different prompt contexts for attribute filtering.
     */
    public enum PromptContext {
        DEFAULT,
        ENTITY_EXTRACTION
    }

    // Global whitelist of allowed attributes for all field types (default context)
    private static final Set<String> ALLOWED_ATTRIBUTES = Set.of(
            "minLength",
            "maxLength",
            "minValue",
            "maxValue",
            "formatPattern",
            "options",
            "allowedDomains",
            "minSelection",
            "maxSelection",
            "field",
            "fields"
    );

    // Restricted whitelist for entity extraction context - only optional and format attributes
    private static final Set<String> ENTITY_EXTRACTION_ATTRIBUTES = Set.of(
            "options",
            "formatPattern",
            "field",
            "fields"
    );

    private FieldAttributeFilter() {
        // Utility class - prevent instantiation
    }

    /**
     * Filter field attributes to only include whitelisted attributes.
     * This is applied globally to all field types using default context.
     *
     * @param attributes The original field attributes
     * @return Filtered map containing only whitelisted attributes
     */
    public static Map<String, Object> filterAttributes(Map<String, Object> attributes) {
        return filterAttributes(attributes, PromptContext.DEFAULT); // Default context
    }

    /**
     * Filter field attributes to only include whitelisted attributes based on prompt context.
     * This allows context-aware filtering for different prompt types.
     *
     * @param attributes The original field attributes
     * @param context The prompt context determining which attributes to include
     * @return Filtered map containing only context-appropriate whitelisted attributes
     */
    public static Map<String, Object> filterAttributes(Map<String, Object> attributes, PromptContext context) {
        if (attributes == null) {
            return new HashMap<>();
        }

        Set<String> allowedAttributes = getAllowedAttributesForContext(context);
        Map<String, Object> filtered = new HashMap<>();

        for (Map.Entry<String, Object> entry : attributes.entrySet()) {
            if (allowedAttributes.contains(entry.getKey())) {
                filtered.put(entry.getKey(), entry.getValue());
            }
        }

        return filtered;
    }

    /**
     * Get the allowed attributes set for a specific prompt context.
     *
     * @param context The prompt context
     * @return Set of allowed attribute names for the context
     */
    private static Set<String> getAllowedAttributesForContext(PromptContext context) {
        return switch (context) {
            case ENTITY_EXTRACTION -> ENTITY_EXTRACTION_ATTRIBUTES;
            case DEFAULT -> ALLOWED_ATTRIBUTES;
        };
    }

}
