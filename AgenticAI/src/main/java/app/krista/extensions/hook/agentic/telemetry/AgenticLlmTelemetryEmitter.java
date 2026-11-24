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

import app.krista.extensions.hook.agentic.usagecollector.TokenUsageRecord;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.logs.Severity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Emits per-LLM-call telemetry for the Agentic AI runtime to SigNoz via OpenTelemetry.
 *
 * <p>One event is produced per call through {@code AgenticChatLanguageModel.generate(...)}.
 * The emitter publishes one OTel log record and increments a counter, using the same
 * {@link Attributes} bag so dashboards can correlate counts with dimensions.
 *
 * <p>Emission is fire-and-forget: any {@link Throwable} is caught and logged so that
 * telemetry failures never propagate back into the main request flow.
 *
 * <p>Stateless and thread-safe; all public methods are static.
 */
public final class AgenticLlmTelemetryEmitter {

    private static final Logger LOGGER = LoggerFactory.getLogger(AgenticLlmTelemetryEmitter.class);

    private static final String LOG_NAME = "agentic-llm-call";
    private static final String LOG_BODY = "Agentic LLM call";
    private static final String COUNTER_NAME = "total-agentic-llm-requests";
    private static final String COUNTER_DESCRIPTION = "Total agentic LLM calls with token and cost tracking";
    private static final String COUNTER_UNIT = "1";

    private AgenticLlmTelemetryEmitter() {
    }

    /**
     * Emits a log record and a counter to SigNoz for a single LLM call.
     *
     * @param record         the completed usage record; a {@code null} record is silently ignored
     * @param sessionId      the Agentic session identifier (may be {@code null} if resolution failed)
     * @param status         terminal status, typically {@code "COMPLETE"} or {@code "FAILED"}
     * @param errorMessage   error description when status is FAILED; {@code null} otherwise
     * @param requestContext workspace/invoker identity captured at request entry; {@code null} is tolerated
     */
    public static void emit(TokenUsageRecord record, String sessionId,
                            String status, String errorMessage,
                            AgenticRequestContext requestContext) {

        try {

            if (record == null) {

                LOGGER.debug("[TELEMETRY] TokenUsageRecord is null, skipping emission");
                return;
            }

            Attributes attributes = buildAttributes(record, sessionId, status, errorMessage, requestContext);
            TelemetryService telemetry = TelemetryService.getInstance();
            telemetry.recordLog(LOG_NAME, Severity.INFO, LOG_BODY, attributes);
            telemetry.recordCounter(COUNTER_NAME, COUNTER_DESCRIPTION, COUNTER_UNIT, 1, attributes);

            LOGGER.debug("[TELEMETRY] agentic-llm-call emitted session={} agent={} tokens={} cost={}",
                    sessionId, record.getAgentName(), record.getTotalTokens(), record.getCost());
        } catch (Throwable e) {

            // Catch Throwable (not just Exception) to handle NoClassDefFoundError when
            // TelemetryAccess is not on the runtime classpath. Telemetry must never crash the main flow.
            String agent = record != null ? record.getAgentName() : "unknown";
            LOGGER.error("[TELEMETRY] Failed to emit agentic-llm-call session={} agent={}: {}",
                    sessionId, agent, e.getMessage(), e);
        }
    }

    private static Attributes buildAttributes(TokenUsageRecord record, String sessionId,
                                              String status, String errorMessage,
                                              AgenticRequestContext requestContext) {

        AttributesBuilder builder = Attributes.builder();

        AgenticSpans.putStringIfPresent(builder, "sessionId", sessionId);
        AgenticSpans.putStringIfPresent(builder, "agentName", record.getAgentName());
        AgenticSpans.putStringIfPresent(builder, "modelName", record.getModelName());
        AgenticSpans.putStringIfPresent(builder, "apiKeyMasked", record.getApiKeyMasked());
        AgenticSpans.putStringIfPresent(builder, "status", status);
        AgenticSpans.putStringIfPresent(builder, "errorMessage", errorMessage);

        if (requestContext != null) {

            AgenticSpans.putStringIfPresent(builder, "workspaceId", requestContext.workspaceId());
            AgenticSpans.putStringIfPresent(builder, "workspaceName", requestContext.workspaceName());
            AgenticSpans.putStringIfPresent(builder, "invokerId", requestContext.invokerId());
            AgenticSpans.putStringIfPresent(builder, "invokerName", requestContext.invokerName());
        }

        builder.put("promptTokens", record.getPromptTokens());
        builder.put("completionTokens", record.getCompletionTokens());
        builder.put("totalTokens", record.getTotalTokens());
        builder.put("cost", record.getCost());
        builder.put("durationMs", record.getRequestDurationMs());
        builder.put("promptLength", record.getPromptLength());
        builder.put("completionLength", record.getCompletionLength());

        if (record.getTimestamp() != null) {

            builder.put("eventTime", record.getTimestamp().toEpochMilli());
        }
        return builder.build();
    }
}
