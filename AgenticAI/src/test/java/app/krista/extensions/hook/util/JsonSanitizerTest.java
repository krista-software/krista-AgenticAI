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

package app.krista.extensions.hook.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for JsonSanitizer.
 */
public class JsonSanitizerTest {

    @Test
    void testSanitize_UnescapedQuotesInString() {
        // Given: JSON with unescaped quotes inside string value
        String input = "{\"query\": \"What did \"John\" say in the meeting?\"}";
        
        // When: Sanitize the JSON
        String result = JsonSanitizer.sanitize(input);
        
        // Then: Quotes inside string should be escaped
        assertEquals("{\"query\": \"What did \\\"John\\\" say in the meeting?\"}", result);
    }

    @Test
    void testSanitize_MultipleUnescapedQuotes() {
        // Given: JSON with multiple unescaped quotes
        String input = "{\"query\": \"Search for \"AI Solutions\" and \"Machine Learning\"\"}";
        
        // When: Sanitize the JSON
        String result = JsonSanitizer.sanitize(input);
        
        // Then: All unescaped quotes should be escaped
        assertEquals("{\"query\": \"Search for \\\"AI Solutions\\\" and \\\"Machine Learning\\\"\"}", result);
    }

    @Test
    void testSanitize_NoUnescapedQuotes() {
        // Given: Valid JSON with no unescaped quotes
        String input = "{\"query\": \"What did John say?\", \"count\": 5}";
        
        // When: Sanitize the JSON
        String result = JsonSanitizer.sanitize(input);
        
        // Then: Should remain unchanged
        assertEquals(input, result);
    }

    @Test
    void testSanitize_EscapedQuotesPreserved() {
        // Given: JSON with already-escaped quotes
        String input = "{\"query\": \"What did \\\"John\\\" say?\"}";
        
        // When: Sanitize the JSON
        String result = JsonSanitizer.sanitize(input);
        
        // Then: Already-escaped quotes should remain escaped
        assertEquals(input, result);
    }

    @Test
    void testSanitize_NullInput() {
        // Given: null input
        String input = null;
        
        // When: Sanitize
        String result = JsonSanitizer.sanitize(input);
        
        // Then: Should return null
        assertNull(result);
    }

    @Test
    void testSanitize_EmptyInput() {
        // Given: empty string
        String input = "";
        
        // When: Sanitize
        String result = JsonSanitizer.sanitize(input);
        
        // Then: Should return empty string
        assertEquals("", result);
    }

    @Test
    void testSanitize_NestedObjects() {
        // Given: Nested JSON with unescaped quotes
        String input = "{\"outer\": {\"inner\": \"Value with \"quotes\"\"}}";
        
        // When: Sanitize
        String result = JsonSanitizer.sanitize(input);
        
        // Then: Inner quotes should be escaped
        assertEquals("{\"outer\": {\"inner\": \"Value with \\\"quotes\\\"\"}}", result);
    }

    @Test
    void testWasSanitized_TrueWhenChanged() {
        // Given: JSON that needs sanitization
        String original = "{\"query\": \"With \"quotes\"\"}";
        String sanitized = "{\"query\": \"With \\\"quotes\\\"\"}";
        
        // When: Check if sanitized
        boolean result = JsonSanitizer.wasSanitized(original, sanitized);
        
        // Then: Should return true
        assertTrue(result);
    }

    @Test
    void testWasSanitized_FalseWhenUnchanged() {
        // Given: JSON that doesn't need sanitization
        String original = "{\"query\": \"No quotes\"}";
        String sanitized = "{\"query\": \"No quotes\"}";
        
        // When: Check if sanitized
        boolean result = JsonSanitizer.wasSanitized(original, sanitized);
        
        // Then: Should return false
        assertFalse(result);
    }

    @Test
    void testSanitize_RealWorldExample() {
        // Given: Real-world example from the error
        String input = "{\"invokerId\": \"INV_07e36a19-fe0d-4050-b150-83e6fe83c6ea\", \"query\": \"From the KEU_Internal docset: In the most recent \\\"AI Solutions\\\" meeting, what did John say?\"}";

        // When: Sanitize
        String result = JsonSanitizer.sanitize(input);

        // Then: Should properly escape the quotes
        assertTrue(result.contains("\\\"AI Solutions\\\""));
        assertFalse(result.contains("\"AI Solutions\"")); // Unescaped should be gone
    }

