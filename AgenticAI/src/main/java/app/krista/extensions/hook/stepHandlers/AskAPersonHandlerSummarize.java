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

package app.krista.extensions.hook.stepHandlers;

import app.krista.extension.executor.ExtensionResponse;
import app.krista.extension.executor.Invoker;
import app.krista.extension.executor.impl.AgentAction;
import app.krista.extensions.hook.llm.LLMServiceWithFallback;
import app.krista.extensions.hook.common.*;
import app.krista.extensions.hook.common.models.StepMetadata;
import app.krista.extensions.hook.factory.ExtensionResponseFactory;
import app.krista.extensions.hook.helper.StepHandlerHelper;
import app.krista.extensions.hook.translation.MessageTranslationService;
import app.krista.extensions.hook.translation.TranslatableConstants;
import app.krista.model.base.FreeForm;

import java.util.Map;
import java.util.Objects;

public class AskAPersonHandlerSummarize implements StepExecutionHandler {
    private final LLMServiceWithFallback llmService;
    private final String askUserPrompt;
    private final String defaultValuesConfirmationPrompt;
    private final MessageTranslationService translationService;

    public AskAPersonHandlerSummarize(Invoker invoker) throws Exception {
        this.llmService = new LLMServiceWithFallback(invoker);
        this.askUserPrompt = FileReader.readFileAsStringFromResource("prompts/AskUser.txt");
        this.defaultValuesConfirmationPrompt = FileReader.readFileAsStringFromResource("prompts/DefaultValuesConfirmation.txt");
        this.translationService = new MessageTranslationService(llmService);
    }

    @Override
    public ExtensionResponse processRequest(AgentAction agentAction, FreeForm currentVariables, String formMessage, String userMessage, StepMetadata metaData, FreeForm currentVariablesDefault, String language) throws Exception {
        if (agentAction != AgentAction.SUMMARIZE_ASK) {
            throw new UnsupportedOperationException("SummarizeAskHandler only supports SUMMARIZE_ASK action");
        }

        return handleSummarizeAsk(currentVariables, formMessage, metaData, currentVariablesDefault, language);
    }

    private ExtensionResponse handleSummarizeAsk(FreeForm currentVariables, String formMessage, StepMetadata metaData, FreeForm currentVariablesDefault, String language) throws Exception {
        try {
            // Check if currentVariables is empty (no fields defined) AND formMessage has no krista-field variables AND no JSON field data
            boolean isCurrentVariablesEmpty = StepHandlerHelper.isCurrentVariablesEmpty(currentVariables);
            boolean hasKristaFieldVariables = StepHandlerHelper.containsKristaFieldVariables(formMessage);
            boolean hasJsonFieldData = StepHandlerHelper.containsJsonFieldData(formMessage);

            if (isCurrentVariablesEmpty && !hasKristaFieldVariables && !hasJsonFieldData) {
                // If no variables, no krista-field variables, and no JSON field data, return formMessage directly without LLM call
                if (Objects.nonNull(formMessage)) {
                    String messageWithProceed = appendProceedMessage(formMessage, language);
                    return ExtensionResponseFactory.createAskUserResponse(currentVariables, messageWithProceed);
                } else {
                    // If no formMessage either, return a default response
                    String defaultMessage = TranslatableConstants.translate(TranslatableConstants.NO_INFORMATION_AVAILABLE, translationService, language);
                    return ExtensionResponseFactory.createInformUserResponse(defaultMessage);
                }
            }

            Map<String, Object> inputFieldValueMap = FreeFormHelper.convertFreeFormToFieldValueMap(currentVariables);

            // Check if the field value map is empty (no fields defined)
            if (inputFieldValueMap.isEmpty()) {
                // If no variables, return formMessage directly without LLM call
                if (Objects.nonNull(formMessage)) {
                    String messageWithProceed = appendProceedMessage(formMessage, language);
                    return ExtensionResponseFactory.createAskUserResponse(currentVariables, messageWithProceed);
                } else {
                    // If no formMessage either, return a default response
                    String defaultMessage = TranslatableConstants.translate(TranslatableConstants.NO_INFORMATION_AVAILABLE, translationService, language);
                    return ExtensionResponseFactory.createInformUserResponse(defaultMessage);
                }
            }

            // For SUMMARIZE_ASK, choose between AskUser and DefaultValuesConfirmation prompts
            // convertFormResponseToText has been moved to InformAPersonHandlerSummarize
            if (Objects.nonNull(formMessage)) {
                // Check if CurrentVariablesDefault has at least one value
                boolean hasDefaultValues = FreeFormHelper.hasAtLeastOneValue(currentVariablesDefault, false);

                if (hasDefaultValues) {
                    // Use DefaultValuesConfirmation prompt with CurrentVariablesDefault
                    return convertDefaultValuesToConfirmation(currentVariablesDefault, language);
                } else {
                    // Use existing AskUser prompt with currentVariables
                    return convertEntireFormToQuestion(inputFieldValueMap, currentVariables, formMessage, metaData, language);
                }
            } else {
                // When formMessage is null, this should be handled by InformAPersonHandlerSummarize
                // Return a default response for now
                String defaultMessage = TranslatableConstants.translate(TranslatableConstants.NO_INFORMATION_AVAILABLE, translationService, language);
                return ExtensionResponseFactory.createInformUserResponse(defaultMessage);
            }
        } catch (StepHandlerHelper.UnsupportedFieldTypeException e) {
            // Handle unsupported field type from isCurrentVariablesEmpty or convertFreeFormToFieldValueMap
            String errorMessage = TranslatableConstants.translate(TranslatableConstants.FIELD_NOT_SUPPORTED, translationService, language);
            return ExtensionResponseFactory.createAskUserResponse(currentVariables, errorMessage);
        } catch (Exception e) {
            // Re-throw other exceptions
            throw e;
        }
    }

