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

package app.krista.extensions.hook.agentic.session;

import app.krista.extensions.hook.agentic.AgenticConstants;
import app.krista.extensions.hook.agentic.dto.AgenticToolDefinition;
import app.krista.extensions.hook.agentic.dto.ExpectedOutput;
import app.krista.extensions.hook.agentic.dto.InputVariable;
import app.krista.extensions.hook.agentic.telemetry.AgenticRequestContext;
import app.krista.extensions.hook.agentic.usagecollector.SessionUsageCollector;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Represents an active agentic conversation session.
 * Stores the session state, message history, and iteration count.
 */
public class AgenticSession {

    // Execution limit constants (nullable = can be disabled)

    /**
     * Maximum number of iterations (LLM calls) per session.
     * Set to null to disable iteration limit.
     * Default: 20 iterations
     */
    public static final Integer MAX_ITERATIONS = 20;

    /**
     * Maximum cost per session in USD.
     * Set to null to disable cost limit.
     * Default: $5.00 to prevent expensive runaway execution.
     */
    public static final Double MAX_COST_PER_SESSION = 5.0;

    /**
     * Maximum session duration in minutes.
     * Set to null to disable time limit.
     * Default: 5.0 minutes to prevent long-running sessions.
     */
    public static final Double MAX_SESSION_DURATION_MINUTES = 5.0;
    
    /**
     * Session lifecycle states.
     * ACTIVE: Session in progress, can continue conversation.
     * TERMINATED: Session completed or errored, ready for cleanup.
     */
    public enum SessionState {
        ACTIVE,
        TERMINATED
    }

    private String sessionId;
    private SessionState state = SessionState.ACTIVE;
    private Instant terminatedAt;
    private List<AgenticToolDefinition> tools;
    private String userMessage;
    private List<Map<String, Object>> messageHistory;
    private int iterationCount;
    private Instant createdAt;
    private Instant lastAccessAt;
    private List<InputVariable> inputVariables;
    private List<ExpectedOutput> expectedOutputs;
    private String parentSessionId; // For child agents, stores parent session reference
    private SessionUsageCollector usageCollector; // For tracking token usage
    private List<String> policyBrief;
    private String stepGoal;
    private AgenticRequestContext requestContext; // Workspace/invoker identity captured at request entry

    // Telemetry: tool dispatch timestamps. Stamped when processResponse() returns an
    // EXECUTE_TOOL response so continueFlow() can emit a retrospective agentic.tool.call
    // span covering the out-of-band window during which the platform executed the tool.
    // transient: ephemeral within a single JVM lifetime — orphaned after a restart, so not persisted.
    private transient Instant lastToolDispatchedAt;
    private transient String lastDispatchedToolName;

    // Telemetry: spanId of the most recent llm.call and most recent tool.call. Used to
    // stamp agentic.parent.span.id on newly-created spans so SigNoz queries can navigate
    // the logical parent-child hierarchy via attribute filters when the native SDK
    // tree-view fails (platform agent overrides our setParent). These are state-tracking
    // fields only — they don't affect span creation or export.
    // transient: span IDs from a previous JVM are orphans in SigNoz, so they are not persisted.
    private transient String lastLlmSpanId;
    private transient String lastToolCallSpanId;

    // Telemetry: this session's own conversation.id (CEC). Captured at session creation
    // from AgenticStepContext. Used so child sessions can read parent's conversation.id
    // without depending on the (different) ThreadLocal in their own POST.
    private String conversationId;

    // Telemetry: parent conversation id (parent session's executionId / CEC). Only set on
    // child sessions. Stamped as agentic.parent.conversation.id on every span emitted under
    // this session so a SigNoz query by parent CEC surfaces both parent and child activity.
    private String parentConversationId;

    // Telemetry: W3C traceparent of the first turn's flow.start span. Stashed once at session
    // creation and restored as the current OTel Context at every subsequent turn so all turns
    // share ONE trace ID in SigNoz instead of fragmenting into one trace per platform POST.
    private String rootTraceparent;

    private TerminationReason terminationReason; // Why the session terminated (set on terminal path)
    private final List<Map<String, Object>> activityFeed = new CopyOnWriteArrayList<>();
    private final List<Map<String, Object>> childSessionSummaries = new CopyOnWriteArrayList<>();
    private final List<Map<String, Object>> childConversationFlows = new CopyOnWriteArrayList<>();

