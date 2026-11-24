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

package app.krista.extensions.hook.agentic.factory;

import app.krista.extension.executor.Invoker;
import app.krista.extensions.hook.agentic.usagecollector.AgenticChatLanguageModel;
import app.krista.extensions.hook.agentic.usagecollector.CostCalculator;
import dev.langchain4j.model.anthropic.AnthropicChatModel;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.googleai.GoogleAiGeminiChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import app.krista.extensions.hook.Constants;
import app.krista.extensions.hook.llm.claude.util.ClaudeConstants;
import app.krista.extensions.hook.llm.openai.util.OpenAIConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;

/**
 * Factory for creating ChatLanguageModel instances from different providers.
 *
 * Supports:
 * - OpenAI (GPT-4, GPT-4o, GPT-3.5-turbo, etc.)
 * - Anthropic Claude (Claude 3.5 Sonnet, Claude 3 Opus, etc.)
 * - Google Gemini (gemini-pro, gemini-1.5-pro, etc.)
 * - Azure OpenAI
 */
public class ModelFactory {

    private static final Logger LOGGER = LoggerFactory.getLogger(ModelFactory.class);

    /**
     * Supported LLM providers
     */
    public enum Provider {
        OPENAI,
        CLAUDE,
    }

    /**
     * Configuration for creating a model
     */
    public static class ModelConfig {
        private final Provider provider;
        private final String modelName;
        private final String apiKey;
        private final Double temperature;
        private final Integer timeoutSeconds;
        private final Integer maxTokens;

        private ModelConfig(Builder builder) {
            this.provider = builder.provider;
            this.modelName = builder.modelName;
            this.apiKey = builder.apiKey;
            this.temperature = builder.temperature;
            this.timeoutSeconds = builder.timeoutSeconds;
            this.maxTokens = builder.maxTokens;
        }

        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {
            private Provider provider = Provider.OPENAI;
            private String modelName = "gpt-4o-mini";
            private String apiKey;
            private Double temperature = 0.3;
            private Integer timeoutSeconds = 60;
            // Anthropic's API requires max_tokens; langchain4j defaults it to 1024 if unset,
            // which truncates long structured tool-call arguments. 8192 leaves headroom for
            // the agentic flow's JSON envelope around long HTML answers.
            private Integer maxTokens = 4096;

            public Builder provider(Provider provider) {
                this.provider = provider;
                return this;
            }

            public Builder modelName(String modelName) {
                this.modelName = modelName;
                return this;
            }

            public Builder apiKey(String apiKey) {
                this.apiKey = apiKey;
                return this;
            }

            public Builder temperature(Double temperature) {
                this.temperature = temperature;
                return this;
            }

            public Builder timeoutSeconds(Integer timeoutSeconds) {
                this.timeoutSeconds = timeoutSeconds;
                return this;
            }

            public Builder maxTokens(Integer maxTokens) {
                this.maxTokens = maxTokens;
                return this;
            }

            public ModelConfig build() {
                return new ModelConfig(this);
            }
        }

        public Provider getProvider() { return provider; }
        public String getModelName() { return modelName; }
        public String getApiKey() { return apiKey; }
        public Double getTemperature() { return temperature; }
        public Integer getTimeoutSeconds() { return timeoutSeconds; }
        public Integer getMaxTokens() { return maxTokens; }
    }

    /**
     * Create a ChatLanguageModel based on the configuration
     */
    public static ChatLanguageModel createModel(ModelConfig config) {
        LOGGER.info("Creating {} model: {}", config.getProvider(), config.getModelName());

        switch (config.getProvider()) {
            case OPENAI:
                return createOpenAiModel(config);
            case CLAUDE:
                return createAnthropicModel(config);
            default:
                throw new IllegalArgumentException("Unsupported provider: " + config.getProvider());
        }
    }

