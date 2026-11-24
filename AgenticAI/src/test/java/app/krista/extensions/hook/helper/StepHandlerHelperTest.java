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

import org.junit.Test;
import static org.junit.Assert.*;

public class StepHandlerHelperTest {

    @Test
    public void testContainsKristaFieldVariables_WithKristaField_ReturnsTrue() {
        // Test with krista-field tag
        String messageWithKristaField = "Hello <krista-field ref=\"vars.inScope(\\\"current\\\").get(\\\"Name\\\")\" value=\"John\" name=\"Name\" fieldtype=\"com.krista.fields.Text\" attributes=\"{}\"></krista-field>!";
        assertTrue("Should detect krista-field variables", StepHandlerHelper.containsKristaFieldVariables(messageWithKristaField));
    }

    @Test
    public void testContainsKristaFieldVariables_WithoutKristaField_ReturnsFalse() {
        // Test with plain text
        String plainTextMessage = "This is just plain text without any variables.";
        assertFalse("Should not detect krista-field variables in plain text", StepHandlerHelper.containsKristaFieldVariables(plainTextMessage));
    }

    @Test
    public void testContainsKristaFieldVariables_WithNullMessage_ReturnsFalse() {
        // Test with null message
        assertFalse("Should return false for null message", StepHandlerHelper.containsKristaFieldVariables(null));
    }

    @Test
    public void testContainsKristaFieldVariables_WithEmptyMessage_ReturnsFalse() {
        // Test with empty message
        assertFalse("Should return false for empty message", StepHandlerHelper.containsKristaFieldVariables(""));
        assertFalse("Should return false for whitespace-only message", StepHandlerHelper.containsKristaFieldVariables("   "));
    }

    @Test
    public void testContainsKristaFieldVariables_CaseInsensitive_ReturnsTrue() {
        // Test case insensitivity
        String messageWithUpperCase = "Hello <KRISTA-FIELD ref=\"test\">value</KRISTA-FIELD>!";
        assertTrue("Should detect krista-field variables case-insensitively", StepHandlerHelper.containsKristaFieldVariables(messageWithUpperCase));
    }

    @Test
    public void testContainsKristaFieldVariables_WithMultipleKristaFields_ReturnsTrue() {
        // Test with multiple krista-field tags
        String messageWithMultipleFields = "Name: <krista-field ref=\"name\">John</krista-field>, Age: <krista-field ref=\"age\">25</krista-field>";
        assertTrue("Should detect multiple krista-field variables", StepHandlerHelper.containsKristaFieldVariables(messageWithMultipleFields));
    }

    @Test
    public void testContainsKristaFieldVariables_WithOtherTags_ReturnsFalse() {
        // Test with other HTML-like tags but no krista-field
        String messageWithOtherTags = "This has <div>some</div> <span>other</span> tags but no krista-field.";
        assertFalse("Should not detect non-krista-field tags", StepHandlerHelper.containsKristaFieldVariables(messageWithOtherTags));
    }

    @Test
    public void testContainsKristaFieldVariables_WithPartialMatch_ReturnsFalse() {
        // Test with partial matches that shouldn't trigger
        String messageWithPartialMatch = "This contains krista but not the full krista-field tag.";
        assertFalse("Should not detect partial matches", StepHandlerHelper.containsKristaFieldVariables(messageWithPartialMatch));
    }

    @Test
    public void testContainsJsonFieldData_WithPhoneNumberJson_ReturnsTrue() {
        // Test with phone number JSON data
        String phoneNumberJson = "<p align=\"left\">{\"type\":\"com.krista.net.model.fieldschemas.PhoneNumber\",\"countryCode\":0,\"nationalNumber\":7050337381}</p>";
        assertTrue("Should detect phone number JSON field data", StepHandlerHelper.containsJsonFieldData(phoneNumberJson));
    }

    @Test
    public void testContainsJsonFieldData_WithDateRangeJson_ReturnsTrue() {
        // Test with date range JSON data
        String dateRangeJson = "{\"type\":\"com.krista.net.model.fieldschemas.DateRange\",\"from\":1746374556566,\"to\":1746550956566}";
        assertTrue("Should detect date range JSON field data", StepHandlerHelper.containsJsonFieldData(dateRangeJson));
    }

    @Test
    public void testContainsJsonFieldData_WithPlainText_ReturnsFalse() {
        // Test with plain text
        String plainText = "This is just plain text without any JSON data.";
        assertFalse("Should not detect JSON field data in plain text", StepHandlerHelper.containsJsonFieldData(plainText));
    }

    @Test
    public void testContainsJsonFieldData_WithNullMessage_ReturnsFalse() {
        // Test with null message
        assertFalse("Should return false for null message", StepHandlerHelper.containsJsonFieldData(null));
    }

    @Test
    public void testContainsJsonFieldData_WithEmptyMessage_ReturnsFalse() {
        // Test with empty message
        assertFalse("Should return false for empty message", StepHandlerHelper.containsJsonFieldData(""));
        assertFalse("Should return false for whitespace-only message", StepHandlerHelper.containsJsonFieldData("   "));
    }

    @Test
    public void testContainsJsonFieldData_WithOtherJsonData_ReturnsFalse() {
        // Test with other JSON data that doesn't match the pattern
        String otherJson = "{\"name\":\"John\",\"age\":30,\"type\":\"person\"}";
        assertFalse("Should not detect non-field JSON data", StepHandlerHelper.containsJsonFieldData(otherJson));
    }

    @Test
    public void testContainsJsonFieldData_WithMultipleFieldTypes_ReturnsTrue() {
        // Test with multiple field types in one message
        String multipleFields = "Phone: {\"type\":\"com.krista.net.model.fieldschemas.PhoneNumber\",\"nationalNumber\":123} and Date: {\"type\":\"com.krista.net.model.fieldschemas.DateRange\",\"from\":123}";
        assertTrue("Should detect multiple JSON field data types", StepHandlerHelper.containsJsonFieldData(multipleFields));
    }
}
