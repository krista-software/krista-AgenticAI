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

import app.krista.extensions.hook.agentic.dto.AgenticResponse;
import app.krista.extensions.hook.agentic.guardrails.GuardrailResults;
import app.krista.extensions.hook.agentic.guardrails.GuardrailViolationException;
import app.krista.extensions.hook.agentic.guardrails.MalformedJsonException;
import app.krista.extensions.hook.agentic.guardrails.OutputGuardrails;
import app.krista.extensions.hook.agentic.persistence.AgenticPersistentSessionStore;
import app.krista.extensions.hook.agentic.session.AgenticSession;
import app.krista.extensions.hook.agentic.session.AgenticSessionStore;
import app.krista.extensions.hook.agentic.telemetry.AgenticLlmTelemetryEmitter;
import app.krista.extensions.hook.agentic.telemetry.AgenticRequestContext;
import app.krista.extensions.hook.agentic.telemetry.AgenticSpans;
import app.krista.extensions.hook.agentic.telemetry.AgenticStepContext;
import app.krista.extensions.hook.agentic.util.ChatMessageUtils;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import app.krista.extensions.hook.util.JsonMapper;
import app.krista.extensions.hook.util.JsonSanitizer;
import com.fasterxml.jackson.databind.JsonNode;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;

/**
 * Wrapper for ChatLanguageModel that tracks token usage and costs.
 * This is a proxy/wrapper pattern that intercepts all LLM calls and tracks usage.
 * Extracts session ID from the LLM response and looks up the session from AgenticSessionStore.
 */
public class AgenticChatLanguageModel implements ChatLanguageModel {

    private static final Logger LOGGER = LoggerFactory.getLogger(AgenticChatLanguageModel.class);

    private final ChatLanguageModel delegate;
    private final String agentName;
    private final String modelName;
    private final String apiKeyMasked;
    private final boolean enforceOutputGuardrails;

    private static final ThreadLocal<String> CURRENT_SESSION_ID = new ThreadLocal<>();

    public static void setCurrentSessionId(String sessionId) {

        CURRENT_SESSION_ID.set(sessionId);
    }

    public static void clearCurrentSessionId() {

        CURRENT_SESSION_ID.remove();
    }

    public AgenticChatLanguageModel(ChatLanguageModel delegate,
                                   String agentName,
                                   String modelName,
                                   String apiKeyMasked,
                                   boolean enforceOutputGuardrails) {
        this.delegate = delegate;
        this.agentName = agentName;
        this.modelName = modelName;
        this.apiKeyMasked = apiKeyMasked;
        this.enforceOutputGuardrails = enforceOutputGuardrails;
    }

