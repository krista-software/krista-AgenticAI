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

import app.krista.extensions.hook.common.NamedFieldConverter;
import app.krista.extensions.hook.common.validator.FieldValidator;
import app.krista.extensions.hook.common.validator.FieldValidatorRegistry;
import app.krista.model.field.NamedField;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@SuppressWarnings("unchecked")
public class MultiFieldValidator extends DefaultFieldValidator {

    private final FieldValidatorRegistry fieldValidatorRegistry;

    public MultiFieldValidator(FieldValidatorRegistry fieldValidatorRegistry) {
        this.fieldValidatorRegistry = fieldValidatorRegistry;
    }

    @Override
    public Map<String, Object> validate(NamedField field, Object value) {
        Map<String, Object> attributes = field.getAttributeValues();
        Map<String, Object> nestedErrors = new HashMap<>();

        try {
            // Get the nested fields from the MultiField's attributes
            Object fieldsObj = attributes.get("fields");
            if (fieldsObj == null) {
                System.out.println("MultiField '" + field.getName() + "' does not contain 'fields' attribute");
                return null;
            }

            // The value should be a Map containing values for each nested field
            if (!(value instanceof Map<?, ?>)) {
                if (value != null) {
                    System.out.println("MultiField '" + field.getName() + "' value must be a Map but got: {}");
                }
                return null;
            }
            Map<String, Object> valueMap = (Map<String, Object>) value;

            // Handle List<NamedField> format for fields
            if (fieldsObj instanceof List<?> fieldsList) {
                for (Object fieldObj : fieldsList) {
                    validateNestedField(fieldObj, valueMap, nestedErrors);
                }
            } else {
                System.out.println("MultiField '" + field.getName() + "' 'fields' attribute must be a List of NamedField objects but got: {}");
            }
        } catch (Exception e) {
            System.out.println("Error validating MultiField '" + field.getName() + "': {}");
        }

        return nestedErrors.isEmpty() ? null : Map.of(field.getName(), nestedErrors);
    }

    private void validateNestedField(Object fieldObj, Map<String, Object> valueMap, Map<String, Object> nestedErrors) {
        // Convert to app.krista.model.field.NamedField
        NamedField namedField = NamedFieldConverter.convertToAppNamedField(fieldObj);

        if (namedField != null) {
            String fieldName = namedField.getName();
            Object fieldValue = valueMap.get(fieldName);

            if (fieldValue != null) {
                try {
                    FieldValidator validator = fieldValidatorRegistry.get(namedField.getFieldType());

                    // Validate the nested field
                    Map<String, Object> nestedValidationResult = validator.validate(namedField, fieldValue);

                    // Preserve the nested structure instead of flattening
                    if (nestedValidationResult != null && nestedValidationResult.containsKey(fieldName)) {
                        Object nestedFieldErrors = nestedValidationResult.get(fieldName);
                        if (nestedFieldErrors != null) {
                            // If it's a list of errors, add it directly
                            if (nestedFieldErrors instanceof List<?> && !((List<?>) nestedFieldErrors).isEmpty()) {
                                nestedErrors.put(fieldName, nestedFieldErrors);
                            }
                            // If it's a nested map (from another MultiField), add it directly
                            else if (nestedFieldErrors instanceof Map<?, ?> && !((Map<?, ?>) nestedFieldErrors).isEmpty()) {
                                nestedErrors.put(fieldName, nestedFieldErrors);
                            }
                        }
                    }
                } catch (Exception e) {
                    System.out.println("Error validating nested field '" + fieldName + "' in MultiField: " + e.getMessage());
                    // Continue with other fields - don't let one field validation failure stop others
                }
            }
        } else {
            System.out.println("Failed to convert field to NamedField for validation: " + fieldObj.getClass().getName());
        }
    }
}