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

package app.krista.extensions.hook.llm;

import app.krista.extension.executor.Invoker;
import app.krista.extensions.hook.Constants;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Before;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * Test class specifically for testing JSON code block handling in LLM responses.
 * This tests the preprocessing functionality that handles JSON wrapped in markdown code blocks.
 */
public class JsonCodeBlockHandlingTest {

    private ObjectMapper objectMapper;
    private TestableJsonProcessor processor;

    @Before
    public void setUp() {
        objectMapper = new ObjectMapper();
        processor = new TestableJsonProcessor();
    }

    @Test
    public void testPreprocessJsonString_WithJsonCodeBlocks_ExtractsCorrectly() {
        // Arrange
        String jsonWithCodeBlocks = "```json\n{\"name\": \"John\", \"age\": 30}\n```";
        
        // Act
        String result = processor.preprocessJsonString(jsonWithCodeBlocks);
        
        // Assert
        assertEquals("Should extract JSON from code blocks", "{\"name\": \"John\", \"age\": 30}", result);
    }

    @Test
    public void testPreprocessJsonString_WithUppercaseJsonCodeBlocks_ExtractsCorrectly() {
        // Arrange
        String jsonWithCodeBlocks = "```JSON\n{\"email\": \"test@example.com\"}\n```";
        
        // Act
        String result = processor.preprocessJsonString(jsonWithCodeBlocks);
        
        // Assert
        assertEquals("Should extract JSON from uppercase code blocks", "{\"email\": \"test@example.com\"}", result);
    }

    @Test
    public void testPreprocessJsonString_WithPlainCodeBlocks_ExtractsCorrectly() {
        // Arrange
        String jsonWithCodeBlocks = "```\n{\"phone\": \"1234567890\"}\n```";
        
        // Act
        String result = processor.preprocessJsonString(jsonWithCodeBlocks);
        
        // Assert
        assertEquals("Should extract JSON from plain code blocks", "{\"phone\": \"1234567890\"}", result);
    }

    @Test
    public void testPreprocessJsonString_WithoutCodeBlocks_ReturnsOriginal() {
        // Arrange
        String plainJson = "{\"city\": \"New York\"}";
        
        // Act
        String result = processor.preprocessJsonString(plainJson);
        
        // Assert
        assertEquals("Should return original JSON when no code blocks", plainJson, result);
    }

    @Test
    public void testPreprocessJsonString_WithMultilineJson_ExtractsCorrectly() {
        // Arrange
        String multilineJson = "```json\n{\n  \"name\": \"John Doe\",\n  \"age\": 25,\n  \"address\": \"123 Main St\"\n}\n```";
        String expectedJson = "{\n  \"name\": \"John Doe\",\n  \"age\": 25,\n  \"address\": \"123 Main St\"\n}";
        
        // Act
        String result = processor.preprocessJsonString(multilineJson);
        
        // Assert
        assertEquals("Should extract multiline JSON correctly", expectedJson, result);
    }

    @Test
    public void testPreprocessJsonString_WithExtraTextAround_ExtractsCorrectly() {
        // Arrange
        String responseWithExtraText = "Here is the data:\n\n```json\n{\"status\": \"active\"}\n```\n\nDone.";
        
        // Act
        String result = processor.preprocessJsonString(responseWithExtraText);
        
        // Assert
        assertEquals("Should extract JSON ignoring extra text", "{\"status\": \"active\"}", result);
    }

    @Test
    public void testPreprocessJsonString_WithEmptyCodeBlocks_ReturnsEmpty() {
        // Arrange
        String emptyCodeBlocks = "```json\n\n```";
        
        // Act
        String result = processor.preprocessJsonString(emptyCodeBlocks);
        
        // Assert
        assertEquals("Should return empty string for empty code blocks", "", result);
    }

    @Test
    public void testPreprocessJsonString_WithMalformedCodeBlocks_ReturnsOriginal() {
        // Arrange
        String malformedCodeBlocks = "```json\n{\"name\": \"John\""; // Missing closing ```
        
        // Act
        String result = processor.preprocessJsonString(malformedCodeBlocks);
        
        // Assert
        assertEquals("Should return original string for malformed code blocks", malformedCodeBlocks, result);
    }

    @Test
    public void testConvertStringToMap_WithJsonCodeBlocks_ParsesCorrectly() throws Exception {
        // Arrange
        String jsonWithCodeBlocks = "```json\n{\"variableMapping\": {\"name\": \"John\"}, \"move_to_next_step\": true}\n```";
        
        // Act
        Map<String, Object> result = processor.convertStringToMap(jsonWithCodeBlocks);
        
        // Assert
        assertNotNull("Result should not be null", result);
        assertTrue("Should contain variableMapping", result.containsKey("variableMapping"));
        assertTrue("Should contain move_to_next_step", result.containsKey("move_to_next_step"));
        
        @SuppressWarnings("unchecked")
        Map<String, Object> variableMapping = (Map<String, Object>) result.get("variableMapping");
        assertEquals("Should extract name correctly", "John", variableMapping.get("name"));
        assertEquals("Should extract move_to_next_step correctly", true, result.get("move_to_next_step"));
    }

    @Test
    public void testConvertStringToMap_WithoutCodeBlocks_ParsesCorrectly() throws Exception {
        // Arrange
        String plainJson = "{\"response\": \"Hello, world!\"}";
        
        // Act
        Map<String, Object> result = processor.convertStringToMap(plainJson);
        
        // Assert
        assertNotNull("Result should not be null", result);
        assertEquals("Should extract response correctly", "Hello, world!", result.get("response"));
    }

    @Test
    public void testConvertStringToMap_WithMalformedJson_ThrowsException() {
        // Arrange
        String malformedJson = "```json\n{\"name\": \"John\", \"age\": 30"; // Missing closing brace and ```
        
        // Act & Assert
        try {
            processor.convertStringToMap(malformedJson);
            fail("Should throw RuntimeException for malformed JSON");
        } catch (RuntimeException e) {
            assertTrue("Should contain JSON parsing error message", e.getMessage().contains("Failed to parse LLM response JSON"));
        }
    }

    /**
     * Test helper class that exposes the private methods for testing
     */
    private class TestableJsonProcessor {
        private final ObjectMapper objectMapper = new ObjectMapper();

        public String preprocessJsonString(String jsonString) {
            if (jsonString == null || jsonString.trim().isEmpty()) {
                return jsonString;
            }

            String input = jsonString.trim();

            // Regex pattern to match markdown code blocks with optional language specifier
            Pattern codeBlockPattern = Pattern.compile(
                "```(?:json|JSON)?\\s*\\r?\\n?(.*?)\\r?\\n?\\s*```",
                Pattern.DOTALL
            );

            Matcher matcher = codeBlockPattern.matcher(input);

            if (matcher.find()) {
                String extractedContent = matcher.group(1).trim();
                return extractedContent;
            }

            // No code block markers found, return original string
            return jsonString;
        }

        public Map<String, Object> convertStringToMap(String jsonString) {
            try {
                // Preprocess the string to handle JSON wrapped in markdown code blocks
                String cleanedJson = preprocessJsonString(jsonString);
                return objectMapper.readValue(cleanedJson, new TypeReference<>() {
                });
            } catch (Exception e) {
                throw new RuntimeException("Failed to parse LLM response JSON: " + jsonString, e);
            }
        }
    }
}
