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

import app.krista.extensions.hook.agentic.usagecollector.SessionUsageCollector.SessionStatus;
import app.krista.extensions.hook.agentic.usagecollector.SessionUsageCollector.SessionSummary;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.logs.Severity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Emits one aggregated telemetry event per completed Agentic AI session to SigNoz.
 *
 * <p>Mirrors studioAI's {@code step-generation} emission pattern: a single OTel log record
 * and counter increment at the terminal status (COMPLETED or ERROR) of a logical unit of
 * work, with totals aggregated across every internal LLM call that ran during it.
 *
 * <p>Emission is fire-and-forget: any {@link Throwable} is caught and logged so that
 * telemetry failures never propagate back into the main request flow.
 *
 * <p>Stateless and thread-safe; all public methods are static.
 */
public final class AgenticSessionTelemetryEmitter {

    private static final Logger LOGGER = LoggerFactory.getLogger(AgenticSessionTelemetryEmitter.class);

    private static final String LOG_NAME = "agentic-session";
    private static final String LOG_BODY = "Agentic session summary";
    private static final String COUNTER_NAME = "total-agentic-sessions";
    private static final String COUNTER_DESCRIPTION = "Total Agentic sessions completed or errored";
    private static final String COUNTER_UNIT = "1";

    private AgenticSessionTelemetryEmitter() {
    }

    /**
     * Emits a log record and a counter to SigNoz summarising one completed session.
     *
     * @param summary        the session summary produced by
     *                       {@link app.krista.extensions.hook.agentic.usagecollector.SessionUsageCollector#getSessionSummary()};
     *                       a {@code null} summary is silently ignored
     * @param requestContext workspace/invoker identity captured at request entry;
     *                       {@code null} is tolerated and those attributes are omitted
     */
    public static void emit(SessionSummary summary, AgenticRequestContext requestContext) {

        try {

            if (summary == null) {

                LOGGER.debug("[TELEMETRY] SessionSummary is null, skipping emission");
                return;
            }

            Attributes attributes = buildAttributes(summary, requestContext);
            TelemetryService telemetry = TelemetryService.getInstance();
            telemetry.recordLog(LOG_NAME, Severity.INFO, LOG_BODY, attributes);
            telemetry.recordCounter(COUNTER_NAME, COUNTER_DESCRIPTION, COUNTER_UNIT, 1, attributes);

            LOGGER.debug("[TELEMETRY] agentic-session emitted session={} agent={} status={} calls={} cost={}",
                    summary.getSessionId(), summary.getAgentName(), summary.getStatus(),
                    summary.getTotalLlmCalls(), summary.getTotalCost());
        } catch (Throwable e) {

            // Catch Throwable (not just Exception) to handle NoClassDefFoundError when
            // TelemetryAccess is not on the runtime classpath. Telemetry must never crash the main flow.
            String sid = summary != null ? summary.getSessionId() : "unknown";
            LOGGER.error("[TELEMETRY] Failed to emit agentic-session for session={}: {}",
                    sid, e.getMessage(), e);
        }
    }

    private static Attributes buildAttributes(SessionSummary summary, AgenticRequestContext requestContext) {

        AttributesBuilder builder = Attributes.builder();

        AgenticSpans.putStringIfPresent(builder, "sessionId", summary.getSessionId());
        AgenticSpans.putStringIfPresent(builder, "agentName", summary.getAgentName());
        AgenticSpans.putStringIfPresent(builder, "errorMessage", summary.getErrorMessage());

        if (requestContext != null) {

            AgenticSpans.putStringIfPresent(builder, "workspaceId", requestContext.workspaceId());
            AgenticSpans.putStringIfPresent(builder, "workspaceName", requestContext.workspaceName());
            AgenticSpans.putStringIfPresent(builder, "invokerId", requestContext.invokerId());
            AgenticSpans.putStringIfPresent(builder, "invokerName", requestContext.invokerName());
        }

        SessionStatus status = summary.getStatus();
        if (status != null) {

            builder.put("status", status.name());
        }

        builder.put("totalLlmCalls", summary.getTotalLlmCalls());
        builder.put("totalPromptTokens", summary.getTotalPromptTokens());
        builder.put("totalCompletionTokens", summary.getTotalCompletionTokens());
        builder.put("totalTokens", summary.getTotalTokens());
        builder.put("totalCost", summary.getTotalCost());
        builder.put("durationMs", summary.getDurationMs());
        builder.put("childSessionsCount", summary.getChildSessionsCount());
        builder.put("childTotalCost", summary.getChildTotalCost());

        if (summary.getStartTime() != null) {

            builder.put("startTime", summary.getStartTime().toEpochMilli());
        }
        if (summary.getEndTime() != null) {

            builder.put("endTime", summary.getEndTime().toEpochMilli());
        }
        return builder.build();
    }
}
