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

package app.krista.extensions.hook.agentic.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for the private helper methods {@code truncateForLog} and {@code truncateArgs}
 * in {@link AgenticFlowService}.
 *
 * <p>Both methods are pure static-logic helpers that depend on no instance state.
 * They are accessed via reflection so that no production API contract is changed
 * (i.e. the methods remain private).</p>
 *
 * <p>The service is instantiated with {@code null} dependencies because neither
 * target method touches any injected collaborator.</p>
 */
public class TruncateForLogTest {

    /**
     * An object whose only property getter throws unconditionally, making it
     * impossible for Jackson to serialize the bean.  Used to exercise the
     * catch-block fall-back path in {@code truncateForLog}.
     */
    static class UnserializableValue {

        public String getValue() {

            throw new RuntimeException("cannot serialize");
        }
    }

    /** Maximum character length before truncation is applied. */
    private static final int TRUNCATION_LIMIT = 1000;

    private AgenticFlowService service;
    private Method truncateForLog;
    private Method truncateArgs;

    @BeforeEach
    void setUp() throws Exception {

        // The 3-argument constructor is sufficient; neither target method uses
        // sessionStore, toolPlanner, or chatMemoryMap.
        service = new AgenticFlowService(null, null, null);

        truncateForLog = AgenticFlowService.class
                .getDeclaredMethod("truncateForLog", Object.class);
        truncateForLog.setAccessible(true);

        truncateArgs = AgenticFlowService.class
                .getDeclaredMethod("truncateArgs", Map.class);
        truncateArgs.setAccessible(true);
    }

    // -------------------------------------------------------------------------
    // truncateForLog — branch 1: null input
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("truncateForLog(null) returns the literal string \"null\"")
    void truncateForLog_nullValue_returnsLiteralNull() throws Exception {

        String result = (String) truncateForLog.invoke(service, (Object) null);

        assertEquals("null", result);
    }

    // -------------------------------------------------------------------------
    // truncateForLog — branch 2: non-null value whose JSON is within 1000 chars
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("truncateForLog returns full JSON when serialized length is within 1000 characters")
    void truncateForLog_shortValue_returnsFullJson() throws Exception {

        Map<String, Object> input = Map.of("key", "value", "count", 42);

        String result = (String) truncateForLog.invoke(service, input);

        assertNotNull(result);
        assertTrue(result.length() <= TRUNCATION_LIMIT,
                "Result should not be truncated for a small payload, but length was: " + result.length());
        assertTrue(result.contains("key"),
                "Result should contain the original field names");
        assertTrue(result.contains("value"),
                "Result should contain the original field values");
    }

    // -------------------------------------------------------------------------
    // truncateForLog — branch 3: non-null value whose JSON exceeds 1000 chars
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("truncateForLog truncates JSON that exceeds 1000 characters and appends length metadata")
    void truncateForLog_longValue_returnsTruncatedJsonWithMetadata() throws Exception {

        // Build a string longer than 1000 characters when JSON-serialised.
        String longString = "x".repeat(1200);
        Map<String, Object> input = Map.of("data", longString);

        String result = (String) truncateForLog.invoke(service, input);

        assertNotNull(result);

        // The visible prefix is exactly 1000 characters of the original JSON.
        String prefix = result.substring(0, TRUNCATION_LIMIT);
        assertEquals(TRUNCATION_LIMIT, prefix.length(),
                "The truncated prefix must be exactly 1000 characters");

        // The suffix must include the truncation marker with the full length embedded.
        assertTrue(result.contains("...[truncated, full length:"),
                "Result must contain the truncation marker");

        // The reported full length must match what Jackson would have produced.
        // We extract the number from the marker and cross-check.
        int markerIndex = result.indexOf("...[truncated, full length: ");
        String afterMarker = result.substring(markerIndex + "...[truncated, full length: ".length());
        String reportedLengthStr = afterMarker.replace("]", "").trim();
        int reportedLength = Integer.parseInt(reportedLengthStr);
        assertTrue(reportedLength > TRUNCATION_LIMIT,
                "Reported full length (" + reportedLength + ") must exceed the truncation limit");
    }

    // -------------------------------------------------------------------------
    // truncateForLog — branch 4: Jackson serialization failure falls back to toString
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("truncateForLog falls back to toString() when Jackson cannot serialize the value")
    void truncateForLog_unserializableValue_fallsBackToToString() throws Exception {

        UnserializableValue bad = new UnserializableValue();

        // Jackson will throw when it calls getValue(); the catch block must
        // fall back to bad.toString() rather than propagating the exception.
        String result = (String) truncateForLog.invoke(service, bad);

        assertNotNull(result, "Result must not be null even when serialization fails");
        // The default Object.toString() always contains the class name.
        assertTrue(result.contains("UnserializableValue"),
                "Fall-back result should be the object's toString() output, was: " + result);
    }

    // -------------------------------------------------------------------------
    // truncateArgs — branch 1: null map
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("truncateArgs(null) returns the literal string \"{}\"")
    void truncateArgs_nullMap_returnsEmptyJsonObject() throws Exception {

        String result = (String) truncateArgs.invoke(service, (Object) null);

        assertEquals("{}", result);
    }

    // -------------------------------------------------------------------------
    // truncateArgs — branch 2: empty map
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("truncateArgs(empty map) returns the literal string \"{}\"")
    void truncateArgs_emptyMap_returnsEmptyJsonObject() throws Exception {

        String result = (String) truncateArgs.invoke(service, Map.of());

        assertEquals("{}", result);
    }

    // -------------------------------------------------------------------------
    // truncateArgs — branch 3: non-empty map delegates to truncateForLog
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("truncateArgs(non-empty map) delegates to truncateForLog and returns JSON")
    void truncateArgs_nonEmptyMap_returnsJson() throws Exception {

        Map<String, Object> arguments = Map.of("param1", "hello", "param2", 99);

        String result = (String) truncateArgs.invoke(service, arguments);

        assertNotNull(result);
        assertTrue(result.contains("param1"),
                "Result should contain the argument keys");
        assertTrue(result.contains("hello"),
                "Result should contain the argument values");
        // Must be valid JSON object syntax
        assertTrue(result.startsWith("{") && result.endsWith("}"),
                "Result must be a JSON object literal, was: " + result);
    }
}
