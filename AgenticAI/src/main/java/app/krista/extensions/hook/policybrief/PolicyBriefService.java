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

import app.krista.extension.executor.Invoker;
import app.krista.extensions.hook.util.JsonMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Core orchestration service for policy brief generation.
 *
 * <p>Manages the lifecycle of {@link PolicyBriefJob} instances: starting asynchronous
 * generation via a virtual thread, polling for results, and submitting author feedback
 * for iterative refinement.</p>
 *
 * <p>This service does NOT perform RAG queries directly. Instead, the LLM can request
 * policy document searches by returning an {@code EXECUTE_TOOL} response. The platform
 * layer detects this status during polling, executes the tool (e.g., AIQA search),
 * and submits the results back via {@link #submitFeedback(String, String)}.</p>
 *
 * <p>Tool definitions are split into two categories before being passed to the LLM:
 * <ul>
 *   <li><b>Step tools</b> — provided as context only so the LLM understands what the
 *       step does at runtime. The LLM must NOT invoke these.</li>
 *   <li><b>Search tool</b> ({@value #SEARCH_TOOL_NAME}) — the only tool the LLM may
 *       invoke via EXECUTE_TOOL to retrieve policy documents.</li>
 * </ul>
 * If the LLM returns an EXECUTE_TOOL response targeting a step tool instead of the
 * search tool, the service self-corrects by sending the error back to the LLM for retry.
 * The existing {@link #MAX_EXECUTE_TOOL_ROUNDS} limit prevents infinite retries.</p>
 *
 * <p>Thread safety: each job mutation runs on a dedicated virtual thread. The
 * {@link PolicyBriefJob} fields are {@code volatile}, ensuring visibility to the
 * polling thread without explicit synchronization.</p>
 */
public class PolicyBriefService {

    private static final Logger log = LoggerFactory.getLogger(PolicyBriefService.class);

    /**
     * The name of the search tool that the LLM is allowed to invoke via EXECUTE_TOOL.
     * All other tools in the tool definitions are step tools (context only).
     */
    private static final String SEARCH_TOOL_NAME = "search_policy_docset";
    private static final String TOOL_FAILURE_MARKER = "POLICY_BRIEF_TOOL_FAILURE:";
    private static final String NO_SEARCH_RESULTS_MARKER = "POLICY_BRIEF_NO_SEARCH_RESULTS:";
    private static final String TOOL_FAILURE_MESSAGE =
            "Policy document search failed. You can retry, revise your request, or continue with the current brief.";
    private static final String NO_SEARCH_RESULTS_MESSAGE =
            "No policy documents were found in the configured docset for this search. " +
                    "The brief was not updated from policy-doc search results.";

    /**
     * Maximum number of EXECUTE_TOOL rounds allowed per job. After this limit the extension
     * overrides the status to AWAITING_INPUT so the platform never sees another EXECUTE_TOOL.
     */
    private static final int MAX_EXECUTE_TOOL_ROUNDS = 3;

    /**
     * Default minimum relevance score for AIQA policy document searches.
     *
     * <p>This value is injected into the tool arguments so the platform can pass it
     * through to the AIQA catalog request. Keeping this on the extension side allows
     * tuning without a platform release cycle.</p>
     */
    private static final double DEFAULT_MIN_RELEVANCE_SCORE = 0.1;

    private final PolicyBriefJobStore jobStore;
    private final Invoker invoker;

    /**
     * Creates a new service instance.
     *
     * @param invoker the platform invoker used for LLM model creation via
     *                {@link PolicyBriefGeneratorFactory}
     */
    public PolicyBriefService(Invoker invoker) {

        this.jobStore = PolicyBriefJobStore.getInstance();
        this.invoker = invoker;
    }

    /**
     * Maximum number of rules accepted in a seeded existing brief before truncation.
     */
    private static final int MAX_SEED_RULES = 200;

    /**
     * Maximum serialized JSON size (in bytes) for a seeded existing brief before truncation.
     */
    private static final int MAX_SEED_BYTES = 50_000;

    private static final String EMPTY_COMPLETE_MESSAGE =
            "I do not have a draft yet. Please provide a replacement draft using replace_brief " +
                    "or search for additional policy documents.";

    private record PolicyBriefResponse(String status, List<PolicyBriefEditOperation> operations,
                                       String message, String toolName,
                                       Map<String, Object> arguments) {
    }

    private record PolicyBriefEditOperation(String op, String ruleId, String text,
                                            String afterRuleId, List<String> replacementRules) {
    }

    private record ToolFailureFeedback(String reason, String detail) {
    }

    private record NoSearchResultsFeedback(String reason, String detail) {
    }

    /**
     * Starts an asynchronous policy brief generation job.
     *
     * <p>A new {@link PolicyBriefJob} is created in {@code PENDING} status and stored
     * in the job store. A virtual thread is spawned to call the LLM, which will
     * typically return {@code EXECUTE_TOOL} requesting a policy document search.
     * The caller receives the job ID immediately and can poll via
     * {@link #getResult(String)}.</p>
     *
     * <p>Equivalent to {@link #startGeneration(String, String, List)} with an empty seed brief (cold-start path).</p>
     *
     * @param intent          the step intent describing what the agent will do
     * @param toolDefinitions JSON string of tool definitions available to the step
     * @return the unique job ID for polling
     */
    public String startGeneration(String intent, String toolDefinitions) {

        return startGeneration(intent, toolDefinitions, List.of());
    }

    /**
     * Starts an asynchronous policy brief generation job, optionally seeded with an existing brief.
     *
     * <p>When {@code existingBrief} is non-empty, the LLM session starts pre-anchored on that
     * brief. The first poll will immediately return the seed rules (pre-population for UX),
     * and the LLM is instructed to wait for author input before making changes.</p>
     *
     * @param intent          the step intent describing what the agent will do
     * @param toolDefinitions JSON string of tool definitions available to the step
     * @param existingBrief   previously saved brief rules to seed the session with; empty list for cold start
     * @return the unique job ID for polling
     */
    public String startGeneration(String intent, String toolDefinitions, List<String> existingBrief) {

        String jobId = UUID.randomUUID().toString();

        PolicyBriefJob job = new PolicyBriefJob(
                jobId,
                PolicyBriefJob.Status.PENDING,
                intent,
                toolDefinitions
        );

        // Apply size guard and seed the job before deciding the execution path
        List<String> safeBrief = applySizeGuard(existingBrief);
        if (!safeBrief.isEmpty()) {

            job.setPolicyBrief(safeBrief);
        }

        jobStore.put(jobId, job);

        if (!safeBrief.isEmpty()) {

            // Seeded path: skip LLM, go directly to AWAITING_INPUT
            try {

                splitToolDefinitions(job.getToolDefinitions());
            } catch (Exception e) {

                log.error("Seeded job tool definition validation failed: jobId={}", jobId, e);
                job.setStatus(PolicyBriefJob.Status.FAILED);
                job.setErrorMessage("Tool definition validation failed: " + e.getMessage());
                return jobId;
            }

            job.setSeeded(true);
            int ruleCount = safeBrief.size();
            job.setStatus(PolicyBriefJob.Status.AWAITING_INPUT);
            job.setMessage("Here is your existing policy brief with "
                    + (ruleCount == 1 ? "1 rule" : ruleCount + " rules")
                    + ". Tell me what changes you'd like, or say 'looks good' to confirm.");

            log.info("Seeded job skipping LLM — direct AWAITING_INPUT: jobId={}, ruleCount={}",
                    jobId, ruleCount);

            return jobId;
        }

        // Cold-start path: spawn virtual thread → generateBriefAsync
        log.info("Policy brief generation started: jobId={}, seeded=false, seedRuleCount=0", jobId);

        Thread.startVirtualThread(() -> generateBriefAsync(job));

        return jobId;
    }

    /**
     * Applies the size guard to an existing brief: truncates to {@value #MAX_SEED_RULES} rules
     * and/or {@value #MAX_SEED_BYTES} serialized bytes, whichever limit is hit first.
     *
     * @param brief the incoming brief rules
     * @return the (possibly truncated) brief, or the original if within limits
     */
    private List<String> applySizeGuard(List<String> brief) {

        if (brief == null || brief.isEmpty()) {

            return List.of();
        }

        List<String> result = brief;

        if (result.size() > MAX_SEED_RULES) {

            log.warn("Existing brief has {} rules, truncating to {} for seed",
                    result.size(), MAX_SEED_RULES);
            result = List.copyOf(result.subList(0, MAX_SEED_RULES));
        }

        try {

            String serialized = JsonMapper.getInstance().writeValueAsString(result);
            if (serialized.getBytes(StandardCharsets.UTF_8).length > MAX_SEED_BYTES) {

                log.warn("Existing brief serialized size exceeds {} bytes, truncating rules", MAX_SEED_BYTES);
                // Iteratively remove from the end until under the limit
                List<String> truncated = new ArrayList<>(result);
                while (truncated.size() > 1) {

                    truncated.removeLast();
                    String s = JsonMapper.getInstance().writeValueAsString(truncated);
                    if (s.getBytes(StandardCharsets.UTF_8).length <= MAX_SEED_BYTES) {

                        return List.copyOf(truncated);
                    }
                }
                return List.copyOf(truncated);
            }

        } catch (Exception e) {

            log.warn("Failed to check serialized size of existing brief, proceeding with rule-count guard only", e);
        }

        return result;
    }

    /**
     * Retrieves the current state of a policy brief job.
     *
     * @param jobId the job identifier
     * @return the job, or {@code null} if not found or expired
     */
    public PolicyBriefJob getResult(String jobId) {

        return jobStore.get(jobId);
    }

    /**
     * Submits author feedback or tool execution results for an existing job,
     * triggering a refinement round.
     *
     * <p>The job status is set back to {@code PENDING} and a virtual thread is spawned
     * to call the LLM with the provided input in the context of the existing
     * conversation memory. The input may be either:</p>
     * <ul>
     *   <li>Author feedback text (e.g., "Remove point 5")</li>
     *   <li>RAG results from a tool execution (platform submits these after EXECUTE_TOOL)</li>
     * </ul>
     *
     * @param jobId  the job identifier
     * @param answer the author's feedback or tool execution results
     * @throws IllegalArgumentException if the job is not found
     */
    public void submitFeedback(String jobId, String answer) {

        PolicyBriefJob job = jobStore.get(jobId);
        if (job == null) {

            throw new IllegalArgumentException("No policy brief job found for jobId: " + jobId);
        }

        boolean wasToolResult = job.getStatus() == PolicyBriefJob.Status.EXECUTE_TOOL;

        // Clear any previous tool request fields when re-entering PENDING
        job.setToolName(null);
        job.setToolArguments(null);

        if (wasToolResult) {

            NoSearchResultsFeedback noSearchResults = parseNoSearchResultsFeedback(answer);
            if (noSearchResults != null) {

                job.setStatus(PolicyBriefJob.Status.AWAITING_INPUT);
                job.setMessage(NO_SEARCH_RESULTS_MESSAGE);
                job.setErrorMessage(noSearchResults.detail());
                log.info("Policy brief tool execution returned no policy documents: jobId={}, reason={}, detail={}",
                        jobId, noSearchResults.reason(), noSearchResults.detail());
                return;
            }

            ToolFailureFeedback toolFailure = parseToolFailureFeedback(answer);
            if (toolFailure != null) {

                job.setStatus(PolicyBriefJob.Status.AWAITING_INPUT);
                job.setMessage(TOOL_FAILURE_MESSAGE);
                job.setErrorMessage(toolFailure.detail());
                log.warn("Policy brief tool execution failed: jobId={}, reason={}, detail={}",
                        jobId, toolFailure.reason(), toolFailure.detail());
                return;
            }
        }

        job.setStatus(PolicyBriefJob.Status.PENDING);
        job.setErrorMessage(null);
        log.info("Policy brief feedback submitted: jobId={}, seeded={}", jobId, job.isSeeded());

        Thread.startVirtualThread(() -> refineBriefAsync(job, answer, true, wasToolResult));
    }

    // ---- Tool definition splitting ----

    /**
     * Holds the result of splitting tool definitions into step tools (context only)
     * and the search tool (invocable).
     *
     * @param stepTools  JSON array string of step tool definitions (reference only for the LLM)
     * @param searchTool JSON object string of the search tool definition (invocable via EXECUTE_TOOL)
     */
    private record SplitTools(String stepTools, String searchTool) {

    }

    /**
     * Splits the combined tool definitions JSON array into step tools and the search tool.
     *
     * <p>Parses the JSON array of tool definitions, finds the tool whose {@code name} field
     * equals {@value #SEARCH_TOOL_NAME}, and separates it from the remaining step tools.</p>
     *
     * @param toolDefinitionsJson JSON array string containing all tool definitions
     * @return a {@link SplitTools} record with separated step tools and search tool
     * @throws IllegalStateException if the search tool is not found in the definitions
     */
    private SplitTools splitToolDefinitions(String toolDefinitionsJson) {

        try {

            ObjectMapper mapper = JsonMapper.getInstance();
            JsonNode root = mapper.readTree(toolDefinitionsJson);

            if (!root.isArray()) {

                throw new IllegalStateException(
                        "Expected tool definitions to be a JSON array but got: " + root.getNodeType());
            }

            ArrayNode allTools = (ArrayNode) root;
            ArrayNode stepTools = mapper.createArrayNode();
            JsonNode searchTool = null;
            List<String> availableToolNames = new ArrayList<>();

            for (JsonNode tool : allTools) {

                JsonNode nameNode = tool.path("name");
                String toolName = nameNode.isTextual() ? nameNode.asText() : "(unnamed)";
                availableToolNames.add(toolName);

                if (SEARCH_TOOL_NAME.equals(toolName)) {

                    searchTool = tool;
                } else {

                    stepTools.add(tool);
                }
            }

            if (searchTool == null) {

                throw new IllegalStateException(
                        "Required search tool '" + SEARCH_TOOL_NAME + "' not found in tool definitions. " +
                                "The platform must include a tool named '" + SEARCH_TOOL_NAME +
                                "' in the tool definitions. Available tools: " + availableToolNames);
            }

            return new SplitTools(
                    mapper.writeValueAsString(stepTools),
                    mapper.writeValueAsString(searchTool)
            );

        } catch (IllegalStateException e) {

            // Re-throw our own validation errors as-is
            throw e;

        } catch (Exception e) {

            throw new IllegalStateException(
                    "Failed to parse tool definitions JSON: " + e.getMessage(), e);
        }
    }

    // ---- Async workers ----

    /**
     * Performs the initial policy brief generation on a virtual thread.
     * Calls the LLM directly -- the LLM will use EXECUTE_TOOL if it needs policy documents.
     */
    private void generateBriefAsync(PolicyBriefJob job) {

        try {

            PolicyBriefGenerator generator = PolicyBriefGeneratorFactory.getInstance(invoker);
            SplitTools split = splitToolDefinitions(job.getToolDefinitions());

            // Cold-start path only — seeded jobs skip generateBriefAsync entirely
            String ragResults = "(No policy documents retrieved yet. Use EXECUTE_TOOL to search for relevant policy documents.)";
            String userInput = "Generate a policy brief based on the step intent and tool definitions. " +
                    "Search for relevant policy documents first using EXECUTE_TOOL.";

            String llmResponse = generator.generateOrRefine(
                    job.getJobId(),
                    ragResults,
                    job.getIntent(),
                    split.stepTools(),
                    split.searchTool(),
                    userInput
            );

            applyLlmResponse(job, llmResponse, true,
                    "generate the brief with available information.");
            log.info("Policy brief initial LLM call complete: jobId={}, status={}", job.getJobId(), job.getStatus());

            if (job.getStatus() == PolicyBriefJob.Status.COMPLETE) {

                PolicyBriefGeneratorFactory.cleanupMemory(job.getJobId());
            }

        } catch (Exception e) {

            log.error("Policy brief generation failed: jobId={}", job.getJobId(), e);
            job.setStatus(PolicyBriefJob.Status.FAILED);
            job.setErrorMessage("Generation failed: " + e.getMessage());
        }
    }

    /**
     * Performs a refinement round on a virtual thread using author feedback or tool results.
     * Re-uses the existing conversation memory so the LLM has full context.
     */
    private void refineBriefAsync(PolicyBriefJob job, String answer, boolean allowSelfCorrection,
                                  boolean wasToolResult) {

        boolean wasSeeded = job.isSeeded();
        try {

            // Clear seeded flag immediately — ChatMemory will record this turn
            if (wasSeeded) {

                job.setSeeded(false);
            }

            PolicyBriefGenerator generator = PolicyBriefGeneratorFactory.getInstance(invoker);
            SplitTools split = splitToolDefinitions(job.getToolDefinitions());

            // Suppress proactive EXECUTE_TOOL (RULE 1) on the seeded first turn;
            // leave RULE 3 ("implies missing policy areas") fully in control of search decisions
            String ragResults = wasSeeded
                    ? "Policy documents provided by the author — see user input below."
                    : "";
            String refinementInput = buildRefinementInput(job, answer, wasSeeded, wasToolResult);

            String llmResponse = generator.generateOrRefine(
                    job.getJobId(),
                    ragResults,
                    job.getIntent(),
                    split.stepTools(),
                    split.searchTool(),
                    refinementInput
            );

            applyLlmResponse(job, llmResponse, allowSelfCorrection,
                    "update the brief with available information.");
            log.info("Policy brief refined: jobId={}, status={}, wasSeeded={}",
                    job.getJobId(), job.getStatus(), wasSeeded);

            // If the job is complete, clean up chat memory to free resources
            if (job.getStatus() == PolicyBriefJob.Status.COMPLETE) {

                PolicyBriefGeneratorFactory.cleanupMemory(job.getJobId());
            }

        } catch (Exception e) {

            log.error("Policy brief refinement failed: jobId={}", job.getJobId(), e);
            job.setStatus(PolicyBriefJob.Status.FAILED);
            job.setErrorMessage("Refinement failed: " + e.getMessage());
        }
    }

    // ---- Seeded brief formatting ----

    /**
     * Builds the composite user input for the first feedback on a seeded job.
     *
     * <p>Prepends the existing brief as a numbered list so the LLM can reference
     * rules by number, then appends the author's feedback in XML-tagged format
     * for unambiguous structural separation.</p>
     *
     * @param job            the seeded job containing the existing brief
     * @param authorFeedback the author's first feedback text
     * @return the composite input string with brief context + author feedback
     */
    private String buildRefinementInput(PolicyBriefJob job, String authorFeedback, boolean wasSeeded,
                                        boolean wasToolResult) {

        StringBuilder sb = new StringBuilder();
        List<PolicyBriefRule> rules = job.getPolicyBriefRules();

        sb.append("CURRENT POLICY BRIEF WITH RULE IDS (")
                .append(rules.size() == 1 ? "1 rule" : rules.size() + " rules")
                .append("):\n<current_policy_brief>\n");

        for (PolicyBriefRule rule : rules) {

            sb.append("- ")
                    .append(rule.id())
                    .append(": ")
                    .append(rule.text().replace('\n', ' '))
                    .append('\n');
        }

        sb.append("</current_policy_brief>\n\n");
        if (wasSeeded) {

            sb.append("NOTE: This was a seeded brief. Preserve unaffected rules unless the feedback explicitly changes them.\n\n");
        }

        if (wasToolResult) {

            sb.append("LATEST SEARCH RESULTS:\n<tool_results>\n")
                    .append(authorFeedback)
                    .append("\n</tool_results>");
        } else {

            sb.append("LATEST AUTHOR FEEDBACK:\n<feedback>\n")
                    .append(authorFeedback)
                    .append("\n</feedback>");
        }

        return sb.toString();
    }

    // ---- Response parsing ----

    /**
     * Parses the LLM JSON response and updates the job state accordingly.
     *
     * <p>Expected JSON structures:
     * <pre>
     * // Brief generation or refinement:
     * {
     *   "status": "AWAITING_INPUT" | "COMPLETE",
     *   "operations": [{"op":"replace_brief","replacementRules":["rule1", "rule2"]}],
     *   "message": "conversational message"
     * }
     *
     * // Tool execution request:
     * {
     *   "status": "EXECUTE_TOOL",
     *   "toolName": "search_policy_docset",
     *   "arguments": {"Question": "search query"}
     * }
     * </pre>
     * </p>
     *
     * <p>If the LLM returns EXECUTE_TOOL with a tool name other than {@value #SEARCH_TOOL_NAME},
     * the response is rejected and the LLM is asked to self-correct via a refinement round.
     * The existing {@link #MAX_EXECUTE_TOOL_ROUNDS} limit prevents infinite retries.</p>
     */
    private void applyLlmResponse(PolicyBriefJob job, String llmResponse, boolean allowSelfCorrection,
                                  String fallbackInstruction) {

        try {

            String cleaned = cleanJsonResponse(llmResponse);
            PolicyBriefResponse response = JsonMapper.getInstance()
                    .readValue(cleaned, PolicyBriefResponse.class);

            if (handleExecuteToolResponse(job, response.status(), response.toolName(), response.arguments(),
                    fallbackInstruction, allowSelfCorrection)) {

                return;
            }

            List<PolicyBriefRule> updatedRules = applyOperations(job, response);
            job.setToolName(null);
            job.setToolArguments(null);
            job.setErrorMessage(null);
            job.setPolicyBriefRules(updatedRules);
            PolicyBriefJob.Status nextStatus = parseNonToolStatus(response.status());
            if (nextStatus == PolicyBriefJob.Status.COMPLETE && updatedRules.isEmpty()) {

                job.setStatus(PolicyBriefJob.Status.AWAITING_INPUT);
                job.setMessage(EMPTY_COMPLETE_MESSAGE);
            } else {

                job.setStatus(nextStatus);
                if (response.message() != null) {

                    job.setMessage(response.message());
                }
            }

            log.info("Applied policy brief operations: jobId={}, opCount={}, resultingRuleCount={}, ops={}",
                    job.getJobId(),
                    response.operations() != null ? response.operations().size() : 0,
                    updatedRules.size(),
                    summarizeOperations(response.operations()));

        } catch (Exception e) {

            log.warn("Invalid policy brief response for jobId={}: {}. Raw response: {}",
                    job.getJobId(), e.getMessage(), llmResponse);
            if (allowSelfCorrection) {

                job.setToolName(null);
                job.setToolArguments(null);
                job.setStatus(PolicyBriefJob.Status.PENDING);
                Thread.startVirtualThread(() -> {
                    try {
                        refineBriefAsync(job,
                                "ERROR: Your policy brief response was invalid. Return valid JSON with status, " +
                                        "optional message, and structured operations only. Use replace_brief for " +
                                        "first-draft creation from an empty brief. Validation error: " + e.getMessage(),
                                false,
                                false);
                    } catch (Exception retryFailure) {
                        finalizeRefinementFailure(job, retryFailure.getMessage());
                    }
                });
                return;
            }

            finalizeRefinementFailure(job, e.getMessage());
        }
    }

    private String cleanJsonResponse(String llmResponse) {

        String cleaned = llmResponse.strip();
        if (cleaned.startsWith("```")) {

            cleaned = cleaned.replaceFirst("```(?:json)?\\s*", "");
            if (cleaned.endsWith("```")) {

                cleaned = cleaned.substring(0, cleaned.length() - 3).strip();
            }
        }
        return cleaned;
    }

    private boolean handleExecuteToolResponse(PolicyBriefJob job, String statusStr, String toolName,
                                              Map<String, Object> arguments, String fallbackInstruction,
                                              boolean allowSelfCorrection) throws Exception {

        if (!"EXECUTE_TOOL".equalsIgnoreCase(statusStr)) {

            return false;
        }

        if (job.getExecuteToolCount() >= MAX_EXECUTE_TOOL_ROUNDS) {

            log.warn("EXECUTE_TOOL limit reached ({}) for jobId={}. Overriding to AWAITING_INPUT.",
                    MAX_EXECUTE_TOOL_ROUNDS, job.getJobId());
            job.setStatus(PolicyBriefJob.Status.AWAITING_INPUT);
            job.setMessage("Maximum policy document search attempts reached. " +
                    "Here is the best brief I can generate with the information available.");
            return true;
        }

        job.incrementExecuteToolCount();

        if (toolName == null || toolName.isBlank()) {

            throw new IllegalArgumentException("EXECUTE_TOOL responses must include toolName");
        }

        if (!SEARCH_TOOL_NAME.equals(toolName)) {

            if (!allowSelfCorrection) {

                throw new IllegalArgumentException(
                        "EXECUTE_TOOL may only target '" + SEARCH_TOOL_NAME + "', but got '" + toolName + "'");
            }

            log.warn("LLM tried to invoke '{}' instead of '{}' for jobId={}",
                    toolName, SEARCH_TOOL_NAME, job.getJobId());
            job.setToolName(null);
            job.setToolArguments(null);
            job.setStatus(PolicyBriefJob.Status.PENDING);
            Thread.startVirtualThread(() -> refineBriefAsync(job,
                    "ERROR: You tried to invoke '" + toolName + "' but you can ONLY invoke '" +
                            SEARCH_TOOL_NAME + "'. Please use the correct search tool or " + fallbackInstruction,
                    false,
                    false));
            return true;
        }

        if (arguments == null) {

            arguments = Map.of();
        }

        Map<String, Object> argsMap = new LinkedHashMap<>(arguments);
        argsMap.putIfAbsent("Minimum relevance score", DEFAULT_MIN_RELEVANCE_SCORE);
        job.setToolName(toolName);
        job.setToolArguments(JsonMapper.getInstance().writeValueAsString(argsMap));
        job.setStatus(PolicyBriefJob.Status.EXECUTE_TOOL);

        log.info("LLM requested tool execution: jobId={}, toolName={}, executeToolCount={}",
                job.getJobId(), job.getToolName(), job.getExecuteToolCount());
        return true;
    }

    private PolicyBriefJob.Status parseNonToolStatus(String statusStr) {

        if ("COMPLETE".equalsIgnoreCase(statusStr)) {

            return PolicyBriefJob.Status.COMPLETE;
        }
        return PolicyBriefJob.Status.AWAITING_INPUT;
    }

    private List<PolicyBriefRule> applyOperations(PolicyBriefJob job, PolicyBriefResponse response) {

        List<PolicyBriefRule> working = new ArrayList<>(job.getPolicyBriefRules());
        List<PolicyBriefEditOperation> operations = response.operations();

        if (operations == null || operations.isEmpty()) {

            return List.copyOf(working);
        }

        if (working.isEmpty() && operations.stream().filter(Objects::nonNull)
                .map(PolicyBriefEditOperation::op)
                .noneMatch("replace_brief"::equals)) {

            throw new IllegalArgumentException(
                    "An empty policy brief must be created with replace_brief");
        }

        for (PolicyBriefEditOperation operation : operations) {

            if (operation == null || operation.op() == null || operation.op().isBlank()) {

                throw new IllegalArgumentException("Each refinement operation must declare a non-empty op field");
            }

            switch (operation.op()) {
                case "add_rule" -> applyAddRule(job, working, operation);
                case "update_rule" -> applyUpdateRule(job, working, operation);
                case "delete_rule" -> applyDeleteRule(working, operation);
                case "replace_brief" -> working = applyReplaceBrief(job, operation);
                default -> throw new IllegalArgumentException("Unsupported refinement op: " + operation.op());
            }
        }

        return List.copyOf(working);
    }

    private void applyAddRule(PolicyBriefJob job, List<PolicyBriefRule> working, PolicyBriefEditOperation operation) {

        String text = requireNonBlank(operation.text(), "add_rule requires non-empty text");
        PolicyBriefRule newRule = job.createRule(normalizeVisibleRuleText(job.getJobId(), "add_rule", text.strip()));
        if (operation.afterRuleId() == null || operation.afterRuleId().isBlank()) {

            working.add(newRule);
            return;
        }

        int anchorIndex = findRuleIndex(working, operation.afterRuleId());
        working.add(anchorIndex + 1, newRule);
    }

    private void applyUpdateRule(PolicyBriefJob job, List<PolicyBriefRule> working, PolicyBriefEditOperation operation) {

        String ruleId = requireNonBlank(operation.ruleId(), "update_rule requires ruleId");
        String text = requireNonBlank(operation.text(), "update_rule requires non-empty text");
        int index = findRuleIndex(working, ruleId);
        working.set(index, new PolicyBriefRule(ruleId,
                normalizeVisibleRuleText(job.getJobId(), "update_rule", text.strip())));
    }

    private void applyDeleteRule(List<PolicyBriefRule> working, PolicyBriefEditOperation operation) {

        String ruleId = requireNonBlank(operation.ruleId(), "delete_rule requires ruleId");
        int index = findRuleIndex(working, ruleId);
        working.remove(index);
    }

    private List<PolicyBriefRule> applyReplaceBrief(PolicyBriefJob job, PolicyBriefEditOperation operation) {

        List<String> replacementRules = operation.replacementRules();
        if (replacementRules == null || replacementRules.isEmpty()) {

            throw new IllegalArgumentException("replace_brief requires a non-empty replacementRules array");
        }

        List<String> strippedRules = replacementRules.stream()
                .map(rule -> requireNonBlank(rule, "replace_brief rules must be non-empty").strip())
                .toList();

        List<String> normalizedRules = strippedRules.stream()
                .map(PolicyBriefRuleTextNormalizer::normalizeVisibleRuleText)
                .toList();

        if (!strippedRules.equals(normalizedRules)) {

            log.info("Normalized leaked policy brief rule prefix: jobId={}, operationType={}",
                    job.getJobId(), "replace_brief");
        }

        return normalizedRules.stream()
                .map(job::createRule)
                .toList();
    }

    private int findRuleIndex(List<PolicyBriefRule> working, String ruleId) {

        for (int i = 0; i < working.size(); i++) {

            if (working.get(i).id().equals(ruleId)) {

                return i;
            }
        }
        return throwMissingRule(ruleId);
    }

    private int throwMissingRule(String ruleId) {

        throw new IllegalArgumentException("Unknown ruleId referenced in refinement op: " + ruleId);
    }

    private String requireNonBlank(String value, String message) {

        if (value == null || value.isBlank()) {

            throw new IllegalArgumentException(message);
        }
        return value;
    }

    private String normalizeVisibleRuleText(String jobId, String operationType, String text) {

        String normalized = PolicyBriefRuleTextNormalizer.normalizeVisibleRuleText(text);
        if (!Objects.equals(text, normalized)) {

            log.info("Normalized leaked policy brief rule prefix: jobId={}, operationType={}",
                    jobId, operationType);
        }
        return normalized;
    }

    private ToolFailureFeedback parseToolFailureFeedback(String answer) {

        SyntheticToolFeedback feedback = parseSyntheticToolFeedback(answer, TOOL_FAILURE_MARKER);
        if (feedback == null) {

            return null;
        }

        return new ToolFailureFeedback(feedback.reason(), feedback.detail());
    }

    private NoSearchResultsFeedback parseNoSearchResultsFeedback(String answer) {

        SyntheticToolFeedback feedback = parseSyntheticToolFeedback(answer, NO_SEARCH_RESULTS_MARKER);
        if (feedback == null) {

            return null;
        }

        return new NoSearchResultsFeedback(feedback.reason(), feedback.detail());
    }

    private record SyntheticToolFeedback(String reason, String detail) {
    }

    private SyntheticToolFeedback parseSyntheticToolFeedback(String answer, String marker) {

        if (answer == null || !answer.startsWith(marker)) {

            return null;
        }

        String reason = answer.substring(marker.length()).strip();
        String detail = reason;

        for (String line : answer.split("\\R")) {

            if (!line.startsWith("detail=")) {

                continue;
            }

            String parsed = line.substring("detail=".length()).strip();
            if (!parsed.isBlank()) {

                detail = parsed;
            }
        }

        return new SyntheticToolFeedback(reason.isBlank() ? "tool execution failed" : reason, detail);
    }

    private void finalizeRefinementFailure(PolicyBriefJob job, String errorMessage) {

        job.setToolName(null);
        job.setToolArguments(null);
        job.setStatus(PolicyBriefJob.Status.AWAITING_INPUT);
        job.setMessage("I could not produce a valid policy brief response. Please try again or rephrase your request.");
        job.setErrorMessage("Policy brief validation error: " + errorMessage);
    }

    private String summarizeOperations(List<PolicyBriefEditOperation> operations) {

        if (operations == null || operations.isEmpty()) {

            return "none";
        }

        return operations.stream()
                .map(op -> op.op() + (op.ruleId() != null ? ":" + op.ruleId() : ""))
                .toList()
                .toString();
    }
}
