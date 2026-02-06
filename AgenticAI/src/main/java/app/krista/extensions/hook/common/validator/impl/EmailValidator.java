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

public class EmailValidator extends DefaultFieldValidator {


    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> validate(NamedField field, Object value) {
        Map<String, Object> attributes = field.getAttributeValues();
        List<String> errors = new ArrayList<>();

        if (value instanceof String) {
            try {
                if (attributes.containsKey("allowedDomains")) {
                    String email = (String) value;
                    if (email.contains("@")) {
                        String domain = email.substring(email.indexOf("@") + 1);
                        Object allowedDomainsObj = attributes.get("allowedDomains");
                        if (allowedDomainsObj instanceof List<?>) {
                            List<String> allowedDomains = (List<String>) allowedDomainsObj;
                            if (!allowedDomains.contains(domain)) {
                                errors.add("Invalid email domain: " + domain);
                            }
                        } else {
                            System.out.println("allowedDomains attribute is not a valid list for field '" + field.getName() + "'");
                            errors.add("Email validation configuration error");
                        }
                    } else {
                        errors.add("Invalid email format: missing @ symbol");
                    }
                }
                // If no allowedDomains specified, basic email validation passes
            } catch (Exception e) {
                System.out.println("Error validating email field '" + field.getName() + "': {}");
                errors.add("Email validation error occurred");
            }
        }
        else if (value != null) {
            System.out.println("Invalid Email Format for field '" + field.getName() + "'. Expected String but got: {}");
            errors.add("Invalid email format");
        }

        return errors.isEmpty() ? null : Map.of(field.getName(), errors);
    }
}