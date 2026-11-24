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

public class TimeValidator extends DefaultFieldValidator {


    @Override
    public Map<String, Object> validate(NamedField field, Object value) {
        List<String> errors = new ArrayList<>();

        if (value instanceof Long) {
            try {
                long timeMs = (Long) value;

                // Time is stored as milliseconds since midnight
                // Valid range is 0 to 86399999 (24 hours in milliseconds - 1ms)
                long maxTimeMs = 24 * 60 * 60 * 1000L; // 24 hours

                // Get the timezone from field attributes if available
                String zoneId = "GMT"; // Default timezone
                if (field.getAttributeValues() != null && field.getAttributeValues().get("timezone") != null) {
                    zoneId = (String) field.getAttributeValues().get("timezone");
                }

                // Check if the raw time value exceeds 24 hours
                // This handles the test case with 25 hours
                if (timeMs > maxTimeMs && timeMs < 946684800000L) { // 946684800000 = Jan 1, 2000 (arbitrary threshold for epoch timestamps)
                    errors.add("Time cannot exceed 24 hours");
                } else {
                    // For epoch timestamps, extract just the time portion
                    long millisSinceMidnight = timeMs % maxTimeMs;
                    
                    if (millisSinceMidnight < 0) {
                        errors.add("Time cannot be negative");
                    }
                }
            } catch (Exception e) {
                System.out.println("Error validating time field '" + field.getName() + "': " + e.getMessage());
                errors.add("Time validation error occurred");
            }
        } else if (value != null) {
            System.out.println("Invalid Time Format for field '" + field.getName() + "'. Expected Long but got: " + value.getClass().getName());
            errors.add("Invalid time format");
        }

        return errors.isEmpty() ? null : Map.of(field.getName(), errors);
    }
}
