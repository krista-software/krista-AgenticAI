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

/**
 * Manual test to verify FormMessagePreprocessor works with the exact example from logs
 */
public class FormMessagePreprocessorManualTest {

    public static void main(String[] args) {
        // Test with the exact format from logs
        String originalMessage = "<p align=\"left\">Here is your number {\"type\":\"com.krista.net.model.fieldschemas.PhoneNumber\",\"countryCode\":0,\"nationalNumber\":7050337381}</p>";
        
        System.out.println("=== FormMessagePreprocessor Manual Test ===");
        System.out.println("Original message:");
        System.out.println(originalMessage);
        System.out.println();
        
        String processedMessage = FormMessagePreprocessor.preprocessFormMessage(originalMessage);
        
        System.out.println("Processed message:");
        System.out.println(processedMessage);
        System.out.println();
        
        String expectedMessage = "<p align=\"left\">Here is your number 7050337381</p>";
        boolean isCorrect = expectedMessage.equals(processedMessage);
        
        System.out.println("Expected message:");
        System.out.println(expectedMessage);
        System.out.println();
        
        System.out.println("Test result: " + (isCorrect ? "PASS" : "FAIL"));
        
        if (!isCorrect) {
            System.out.println("ERROR: Processed message does not match expected result!");
        }
        
        // Test with non-zero country code (should remain unchanged)
        System.out.println("\n=== Test with non-zero country code ===");
        String nonZeroCountryMessage = "<p align=\"left\">Here is your number {\"type\":\"com.krista.net.model.fieldschemas.PhoneNumber\",\"countryCode\":91,\"nationalNumber\":7050337381}</p>";
        System.out.println("Original message with countryCode=91:");
        System.out.println(nonZeroCountryMessage);
        
        String processedNonZero = FormMessagePreprocessor.preprocessFormMessage(nonZeroCountryMessage);
        System.out.println("Processed message (should be unchanged):");
        System.out.println(processedNonZero);
        
        boolean nonZeroCorrect = nonZeroCountryMessage.equals(processedNonZero);
        System.out.println("Non-zero country code test result: " + (nonZeroCorrect ? "PASS" : "FAIL"));
        
        if (!nonZeroCorrect) {
            System.out.println("ERROR: Message with non-zero country code was modified when it shouldn't be!");
        }
    }
}