    public AgenticSession(String sessionId, List<AgenticToolDefinition> tools, String userMessage,
                         List<Map<String, Object>> messageHistory, int iterationCount,
                         Instant createdAt, Instant lastAccessAt, 
                         List<InputVariable> inputVariables, List<ExpectedOutput> expectedOutputs) {
        this.sessionId = sessionId;
        this.tools = tools;
        this.userMessage = userMessage;
        this.messageHistory = messageHistory != null ? messageHistory : new ArrayList<>();
        this.iterationCount = iterationCount;
        this.createdAt = createdAt;
        this.lastAccessAt = lastAccessAt;
        this.inputVariables = inputVariables;
        this.expectedOutputs = expectedOutputs;
    }

    /**
     * Adds a tool execution result to the message history.
     * Uses HashMap to tolerate null toolName and toolResult, which CAM may omit on tool error callbacks.
     * Success context is not stored separately — the LLM reads it directly from the toolResult payload.
     */
    public void addToolResult(String toolName, Object toolResult) {

        Map<String, Object> entry = new HashMap<>();
        entry.put(AgenticConstants.FIELD_ROLE, AgenticConstants.ROLE_TOOL);
        entry.put(AgenticConstants.FIELD_TOOL_NAME, toolName != null ? toolName : AgenticConstants.UNRESOLVED_TOOL_NAME);
        entry.put(AgenticConstants.FIELD_RESULT, toolResult != null ? toolResult : Map.of());
        entry.put(AgenticConstants.FIELD_TIMESTAMP, Instant.now().toString());
        messageHistory.add(entry);
        iterationCount++;
        lastAccessAt = Instant.now();
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public List<AgenticToolDefinition> getTools() {
        return tools;
    }

    public void setTools(List<AgenticToolDefinition> tools) {
        this.tools = tools;
    }

    public String getUserMessage() {
        return userMessage;
    }

    public void setUserMessage(String userMessage) {
        this.userMessage = userMessage;
    }

    public List<Map<String, Object>> getMessageHistory() {
        return messageHistory;
    }

    public void setMessageHistory(List<Map<String, Object>> messageHistory) {
        this.messageHistory = messageHistory;
    }

    public int getIterationCount() {
        return iterationCount;
    }

    public void setIterationCount(int iterationCount) {
        this.iterationCount = iterationCount;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getLastAccessAt() {
        return lastAccessAt;
    }

    public void setLastAccessAt(Instant lastAccessAt) {
        this.lastAccessAt = lastAccessAt;
    }

    public List<InputVariable> getInputVariables() {
        return inputVariables;
    }

    public void setInputVariables(List<InputVariable> inputVariables) {
        this.inputVariables = inputVariables;
    }

    public List<ExpectedOutput> getExpectedOutputs() {
        return expectedOutputs;
    }

    public void setExpectedOutputs(List<ExpectedOutput> expectedOutputs) {
        this.expectedOutputs = expectedOutputs;
    }

    public String getParentSessionId() {
        return parentSessionId;
    }

    public void setParentSessionId(String parentSessionId) {
        this.parentSessionId = parentSessionId;
    }

    public SessionUsageCollector getUsageCollector() {
        return usageCollector;
    }

    public void setUsageCollector(SessionUsageCollector usageCollector) {

        this.usageCollector = usageCollector;
    }

    public List<String> getPolicyBrief() {

        return policyBrief;
    }

    public void setPolicyBrief(List<String> policyBrief) {

        this.policyBrief = policyBrief;
    }

    public String getStepGoal() {

        return stepGoal;
    }

    public void setStepGoal(String stepGoal) {

        this.stepGoal = stepGoal;
    }

    public AgenticRequestContext getRequestContext() {
        return requestContext;
    }

    public void setRequestContext(AgenticRequestContext requestContext) {
        this.requestContext = requestContext;
    }

    public Instant getLastToolDispatchedAt() {
        return lastToolDispatchedAt;
    }

    public void setLastToolDispatchedAt(Instant lastToolDispatchedAt) {
        this.lastToolDispatchedAt = lastToolDispatchedAt;
    }

    public String getLastDispatchedToolName() {
        return lastDispatchedToolName;
    }

    public void setLastDispatchedToolName(String lastDispatchedToolName) {
        this.lastDispatchedToolName = lastDispatchedToolName;
    }

    public String getLastLlmSpanId() {
        return lastLlmSpanId;
    }

    public void setLastLlmSpanId(String lastLlmSpanId) {
        this.lastLlmSpanId = lastLlmSpanId;
    }

    public String getLastToolCallSpanId() {
        return lastToolCallSpanId;
    }

    public void setLastToolCallSpanId(String lastToolCallSpanId) {
        this.lastToolCallSpanId = lastToolCallSpanId;
    }

    public String getConversationId() {
        return conversationId;
    }

    public void setConversationId(String conversationId) {
        this.conversationId = conversationId;
    }

    public String getParentConversationId() {
        return parentConversationId;
    }

    public void setParentConversationId(String parentConversationId) {
        this.parentConversationId = parentConversationId;
    }

    public String getRootTraceparent() {
        return rootTraceparent;
    }

    public void setRootTraceparent(String rootTraceparent) {
        this.rootTraceparent = rootTraceparent;
    }

    /**
     * Gets the current session state.
     * @return ACTIVE or TERMINATED
     */
    public SessionState getState() {
        return state;
    }

    /**
     * Checks if session is terminated (COMPLETE or ERROR).
     * @return true if session is terminated
     */
    public boolean isTerminated() {
        return state == SessionState.TERMINATED;
    }

    /**
     * Marks session as terminated.
     * Called when session receives COMPLETE or ERROR response.
     * Only sets termination timestamp on first call - subsequent calls are no-ops.
     */
    public void markTerminated() {
        this.state = SessionState.TERMINATED;
        if (this.terminatedAt == null) {
            this.terminatedAt = Instant.now();
        }
    }

    /**
     * Gets the termination timestamp.
     * @return Instant when session was terminated, or null if still active
     */
    public Instant getTerminatedAt() {
        return terminatedAt;
    }

    public TerminationReason getTerminationReason() {
        return terminationReason;
    }

    public void setTerminationReason(TerminationReason terminationReason) {
        this.terminationReason = terminationReason;
    }

    /**
     * Appends a timestamped event to this session's activity feed. Typed {@link ActivityEvent}
     * variants ensure each event carries the right fields — call sites can't miss a key or
     * misspell an event-type string.
     */
    public void recordActivity(ActivityEvent event) {
        if (event == null) {
            return;
        }
        Map<String, Object> entry = new HashMap<>();
        entry.put(AgenticConstants.FIELD_TIMESTAMP, Instant.now().toString());
        entry.put("type", event.type());
        Map<String, Object> details = event.details();
        if (details != null && !details.isEmpty()) {
            entry.put("details", details);
        }
        activityFeed.add(entry);
    }

    /**
     * Returns an unmodifiable view of the activity feed. Order is insertion order.
     */
    public List<Map<String, Object>> getActivityFeed() {
        return Collections.unmodifiableList(activityFeed);
    }

    /**
     * Records a finished child session's summary entry on this parent. Called during
     * the child→parent rollup in {@code performSessionCleanup}.
     */
    public void recordChildSessionSummary(Map<String, Object> childSummary) {
        if (childSummary != null && !childSummary.isEmpty()) {
            childSessionSummaries.add(childSummary);
        }
    }

    /**
     * Returns an unmodifiable view of child-session summaries aggregated on this parent.
     */
    public List<Map<String, Object>> getChildSessionSummaries() {
        return Collections.unmodifiableList(childSessionSummaries);
    }

    /**
     * Records a finished child session's conversation flow on this parent, in completion
     * order, so it can be woven inline into the parent's {@code conversationHistory} at the
     * matching invoker-delegation turn (KR-20450). Captured during {@code performSessionCleanup}
     * while the child's chat memory is still live. Each entry is {@code {childSessionId, turns}},
     * where {@code turns} are rendered in the same shape as the parent's turns.
     */
    public void recordChildConversationFlow(String childSessionId, List<Map<String, Object>> turns) {
        if (turns == null || turns.isEmpty()) {
            return;
        }
        Map<String, Object> flow = new HashMap<>();
        flow.put("childSessionId", childSessionId);
        flow.put("turns", turns);
        childConversationFlows.add(flow);
    }

    /**
     * Returns an unmodifiable, completion-ordered view of child conversation flows captured
     * on this parent for inline interleaving.
     */
    public List<Map<String, Object>> getChildConversationFlows() {
        return Collections.unmodifiableList(childConversationFlows);
    }

    @Override
    public String toString() {
        return "AgenticSession{" +
                "sessionId='" + sessionId + '\'' +
                ", tools=" + tools +
                ", systemPrompt='" + userMessage + '\'' +
                ", messageHistorySize=" + messageHistory.size() +
                ", iterationCount=" + iterationCount +
                ", createdAt=" + createdAt +
                ", lastAccessAt=" + lastAccessAt +
                ", inputVariablesCount=" + (inputVariables != null ? inputVariables.size() : 0) +
                ", expectedOutputsCount=" + (expectedOutputs != null ? expectedOutputs.size() : 0) +
                '}';
    }
}
