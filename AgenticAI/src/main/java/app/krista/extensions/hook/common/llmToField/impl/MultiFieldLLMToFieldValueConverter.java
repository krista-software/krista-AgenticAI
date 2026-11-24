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

package app.krista.extensions.hook.common.llmToField.impl;

import app.krista.extensions.hook.common.NamedFieldConverter;
import app.krista.extensions.hook.common.llmToField.LLMToFieldValueConverter;
import app.krista.extensions.hook.common.llmToField.LLMToFieldValueConverterRegistry;
import app.krista.model.field.Field;
import app.krista.model.field.NamedField;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MultiFieldLLMToFieldValueConverter implements LLMToFieldValueConverter<Map<String, Object>> {

    private final LLMToFieldValueConverterRegistry registry;

    public MultiFieldLLMToFieldValueConverter(LLMToFieldValueConverterRegistry registry) {
        this.registry = registry;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> extract(Field field, Object input) {
        if (!(input instanceof Map<?, ?>)) {
            System.out.println("Failed to extract MultiField value: input must be a Map but got: " +
                (input != null ? input.getClass().getSimpleName() : "null"));
            return null;
        }

        Map<String, Object> inputMap = (Map<String, Object>) input;
        Map<String, Object> result = new HashMap<>();

        // Get the nested fields from the MultiField's attributes
        Map<String, Object> attributes = field.getAttributeValues();
        Object fieldsObj = attributes.get("fields");

        if (fieldsObj == null) {
            System.out.println("Failed to extract MultiField value: MultiField does not contain 'fields' attribute");
            return null;
        }

        // Handle List<NamedField> format for fields
        if (fieldsObj instanceof List<?> fieldsList) {
            for (Object fieldObj : fieldsList) {
                processNestedField(fieldObj, inputMap, result);
            }
        } else {
            System.out.println("Failed to extract MultiField value: 'fields' attribute must be a List of NamedField objects but got: " +
                fieldsObj.getClass().getSimpleName());
            return null;
        }

        return result;
    }

    private void processNestedField(Object fieldObj, Map<String, Object> inputMap, Map<String, Object> result) {
        // Convert to app.krista.model.field.NamedField
        NamedField namedField = NamedFieldConverter.convertToAppNamedField(fieldObj);

        if (namedField != null) {
            String fieldName = namedField.getName();
            Object fieldValue = inputMap.get(fieldName);

            if (fieldValue != null) {
                try {
                    LLMToFieldValueConverter<?> converter = registry.get(namedField.getFieldType());
                    Object convertedValue = converter.extract(namedField, fieldValue);
                    if (convertedValue != null) {
                        result.put(fieldName, convertedValue);
                    }
                } catch (IllegalStateException e) {
                    System.out.println("No converter found for field type '" + namedField.getFieldType() + "' in nested field '" + fieldName + "'. Storing value as-is. Error: " + e.getMessage());
                    result.put(fieldName, fieldValue);
                }
            }
        } else {
            System.out.println("Failed to convert field to NamedField: " + fieldObj.getClass().getName());
        }
    }
}
