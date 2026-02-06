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

package app.krista.extensions.hook.api;

import app.krista.extension.executor.ExtensionResponse;
import app.krista.extension.executor.Invoker;
import app.krista.extension.executor.impl.AgentAction;
import app.krista.extension.impl.anno.CatalogRequest;
import app.krista.extension.impl.anno.Domain;
import app.krista.extension.impl.anno.Field;
import app.krista.extension.impl.anno.Attribute;
import app.krista.extensions.hook.factory.StepHandlerFactory;
import app.krista.extensions.hook.stepHandlers.StepExecutionHandler;
import app.krista.extensions.hook.helper.ActionParser;
import app.krista.extensions.hook.helper.RequestLogger;
import app.krista.extensions.hook.handler.IntentClassificationHandler;
import app.krista.extensions.hook.common.models.StepMetadata;
import app.krista.extensions.hook.preprocessor.FormMessagePreprocessor;
import app.krista.extensions.hook.common.LanguageCode;
import app.krista.model.base.FreeForm;

import javax.inject.Inject;
import java.util.*;
import java.util.Map;

@Domain(id = "catEntryDomain_66277b92-e35f-4838-a41d-f5e4785eb2c9",
        name = "AgenticAI",
        ecosystemId = "catEntryEcosystem_2141418b-7dd8-4c04-865e-8763ba9aadcf",
        ecosystemName = "AI",
        ecosystemVersion = "4f61efe0-8b50-4ef7-a841-1334a1fe1e03")
public class CatalogRequestHandler {

    private final StepHandlerFactory stepHandlerFactory;
    private final RequestLogger requestLogger;
    private final Invoker invoker;

    @Inject
    public CatalogRequestHandler(Invoker invoker) {
        this.stepHandlerFactory = new StepHandlerFactory();
        this.requestLogger = new RequestLogger();
        this.invoker = invoker;
    }

    @CatalogRequest(
            id = "localDomainRequest_9ae32c9b-c6ad-4055-830f-9f29397e052d",
            name = "enhance",
            description = "This request will enhance the information that is provided from CAM.",
            area = "CAMHook",
            type = CatalogRequest.Type.CHANGE_SYSTEM)
    @Field(name = "CurrentVariables", type = "TypedFreeForm", required = false, attributes = {}, options = {})
    public ExtensionResponse enhance(
            @Field(name = "CurrentVariables", type = "TypedFreeForm", required = true, attributes = {}, options = {}) FreeForm currentVariables,
            @Field(name = "PreviousVariables", type = "TypedFreeForm", required = true, attributes = {}, options = {}) FreeForm previousVariables,
            @Field.Text(name = "Action", required = true, attributes = {@Attribute(name = "visualWidth", value = "S")}, options = {}) String action,
            @Field.Text(name = "FormMessage", required = true, attributes = {@Attribute(name = "visualWidth", value = "S")}, options = {}) String formMessage,
            @Field(name = "CurrentVariablesDefault", type = "TypedFreeForm", required = true, attributes = {}, options = {}) FreeForm currentVariablesDefault,
            @Field.Text(name = "UserMessage", required = true, attributes = {@Attribute(name = "visualWidth", value = "S")}, options = {}) String userMessage,
            @Field.Text(name = "Language", required = true, attributes = {@Attribute(name = "visualWidth", value = "S")}, options = {}) String language,
            @Field.Desc(name = "MetaData", type = "{ ConversationTitle: Text, ExecutionId: Text, WorkspaceName: Text, TopicName: Text, StepTitle: Text, StepType: Text, ConversationDescription: Text }",required = true) Map<String, Object> metaData,
            @Field.Desc(name = "WorkspaceInstructions", type = "{ OrganizationName: Text, OrganizationType: Text, InteractionType: Text, CustomGuidelines: Text }",required = true) Map<String, Object> workspaceInstructions,
            @Field.Desc(name = "AgentInstructions", type = "{ AgentPurpose: Text, PrimaryUsers: [ Text ], InteractionType: [ Text ], ToneStyle: Text, SpecialInstructions: Text }",required = true) Map<String, Object> agentInstructions) {

        // Log all request parameters first, before any processing
        requestLogger.logRequestParameters(currentVariables, currentVariablesDefault, action, formMessage, userMessage);

        try {
            // Preprocess formMessage to handle phone number JSON objects and other transformations
            String processedFormMessage = FormMessagePreprocessor.preprocessFormMessage(formMessage);

            // Parse the action using ActionParser utility
            AgentAction agentAction = ActionParser.parseAction(action);
            System.out.println("Successfully parsed AgentAction: " + agentAction);

            // Convert language code to display name using LanguageCode enum
            String targetLanguage = LanguageCode.getDisplayName(language);
            System.out.println("Language code: " + language + " -> Target language: " + targetLanguage);

            // Create step handler using factory with automatic LLM fallback
            StepExecutionHandler stepExecutionHandler = stepHandlerFactory.createStepHandler(agentAction, invoker);
            System.out.println("Using handler: " + stepExecutionHandler.getClass().getSimpleName());

            // Convert metadata Map to StepMetadata object
            StepMetadata stepMetadata = StepMetadata.fromMap(metaData);
            System.out.println("Created StepMetadata: " + stepMetadata);

            ExtensionResponse response = stepExecutionHandler.processRequest(agentAction, currentVariables, processedFormMessage, userMessage, stepMetadata, currentVariablesDefault, targetLanguage);
            requestLogger.logResponse(response);
            return response;
        } catch (IllegalArgumentException e) {
            System.out.println("Error processing request: " + e.getMessage());
            throw new RuntimeException("Invalid action value: " + action + ". Supported values are: " +
                    String.join(", ", ActionParser.getEnumValuesAsString()), e);
        } catch (Exception e) {
            System.out.println("Error processing request: " + e.getMessage());
            throw new RuntimeException(e);
        }
    }

    @CatalogRequest(
            id = "localDomainRequest_51cfbad1-f6ea-4738-bb5f-c94cbfd311b2",
            name = "Ask me anything",
            description = "User types in query on client which is answered by Agentic AI",
            area = "CAMHook",
            type = CatalogRequest.Type.CHANGE_SYSTEM)
    @Field(name = "CurrentVariables", type = "TypedFreeForm", required = false, attributes = {}, options = {})
    @Field.Desc(name = "ConversationDefnId", type = "[ Text ]",required = false)
    @Field.Text(name = "Intent", required = false, attributes = {@Attribute(name = "visualWidth", value = "S")}, options = {})
    public ExtensionResponse askMeAnything(
            @Field.Text(name = "UserMessage", required = true, attributes = {@Attribute(name = "visualWidth", value = "S")}, options = {}) String userMessage,
            @Field.Desc(name = "ConversationsMetaData", type = "[ { ConversationTitle: Text, ConversationDescription: Text, ConversationDefnId: Text } ]",required = true) List<Map<String, Object>> conversationsMetaData) {

        // Log request parameters
        System.out.println("AskMeAnything - UserMessage: " + userMessage);
        System.out.println("AskMeAnything - Available conversations: " + (conversationsMetaData != null ? conversationsMetaData.size() : 0));

        try {
            // Create intent classification handler and delegate processing
            IntentClassificationHandler handler = new IntentClassificationHandler(invoker);
            return handler.classifyIntent(userMessage, conversationsMetaData);

        } catch (Exception e) {
            System.out.println("Error in askMeAnything: " + e.getMessage());
            throw new RuntimeException("Intent classification failed: " + e.getMessage(), e);
        }
    }

}
