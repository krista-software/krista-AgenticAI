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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Unit tests for {@link AgenticFlowService#readableToolLabel(String)}, which turns the
 * platform's tool descriptor (often a JSON blob) into a short, human-readable span-name
 * label. Verifies JSON extraction, plain-text passthrough, null handling and truncation.
 */
class ReadableToolLabelTest {

    @Test
    void jsonWithToolTypeReturnsToolType() {
        assertEquals("catalog_request",
                AgenticFlowService.readableToolLabel("{\"toolType\":\"catalog_request\",\"invokerId\":\"abc\"}"));
    }

    @Test
    void jsonWithNameButNoToolTypeReturnsName() {
        assertEquals("ask_a_person",
                AgenticFlowService.readableToolLabel("{\"name\":\"ask_a_person\"}"));
    }

    @Test
    void plainTextReturnedAsIs() {
        assertEquals("simpleTool", AgenticFlowService.readableToolLabel("simpleTool"));
    }

    @Test
    void leadingAndTrailingWhitespaceTrimmed() {
        assertEquals("simpleTool", AgenticFlowService.readableToolLabel("  simpleTool  "));
    }

    @Test
    void nullReturnsUnknown() {
        assertEquals("unknown", AgenticFlowService.readableToolLabel(null));
    }

    @Test
    void blankReturnsUnknown() {
        assertEquals("unknown", AgenticFlowService.readableToolLabel("   "));
    }

    @Test
    void longPlainTextTruncatedTo40CharsWithEllipsis() {
        String longName = "a".repeat(50);

        String result = AgenticFlowService.readableToolLabel(longName);

        assertEquals("a".repeat(40) + "…", result);
        assertEquals(41, result.length()); // 40 chars + single ellipsis character
    }

    @Test
    void malformedJsonFallsThroughToTruncation() {
        // Starts with '{' so JSON parse is attempted, fails, then falls through to the raw
        // (short) string — no exception escapes.
        String input = "{not valid json";
        assertEquals(input, AgenticFlowService.readableToolLabel(input));
    }
}
