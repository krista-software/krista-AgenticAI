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

package app.krista.extensions.hook.common.fieldToLLM.valueConverter.impl;

import app.krista.extensions.hook.common.fieldToLLM.valueConverter.FieldValueToLLMValueConverter;
import app.krista.model.field.Field;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public class TimeFieldValueToLLMValueConverter implements FieldValueToLLMValueConverter<String> {

    @Override
    public String extract(Field field, Object fieldValue) throws Exception {

        if (fieldValue instanceof Long) {
            // Time fields store epoch milliseconds representing a specific moment in time
            // Convert to local time in the specified timezone
            long timestamp = (Long) fieldValue;

            // Get the timezone from field attributes if available
            ZoneId zoneId = ZoneId.systemDefault(); // Default to system timezone
            if (field.getAttributeValues() != null && field.getAttributeValues().get("timezone") != null) {
                zoneId = ZoneId.of((String) field.getAttributeValues().get("timezone"));
            }

            // Convert timestamp to local time in the specified timezone
            LocalTime time = Instant.ofEpochMilli(timestamp)
                    .atZone(zoneId)
                    .toLocalTime();

            return time.format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        } else {
            throw new Exception("Failed to extract Time value from type: " + fieldValue.getClass().getName());
        }
    }
}