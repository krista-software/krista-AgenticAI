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

/**
 * Utility class containing translatable constants for user-facing messages.
 * This class provides a centralized location for all hardcoded English messages
 * that need to be translated for users.
 */
public class TranslatableConstants {

    // Common user-facing messages
    public static final String NO_INFORMATION_AVAILABLE = "No information available.";
    public static final String PLEASE_TYPE_YES_TO_CONTINUE = "Please type Yes to continue";

    // Error messages for users
    public static final String FIELD_NOT_SUPPORTED = "Field not supported, please switch to form based mode";
    public static final String UNABLE_TO_CONVERT_USER_INPUT = "Unable to convert user input to text due to presence of unsupported field";
    public static final String ERROR_PROCESSING_REQUEST_PREFIX = "Error processing request: ";
    
    private TranslatableConstants() {
        // Utility class - prevent instantiation
    }

    /**
     * Translates a constant message using the provided translation service.
     * 
     * @param constantMessage the constant message to translate
     * @param translationService the translation service to use
     * @param language the target language
     * @return the translated message, or original if translation fails
     */
    public static String translate(String constantMessage, MessageTranslationService translationService, String language) {
        if (translationService == null) {
            return constantMessage;
        }
        return translationService.translateMessage(constantMessage, language);
    }




}
