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

package app.krista.extensions.hook.common.fieldToLLM.valueConverter.impl;

import app.krista.extensions.hook.common.NamedFieldConverter;
import app.krista.extensions.hook.common.fieldToLLM.valueConverter.FieldValueToLLMValueConverter;
import app.krista.extensions.hook.common.fieldToLLM.valueConverter.FieldValueToLLMValueConverterRegistry;
import app.krista.model.field.Field;
import app.krista.model.field.NamedField;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MultiFieldValueToLLMValueConverter implements FieldValueToLLMValueConverter<Map<String, Object>> {

    private final FieldValueToLLMValueConverterRegistry registry;

    public MultiFieldValueToLLMValueConverter(FieldValueToLLMValueConverterRegistry registry) {
        this.registry = registry;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> extract(Field field, Object fieldValue) throws Exception {
        if (fieldValue == null) {
            return new HashMap<>();
        }

        if (!(fieldValue instanceof Map)) {
            throw new Exception("Failed to extract MultiField value: input must be a Map");
        }

        Map<String, Object> inputMap = (Map<String, Object>) fieldValue;
        Map<String, Object> result = new HashMap<>();

        // Get the nested fields from the MultiField's attributes
        Map<String, Object> attributes = field.getAttributeValues();
        Object fieldsObj = attributes.get("fields");

        if (fieldsObj == null) {
            // If no fields attribute, return the map as is
            return inputMap;
        }

        if (!(fieldsObj instanceof List<?> fieldsList)) {
            throw new Exception("MultiField 'fields' attribute must be a List");
        }

        // Process each nested field
        for (Object nestedFieldObj : fieldsList) {
            NamedField namedField = NamedFieldConverter.convertToAppNamedField(nestedFieldObj);
            // Skip if we can't convert to NamedField
            if (namedField == null) {
                System.out.println("Failed to convert field to NamedField: " + nestedFieldObj.getClass().getName());
                continue;
            }

            processNestedField(namedField, inputMap, result);
        }

        return result;
    }

    private void processNestedField(NamedField nestedField, Map<String, Object> inputMap, Map<String, Object> result) throws Exception {
        String fieldName = nestedField.getName();
        Object fieldValue = inputMap.get(fieldName);

        if (fieldValue != null) {
            String fieldType = nestedField.getFieldType();
            FieldValueToLLMValueConverter<?> converter = registry.get(fieldType);

            if (converter != null) {
                Object convertedValue = converter.extract(nestedField, fieldValue);
                result.put(fieldName, convertedValue);
            } else {
                // If no specific converter found, store the value as-is
                result.put(fieldName, fieldValue);
            }
        }
    }
}