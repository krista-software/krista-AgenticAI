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

package app.krista.extensions.hook.common;

import app.krista.model.field.Field;
import app.krista.model.field.NamedValuedField;

import java.util.Map;

/**
 * Utility class to convert between different Field types.
 * Handles conversion from com.krista.model.types.Field to app.krista.model.field.Field
 */
public class FieldConverter {

    private FieldConverter() {
        // Utility class - prevent instantiation
    }

    /**
     * Converts any Field-like object to app.krista.model.field.Field
     * @param sourceField The source field object (could be com.krista.model.types.Field or app.krista.model.field.Field)
     * @return Converted app.krista.model.field.Field or null if conversion fails
     */
    public static Field convertToAppField(Object sourceField) {
        if (sourceField == null) {
            return null;
        }

        // If it's already the correct type, return as-is
        if (sourceField instanceof Field) {
            return (Field) sourceField;
        }

        try {
            // Extract properties using reflection for com.krista.model.types.Field
            // getFieldType() returns KristaType<?>, need to convert to String representation
            Object fieldTypeObj = sourceField.getClass().getMethod("getFieldType").invoke(sourceField);
            String fieldType = fieldTypeObj.toString(); // Convert KristaType to String representation

            // Strip "KristaType:" prefix if present
            if (fieldType.startsWith("KristaType:")) {
                fieldType = fieldType.substring("KristaType:".length());
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> attributes = (Map<String, Object>) sourceField.getClass().getMethod("getAttributes").invoke(sourceField);

            @SuppressWarnings("unchecked")
            Map<String, NamedValuedField> options = (Map<String, NamedValuedField>) sourceField.getClass().getMethod("getOptions").invoke(sourceField);

            // Create new app.krista.model.field.Field with extracted properties
            return new Field(fieldType, attributes, options);

        } catch (Exception e) {
            System.out.println("Failed to convert Field from " + sourceField.getClass().getName() + " to app.krista.model.field.Field: " + e.getMessage());
            return null;
        }
    }

}