    @Override
    public Response<dev.langchain4j.data.message.AiMessage> generate(List<ChatMessage> messages) {
        long startTime = System.currentTimeMillis();

        // Open llm.call span. Parent is implicit Context.current() (the platform's POST)
        // so llm.call and its sub-spans share the per-turn trace. Cross-turn linkage is
        // carried by the agentic.parent.span.id attribute stamped below.
        String currentSessionId = CURRENT_SESSION_ID.get();
        AgenticSession sessionForTrace = currentSessionId != null ? lookupSession(currentSessionId) : null;

        String llmSpanName = "llm.call: " + (modelName != null && !modelName.isBlank() ? modelName : "unknown");
        Span llmSpan = AgenticSpans.builder(llmSpanName)
                .setAttribute(AgenticSpans.OPERATION, "llm.call")
                .setAttribute(AgenticSpans.AGENT_NAME, agentName != null ? agentName : "")
                .setAttribute(AgenticSpans.MODEL_NAME, modelName != null ? modelName : "")
                .startSpan();
        AgenticSpans.putIfPresent(llmSpan, AgenticSpans.SESSION_ID, currentSessionId);
        AgenticSpans.putStepContext(llmSpan, AgenticStepContext.getCurrent());
        AgenticSpans.putParentConversation(llmSpan, sessionForTrace);

        // Link this llm.call to the previous tool.call (if any) and stash its spanId so
        // the next tool.call can point back at it.
        String llmSpanId = llmSpan.getSpanContext().getSpanId();
        if (sessionForTrace != null) {

            String previousToolCallSpanId = sessionForTrace.getLastToolCallSpanId();
            if (previousToolCallSpanId != null && !previousToolCallSpanId.isBlank()) {

                llmSpan.setAttribute(AgenticSpans.PARENT_SPAN_ID, previousToolCallSpanId);
            }
            sessionForTrace.setLastLlmSpanId(llmSpanId);
        }

        // Best-effort parent for sub-spans. Platform OTel agent often overrides this and
        // reparents to the POST span — the agentic.parent.span.id attribute keeps the
        // logical link intact regardless.
        Context llmContext = Context.current().with(llmSpan);

        try (Scope llmScope = llmSpan.makeCurrent()) {

        // Input guardrails are applied at the service layer (AgenticFlowService) where tool-type context
        // is available, allowing selective validation — e.g. skip AIQA HTML tool results which are
        // trusted system responses, and only validate ask_a_person results which carry user-typed text.
        // Applying input guardrails here would falsely block legitimate tool results embedded in the
        // @UserMessage template content.

        // Sub-span: build prompt — usually fast (milliseconds), but useful to confirm.
        Span promptSpan = AgenticSpans.builder("llm.prompt.build")
                .setParent(llmContext)
                .setAttribute(AgenticSpans.OPERATION, "llm.prompt.build")
                .setAttribute(AgenticSpans.PARENT_SPAN_ID, llmSpanId)
                .startSpan();
        AgenticSpans.putStepContext(promptSpan, AgenticStepContext.getCurrent());
        String fullPrompt;
        String promptSummary;
        try {

            fullPrompt = buildFullPrompt(messages);
            promptSummary = CostCalculator.createPromptSummary(fullPrompt, 200);
            promptSpan.setAttribute("agentic.prompt.length", (long) fullPrompt.length());
            promptSpan.setAttribute("agentic.prompt.message_count", (long) messages.size());
        } finally {

            promptSpan.end();
        }

        // Sub-span: LLM provider HTTP roundtrip — usually the dominant cost of llm.call.
        LOGGER.debug("Calling delegate LLM - agent: {}, messageCount: {}", agentName, messages.size());
        Span apiSpan = AgenticSpans.builder("llm.api.call: " + (modelName != null && !modelName.isBlank() ? modelName : "unknown"))
                .setParent(llmContext)
                .setAttribute(AgenticSpans.OPERATION, "llm.api.call")
                .setAttribute(AgenticSpans.MODEL_NAME, modelName != null ? modelName : "")
                .setAttribute(AgenticSpans.PARENT_SPAN_ID, llmSpanId)
                .startSpan();
        AgenticSpans.putStepContext(apiSpan, AgenticStepContext.getCurrent());
        Response<dev.langchain4j.data.message.AiMessage> response;
        try {

            response = RateLimitRetryHandler.executeWithRetry(delegate, messages, agentName);
        } catch (RuntimeException | Error t) {

            AgenticSpans.recordError(apiSpan, t);
            throw t;
        } finally {

            apiSpan.end();
        }

        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;

        // Sub-span: extract JSON from the model's raw response (handles markdown fences,
        // surrounding prose, etc).
        Span parseSpan = AgenticSpans.builder("llm.response.parse")
                .setParent(llmContext)
                .setAttribute(AgenticSpans.OPERATION, "llm.response.parse")
                .setAttribute(AgenticSpans.PARENT_SPAN_ID, llmSpanId)
                .startSpan();
        AgenticSpans.putStepContext(parseSpan, AgenticStepContext.getCurrent());
        String completionText;
        try {

            completionText = (response.content() != null && response.content().text() != null)
                    ? response.content().text()
                    : "";

            String extractedText = JsonSanitizer.extractJsonObject(completionText);
            if (!extractedText.equals(completionText)) {

                LOGGER.debug("Extracted JSON object from LLM response (model: {}, agent: {}, original length: {}, extracted length: {})",
                        modelName, agentName, completionText.length(), extractedText.length());
                completionText = extractedText;
                response = new Response<>(
                        new dev.langchain4j.data.message.AiMessage(completionText),
                        response.tokenUsage(),
                        response.finishReason()
                );
            }
            parseSpan.setAttribute("agentic.response.length", (long) completionText.length());
        } finally {

            parseSpan.end();
        }

        LOGGER.debug("LLM response received - length: {}", completionText.length());
        LOGGER.trace("LLM response content: {}", completionText);

        // Sub-span: output guardrails — schema validation + JSON shape check.
        if (enforceOutputGuardrails) {

            LOGGER.debug("Validating output guardrails for agent: {}", agentName);
            Span guardSpan = AgenticSpans.builder("llm.guardrails")
                    .setParent(llmContext)
                    .setAttribute(AgenticSpans.OPERATION, "llm.guardrails")
                    .setAttribute(AgenticSpans.PARENT_SPAN_ID, llmSpanId)
                    .startSpan();
            AgenticSpans.putStepContext(guardSpan, AgenticStepContext.getCurrent());
            try {

                // Parse the JSON response to AgenticResponse object
                AgenticResponse agenticResponse = JsonMapper.getInstance().readValue(completionText, AgenticResponse.class);
                LOGGER.debug("Output guardrails: JSON parsing successful");

                // Validate the response structure
                GuardrailResults outputCheck = OutputGuardrails.validateResponse(agenticResponse);
                if (outputCheck.isBlocked()) {
                    guardSpan.setAttribute("agentic.guardrails.blocked", true);
                    LOGGER.error("Output blocked by guardrails for {}: {}", agentName, outputCheck.getReason());
                    throw new GuardrailViolationException(
                            "LLM response validation failed: " + outputCheck.getReason()
                    );
                }
                guardSpan.setAttribute("agentic.guardrails.blocked", false);
                LOGGER.debug("Output passed guardrails validation for agent: {}", agentName);
            } catch (GuardrailViolationException e) {
                AgenticSpans.recordError(guardSpan, e);
                throw e;
            } catch (Exception e) {
                AgenticSpans.recordError(guardSpan, e);
                // If parsing fails, it's a malformed response - use MalformedJsonException for retry logic
                LOGGER.error("Malformed JSON - failed to parse LLM response: {} (type: {})",
                    e.getMessage(), e.getClass().getName());
                LOGGER.debug("Response preview: {}",
                    completionText.length() > 200 ? completionText.substring(0, 200) + "..." : completionText);
                throw new MalformedJsonException(
                        "LLM returned malformed JSON response: " + e.getMessage()
                );
            } finally {
                guardSpan.end();
            }
        }

        // Extract token usage
        TokenUsage tokenUsage = response.tokenUsage();
        int promptTokens = tokenUsage != null ? tokenUsage.inputTokenCount() : 0;
        int completionTokens = tokenUsage != null ? tokenUsage.outputTokenCount() : 0;
        int totalTokens = promptTokens + completionTokens;

        // Calculate cost
        double cost = CostCalculator.calculateCost(modelName, promptTokens, completionTokens);

        // Create usage record
        TokenUsageRecord record = TokenUsageRecord.builder()
                .timestamp(Instant.now())
                .modelName(modelName)
                .apiKeyMasked(apiKeyMasked)
                .agentName(agentName)
                .promptSummary(promptSummary)
                .fullPrompt(fullPrompt)
                .completionText(completionText)
                .promptTokens(promptTokens)
                .completionTokens(completionTokens)
                .totalTokens(totalTokens)
                .cost(cost)
                .requestDurationMs(duration)
                .build();

        // Extract session ID from the response and get the usage collector
        String sessionId;
        try {

            sessionId = CURRENT_SESSION_ID.get();
        } finally {

            CURRENT_SESSION_ID.remove();
        }

        if (sessionId == null) {

            LOGGER.error("ThreadLocal sessionId was null — code path bypassed callLlmWithValidation(). Falling back to LLM response extraction.");
            sessionId = extractSessionIdFromResponse(completionText);
        } else {

            // Compare with LLM's echoed value for monitoring
            String llmSessionId = extractSessionIdFromResponse(completionText);
            if (llmSessionId != null && !llmSessionId.equals(sessionId)) {

                LOGGER.warn("LLM echoed mismatched sessionId. Expected: {}, Got: {}. Using server-known value for usage tracking.",
                    sessionId, llmSessionId);
            }
        }
        AgenticSession resolvedSession = null;
        if (sessionId != null) {
            // Reuse the session already loaded at the top of generate() when the id matches
            // (the common path). Only re-look-up when sessionId came from the response-extraction
            // fallback and differs from the ThreadLocal value used above.
            resolvedSession = (sessionId.equals(currentSessionId) && sessionForTrace != null)
                    ? sessionForTrace
                    : lookupSession(sessionId);

            if (resolvedSession != null && resolvedSession.getUsageCollector() != null) {
                resolvedSession.getUsageCollector().addUsageRecord(record);
            } else {
                LOGGER.warn("No session or usage collector found for session ID: {}", sessionId);
            }
        } else {
            LOGGER.warn("Could not extract session ID from LLM response. Usage tracking skipped.");
        }

        // Fire-and-forget per-call telemetry. Emitter swallows all Throwables internally.
        AgenticRequestContext callContext = resolvedSession != null ? resolvedSession.getRequestContext() : null;
        AgenticLlmTelemetryEmitter.emit(record, sessionId, "COMPLETE", null, callContext);

        // Annotate llm.call span with the same dimensions so dashboards can aggregate spans
        // without joining against log records.
        AgenticSpans.putIfPresent(llmSpan, AgenticSpans.SESSION_ID, sessionId);
        AgenticSpans.putRequestContext(llmSpan, callContext);
        llmSpan.setAttribute(AgenticSpans.PROMPT_TOKENS, (long) promptTokens);
        llmSpan.setAttribute(AgenticSpans.COMPLETION_TOKENS, (long) completionTokens);
        llmSpan.setAttribute(AgenticSpans.TOTAL_TOKENS, (long) totalTokens);
        llmSpan.setAttribute(AgenticSpans.COST, cost);
        llmSpan.setAttribute(AgenticSpans.DURATION_MS, duration);

        return response;
        } catch (RuntimeException | Error t) {
            AgenticSpans.recordError(llmSpan, t);
            throw t;
        } finally {
            llmSpan.end();
        }
    }

