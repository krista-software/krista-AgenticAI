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
import app.krista.extensions.hook.helper.StepHandlerHelper;
import app.krista.extensions.hook.llm.LLMServiceWithFallback;
import app.krista.extensions.hook.common.*;
import app.krista.extensions.hook.common.fieldToLLM.FieldAttributeFilter;
import app.krista.extensions.hook.common.models.StepMetadata;
import app.krista.extensions.hook.common.validator.LLMResponseValidator;
import app.krista.extensions.hook.factory.ExtensionResponseFactory;
import app.krista.extensions.hook.translation.MessageTranslationService;
import app.krista.extensions.hook.translation.TranslatableConstants;
import app.krista.model.base.FreeForm;

import java.util.Map;

public class AskAPersonHandlerExtractVariables implements StepExecutionHandler {
    private final LLMServiceWithFallback llmService;
    private final String entityExtractionPrompt;
    private final String confirmationDetailsPrompt;
    private final MessageTranslationService translationService;
    private final String followUoPrompt;
    private final String defaultValuesConfirmationPrompt;

    public AskAPersonHandlerExtractVariables(Invoker invoker) throws Exception {
        this.llmService = new LLMServiceWithFallback(invoker);
        this.entityExtractionPrompt = FileReader.readFileAsStringFromResource("prompts/EntityExtraction.txt");
        this.followUoPrompt = FileReader.readFileAsStringFromResource("prompts/FollowUp.txt");
        this.confirmationDetailsPrompt = FileReader.readFileAsStringFromResource("prompts/ConfirmDetails.txt");
        this.defaultValuesConfirmationPrompt = FileReader.readFileAsStringFromResource("prompts/DefaultValuesConfirmation.txt");
        this.translationService = new MessageTranslationService(llmService);
    }

    @Override
    public ExtensionResponse processRequest(AgentAction agentAction, FreeForm currentVariables, String formMessage, String userMessage, StepMetadata metaData, FreeForm currentVariablesDefault, String language, OrchestrationContext orchestrationContext) {
        if (agentAction != AgentAction.EXTRACT_VARIABLES) {
            throw new UnsupportedOperationException("ExtractVariablesHandler only supports EXTRACT_VARIABLES action");
        }

        return handleExtractVariables(currentVariables, formMessage, userMessage, metaData, currentVariablesDefault, language);
    }

    private ExtensionResponse handleExtractVariables(FreeForm currentVariables, String formMessage, String userMessage, StepMetadata metaData, FreeForm currentVariablesDefault, String language) {
        try {
            // Step 1: Extract entities from user input
            LLMServiceWithFallback.EntityExtractionResult entityResult = performEntityExtraction(currentVariables, formMessage, userMessage, language);

            // Step 2: Update FreeForm with extracted variables and validate
            VariableUpdateResult updateResult = updateVariablesWithValidation(currentVariables, entityResult);

            // Step 3: Generate appropriate response based on form state and entity result
            return generateResponseBasedOnFormState(updateResult, entityResult, formMessage, userMessage, metaData, currentVariablesDefault, language);

        } catch (Exception e) {
            // Check if the exception is caused by unsupported field type
            if (StepHandlerHelper.isUnsupportedFieldTypeException(e)) {
                // Use ChatFormMessageAction to display message in both chat and form modes
                String errorMessage = TranslatableConstants.translate(TranslatableConstants.FIELD_NOT_SUPPORTED, translationService, language);
                return ExtensionResponseFactory.createChatFormMessageResponse(errorMessage);
            }
            return ExtensionResponseFactory.createErrorResponse(e.getMessage(), currentVariables);
        }
    }

    /**
     * Performs entity extraction from user input using LLM service.
     */
    private LLMServiceWithFallback.EntityExtractionResult performEntityExtraction(FreeForm currentVariables, String formMessage, String userMessage, String language) throws Exception {
        Map<String, Object> inputFieldValueMap = FreeFormHelper.convertFreeFormToFieldValueMap(currentVariables, FieldAttributeFilter.PromptContext.ENTITY_EXTRACTION);
        return llmService.extractVariableMapping(entityExtractionPrompt,
                PromptBuilder.createPromptForEntityExtraction(inputFieldValueMap, formMessage, userMessage, language));
    }

