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

import app.krista.model.field.NamedField;
import app.krista.model.field.NamedValuedField;

import java.util.HashMap;
import java.util.Map;

/**
 * Utility class to convert between different NamedField types.
 * Handles conversion from com.krista.model.types.NamedField to app.krista.model.field.NamedField
 */
public class NamedFieldConverter {

    private NamedFieldConverter() {
        // Utility class - prevent instantiation
    }

    /**
     * Converts any NamedField-like object to app.krista.model.field.NamedField
     * @param sourceField The source field object (could be com.krista.model.types.NamedField or app.krista.model.field.NamedField)
     * @return Converted app.krista.model.field.NamedField or null if conversion fails
     */
    public static NamedField convertToAppNamedField(Object sourceField) {
        if (sourceField == null) {
            return null;
        }

        // If it's already the correct type, return as-is
        if (sourceField instanceof NamedField) {
            return (NamedField) sourceField;
        }

        try {
            // Extract properties using reflection for com.krista.model.types.NamedField
            String name = (String) sourceField.getClass().getMethod("getName").invoke(sourceField);

            // getFieldType() returns KristaType<?>, need to convert to String representation
            Object fieldTypeObj = sourceField.getClass().getMethod("getFieldType").invoke(sourceField);
            String fieldType = fieldTypeObj.toString(); // Convert KristaType to String representation

            // Strip "KristaType:" prefix if present
            if (fieldType.startsWith("KristaType:")) {
                fieldType = fieldType.substring("KristaType:".length());
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> attributes = (Map<String, Object>) sourceField.getClass().getMethod("getAttributes").invoke(sourceField);

            // Use default empty options map since options conversion is complex and not essential
            Map<String, NamedValuedField> options = new HashMap<>();

            // Create new app.krista.model.field.NamedField with extracted properties
            return new NamedField(name, fieldType, attributes, options);

        } catch (Exception e) {
            System.out.println("Failed to convert NamedField from " + sourceField.getClass().getName() + " to app.krista.model.field.NamedField: " + e.getMessage());
            return null;
        }
    }

    /**
     * Safely extracts the name from any NamedField-like object
     * @param namedField The field object
     * @return The field name or null if extraction fails
     */
    public static String extractName(Object namedField) {
        if (namedField == null) {
            return null;
        }

        try {
            if (namedField instanceof NamedField) {
                return ((NamedField) namedField).getName();
            } else {
                // Use reflection for other NamedField types
                return (String) namedField.getClass().getMethod("getName").invoke(namedField);
            }
        } catch (Exception e) {
            System.out.println("Failed to extract name from field: " + e.getMessage());
            return null;
        }
    }

    /**
     * Safely extracts the field type from any NamedField-like object
     * @param namedField The field object
     * @return The field type or null if extraction fails
     */
    public static String extractFieldType(Object namedField) {
        if (namedField == null) {
            return null;
        }

        try {
            if (namedField instanceof NamedField) {
                return ((NamedField) namedField).getFieldType();
            } else {
                // Use reflection for other NamedField types - getFieldType() returns KristaType<?>
                Object fieldTypeObj = namedField.getClass().getMethod("getFieldType").invoke(namedField);
                String fieldType = fieldTypeObj.toString(); // Convert KristaType to String representation

                // Strip "KristaType:" prefix if present
                if (fieldType.startsWith("KristaType:")) {
                    fieldType = fieldType.substring("KristaType:".length());
                }

                return fieldType;
            }
        } catch (Exception e) {
            System.out.println("Failed to extract field type from field: " + e.getMessage());
            return null;
        }
    }

}