    /**
     * Create OpenAI model (GPT-4, GPT-4o, GPT-3.5-turbo, etc.)
     *
     * Note: do NOT call .maxTokens(...) here. OpenAI's reasoning-class models
     * (GPT-5 family, o1, o3) reject the 'max_tokens' parameter and require
     * 'max_completion_tokens' instead, which langchain4j 0.36.2 does not expose.
     * Omitting the field lets OpenAI use its server-side default, which works
     * for both legacy (gpt-4o, gpt-4) and reasoning models.
     */
    private static ChatLanguageModel createOpenAiModel(ModelConfig config) {

        return OpenAiChatModel.builder()
                .apiKey(config.getApiKey())
                .modelName(config.getModelName())
                .temperature(config.getTemperature())
                .timeout(Duration.ofSeconds(config.getTimeoutSeconds()))
                .logRequests(true)
                .build();
    }

    /**
     * Create Anthropic Claude model (Claude 3.5 Sonnet, Claude 3 Opus, etc.)
     */
    private static ChatLanguageModel createAnthropicModel(ModelConfig config) {

        return AnthropicChatModel.builder()
                .apiKey(config.getApiKey())
                .modelName(config.getModelName())
                .temperature(config.getTemperature())
                .timeout(Duration.ofSeconds(config.getTimeoutSeconds()))
                .maxTokens(config.getMaxTokens())
                .logRequests(true)
                .build();
    }


    /**
     * Convenience method to create a model from invoker attributes.
     * Reads LLM_PROVIDER and API_KEY from invoker attributes.
     */
    public static ChatLanguageModel createFromAttributes(Invoker invoker) {
        return createFromAttributes(invoker, false, "Agent");
    }

    /**
     * Convenience method to create a model from invoker attributes with optional agentic wrapping.
     *
     * @param invoker The invoker containing API key and configuration
     * @param isAgenticModel If true, wraps the base model with AgenticChatLanguageModel for usage tracking
     * @param agentName The name of the agent (used for usage tracking when isAgenticModel is true)
     * @return ChatLanguageModel instance (wrapped or unwrapped based on isAgenticModel)
     */
    public static ChatLanguageModel createFromAttributes(Invoker invoker, boolean isAgenticModel, String agentName) {
        String providerStr = invoker.getAttributes()
                .getOrDefault(Constants.CLOUD_LLM_PROVIDER, "openai")
                .toString()
                .trim()
                .toLowerCase();

        String apiKey = invoker.getAttributes()
                .getOrDefault(Constants.CLOUD_API_KEY, "")
                .toString()
                .trim();

        if (apiKey == null || apiKey.isEmpty()) {
            throw new IllegalStateException("Cloud API Key is not provided.");
        }

        Provider provider = Provider.OPENAI;  // Default
        if (providerStr != null && !providerStr.isEmpty()) {
            try {
                provider = Provider.valueOf(providerStr.toUpperCase());
            } catch (IllegalArgumentException e) {
                LOGGER.warn("Invalid LLM_PROVIDER: {}. Using default: OPENAI", providerStr);
            }
        }

        String modelName = OpenAIConstants.GPT_5_2;

        // Set default model names based on provider
        switch (provider) {
                case OPENAI:
                    modelName = OpenAIConstants.GPT_5_2;
                    break;
                case CLAUDE:
                    modelName = ClaudeConstants.CLAUDE_OPUS_4;
                    break;
        }

        LOGGER.info("Creating model from environment: provider={}, model={}", provider, modelName);

        // Create the base model
        ChatLanguageModel baseModel = createModel(ModelConfig.builder()
                .provider(provider)
                .modelName(modelName)
                .apiKey(apiKey)
                .build());

        // Wrap with AgenticChatLanguageModel if requested
        if (isAgenticModel) {
            String apiKeyMasked = CostCalculator.maskApiKey(apiKey);
            LOGGER.debug("Wrapping model with AgenticChatLanguageModel for usage tracking");
            return new AgenticChatLanguageModel(
                    baseModel,
                    agentName,
                    modelName,
                    apiKeyMasked,
                    true
            );
        }

        return baseModel;
    }
}
