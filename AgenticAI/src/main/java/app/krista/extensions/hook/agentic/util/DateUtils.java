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

package app.krista.extensions.hook.agentic.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;

/**
 * Utility class for date parsing and formatting.
 * Converts between epoch milliseconds and human-readable date strings.
 * 
 * This class mirrors the functionality from StudioAI's DateUtils
 * to ensure consistent date handling across extensions.
 */
public final class DateUtils {

    private static final Logger LOGGER = LoggerFactory.getLogger(DateUtils.class);

    // Formatters for different date/time patterns
    public static final DateTimeFormatter FORMATTER_WITH_SECONDS = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss");
    public static final DateTimeFormatter FORMATTER_NO_SECONDS = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm");
    public static final DateTimeFormatter FORMATTER_DATE_ONLY = DateTimeFormatter.ofPattern("dd-MM-yyyy");
    
    // Alternative formatters for flexibility
    public static final DateTimeFormatter ISO_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
    public static final DateTimeFormatter ISO_DATE_ONLY = DateTimeFormatter.ISO_LOCAL_DATE;

    // Parsers to try in order
    private static final List<DateTimeFormatter> PARSERS = List.of(
            FORMATTER_WITH_SECONDS,
            FORMATTER_NO_SECONDS,
            FORMATTER_DATE_ONLY,
            ISO_FORMATTER,
            ISO_DATE_ONLY
    );

    private DateUtils() {
        // Utility class - prevent instantiation
    }

    /**
     * Parses a date string to epoch milliseconds.
     * Tries multiple date formats in order of preference.
     * 
     * Supported formats:
     * - ISO format: 2024-01-15T10:30:00
     * - dd-MM-yyyy HH:mm:ss: 15-01-2024 10:30:00
     * - dd-MM-yyyy HH:mm: 15-01-2024 10:30
     * - dd-MM-yyyy: 15-01-2024
     * - yyyy-MM-dd: 2024-01-15
     * 
     * @param dateStr The date string to parse
     * @param zoneId The timezone for parsing
     * @return Epoch milliseconds, or null if parsing fails
     */
    public static Long parseToEpochMillis(String dateStr, ZoneId zoneId) {
        if (dateStr == null || dateStr.isBlank()) {
            return null;
        }

        ZoneId effectiveZoneId = zoneId != null ? zoneId : ZoneId.of("UTC");

        // Try ISO instant format first (e.g., 2024-01-15T10:30:00Z)
        try {
            return Instant.parse(dateStr).toEpochMilli();
        } catch (DateTimeParseException ignored) {
            // Not an ISO instant, continue with other formats
        }

        // Try each parser
        for (DateTimeFormatter formatter : PARSERS) {
            try {
                if (formatter == FORMATTER_DATE_ONLY || formatter == ISO_DATE_ONLY) {
                    // Date-only: parse as LocalDate and set to start of day
                    LocalDate date = LocalDate.parse(dateStr, formatter);
                    return date.atStartOfDay(effectiveZoneId).toInstant().toEpochMilli();
                } else {
                    // Date-time: parse as LocalDateTime
                    LocalDateTime dateTime = LocalDateTime.parse(dateStr, formatter);
                    return dateTime.atZone(effectiveZoneId).toInstant().toEpochMilli();
                }
            } catch (DateTimeParseException ignored) {
                // Try next parser
            }
        }

        LOGGER.warn("Could not parse date string: '{}' with any known format", dateStr);
        return null;
    }

    /**
     * Formats an epoch millisecond value to a human-readable string.
     * 
     * @param value The epoch milliseconds (Number or String)
     * @param zoneId The timezone for formatting
     * @return Formatted date string (dd-MM-yyyy HH:mm:ss), or null if invalid
     */
    public static String formatFromEpoch(Object value, ZoneId zoneId) {
        if (value == null) {
            return null;
        }

        ZoneId effectiveZoneId = zoneId != null ? zoneId : ZoneId.of("UTC");

        try {
            long epochMillis;
            if (value instanceof Number) {
                epochMillis = ((Number) value).longValue();
            } else {
                epochMillis = Long.parseLong(value.toString());
            }
            
            return Instant.ofEpochMilli(epochMillis)
                    .atZone(effectiveZoneId)
                    .format(FORMATTER_WITH_SECONDS);
        } catch (Exception e) {
            LOGGER.warn("Invalid epoch value '{}': {}", value, e.getMessage());
            return String.valueOf(value);
        }
    }