    /**
     * Updates FreeForm with extracted variables and performs validation.
     */
    private VariableUpdateResult updateVariablesWithValidation(FreeForm currentVariables,
                                                              LLMServiceWithFallback.EntityExtractionResult entityResult) throws Exception {
        Map<String, Object> validationErrors = LLMResponseValidator.validate(currentVariables, entityResult.getVariableMapping());
        FreeForm updatedFreeForm = FreeFormHelper.updateFreeFormValues(currentVariables, entityResult.getVariableMapping(), validationErrors);
        
        // Use the same FieldAttributeFilter context as in performEntityExtraction to ensure consistency
        Map<String, Object> updatedInputFieldValueMap = FreeFormHelper.convertFreeFormToFieldValueMap(updatedFreeForm, FieldAttributeFilter.PromptContext.ENTITY_EXTRACTION);

        return new VariableUpdateResult(updatedFreeForm, updatedInputFieldValueMap, validationErrors);
    }

    /**
     * Generates appropriate response based on form completion state and entity extraction result.
     */
    private ExtensionResponse generateResponseBasedOnFormState(VariableUpdateResult updateResult,
                                                              LLMServiceWithFallback.EntityExtractionResult entityResult,
                                                              String formMessage, String userMessage, StepMetadata metaData,
                                                              FreeForm currentVariablesDefault, String language) {
        // Check if updated freeForm does not have value for at least one of the fields present in currentVariablesDefault
        if(FreeFormHelper.hasAtLeastOneValue(currentVariablesDefault, false) &&
                !FreeFormHelper.isFreeFormFilled(updateResult.updatedFreeForm, false) &&
                (FreeFormDefaultsHelper.hasMissingValuesFromReference(updateResult.updatedFreeForm, currentVariablesDefault, false) ||
                        FreeFormDefaultsHelper.hasOnlyReferenceFieldValues(updateResult.updatedFreeForm, currentVariablesDefault, false))) {
            return handleDefaultValuesConfirmation(currentVariablesDefault, updateResult, entityResult, formMessage, metaData, userMessage, language);
        } else if (FreeFormHelper.isFreeFormFilled(updateResult.updatedFreeForm, true) && updateResult.validationErrors.isEmpty()) {
            return handleFilledFormWithNoValidationErrorResponse(updateResult, entityResult, formMessage, metaData, language);
        } else {
            return handlePartiallyFilledFormOrValidationErrorResponse(updateResult, formMessage, userMessage, metaData, language);
        }
    }

    /**
     * Handles response generation for default values confirmation.
     */
    private ExtensionResponse handleDefaultValuesConfirmation(FreeForm currentVariablesDefault,
                                                              VariableUpdateResult updateResult,
                                                              LLMServiceWithFallback.EntityExtractionResult entityResult,
                                                              String formMessage, StepMetadata metaData, String userMessage, String language) {
        try {
            DefaultValuesProcessingResult processingResult = processDefaultValuesWithExtractedData(
                    currentVariablesDefault, updateResult);

            return generateDefaultValuesResponse(processingResult, updateResult, entityResult,
                    formMessage, metaData, userMessage, language);
        } catch (Exception e) {
            return ExtensionResponseFactory.createErrorResponse("Error generating default values confirmation: " + e.getMessage(), updateResult.updatedFreeForm);
        }
    }

    /**
     * Processes default values by merging with extracted entity data.
     */
    private DefaultValuesProcessingResult processDefaultValuesWithExtractedData(FreeForm currentVariablesDefault,
                                                                               VariableUpdateResult updateResult) {
        // Update default values with extracted entity values
        FreeForm updatedDefaultValues = FreeFormDefaultsHelper.updateFreeFormFromFreeForm(
                currentVariablesDefault, updateResult.updatedFreeForm, true);

        FreeForm updatedFreeForm = FreeFormDefaultsHelper.updateFreeFormFromFreeForm(
                updateResult.updatedFreeForm, updatedDefaultValues, false);

        return new DefaultValuesProcessingResult(updatedDefaultValues, updatedFreeForm);
    }

