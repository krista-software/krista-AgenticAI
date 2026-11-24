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

import app.krista.extensions.hook.common.llmToField.LLMToFieldValueConverter;
import app.krista.model.field.Field;


import java.util.List;

public class CheckboxLLMToFieldValueConverter implements LLMToFieldValueConverter<List<String>> {



    @Override
    @SuppressWarnings("unchecked")
    public List<String> extract(Field field, Object input) {
        if (input instanceof List<?>) {
            // Check if all elements are strings or can be converted to strings
            try {
                return (List<String>) input;
            } catch (ClassCastException e) {
                System.out.println("Failed to extract Checkboxes value. List contains non-String elements: " + e.getMessage());
                return null;
            }
        }
        else {
            System.out.println("Failed to extract Checkboxes value. Expected List but got: " +
                (input != null ? input.getClass().getSimpleName() : "null"));
            return null;
        }
    }
}
