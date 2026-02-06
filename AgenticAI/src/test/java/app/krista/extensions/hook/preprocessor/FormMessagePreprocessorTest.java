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

import org.junit.Test;
import static org.junit.Assert.*;

public class FormMessagePreprocessorTest {

    @Test
    public void testPreprocessFormMessage_WithPhoneNumberJson_ReplacesWithNationalNumber() {
        // Test with the exact format from logs
        String formMessage = "<p align=\"left\">Here is your number {\"type\":\"com.krista.net.model.fieldschemas.PhoneNumber\",\"countryCode\":0,\"nationalNumber\":7050337381}</p>";
        
        String result = FormMessagePreprocessor.preprocessFormMessage(formMessage);
        
        String expected = "<p align=\"left\">Here is your number 7050337381</p>";
        assertEquals("Phone number JSON should be replaced with national number", expected, result);
    }

    @Test
    public void testPreprocessFormMessage_WithNonZeroCountryCode_KeepsOriginal() {
        String formMessage = "<p>Contact: {\"type\":\"com.krista.net.model.fieldschemas.PhoneNumber\",\"countryCode\":\"+91\",\"nationalNumber\":7050337381}</p>";

        String result = FormMessagePreprocessor.preprocessFormMessage(formMessage);

        // Should keep original when countryCode is not 0
        assertEquals("Phone number JSON with non-zero country code should remain unchanged", formMessage, result);
    }

    @Test
    public void testPreprocessFormMessage_WithMultiplePhoneNumbers_ReplacesOnlyCountryCodeZero() {
        String formMessage = "Primary: {\"type\":\"com.krista.net.model.fieldschemas.PhoneNumber\",\"countryCode\":0,\"nationalNumber\":7050337381} " +
                           "Secondary: {\"type\":\"com.krista.net.model.fieldschemas.PhoneNumber\",\"countryCode\":\"+91\",\"nationalNumber\":9876543210}";

        String result = FormMessagePreprocessor.preprocessFormMessage(formMessage);

        String expected = "Primary: 7050337381 Secondary: {\"type\":\"com.krista.net.model.fieldschemas.PhoneNumber\",\"countryCode\":\"+91\",\"nationalNumber\":9876543210}";
        assertEquals("Only phone number JSON with countryCode=0 should be replaced", expected, result);
    }

    @Test
    public void testPreprocessFormMessage_WithDifferentFieldOrder_ReplacesCorrectly() {
        // Test with nationalNumber first
        String formMessage = "Phone: {\"nationalNumber\":7050337381,\"type\":\"com.krista.net.model.fieldschemas.PhoneNumber\",\"countryCode\":0}";
        
        String result = FormMessagePreprocessor.preprocessFormMessage(formMessage);
        
        String expected = "Phone: 7050337381";
        assertEquals("Phone number JSON with different field order should be replaced", expected, result);
    }

    @Test
    public void testPreprocessFormMessage_WithCountryCodeZeroFirst_ReplacesCorrectly() {
        // Test with countryCode=0 first
        String formMessage = "Phone: {\"countryCode\":0,\"type\":\"com.krista.net.model.fieldschemas.PhoneNumber\",\"nationalNumber\":7050337381}";

        String result = FormMessagePreprocessor.preprocessFormMessage(formMessage);

        String expected = "Phone: 7050337381";
        assertEquals("Phone number JSON with countryCode=0 first should be replaced", expected, result);
    }

    @Test
    public void testPreprocessFormMessage_WithSpacesInJsonAndCountryCodeZero_ReplacesCorrectly() {
        String formMessage = "Phone: { \"type\" : \"com.krista.net.model.fieldschemas.PhoneNumber\" , \"countryCode\" : 0 , \"nationalNumber\" : 7050337381 }";

        String result = FormMessagePreprocessor.preprocessFormMessage(formMessage);

        String expected = "Phone: 7050337381";
        assertEquals("Phone number JSON with extra spaces and countryCode=0 should be replaced", expected, result);
    }

    @Test
    public void testPreprocessFormMessage_WithEmptyNationalNumber_KeepsOriginal() {
        String formMessage = "<p>Phone: {\"type\":\"com.krista.net.model.fieldschemas.PhoneNumber\",\"countryCode\":0,\"nationalNumber\":\"\"}</p>";
        
        String result = FormMessagePreprocessor.preprocessFormMessage(formMessage);
        
        // Should keep original when nationalNumber is empty
        assertEquals("Empty national number should keep original JSON", formMessage, result);
    }

    @Test
    public void testPreprocessFormMessage_WithZeroNationalNumber_KeepsOriginal() {
        String formMessage = "<p>Phone: {\"type\":\"com.krista.net.model.fieldschemas.PhoneNumber\",\"countryCode\":0,\"nationalNumber\":0}</p>";
        
        String result = FormMessagePreprocessor.preprocessFormMessage(formMessage);
        
        // Should keep original when nationalNumber is zero
        assertEquals("Zero national number should keep original JSON", formMessage, result);
    }