    private ExtensionResponse convertEntireFormToQuestion(Map<String, Object> inputFieldValueMap, FreeForm freeForm, String formMessage, StepMetadata metaData, String language) {
        String askUserResponse = llmService.generateResponse(askUserPrompt,
                PromptBuilder.createPromptForEntireQuestionGeneration(inputFieldValueMap, formMessage, metaData, language));

        return ExtensionResponseFactory.createAskUserResponse(freeForm, askUserResponse);
    }

    private ExtensionResponse convertDefaultValuesToConfirmation(FreeForm currentVariablesDefault, String language) throws Exception {
        // Convert CurrentVariablesDefault to field value map
        Map<String, Object> defaultFieldValueMap = FreeFormHelper.convertFreeFormToFieldValueMap(currentVariablesDefault);

        String defaultValuesResponse = llmService.generateResponse(defaultValuesConfirmationPrompt,
                PromptBuilder.createPromptForDefaultValuesConfirmation(defaultFieldValueMap, language));

        return ExtensionResponseFactory.createAskUserResponse(currentVariablesDefault, defaultValuesResponse);
    }

    /**
     * Appends "please type proceed to continue" message to the form message,
     * handling both HTML and plain text formatting properly.
     */
    private String appendProceedMessage(String formMessage, String language) {
        if (formMessage == null || formMessage.trim().isEmpty()) {
            return TranslatableConstants.translate(TranslatableConstants.PLEASE_TYPE_YES_TO_CONTINUE, translationService, language);
        }

        String trimmedMessage = formMessage.trim();

        // Check if the message contains HTML tags
        if (isHtmlContent(trimmedMessage)) {
            return appendToHtmlContent(trimmedMessage, language);
        } else {
            // Plain text - just append with proper punctuation
            return appendToPlainText(trimmedMessage, language);
        }
    }

    /**
     * Simple check to determine if content contains HTML tags
     */
    private boolean isHtmlContent(String content) {
        return content.contains("<") && content.contains(">");
    }

    /**
     * Appends proceed message to HTML content by adding a paragraph at the end
     */
    private String appendToHtmlContent(String htmlContent, String language) {
        // Combine the content with proceed message and translate the entire thing
        String combinedMessage = htmlContent + "<p><em>" + TranslatableConstants.PLEASE_TYPE_YES_TO_CONTINUE + "</em></p>";
        return translationService.translateMessage(combinedMessage, language);
    }

    /**
     * Appends proceed message to plain text with proper punctuation
     */
    private String appendToPlainText(String plainText, String language) {
        // Check if the text ends with punctuation and create complete message
        char lastChar = plainText.charAt(plainText.length() - 1);
        String combinedMessage;
        if (lastChar == '.' || lastChar == '!' || lastChar == '?') {
            combinedMessage = plainText + " " + TranslatableConstants.PLEASE_TYPE_YES_TO_CONTINUE;
        } else {
            combinedMessage = plainText + ". " + TranslatableConstants.PLEASE_TYPE_YES_TO_CONTINUE;
        }
        // Translate the entire combined message for proper context
        return translationService.translateMessage(combinedMessage, language);
    }

}
