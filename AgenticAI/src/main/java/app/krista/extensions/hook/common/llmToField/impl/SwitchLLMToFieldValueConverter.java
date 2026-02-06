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

public class SwitchLLMToFieldValueConverter implements LLMToFieldValueConverter<Boolean> {


    @Override
    public Boolean extract(Field field, Object input) {
        if(input instanceof Boolean) {
            return (Boolean) input;
        }
        else {
            System.out.println("Failed to extract Switch value. Expected Boolean but got: " +
                (input != null ? input.getClass().getSimpleName() : "null"));
            return null;
        }
    }
}
