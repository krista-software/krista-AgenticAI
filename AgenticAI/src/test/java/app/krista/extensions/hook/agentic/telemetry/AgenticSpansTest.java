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

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.context.Context;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for the W3C traceparent serialize/restore helpers in {@link AgenticSpans}.
 * These carry the cross-turn trace linkage that keeps a conversation as one SigNoz trace,
 * so the serialize → restore roundtrip must be lossless.
 */
class AgenticSpansTest {

    // W3C spec example ids — valid 32-hex trace id and 16-hex span id.
    private static final String TRACE_ID = "0af7651916cd43dd8448eb211c80319c";
    private static final String SPAN_ID = "b7ad6b7169203331";

    @Test
    @DisplayName("serializeTraceparent → contextFromTraceparent preserves trace id, span id and sampled flag")
    void traceparentRoundTripSampled() {
        SpanContext original = SpanContext.create(
                TRACE_ID, SPAN_ID, TraceFlags.getSampled(), TraceState.getDefault());

        String traceparent = AgenticSpans.serializeTraceparent(Span.wrap(original));
        assertEquals("00-" + TRACE_ID + "-" + SPAN_ID + "-01", traceparent);

        Context restored = AgenticSpans.contextFromTraceparent(traceparent);
        assertNotNull(restored);
        SpanContext roundTripped = Span.fromContext(restored).getSpanContext();
        assertEquals(TRACE_ID, roundTripped.getTraceId());
        assertEquals(SPAN_ID, roundTripped.getSpanId());
        assertTrue(roundTripped.isSampled());
        assertTrue(roundTripped.isRemote());
    }

    @Test
    @DisplayName("unsampled flag round-trips as 00")
    void traceparentRoundTripUnsampled() {
        SpanContext original = SpanContext.create(
                TRACE_ID, SPAN_ID, TraceFlags.getDefault(), TraceState.getDefault());

        String traceparent = AgenticSpans.serializeTraceparent(Span.wrap(original));
        assertEquals("00-" + TRACE_ID + "-" + SPAN_ID + "-00", traceparent);

        SpanContext restored = Span.fromContext(AgenticSpans.contextFromTraceparent(traceparent)).getSpanContext();
        assertFalse(restored.isSampled());
    }

    @Test
    void serializeTraceparentNullSpanReturnsNull() {
        assertNull(AgenticSpans.serializeTraceparent(null));
    }

    @Test
    void serializeTraceparentInvalidContextReturnsNull() {
        assertNull(AgenticSpans.serializeTraceparent(Span.getInvalid()));
    }

    @Test
    void serializeCurrentTraceparentReturnsNullWhenNoActiveSpan() {
        // No span has been made current on this thread, so Span.current() is the invalid span.
        assertNull(AgenticSpans.serializeCurrentTraceparent());
    }

    @Test
    void contextFromTraceparentNullOrBlankReturnsNull() {
        assertNull(AgenticSpans.contextFromTraceparent(null));
        assertNull(AgenticSpans.contextFromTraceparent(""));
        assertNull(AgenticSpans.contextFromTraceparent("   "));
    }

    @Test
    void contextFromTraceparentMalformedReturnsNull() {
        assertNull(AgenticSpans.contextFromTraceparent("not-a-traceparent"));
        assertNull(AgenticSpans.contextFromTraceparent("00-onlythree-parts"));
        // All-zero ids are syntactically 4 parts but not a valid span context.
        assertNull(AgenticSpans.contextFromTraceparent(
                "00-00000000000000000000000000000000-0000000000000000-01"));
    }
}
