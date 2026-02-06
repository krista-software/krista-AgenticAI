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

package app.krista.extensions.hook.factory;

import app.krista.extension.executor.Invoker;
import app.krista.extensions.hook.Constants;
import app.krista.extensions.hook.config.LLMConfiguration;
import app.krista.extensions.hook.llm.LLMClient;
import app.krista.extensions.hook.llm.claude.ClaudeClient;
import app.krista.extensions.hook.llm.claude.util.ClaudeConstants;
import app.krista.extensions.hook.llm.krista.KristaClient;
import app.krista.extensions.hook.llm.krista.util.KristaConstants;
import app.krista.extensions.hook.llm.openai.OpenAIClient;
import app.krista.extensions.hook.llm.openai.util.OpenAIConstants;

/**
 * Factory for creating LLM clients based on configuration
 */
public class LLMClientFactory {

    private final Invoker invoker;
    private final LLMConfiguration llmConfiguration;

    public LLMClientFactory(Invoker invoker) {
        this.invoker = invoker;
        this.llmConfiguration = new LLMConfiguration(invoker);
    }


    /**
     * Creates a cloud LLM client with configurable timeout
     */
    public LLMClient createCloudClient(String cloudProvider, int timeoutSeconds) {
        String apiKey = invoker.getAttributes().getOrDefault(Constants.CLOUD_API_KEY, "").toString().trim();
        if (apiKey.isEmpty()) {
            throw new IllegalArgumentException("Cloud API Key is required for cloud LLM providers. Please configure it in the extension settings.");
        }

        String actualCloudProvider = cloudProvider != null ? cloudProvider.toLowerCase() : "openai";
        if ("claude".equals(actualCloudProvider)) {
            System.out.println("Creating Claude client with model: " + ClaudeConstants.CLAUDE_SONNET_4 + " and timeout: " + timeoutSeconds + "s");
            return new ClaudeClient(apiKey, ClaudeConstants.CLAUDE_SONNET_4, timeoutSeconds);
        } else {
            System.out.println("Creating OpenAI client with model: " + OpenAIConstants.GPT_4_1_mini + " and timeout: " + timeoutSeconds + "s");
            return new OpenAIClient(apiKey, OpenAIConstants.GPT_4_1_mini, timeoutSeconds);
        }
    }

    /**
     * Creates a Krista LLM client with configurable timeout
     */
    public LLMClient createKristaClient(int timeoutSeconds) {
        // Check if Krista LLM fallback is enabled (this now includes API key validation)
        if (!llmConfiguration.isKristaLLMFallbackEnabled()) {
            throw new IllegalArgumentException("Krista LLM fallback is disabled. Cannot create Krista client.");
        }

        // At this point, we know the API key exists because isKristaLLMFallbackEnabled() checks it
        String apiKey = llmConfiguration.getKristaAPIKey();
        System.out.println("Creating Krista client with model: " + KristaConstants.QWEN_32B_AWQ + " at URL: " + KristaConstants.DEFAULT_API_URL + " and timeout: " + timeoutSeconds + "s");
        return new KristaClient(apiKey, KristaConstants.QWEN_32B_AWQ, timeoutSeconds);
    }
}
