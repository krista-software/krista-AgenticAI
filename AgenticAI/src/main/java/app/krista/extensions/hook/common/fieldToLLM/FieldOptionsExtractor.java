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

import app.krista.model.field.Field;
import app.krista.model.field.NamedValuedField;

import java.util.Map;

/**
 * Global utility for extracting field-level options.
 * This logic is applied consistently across all field types.
 */
public class FieldOptionsExtractor {

    private FieldOptionsExtractor() {
        // Utility class - prevent instantiation
    }

    /**
     * Extract isMandatory from field.options.required.value
     * Defaults to false if not found or invalid structure.
     * This is applied globally to all field types.
     * 
     * @param field The field to extract isMandatory from
     * @return true if field is mandatory, false otherwise
     */
    public static boolean extractIsMandatory(Field field) {
        try {
            Map<String, NamedValuedField> options = field.getOptions();
            if (options == null) {
                return false;
            }

            NamedValuedField requiredField = options.get("required");

            Object valueObj = requiredField.getValue();
            
            if (valueObj instanceof Boolean) {
                return (Boolean) valueObj;
            }
            
            return false;
        } catch (Exception e) {
            // If any error occurs during extraction, default to true
            return true;
        }
    }
}