    /**
     * Generates appropriate response based on entity result and form completion state.
     */
    private ExtensionResponse generateDefaultValuesResponse(DefaultValuesProcessingResult processingResult,
                                                           VariableUpdateResult updateResult,
                                                           LLMServiceWithFallback.EntityExtractionResult entityResult,
                                                           String formMessage, StepMetadata metaData, String userMessage, String language) throws Exception {
        if (entityResult.shouldMoveToNextStep() && FreeFormHelper.isFreeFormFilled(processingResult.updatedFreeForm, false)) {
            return createConfirmationResponseForFilledForm(processingResult.updatedFreeForm);
        } else if (entityResult.shouldMoveToNextStep() && !FreeFormHelper.isFreeFormFilled(processingResult.updatedFreeForm, false)) {
            return createFollowUpResponseForIncompleteForm(processingResult.updatedFreeForm, updateResult,
                    formMessage, metaData, userMessage, language);
        } else {
            return createDefaultValuesConfirmationResponse(processingResult.updatedDefaultValues, updateResult.updatedFreeForm, language);
        }
    }

    /**
     * Creates confirmation response for fully filled form.
     */
    private ExtensionResponse createConfirmationResponseForFilledForm(FreeForm updatedFreeForm) {
        return ExtensionResponseFactory.createConfirmationResponse(updatedFreeForm);
    }

    /**
     * Creates follow-up response for incomplete form when moving to next step.
     */
    private ExtensionResponse createFollowUpResponseForIncompleteForm(FreeForm updatedFreeForm,
                                                                     VariableUpdateResult updateResult,
                                                                     String formMessage, StepMetadata metaData, String userMessage, String language) throws Exception {
        Map<String, Object> updatedFreeFormMap = FreeFormHelper.convertFreeFormToFieldValueMap(updatedFreeForm);

        return ExtensionResponseFactory.createEntityExtractionResponse(
                updatedFreeForm,
                llmService.generateResponse(followUoPrompt,
                        PromptBuilder.createPromptForQuestionGeneration(updatedFreeFormMap, formMessage,
                                updateResult.validationErrors, userMessage, metaData, language)));
    }

    /**
     * Creates default values confirmation response.
     */
    private ExtensionResponse createDefaultValuesConfirmationResponse(FreeForm updatedDefaultValues, FreeForm originalUpdatedFreeForm, String language) throws Exception {
        Map<String, Object> defaultFieldValueMap = FreeFormHelper.convertFreeFormToFieldValueMap(updatedDefaultValues);

        return ExtensionResponseFactory.createEntityExtractionResponse(
                originalUpdatedFreeForm,
                llmService.generateResponse(defaultValuesConfirmationPrompt,
                        PromptBuilder.createPromptForDefaultValuesConfirmation(defaultFieldValueMap, language)));
    }

    /**
     * Record to hold the result of default values processing.
     */
    private record DefaultValuesProcessingResult(FreeForm updatedDefaultValues, FreeForm updatedFreeForm) {
    }


    /**
     * Handles response generation for fully filled forms with no validation errors.
     */
    private ExtensionResponse handleFilledFormWithNoValidationErrorResponse(VariableUpdateResult updateResult,
                                                                            LLMServiceWithFallback.EntityExtractionResult entityResult,
                                                                            String formMessage, StepMetadata metaData, String language) {
        if (entityResult.shouldMoveToNextStep()) {
            return ExtensionResponseFactory.createConfirmationResponse(updateResult.updatedFreeForm);
        } else {
            String responseText = llmService.generateResponse(confirmationDetailsPrompt,
                    PromptBuilder.createPromptForConfirmation(updateResult.updatedInputFieldValueMap, formMessage, metaData, language));

            return ExtensionResponseFactory.createEntityExtractionResponse(updateResult.updatedFreeForm, responseText);
        }
    }

    /**
     * Handles response generation for partially filled forms or LLM response with validation errors.
     */
    private ExtensionResponse handlePartiallyFilledFormOrValidationErrorResponse(VariableUpdateResult updateResult,
                                                                                 String formMessage, String userMessage, StepMetadata metaData, String language) {
        String responseText = llmService.generateResponse(followUoPrompt,
                PromptBuilder.createPromptForQuestionGeneration(updateResult.updatedInputFieldValueMap, formMessage, updateResult.validationErrors, userMessage, metaData, language));

        return ExtensionResponseFactory.createEntityExtractionResponse(updateResult.updatedFreeForm, responseText);
    }

    /**
     * Record to hold the result of variable update operations.
     */
    private record VariableUpdateResult(FreeForm updatedFreeForm,
                                        Map<String, Object> updatedInputFieldValueMap,
                                        Map<String, Object> validationErrors) {
    }
}
