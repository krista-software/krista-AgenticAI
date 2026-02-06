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

public class RatingValidator extends DefaultFieldValidator {


    @Override
    public Map<String, Object> validate(NamedField field, Object value) {
        Map<String, Object> attributes = field.getAttributeValues();
        List<String> errors = new ArrayList<>();

        if (value instanceof Number) {
            try {
                int rating = ((Number) value).intValue();

                // Default rating scale is 1-5
                int minRating = 1;
                int maxRating = 5;

                // Check for custom rating scale
                if (attributes.containsKey("minRating")) {
                    Object minRatingObj = attributes.get("minRating");
                    if (minRatingObj instanceof Number) {
                        minRating = ((Number) minRatingObj).intValue();
                    }
                }
                if (attributes.containsKey("maxRating")) {
                    Object maxRatingObj = attributes.get("maxRating");
                    if (maxRatingObj instanceof Number) {
                        maxRating = ((Number) maxRatingObj).intValue();
                    }
                }

                if (rating < minRating) {
                    errors.add("Rating must be at least " + minRating);
                }
                if (rating > maxRating) {
                    errors.add("Rating cannot exceed " + maxRating);
                }
            } catch (Exception e) {
                System.out.println("Error validating rating field '" + field.getName() + "': {}");
                errors.add("Rating validation error occurred");
            }
        } else if (value != null) {
            System.out.println("Invalid Rating Format for field '" + field.getName() + "'. Expected Number but got: {}");
            errors.add("Invalid rating format");
        }

        return errors.isEmpty() ? null : Map.of(field.getName(), errors);
    }
}
