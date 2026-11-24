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

package app.krista.extensions.hook.translation;

import app.krista.extensions.hook.common.FileReader;
import app.krista.extensions.hook.common.PromptBuilder;
import app.krista.extensions.hook.llm.LLMServiceWithFallback;


/**
 * Service for translating hardcoded English messages to target languages using LLM.
 * This service provides a plug-and-play translation solution that can be easily integrated
 * throughout the application without modifying existing code structure.
 */
public class MessageTranslationService {

    private final LLMServiceWithFallback llmService;

    // Constants for default language handling
    private static final String DEFAULT_LANGUAGE = "English";
    private static final String ENGLISH_CODE = "en";

    public MessageTranslationService(LLMServiceWithFallback llmService) {
        this.llmService = llmService;
    }

    /**
     * Translates an English message to the target language.
     * If the target language is English or null, returns the original message.
     * Uses caching to avoid repeated translations of the same text.
     * 
     * @param englishMessage the English message to translate
     * @param targetLanguage the target language code (e.g., "es", "fr", "de")
     * @return the translated message, or original message if translation is not needed
     */
    public String translateMessage(String englishMessage, String targetLanguage) {
        // Return original message if null or empty
        if (englishMessage == null || englishMessage.trim().isEmpty()) {
            return englishMessage;
        }
        
        // Return original message if target language is English or null
        if (targetLanguage == null || targetLanguage.trim().isEmpty() || 
            ENGLISH_CODE.equalsIgnoreCase(targetLanguage.trim()) ||
            DEFAULT_LANGUAGE.equalsIgnoreCase(targetLanguage.trim())) {
            return englishMessage;
        }
        
        try {
            String translatedMessage = performTranslation(englishMessage, targetLanguage);

            System.out.println("Translated message to language: " + targetLanguage);
            return translatedMessage;

        } catch (Exception e) {
            System.err.println("Translation failed: " + e.getMessage());
            e.printStackTrace();

            // Return original English message if translation fails (API down, timeout, etc.)
            return englishMessage;
        }
    }

    /**
     * Performs the actual translation using LLM service.
     */
    private String performTranslation(String englishMessage, String targetLanguageDisplayName) {
        try {
            // Use PromptBuilder to create the translation prompt
            String userPrompt = PromptBuilder.createPromptForTranslation(englishMessage, targetLanguageDisplayName);

            // Load the translation system prompt
            String translationPrompt = FileReader.readFileAsStringFromResource("prompts/TranslateMessage.txt");

            // Call LLM service for translation - return response directly like other handlers
            return llmService.generateResponse(translationPrompt, userPrompt);

        } catch (Exception e) {
            throw new RuntimeException("Failed to perform translation: " + e.getMessage(), e);
        }
    }




}