    // --- extractJsonObject tests ---

    @Test
    void testExtractJsonObject_TextBeforeJson() {

        String input = "Here is the response:\n{\"sessionId\": \"123\", \"responseType\": \"COMPLETE\"}";
        assertEquals("{\"sessionId\": \"123\", \"responseType\": \"COMPLETE\"}", JsonSanitizer.extractJsonObject(input));
    }

    @Test
    void testExtractJsonObject_TextAfterJson() {

        String input = "{\"sessionId\": \"123\"}\nHope this helps!";
        assertEquals("{\"sessionId\": \"123\"}", JsonSanitizer.extractJsonObject(input));
    }

    @Test
    void testExtractJsonObject_TextBothSides() {

        String input = "Sure!\n{\"sessionId\": \"123\"}\nLet me know.";
        assertEquals("{\"sessionId\": \"123\"}", JsonSanitizer.extractJsonObject(input));
    }

    @Test
    void testExtractJsonObject_CleanJson() {

        String input = "{\"key\": \"value\"}";
        assertEquals("{\"key\": \"value\"}", JsonSanitizer.extractJsonObject(input));
    }

    @Test
    void testExtractJsonObject_NestedBraces() {

        String input = "Result: {\"outer\": {\"inner\": \"val\"}} done";
        assertEquals("{\"outer\": {\"inner\": \"val\"}}", JsonSanitizer.extractJsonObject(input));
    }

    @Test
    void testExtractJsonObject_NoBraces() {

        String input = "no json here";
        assertEquals(input, JsonSanitizer.extractJsonObject(input));
    }

    @Test
    void testExtractJsonObject_NullInput() {

        assertNull(JsonSanitizer.extractJsonObject(null));
    }

    @Test
    void testExtractJsonObject_EmptyInput() {

        assertEquals("", JsonSanitizer.extractJsonObject(""));
    }

    @Test
    void testExtractJsonObject_OnlyOpenBrace() {

        String input = "{ no closing";
        assertEquals(input, JsonSanitizer.extractJsonObject(input));
    }

    @Test
    void testExtractJsonObject_BracesInWrongOrder() {

        String input = "} before {";
        assertEquals(input, JsonSanitizer.extractJsonObject(input));
    }

    @Test
    @DisplayName("extractJsonObject strips markdown code fence wrapping JSON")
    void testExtractJsonObject_MarkdownCodeFence() {

        String input = "```json\n{\"sessionId\":\"123\",\"responseType\":\"COMPLETE\",\"finalResponse\":\"Done\"}\n```";
        assertEquals("{\"sessionId\":\"123\",\"responseType\":\"COMPLETE\",\"finalResponse\":\"Done\"}", JsonSanitizer.extractJsonObject(input));
    }

    @Test
    @DisplayName("extractJsonObject strips preamble text and markdown code fence wrapping JSON")
    void testExtractJsonObject_PreambleWithMarkdownCodeFence() {

        String input = "Here is my response:\n```json\n{\"sessionId\":\"123\",\"responseType\":\"COMPLETE\",\"finalResponse\":\"Done\"}\n```";
        assertEquals("{\"sessionId\":\"123\",\"responseType\":\"COMPLETE\",\"finalResponse\":\"Done\"}", JsonSanitizer.extractJsonObject(input));
    }

    @Test
    @DisplayName("extractJsonObject strips markdown code fence without language tag")
    void testExtractJsonObject_MarkdownCodeFenceNoLanguageTag() {

        String input = "```\n{\"key\":\"value\"}\n```";
        assertEquals("{\"key\":\"value\"}", JsonSanitizer.extractJsonObject(input));
    }

    @Test
    @DisplayName("extractJsonObject strips markdown code fence with trailing commentary")
    void testExtractJsonObject_MarkdownCodeFenceWithTrailingText() {

        String input = "```json\n{\"sessionId\":\"456\"}\n```\nLet me know if you need anything else!";
        assertEquals("{\"sessionId\":\"456\"}", JsonSanitizer.extractJsonObject(input));
    }
}
