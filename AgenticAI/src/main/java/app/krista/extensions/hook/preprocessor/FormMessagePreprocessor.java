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

package app.krista.extensions.hook.preprocessor;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Preprocessor for formMessage content to handle specific field transformations
 * before the message is processed by step handlers.
 * 
 * Currently handles:
 * - Phone number JSON objects: Replaces phone number JSON with just the national number
 */
public class FormMessagePreprocessor {

    // Pattern to match phone number JSON objects with type, countryCode=0, and nationalNumber
    // Matches: {"type":"com.krista.net.model.fieldschemas.PhoneNumber","countryCode":0,"nationalNumber":7050337381}
    // Only matches when countryCode is 0 (numeric zero)
    private static final Pattern PHONE_NUMBER_JSON_PATTERN = Pattern.compile(
        "\\{\\s*\"type\"\\s*:\\s*\"com\\.krista\\.net\\.model\\.fieldschemas\\.PhoneNumber\"\\s*," +
        "\\s*\"countryCode\"\\s*:\\s*0\\s*," +
        "\\s*\"nationalNumber\"\\s*:\\s*(\\d+)\\s*\\}"
    );

    // Alternative pattern for different field order (nationalNumber first) - countryCode must be 0
    private static final Pattern PHONE_NUMBER_JSON_PATTERN_ALT = Pattern.compile(
        "\\{\\s*\"type\"\\s*:\\s*\"com\\.krista\\.net\\.model\\.fieldschemas\\.PhoneNumber\"\\s*," +
        "\\s*\"nationalNumber\"\\s*:\\s*(\\d+)\\s*," +
        "\\s*\"countryCode\"\\s*:\\s*0\\s*\\}"
    );

    // Pattern for countryCode first, then nationalNumber - countryCode must be 0
    private static final Pattern PHONE_NUMBER_JSON_PATTERN_COUNTRY_FIRST = Pattern.compile(
        "\\{\\s*\"countryCode\"\\s*:\\s*0\\s*," +
        "\\s*\"type\"\\s*:\\s*\"com\\.krista\\.net\\.model\\.fieldschemas\\.PhoneNumber\"\\s*," +
        "\\s*\"nationalNumber\"\\s*:\\s*(\\d+)\\s*\\}"
    );

    // Pattern for nationalNumber first, then countryCode - countryCode must be 0
    private static final Pattern PHONE_NUMBER_JSON_PATTERN_NATIONAL_FIRST = Pattern.compile(
        "\\{\\s*\"nationalNumber\"\\s*:\\s*(\\d+)\\s*," +
        "\\s*\"type\"\\s*:\\s*\"com\\.krista\\.net\\.model\\.fieldschemas\\.PhoneNumber\"\\s*," +
        "\\s*\"countryCode\"\\s*:\\s*0\\s*\\}"
    );

    // Additional patterns for other field orders - countryCode must be 0
    private static final Pattern PHONE_NUMBER_JSON_PATTERN_COUNTRY_NATIONAL_TYPE = Pattern.compile(
        "\\{\\s*\"countryCode\"\\s*:\\s*0\\s*," +
        "\\s*\"nationalNumber\"\\s*:\\s*(\\d+)\\s*," +
        "\\s*\"type\"\\s*:\\s*\"com\\.krista\\.net\\.model\\.fieldschemas\\.PhoneNumber\"\\s*\\}"
    );

    private static final Pattern PHONE_NUMBER_JSON_PATTERN_NATIONAL_COUNTRY_TYPE = Pattern.compile(
        "\\{\\s*\"nationalNumber\"\\s*:\\s*(\\d+)\\s*," +
        "\\s*\"countryCode\"\\s*:\\s*0\\s*," +
        "\\s*\"type\"\\s*:\\s*\"com\\.krista\\.net\\.model\\.fieldschemas\\.PhoneNumber\"\\s*\\}"
    );

    private FormMessagePreprocessor() {
        // Utility class - prevent instantiation
    }

