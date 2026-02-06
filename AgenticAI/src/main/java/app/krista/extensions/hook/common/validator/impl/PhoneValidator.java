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

import app.krista.model.base.PhoneNumber;
import app.krista.model.field.NamedField;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class PhoneValidator extends DefaultFieldValidator {


    @Override
    public Map<String, Object> validate(NamedField field, Object value) {
        List<String> errors = new ArrayList<>();

        if (value instanceof PhoneNumber phoneNumber) {
            try {
                // Validate phone number components
                if (phoneNumber.getCountryCode() < 0) {
                    errors.add("Invalid country code");
                }
                if (phoneNumber.getNationalNumber() <= 0) {
                    errors.add("Invalid phone number");
                }

                // Additional validation can be added here based on field attributes
                // For example, checking against specific country code requirements
                Map<String, Object> attributes = field.getAttributeValues();
                if (attributes.containsKey("countryCode")) {
                    Object expectedCountryCodeObj = attributes.get("countryCode");
                    if (expectedCountryCodeObj instanceof Number) {
                        int expectedCountryCode = ((Number) expectedCountryCodeObj).intValue();
                        if (phoneNumber.getCountryCode() != expectedCountryCode) {
                            errors.add("Country code must be " + expectedCountryCode);
                        }
                    }
                }

                // Validate phone number length based on formatPattern
                if (attributes.containsKey("formatPattern")) {
                    Object formatPatternObj = attributes.get("formatPattern");
                    if (formatPatternObj instanceof String formatPattern) {
                        int expectedDigits = countDigitPlaceholders(formatPattern);
                        if (expectedDigits > 0) {
                            String nationalNumberStr = String.valueOf(phoneNumber.getNationalNumber());
                            if (nationalNumberStr.length() != expectedDigits) {
                                errors.add("Phone number must be exactly " + expectedDigits + " digits as per format pattern");
                            }
                        }
                    }
                }

                // Always enforce E.164 maximum
                String nationalNumberStr = String.valueOf(phoneNumber.getNationalNumber());
                if (nationalNumberStr.length() > 14) {
                    errors.add("National phone number exceeds maximum 14 digits (E.164 standard)");
                }
            } catch (Exception e) {
                System.out.println("Error validating phone number for field '" + field.getName() + "': {}");
                errors.add("Phone number validation error occurred");
            }
        } else if (value != null) {
            System.out.println("Invalid Phone number Format for field '" + field.getName() + "'. Expected PhoneNumber but got: {}");
            errors.add("Invalid phone number format");
        }

        return errors.isEmpty() ? null : Map.of(field.getName(), errors);
    }

    /**
     * Counts digit placeholders (#) in format pattern to determine expected phone number length
     */
    private int countDigitPlaceholders(String formatPattern) {
        if (formatPattern == null) return 0;

        int count = 0;
        for (char c : formatPattern.toCharArray()) {
            if (c == '#') {
                count++;
            }
        }
        return count;
    }
}
