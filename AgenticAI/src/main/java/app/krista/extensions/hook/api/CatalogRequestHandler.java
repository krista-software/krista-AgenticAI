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
import app.krista.extensions.hook.agentic.telemetry.AgenticRequestContext;
import app.krista.extensions.hook.agentic.telemetry.AgenticSpans;
import app.krista.extensions.hook.agentic.telemetry.AgenticStepContext;
import app.krista.extensions.hook.agentic.telemetry.AgenticTracerProvider;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Scope;
import app.krista.extensions.hook.common.OrchestrationContext;
import app.krista.extensions.hook.factory.StepHandlerFactory;
import app.krista.extensions.hook.stepHandlers.StepExecutionHandler;
import app.krista.extensions.hook.helper.ActionParser;
import app.krista.extensions.hook.helper.RequestLogger;
import app.krista.extensions.hook.handler.IntentClassificationHandler;
import app.krista.extensions.hook.common.models.StepMetadata;
import app.krista.extensions.hook.policybrief.PolicyBriefJob;
import app.krista.extensions.hook.policybrief.PolicyBriefService;
import app.krista.extensions.hook.preprocessor.FormMessagePreprocessor;
import app.krista.extensions.hook.common.LanguageCode;
import app.krista.extensions.hook.util.JsonSanitizer;
import app.krista.extensions.hook.util.JsonMapper;
import app.krista.extensions.util.KeyValueStore;
import app.krista.ksdk.workspace.Workspace;
import app.krista.model.base.FreeForm;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Domain(id = "catEntryDomain_66277b92-e35f-4838-a41d-f5e4785eb2c9",
        name = "AgenticAI",
        ecosystemId = "catEntryEcosystem_2141418b-7dd8-4c04-865e-8763ba9aadcf",
        ecosystemName = "AI",
        ecosystemVersion = "4f61efe0-8b50-4ef7-a841-1334a1fe1e03")
