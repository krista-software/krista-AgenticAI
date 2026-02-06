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
import app.krista.extensions.hook.common.FileReader;
import app.krista.extensions.hook.common.FreeFormHelper;
import app.krista.extensions.hook.common.PromptBuilder;
import app.krista.extensions.hook.common.models.StepMetadata;
import app.krista.extensions.hook.factory.ExtensionResponseFactory;
import app.krista.extensions.hook.helper.StepHandlerHelper;
import app.krista.extensions.hook.translation.MessageTranslationService;
import app.krista.extensions.hook.translation.TranslatableConstants;
import app.krista.model.base.FreeForm;

import java.util.Map;

public class InformAPersonHandlerSummarize implements StepExecutionHandler {
    private final LLMServiceWithFallback llmService;
    private final String informPrompt;
    private final MessageTranslationService translationService;

    public InformAPersonHandlerSummarize(Invoker invoker) throws Exception {
        this.llmService = new LLMServiceWithFallback(invoker);
        this.informPrompt = FileReader.readFileAsStringFromResource("prompts/InformUser.txt");
        this.translationService = new MessageTranslationService(llmService);
    }

    @Override
    public ExtensionResponse processRequest(AgentAction agentAction, FreeForm currentVariables, String formMessage, String userMessage, StepMetadata metaData, FreeForm currentVariablesDefault, String language) throws StepHandlerHelper.UnsupportedFieldTypeException {
        // This handler only supports SUMMARIZE_INFORM
        if (agentAction != AgentAction.SUMMARIZE_INFORM) {
            throw new UnsupportedOperationException("InformStepExecutionHandler only supports SUMMARIZE_INFORM action");
        }

        // Determine if this is a user response case (stepType != CST_1030)
        boolean isUserResponseCase = !"CST_1030".equals(metaData.getStepType());

        try {
            // Check if currentVariables is empty (no fields defined) AND formMessage has no krista-field variables AND no JSON field data
            boolean isCurrentVariablesEmpty = StepHandlerHelper.isCurrentVariablesEmpty(currentVariables);
            boolean hasKristaFieldVariables = StepHandlerHelper.containsKristaFieldVariables(formMessage);
            boolean hasJsonFieldData = StepHandlerHelper.containsJsonFieldData(formMessage);

            if (isCurrentVariablesEmpty && !hasKristaFieldVariables && !hasJsonFieldData) {
                // If no variables, no krista-field variables, and no JSON field data, return formMessage with translation
                String responseMessage;
                if (formMessage != null) {
                    responseMessage = translationService.translateMessage(formMessage, language);
                } else {
                    responseMessage = TranslatableConstants.translate(TranslatableConstants.NO_INFORMATION_AVAILABLE, translationService, language);
                }
                return ExtensionResponseFactory.createInformUserResponseWithVariables(currentVariables, responseMessage);
            }

            Map<String, Object> inputFieldValueMap = FreeFormHelper.convertFreeFormToFieldValueMap(currentVariables);

            if (isUserResponseCase) {
                // User response case - use convertFormResponseToText
                return convertFormResponseToText(inputFieldValueMap, metaData, language);
            } else {
                // CST_1030 case - use standard inform logic
                return processInformStep(inputFieldValueMap, currentVariables, formMessage, metaData, language);
            }
        } catch (StepHandlerHelper.UnsupportedFieldTypeException e) {
            // Handle unsupported field type from isCurrentVariablesEmpty or convertFreeFormToFieldValueMap
            return handleUnsupportedFieldTypeException(currentVariables, isUserResponseCase, language);
        } catch (Exception e) {
            // Re-throw other exceptions
            throw new RuntimeException(e);
        }
    }

    /**
     * Handles unsupported field type exceptions by returning appropriate error messages.
     * @param currentVariables the current FreeForm variables
     * @param isUserResponseCase true if stepType != CST_1030, false otherwise
     * @param language the target language for translation
     * @return ExtensionResponse with appropriate error message
     */
    private ExtensionResponse handleUnsupportedFieldTypeException(FreeForm currentVariables, boolean isUserResponseCase, String language) {
        if (isUserResponseCase) {
            String errorMessage = TranslatableConstants.translate(TranslatableConstants.UNABLE_TO_CONVERT_USER_INPUT, translationService, language);
            return ExtensionResponseFactory.createInformUserResponse(errorMessage);
        } else {
            // Don't return FreeForm for unsupported fields to avoid serialization issues
            String errorMessage = TranslatableConstants.translate(TranslatableConstants.FIELD_NOT_SUPPORTED, translationService, language);
            return ExtensionResponseFactory.createInformUserResponse(errorMessage);
        }
    }

    /**
     * Processes standard inform step for CST_1030 step types.
     * This method handles the standard inform logic with form message template.
     */
    private ExtensionResponse processInformStep(Map<String, Object> inputFieldValueMap, FreeForm currentVariables, String formMessage, StepMetadata metaData, String language) {
        String informResponse = llmService.generateResponse(informPrompt,
                PromptBuilder.createPromptForInformAPersonStep(inputFieldValueMap, formMessage, metaData, language));

        return ExtensionResponseFactory.createInformUserResponseWithVariables(currentVariables, informResponse);
    }

    /**
     * Converts form response to text using InformingUser prompt.
     * This method was moved from AskAPersonHandlerSummarize and is used for user response cases (stepType != CST_1030).
     */
    private ExtensionResponse convertFormResponseToText(Map<String, Object> inputFieldValueMap, StepMetadata metaData, String language) {
        String informUserResponse = llmService.generateResponse(informPrompt,
                PromptBuilder.createPromptForInformingUser(inputFieldValueMap, metaData, language));

        return ExtensionResponseFactory.createInformUserResponse(informUserResponse);
    }

}
