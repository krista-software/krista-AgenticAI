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

import java.time.Instant;

/**
 * Record of a single LLM call with token usage, cost, and metadata.
 * Immutable record for thread-safety.
 */
public class TokenUsageRecord {
    
    private final Instant timestamp;
    private final String modelName;
    private final String apiKeyMasked;  // Last 4 characters only
    private final String agentName;
    private final String promptSummary;  // Truncated prompt for readability
    private final String fullPrompt;     // Full prompt text
    private final int promptLength;      // Character count
    private final String completionText;
    private final int completionLength;  // Character count
    private final int promptTokens;
    private final int completionTokens;
    private final int totalTokens;
    private final double cost;           // In USD
    private final long requestDurationMs;
    
    private TokenUsageRecord(Builder builder) {
        this.timestamp = builder.timestamp;
        this.modelName = builder.modelName;
        this.apiKeyMasked = builder.apiKeyMasked;
        this.agentName = builder.agentName;
        this.promptSummary = builder.promptSummary;
        this.fullPrompt = builder.fullPrompt;
        this.promptLength = builder.promptLength;
        this.completionText = builder.completionText;
        this.completionLength = builder.completionLength;
        this.promptTokens = builder.promptTokens;
        this.completionTokens = builder.completionTokens;
        this.totalTokens = builder.totalTokens;
        this.cost = builder.cost;
        this.requestDurationMs = builder.requestDurationMs;
    }
    
    public static Builder builder() {
        return new Builder();
    }
    
    // Getters
    public Instant getTimestamp() { return timestamp; }
    public String getModelName() { return modelName; }
    public String getApiKeyMasked() { return apiKeyMasked; }
    public String getAgentName() { return agentName; }
    public String getPromptSummary() { return promptSummary; }
    public String getFullPrompt() { return fullPrompt; }
    public int getPromptLength() { return promptLength; }
    public String getCompletionText() { return completionText; }
    public int getCompletionLength() { return completionLength; }
    public int getPromptTokens() { return promptTokens; }
    public int getCompletionTokens() { return completionTokens; }
    public int getTotalTokens() { return totalTokens; }
    public double getCost() { return cost; }
    public long getRequestDurationMs() { return requestDurationMs; }
    
    public static class Builder {
        private Instant timestamp = Instant.now();
        private String modelName;
        private String apiKeyMasked;
        private String agentName;
        private String promptSummary;
        private String fullPrompt;
        private int promptLength;
        private String completionText;
        private int completionLength;
        private int promptTokens;
        private int completionTokens;
        private int totalTokens;
        private double cost;
        private long requestDurationMs;
        
        public Builder timestamp(Instant timestamp) {
            this.timestamp = timestamp;
            return this;
        }
        
        public Builder modelName(String modelName) {
            this.modelName = modelName;
            return this;
        }
        
        public Builder apiKeyMasked(String apiKeyMasked) {
            this.apiKeyMasked = apiKeyMasked;
            return this;
        }
        
        public Builder agentName(String agentName) {
            this.agentName = agentName;
            return this;
        }
        
        public Builder promptSummary(String promptSummary) {
            this.promptSummary = promptSummary;
            return this;
        }
        
        public Builder fullPrompt(String fullPrompt) {
            this.fullPrompt = fullPrompt;
            this.promptLength = fullPrompt != null ? fullPrompt.length() : 0;
            return this;
        }
        
        public Builder completionText(String completionText) {
            this.completionText = completionText;
            this.completionLength = completionText != null ? completionText.length() : 0;
            return this;
        }
        
        public Builder promptTokens(int promptTokens) {
            this.promptTokens = promptTokens;
            return this;
        }
        
        public Builder completionTokens(int completionTokens) {
            this.completionTokens = completionTokens;
            return this;
        }
        
        public Builder totalTokens(int totalTokens) {
            this.totalTokens = totalTokens;
            return this;
        }
        
        public Builder cost(double cost) {
            this.cost = cost;
            return this;
        }
        
        public Builder requestDurationMs(long requestDurationMs) {
            this.requestDurationMs = requestDurationMs;
            return this;
        }
        
        public TokenUsageRecord build() {
            return new TokenUsageRecord(this);
        }
    }
}

