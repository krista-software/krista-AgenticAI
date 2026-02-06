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
import app.krista.model.base.DateRange;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class DateTimeRangeValidator extends DefaultFieldValidator {


    @Override
    public Map<String, Object> validate(NamedField field, Object value) {

        Map<String, Object> attributes = field.getAttributeValues();
        List<String> errors = new ArrayList<>();

        if (value instanceof DateRange dateTimeRange) {
            try {
                long startTime = dateTimeRange.getFrom();
                long endTime = dateTimeRange.getTo();

                // Validate that start time is before end time
                if (startTime >= endTime) {
                    errors.add("Start date/time must be before end date/time");
                }

                // Check duration constraints in hours
                long durationMs = endTime - startTime;
                long durationHours = durationMs / (60 * 60 * 1000L);

                if (attributes.containsKey("maxDurationInHours")) {
                    Object maxHoursObj = attributes.get("maxDurationInHours");
                    if (maxHoursObj instanceof Number) {
                        int maxHours = ((Number) maxHoursObj).intValue();
                        if (durationHours > maxHours) {
                            errors.add("Duration cannot exceed " + maxHours + " hours");
                        }
                    }
                }

                if (attributes.containsKey("minDurationInHours")) {
                    Object minHoursObj = attributes.get("minDurationInHours");
                    if (minHoursObj instanceof Number) {
                        int minHours = ((Number) minHoursObj).intValue();
                        if (durationHours < minHours) {
                            errors.add("Duration must be at least " + minHours + " hours");
                        }
                    }
                }

                // Check if date/time range is within allowed range
                if (attributes.containsKey("allowedRange")) {
                    Object allowedRangeObj = attributes.get("allowedRange");
                    if (allowedRangeObj instanceof Map<?, ?>) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> allowedRange = (Map<String, Object>) allowedRangeObj;

                        if (allowedRange.containsKey("from")) {
                            Object fromObj = allowedRange.get("from");
                            if (fromObj instanceof Number) {
                                long minDateTime = ((Number) fromObj).longValue();
                                if (startTime < minDateTime || endTime < minDateTime) {
                                    errors.add("Date/time range is before the allowed minimum date/time");
                                }
                            }
                        }

                        if (allowedRange.containsKey("to")) {
                            Object toObj = allowedRange.get("to");
                            if (toObj instanceof Number) {
                                long maxDateTime = ((Number) toObj).longValue();
                                if (startTime > maxDateTime || endTime > maxDateTime) {
                                    errors.add("Date/time range is after the allowed maximum date/time");
                                }
                            }
                        }
                    }
                }

                // Check allowPast, allowFuture constraints
                long currentTime = System.currentTimeMillis();

                if (attributes.containsKey("allowPast")) {
                    Object allowPastObj = attributes.get("allowPast");
                    if (allowPastObj instanceof Boolean && !(Boolean) allowPastObj) {
                        if (endTime < currentTime) {
                            errors.add("Past date/times are not allowed");
                        }
                    }
                }

                if (attributes.containsKey("allowFuture")) {
                    Object allowFutureObj = attributes.get("allowFuture");
                    if (allowFutureObj instanceof Boolean && !(Boolean) allowFutureObj) {
                        if (startTime > currentTime) {
                            errors.add("Future date/times are not allowed");
                        }
                    }
                }
            } catch (Exception e) {
                System.out.println("Error validating date/time range field '" + field.getName() + "': {}");
                errors.add("Date/time range validation error occurred");
            }
        } else if (value != null) {
            System.out.println("Invalid DateTimeRange Format for field '" + field.getName() + "'. Expected DateRange but got: {}");
            errors.add("Invalid date/time range format");
        }

        return errors.isEmpty() ? null : Map.of(field.getName(), errors);
    }
}