    /**
     * Preprocesses the formMessage to replace phone number JSON objects with just the national number.
     * Only replaces phone number JSON when countryCode is 0.
     *
     * @param formMessage the original form message that may contain phone number JSON objects
     * @return the processed form message with phone number JSON replaced by national numbers (only when countryCode=0)
     */
    public static String preprocessFormMessage(String formMessage) {
        if (formMessage == null || formMessage.trim().isEmpty()) {
            return formMessage;
        }

        System.out.println("Original formMessage: " + formMessage);

        String processedMessage = formMessage;

        // Apply all phone number patterns to handle different field orders
        processedMessage = replacePhoneNumberJson(processedMessage, PHONE_NUMBER_JSON_PATTERN);
        processedMessage = replacePhoneNumberJson(processedMessage, PHONE_NUMBER_JSON_PATTERN_ALT);
        processedMessage = replacePhoneNumberJson(processedMessage, PHONE_NUMBER_JSON_PATTERN_COUNTRY_FIRST);
        processedMessage = replacePhoneNumberJson(processedMessage, PHONE_NUMBER_JSON_PATTERN_NATIONAL_FIRST);
        processedMessage = replacePhoneNumberJson(processedMessage, PHONE_NUMBER_JSON_PATTERN_COUNTRY_NATIONAL_TYPE);
        processedMessage = replacePhoneNumberJson(processedMessage, PHONE_NUMBER_JSON_PATTERN_NATIONAL_COUNTRY_TYPE);

        if (!processedMessage.equals(formMessage)) {
            System.out.println("Processed formMessage: " + processedMessage);
        }

        return processedMessage;
    }

    /**
     * Replaces phone number JSON objects with the national number using the given pattern.
     * 
     * @param message the message to process
     * @param pattern the regex pattern to match phone number JSON
     * @return the message with phone number JSON replaced
     */
    private static String replacePhoneNumberJson(String message, Pattern pattern) {
        Matcher matcher = pattern.matcher(message);
        StringBuffer result = new StringBuffer();

        while (matcher.find()) {
            String nationalNumber = matcher.group(1);
            
            // Only replace if nationalNumber is not empty and not zero
            // Note: countryCode=0 condition is already handled by the regex patterns
            if (nationalNumber != null && !nationalNumber.trim().isEmpty() && !nationalNumber.equals("0")) {
                System.out.println("Replacing phone number JSON (countryCode=0) with national number: " + nationalNumber);
                matcher.appendReplacement(result, nationalNumber);
            } else {
                // Keep original if nationalNumber is empty or zero
                matcher.appendReplacement(result, matcher.group(0));
            }
        }
        matcher.appendTail(result);

        return result.toString();
    }

    /**
     * Checks if the formMessage contains phone number JSON objects that need preprocessing.
     * Only detects phone number JSON objects with countryCode=0.
     * This can be used to optimize processing by skipping preprocessing when not needed.
     *
     * @param formMessage the form message to check
     * @return true if the message contains phone number JSON objects with countryCode=0, false otherwise
     */
    public static boolean containsPhoneNumberJson(String formMessage) {
        if (formMessage == null || formMessage.trim().isEmpty()) {
            return false;
        }

        return PHONE_NUMBER_JSON_PATTERN.matcher(formMessage).find() ||
               PHONE_NUMBER_JSON_PATTERN_ALT.matcher(formMessage).find() ||
               PHONE_NUMBER_JSON_PATTERN_COUNTRY_FIRST.matcher(formMessage).find() ||
               PHONE_NUMBER_JSON_PATTERN_NATIONAL_FIRST.matcher(formMessage).find() ||
               PHONE_NUMBER_JSON_PATTERN_COUNTRY_NATIONAL_TYPE.matcher(formMessage).find() ||
               PHONE_NUMBER_JSON_PATTERN_NATIONAL_COUNTRY_TYPE.matcher(formMessage).find();
    }
}
