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
import app.krista.model.base.PhoneNumber;
import app.krista.model.field.Field;


import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PhoneNumberLLMToFieldValueConverter implements LLMToFieldValueConverter<PhoneNumber> {

    // Default country code when none is specified
    private static final int DEFAULT_COUNTRY_CODE = 0;
    private static final Pattern COUNTRY_CODE_PATTERN = Pattern.compile("^\\+?(\\d+)");

    @Override
    public PhoneNumber extract(Field field, Object input) {
        if (input instanceof Number || input instanceof String) {
            try {
                // Extract country code from formatPattern, countryCode attribute, or use default
                int countryCode = extractCountryCode(field);

                // Parse phone number from input (should be national number only)
                long phoneNumber = parsePhoneNumber(input);
                if (phoneNumber <= 0) {
                    System.out.println("Failed to extract Phone number. Invalid phone number: " + input);
                    return null;
                }

                return new PhoneNumber(countryCode, phoneNumber);
            } catch (Exception e) {
                System.out.println("Failed to create PhoneNumber object. Error: " + e.getMessage());
                return null;
            }
        } else {
            System.out.println("Failed to extract Phone number. Expected Number or String but got: " +
                (input != null ? input.getClass().getSimpleName() : "null"));
            return null;
        }
    }

    /**
     * Extracts country code from formatPattern attribute, falls back to countryCode attribute,
     * or returns default country code if none is specified
     */
    private int extractCountryCode(Field field) {
        // First try to extract from formatPattern
        Object formatPatternObj = field.getAttributeValues().get("formatPattern");
        if (formatPatternObj instanceof String formatPattern) {
            Matcher matcher = COUNTRY_CODE_PATTERN.matcher(formatPattern);
            if (matcher.find()) {
                try {
                    return Integer.parseInt(matcher.group(1));
                } catch (NumberFormatException e) {
                    System.out.println("Failed to parse country code from formatPattern: " + formatPattern);
                }
            }
        }

        // Fallback to countryCode attribute for backward compatibility
        Object countryCodeObj = field.getAttributeValues().get("countryCode");
        if (countryCodeObj instanceof Number) {
            return ((Number) countryCodeObj).intValue();
        }

        // Use default country code when none is specified
        System.out.println("No country code found in formatPattern or countryCode attribute. Using default country code: +" + DEFAULT_COUNTRY_CODE);
        return DEFAULT_COUNTRY_CODE;
    }

    /**
     * Parses national phone number from input (Number or String)
     * Input should contain only the national number, not the country code
     */
    private long parsePhoneNumber(Object input) {
        if (input instanceof Number) {
            return ((Number) input).longValue();
        } else if (input instanceof String) {
            String phoneStr = (String) input;
            // Remove all non-digit characters (spaces, dashes, etc.)
            String digitsOnly = phoneStr.replaceAll("\\D", "");
            if (!digitsOnly.isEmpty()) {
                try {
                    return Long.parseLong(digitsOnly);
                } catch (NumberFormatException e) {
                    System.out.println("Failed to parse phone number from string: " + phoneStr);
                }
            }
        }
        return 0; // Invalid phone number
    }


}
