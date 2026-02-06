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

public class NumberLLMToFieldValueConverter implements LLMToFieldValueConverter<Double> {


    @Override
    public Double extract(Field field, Object input) {
        if (input instanceof Number || input instanceof String) {
            try {
                if (input instanceof Number) {
                    return ((Number) input).doubleValue();
                } else {
                    String numberStr = (String) input;
                    if (numberStr.trim().isEmpty()) {
                        System.out.println("Failed to extract number value. Empty string provided");
                        return null;
                    }
                    return Double.parseDouble(numberStr.trim());
                }
            } catch (NumberFormatException e) {
                System.out.println("Failed to parse number from string: " + input + ". Error: " + e.getMessage());
                return null;
            } catch (Exception e) {
                System.out.println("Failed to extract number value. Error: " + e.getMessage());
                return null;
            }
        }
        System.out.println("Failed to extract number value. Expected Number or String but got: " +
            (input != null ? input.getClass().getSimpleName() : "null"));
        return null;
    }
}