public class CatalogRequestHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(CatalogRequestHandler.class);

    private final StepHandlerFactory stepHandlerFactory;
    private final RequestLogger requestLogger;
    private final Invoker invoker;
    private final Workspace workspace;

    @Inject
    public CatalogRequestHandler(Invoker invoker, KeyValueStore keyValueStore, Workspace workspace) {

        this.stepHandlerFactory = new StepHandlerFactory(keyValueStore);
        this.requestLogger = new RequestLogger();
        this.invoker = invoker;
        this.workspace = workspace;
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
            @Field.Desc(name = "AgentInstructions", type = "{ AgentPurpose: Text, PrimaryUsers: [ Text ], InteractionType: [ Text ], ToneStyle: Text, SpecialInstructions: Text }",required = true) Map<String, Object> agentInstructions,
            @Field.Text(name = "ToolDefinitions", required = false, attributes = {@Attribute(name = "visualWidth", value = "S")}, options = {}) String toolDefinitions,
            @Field.Text(name = "SessionId", required = false, attributes = {@Attribute(name = "visualWidth", value = "S")}, options = {}) String sessionId,
            @Field.Text(name = "PreviousToolName", required = false, attributes = {@Attribute(name = "visualWidth", value = "S")}, options = {}) String previousToolName,
            @Field(name = "PreviousToolResult", type = "Paragraph", required = false, attributes = {@Attribute(name = "visualWidth", value = "L")}, options = {}) String previousToolResult,
            @Field.Boolean(name = "GenerateSummary", required = true, attributes = {@Attribute(name = "visualWidth", value = "S")}, options = {}) Boolean generateSummary,
            @Field.Text(name = "InputVariables", required = true, attributes = {@Attribute(name = "visualWidth", value = "S")}, options = {}) String inputVariables,
            @Field.Text(name = "ExpectedOutputs", required = true, attributes = {@Attribute(name = "visualWidth", value = "S")}, options = {}) String expectedOutputs,
            @Field.Text(name = "ExecutionRules", required = true, attributes = {@Attribute(name = "visualWidth", value = "S")}, options = {}) String executionRules,
            @Field.Text(name = "Prompt", required = true, attributes = {@Attribute(name = "visualWidth", value = "S")}, options = {}) String prompt,
            @Field.Text(name = "PolicyBrief", required = false, attributes = {@Attribute(name = "visualWidth", value = "S")}, options = {}) String policyBrief) {

        // Log all request parameters first, before any processing
        requestLogger.logRequestParameters(currentVariables, currentVariablesDefault, action, formMessage, userMessage);

        // Capture workspace/invoker identity once for telemetry dimensions; propagated to
        // session-creation code via a ThreadLocal and cleared in the finally block so it
        // never leaks to the next request on a pooled thread.
        AgenticRequestContext requestContext = AgenticRequestContext.from(invoker, workspace);
        AgenticRequestContext.setCurrent(requestContext);

        // Parse step metadata up front and set the step-context ThreadLocal before the request
        // span so its conversation/step attributes are available to stamp on the span.
        StepMetadata stepMetadata = null;
        try {
            stepMetadata = StepMetadata.fromMap(metaData);
        } catch (Exception e) {
            LOGGER.debug("Could not parse step metadata for telemetry: {}", e.getMessage());
        }
        AgenticStepContext.setCurrent(AgenticStepContext.from(stepMetadata));

        // Structural span for the extension request. Inherits Context.current() (the platform's
        // auto-instrumented POST/HTTP server span) so it nests under that POST and shares the
        // platform's trace — which is exported reliably by the platform's own instrumentation.
        Span requestSpan = AgenticSpans.builder("request.enhance")
                .setAttribute(AgenticSpans.OPERATION, "request.enhance")
                .setAttribute(AgenticSpans.ACTION, action != null ? action : "")
                .startSpan();
        AgenticSpans.putRequestContext(requestSpan, requestContext);
        AgenticSpans.putStepContext(requestSpan, AgenticStepContext.getCurrent());

        try (Scope ignored = requestSpan.makeCurrent()) {
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

            // StepMetadata was parsed and the step-context ThreadLocal set above (before the
            // request span) so request.enhance could join the conversation trace.
            System.out.println("Created StepMetadata: " + stepMetadata);

            // Create orchestration context
            OrchestrationContext orchestrationContext;
            if (agentAction == AgentAction.ORCHESTRATE) {
                // Parse previousToolResult from JSON string to Object
                Object parsedToolResult = parseToolResult(previousToolResult);

                // Parse expectedOutputs from JSON string to List
                List<Map<String, Object>> parsedExpectedOutputs = parseExpectedOutputs(expectedOutputs);

                // Parse inputVariables from JSON string to List
                List<Map<String, Object>> parsedInputVariables = parseInputVariables(inputVariables);

                // Parse toolDefinitions from JSON string (matches InputVariables/ExpectedOutputs pattern)
                List<Map<String, Object>> parsedToolDefinitions = parseToolDefinitions(toolDefinitions);

                // Parse policy brief from JSON string
                List<String> parsedPolicyBrief = parsePolicyBrief(policyBrief);

                // Dev-time convenience: embed policy rules inline in the Prompt field using the delimiter
                // ===Policy Brief===
                // Rule one
                // Rule two
                // The text before the delimiter is used as the actual goal; rules below become the policyBrief.
                // Ignored when a policyBrief is already supplied via the attribute (attribute takes precedence).
                String effectivePrompt = prompt;
                if (parsedPolicyBrief.isEmpty() && prompt != null && prompt.contains("===Policy Brief===")) {

                    String[] parts = prompt.split("===Policy Brief===", 2);
                    effectivePrompt = parts[0].trim();
                    List<String> inlinePolicyBrief = java.util.Arrays.stream(parts[1].split("\\r?\\n"))
                            .map(String::trim)
                            .filter(line -> !line.isEmpty())
                            .collect(java.util.stream.Collectors.toList());
                    parsedPolicyBrief = inlinePolicyBrief;
                    System.out.println("Inline policy brief extracted: " + parsedPolicyBrief.size() + " rule(s)");
                }

                orchestrationContext = OrchestrationContext.of(parsedToolDefinitions, sessionId, previousToolName, parsedToolResult, effectivePrompt, parsedInputVariables, parsedExpectedOutputs, parsedPolicyBrief);
                System.out.println("Created OrchestrationContext: " + orchestrationContext);
            } else {
                orchestrationContext = OrchestrationContext.empty();
            }

            ExtensionResponse response = stepExecutionHandler.processRequest(agentAction, currentVariables, processedFormMessage, userMessage, stepMetadata, currentVariablesDefault, targetLanguage, orchestrationContext);
            requestLogger.logResponse(response);
            return response;
        } catch (IllegalArgumentException e) {
            System.err.println("Error processing request: " + e.getMessage());
            e.printStackTrace();
            AgenticSpans.recordError(requestSpan, e);
            throw new RuntimeException("Invalid action value: " + action + ". Supported values are: " +
                    String.join(", ", ActionParser.getEnumValuesAsString()), e);
        } catch (Exception e) {
            System.err.println("Error processing request: " + e.getMessage());
            e.printStackTrace();
            AgenticSpans.recordError(requestSpan, e);
            throw new RuntimeException(e);
        } finally {
            requestSpan.end();
            AgenticRequestContext.clearCurrent();
            AgenticStepContext.clearCurrent();
            // Drain the BatchSpanProcessor before returning. Spans are buffered and exported in a
            // batched HTTP call; this small JVM can exit before the batch interval elapses, so we
            // force-flush to push the turn's spans (flow.*, llm.call + sub-spans, tool.call) to the
            // collector now. forceFlush blocks until the batch export completes.
            // NOTE: keep this at ~1000ms. A shorter value (50ms was tried) returns before the
            // batch export completes and spans are dropped. It costs up to 1s on the response path,
            // but that is the price of reliable export from this short-lived JVM.
            AgenticTracerProvider.getInstance().flush(1000);
        }
    }

    /**
     * Parses the tool result from JSON string to Object.
     * If the input is null, empty, or not valid JSON, returns null.
     * This ensures that downstream code receives properly typed objects (Map, List, etc.)
     * instead of JSON strings.
     *
     * @param toolResultJson JSON string from CAM
     * @return Parsed Object (typically Map or List), or null if input is null/empty/invalid
     */
    private Object parseToolResult(String toolResultJson) {
        if (toolResultJson == null || toolResultJson.trim().isEmpty()) {
            return null;
        }

        try {
            // Sanitize JSON to handle unescaped quotes from LLM-generated content
            String sanitizedJson = JsonSanitizer.sanitize(toolResultJson);

            // Log if sanitization was applied
            if (JsonSanitizer.wasSanitized(toolResultJson, sanitizedJson)) {
                System.out.println("JSON sanitization applied to previousToolResult");
            }

            // Parse JSON string to Object (will be Map, List, String, Number, Boolean, or null)
            Object parsed = JsonMapper.getInstance().readValue(sanitizedJson, Object.class);
            System.out.println("Successfully parsed tool result JSON. Type: " +
                (parsed != null ? parsed.getClass().getSimpleName() : "null"));
            return parsed;
        } catch (Exception e) {
            System.err.println("Failed to parse tool result as JSON: " + e.getMessage());
            e.printStackTrace();
            System.err.println("Tool result content: " + toolResultJson);
            throw new RuntimeException("Invalid JSON in previousToolResult: " + e.getMessage(), e);
        }
    }

    /**
     * Parses the expected outputs from JSON string to List.
     * If the input is null or empty, returns an empty list.
     *
     * @param expectedOutputsJson JSON string from CAM
     * @return Parsed List of expected outputs, or empty list if input is null/empty
     */
    /**
     * Parses the tool definitions from JSON string to List.
     * If the input is null or empty, returns an empty list.
     *
     * @param toolDefinitionsJson JSON string from CAM
     * @return Parsed List of tool definitions, or empty list if input is null/empty/invalid
     */
    private List<String> parsePolicyBrief(String policyBriefJson) {

        if (policyBriefJson == null || policyBriefJson.trim().isEmpty()) {

            return List.of();
        }
        try {

            return JsonMapper.getInstance().readValue(
                    policyBriefJson,
                    JsonMapper.getInstance().getTypeFactory()
                            .constructCollectionType(List.class, String.class));
        } catch (Exception e) {

            LOGGER.error("Failed to parse policy brief: {}", e.getMessage(), e);
            return List.of();
        }
    }

    private List<Map<String, Object>> parseToolDefinitions(String toolDefinitionsJson) {

        if (toolDefinitionsJson == null || toolDefinitionsJson.trim().isEmpty()) {

            return List.of();
        }

        try {

            return JsonMapper.getInstance().readValue(
                toolDefinitionsJson,
                JsonMapper.getInstance().getTypeFactory()
                    .constructCollectionType(List.class, Map.class));
        } catch (Exception e) {

            LOGGER.error("Failed to parse tool definitions: {}", e.getMessage(), e);
            return List.of();
        }
    }

    private List<Map<String, Object>> parseExpectedOutputs(String expectedOutputsJson) {
        if (expectedOutputsJson == null || expectedOutputsJson.trim().isEmpty()) {
            System.out.println("ExpectedOutputs is null/empty, returning empty list");
            return List.of();
        }

        try {
            // Parse JSON string to List<Map<String, Object>>
            List<Map<String, Object>> parsed = JsonMapper.getInstance().readValue(
                expectedOutputsJson,
                JsonMapper.getInstance().getTypeFactory().constructCollectionType(List.class, Map.class)
            );
            System.out.println("Successfully parsed expectedOutputs JSON. Count: " + parsed.size());
            return parsed;
        } catch (Exception e) {
            System.err.println("Failed to parse expectedOutputs as JSON: " + e.getMessage());
            e.printStackTrace();
            System.err.println("ExpectedOutputs content: " + expectedOutputsJson);
            // Return empty list on parse error
            System.err.println("Falling back to empty expectedOutputs list");
            return List.of();
        }
    }

    /**
     * Parses the input variables from JSON string to List.
     * If the input is null or empty, returns an empty list.
     *
     * @param inputVariablesJson JSON string from CAM
     * @return Parsed List of input variables, or empty list if input is null/empty
     */
    private List<Map<String, Object>> parseInputVariables(String inputVariablesJson) {
        if (inputVariablesJson == null || inputVariablesJson.trim().isEmpty()) {
            System.out.println("InputVariables is null/empty, returning empty list");
            return List.of();
        }

        try {
            // Parse JSON string to List<Map<String, Object>>
            List<Map<String, Object>> parsed = JsonMapper.getInstance().readValue(
                inputVariablesJson,
                JsonMapper.getInstance().getTypeFactory().constructCollectionType(List.class, Map.class)
            );
            System.out.println("Successfully parsed inputVariables JSON. Count: " + parsed.size());
            return parsed;
        } catch (Exception e) {
            System.err.println("Failed to parse inputVariables as JSON: " + e.getMessage());
            e.printStackTrace();
            System.err.println("InputVariables content: " + inputVariablesJson);
            // Return empty list on parse error
            System.err.println("Falling back to empty inputVariables list");
            return List.of();
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
        System.out.println("AskMeAnything - Available conversations: " +
            (conversationsMetaData != null ? conversationsMetaData.size() : 0));

        AgenticRequestContext askContext = AgenticRequestContext.from(invoker, workspace);
        AgenticRequestContext.setCurrent(askContext);

        Span askSpan = AgenticSpans.builder("request.askMeAnything")
                .setAttribute(AgenticSpans.OPERATION, "request.askMeAnything")
                .startSpan();
        AgenticSpans.putRequestContext(askSpan, askContext);

        try (Scope ignored = askSpan.makeCurrent()) {

            // Create intent classification handler and delegate processing
            IntentClassificationHandler handler = new IntentClassificationHandler(invoker);
            return handler.classifyIntent(userMessage, conversationsMetaData);

        }
        catch (Exception e) {

            System.err.println("Error in askMeAnything: " + e.getMessage());
            e.printStackTrace();
            AgenticSpans.recordError(askSpan, e);
            throw new RuntimeException("Intent classification failed: " + e.getMessage(), e);
        } finally {
            askSpan.end();
            AgenticRequestContext.clearCurrent();
            // Full synchronous flush so spans reach the collector before the small JVM exits
            // (see enhance() for rationale).
            AgenticTracerProvider.getInstance().flush(1000);
        }
    }

    // ---- Policy Brief Authoring Catalog Requests ----

    @CatalogRequest(
            id = "localDomainRequest_e342de5e-6642-450e-9a79-8badc1704b1e",
            name = "generatePolicyBrief",
            description = "Start a policy brief generation job from a policy docset",
            area = "CAMHook",
            type = CatalogRequest.Type.CHANGE_SYSTEM)
    @Field.Text(name = "Job Id", required = false, attributes = {@Attribute(name = "visualWidth", value = "S")}, options = {})
    public ExtensionResponse generatePolicyBrief(
            @Field.Text(name = "Intent", required = true, attributes = {@Attribute(name = "visualWidth", value = "S")}, options = {}) String intent,
            @Field.Text(name = "Tool Definitions", required = true, attributes = {@Attribute(name = "visualWidth", value = "S")}, options = {}) String toolDefinitions,
            @Field.Text(name = "Policy Brief", required = false, attributes = {@Attribute(name = "visualWidth", value = "S")}, options = {}) String policyBrief) {

        LOGGER.info("generatePolicyBrief - intent={}", intent);

        try {

            List<String> parsedBrief = parsePolicyBrief(policyBrief);
            PolicyBriefService service = new PolicyBriefService(invoker);
            String jobId = service.startGeneration(intent, toolDefinitions, parsedBrief);

            Map<String, Object> responseVariables = new HashMap<>();
            responseVariables.put("Job Id", jobId);

            return new ExtensionResponse(
                    ExtensionResponse.Result.SUCCESS,
                    responseVariables,
                    null,
                    null,
                    null
            );

        } catch (Exception e) {

            LOGGER.error("Error in generatePolicyBrief: {}", e.getMessage(), e);
            throw new RuntimeException("Policy brief generation failed: " + e.getMessage(), e);
        }
    }

    @CatalogRequest(
            id = "localDomainRequest_c7f15596-8704-4b7c-9e7c-c3c19149f2c9",
            name = "getPolicyBriefResult",
            description = "Poll for policy brief generation status and results",
            area = "CAMHook",
            type = CatalogRequest.Type.QUERY_SYSTEM)
    @Field.Text(name = "Status", required = false, attributes = {@Attribute(name = "visualWidth", value = "S")}, options = {})
    @Field.Text(name = "Policy Brief", required = false, attributes = {@Attribute(name = "visualWidth", value = "S")}, options = {})
    @Field.Text(name = "Message", required = false, attributes = {@Attribute(name = "visualWidth", value = "S")}, options = {})
    @Field.Text(name = "Error Message", required = false, attributes = {@Attribute(name = "visualWidth", value = "S")}, options = {})
    @Field.Text(name = "Tool Name", required = false, attributes = {@Attribute(name = "visualWidth", value = "S")}, options = {})
    @Field.Text(name = "Tool Arguments", required = false, attributes = {@Attribute(name = "visualWidth", value = "S")}, options = {})
    public ExtensionResponse getPolicyBriefResult(
            @Field.Text(name = "Job Id", required = true, attributes = {@Attribute(name = "visualWidth", value = "S")}, options = {}) String jobId) {
        LOGGER.info("getPolicyBriefResult - jobId={}", jobId);

        try {

            PolicyBriefService service = new PolicyBriefService(invoker);
            PolicyBriefJob job = service.getResult(jobId);

            Map<String, Object> responseVariables = new HashMap<>();

            if (job == null) {

                responseVariables.put("Status", "NOT_FOUND");
                responseVariables.put("Message", "No policy brief job found for the given Job Id.");
            } else {

                responseVariables.put("Status", job.getStatus().name());
                responseVariables.put("Message", job.getMessage() != null ? job.getMessage() : "");
                responseVariables.put("Error Message", job.getErrorMessage() != null ? job.getErrorMessage() : "");

                responseVariables.put("Tool Name", job.getToolName() != null ? job.getToolName() : "");
                responseVariables.put("Tool Arguments", job.getToolArguments() != null ? job.getToolArguments() : "");

                try {

                    responseVariables.put("Policy Brief",
                            JsonMapper.getInstance().writeValueAsString(job.getPolicyBrief()));
                } catch (Exception e) {

                    LOGGER.warn("Failed to serialize policy brief for jobId={}: {}", jobId, e.getMessage());
                    responseVariables.put("Policy Brief", "[]");
                }
            }

            return new ExtensionResponse(
                    ExtensionResponse.Result.SUCCESS,
                    responseVariables,
                    null,
                    null,
                    null
            );

        } catch (Exception e) {

            LOGGER.error("Error in getPolicyBriefResult: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to get policy brief result: " + e.getMessage(), e);
        }
    }

    @CatalogRequest(
            id = "localDomainRequest_4eac4e0b-555d-408e-b061-7f27484cde3c",
            name = "submitPolicyBriefFeedback",
            description = "Submit author feedback for policy brief refinement",
            area = "CAMHook",
            type = CatalogRequest.Type.CHANGE_SYSTEM)
    @Field.Text(name = "Status", required = false, attributes = {@Attribute(name = "visualWidth", value = "S")}, options = {})
    @Field.Text(name = "Message", required = false, attributes = {@Attribute(name = "visualWidth", value = "S")}, options = {})
    public ExtensionResponse submitPolicyBriefFeedback(
            @Field.Text(name = "Job Id", required = true, attributes = {@Attribute(name = "visualWidth", value = "S")}, options = {}) String jobId,
            @Field.Text(name = "Answer", required = true, attributes = {@Attribute(name = "visualWidth", value = "S")}, options = {}) String answer) {
        LOGGER.info("submitPolicyBriefFeedback - jobId={}, answer length={}", jobId,
                answer != null ? answer.length() : 0);

        try {

            PolicyBriefService service = new PolicyBriefService(invoker);
            service.submitFeedback(jobId, answer);

            Map<String, Object> responseVariables = new HashMap<>();
            responseVariables.put("Status", "PENDING");
            responseVariables.put("Message", "Feedback submitted. Poll getPolicyBriefResult for updated results.");

            return new ExtensionResponse(
                    ExtensionResponse.Result.SUCCESS,
                    responseVariables,
                    null,
                    null,
                    null
            );

        } catch (IllegalArgumentException e) {

            LOGGER.warn("submitPolicyBriefFeedback - job not found: jobId={}", jobId);

            Map<String, Object> responseVariables = new HashMap<>();
            responseVariables.put("Status", "NOT_FOUND");
            responseVariables.put("Message", e.getMessage());

            return new ExtensionResponse(
                    ExtensionResponse.Result.SUCCESS,
                    responseVariables,
                    null,
                    null,
                    null
            );

        } catch (Exception e) {

            LOGGER.error("Error in submitPolicyBriefFeedback: {}", e.getMessage(), e);
            throw new RuntimeException("Policy brief feedback failed: " + e.getMessage(), e);
        }
    }

}
