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

package app.krista.extensions.hook.common.validator.impl;

import app.krista.extensions.hook.common.FieldConverter;
import app.krista.extensions.hook.common.validator.FieldValidator;
import app.krista.extensions.hook.common.validator.FieldValidatorRegistry;
import app.krista.model.field.Field;
import app.krista.model.field.NamedField;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ListValidator extends DefaultFieldValidator {

    private final FieldValidatorRegistry fieldValidatorRegistry;

    public ListValidator(FieldValidatorRegistry fieldValidatorRegistry) {
        this.fieldValidatorRegistry = fieldValidatorRegistry;
    }

    @Override
    public Map<String, Object> validate(NamedField field, Object value) {
        Map<String, Object> attributes = field.getAttributeValues();
        List<String> errors = new ArrayList<>();

        if (value instanceof List<?> valueList) {
            try {
                // Get the element field definition from the List's attributes
                Object fieldObj = attributes.get("field");
                if (fieldObj == null) {
                    System.out.println("List field '{}' does not contain 'field' attribute" + field.getName());
                    errors.add("List validation configuration error");
                    return Map.of(field.getName(), errors);
                }

                // Convert to app.krista.model.field.Field
                Field elementField = FieldConverter.convertToAppField(fieldObj);
                if (elementField == null) {
                    System.out.println("List field '" + field.getName() + "' 'field' attribute must be a Field object but got: " + fieldObj.getClass().getSimpleName());
                    errors.add("List validation configuration error");
                    return Map.of(field.getName(), errors);
                }

                String elementFieldType = elementField.getFieldType();
                FieldValidator elementValidator = fieldValidatorRegistry.get(elementFieldType);

                // Validate each element in the list
                for (int i = 0; i < valueList.size(); i++) {
                    Object element = valueList.get(i);
                    if (element != null) {
                        try {
                            // Create a temporary NamedField for the element validation
                            NamedField elementNamedField = new NamedField("listElement", elementFieldType,
                                elementField.getAttributeValues(), elementField.getOptions());

                            Map<String, Object> elementValidationResult = elementValidator.validate(elementNamedField, element);

                            // Check if there are validation errors for this element
                            if (elementValidationResult != null && elementValidationResult.containsKey("listElement")) {
                                Object elementErrors = elementValidationResult.get("listElement");

                                // Handle simple field validation errors (List<String>)
                                if (elementErrors instanceof List<?> elementErrorList && !elementErrorList.isEmpty()) {
                                    for (Object error : elementErrorList) {
                                        errors.add("Element at index " + i + ": " + error.toString());
                                    }
                                }
                                // Handle nested MultiField validation errors (Map<String, Object>)
                                else if (elementErrors instanceof Map<?, ?> nestedErrorMap && !nestedErrorMap.isEmpty()) {
                                    addNestedErrorsWithIndex(errors, nestedErrorMap, i, "");
                                }
                            }
                        } catch (Exception e) {
                            System.out.println("Error validating list element at index " + i + " in field '" + field.getName() + "': {}");
                            errors.add("Element at index " + i + ": validation error occurred");
                        }
                    }
                }

            } catch (Exception e) {
                System.out.println("Error validating List field '" + field.getName() + "': {}");
                errors.add("List validation error occurred");
            }
        } else if (value != null) {
            System.out.println("Invalid List Format for field '" + field.getName() + "'. Expected List but got: {}");
            errors.add("Invalid list format");
        }

        return errors.isEmpty() ? null : Map.of(field.getName(), errors);
    }

    /**
     * Recursively adds nested validation errors with index information for list elements
     */
    private void addNestedErrorsWithIndex(List<String> errors, Map<?, ?> nestedErrorMap, int index, String path) {
        for (Map.Entry<?, ?> entry : nestedErrorMap.entrySet()) {
            String fieldName = entry.getKey().toString();
            Object fieldErrors = entry.getValue();
            String currentPath = path.isEmpty() ? fieldName : path + "." + fieldName;

            if (fieldErrors instanceof List<?> errorList && !errorList.isEmpty()) {
                // Leaf level errors - add them with full path
                for (Object error : errorList) {
                    errors.add("Element at index " + index + " (" + currentPath + "): " + error.toString());
                }
            } else if (fieldErrors instanceof Map<?, ?> nestedMap && !nestedMap.isEmpty()) {
                // Nested structure - recurse deeper
                addNestedErrorsWithIndex(errors, nestedMap, index, currentPath);
            }
        }
    }
}
