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

public class DateValidator extends DefaultFieldValidator {


    @Override
    public Map<String, Object> validate(NamedField field, Object value) {

        Map<String, Object> attributes = field.getAttributeValues();
        List<String> errors = new ArrayList<>();

        if (value instanceof Long) {
            try {
                long timestamp = (Long) value;

                // Check if date is in allowed range
                if (attributes.containsKey("allowedRange")) {
                    Object allowedRangeObj = attributes.get("allowedRange");
                    if (allowedRangeObj instanceof Map<?, ?>) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> allowedRange = (Map<String, Object>) allowedRangeObj;

                        if (allowedRange.containsKey("from")) {
                            Object fromObj = allowedRange.get("from");
                            if (fromObj instanceof Number) {
                                long minDate = ((Number) fromObj).longValue();
                                if (timestamp < minDate) {
                                    errors.add("Date is before the allowed minimum date");
                                }
                            }
                        }

                        if (allowedRange.containsKey("to")) {
                            Object toObj = allowedRange.get("to");
                            if (toObj instanceof Number) {
                                long maxDate = ((Number) toObj).longValue();
                                if (timestamp > maxDate) {
                                    errors.add("Date is after the allowed maximum date");
                                }
                            }
                        }
                    }
                }

                // Check allowPast, allowToday, allowFuture constraints
                long currentTime = System.currentTimeMillis();
                long oneDayMs = 24 * 60 * 60 * 1000L;

                if (attributes.containsKey("allowPast")) {
                    Object allowPastObj = attributes.get("allowPast");
                    if (allowPastObj instanceof Boolean && !(Boolean) allowPastObj) {
                        if (timestamp < currentTime - oneDayMs) {
                            errors.add("Past dates are not allowed");
                        }
                    }
                }

                if (attributes.containsKey("allowToday")) {
                    Object allowTodayObj = attributes.get("allowToday");
                    if (allowTodayObj instanceof Boolean && !(Boolean) allowTodayObj) {
                        if (Math.abs(timestamp - currentTime) <= oneDayMs) {
                            errors.add("Today's date is not allowed");
                        }
                    }
                }

                if (attributes.containsKey("allowFuture")) {
                    Object allowFutureObj = attributes.get("allowFuture");
                    if (allowFutureObj instanceof Boolean && !(Boolean) allowFutureObj) {
                        if (timestamp > currentTime + oneDayMs) {
                            errors.add("Future dates are not allowed");
                        }
                    }
                }
            } catch (Exception e) {
                System.out.println("Error validating date field '" + field.getName() + "': {}");
                errors.add("Date validation error occurred");
            }
        } else if (value != null) {
            System.out.println("Invalid Date Format for field '" + field.getName() + "'. Expected Long but got: {}");
            errors.add("Invalid date format");
        }

        return errors.isEmpty() ? null : Map.of(field.getName(), errors);
    }
}