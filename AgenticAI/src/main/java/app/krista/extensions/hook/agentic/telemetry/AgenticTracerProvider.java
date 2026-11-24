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

import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.TracerProvider;
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import io.opentelemetry.sdk.trace.samplers.Sampler;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Owns AgenticAI's OpenTelemetry tracer.
 *
 * <p>The platform's {@code TelemetryAccess} ships logs and metrics to SigNoz but its
 * {@code SdkTracerProvider} is built without a span exporter, so spans created against it
 * are dropped. To make traces actually reach SigNoz from AgenticAI without requiring any
 * platform-side change, this class stands up its own {@link SdkTracerProvider} with an
 * {@link OtlpHttpSpanExporter} pointed at the SigNoz OTLP/HTTP endpoint.
 *
 * <p>Endpoint resolution (first non-blank wins):
 * <ol>
 *   <li>{@code AGENTIC_OTEL_TRACE_ENDPOINT} env var — explicit traces endpoint</li>
 *   <li>{@code OTEL_EXPORTER_OTLP_ENDPOINT} env var — base OTLP endpoint, {@code /v1/traces} appended</li>
 *   <li>{@code AGENTIC_OTEL_ENDPOINT} env var — explicit base endpoint, {@code /v1/traces} appended</li>
 * </ol>
 * <p>If none are set, traces are emitted into a no-op tracer (kept silent so this is safe to
 * include in environments without a SigNoz collector configured).
 *
 * <p>Service name is read from {@code AGENTIC_OTEL_SERVICE_NAME} (default {@code agentic-ai}).
 *
 * <p>Singleton; lazy-initialised on first {@link #getTracer()} call.
 */
public final class AgenticTracerProvider {

    private static final Logger LOGGER = LoggerFactory.getLogger(AgenticTracerProvider.class);

    private static final String INSTRUMENTATION_NAME = "agentic-ai";
    private static final String SERVICE_NAME_DEFAULT = "agentic-ai";
    private static final String SERVICE_NAME_ENV = "AGENTIC_OTEL_SERVICE_NAME";
    private static final String TRACE_ENDPOINT_ENV = "AGENTIC_OTEL_TRACE_ENDPOINT";
    private static final String OTEL_BASE_ENDPOINT_ENV = "OTEL_EXPORTER_OTLP_ENDPOINT";
    private static final String AGENTIC_BASE_ENDPOINT_ENV = "AGENTIC_OTEL_ENDPOINT";

    private static volatile AgenticTracerProvider instance;

    private final Tracer tracer;
    private final boolean exporterConfigured;
    // Held as a field so flush() can drain pending spans at request-boundary. Null when
    // the tracer is configured as no-op (no endpoint).
    private final SdkTracerProvider sdkTracerProvider;

    private AgenticTracerProvider() {

        String serviceName = firstNonBlank(readConfig(SERVICE_NAME_ENV), SERVICE_NAME_DEFAULT);//read from env first, then fallback to default
        Resource resource = Resource.getDefault().merge(Resource.create(
                Attributes.of(AttributeKey.stringKey("service.name"), serviceName)));

        String tracesEndpoint = resolveTracesEndpoint();
        if (tracesEndpoint == null) {

            LOGGER.warn("[TELEMETRY] No OTLP traces endpoint configured (set {}, {} or {}). " +
                            "Spans will be created but not exported.",
                    TRACE_ENDPOINT_ENV, OTEL_BASE_ENDPOINT_ENV, AGENTIC_BASE_ENDPOINT_ENV);
            this.tracer = TracerProvider.noop().get(INSTRUMENTATION_NAME);
            this.exporterConfigured = false;
            this.sdkTracerProvider = null;
            return;
        }

        OtlpHttpSpanExporter spanExporter = OtlpHttpSpanExporter.builder()
                .setEndpoint(tracesEndpoint)
                .setTimeout(Duration.ofSeconds(10))
                .build();

        this.sdkTracerProvider = SdkTracerProvider.builder()
                .setResource(resource)
                // Force-sample every span. The SDK default is parentBased(alwaysOn), which
                // OBEYS the parent's sampled flag — so when our spans are children of the
                // platform's POST span (or a restored conversation-root context) and that
                // parent is unsampled, the entire chain (flow.*, llm.call + sub-spans,
                // tool.call) is dropped before export and only the platform's own span shows
                // in SigNoz. alwaysOn ignores the inherited flag so our spans always export.
                .setSampler(Sampler.alwaysOn())
                // BatchSpanProcessor buffers spans and exports them in one batched HTTP call.
                // The per-request forceFlush() in the request handler drains the batch before the
                // response returns, so all of the turn's spans reach the collector even though the
                // agentic-ai extension runs in a small, short-lived JVM. Our spans share the
                // platform's POST trace (inherited Context.current()), so the trace root is the
                // platform-exported POST span and the trace renders reliably.
                .addSpanProcessor(BatchSpanProcessor.builder(spanExporter)
                        .setScheduleDelay(Duration.ofMillis(500))
                        .build())
                .build();

        // Obtain the tracer straight from the SdkTracerProvider rather than wrapping it in an
        // OpenTelemetrySdk aggregator. This mirrors the platform's own TelemetryAccessUtil (which
        // builds SdkTracerProvider/SdkLoggerProvider/SdkMeterProvider directly) and avoids a
        // compile/runtime dependency on the opentelemetry-sdk aggregator artifact, which newer
        // krista-apis/core no longer ships. We serialise/restore trace context manually via
        // AgenticSpans traceparent helpers, so a global ContextPropagators is not needed here.
        this.tracer = this.sdkTracerProvider.get(INSTRUMENTATION_NAME);
        this.exporterConfigured = true;

        LOGGER.info("[TELEMETRY] AgenticAI OTel tracer initialised - service={}, endpoint={}",
                serviceName, tracesEndpoint);

        // Best-effort flush on JVM shutdown so terminal spans are not lost.
        Runtime.getRuntime().addShutdownHook(new Thread(() ->
                sdkTracerProvider.shutdown().join(5, TimeUnit.SECONDS),
                "agentic-otel-shutdown"));
    }

    public static AgenticTracerProvider getInstance() {

        if (instance == null) {

            synchronized (AgenticTracerProvider.class) {

                if (instance == null) {

                    instance = new AgenticTracerProvider();
                }
            }
        }
        return instance;
    }

    public Tracer getTracer() {

        return tracer;
    }

    public boolean isExporterConfigured() {

        return exporterConfigured;
    }

    /**
     * Drains the BatchSpanProcessor synchronously so spans created in this request reach the
     * collector before the JVM potentially exits. Required because the agentic-ai extension JVM
     * is small (-Xmx250m, ExitOnOutOfMemoryError) and can terminate before the batch interval
     * elapses, dropping unflushed spans.
     *
     * <p>Safe to call from any thread. No-op when the tracer is no-op (no endpoint). Bounded
     * by {@code timeoutMillis} so a slow/unreachable collector doesn't stall the request.
     */
    public void flush(long timeoutMillis) {

        if (sdkTracerProvider == null) {

            return;
        }
        try {

            sdkTracerProvider.forceFlush().join(timeoutMillis, TimeUnit.MILLISECONDS);
        } catch (Throwable t) {

            LOGGER.debug("[TELEMETRY] forceFlush suppressed: {}", t.getMessage());
        }
    }

    private static String resolveTracesEndpoint() {

        // Prefer the explicit traces endpoint (env var or -D system property).
        String explicit = readConfig(TRACE_ENDPOINT_ENV);
        if (isPresent(explicit)) {

            return explicit;
        }
        // Else fall back to a base OTLP endpoint and append /v1/traces.
        String base = firstNonBlank(
                readConfig(OTEL_BASE_ENDPOINT_ENV),
                readConfig(AGENTIC_BASE_ENDPOINT_ENV),
                readPlatformTelemetryEndpoint());
        if (!isPresent(base)) {

            return null;
        }
        String trimmed = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
        return trimmed + "/v1/traces";
    }

    /**
     * Reflectively reads the platform's existing telemetry endpoint from
     * {@code com.krista.core.spec.Spec.getTelemetryAccess().endpoint()}.
     *
     * <p>Used as a fallback so AgenticAI inherits the SigNoz endpoint that the platform
     * already has in {@code krista-config.json} (the same one used today for logs and
     * metrics export). This means an operator who has set up SigNoz once for the platform
     * gets AgenticAI traces flowing without having to set any extra env var.
     *
     * <p>Reflection is used instead of a hard import so AgenticAI doesn't take a build-time
     * dependency on platform-internal classes; if the class is unavailable at runtime the
     * fallback simply returns {@code null} and resolution moves on.
     */
    private static String readPlatformTelemetryEndpoint() {

        try {

            Class<?> specClass = Class.forName("com.krista.core.spec.Spec");
            Object specTelemetry = specClass.getMethod("getTelemetryAccess").invoke(null);
            if (specTelemetry == null) {

                return null;
            }
            Object endpoint = specTelemetry.getClass().getMethod("endpoint").invoke(specTelemetry);
            return endpoint != null ? endpoint.toString() : null;
        } catch (Throwable t) {

            LOGGER.debug("[TELEMETRY] Could not read platform Spec.getTelemetryAccess().endpoint(): {}",
                    t.getMessage());
            return null;
        }
    }

    /**
     * Reads a configuration value from the environment first, then from JVM system properties.
     * System property names use a lowercased dotted form: {@code OTEL_EXPORTER_OTLP_ENDPOINT}
     * also matches {@code -Dotel.exporter.otlp.endpoint=...}, which is the convention the
     * standard OTel SDK uses.
     */
    private static String readConfig(String envName) {

        String fromEnv = System.getenv(envName);
        if (isPresent(fromEnv)) {

            return fromEnv;
        }
        String propName = envName.toLowerCase().replace('_', '.');
        return System.getProperty(propName);
    }

    private static boolean isPresent(String s) {

        return s != null && !s.isBlank();
    }

    private static String firstNonBlank(String... values) {

        if (values == null) {

            return null;
        }
        for (String v : values) {

            if (isPresent(v)) {

                return v;
            }
        }
        return null;
    }
}
