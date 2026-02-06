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
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Utility class for building prompts in standardized JSON format with consistent key names:
 * FreeForm, MessageTemplate, UserInput, and ValidationErrors.
 */
@SuppressWarnings("unused")
public class PromptBuilder {

    /**
     * Gets the current date in the required format for prompts.
     * @return formatted date string like "Thursday, July 03, 2025"
     */
    private static String getCurrentDateForPrompt() {
        LocalDate currentDate = LocalDate.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("EEEE, MMMM dd, yyyy", Locale.ENGLISH);
        return currentDate.format(formatter);
    }

    /**
     * Adds the current date prefix to a prompt.
     * @param prompt the original prompt
     * @return prompt with date prefix
     */
    private static String addDatePrefix(String prompt) {
        String datePrefix = "Today is " + getCurrentDateForPrompt() + ". Use this date as context for your response.\n\n";
        return datePrefix + prompt;
    }

    /**
     * Replaces {TARGET_LANGUAGE} placeholders in the prompt with the actual target language.
     * @param prompt the prompt with placeholders
     * @param targetLanguage the target language to replace placeholders with
     * @return prompt with language placeholders replaced
     */
    private static String replaceLanguagePlaceholders(String prompt, String targetLanguage) {
        return prompt.replace("{TARGET_LANGUAGE}", targetLanguage);
    }

