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

package app.krista.extensions.hook.policybrief;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Represents the state of a policy brief generation job.
 *
 * <p>Instances are shared between the worker virtual thread (which writes status, policyBrief,
 * message, errorMessage, toolName, and toolArguments) and the polling catalog-request thread
 * (which reads them). All mutable fields are declared {@code volatile} to guarantee visibility
 * across threads without requiring synchronized blocks.</p>
 *
 * <p><b>Threading invariant:</b> Only one worker thread mutates a given job at a time.
 * {@link PolicyBriefService} enforces this via sequential handoff — the current worker thread
 * returns before a new one is spawned. {@code volatile} provides cross-thread visibility for
 * the polling reader, not concurrent-write safety. Non-atomic operations like {@code ++} on
 * volatile fields are safe under this single-writer guarantee.</p>
 */
public class PolicyBriefJob {

    private static final Logger log = LoggerFactory.getLogger(PolicyBriefJob.class);

    /**
     * Lifecycle status of a policy brief generation job.
     */
    public enum Status {

        /** Job has been created and is being processed. */
        PENDING,

        /** LLM produced an initial brief and is waiting for author feedback. */
        AWAITING_INPUT,

        /** LLM requests that the platform execute a tool (e.g., RAG search) on its behalf. */
        EXECUTE_TOOL,

        /** Author confirmed the brief; generation is complete. */
        COMPLETE,

        /** An unrecoverable error occurred during generation or refinement. */
        FAILED
    }

    private final String jobId;

    private volatile Status status;
    private volatile List<PolicyBriefRule> policyBriefRules;
    private volatile String message;
    private volatile String errorMessage;
    private volatile String toolName;
    private volatile String toolArguments;
    private volatile int executeToolCount;
    private volatile boolean seeded;
    private final AtomicInteger nextRuleId = new AtomicInteger(1);

    // Immutable context fields set at creation time
    private final String intent;
    private final String toolDefinitions;

    /**
     * Creates a new policy brief job with the given context.
     *
     * @param jobId            unique identifier for this job
     * @param status           initial status (typically {@link Status#PENDING})
     * @param intent           the step intent describing what the agent will do
     * @param toolDefinitions  JSON string of tool definitions available to the step
     */
    public PolicyBriefJob(String jobId,
                          Status status,
                          String intent,
                          String toolDefinitions) {

        this.jobId = jobId;
        this.status = status;
        this.intent = intent;
        this.toolDefinitions = toolDefinitions;
        this.policyBriefRules = List.of();
    }

    // ---- Getters ----

    public String getJobId() {

        return jobId;
    }

    public Status getStatus() {

        return status;
    }

    public List<String> getPolicyBrief() {

        return policyBriefRules.stream()
                .map(PolicyBriefRule::text)
                .toList();
    }

    public List<PolicyBriefRule> getPolicyBriefRules() {

        return policyBriefRules;
    }

    public String getMessage() {

        return message;
    }

    public String getErrorMessage() {

        return errorMessage;
    }

    public String getIntent() {

        return intent;
    }

    public String getToolDefinitions() {

        return toolDefinitions;
    }

    public String getToolName() {

        return toolName;
    }

    public String getToolArguments() {

        return toolArguments;
    }

    public int getExecuteToolCount() {

        return executeToolCount;
    }

    /**
     * Increments the EXECUTE_TOOL round counter and returns the new value.
     *
     * <p>Not safe for concurrent callers — relies on the single-writer guarantee
     * documented at the class level.</p>
     *
     * @return the counter value after incrementing
     */
    public int incrementExecuteToolCount() {

        return ++executeToolCount;
    }

    /**
     * Returns whether this job was seeded with an existing brief.
     *
     * <p>When {@code true}, the first {@code submitFeedback} call will prepend
     * the existing brief to the author's feedback before calling the LLM.</p>
     *
     * @return {@code true} if this job was seeded with a pre-existing brief
     */
    public boolean isSeeded() {

        return seeded;
    }

    /**
     * Sets the seeded flag for this job.
     *
     * @param seeded {@code true} if the job is seeded with a pre-existing brief
     */
    public void setSeeded(boolean seeded) {

        this.seeded = seeded;
    }

    // ---- Setters for mutable fields ----

    public void setStatus(Status status) {

        this.status = status;
    }

    public void setPolicyBrief(List<String> policyBrief) {

        this.policyBriefRules = createRules(policyBrief);
    }

    public void setPolicyBriefRules(List<PolicyBriefRule> policyBriefRules) {

        this.policyBriefRules = policyBriefRules != null ? List.copyOf(policyBriefRules) : List.of();
        updateNextRuleId(this.policyBriefRules);
    }

    public PolicyBriefRule createRule(String text) {

        return new PolicyBriefRule("R" + nextRuleId.getAndIncrement(), text);
    }

    public void setMessage(String message) {

        this.message = message;
    }

    public void setErrorMessage(String errorMessage) {

        this.errorMessage = errorMessage;
    }

    public void setToolName(String toolName) {

        this.toolName = toolName;
    }

    public void setToolArguments(String toolArguments) {

        this.toolArguments = toolArguments;
    }

    @Override
    public String toString() {

        return "PolicyBriefJob{" +
                "jobId='" + jobId + '\'' +
                ", status=" + status +
                ", policyBrief=" + (policyBriefRules != null ? policyBriefRules.size() + " rules" : "null") +
                ", message='" + message + '\'' +
                ", errorMessage='" + errorMessage + '\'' +
                ", toolName='" + toolName + '\'' +
                '}';
    }

    private List<PolicyBriefRule> createRules(List<String> policyBrief) {

        if (policyBrief == null || policyBrief.isEmpty()) {

            return List.of();
        }

        return policyBrief.stream()
                .map(this::createSeededRule)
                .toList();
    }

    private PolicyBriefRule createSeededRule(String text) {

        String normalized = PolicyBriefRuleTextNormalizer.normalizeVisibleRuleText(text);
        if (!Objects.equals(text, normalized)) {

            log.info("Normalized leaked policy brief rule prefix: jobId={}, operationType={}",
                    jobId, "set_policy_brief");
        }

        return createRule(normalized);
    }

    private void updateNextRuleId(List<PolicyBriefRule> rules) {

        int maxRuleNumber = rules.stream()
                .map(PolicyBriefRule::id)
                .mapToInt(this::extractRuleNumber)
                .max()
                .orElse(0);
        nextRuleId.updateAndGet(current -> Math.max(current, maxRuleNumber + 1));
    }

    private int extractRuleNumber(String ruleId) {

        if (ruleId == null || ruleId.length() < 2 || ruleId.charAt(0) != 'R') {

            return 0;
        }

        try {

            return Integer.parseInt(ruleId.substring(1));
        } catch (NumberFormatException e) {

            return 0;
        }
    }
}
