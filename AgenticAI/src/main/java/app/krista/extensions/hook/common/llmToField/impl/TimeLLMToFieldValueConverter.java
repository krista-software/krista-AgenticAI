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
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

public class TimeLLMToFieldValueConverter implements LLMToFieldValueConverter<Long> {


    @Override
    public Long extract(Field field, Object input) {
        if(input instanceof String timeString) {

            try {
                // Define the expected time format: HH:MM:SS
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm:ss");

                // Parse the string to LocalTime
                LocalTime time = LocalTime.parse(timeString, formatter);

                // Get the timezone from field attributes if available
                ZoneId zoneId = ZoneId.systemDefault(); // Default to system timezone
                if (field.getAttributeValues() != null && field.getAttributeValues().get("timezone") != null) {
                    zoneId = ZoneId.of((String) field.getAttributeValues().get("timezone"));
                }

                // Convert to epoch milliseconds representing this time today in the specified timezone
                LocalDate today = LocalDate.now(zoneId);
                return time.atDate(today)
                        .atZone(zoneId)
                        .toInstant()
                        .toEpochMilli();

            } catch (DateTimeParseException e) {
                System.out.println("Failed to parse time string '" + timeString + "'. Expected format: HH:MM:SS. Error: " + e.getMessage());
                return null;
            } catch (Exception e) {
                System.out.println("Failed to process time string '" + timeString + "'. Error: " + e.getMessage());
                return null;
            }
        }
        else {
            System.out.println("Failed to extract Time value. Expected String input but got: " +
                (input != null ? input.getClass().getSimpleName() : "null"));
            return null;
        }
    }
}
