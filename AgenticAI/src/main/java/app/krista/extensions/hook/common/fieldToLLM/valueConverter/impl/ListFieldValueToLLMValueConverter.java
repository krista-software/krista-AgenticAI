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

import app.krista.extensions.hook.common.FieldConverter;
import app.krista.extensions.hook.common.fieldToLLM.valueConverter.FieldValueToLLMValueConverter;
import app.krista.extensions.hook.common.fieldToLLM.valueConverter.FieldValueToLLMValueConverterRegistry;
import app.krista.model.field.Field;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ListFieldValueToLLMValueConverter implements FieldValueToLLMValueConverter<List<?>> {

    private final FieldValueToLLMValueConverterRegistry registry;

    public ListFieldValueToLLMValueConverter(FieldValueToLLMValueConverterRegistry registry) {
        this.registry = registry;
    }

    @Override
    public List<?> extract(Field field, Object fieldValue) throws Exception {
        if (fieldValue == null) {
            return new ArrayList<>();
        }

        if (fieldValue instanceof List<?> inputList) {
            List<Object> result = new ArrayList<>();

            // Get the element field definition from the List's attributes
            Map<String, Object> attributes = field.getAttributeValues();
            Object fieldObj = attributes.get("field");

            if (fieldObj == null) {
                // If no field attribute, return the list as is
                return inputList;
            }

            // Convert to app.krista.model.field.Field
            Field elementField = FieldConverter.convertToAppField(fieldObj);
            if (elementField == null) {
                throw new Exception("List 'field' attribute must be a Field object but got: " + fieldObj.getClass().getSimpleName());
            }

            String elementFieldType = elementField.getFieldType();

            // Process each item in the list
            for (Object item : inputList) {
                if (item != null) {
                    FieldValueToLLMValueConverter<?> converter = registry.get(elementFieldType);
                    Object convertedValue = converter.extract(elementField, item);
                    result.add(convertedValue);
                } else {
                    result.add(null);
                }
            }

            return result;
        } else {
            throw new Exception("Failed to extract List value from type: " + fieldValue.getClass().getName());
        }
    }
}