    /**
     * Looks up an active session by ID, checking the persistent store first and falling
     * back to the in-memory cache. Tolerates a null id and any lookup failure — telemetry
     * must never affect business behavior.
     */
    private AgenticSession lookupSession(String sessionId) {

        if (sessionId == null) {

            return null;
        }
        try {

            AgenticPersistentSessionStore persistent = AgenticPersistentSessionStore.getInstance();
            AgenticSession found = persistent != null ? persistent.getSession(sessionId) : null;
            if (found != null) {

                return found;
            }
            AgenticSessionStore local = AgenticSessionStore.getInstance();
            return local != null ? local.getSession(sessionId) : null;
        } catch (Throwable t) {

            LOGGER.debug("[TELEMETRY] lookupSession suppressed: {}", t.getMessage());
            return null;
        }
    }

    /**
     * Extract session ID from the LLM response JSON.
     * The response should contain a "sessionId" field in the AgenticResponse structure.
     */
    private String extractSessionIdFromResponse(String responseText) {
        if (responseText == null || responseText.trim().isEmpty()) {
            return null;
        }

        try {

            // Try to parse as JSON
            JsonNode jsonNode = JsonMapper.getInstance().readTree(responseText);
            if (jsonNode.has("sessionId")) {
                return jsonNode.get("sessionId").asText();
            }
        } catch (Exception e) {
            // Not valid JSON or doesn't have sessionId field
            LOGGER.debug("Could not parse session ID from response: {}", e.getMessage());
        }

        return null;
    }

    /**
     * Build full prompt text from all messages.
     */
    private String buildFullPrompt(List<ChatMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        for (ChatMessage message : messages) {
            sb.append(message.type()).append(": ").append(ChatMessageUtils.getText(message)).append("\n");
        }
        return sb.toString();
    }
}
