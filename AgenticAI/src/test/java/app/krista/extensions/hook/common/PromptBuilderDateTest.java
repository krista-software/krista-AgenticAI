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

package app.krista.extensions.hook.common;

import app.krista.extensions.hook.common.models.StepMetadata;
import org.junit.Test;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * Test class to verify that PromptBuilder adds current date to all user prompts.
 */
public class PromptBuilderDateTest {

    private StepMetadata createTestMetadata() {
        return new StepMetadata(
            "Test Conversation", "test-execution-id", "Test Workspace",
            "Test Topic", "Test Step", "Test Type", "Test Description"
        );
    }

    private String getExpectedDatePrefix() {
        LocalDate currentDate = LocalDate.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("EEEE, MMMM dd, yyyy", Locale.ENGLISH);
        return "Today is " + currentDate.format(formatter) + ". Use this date only as internal context for interpreting relative date references or evaluating date constraints. Do not mention or include the current date in your response.\n\n";
    }

    @Test
    public void testCreatePromptForInformAPersonStep_HasDatePrefix() {
        Map<String, Object> fieldValueMap = new HashMap<>();
        fieldValueMap.put("testField", "testValue");
        String formMessage = "Test form message";
        StepMetadata metaData = createTestMetadata();

        String result = PromptBuilder.createPromptForInformAPersonStep(fieldValueMap, formMessage, metaData, "English");

        String expectedPrefix = getExpectedDatePrefix();
        assertTrue("Prompt should start with date prefix", result.startsWith(expectedPrefix));
        assertTrue("Prompt should contain JSON data after date prefix", result.contains("testField"));
    }

    @Test
    public void testCreatePromptForInformingUser_HasDatePrefix() {
        Map<String, Object> fieldValueMap = new HashMap<>();
        fieldValueMap.put("testField", "testValue");
        StepMetadata metaData = createTestMetadata();

        String result = PromptBuilder.createPromptForInformingUser(fieldValueMap, metaData, "English");

        String expectedPrefix = getExpectedDatePrefix();
        assertTrue("Prompt should start with date prefix", result.startsWith(expectedPrefix));
        assertTrue("Prompt should contain JSON data after date prefix", result.contains("testField"));
    }

    @Test
    public void testCreatePromptForEntityExtraction_HasDatePrefix() {
        Map<String, Object> fieldValueMap = new HashMap<>();
        fieldValueMap.put("testField", "testValue");
        String formMessage = "Test form message";
        String userResponse = "Test user response";

        String result = PromptBuilder.createPromptForEntityExtraction(fieldValueMap, formMessage, userResponse, "English");

        String expectedPrefix = getExpectedDatePrefix();
        assertTrue("Prompt should start with date prefix", result.startsWith(expectedPrefix));
        assertTrue("Prompt should contain JSON data after date prefix", result.contains("testField"));
        assertTrue("Prompt should contain user response", result.contains("Test user response"));
    }

    @Test
    public void testCreatePromptForEntireQuestionGeneration_HasDatePrefix() {
        Map<String, Object> fieldValueMap = new HashMap<>();
        fieldValueMap.put("testField", "testValue");
        String formMessage = "Test form message";
        StepMetadata metaData = createTestMetadata();

        String result = PromptBuilder.createPromptForEntireQuestionGeneration(fieldValueMap, formMessage, metaData, "English");

        String expectedPrefix = getExpectedDatePrefix();
        assertTrue("Prompt should start with date prefix", result.startsWith(expectedPrefix));
        assertTrue("Prompt should contain JSON data after date prefix", result.contains("testField"));
    }

    @Test
    public void testCreatePromptForQuestionGeneration_HasDatePrefix() {
        Map<String, Object> fieldValueMap = new HashMap<>();
        fieldValueMap.put("testField", "testValue");
        String formMessage = "Test form message";
        Map<String, Object> validationErrors = new HashMap<>();
        validationErrors.put("error1", "Test error");
        String userResponse = "Test user response";
        StepMetadata metaData = createTestMetadata();

        String result = PromptBuilder.createPromptForQuestionGeneration(
            fieldValueMap, formMessage, validationErrors, userResponse, metaData, "English");

        String expectedPrefix = getExpectedDatePrefix();
        assertTrue("Prompt should start with date prefix", result.startsWith(expectedPrefix));
        assertTrue("Prompt should contain JSON data after date prefix", result.contains("testField"));
        assertTrue("Prompt should contain validation errors", result.contains("Test error"));
    }

    @Test
    public void testCreatePromptForConfirmation_HasDatePrefix() {
        Map<String, Object> fieldValueMap = new HashMap<>();
        fieldValueMap.put("testField", "testValue");
        String formMessage = "Test form message";
        StepMetadata metaData = createTestMetadata();

        String result = PromptBuilder.createPromptForConfirmation(fieldValueMap, formMessage, metaData, "English");

        String expectedPrefix = getExpectedDatePrefix();
        assertTrue("Prompt should start with date prefix", result.startsWith(expectedPrefix));
        assertTrue("Prompt should contain JSON data after date prefix", result.contains("testField"));
    }

    @Test
    public void testDateFormat_IsCorrect() {
        String expectedPrefix = getExpectedDatePrefix();
        
        // Verify the format matches the expected pattern
        assertTrue("Date prefix should contain 'Today is'", expectedPrefix.contains("Today is"));
        assertTrue("Date prefix should contain context instruction",
            expectedPrefix.contains("Use this date only as internal context for interpreting relative date references or evaluating date constraints. Do not mention or include the current date in your response"));
        
        // Verify it ends with double newline for proper separation
        assertTrue("Date prefix should end with double newline", expectedPrefix.endsWith("\n\n"));
    }
}
