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

import app.krista.model.field.NamedField;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class CheckboxesValidator extends DefaultFieldValidator {


    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> validate(NamedField field, Object value) {
        Map<String, Object> attributes = field.getAttributeValues();
        List<String> errors = new ArrayList<>();

        if (value instanceof List<?> valueList) {
            try {
                if (!valueList.isEmpty() && valueList.getFirst() instanceof String) {
                    List<String> values = (List<String>) valueList;

                    Object optionsObj = attributes.get("options");
                    if (!(optionsObj instanceof List<?> optionsRaw)) {
                        System.out.println("Options attribute is not a valid list for field '{}'" + field.getName());
                        errors.add("Checkboxes validation configuration error");
                        return Map.of(field.getName(), errors);
                    }

                    List<String> options = new ArrayList<>();
                    for (Object option : optionsRaw) {
                        if (option instanceof String) {
                            options.add((String) option);
                        }
                    }

                    for (String val : values) {
                        if (!options.contains(val)) {
                            errors.add("Invalid value: " + val + ". Must be one of: " + options);
                        }
                    }

                    if (attributes.containsKey("minSelection")) {
                        Object minSelObj = attributes.get("minSelection");
                        if (minSelObj instanceof Number) {
                            int minSel = ((Number) minSelObj).intValue();
                            if (valueList.size() < minSel) {
                                errors.add("At least " + minSel + " selections required");
                            }
                        }
                    }

                    if (attributes.containsKey("maxSelection")) {
                        Object maxSelObj = attributes.get("maxSelection");
                        if (maxSelObj instanceof Number) {
                            int maxSel = ((Number) maxSelObj).intValue();
                            if (valueList.size() > maxSel) {
                                errors.add("At most " + maxSel + " selections allowed");
                            }
                        }
                    }
                } else if (!valueList.isEmpty()) {
                    System.out.println("Invalid Checkboxes Format for field '" + field.getName() + "': List contains non-String elements");
                    errors.add("Invalid checkboxes format: list must contain strings");
                }
                // Empty list is valid for checkboxes
            } catch (Exception e) {
                System.out.println("Error validating Checkboxes field '" + field.getName() + "': {}");
                errors.add("Checkboxes validation error occurred");
            }
        } else if (value != null) {
            System.out.println("Invalid Checkboxes Format for field '" + field.getName() + "'. Expected List but got: {}");
            errors.add("Invalid checkboxes format");
        }

        return errors.isEmpty() ? null : Map.of(field.getName(), errors);
    }
}