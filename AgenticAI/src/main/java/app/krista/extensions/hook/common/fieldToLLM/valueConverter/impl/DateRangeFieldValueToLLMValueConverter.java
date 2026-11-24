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
import app.krista.model.base.DateRange;
import app.krista.model.field.Field;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

public class DateRangeFieldValueToLLMValueConverter implements FieldValueToLLMValueConverter<Map<String, String>> {

    @Override
    public Map<String, String> extract(Field field, Object fieldValue) throws Exception {

        if (fieldValue instanceof DateRange dateRange) {

            // Get the timezone from field attributes if available
            ZoneId zoneId = ZoneId.systemDefault(); // Default to system timezone
            if (field.getAttributeValues() != null && field.getAttributeValues().get("timezone") != null) {
                zoneId = ZoneId.of((String) field.getAttributeValues().get("timezone"));
            }

            // Convert from and to timestamps to formatted strings using the appropriate timezone
            LocalDateTime fromDateTime = LocalDateTime.ofInstant(
                    Instant.ofEpochMilli(dateRange.getFrom()), zoneId);

            LocalDateTime toDateTime = LocalDateTime.ofInstant(
                    Instant.ofEpochMilli(dateRange.getTo()), zoneId);

            // Create and return a map with the formatted date strings
            Map<String, String> dateRangeMap = new HashMap<>();
            dateRangeMap.put("startOfDateRange", fromDateTime.format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")));
            dateRangeMap.put("endOfDateRange", toDateTime.format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")));

            return dateRangeMap;
        } else {
            throw new Exception("Failed to extract DateRange value from type: " + fieldValue.getClass().getName());
        }
    }
}