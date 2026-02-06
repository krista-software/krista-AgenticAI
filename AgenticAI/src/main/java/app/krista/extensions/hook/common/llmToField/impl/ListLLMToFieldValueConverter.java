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

import app.krista.extensions.hook.common.FieldConverter;
import app.krista.extensions.hook.common.llmToField.LLMToFieldValueConverter;
import app.krista.extensions.hook.common.llmToField.LLMToFieldValueConverterRegistry;
import app.krista.model.field.Field;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ListLLMToFieldValueConverter implements LLMToFieldValueConverter<List<?>> {

    private final LLMToFieldValueConverterRegistry registry;

    public ListLLMToFieldValueConverter(LLMToFieldValueConverterRegistry registry) {
        this.registry = registry;
    }

    @Override
    public List<?> extract(Field field, Object input) {
        if (!(input instanceof List<?> inputList)) {
            System.out.println("Failed to extract List value: input must be a List but got: " +
                (input != null ? input.getClass().getSimpleName() : "null"));
            return null;
        }

        List<Object> result = new ArrayList<>();

        // Get the element field definition from the List's attributes
        Map<String, Object> attributes = field.getAttributeValues();
        Object fieldObj = attributes.get("field");

        if (fieldObj == null) {
            System.out.println("Failed to extract List value: List does not contain 'field' attribute");
            return null;
        }

        // Convert to app.krista.model.field.Field
        Field elementField = FieldConverter.convertToAppField(fieldObj);
        if (elementField == null) {
            System.out.println("Failed to extract List value: List 'field' attribute must be a Field object but got: " +
                fieldObj.getClass().getSimpleName());
            return null;
        }

        String elementFieldType = elementField.getFieldType();

        // Process each item in the list
        for (int i = 0; i < inputList.size(); i++) {
            Object item = inputList.get(i);
            if (item != null) {
                try {
                    LLMToFieldValueConverter<?> converter = registry.get(elementFieldType);
                    Object convertedValue = converter.extract(elementField, item);
                    if (convertedValue != null) {
                        result.add(convertedValue);
                    }
                    // If convertedValue is null, skip this element
                } catch (IllegalStateException e) {
                    System.out.println("No converter found for element field type '" + elementFieldType + "' at index " + i + ". Storing value as-is. Error: " + e.getMessage());
                    result.add(item);
                }
            } else {
                result.add(null);
            }
        }

        return result;
    }

}