    /**
     * Converts a map to JSON string with pretty printing.
     * @param jsonMap the map to convert
     * @return JSON string representation
     */
    private static String convertMapToString(Map<String, Object> jsonMap) {
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            objectMapper.enable(SerializationFeature.INDENT_OUTPUT); // for pretty-printing
            return objectMapper.writeValueAsString(jsonMap);
        } catch (Exception e) {
            throw new RuntimeException("Failed to convert map to string", e);
        }
    }

    /**
     * Creates a prompt for informing a person with FreeForm data and message template.
     * @param fieldValueMap the field value map (FreeForm data)
     * @param formMessage the form message template
     * @param metaData the step metadata
     * @param targetLanguage the target language for the response
     * @return JSON formatted prompt string with date prefix
     */
    public static String createPromptForInformAPersonStep(Map<String, Object> fieldValueMap, String formMessage, StepMetadata metaData, String targetLanguage) {
        Map<String, Object> promptJson = new HashMap<>();
        promptJson.put("FreeForm", fieldValueMap);
        promptJson.put("MessageTemplate", formMessage);
        promptJson.put("TargetLanguage", targetLanguage);
        String prompt = convertMapToString(promptJson);
        String promptWithDate = addDatePrefix(prompt);
        String promptWithLanguage = replaceLanguagePlaceholders(promptWithDate, targetLanguage);
        System.out.println("Generated InformAPersonStep prompt: " + promptWithLanguage);
        return promptWithLanguage;
    }

    /**
     * Creates a prompt for informing user with only FreeForm data.
     * @param fieldValueMap the field value map (FreeForm data)
     * @param metaData the step metadata
     * @param targetLanguage the target language for the response
     * @return JSON formatted prompt string with date prefix
     */
    public static String createPromptForInformingUser(Map<String, Object> fieldValueMap, StepMetadata metaData, String targetLanguage) {
        Map<String, Object> promptJson = new HashMap<>();
        promptJson.put("FreeForm", fieldValueMap);
        promptJson.put("TargetLanguage", targetLanguage);
        String prompt = convertMapToString(promptJson);
        String promptWithDate = addDatePrefix(prompt);
        String promptWithLanguage = replaceLanguagePlaceholders(promptWithDate, targetLanguage);
        System.out.println("Generated InformingUser prompt: " + promptWithLanguage);
        return promptWithLanguage;
    }

    /**
     * Creates a prompt for entity extraction with FreeForm data, message template, and user input.
     * @param fieldValueMap the field value map (FreeForm data)
     * @param formMessage the form message template (optional, can be null)
     * @param userResponse the user input/response
     * @param targetLanguage the target language for the response
     * @return JSON formatted prompt string with date prefix
     */
    public static String createPromptForEntityExtraction(Map<String, Object> fieldValueMap, String formMessage, String userResponse, String targetLanguage) {
        Map<String, Object> promptJson = new HashMap<>();
        promptJson.put("FreeForm", fieldValueMap);
        if (formMessage != null) {
            promptJson.put("MessageTemplate", formMessage);
        }
        promptJson.put("UserInput", userResponse);
        promptJson.put("TargetLanguage", targetLanguage);
        String prompt = convertMapToString(promptJson);
        String promptWithDate = addDatePrefix(prompt);
        String promptWithLanguage = replaceLanguagePlaceholders(promptWithDate, targetLanguage);
        System.out.println("Generated EntityExtraction prompt: " + promptWithLanguage);
        return promptWithLanguage;
    }

    /**
     * Creates a prompt for asking user questions with FreeForm data, message template, and validation errors.
     * Used with AskUser.txt prompt.
     * @param fieldValueMap the field value map (FreeForm data)
     * @param formMessage the form message template
     * @param metaData the step metadata
     * @param targetLanguage the target language for the response
     * @return JSON formatted prompt string with date prefix
     */
    public static String createPromptForEntireQuestionGeneration(
            Map<String, Object> fieldValueMap, String formMessage, StepMetadata metaData, String targetLanguage) {
        Map<String, Object> promptJson = new HashMap<>();
        promptJson.put("FreeForm", fieldValueMap);
        promptJson.put("MessageTemplate", formMessage);
        promptJson.put("TargetLanguage", targetLanguage);
        String prompt = convertMapToString(promptJson);
        String promptWithDate = addDatePrefix(prompt);
        String promptWithLanguage = replaceLanguagePlaceholders(promptWithDate, targetLanguage);
        System.out.println("Generated Entire QuestionGeneration prompt: " + promptWithLanguage);
        return promptWithLanguage;
    }

    /**
     * Creates a prompt for default values confirmation with FreeForm data.
     * Used with DefaultValuesConfirmation.txt prompt.
     * @param fieldValueMap the field value map (CurrentVariablesDefault data)
     * @param targetLanguage the target language for the response
     * @return JSON formatted prompt string with date prefix
     */
    public static String createPromptForDefaultValuesConfirmation(
            Map<String, Object> fieldValueMap, String targetLanguage) {
        Map<String, Object> promptJson = new HashMap<>();
        promptJson.put("FreeForm", fieldValueMap);
        promptJson.put("TargetLanguage", targetLanguage);
        String prompt = convertMapToString(promptJson);
        String promptWithDate = addDatePrefix(prompt);
        String promptWithLanguage = replaceLanguagePlaceholders(promptWithDate, targetLanguage);
        System.out.println("Generated DefaultValuesConfirmation prompt: " + promptWithLanguage);
        return promptWithLanguage;
    }

    /**
     * Creates a prompt for asking user questions with FreeForm data, message template, and validation errors.
     * Used with AskUser.txt prompt.
     * @param fieldValueMap the field value map (FreeForm data)
     * @param formMessage the form message template
     * @param validationErrors the validation errors map (optional, can be null)
     * @param userResponse the user response (optional, can be null)
     * @param metaData the step metadata
     * @param targetLanguage the target language for the response
     * @return JSON formatted prompt string with date prefix
     */
    public static String createPromptForQuestionGeneration(
            Map<String, Object> fieldValueMap, String formMessage, Map<String, Object> validationErrors, String userResponse, StepMetadata metaData, String targetLanguage) {
        Map<String, Object> promptJson = new HashMap<>();
        promptJson.put("FreeForm", fieldValueMap);
        promptJson.put("MessageTemplate", formMessage);
        promptJson.put("TargetLanguage", targetLanguage);
        if (validationErrors != null && !validationErrors.isEmpty()) {
            promptJson.put("ValidationErrors", validationErrors);
        }
        if (userResponse != null && !userResponse.isEmpty()) {
            promptJson.put("UserInput", userResponse);
        }
        String prompt = convertMapToString(promptJson);
        String promptWithDate = addDatePrefix(prompt);
        String promptWithLanguage = replaceLanguagePlaceholders(promptWithDate, targetLanguage);
        System.out.println("Generated QuestionGeneration prompt: " + promptWithLanguage);
        return promptWithLanguage;
    }

    /**
     * Creates a prompt for confirmation details with FreeForm data and message template (no validation errors).
     * Used with ConfirmDetails.txt prompt.
     * @param fieldValueMap the field value map (FreeForm data)
     * @param formMessage the form message template
     * @param metaData the step metadata
     * @param targetLanguage the target language for the response
     * @return JSON formatted prompt string with date prefix
     */
    public static String createPromptForConfirmation(
            Map<String, Object> fieldValueMap, String formMessage, StepMetadata metaData, String targetLanguage) {
        Map<String, Object> promptJson = new HashMap<>();
        promptJson.put("FreeForm", fieldValueMap);
        promptJson.put("MessageTemplate", formMessage);
        promptJson.put("TargetLanguage", targetLanguage);
        String prompt = convertMapToString(promptJson);
        String promptWithDate = addDatePrefix(prompt);
        String promptWithLanguage = replaceLanguagePlaceholders(promptWithDate, targetLanguage);
        System.out.println("Generated Confirmation prompt: " + promptWithLanguage);
        return promptWithLanguage;
    }

    /**
     * Creates a prompt for message translation.
     * Used with TranslateMessage.txt prompt.
     * @param englishMessage the English message to translate
     * @param targetLanguage the target language for translation
     * @return JSON formatted prompt string with date prefix
     */
    public static String createPromptForTranslation(String englishMessage, String targetLanguage) {
        Map<String, Object> promptJson = new HashMap<>();
        promptJson.put("EnglishMessage", englishMessage);
        promptJson.put("TargetLanguage", targetLanguage);
        String prompt = convertMapToString(promptJson);
        String promptWithDate = addDatePrefix(prompt);
        String promptWithLanguage = replaceLanguagePlaceholders(promptWithDate, targetLanguage);
        System.out.println("Generated Translation prompt: " + promptWithLanguage);
        return promptWithLanguage;
    }

    /**
     * Creates a prompt for intent classification.
     * Used with IntentClassification.txt prompt.
     * @param userMessage the user's input message
     * @param conversationsMetaData list of available conversations metadata
     * @return JSON formatted prompt string
     */
    public static String createPromptForIntentClassification(String userMessage, List<Map<String, Object>> conversationsMetaData) {
        Map<String, Object> promptJson = new HashMap<>();
        promptJson.put("UserInput", userMessage);
        promptJson.put("AvailableConversations", conversationsMetaData != null ? conversationsMetaData : new ArrayList<>());
        String prompt = convertMapToString(promptJson);
        System.out.println("Generated IntentClassification prompt: " + prompt);
        return prompt;
    }
}
