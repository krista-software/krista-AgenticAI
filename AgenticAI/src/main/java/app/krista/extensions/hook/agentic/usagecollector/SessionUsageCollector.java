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

import app.krista.extensions.hook.agentic.telemetry.AgenticRequestContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Collects token usage records for a single ToolPlanner session.
 * Thread-safe using CopyOnWriteArrayList.
 */
public class SessionUsageCollector {

    private static final Logger LOGGER = LoggerFactory.getLogger(SessionUsageCollector.class);

    private final String sessionId;
    private final String agentName;
    private final Instant startTime;
    private Instant endTime;
    private SessionStatus status;
    private String errorMessage;
    private String errorCode;

    // Thread-safe list for concurrent LLM calls
    private final List<TokenUsageRecord> usageRecords;
    
    // Child session tracking for roll-up
    private int childSessionsCount = 0;
    private double childTotalCost = 0.0;

    // Workspace/invoker identity captured at request entry; used for telemetry dimensions
    private AgenticRequestContext requestContext;
    
    public enum SessionStatus {
        IN_PROGRESS,
        COMPLETED,
        ERROR
    }
    
    public SessionUsageCollector(String sessionId, String agentName) {
        this.sessionId = sessionId;
        this.agentName = agentName;
        this.startTime = Instant.now();
        this.status = SessionStatus.IN_PROGRESS;
        this.usageRecords = new CopyOnWriteArrayList<>();
    }
    
    /**
     * Add a token usage record for an LLM call.
     */
    public void addUsageRecord(TokenUsageRecord record) {
        usageRecords.add(record);
        LOGGER.info("Usage tracked for session {}: {} input + {} output tokens, cost: ${}",
            sessionId, record.getPromptTokens(), record.getCompletionTokens(),
            String.format("%.6f", record.getCost()));
    }
    
    /**
     * Mark the session as completed.
     */
    public void markCompleted() {
        this.status = SessionStatus.COMPLETED;
        this.endTime = Instant.now();
        LOGGER.info("Session {} completed with {} LLM calls", sessionId, usageRecords.size());
    }
    
    /**
     * Mark the session as errored.
     */
    public void markError(String errorMessage) {
        this.status = SessionStatus.ERROR;
        this.endTime = Instant.now();
        this.errorMessage = errorMessage;
        LOGGER.warn("Session {} errored: {}", sessionId, errorMessage);
    }

    /**
     * Mark the session as errored with an error code for structured observability.
     */
    public void markError(String errorMessage, String errorCode) {

        this.status = SessionStatus.ERROR;
        this.endTime = Instant.now();
        this.errorMessage = errorMessage;
        this.errorCode = errorCode;
        LOGGER.warn("Session {} errored [{}]: {}", sessionId, errorCode, errorMessage);
    }

    /**
     * Get a summary of the session usage.
     */
    public SessionSummary getSessionSummary() {
        int totalPromptTokens = 0;
        int totalCompletionTokens = 0;
        int totalTokens = 0;
        double totalCost = 0.0;
        
        for (TokenUsageRecord record : usageRecords) {
            totalPromptTokens += record.getPromptTokens();
            totalCompletionTokens += record.getCompletionTokens();
            totalTokens += record.getTotalTokens();
            totalCost += record.getCost();
        }
        
        long durationMs = endTime != null ? 
            endTime.toEpochMilli() - startTime.toEpochMilli() : 
            Instant.now().toEpochMilli() - startTime.toEpochMilli();
        
        return new SessionSummary(
            sessionId,
            agentName,
            startTime,
            endTime,
            status,
            errorMessage,
            errorCode,
            usageRecords.size(),
            totalPromptTokens,
            totalCompletionTokens,
            totalTokens,
            totalCost,
            durationMs,
            childSessionsCount,
            childTotalCost
        );
    }
    
    /**
     * Get all usage records (immutable copy).
     */
    public List<TokenUsageRecord> getUsageRecords() {
        return new ArrayList<>(usageRecords);
    }
    
