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

package app.krista.extensions.hook.agentic.usagecollector;

import app.krista.extensions.hook.llm.claude.util.ClaudeConstants;
import app.krista.extensions.hook.llm.openai.util.OpenAIConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Calculates LLM costs based on model pricing.
 * Pricing is per 1 million tokens (input and output).
 */
public class CostCalculator {

    private static final Logger LOGGER = LoggerFactory.getLogger(CostCalculator.class);

    // OpenAI GPT-4o-mini pricing (as of 2024)
    private static final double GPT_4O_MINI_INPUT_COST_PER_1M = 0.150;   // $0.15 per 1M input tokens
    private static final double GPT_4O_MINI_OUTPUT_COST_PER_1M = 0.600;  // $0.60 per 1M output tokens

    private static final double GPT_5_2_INPUT_COST_PER_1M = 1.750;   // $1.75 per 1M input tokens
    private static final double GPT_5_2_OUTPUT_COST_PER_1M = 14.000;  // $14.00 per 1M output tokens
    
    // OpenAI GPT-4.1 pricing (as of 2024)
    private static final double GPT_4_1_INPUT_COST_PER_1M = 3.00;   // $3.00 per 1M input tokens
    private static final double GPT_4_1_OUTPUT_COST_PER_1M = 12.00;  // $12.00 per 1M output tokens
    
    // Anthropic Claude Sonnet 4 pricing (as of 2024)
    private static final double CLAUDE_SONNET_4_INPUT_COST_PER_1M = 3.00;   // $3.00 per 1M input tokens
    private static final double CLAUDE_SONNET_4_OUTPUT_COST_PER_1M = 15.00; // $15.00 per 1M output tokens

    // Anthropic Claude Opus 4 pricing (as of 2025)
    private static final double CLAUDE_OPUS_4_INPUT_COST_PER_1M = 5.00;    // $5.00 per 1M input tokens
    private static final double CLAUDE_OPUS_4_OUTPUT_COST_PER_1M = 25.00;  // $25.00 per 1M output tokens
    
    /**
     * Calculate the cost of an LLM call based on model and token usage.
     * 
     * @param modelName The model name (e.g., "gpt-4o-mini", "claude-sonnet-4.0")
     * @param promptTokens Number of input/prompt tokens
     * @param completionTokens Number of output/completion tokens
     * @return Cost in USD
     */
    public static double calculateCost(String modelName, int promptTokens, int completionTokens) {
        if (modelName == null || modelName.isEmpty()) {
            return 0.0;
        }
        
        String normalizedModel = modelName.toLowerCase();
        
        // Determine pricing based on model
        double inputCostPer1M;
        double outputCostPer1M;
        
        if (normalizedModel.equals(OpenAIConstants.GPT_4_1_mini.toLowerCase())) {
            inputCostPer1M = GPT_4O_MINI_INPUT_COST_PER_1M;
            outputCostPer1M = GPT_4O_MINI_OUTPUT_COST_PER_1M;
        } else if (normalizedModel.equals(OpenAIConstants.GPT_5_2.toLowerCase())) {
            inputCostPer1M = GPT_5_2_INPUT_COST_PER_1M;
            outputCostPer1M = GPT_5_2_OUTPUT_COST_PER_1M;
        } else if (normalizedModel.equals(OpenAIConstants.GPT_4_1.toLowerCase())) {
            inputCostPer1M = GPT_4_1_INPUT_COST_PER_1M;
            outputCostPer1M = GPT_4_1_OUTPUT_COST_PER_1M;
        } else if (normalizedModel.equals(ClaudeConstants.CLAUDE_OPUS_4.toLowerCase())) {
            inputCostPer1M = CLAUDE_OPUS_4_INPUT_COST_PER_1M;
            outputCostPer1M = CLAUDE_OPUS_4_OUTPUT_COST_PER_1M;
        } else if (normalizedModel.equals(ClaudeConstants.CLAUDE_SONNET_4.toLowerCase())) {
            inputCostPer1M = CLAUDE_SONNET_4_INPUT_COST_PER_1M;
            outputCostPer1M = CLAUDE_SONNET_4_OUTPUT_COST_PER_1M;
        } else {
            // Default to GPT-4o-mini pricing for unknown models
            LOGGER.warn("Unknown model for cost calculation: {}. Using GPT-4o-mini pricing.", modelName);
            inputCostPer1M = GPT_4O_MINI_INPUT_COST_PER_1M;
            outputCostPer1M = GPT_4O_MINI_OUTPUT_COST_PER_1M;
        }
        
        // Calculate cost
        double inputCost = (promptTokens / 1_000_000.0) * inputCostPer1M;
        double outputCost = (completionTokens / 1_000_000.0) * outputCostPer1M;
        
        return inputCost + outputCost;
    }
    
    /**
     * Mask an API key to show only the last 4 characters.
     * 
     * @param apiKey The full API key
     * @return Masked API key (e.g., "****abcd")
     */
    public static String maskApiKey(String apiKey) {
        if (apiKey == null || apiKey.isEmpty()) {
            return "****";
        }
        
        if (apiKey.length() <= 4) {
            return "****";
        }
        
        return "****" + apiKey.substring(apiKey.length() - 4);
    }
    
    /**
     * Create a prompt summary by truncating to a reasonable length.
     * 
     * @param fullPrompt The full prompt text
     * @param maxLength Maximum length for summary
     * @return Truncated prompt with "..." if needed
     */
    public static String createPromptSummary(String fullPrompt, int maxLength) {
        if (fullPrompt == null || fullPrompt.isEmpty()) {
            return "";
        }
        
        if (fullPrompt.length() <= maxLength) {
            return fullPrompt;
        }
        
        return fullPrompt.substring(0, maxLength) + "...";
    }
}

