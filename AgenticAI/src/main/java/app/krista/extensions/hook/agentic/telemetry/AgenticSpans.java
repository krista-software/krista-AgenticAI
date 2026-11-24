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

package app.krista.extensions.hook.agentic.telemetry;

import app.krista.extensions.hook.agentic.session.AgenticSession;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Helpers for creating spans without sprinkling OTel imports across business code.
 *
 * <p>Conventions:
 * <ul>
 *   <li>Span names use dot-separated lowercase, e.g. {@code agentic.session.start}.</li>
 *   <li>Common attribute keys are exposed as {@link AttributeKey} constants.</li>
 *   <li>Spans should be opened in a try-with-resources on the returned {@link Scope};
 *       call {@link #recordError(Span, Throwable)} on failure and always call
 *       {@code span.end()} in a finally block (or use the {@code run} / {@code call} helpers).</li>
 * </ul>
 *
 * <p>If no OTLP endpoint is configured the underlying tracer is a no-op, so spans created
 * here are cheap and safe — call sites do not need to null-check before opening a span.
 */
public final class AgenticSpans {

    private static final Logger LOGGER = LoggerFactory.getLogger(AgenticSpans.class);

    public static final AttributeKey<String> SESSION_ID = AttributeKey.stringKey("agentic.session.id");
    public static final AttributeKey<String> PARENT_SESSION_ID = AttributeKey.stringKey("agentic.parent.session.id");
    public static final AttributeKey<String> AGENT_NAME = AttributeKey.stringKey("agentic.agent.name");
    public static final AttributeKey<String> MODEL_NAME = AttributeKey.stringKey("agentic.model.name");
    public static final AttributeKey<String> WORKSPACE_ID = AttributeKey.stringKey("agentic.workspace.id");
    public static final AttributeKey<String> WORKSPACE_NAME = AttributeKey.stringKey("agentic.workspace.name");
    public static final AttributeKey<String> INVOKER_ID = AttributeKey.stringKey("agentic.invoker.id");
    public static final AttributeKey<String> INVOKER_NAME = AttributeKey.stringKey("agentic.invoker.name");
    public static final AttributeKey<String> CONVERSATION_ID = AttributeKey.stringKey("agentic.conversation.id");
    public static final AttributeKey<String> PARENT_CONVERSATION_ID = AttributeKey.stringKey("agentic.parent.conversation.id");
    public static final AttributeKey<String> CONVERSATION_NAME = AttributeKey.stringKey("agentic.conversation.name");
    public static final AttributeKey<String> STEP_TYPE = AttributeKey.stringKey("agentic.step.type");
    public static final AttributeKey<String> STEP_NAME = AttributeKey.stringKey("agentic.step.name");
    public static final AttributeKey<String> ACTION = AttributeKey.stringKey("agentic.action");
    public static final AttributeKey<String> TOOL_NAME = AttributeKey.stringKey("agentic.tool.name");
    public static final AttributeKey<String> TOOL_STATUS = AttributeKey.stringKey("agentic.tool.status");
    public static final AttributeKey<String> RESPONSE_TYPE = AttributeKey.stringKey("agentic.response.type");
    public static final AttributeKey<String> ENDPOINT = AttributeKey.stringKey("agentic.endpoint");
    /**
     * Low-cardinality operation type. Use this on dashboards/queries to group spans by what
     * they represent ({@code request.startFlow}, {@code session.start}, {@code llm.call},
     * {@code tool.call}, etc.) without depending on the (now dynamic) span {@code name},
     * which encodes the specific tool/model/agent for human readability.
     */
    public static final AttributeKey<String> OPERATION = AttributeKey.stringKey("agentic.operation");
    /**
     * Logical-parent span ID for grouping in SigNoz when the native SDK tree-view can't
     * link spans natively (the platform agent overrides setParent for our SDK). Filter
     * SigNoz queries by {@code agentic.parent.span.id = <some spanId>} to retrieve the
     * children of that span. Hierarchy:
     * <ul>
     *   <li>llm.call → previous tool.call (or absent on first turn)</li>
     *   <li>llm sub-spans (prompt.build, api.call, response.parse, guardrails) → llm.call</li>
     *   <li>tool.call → the llm.call that dispatched it</li>
     *   <li>tool.result.validate / tool.result.append → the matching tool.call</li>
     * </ul>
     */
    public static final AttributeKey<String> PARENT_SPAN_ID = AttributeKey.stringKey("agentic.parent.span.id");
    public static final AttributeKey<Long> PROMPT_TOKENS = AttributeKey.longKey("agentic.llm.prompt_tokens");
    public static final AttributeKey<Long> COMPLETION_TOKENS = AttributeKey.longKey("agentic.llm.completion_tokens");
    public static final AttributeKey<Long> TOTAL_TOKENS = AttributeKey.longKey("agentic.llm.total_tokens");
    public static final AttributeKey<Double> COST = AttributeKey.doubleKey("agentic.llm.cost");
    public static final AttributeKey<Long> DURATION_MS = AttributeKey.longKey("agentic.duration_ms");
    public static final AttributeKey<Long> ATTEMPT_COUNT = AttributeKey.longKey("agentic.persist.attempts");
    public static final AttributeKey<Boolean> PERSIST_SUCCESS = AttributeKey.booleanKey("agentic.persist.success");

    private AgenticSpans() {
    }

    /**
     * Returns a SpanBuilder for the given name. Without explicit setParent/setNoParent
     * calls by the caller, the new span will inherit from {@code Context.current()} —
     * which in this runtime is typically the platform's auto-instrumented HTTP span.
     * That inheritance is intentional: it keeps our llm.call / tool.call spans in the
     * same trace as the platform's POSTs, which is what makes them visible in SigNoz.
     */
    public static SpanBuilder builder(String spanName) {

        Tracer tracer = AgenticTracerProvider.getInstance().getTracer();
        return tracer.spanBuilder(spanName);
    }


    /**
     * Adds workspace/invoker attributes from a request context onto a span.
     * Tolerates a null context (no-op).
     */
    public static void putRequestContext(Span span, AgenticRequestContext ctx) {

        if (span == null || ctx == null) {

            return;
        }
        putIfPresent(span, WORKSPACE_ID, ctx.workspaceId());
        putIfPresent(span, WORKSPACE_NAME, ctx.workspaceName());
        putIfPresent(span, INVOKER_ID, ctx.invokerId());
        putIfPresent(span, INVOKER_NAME, ctx.invokerName());
    }

    /**
     * Adds conversation/step identity attributes from a step context onto a span.
     * Tolerates a null context (no-op). Use this on every span where filtering by
     * conversation or step is useful — i.e. all top-level and sub-spans we emit.
     */
    public static void putStepContext(Span span, AgenticStepContext ctx) {

        if (span == null || ctx == null) {

            return;
        }
        putIfPresent(span, CONVERSATION_ID, ctx.conversationId());
        putIfPresent(span, CONVERSATION_NAME, ctx.conversationName());
        putIfPresent(span, STEP_TYPE, ctx.stepType());
        putIfPresent(span, STEP_NAME, ctx.stepName());
    }

    /**
     * Stamps {@code agentic.parent.conversation.id} on the span if the session is a child
     * session (i.e. has a non-blank parentConversationId). Lets SigNoz queries by parent CEC
     * surface child-flow activity that runs under its own conversation.id.
     */
    public static void putParentConversation(Span span, AgenticSession session) {

        if (span == null || session == null) {

            return;
        }
        putIfPresent(span, PARENT_CONVERSATION_ID, session.getParentConversationId());
    }

    public static void putIfPresent(Span span, AttributeKey<String> key, String value) {

        if (span != null && value != null && !value.isBlank()) {

            span.setAttribute(key, value);
        }
    }

    /**
     * Adds a string attribute to an {@link AttributesBuilder} (used for log/metric emission)
     * only when the value is non-null and non-blank. Shared by the telemetry emitters.
     */
    public static void putStringIfPresent(AttributesBuilder builder, String key, String value) {

        if (builder != null && value != null && !value.isBlank()) {

            builder.put(key, value);
        }
    }

    /**
     * Marks the span as errored and attaches the throwable. Always safe — null span is a no-op.
     */
    public static void recordError(Span span, Throwable t) {

        if (span == null || t == null) {

            return;
        }
        try {

            span.recordException(t);
            span.setStatus(StatusCode.ERROR, t.getClass().getSimpleName() + ": " + safeMessage(t));
        } catch (Throwable ignored) {

            // Telemetry must never break the main flow.
            LOGGER.debug("[TELEMETRY] recordError suppressed: {}", ignored.getMessage());
        }
    }

    /**
     * Serialises the active {@link Span}'s context as a W3C {@code traceparent} string so it can
     * be stashed on a session and later restored across the platform's
     * {@code EXECUTE_TOOL → continueFlow} out-of-band gap.
     *
     * <p>Returns {@code null} if no valid span context is currently active.
     */
    public static String serializeCurrentTraceparent() {

        return serializeTraceparent(Span.current());
    }

    /**
     * Serialises an explicit {@link Span}'s context as a W3C {@code traceparent} string.
     * Useful for stashing the span's identity on a session without first calling
     * {@code makeCurrent()} on it.
     *
     * <p>Returns {@code null} for a null span or invalid span context.
     */
    public static String serializeTraceparent(Span span) {

        if (span == null) {

            return null;
        }
        SpanContext ctx = span.getSpanContext();
        if (!ctx.isValid()) {

            return null;
        }
        return "00-" + ctx.getTraceId() + "-" + ctx.getSpanId() + "-"
                + (ctx.isSampled() ? "01" : "00");
    }

    /**
     * Builds a {@link Context} carrying a remote-parent span reconstructed from a W3C
     * {@code traceparent} string. Use {@code .makeCurrent()} on the returned {@link Context}
     * before opening child spans so they nest into the original trace.
     *
     * <p>Returns {@code null} for blank/malformed input — callers should fall back to the
     * current context in that case.
     */
    public static Context contextFromTraceparent(String traceparent) {

        if (traceparent == null || traceparent.isBlank()) {

            return null;
        }
        String[] parts = traceparent.split("-");
        if (parts.length != 4) {

            LOGGER.debug("[TELEMETRY] Ignoring malformed traceparent: {}", traceparent);
            return null;
        }
        try {

            TraceFlags flags = "01".equals(parts[3]) ? TraceFlags.getSampled() : TraceFlags.getDefault();
            SpanContext remote = SpanContext.createFromRemoteParent(parts[1], parts[2], flags, TraceState.getDefault());
            if (!remote.isValid()) {

                return null;
            }
            // Build from Context.current() (not Context.root()): the platform OTel agent and
            // one-trace-per-conversation grouping depend on the existing context entries being
            // carried through here, with only the active span overridden to the remote parent.
            return Context.current().with(Span.wrap(remote));
        } catch (Throwable t) {

            LOGGER.debug("[TELEMETRY] Could not parse traceparent {}: {}", traceparent, t.getMessage());
            return null;
        }
    }

    private static String safeMessage(Throwable t) {

        String msg = t.getMessage();
        return msg == null ? "" : msg;
    }
}
