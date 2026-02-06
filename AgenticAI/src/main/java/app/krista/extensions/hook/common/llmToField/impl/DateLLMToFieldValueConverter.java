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

package app.krista.extensions.hook.common.llmToField.impl;

import app.krista.extensions.hook.common.llmToField.LLMToFieldValueConverter;
import app.krista.model.field.Field;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

public class DateLLMToFieldValueConverter implements LLMToFieldValueConverter<Long> {


    @Override
    public Long extract(Field field, Object input) {
        if(input instanceof String dateString) {
            // Get timezone from field attributes if available
            String zoneId = null;
            if (field.getAttributeValues() != null && field.getAttributeValues().get("timezone") != null) {
                zoneId = (String) field.getAttributeValues().get("timezone");
            }
            return parseDateString(dateString, zoneId);
        }
        else {
            System.out.println("Failed to extract Date value. Expected String input but got: " +
                (input != null ? input.getClass().getSimpleName() : "null"));
            return null;
        }
    }

    private Long parseDateString(String dateString, String zoneId) {
        // Define all supported date formats in order of preference
        DateTimeFormatter[] formattersWithTime = {
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"), // Primary format with time
            DateTimeFormatter.ofPattern("MM/dd/yyyy HH:mm:ss")  // Fallback format with time
        };

        DateTimeFormatter[] formattersDateOnly = {
            DateTimeFormatter.ofPattern("dd/MM/yyyy"), // Primary format date-only
            DateTimeFormatter.ofPattern("MM/dd/yyyy")  // Fallback format date-only
        };

        LocalDateTime dateTime = null;

        // First try parsing with time formats
        for (DateTimeFormatter formatter : formattersWithTime) {
            try {
                dateTime = LocalDateTime.parse(dateString, formatter);
                break;
            } catch (DateTimeParseException e) {
                // Continue to next format
            }
        }

        // If time formats failed, try date-only formats and set time to start of day
        if (dateTime == null) {
            for (DateTimeFormatter formatter : formattersDateOnly) {
                try {
                    LocalDate date = LocalDate.parse(dateString, formatter);
                    dateTime = date.atStartOfDay();
                    break;
                } catch (DateTimeParseException e) {
                    // Continue to next format
                }
            }
        }

        if (dateTime == null) {
            System.out.println("Failed to parse date string '" + dateString + "'. Expected formats: dd/MM/yyyy, MM/dd/yyyy, dd/MM/yyyy HH:mm:ss, or MM/dd/yyyy HH:mm:ss");
            return null;
        }

        try {
            // If timezone is specified, use it; otherwise use system default
            if (zoneId != null) {
                return dateTime.atZone(ZoneId.of(zoneId)).toInstant().toEpochMilli();
            } else {
                return dateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
            }
        } catch (Exception e) {
            System.out.println("Failed to process date string '" + dateString + "'. Error: " + e.getMessage());
            return null;
        }
    }
}
