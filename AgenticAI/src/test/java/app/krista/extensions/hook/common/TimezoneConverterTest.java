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

package app.krista.extensions.hook.common;

import app.krista.extensions.hook.common.fieldToLLM.valueConverter.impl.DateFieldValueToLLMValueConverter;
import app.krista.extensions.hook.common.fieldToLLM.valueConverter.impl.DateRangeFieldValueToLLMValueConverter;
import app.krista.extensions.hook.common.fieldToLLM.valueConverter.impl.TimeFieldValueToLLMValueConverter;
import app.krista.extensions.hook.common.llmToField.impl.DateLLMToFieldValueConverter;
import app.krista.extensions.hook.common.llmToField.impl.DateRangeLLMToFieldValueConverter;
import app.krista.extensions.hook.common.llmToField.impl.TimeLLMToFieldValueConverter;
import app.krista.model.base.DateRange;
import app.krista.model.field.Field;
import org.junit.Test;
import org.junit.Before;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Map;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class TimezoneConverterTest {

    @Mock
    private Field dateField;

    @Mock
    private Field dateRangeField;

    @Mock
    private Field timeField;

    private DateFieldValueToLLMValueConverter dateToLLMConverter;
    private DateLLMToFieldValueConverter llmToDateConverter;
    private DateRangeFieldValueToLLMValueConverter dateRangeToLLMConverter;
    private DateRangeLLMToFieldValueConverter llmToDateRangeConverter;
    private TimeFieldValueToLLMValueConverter timeToLLMConverter;
    private TimeLLMToFieldValueConverter llmToTimeConverter;

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        dateToLLMConverter = new DateFieldValueToLLMValueConverter();
        llmToDateConverter = new DateLLMToFieldValueConverter();
        dateRangeToLLMConverter = new DateRangeFieldValueToLLMValueConverter();
        llmToDateRangeConverter = new DateRangeLLMToFieldValueConverter();
        timeToLLMConverter = new TimeFieldValueToLLMValueConverter();
        llmToTimeConverter = new TimeLLMToFieldValueConverter();
    }

    @Test
    public void testDateConverter_WithTimezone_ConvertsCorrectly() throws Exception {
        // Arrange: Create a timestamp for 2024-01-01 12:00:00 UTC
        LocalDateTime utcDateTime = LocalDateTime.of(2024, 1, 1, 12, 0, 0);
        Long utcTimestamp = utcDateTime.toInstant(ZoneOffset.UTC).toEpochMilli();

        // Mock field with Asia/Kolkata timezone (+5:30)
        when(dateField.getAttributeValues()).thenReturn(Map.of("timezone", "Asia/Kolkata"));

        // Act: Convert to LLM format
        String llmValue = dateToLLMConverter.extract(dateField, utcTimestamp);

        // Assert: Should show 17:30:00 (12:00 + 5:30)
        assertEquals("01/01/2024 17:30:00", llmValue);

        // Act: Convert back to field value
        Long convertedBack = llmToDateConverter.extract(dateField, llmValue);

        // Assert: Should get back the original UTC timestamp
        assertEquals(utcTimestamp, convertedBack);
    }

    @Test
    public void testDateRangeConverter_WithTimezone_ConvertsCorrectly() throws Exception {
        // Arrange: Create timestamps for a date range
        LocalDateTime startUtc = LocalDateTime.of(2024, 1, 1, 10, 0, 0);
        LocalDateTime endUtc = LocalDateTime.of(2024, 1, 1, 18, 0, 0);
        Long startTimestamp = startUtc.toInstant(ZoneOffset.UTC).toEpochMilli();
        Long endTimestamp = endUtc.toInstant(ZoneOffset.UTC).toEpochMilli();
        DateRange dateRange = new DateRange(startTimestamp, endTimestamp);

        // Mock field with US/Eastern timezone (-5:00 in winter)
        when(dateRangeField.getAttributeValues()).thenReturn(Map.of("timezone", "US/Eastern"));

        // Act: Convert to LLM format
        Map<String, String> llmValue = dateRangeToLLMConverter.extract(dateRangeField, dateRange);

        // Assert: Should show Eastern time (UTC-5)
        assertNotNull(llmValue);
        assertEquals("01/01/2024 05:00:00", llmValue.get("startOfDateRange"));  // 10:00 UTC - 5 hours
        assertEquals("01/01/2024 13:00:00", llmValue.get("endOfDateRange"));    // 18:00 UTC - 5 hours

        // Act: Convert back to field value using the map representation
        DateRange convertedBack = llmToDateRangeConverter.extract(dateRangeField, llmValue);

        // Assert: Should get back the original timestamps
        assertEquals(startTimestamp, Long.valueOf(convertedBack.getFrom()));
        assertEquals(endTimestamp, Long.valueOf(convertedBack.getTo()));
    }

    @Test
    public void testTimeConverter_WithTimezone_ConvertsCorrectly() throws Exception {
        // Arrange: Create a timestamp for 14:30:00 today in Asia/Kolkata timezone
        LocalDateTime todayInKolkata = LocalDateTime.of(2024, 1, 1, 14, 30, 0);
        Long kolkataTimestamp = todayInKolkata.atZone(ZoneId.of("Asia/Kolkata")).toInstant().toEpochMilli();

        // Mock field with Asia/Kolkata timezone
        when(timeField.getAttributeValues()).thenReturn(Map.of("timezone", "Asia/Kolkata"));

        // Act: Convert to LLM format
        String llmValue = timeToLLMConverter.extract(timeField, kolkataTimestamp);

        // Assert: Should show the local time in Kolkata
        assertEquals("14:30:00", llmValue);

        // Act: Convert back to field value (will use today's date in Kolkata timezone)
        Long convertedBack = llmToTimeConverter.extract(timeField, llmValue);

        // Assert: Should represent 14:30:00 today in Kolkata timezone
        // The exact timestamp will depend on today's date, but the time should be consistent
        LocalDateTime convertedDateTime = Instant.ofEpochMilli(convertedBack)
                .atZone(ZoneId.of("Asia/Kolkata"))
                .toLocalDateTime();
        assertEquals(14, convertedDateTime.getHour());
        assertEquals(30, convertedDateTime.getMinute());
        assertEquals(0, convertedDateTime.getSecond());
    }
}
