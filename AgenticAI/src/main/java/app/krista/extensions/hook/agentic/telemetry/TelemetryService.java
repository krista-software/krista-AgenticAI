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

import app.krista.common.monitoring.TelemetryAccess;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.logs.Severity;
import io.opentelemetry.api.metrics.LongCounter;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thin wrapper around the platform's {@link TelemetryAccess} OpenTelemetry bridge.
 *
 * <p>Exposes simple record* methods so emitter classes do not depend on the OpenTelemetry
 * SDK directly. The platform layer exports these logs and metrics to SigNoz.
 *
 * <p>Singleton — obtained via {@link #getInstance()}.
 */
public final class TelemetryService {

    private final TelemetryAccess telemetryAccess;
    // Counter instruments cached by metric name — built once and reused, the idiomatic OTel
    // pattern (the SDK dedups internally either way, but this avoids rebuilding per call).
    private final ConcurrentHashMap<String, LongCounter> counters = new ConcurrentHashMap<>();
    private static volatile TelemetryService instance;

    private TelemetryService(TelemetryAccess telemetryAccess) {

        this.telemetryAccess = telemetryAccess;
    }

    /**
     * Record a counter metric with attributes.
     */
    public void recordCounter(String metricName, String description, String unit,
                              long counterValue, Attributes attributes) {

        LongCounter counter = counters.computeIfAbsent(metricName, name ->
                telemetryAccess.getMeter()
                        .counterBuilder(name)
                        .setDescription(description)
                        .setUnit(unit)
                        .build());
        counter.add(counterValue, attributes);
    }

    /**
     * Record a log record with attributes.
     */
    public void recordLog(String logName, Severity severity, String body, Attributes attributes) {

        telemetryAccess.getOtelSdk()
                .getLogsBridge()
                .get(logName)
                .logRecordBuilder()
                .setSeverity(severity)
                .setAllAttributes(attributes)
                .setTimestamp(Instant.now())
                .setBody(body)
                .emit();
    }

    /**
     * Returns the process-wide singleton, initialising it lazily on first call.
     */
    public static TelemetryService getInstance() {

        if (instance == null) {

            synchronized (TelemetryService.class) {

                if (instance == null) {

                    instance = new TelemetryService(TelemetryAccess.getInstance());
                }
            }
        }
        return instance;
    }
}
