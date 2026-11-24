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

package app.krista.extensions.hook.helper;

import app.krista.extensions.hook.common.FreeFormHelper;
import app.krista.model.base.FreeForm;

import java.util.Map;

/**
 * Helper utility class for common step handler operations
 */
public class StepHandlerHelper {

    private StepHandlerHelper() {
        // Utility class - prevent instantiation
    }

    /**
     * Checks if currentVariables FreeForm is empty (has no field definitions).
     * This is used to optimize step handlers by avoiding unnecessary LLM calls
     * when there are no variables to process.
     *
     * @param currentVariables the FreeForm to check
     * @return true if FreeForm has no fields, false otherwise
     * @throws UnsupportedFieldTypeException if the FreeForm contains unsupported field types
     */
    public static boolean isCurrentVariablesEmpty(FreeForm currentVariables) throws UnsupportedFieldTypeException {
        if (currentVariables == null) {
            return true;
        }

        try {
            Map<String, Object> fieldValueMap = FreeFormHelper.convertFreeFormToFieldValueMap(currentVariables);
            return fieldValueMap.isEmpty();
        } catch (Exception e) {
            // Check if this is an unsupported field type exception
            if (isUnsupportedFieldTypeException(e)) {
                throw new UnsupportedFieldTypeException("Unsupported field type detected: " + e.getMessage());
            }
            System.out.println("Error checking if currentVariables is empty: " + e.getMessage());
            return true; // Assume empty if we can't determine
        }
    }

    /**
     * Checks if a MessageTemplate contains krista field variables.
     * This is used to determine if LLM processing is needed for MessageTemplate strings.
     *
     * @param messageTemplate the MessageTemplate string to check
     * @return true if the MessageTemplate contains krista field variables, false otherwise
     */
    public static boolean containsKristaFieldVariables(String messageTemplate) {
        if (messageTemplate == null || messageTemplate.trim().isEmpty()) {
            return false;
        }

        // Check for krista-field tags using case-insensitive search
        return messageTemplate.toLowerCase().contains("<krista-field");
    }

    /**
     * Checks if a formMessage contains JSON field data that needs LLM processing.
     * This detects serialized field objects like phone numbers, dates, etc. that need
     * to be converted to human-readable format by the LLM.
     *
     * @param formMessage the form message string to check
     * @return true if the formMessage contains JSON field data, false otherwise
     */
    public static boolean containsJsonFieldData(String formMessage) {
        if (formMessage == null || formMessage.trim().isEmpty()) {
            return false;
        }

        // Check for JSON objects with "type" field indicating serialized field data
        // Common patterns: {"type":"com.krista.net.model.fieldschemas.PhoneNumber",...}
        //                  {"type":"com.krista.net.model.fieldschemas.DateRange",...}
        return formMessage.contains("\"type\":\"com.krista.net.model.fieldschemas.");
    }

    /**
     * Checks if the exception is caused by an unsupported field type
     */
    public static boolean isUnsupportedFieldTypeException(Exception e) {
        // Check if the exception message contains the unsupported field type indicator
        String message = e.getMessage();
        if (message != null && message.contains("No converter registered for field type")) {
            return true;
        }

        // Check the cause chain for unsupported field type exceptions
        Throwable cause = e.getCause();
        while (cause != null) {
            String causeMessage = cause.getMessage();
            if (causeMessage != null && causeMessage.contains("No converter registered for field type")) {
                return true;
            }
            cause = cause.getCause();
        }

        return false;
    }

    /**
     * Custom exception for unsupported field types
     */
    public static class UnsupportedFieldTypeException extends Exception {
        public UnsupportedFieldTypeException(String message) {
            super(message);
        }
    }
}