    @Test
    public void testPreprocessFormMessage_WithoutPhoneNumberJson_ReturnsUnchanged() {
        String formMessage = "<p>This is a regular message without phone numbers</p>";
        
        String result = FormMessagePreprocessor.preprocessFormMessage(formMessage);
        
        assertEquals("Message without phone number JSON should remain unchanged", formMessage, result);
    }

    @Test
    public void testPreprocessFormMessage_WithOtherJsonTypes_ReturnsUnchanged() {
        String formMessage = "<p>Date: {\"type\":\"com.krista.net.model.fieldschemas.DateRange\",\"from\":1746374556566,\"to\":1746550956566}</p>";
        
        String result = FormMessagePreprocessor.preprocessFormMessage(formMessage);
        
        assertEquals("Other JSON types should remain unchanged", formMessage, result);
    }

    @Test
    public void testPreprocessFormMessage_WithNullInput_ReturnsNull() {
        String result = FormMessagePreprocessor.preprocessFormMessage(null);
        
        assertNull("Null input should return null", result);
    }

    @Test
    public void testPreprocessFormMessage_WithEmptyInput_ReturnsEmpty() {
        String result = FormMessagePreprocessor.preprocessFormMessage("");
        
        assertEquals("Empty input should return empty", "", result);
    }

    @Test
    public void testPreprocessFormMessage_WithWhitespaceInput_ReturnsUnchanged() {
        String formMessage = "   ";
        String result = FormMessagePreprocessor.preprocessFormMessage(formMessage);
        
        assertEquals("Whitespace input should return unchanged", formMessage, result);
    }

    @Test
    public void testPreprocessFormMessage_WithNonZeroCountryCodeNumeric_KeepsOriginal() {
        String formMessage = "<p>Phone: {\"type\":\"com.krista.net.model.fieldschemas.PhoneNumber\",\"countryCode\":91,\"nationalNumber\":7050337381}</p>";

        String result = FormMessagePreprocessor.preprocessFormMessage(formMessage);

        // Should keep original when countryCode is not 0
        assertEquals("Phone number JSON with non-zero numeric country code should remain unchanged", formMessage, result);
    }

    @Test
    public void testContainsPhoneNumberJson_WithPhoneNumberJsonCountryCodeZero_ReturnsTrue() {
        String formMessage = "<p>Phone: {\"type\":\"com.krista.net.model.fieldschemas.PhoneNumber\",\"countryCode\":0,\"nationalNumber\":7050337381}</p>";

        boolean result = FormMessagePreprocessor.containsPhoneNumberJson(formMessage);

        assertTrue("Should detect phone number JSON with countryCode=0", result);
    }

    @Test
    public void testContainsPhoneNumberJson_WithPhoneNumberJsonNonZeroCountryCode_ReturnsFalse() {
        String formMessage = "<p>Phone: {\"type\":\"com.krista.net.model.fieldschemas.PhoneNumber\",\"countryCode\":91,\"nationalNumber\":7050337381}</p>";

        boolean result = FormMessagePreprocessor.containsPhoneNumberJson(formMessage);

        assertFalse("Should not detect phone number JSON with non-zero countryCode", result);
    }

    @Test
    public void testContainsPhoneNumberJson_WithoutPhoneNumberJson_ReturnsFalse() {
        String formMessage = "<p>This is a regular message</p>";
        
        boolean result = FormMessagePreprocessor.containsPhoneNumberJson(formMessage);
        
        assertFalse("Should not detect phone number JSON in regular message", result);
    }

    @Test
    public void testContainsPhoneNumberJson_WithNullInput_ReturnsFalse() {
        boolean result = FormMessagePreprocessor.containsPhoneNumberJson(null);
        
        assertFalse("Should return false for null input", result);
    }

    @Test
    public void testPreprocessFormMessage_WithComplexHtml_PreservesStructure() {
        String formMessage = "<div><p align=\"left\">Contact details:</p>" +
                           "<ul><li>Phone: {\"type\":\"com.krista.net.model.fieldschemas.PhoneNumber\",\"countryCode\":0,\"nationalNumber\":7050337381}</li>" +
                           "<li>Email: test@example.com</li></ul></div>";
        
        String result = FormMessagePreprocessor.preprocessFormMessage(formMessage);
        
        String expected = "<div><p align=\"left\">Contact details:</p>" +
                        "<ul><li>Phone: 7050337381</li>" +
                        "<li>Email: test@example.com</li></ul></div>";
        
        assertEquals("Complex HTML structure should be preserved", expected, result);
    }
}