    // Getters
    public String getSessionId() { return sessionId; }
    public String getAgentName() { return agentName; }
    public Instant getStartTime() { return startTime; }
    public Instant getEndTime() { return endTime; }
    public SessionStatus getStatus() { return status; }
    public String getErrorMessage() { return errorMessage; }
    public String getErrorCode() { return errorCode; }
    public int getChildSessionsCount() { return childSessionsCount; }
    public double getChildTotalCost() { return childTotalCost; }
    public AgenticRequestContext getRequestContext() { return requestContext; }

    public void setRequestContext(AgenticRequestContext requestContext) {
        this.requestContext = requestContext;
    }
    
    /**
     * Get the current total cost for this session.
     * Includes costs from all LLM calls in this session.
     * Does NOT include child session costs (use getSessionSummary().getTotalCost() for that).
     *
     * This method is used for execution limit checking.
     *
     * @return Total cost in USD for this session's LLM calls
     */
    public double getCurrentSessionCost() {

        double totalCost = 0.0;

        for (TokenUsageRecord record : usageRecords) {

            totalCost += record.getCost();
        }

        return totalCost;
    }

    /**
     * Merges another collector's stats into this one.
     * Used for rolling up child session usage to parent.
     */
    public void merge(SessionUsageCollector childCollector) {
        // Add child's records to this collector
        this.usageRecords.addAll(childCollector.usageRecords);

        // Update child tracking stats
        this.childSessionsCount++;
        this.childTotalCost += childCollector.getSessionSummary().getTotalCost();

        LOGGER.info("Merged child session {} into parent {} (child cost: ${})",
            childCollector.getSessionId(), this.sessionId,
            String.format("%.4f", childCollector.getSessionSummary().getTotalCost()));
    }
    
    /**
     * Summary of session usage statistics.
     */
    public static class SessionSummary {
        private final String sessionId;
        private final String agentName;
        private final Instant startTime;
        private final Instant endTime;
        private final SessionStatus status;
        private final String errorMessage;
        private final String errorCode;
        private final int totalLlmCalls;
        private final int totalPromptTokens;
        private final int totalCompletionTokens;
        private final int totalTokens;
        private final double totalCost;
        private final long durationMs;
        private final int childSessionsCount;
        private final double childTotalCost;
        
        public SessionSummary(String sessionId, String agentName, Instant startTime, Instant endTime,
                            SessionStatus status, String errorMessage, String errorCode,
                            int totalLlmCalls, int totalPromptTokens, int totalCompletionTokens,
                            int totalTokens, double totalCost, long durationMs,
                            int childSessionsCount, double childTotalCost) {

            this.sessionId = sessionId;
            this.agentName = agentName;
            this.startTime = startTime;
            this.endTime = endTime;
            this.status = status;
            this.errorMessage = errorMessage;
            this.errorCode = errorCode;
            this.totalLlmCalls = totalLlmCalls;
            this.totalPromptTokens = totalPromptTokens;
            this.totalCompletionTokens = totalCompletionTokens;
            this.totalTokens = totalTokens;
            this.totalCost = totalCost;
            this.durationMs = durationMs;
            this.childSessionsCount = childSessionsCount;
            this.childTotalCost = childTotalCost;
        }
        
        // Getters
        public String getSessionId() { return sessionId; }
        public String getAgentName() { return agentName; }
        public Instant getStartTime() { return startTime; }
        public Instant getEndTime() { return endTime; }
        public SessionStatus getStatus() { return status; }
        public String getErrorMessage() { return errorMessage; }
        public String getErrorCode() { return errorCode; }
        public int getTotalLlmCalls() { return totalLlmCalls; }
        public int getTotalPromptTokens() { return totalPromptTokens; }
        public int getTotalCompletionTokens() { return totalCompletionTokens; }
        public int getTotalTokens() { return totalTokens; }
        public double getTotalCost() { return totalCost; }
        public long getDurationMs() { return durationMs; }
        public int getChildSessionsCount() { return childSessionsCount; }
        public double getChildTotalCost() { return childTotalCost; }
    }
}

