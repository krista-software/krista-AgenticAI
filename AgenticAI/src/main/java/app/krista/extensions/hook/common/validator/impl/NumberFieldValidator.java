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

public class NumberFieldValidator extends DefaultFieldValidator {


    @Override
    public Map<String, Object> validate(NamedField field, Object value) {
        Map<String, Object> attributes = field.getAttributeValues();
        List<String> errors = new ArrayList<>();

        if (value instanceof Number) {
            try {
                if (attributes.containsKey("minValue")) {
                    Object minValueObj = attributes.get("minValue");
                    if (minValueObj instanceof Number) {
                        double min = ((Number) minValueObj).doubleValue();
                        if (((Number) value).doubleValue() < min) {
                            errors.add("Value must be at least " + min);
                        }
                    }
                }
                if (attributes.containsKey("maxValue")) {
                    Object maxValueObj = attributes.get("maxValue");
                    if (maxValueObj instanceof Number) {
                        double max = ((Number) maxValueObj).doubleValue();
                        if (((Number) value).doubleValue() > max) {
                            errors.add("Value must not exceed " + max);
                        }
                    }
                }
            } catch (Exception e) {
                System.out.println("Error validating number field '" + field.getName() + "': {}");
                errors.add("Validation error occurred");
            }
        }
        else if (value != null) {
            System.out.println("Invalid Number Format for field '" + field.getName() + "'. Expected Number but got: {}");
            errors.add("Invalid number format");
        }

        return errors.isEmpty() ? null : Map.of(field.getName(), errors);
    }
}