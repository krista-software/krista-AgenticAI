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

package app.krista.extensions.hook.handler;

import app.krista.extension.executor.ExtensionResponse;
import app.krista.extension.executor.Invoker;
import app.krista.extensions.hook.common.FileReader;
import app.krista.extensions.hook.common.PromptBuilder;
import app.krista.extensions.hook.llm.LLMServiceWithFallback;
import app.krista.extensions.hook.util.GsonProvider;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.*;

/**
 * Handler for intent classification logic in the "Ask me anything" catalog request.
 * Determines if user input is a question or conversation initiation request.
 */
public class IntentClassificationHandler {

    private final Invoker invoker;
    private final String intentPrompt;
    private final LLMServiceWithFallback llmService;

    public IntentClassificationHandler(Invoker invoker) throws Exception {
        this.invoker = invoker;
        this.intentPrompt = FileReader.readFileAsStringFromResource("prompts/IntentClassification.txt");
        this.llmService = new LLMServiceWithFallback(invoker);
    }

    /**
     * Processes user message and classifies intent
     *
     * @param userMessage User's input message
     * @param conversationsMetaData Available conversations metadata
     * @return ExtensionResponse with classification results
     */
    public ExtensionResponse classifyIntent(String userMessage, List<Map<String, Object>> conversationsMetaData) {
        try {
            String userPrompt = PromptBuilder.createPromptForIntentClassification(userMessage, conversationsMetaData);
            String llmResponse = llmService.queryWithFallback(intentPrompt, userPrompt);
            Type mapType = new TypeToken<Map<String, Object>>(){}.getType();
            Map<String, Object> result = GsonProvider.getInstance().fromJson(llmResponse, mapType);

            return processSimpleIntentResult(result, userMessage);

        } catch (Exception e) {
            System.out.println("Error in intent classification: " + e.getMessage());
            throw new RuntimeException("Intent classification failed: " + e.getMessage(), e);
        }
    }



    /**
     * Processes the LLM result and returns ExtensionResponse
     */
    private ExtensionResponse processSimpleIntentResult(Map<String, Object> result, String userMessage) {
        String intent = (String) result.get("intent");
        String matchedConversationId = (String) result.get("matched_conversation_id");
        List<String> nearMatches = (List<String>) result.get("near_matches");

        System.out.println("Intent classification result: " + intent);
        System.out.println("Matched conversation ID: " + matchedConversationId);
        System.out.println("Near matches: " + nearMatches);

        Map<String, Object> responseVariables = new HashMap<>();
        responseVariables.put("CurrentVariables", null);

        if ("QUESTION".equals(intent)) {
            responseVariables.put("ConversationDefnId", null);
            responseVariables.put("Intent", "QUESTION");

        } else if ("CONVERSATION_INITIATION".equals(intent)) {
            if (matchedConversationId != null) {
                // Exact match - return single conversation ID as List
                responseVariables.put("ConversationDefnId", Arrays.asList(matchedConversationId));
            } else if (nearMatches != null && !nearMatches.isEmpty()) {
                // Near matches - return list of conversation IDs as List
                responseVariables.put("ConversationDefnId", nearMatches);
            } else {
                // No matches
                responseVariables.put("ConversationDefnId", null);
            }
            responseVariables.put("Intent", "CONVERSATION_INITIATION");

        } else {
            // Unknown intent
            responseVariables.put("ConversationDefnId", null);
            responseVariables.put("Intent", "UNKNOWN");
        }

        return new ExtensionResponse(
            ExtensionResponse.Result.SUCCESS,
            responseVariables,
            null,
            null,
            null
        );
    }
}