    /**
     * Formats an epoch millisecond value to date-only string.
     * 
     * @param value The epoch milliseconds
     * @param zoneId The timezone for formatting
     * @return Formatted date string (dd-MM-yyyy), or null if invalid
     */
    public static String formatDateOnlyFromEpoch(Object value, ZoneId zoneId) {
        if (value == null) {
            return null;
        }

        ZoneId effectiveZoneId = zoneId != null ? zoneId : ZoneId.of("UTC");

        try {
            long epochMillis = value instanceof Number 
                ? ((Number) value).longValue() 
                : Long.parseLong(value.toString());
            
            return Instant.ofEpochMilli(epochMillis)
                    .atZone(effectiveZoneId)
                    .format(FORMATTER_DATE_ONLY);
        } catch (Exception e) {
            LOGGER.warn("Invalid epoch value for date-only: '{}'", value);
            return null;
        }
    }

    /**
     * Formats a date range map (with 'from' and 'to' keys) to human-readable strings.
     * 
     * @param rangeMap Map containing 'from' and 'to' epoch values
     * @param zoneId The timezone for formatting
     * @return Map with formatted 'from' and 'to' strings
     */
    public static Map<String, Object> formatRange(Map<String, Object> rangeMap, ZoneId zoneId) {
        if (rangeMap == null) {
            return Map.of();
        }

        ZoneId effectiveZoneId = zoneId != null ? zoneId : ZoneId.of("UTC");

        Object formattedFrom = formatFromEpoch(rangeMap.get("from"), effectiveZoneId);
        Object formattedTo = formatFromEpoch(rangeMap.get("to"), effectiveZoneId);

        Map<String, Object> result = new java.util.HashMap<>();
        result.put("from", formattedFrom != null ? formattedFrom : rangeMap.get("from"));
        result.put("to", formattedTo != null ? formattedTo : rangeMap.get("to"));
        return result;
    }

    /**
     * Parses a date range map from human-readable strings to epoch values.
     * 
     * @param rangeMap Map containing 'from' and 'to' date strings
     * @param zoneId The timezone for parsing
     * @return Map with epoch millisecond 'from' and 'to' values
     */
    public static Map<String, Object> parseRangeToEpochs(Map<String, Object> rangeMap, ZoneId zoneId) {
        if (rangeMap == null) {
            return Map.of();
        }

        ZoneId effectiveZoneId = zoneId != null ? zoneId : ZoneId.of("UTC");

        Object fromValue = rangeMap.get("from");
        Object toValue = rangeMap.get("to");

        Long fromEpoch = null;
        Long toEpoch = null;

        if (fromValue instanceof String) {
            fromEpoch = parseToEpochMillis((String) fromValue, effectiveZoneId);
        } else if (fromValue instanceof Number) {
            fromEpoch = ((Number) fromValue).longValue();
        }

        if (toValue instanceof String) {
            toEpoch = parseToEpochMillis((String) toValue, effectiveZoneId);
        } else if (toValue instanceof Number) {
            toEpoch = ((Number) toValue).longValue();
        }

        Map<String, Object> result = new java.util.HashMap<>();
        if (fromEpoch != null) {

            result.put("from", fromEpoch);
        } else if (fromValue != null) {

            result.put("from", fromValue);
        }
        if (toEpoch != null) {

            result.put("to", toEpoch);
        } else if (toValue != null) {

            result.put("to", toValue);
        }
        return result;
    }

    /**
     * Checks if a string represents an epoch timestamp (all digits, reasonable length).
     * 
     * @param value The value to check
     * @return true if it looks like an epoch timestamp
     */
    public static boolean isEpochTimestamp(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        
        // Remove non-digit characters
        String digitsOnly = value.replaceAll("[^0-9]", "");
        
        // Epoch timestamps are typically 13 digits (milliseconds) or 10 digits (seconds)
        // Allow some flexibility: 9-14 digits
        return digitsOnly.length() >= 9 && digitsOnly.length() <= 14 && digitsOnly.equals(value.trim());
    }
}
