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

package app.krista.extensions.hook.agentic.util;

import app.krista.extensions.hook.agentic.dto.AgenticToolDefinition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ToolNameValidator.
 */
public class ToolNameValidatorTest {

    private List<AgenticToolDefinition> availableTools;
    private static final String VALID_TOOL_1 = "{\"toolType\":\"catalog_request\",\"invokerId\":\"INV_abc\",\"catalogRequestId\":\"localDomainRequest_123\"}";
    private static final String VALID_TOOL_2 = "{\"toolType\":\"ask_a_person\",\"humanInteractionId\":\"builtin_ask_person\"}";
    private static final String VALID_TOOL_3 = "{\"toolType\":\"inform_a_person\",\"targetAudience\":{\"type\":\"com.krista.net.model.conv.TargetAudience\"}}";

    @BeforeEach
    void setUp() {
        availableTools = new ArrayList<>();
        availableTools.add(createToolDefinition(VALID_TOOL_1, "List documents", false));
        availableTools.add(createToolDefinition(VALID_TOOL_2, "Ask user question", true));
        availableTools.add(createToolDefinition(VALID_TOOL_3, "Inform user", true));
    }

    private AgenticToolDefinition createToolDefinition(String name, String description, boolean isHelper) {
        return new AgenticToolDefinition(
            name,
            description,
            isHelper ? "ask_a_person" : "catalog_request",
            new ArrayList<>(),
            isHelper
        );
    }

    @Test
    @DisplayName("isValidToolName - should return true for exact match")
    void testIsValidToolName_ExactMatch() {
        assertTrue(ToolNameValidator.isValidToolName(VALID_TOOL_1, availableTools));
        assertTrue(ToolNameValidator.isValidToolName(VALID_TOOL_2, availableTools));
        assertTrue(ToolNameValidator.isValidToolName(VALID_TOOL_3, availableTools));
    }

    @Test
    @DisplayName("isValidToolName - should return false for non-existent tool")
    void testIsValidToolName_NoMatch() {
        String invalidTool = "{\"toolType\":\"nonexistent\",\"id\":\"xyz\"}";
        assertFalse(ToolNameValidator.isValidToolName(invalidTool, availableTools));
    }

    @Test
    @DisplayName("isValidToolName - should return false for null input")
    void testIsValidToolName_NullInput() {
        assertFalse(ToolNameValidator.isValidToolName(null, availableTools));
    }

    @Test
    @DisplayName("isValidToolName - should return false for empty string")
    void testIsValidToolName_EmptyString() {
        assertFalse(ToolNameValidator.isValidToolName("", availableTools));
    }

    @Test
    @DisplayName("isValidToolName - should return false for partial match")
    void testIsValidToolName_PartialMatch() {
        // Substring of valid tool should not match
        String partial = "{\"toolType\":\"catalog_request\"}";
        assertFalse(ToolNameValidator.isValidToolName(partial, availableTools));
    }

    @Test
    @DisplayName("isValidToolName - should return false for case-sensitive mismatch")
    void testIsValidToolName_CaseSensitivity() {
        // Different case should not match
        String differentCase = VALID_TOOL_1.replace("catalog_request", "CATALOG_REQUEST");
        assertFalse(ToolNameValidator.isValidToolName(differentCase, availableTools));
    }

    @Test
    @DisplayName("isValidToolName - should return false for whitespace differences")
    void testIsValidToolName_WhitespaceDifferences() {
        // Extra whitespace should not match
        String withWhitespace = VALID_TOOL_1.replace(",", ", ");
        assertFalse(ToolNameValidator.isValidToolName(withWhitespace, availableTools));
    }

    @Test
    @DisplayName("isValidToolName - should handle empty tool list")
    void testIsValidToolName_EmptyToolList() {
        List<AgenticToolDefinition> emptyList = new ArrayList<>();
        assertFalse(ToolNameValidator.isValidToolName(VALID_TOOL_1, emptyList));
    }

    @Test
    @DisplayName("buildRetryContext - should contain error header")
    void testBuildRetryContext_ContainsErrorHeader() {
        String invalidTool = "{\"toolType\":\"invalid\"}";
        String context = ToolNameValidator.buildRetryContext(invalidTool, availableTools);
        
        assertTrue(context.contains("ERROR"));
        assertTrue(context.contains("does not exist"));
    }

    @Test
    @DisplayName("buildRetryContext - should contain the invalid tool name")
    void testBuildRetryContext_ContainsInvalidTool() {
        String invalidTool = "{\"toolType\":\"invalid\"}";
        String context = ToolNameValidator.buildRetryContext(invalidTool, availableTools);
        
        assertTrue(context.contains(invalidTool));
    }

    @Test
    @DisplayName("buildRetryContext - should list all tools when less than 5")
    void testBuildRetryContext_ListsAllToolsWhenLessThan5() {
        String invalidTool = "{\"toolType\":\"invalid\"}";
        String context = ToolNameValidator.buildRetryContext(invalidTool, availableTools);
        
        // Should show all 3 tools
        assertTrue(context.contains(VALID_TOOL_1));
        assertTrue(context.contains(VALID_TOOL_2));
        assertTrue(context.contains(VALID_TOOL_3));
        assertFalse(context.contains("... and ")); // No truncation message
    }

    @Test
    @DisplayName("buildRetryContext - should truncate when more than 5 tools")
    void testBuildRetryContext_TruncatesWhenMoreThan5() {
        // Create 7 tools
        List<AgenticToolDefinition> manyTools = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            manyTools.add(createToolDefinition(
                "{\"toolType\":\"tool_" + i + "\"}",
                "Tool " + i,
                false
            ));
        }
        
        String invalidTool = "{\"toolType\":\"invalid\"}";
        String context = ToolNameValidator.buildRetryContext(invalidTool, manyTools);
        
        // Should show truncation message
        assertTrue(context.contains("and 2 more tools"));
    }

    @Test
    @DisplayName("buildRetryContext - should contain critical instruction")
    void testBuildRetryContext_ContainsCriticalInstruction() {
        String invalidTool = "{\"toolType\":\"invalid\"}";
        String context = ToolNameValidator.buildRetryContext(invalidTool, availableTools);
        
        assertTrue(context.contains("CRITICAL"));
        assertTrue(context.contains("Copy the ENTIRE"));
    }

    @Test
    @DisplayName("buildRetryContext - should handle empty tool list")
    void testBuildRetryContext_EmptyToolList() {
        List<AgenticToolDefinition> emptyList = new ArrayList<>();
        String invalidTool = "{\"toolType\":\"invalid\"}";
        String context = ToolNameValidator.buildRetryContext(invalidTool, emptyList);
        
        assertTrue(context.contains("ERROR"));
        // Should not throw exception
        assertNotNull(context);
    }

    @Test
    @DisplayName("buildRetryContext - should handle null invalid tool name")
    void testBuildRetryContext_NullInvalidTool() {
        String context = ToolNameValidator.buildRetryContext(null, availableTools);
        
        assertTrue(context.contains("null"));
        assertTrue(context.contains("ERROR"));
    }
}
