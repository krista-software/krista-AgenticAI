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

package app.krista.extensions.hook.common.validator;

import app.krista.extensions.hook.common.llmToField.LLMToFieldValueConverter;
import app.krista.extensions.hook.common.llmToField.LLMToFieldValueConverterRegistry;
import app.krista.model.base.FreeForm;
import app.krista.model.field.Field;
import app.krista.model.field.NamedField;


import java.util.HashMap;
import java.util.Map;


public class LLMResponseValidator {



    private LLMResponseValidator(){
    }


    public static Map<String, Object> validate(FreeForm freeForm, Map<String, Object> outputFieldValueMap) {

        FieldValidatorRegistry validatorRegistry = FieldValidatorRegistry.create();
        LLMToFieldValueConverterRegistry converterRegistry = LLMToFieldValueConverterRegistry.create();

        Map<String, Object> validationErrors = new HashMap<>();

        Map<String, FreeForm.TypedValue> typedValueMap = freeForm.getTypedValues();

        for(String fieldName: typedValueMap.keySet()) {
            Field field = typedValueMap.get(fieldName).getField();
            FieldValidator fieldValidator = validatorRegistry.get(field.getFieldType());
            LLMToFieldValueConverter<?> converter = converterRegistry.get(field.getFieldType());
            NamedField namedField = new NamedField(fieldName, field.getFieldType(), field.getAttributeValues(), field.getOptions());
            Object convertedValue = converter.extract(field, outputFieldValueMap.get(fieldName));
            Map<String, Object> currentValidationErrors = fieldValidator.validate(namedField, convertedValue);
            if (currentValidationErrors != null) {
                validationErrors.putAll(currentValidationErrors);
            }
        }

        return validationErrors;
    }

}
