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

import java.util.*;

public class PickOneValidator extends DefaultFieldValidator {


    @Override
    public Map<String, Object> validate(NamedField field, Object value) {
        Map<String, Object> attributes = field.getAttributeValues();
        List<String> errors = new ArrayList<>();

        if (value instanceof String) {
            try {
                Object optionsObj = attributes.get("options");
                if (!(optionsObj instanceof List<?> optionsRaw)) {
                    System.out.println("Options attribute is not a valid list for field '{}'" + field.getName());
                    errors.add("PickOne validation configuration error");
                    return Map.of(field.getName(), errors);
                }

                Set<String> options = new HashSet<>();
                for (Object option : optionsRaw) {
                    if (option instanceof String) {
                        options.add((String) option);
                    }
                }

                if(!options.contains(value)) {
                    errors.add("Invalid value: " + value + ". Must be one of: " + options);
                }
            } catch (Exception e) {
                System.out.println("Error validating PickOne field '" + field.getName() + "': {}");
                errors.add("PickOne validation error occurred");
            }
        } else if (value != null) {
            System.out.println("Invalid PickOne Format for field '" + field.getName() + "'. Expected String but got: {}");
            errors.add("Invalid PickOne format");
        }

        return errors.isEmpty() ? null : Map.of(field.getName(), errors);
    }

}