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

import app.krista.extensions.hook.common.fieldToLLM.valueConverter.FieldValueToLLMValueConverter;
import app.krista.model.field.Field;

import java.util.ArrayList;
import java.util.List;

public class CheckboxFieldValueToLLMValueConverter implements FieldValueToLLMValueConverter<List<String>> {

    @Override
    public List<String> extract(Field field, Object fieldValue) throws Exception {

        if (fieldValue instanceof List<?> list) {
            List<String> result = new ArrayList<>();

            for (Object item : list) {
                if (item != null) {
                    result.add(item.toString());
                }
            }

            return result;
        } else {
            throw new Exception("Failed to extract Checkboxes value from type: " + fieldValue.getClass().getName());
        }
    }
}