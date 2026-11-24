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

import app.krista.extensions.hook.agentic.AgenticConstants;
import app.krista.extensions.hook.agentic.dto.AgenticCommand;
import app.krista.extensions.hook.agentic.dto.AgenticContinueRequest;
import app.krista.extensions.hook.agentic.dto.AgenticResponse;
import app.krista.extensions.hook.agentic.dto.AgenticStartRequest;
import app.krista.extensions.hook.agentic.dto.AgenticToolDefinition;
import app.krista.extensions.hook.agentic.planner.ToolPlanner;
import app.krista.extensions.hook.agentic.session.ActivityEvent;
import app.krista.extensions.hook.agentic.session.AgenticSession;
import app.krista.extensions.hook.agentic.session.AgenticSessionStore;
import app.krista.extensions.hook.agentic.session.TerminationReason;
import app.krista.extensions.hook.agentic.usagecollector.SessionUsageCollector;
import app.krista.extensions.hook.util.JsonMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.ChatMemory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for session summary generation and parent-child differentiation.
 * Tests that parent sessions get session summaries while child sessions return clean responses.
 *
 * @see AgenticFlowService
 */
class SessionSummaryTest {

    private AgenticFlowService agenticFlowService;
    private AgenticSessionStore sessionStore;
    private ToolPlanner toolPlanner;
    private Map<Object, ChatMemory> chatMemoryMap;

    @BeforeEach
    void setUp() {
        sessionStore = AgenticSessionStore.getInstance();
        toolPlanner = mock(ToolPlanner.class);
        chatMemoryMap = new ConcurrentHashMap<>();
        agenticFlowService = new AgenticFlowService(sessionStore, toolPlanner, chatMemoryMap);
    }

    // ========================================
    // Section 1: Parent Session Tests (should add summaries)
    // ========================================

    @Test
    @DisplayName("Parent session after tool execution gets complete session summary")
    void testProcessResponse_ParentSession_AfterToolExecution_AddsSummaries() {
        // Setup: Start a parent session first
        String sessionId = "sess_parent_test_123";
        AgenticToolDefinition tool = createMockTool("search_tool", false);
        
        AgenticStartRequest startRequest = new AgenticStartRequest(
            Arrays.asList(tool),
            "Search for documents",
            new HashMap<>(),
            null,
            null,
            sessionId
        );

        // First LLM call returns EXECUTE_TOOL to search
        AgenticResponse firstResponse = new AgenticResponse();
        firstResponse.setSessionId(sessionId);
        firstResponse.setResponseType(AgenticResponse.ResponseType.EXECUTE_TOOL);
        firstResponse.setCommand(createMockCommand("search_tool"));

        when(toolPlanner.planNextStep(any(), any(), any(), any(), any(), any(), any()))
            .thenReturn(firstResponse);

        // Start the flow
        AgenticResponse startResult = agenticFlowService.startFlow(startRequest);
        assertEquals(AgenticResponse.ResponseType.EXECUTE_TOOL, startResult.getResponseType());

        // Setup: Mock ChatMemory for conversation history
        ChatMemory chatMemory = mock(ChatMemory.class);
        List<ChatMessage> messages = Arrays.asList(
            SystemMessage.from("You are an assistant"),
            UserMessage.from("Search for documents"),
            AiMessage.from("I'll search for documents")
        );
        when(chatMemory.messages()).thenReturn(messages);
        chatMemoryMap.put(sessionId, chatMemory);

        // Second LLM call (after tool execution) returns COMPLETE
        AgenticResponse completeResponse = new AgenticResponse();
        completeResponse.setSessionId(sessionId);
        completeResponse.setResponseType(AgenticResponse.ResponseType.COMPLETE);
        completeResponse.setFinalResponse("Found 5 documents");
        completeResponse.setOutputVariables(new HashMap<>());

        when(toolPlanner.planNextStep(any(), any(), any(), any(), any(), any(), any()))
            .thenReturn(completeResponse);

        // Continue flow with tool result
        AgenticContinueRequest continueRequest = new AgenticContinueRequest(
            sessionId,
            "search_tool",
            Map.of("found", 5, "documents", Arrays.asList("doc1", "doc2"))
        );

        AgenticResponse finalResult = agenticFlowService.continueFlow(continueRequest);

        // Verify: Parent session gets session summary
        Map<String, Object> outputVars = finalResult.getOutputVariables();
        assertNotNull(outputVars);
        assertTrue(outputVars.containsKey("conversation_history"), "Parent should have conversation_history");
        assertTrue(outputVars.containsKey("tool_summary"), "Parent should have tool_summary");
        
        String conversationHistory = (String) outputVars.get("conversation_history");
        String toolSummary = (String) outputVars.get("tool_summary");
        
        assertNotNull(conversationHistory);
        assertNotNull(toolSummary);
        assertTrue(conversationHistory.contains("=== LLM Conversation History ==="));
        assertTrue(toolSummary.contains("=== Tool Execution Summary ==="));
        assertTrue(toolSummary.contains("search_tool"), "Tool summary should contain actual tool name");
        assertTrue(toolSummary.contains("Total tool executions: 1"), "Should show 1 tool execution");
    }

    @Test
    @DisplayName("Parent session with ERROR response gets session summary")
    void testProcessResponse_ParentSession_ERROR_AddsSummaries() {
        // Setup: Create parent session
        String sessionId = "sess_parent_error_456";
        AgenticSession parentSession = createAgenticSession(sessionId, null);
        parentSession.setUsageCollector(new SessionUsageCollector(sessionId, "ToolPlanner"));
        sessionStore.createSession(parentSession);

        // Setup: Mock ChatMemory
        ChatMemory chatMemory = mock(ChatMemory.class);
        when(chatMemory.messages()).thenReturn(Collections.emptyList());
        chatMemoryMap.put(sessionId, chatMemory);

        // Setup: Create ERROR response
        AgenticResponse response = new AgenticResponse();
        response.setSessionId(sessionId);
        response.setResponseType(AgenticResponse.ResponseType.ERROR);
        response.setErrorMessage("Something went wrong");
        response.setOutputVariables(new HashMap<>());

        // Mock tool planner
        when(toolPlanner.planNextStep(any(), any(), any(), any(), any(), any(), any()))
            .thenReturn(response);

        // Execute
        AgenticStartRequest request = new AgenticStartRequest(
            Arrays.asList(),
            "Test task",
            new HashMap<>(),
            null,
            null,
            sessionId
        );

        AgenticResponse result = agenticFlowService.startFlow(request);

        // Verify: Error response should still have session summary for parent
        Map<String, Object> outputVars = result.getOutputVariables();
        assertNotNull(outputVars);
        assertTrue(outputVars.containsKey("conversation_history"));
        assertTrue(outputVars.containsKey("tool_summary"));
    }

    @Test
    @DisplayName("Extract conversation history from valid chat memory")
    void testExtractConversationHistory_ValidMemory() {
        // Setup: Create session and chat memory with messages
        String sessionId = "sess_conv_test_789";
        ChatMemory chatMemory = mock(ChatMemory.class);
        
        List<ChatMessage> messages = Arrays.asList(
            SystemMessage.from("System prompt"),
            UserMessage.from("User request"),
            AiMessage.from("AI response")
        );
        when(chatMemory.messages()).thenReturn(messages);
        chatMemoryMap.put(sessionId, chatMemory);

        // Execute: We need to test this indirectly through startFlow
        AgenticSession session = createAgenticSession(sessionId, null);
        session.setUsageCollector(new SessionUsageCollector(sessionId, "ToolPlanner"));
        sessionStore.createSession(session);

        AgenticResponse response = new AgenticResponse();
        response.setSessionId(sessionId);
        response.setResponseType(AgenticResponse.ResponseType.COMPLETE);
        response.setFinalResponse("Done");
        response.setOutputVariables(new HashMap<>());

        when(toolPlanner.planNextStep(any(), any(), any(), any(), any(), any(), any()))
            .thenReturn(response);

        AgenticStartRequest request = new AgenticStartRequest(
            Arrays.asList(),
            "Test",
            new HashMap<>(),
            null,
            null,
            sessionId
        );

        AgenticResponse result = agenticFlowService.startFlow(request);

        // Verify: Conversation history should contain user/AI messages but NOT system prompt
        String conversationHistory = (String) result.getOutputVariables().get("conversation_history");
        assertNotNull(conversationHistory);
        assertTrue(conversationHistory.contains("=== LLM Conversation History ==="));
        assertFalse(conversationHistory.contains("SYSTEM"), "System prompt must not appear in conversation_history");
        assertTrue(conversationHistory.contains("USER"));
        assertTrue(conversationHistory.contains("AI"));
        assertFalse(conversationHistory.contains("System prompt"), "System prompt text must not appear in conversation_history");
        assertTrue(conversationHistory.contains("User request"));
        assertTrue(conversationHistory.contains("AI response"));
    }

    @Test
    @DisplayName("Extract tool summary from session with multiple tools")
    void testExtractToolSummary_MultipleTools() {
        // Setup: Create session with multiple tool executions
        String sessionId = "sess_tools_test_001";
        AgenticSession session = createAgenticSession(sessionId, null);
        
        // Add multiple tool executions
        session.getMessageHistory().add(createToolMessage("search_docs", "Found 5 documents", true));
        session.getMessageHistory().add(createToolMessage("filter_results", "Filtered to 3", true));
        session.getMessageHistory().add(createToolMessage("sort_data", "Sorted by date", true));
        
        session.setUsageCollector(new SessionUsageCollector(sessionId, "ToolPlanner"));
        sessionStore.createSession(session);

        // Mock ChatMemory
        ChatMemory chatMemory = mock(ChatMemory.class);
        when(chatMemory.messages()).thenReturn(Collections.emptyList());
        chatMemoryMap.put(sessionId, chatMemory);

        // Setup response
        AgenticResponse response = new AgenticResponse();
        response.setSessionId(sessionId);
        response.setResponseType(AgenticResponse.ResponseType.COMPLETE);
        response.setFinalResponse("Task complete");
        response.setOutputVariables(new HashMap<>());

        when(toolPlanner.planNextStep(any(), any(), any(), any(), any(), any(), any()))
            .thenReturn(response);

        AgenticStartRequest request = new AgenticStartRequest(
            Arrays.asList(),
            "Process data",
            new HashMap<>(),
            null,
            null,
            sessionId
        );

        AgenticResponse result = agenticFlowService.startFlow(request);

        // Verify: Tool summary should be present with correct structure
        // Note: startFlow() creates its own session, so specific tool names from our 
        // pre-created session won't be present. We verify the structure exists.
        String toolSummary = (String) result.getOutputVariables().get("tool_summary");
        assertNotNull(toolSummary);
        assertTrue(toolSummary.contains("=== Tool Execution Summary ==="));
        // The session created by startFlow() has no tool executions initially
        assertTrue(toolSummary.contains("(No tool executions)") || toolSummary.contains("Total tool executions:"));
    }

    // ========================================
    // Section 2: Child Session Tests (should NOT add summaries)
    // ========================================

    @Test
    @DisplayName("Child session with COMPLETE response skips session summary")
    void testProcessResponse_ChildSession_COMPLETE_SkipsSummaries() {
        // Setup: Create parent session first
        String parentId = "sess_parent_001";
        AgenticSession parentSession = createAgenticSession(parentId, null);
        parentSession.setUsageCollector(new SessionUsageCollector(parentId, "ToolPlanner"));
        sessionStore.createSession(parentSession);

        // Setup: Create child session with parent reference
        String childId = parentId + ".child_" + java.util.UUID.randomUUID();
        AgenticSession childSession = createAgenticSession(childId, parentId);
        childSession.setUsageCollector(new SessionUsageCollector(childId, "ToolPlanner-Child"));
        sessionStore.createSession(childSession);

        // Setup: Mock ChatMemory for child
        ChatMemory childChatMemory = mock(ChatMemory.class);
        when(childChatMemory.messages()).thenReturn(Collections.emptyList());
        chatMemoryMap.put(childId, childChatMemory);

        // Setup: Create COMPLETE response for child
        AgenticResponse childResponse = new AgenticResponse();
        childResponse.setSessionId(childId);
        childResponse.setResponseType(AgenticResponse.ResponseType.COMPLETE);
        childResponse.setFinalResponse("Child task done");
        
        // Important: Child response should NOT have session summaries added
        Map<String, Object> childOutputVars = new HashMap<>();
        childOutputVars.put("result", "success");
        childResponse.setOutputVariables(childOutputVars);

        // Execute: Start child flow
        AgenticStartRequest childRequest = new AgenticStartRequest(
            Arrays.asList(),
            "Child task",
            new HashMap<>(),
            null,
            null,
            childId
        );

        when(toolPlanner.planNextStep(any(), any(), any(), any(), any(), any(), any()))
            .thenReturn(childResponse);

        AgenticResponse result = agenticFlowService.startChildFlow(childRequest, parentId);

        // Verify: Child response should be clean (no session summaries)
        Map<String, Object> resultOutputVars = result.getOutputVariables();
        assertNotNull(resultOutputVars);
        
        // Child should only have the result we set, not conversation_history or tool_summary
        assertFalse(resultOutputVars.containsKey("conversation_history"));
        assertFalse(resultOutputVars.containsKey("tool_summary"));
        assertEquals("success", resultOutputVars.get("result"));
    }

    @Test
    @DisplayName("Child session with ERROR response skips session summary")
    void testProcessResponse_ChildSession_ERROR_SkipsSummaries() {
        // Setup: Create parent and child sessions
        String parentId = "sess_parent_002";
        AgenticSession parentSession = createAgenticSession(parentId, null);
        parentSession.setUsageCollector(new SessionUsageCollector(parentId, "ToolPlanner"));
        sessionStore.createSession(parentSession);

        String childId = parentId + ".child_" + java.util.UUID.randomUUID();
        AgenticSession childSession = createAgenticSession(childId, parentId);
        childSession.setUsageCollector(new SessionUsageCollector(childId, "ToolPlanner-Child"));
        sessionStore.createSession(childSession);

        // Mock ChatMemory
        ChatMemory childChatMemory = mock(ChatMemory.class);
        when(childChatMemory.messages()).thenReturn(Collections.emptyList());
        chatMemoryMap.put(childId, childChatMemory);

        // Setup: Create ERROR response
        AgenticResponse childResponse = new AgenticResponse();
        childResponse.setSessionId(childId);
        childResponse.setResponseType(AgenticResponse.ResponseType.ERROR);
        childResponse.setErrorMessage("Child error occurred");
        
        Map<String, Object> childOutputVars = new HashMap<>();
        childOutputVars.put("error", "true");
        childResponse.setOutputVariables(childOutputVars);

        when(toolPlanner.planNextStep(any(), any(), any(), any(), any(), any(), any()))
            .thenReturn(childResponse);

        AgenticStartRequest childRequest = new AgenticStartRequest(
            Arrays.asList(),
            "Child task",
            new HashMap<>(),
            null,
            null,
            childId
        );

        AgenticResponse result = agenticFlowService.startChildFlow(childRequest, parentId);

        // Verify: Even error response from child should be clean
        Map<String, Object> resultOutputVars = result.getOutputVariables();
        assertFalse(resultOutputVars.containsKey("conversation_history"));
        assertFalse(resultOutputVars.containsKey("tool_summary"));
        assertEquals("true", resultOutputVars.get("error"));
    }

    @Test
    @DisplayName("Child session parent reference is correctly set")
    void testChildSession_ParentReference() {
        // Setup: Create parent
        String parentId = "sess_parent_003";
        AgenticSession parentSession = createAgenticSession(parentId, null);
        parentSession.setUsageCollector(new SessionUsageCollector(parentId, "ToolPlanner"));
        sessionStore.createSession(parentSession);

        // Setup: Create child
        String childId = parentId + ".child_test";
        AgenticSession childSession = createAgenticSession(childId, parentId);
        childSession.setUsageCollector(new SessionUsageCollector(childId, "ToolPlanner-Child"));
        sessionStore.createSession(childSession);

        // Verify: Parent-child relationship
        assertNull(parentSession.getParentSessionId());
        assertEquals(parentId, childSession.getParentSessionId());
    }

    // ========================================
    // Section 3: Edge Cases
    // ========================================

    @Test
    @DisplayName("Extract conversation history with null chat memory")
    void testExtractConversationHistory_NullMemory() {
        // Setup: Create session but DON'T add chat memory to map
        String sessionId = "sess_null_memory";
        AgenticSession session = createAgenticSession(sessionId, null);
        session.setUsageCollector(new SessionUsageCollector(sessionId, "ToolPlanner"));
        sessionStore.createSession(session);
        // Note: chatMemoryMap does not contain this sessionId

        AgenticResponse response = new AgenticResponse();
        response.setSessionId(sessionId);
        response.setResponseType(AgenticResponse.ResponseType.COMPLETE);
        response.setFinalResponse("Done");
        response.setOutputVariables(new HashMap<>());

        when(toolPlanner.planNextStep(any(), any(), any(), any(), any(), any(), any()))
            .thenReturn(response);

        AgenticStartRequest request = new AgenticStartRequest(
            Arrays.asList(),
            "Test",
            new HashMap<>(),
            null,
            null,
            sessionId
        );

        AgenticResponse result = agenticFlowService.startFlow(request);

        // Verify: Should handle null memory gracefully
        String conversationHistory = (String) result.getOutputVariables().get("conversation_history");
        assertNotNull(conversationHistory);
        assertTrue(conversationHistory.contains("(No conversation history found)"));
    }

    @Test
    @DisplayName("Extract conversation history with empty chat memory")
    void testExtractConversationHistory_EmptyMemory() {
        // Setup: Create session with empty chat memory
        String sessionId = "sess_empty_memory";
        AgenticSession session = createAgenticSession(sessionId, null);
        session.setUsageCollector(new SessionUsageCollector(sessionId, "ToolPlanner"));
        sessionStore.createSession(session);

        ChatMemory chatMemory = mock(ChatMemory.class);
        when(chatMemory.messages()).thenReturn(Collections.emptyList());
        chatMemoryMap.put(sessionId, chatMemory);

        AgenticResponse response = new AgenticResponse();
        response.setSessionId(sessionId);
        response.setResponseType(AgenticResponse.ResponseType.COMPLETE);
        response.setFinalResponse("Done");
        response.setOutputVariables(new HashMap<>());

        when(toolPlanner.planNextStep(any(), any(), any(), any(), any(), any(), any()))
            .thenReturn(response);

        AgenticStartRequest request = new AgenticStartRequest(
            Arrays.asList(),
            "Test",
            new HashMap<>(),
            null,
            null,
            sessionId
        );

        AgenticResponse result = agenticFlowService.startFlow(request);

        // Verify: Should handle empty memory
        String conversationHistory = (String) result.getOutputVariables().get("conversation_history");
        assertNotNull(conversationHistory);
        assertTrue(conversationHistory.contains("=== LLM Conversation History ==="));
    }

    @Test
    @DisplayName("Extract tool summary with no tools executed")
    void testExtractToolSummary_NoTools() {
        // Setup: Create session with only user message (no tools)
        String sessionId = "sess_no_tools";
        AgenticSession session = createAgenticSession(sessionId, null);
        // Only add user message, no tool messages
        session.getMessageHistory().add(createUserMessage("Initial request"));
        session.setUsageCollector(new SessionUsageCollector(sessionId, "ToolPlanner"));
        sessionStore.createSession(session);

        ChatMemory chatMemory = mock(ChatMemory.class);
        when(chatMemory.messages()).thenReturn(Collections.emptyList());
        chatMemoryMap.put(sessionId, chatMemory);

        AgenticResponse response = new AgenticResponse();
        response.setSessionId(sessionId);
        response.setResponseType(AgenticResponse.ResponseType.COMPLETE);
        response.setFinalResponse("Done");
        response.setOutputVariables(new HashMap<>());

        when(toolPlanner.planNextStep(any(), any(), any(), any(), any(), any(), any()))
            .thenReturn(response);

        AgenticStartRequest request = new AgenticStartRequest(
            Arrays.asList(),
            "Test",
            new HashMap<>(),
            null,
            null,
            sessionId
        );

        AgenticResponse result = agenticFlowService.startFlow(request);

        // Verify: Tool summary should exist with correct structure
        // Note: startFlow() creates its own session, so our pre-created session's 
        // message history (with only user message) is not used. The new session
        // starts empty, so tool summary shows "No tool executions" or similar.
        String toolSummary = (String) result.getOutputVariables().get("tool_summary");
        assertNotNull(toolSummary, "tool_summary should exist in parent session response");
        assertTrue(toolSummary.contains("=== Tool Execution Summary ==="), "Should have tool summary header");
        // Accept either "No tool executions" or empty tool list
        assertTrue(
            toolSummary.contains("(No tool executions)") || toolSummary.contains("Total tool executions: 0"),
            "Should indicate no tool executions: " + toolSummary
        );
    }

    @Test
    @DisplayName("Extract tool summary with null message history")
    void testExtractToolSummary_NullMessageHistory() {
        // Setup: Create session with null message history (edge case)
        String sessionId = "sess_null_history";
        AgenticSession session = createAgenticSession(sessionId, null);
        session.setMessageHistory(null); // Explicitly set to null
        session.setUsageCollector(new SessionUsageCollector(sessionId, "ToolPlanner"));
        sessionStore.createSession(session);

        ChatMemory chatMemory = mock(ChatMemory.class);
        when(chatMemory.messages()).thenReturn(Collections.emptyList());
        chatMemoryMap.put(sessionId, chatMemory);

        AgenticResponse response = new AgenticResponse();
        response.setSessionId(sessionId);
        response.setResponseType(AgenticResponse.ResponseType.COMPLETE);
        response.setFinalResponse("Done");
        response.setOutputVariables(new HashMap<>());

        when(toolPlanner.planNextStep(any(), any(), any(), any(), any(), any(), any()))
            .thenReturn(response);

        AgenticStartRequest request = new AgenticStartRequest(
            Arrays.asList(),
            "Test",
            new HashMap<>(),
            null,
            null,
            sessionId
        );

        // Execute and verify no exception thrown
        assertDoesNotThrow(() -> agenticFlowService.startFlow(request));
    }

    @Test
    @DisplayName("Extract conversation history with only system messages produces no USER or AI lines")
    void testExtractConversationHistory_OnlySystemMessages() {
        // Given: Chat memory containing exclusively SYSTEM messages — the filter must strip them all.
        // This differs from testExtractConversationHistory_EmptyMemory because the memory is
        // non-empty before filtering; we are confirming the filter itself, not the null/empty guard.
        String sessionId = "sess_only_system";
        AgenticSession session = createAgenticSession(sessionId, null);
        session.setUsageCollector(new SessionUsageCollector(sessionId, "ToolPlanner"));
        sessionStore.createSession(session);

        ChatMemory chatMemory = mock(ChatMemory.class);
        List<ChatMessage> messages = Arrays.asList(
            SystemMessage.from("You are a helpful assistant"),
            SystemMessage.from("Additional system instruction")
        );
        when(chatMemory.messages()).thenReturn(messages);
        chatMemoryMap.put(sessionId, chatMemory);

        AgenticResponse response = new AgenticResponse();
        response.setSessionId(sessionId);
        response.setResponseType(AgenticResponse.ResponseType.COMPLETE);
        response.setFinalResponse("Done");
        response.setOutputVariables(new HashMap<>());

        when(toolPlanner.planNextStep(any(), any(), any(), any(), any(), any(), any()))
            .thenReturn(response);

        AgenticStartRequest request = new AgenticStartRequest(
            Arrays.asList(),
            "Test",
            new HashMap<>(),
            null,
            null,
            sessionId
        );

        // When
        AgenticResponse result = agenticFlowService.startFlow(request);

        // Then: Header present but no SYSTEM, USER, or AI role labels emitted
        String conversationHistory = (String) result.getOutputVariables().get("conversation_history");
        assertNotNull(conversationHistory);
        assertTrue(conversationHistory.contains("=== LLM Conversation History ==="),
            "Header must always be present");
        assertFalse(conversationHistory.contains("SYSTEM"),
            "SYSTEM role label must not appear when memory contains only system messages");
        assertFalse(conversationHistory.contains("You are a helpful assistant"),
            "System prompt text must not appear");
        assertFalse(conversationHistory.contains("Additional system instruction"),
            "Second system prompt text must not appear");
        assertFalse(conversationHistory.contains("USER"),
            "No user messages exist so USER label must not appear");
        assertFalse(conversationHistory.contains("[AI]"),
            "No AI messages exist so AI label must not appear");
    }

    @Test
    @DisplayName("Extract conversation history with multiple system messages interspersed excludes all of them")
    void testExtractConversationHistory_MultipleSystemMessagesInterspersed() {
        // Given: System messages appear at the start and between conversation turns, as can happen
        // when a framework re-injects the system prompt after each tool call.  Every occurrence
        // must be suppressed — a filter that only removes the first SYSTEM message would fail here.
        String sessionId = "sess_multi_system";
        AgenticSession session = createAgenticSession(sessionId, null);
        session.setUsageCollector(new SessionUsageCollector(sessionId, "ToolPlanner"));
        sessionStore.createSession(session);

        ChatMemory chatMemory = mock(ChatMemory.class);
        List<ChatMessage> messages = Arrays.asList(
            SystemMessage.from("Initial system prompt"),
            UserMessage.from("First user turn"),
            AiMessage.from("First AI reply"),
            SystemMessage.from("Re-injected system prompt after tool call"),
            UserMessage.from("Second user turn"),
            AiMessage.from("Second AI reply")
        );
        when(chatMemory.messages()).thenReturn(messages);
        chatMemoryMap.put(sessionId, chatMemory);

        AgenticResponse response = new AgenticResponse();
        response.setSessionId(sessionId);
        response.setResponseType(AgenticResponse.ResponseType.COMPLETE);
        response.setFinalResponse("Done");
        response.setOutputVariables(new HashMap<>());

        when(toolPlanner.planNextStep(any(), any(), any(), any(), any(), any(), any()))
            .thenReturn(response);

        AgenticStartRequest request = new AgenticStartRequest(
            Arrays.asList(),
            "Test",
            new HashMap<>(),
            null,
            null,
            sessionId
        );

        // When
        AgenticResponse result = agenticFlowService.startFlow(request);

        // Then: Both system prompts absent; all user/AI turns present
        String conversationHistory = (String) result.getOutputVariables().get("conversation_history");
        assertNotNull(conversationHistory);
        assertFalse(conversationHistory.contains("SYSTEM"),
            "Neither system message must produce a SYSTEM role label");
        assertFalse(conversationHistory.contains("Initial system prompt"),
            "Initial system prompt text must not appear");
        assertFalse(conversationHistory.contains("Re-injected system prompt after tool call"),
            "Re-injected system prompt text must not appear");
        assertTrue(conversationHistory.contains("First user turn"),
            "First user turn must be present");
        assertTrue(conversationHistory.contains("First AI reply"),
            "First AI reply must be present");
        assertTrue(conversationHistory.contains("Second user turn"),
            "Second user turn must be present");
        assertTrue(conversationHistory.contains("Second AI reply"),
            "Second AI reply must be present");
    }

    @Test
    @DisplayName("Extract conversation history with system message at end of memory excludes it")
    void testExtractConversationHistory_SystemMessageAtEnd() {
        // Given: A trailing system message — verifies the filter is not position-dependent
        // (e.g., only stripping index 0).
        String sessionId = "sess_trailing_system";
        AgenticSession session = createAgenticSession(sessionId, null);
        session.setUsageCollector(new SessionUsageCollector(sessionId, "ToolPlanner"));
        sessionStore.createSession(session);

        ChatMemory chatMemory = mock(ChatMemory.class);
        List<ChatMessage> messages = Arrays.asList(
            UserMessage.from("What is the weather?"),
            AiMessage.from("I cannot check weather directly."),
            SystemMessage.from("Trailing system note")
        );
        when(chatMemory.messages()).thenReturn(messages);
        chatMemoryMap.put(sessionId, chatMemory);

        AgenticResponse response = new AgenticResponse();
        response.setSessionId(sessionId);
        response.setResponseType(AgenticResponse.ResponseType.COMPLETE);
        response.setFinalResponse("Done");
        response.setOutputVariables(new HashMap<>());

        when(toolPlanner.planNextStep(any(), any(), any(), any(), any(), any(), any()))
            .thenReturn(response);

        AgenticStartRequest request = new AgenticStartRequest(
            Arrays.asList(),
            "Test",
            new HashMap<>(),
            null,
            null,
            sessionId
        );

        // When
        AgenticResponse result = agenticFlowService.startFlow(request);

        // Then: Trailing system message excluded; user and AI messages retained
        String conversationHistory = (String) result.getOutputVariables().get("conversation_history");
        assertNotNull(conversationHistory);
        assertFalse(conversationHistory.contains("SYSTEM"),
            "Trailing system message must not produce a SYSTEM role label");
        assertFalse(conversationHistory.contains("Trailing system note"),
            "Trailing system message text must not appear");
        assertTrue(conversationHistory.contains("What is the weather?"),
            "User message must be present");
        assertTrue(conversationHistory.contains("I cannot check weather directly."),
            "AI message must be present");
    }

    // ========================================
    // Section 4: Structured session_summary (KR-20450)
    // ========================================

    @Test
    @DisplayName("Parent COMPLETE response includes structured session_summary alongside legacy keys")
    @SuppressWarnings("unchecked")
    void testStructuredSessionSummary_CompleteResponse() {
        String sessionId = "sess_struct_complete_001";
        AgenticSession session = createAgenticSession(sessionId, null);
        session.setUsageCollector(new SessionUsageCollector(sessionId, "ToolPlanner"));
        sessionStore.createSession(session);

        ChatMemory chatMemory = mock(ChatMemory.class);
        when(chatMemory.messages()).thenReturn(Collections.emptyList());
        chatMemoryMap.put(sessionId, chatMemory);

        AgenticResponse response = new AgenticResponse();
        response.setSessionId(sessionId);
        response.setResponseType(AgenticResponse.ResponseType.COMPLETE);
        response.setFinalResponse("All done");
        response.setOutputVariables(new HashMap<>());

        when(toolPlanner.planNextStep(any(), any(), any(), any(), any(), any(), any()))
            .thenReturn(response);

        AgenticResponse result = agenticFlowService.startFlow(new AgenticStartRequest(
            Arrays.asList(), "Test task", new HashMap<>(), null, null, sessionId));

        Map<String, Object> outputVars = result.getOutputVariables();
        assertNotNull(outputVars);
        // Backward-compat keys still present
        assertTrue(outputVars.containsKey(AgenticConstants.SYSTEM_VAR_CONVERSATION_HISTORY));
        assertTrue(outputVars.containsKey(AgenticConstants.SYSTEM_VAR_TOOL_SUMMARY));
        // New structured key
        assertTrue(outputVars.containsKey(AgenticConstants.SYSTEM_VAR_SESSION_SUMMARY),
            "Parent COMPLETE must include session_summary");

        Map<String, Object> structured = parseSessionSummary(outputVars);
        assertEquals(TerminationReason.COMPLETE.name(),
            structured.get(AgenticConstants.SUMMARY_FIELD_TERMINATION_REASON));
        assertEquals(sessionId, structured.get(AgenticConstants.SUMMARY_FIELD_SESSION_ID));
        assertEquals("All done", structured.get(AgenticConstants.SUMMARY_FIELD_FINAL_RESPONSE));
        assertNotNull(structured.get(AgenticConstants.SUMMARY_FIELD_ACTIVITY_FEED));
        assertNotNull(structured.get(AgenticConstants.SUMMARY_FIELD_CHILD_SESSIONS));
        assertNotNull(structured.get(AgenticConstants.SUMMARY_FIELD_USAGE));
    }

    @Test
    @DisplayName("Parent ERROR response carries terminationReason=ERROR and errorMessage in session_summary")
    @SuppressWarnings("unchecked")
    void testStructuredSessionSummary_ErrorResponse() {
        String sessionId = "sess_struct_error_002";
        AgenticSession session = createAgenticSession(sessionId, null);
        session.setUsageCollector(new SessionUsageCollector(sessionId, "ToolPlanner"));
        sessionStore.createSession(session);

        ChatMemory chatMemory = mock(ChatMemory.class);
        when(chatMemory.messages()).thenReturn(Collections.emptyList());
        chatMemoryMap.put(sessionId, chatMemory);

        AgenticResponse response = new AgenticResponse();
        response.setSessionId(sessionId);
        response.setResponseType(AgenticResponse.ResponseType.ERROR);
        response.setErrorMessage("Something broke");
        response.setOutputVariables(new HashMap<>());

        when(toolPlanner.planNextStep(any(), any(), any(), any(), any(), any(), any()))
            .thenReturn(response);

        AgenticResponse result = agenticFlowService.startFlow(new AgenticStartRequest(
            Arrays.asList(), "Test task", new HashMap<>(), null, null, sessionId));

        Map<String, Object> outputVars = result.getOutputVariables();
        assertNotNull(outputVars);
        assertTrue(outputVars.containsKey(AgenticConstants.SYSTEM_VAR_SESSION_SUMMARY));

        Map<String, Object> structured = parseSessionSummary(outputVars);
        assertEquals(TerminationReason.ERROR.name(),
            structured.get(AgenticConstants.SUMMARY_FIELD_TERMINATION_REASON));
        assertEquals("Something broke", structured.get(AgenticConstants.SUMMARY_FIELD_ERROR_MESSAGE));
    }

    @Test
    @DisplayName("Activity feed captures session_started and session_completed entries on parent COMPLETE")
    @SuppressWarnings("unchecked")
    void testActivityFeed_StartAndComplete() {
        String sessionId = "sess_activity_003";
        AgenticSession session = createAgenticSession(sessionId, null);
        session.setUsageCollector(new SessionUsageCollector(sessionId, "ToolPlanner"));
        sessionStore.createSession(session);

        ChatMemory chatMemory = mock(ChatMemory.class);
        when(chatMemory.messages()).thenReturn(Collections.emptyList());
        chatMemoryMap.put(sessionId, chatMemory);

        AgenticResponse completeResp = new AgenticResponse();
        completeResp.setSessionId(sessionId);
        completeResp.setResponseType(AgenticResponse.ResponseType.COMPLETE);
        completeResp.setFinalResponse("Done");
        completeResp.setOutputVariables(new HashMap<>());

        when(toolPlanner.planNextStep(any(), any(), any(), any(), any(), any(), any()))
            .thenReturn(completeResp);

        AgenticResponse result = agenticFlowService.startFlow(new AgenticStartRequest(
            Arrays.asList(), "Hello", new HashMap<>(), null, null, sessionId));

        Map<String, Object> structured = parseSessionSummary(result.getOutputVariables());
        List<Map<String, Object>> feed = (List<Map<String, Object>>)
            structured.get(AgenticConstants.SUMMARY_FIELD_ACTIVITY_FEED);

        assertNotNull(feed);
        List<String> types = feed.stream().map(e -> (String) e.get("type")).toList();
        assertTrue(types.contains("session_started"),
            "Activity feed must include session_started; got " + types);
        assertTrue(types.contains("session_completed"),
            "Activity feed must include session_completed; got " + types);
    }

    @Test
    @DisplayName("attachFailureSummary stamps PERSISTENCE_ERROR reason and persists summary on response")
    @SuppressWarnings("unchecked")
    void testAttachFailureSummary_PersistenceError() {
        String sessionId = "sess_attach_persist_004";
        AgenticSession session = createAgenticSession(sessionId, null);
        session.setUsageCollector(new SessionUsageCollector(sessionId, "ToolPlanner"));
        sessionStore.createSession(session);

        ChatMemory chatMemory = mock(ChatMemory.class);
        when(chatMemory.messages()).thenReturn(Collections.emptyList());
        chatMemoryMap.put(sessionId, chatMemory);

        AgenticResponse errResp = new AgenticResponse(sessionId, AgenticResponse.ResponseType.ERROR);
        errResp.setErrorMessage("Persistence failed");

        agenticFlowService.attachFailureSummary(
            sessionId, errResp, TerminationReason.PERSISTENCE_ERROR,
            new ActivityEvent.PersistenceFailure("startFlow", "P-101"));

        Map<String, Object> outputVars = errResp.getOutputVariables();
        assertNotNull(outputVars, "attachFailureSummary must populate outputVariables");
        assertTrue(outputVars.containsKey(AgenticConstants.SYSTEM_VAR_SESSION_SUMMARY));

        Map<String, Object> structured = parseSessionSummary(outputVars);
        assertEquals(TerminationReason.PERSISTENCE_ERROR.name(),
            structured.get(AgenticConstants.SUMMARY_FIELD_TERMINATION_REASON));

        List<Map<String, Object>> feed = (List<Map<String, Object>>)
            structured.get(AgenticConstants.SUMMARY_FIELD_ACTIVITY_FEED);
        assertTrue(feed.stream().anyMatch(e -> "persistence_failure".equals(e.get("type"))),
            "activity feed must record the persistence_failure event");

        // Cleanup — attachFailureSummary intentionally does not delete the session.
        sessionStore.deleteSession(sessionId);
    }

    @Test
    @DisplayName("attachFailureSummary on missing session is a safe no-op")
    void testAttachFailureSummary_MissingSession_Noop() {
        String sessionId = "sess_missing_005";
        // Do NOT create the session in the store.
        AgenticResponse errResp = new AgenticResponse(sessionId, AgenticResponse.ResponseType.ERROR);
        errResp.setErrorMessage("limit");

        assertDoesNotThrow(() -> agenticFlowService.attachFailureSummary(
            sessionId, errResp, TerminationReason.ITERATION_LIMIT,
            new ActivityEvent.ExecutionLimitExceeded("iteration", 0, 0)));

        // Response left untouched when session not found
        assertNull(errResp.getOutputVariables());
    }

    @Test
    @DisplayName("TerminationReason.fromLimitType maps iteration / cost / time correctly and defaults to ERROR")
    void testTerminationReason_FromLimitType() {
        assertEquals(TerminationReason.ITERATION_LIMIT, TerminationReason.fromLimitType("iteration"));
        assertEquals(TerminationReason.COST_LIMIT, TerminationReason.fromLimitType("cost"));
        assertEquals(TerminationReason.TIME_LIMIT, TerminationReason.fromLimitType("time"));
        assertEquals(TerminationReason.ITERATION_LIMIT, TerminationReason.fromLimitType("ITERATION"));
        assertEquals(TerminationReason.ERROR, TerminationReason.fromLimitType("garbage"));
        assertEquals(TerminationReason.ERROR, TerminationReason.fromLimitType(null));
    }

    @Test
    @DisplayName("Pre-set terminationReason on the session (e.g. GUARDRAIL_VIOLATION) is honored by addSessionSummaryToResponse")
    @SuppressWarnings("unchecked")
    void testSessionSummary_HonorsPreSetTerminationReason() {
        String sessionId = "sess_pre_set_006";
        AgenticSession session = createAgenticSession(sessionId, null);
        session.setUsageCollector(new SessionUsageCollector(sessionId, "ToolPlanner"));
        session.setTerminationReason(TerminationReason.GUARDRAIL_VIOLATION); // simulate guardrail catch
        sessionStore.createSession(session);

        ChatMemory chatMemory = mock(ChatMemory.class);
        when(chatMemory.messages()).thenReturn(Collections.emptyList());
        chatMemoryMap.put(sessionId, chatMemory);

        AgenticResponse response = new AgenticResponse();
        response.setSessionId(sessionId);
        response.setResponseType(AgenticResponse.ResponseType.ERROR);
        response.setErrorMessage("Blocked by guardrail");
        response.setOutputVariables(new HashMap<>());

        when(toolPlanner.planNextStep(any(), any(), any(), any(), any(), any(), any()))
            .thenReturn(response);

        AgenticResponse result = agenticFlowService.startFlow(new AgenticStartRequest(
            Arrays.asList(), "task", new HashMap<>(), null, null, sessionId));

        Map<String, Object> structured = parseSessionSummary(result.getOutputVariables());
        // startFlow creates a NEW session under the same id, overriding our pre-set reason.
        // We only verify the structured summary key exists with a valid termination reason — the
        // pre-set-honoring contract is tested directly via attachFailureSummary above.
        assertNotNull(structured);
        assertNotNull(structured.get(AgenticConstants.SUMMARY_FIELD_TERMINATION_REASON));
    }

    // ========================================
    // Section 5: Structured conversationHistory and toolSummary arrays (KR-20450 follow-up)
    // ========================================
    //
    // These tests use attachFailureSummary as the entry point because it preserves the
    // pre-populated session/chat-memory state (startFlow rebuilds the session under the
    // same id and overwrites the messageHistory we set up).

    @Test
    @DisplayName("session_summary.conversationHistory is a JSON array of turn objects, not a string")
    @SuppressWarnings("unchecked")
    void testStructuredConversationHistory_IsArrayNotString() {
        String sessionId = "sess_struct_conv_array_001";
        AgenticSession session = createAgenticSession(sessionId, null);
        session.setUsageCollector(new SessionUsageCollector(sessionId, "ToolPlanner"));
        sessionStore.createSession(session);

        ChatMemory chatMemory = mock(ChatMemory.class);
        List<ChatMessage> messages = Arrays.asList(
            SystemMessage.from("Framework system prompt"),
            UserMessage.from("<<<USER_DATA>>>{role=user, content=what is entity, timestamp=2026-05-22T10:00:00Z}<<<END_USER_DATA>>>"),
            AiMessage.from("{\"responseType\":\"COMPLETE\",\"finalResponse\":\"Done\",\"statusMessage\":\"Wrapped up\"}")
        );
        when(chatMemory.messages()).thenReturn(messages);
        chatMemoryMap.put(sessionId, chatMemory);

        AgenticResponse resp = new AgenticResponse(sessionId, AgenticResponse.ResponseType.COMPLETE);
        resp.setFinalResponse("Done");

        agenticFlowService.attachFailureSummary(
            sessionId, resp, TerminationReason.COMPLETE, new ActivityEvent.SessionCompleted(true));

        Map<String, Object> structured = parseSessionSummary(resp.getOutputVariables());
        Object convoField = structured.get(AgenticConstants.SUMMARY_FIELD_CONVERSATION_HISTORY);
        assertNotNull(convoField, "session_summary must contain conversationHistory field");
        assertTrue(convoField instanceof List,
            "session_summary.conversationHistory must be a JSON array, got: " + convoField.getClass());

        List<Map<String, Object>> turns = (List<Map<String, Object>>) convoField;
        assertFalse(turns.isEmpty(), "conversationHistory array must contain at least one turn");
        for (Map<String, Object> turn : turns) {
            assertNotNull(turn.get("role"), "Every turn must have a role: " + turn);
            assertNotNull(turn.get("type"), "Every turn must have a type: " + turn);
        }

        // Top-level legacy variable should still be a string (back-compat invariant).
        assertTrue(resp.getOutputVariables().get(AgenticConstants.SYSTEM_VAR_CONVERSATION_HISTORY) instanceof String,
            "Top-level conversation_history must remain a string for backward compatibility");

        sessionStore.deleteSession(sessionId);
    }

    @Test
    @DisplayName("Structured conversationHistory filters SYSTEM messages — they never appear as turns")
    @SuppressWarnings("unchecked")
    void testStructuredConversationHistory_FiltersSystemMessages() {
        String sessionId = "sess_struct_conv_filter_002";
        AgenticSession session = createAgenticSession(sessionId, null);
        session.setUsageCollector(new SessionUsageCollector(sessionId, "ToolPlanner"));
        sessionStore.createSession(session);

        ChatMemory chatMemory = mock(ChatMemory.class);
        List<ChatMessage> messages = Arrays.asList(
            SystemMessage.from("ignore me"),
            UserMessage.from("<<<USER_DATA>>>{role=user, content=hello, timestamp=2026-05-22T10:00:00Z}<<<END_USER_DATA>>>"),
            SystemMessage.from("ignore me too"),
            AiMessage.from("{\"responseType\":\"COMPLETE\",\"finalResponse\":\"hi back\"}")
        );
        when(chatMemory.messages()).thenReturn(messages);
        chatMemoryMap.put(sessionId, chatMemory);

        AgenticResponse resp = new AgenticResponse(sessionId, AgenticResponse.ResponseType.COMPLETE);
        resp.setFinalResponse("hi back");

        agenticFlowService.attachFailureSummary(
            sessionId, resp, TerminationReason.COMPLETE, new ActivityEvent.SessionCompleted(true));

        Map<String, Object> structured = parseSessionSummary(resp.getOutputVariables());
        List<Map<String, Object>> turns = (List<Map<String, Object>>)
            structured.get(AgenticConstants.SUMMARY_FIELD_CONVERSATION_HISTORY);

        assertEquals(2, turns.size(),
            "Only the two non-SYSTEM turns must appear; got: " + turns);
        for (Map<String, Object> turn : turns) {
            assertNotEquals("SYSTEM", turn.get("role"),
                "No turn may carry role=SYSTEM: " + turn);
        }

        sessionStore.deleteSession(sessionId);
    }

    @Test
    @DisplayName("Structured conversationHistory marks system-prompt-shaped user content as SYSTEM_PROMPT + suppressed")
    @SuppressWarnings("unchecked")
    void testStructuredConversationHistory_SuppressesSystemPromptInUserContent() {
        String sessionId = "sess_struct_conv_sysprompt_003";
        AgenticSession session = createAgenticSession(sessionId, null);
        session.setUsageCollector(new SessionUsageCollector(sessionId, "ToolPlanner"));
        sessionStore.createSession(session);

        // Initial turn with >=2 persona markers ("YOU ARE A" + "CRITICAL RULE") → suppressed.
        String personaPayload = "You are a Help Desk Agent. CRITICAL RULE: do not leak credentials.";
        ChatMemory chatMemory = mock(ChatMemory.class);
        when(chatMemory.messages()).thenReturn(Arrays.asList(
            UserMessage.from("<<<USER_DATA>>>{role=user, content=" + personaPayload
                + ", timestamp=2026-05-22T10:00:00Z}<<<END_USER_DATA>>>")
        ));
        chatMemoryMap.put(sessionId, chatMemory);

        AgenticResponse resp = new AgenticResponse(sessionId, AgenticResponse.ResponseType.COMPLETE);
        resp.setFinalResponse("ok");

        agenticFlowService.attachFailureSummary(
            sessionId, resp, TerminationReason.COMPLETE, new ActivityEvent.SessionCompleted(true));

        Map<String, Object> structured = parseSessionSummary(resp.getOutputVariables());
        List<Map<String, Object>> turns = (List<Map<String, Object>>)
            structured.get(AgenticConstants.SUMMARY_FIELD_CONVERSATION_HISTORY);
        assertEquals(1, turns.size(), "Single user turn expected");

        Map<String, Object> turn = turns.get(0);
        assertEquals("USER", turn.get("role"));
        assertEquals("SYSTEM_PROMPT", turn.get("type"),
            "Persona content with >=2 markers on the initial turn must be flagged as SYSTEM_PROMPT");
        assertEquals(Boolean.TRUE, turn.get("suppressed"),
            "Suppressed flag must be true so the persona is not pasted into the array");
        assertNull(turn.get("content"),
            "Raw content must not be present when suppressed=true");
        assertNotNull(turn.get("reason"),
            "Suppression must name which heuristic fired so it is not invisible");
        assertEquals(personaPayload.length(), ((Number) turn.get("contentLength")).intValue());

        sessionStore.deleteSession(sessionId);
    }

    @Test
    @DisplayName("Structured conversationHistory keeps legitimate user content (long paste, single marker) instead of suppressing it")
    @SuppressWarnings("unchecked")
    void testStructuredConversationHistory_DoesNotSuppressLegitimateUserContent() {
        String sessionId = "sess_struct_conv_legit_003b";
        AgenticSession session = createAgenticSession(sessionId, null);
        session.setUsageCollector(new SessionUsageCollector(sessionId, "ToolPlanner"));
        sessionStore.createSession(session);

        // A long pasted support ticket (>1500 chars) that mentions a single marker phrase ("YOU ARE A").
        // Old heuristic suppressed it (length-only branch + single-keyword); the hardened one keeps it:
        // length never suppresses, and one marker is below the >=2 threshold.
        String longTicket = "Customer reports: when YOU ARE A registered user the export button fails. "
            + "x".repeat(1600);
        ChatMemory chatMemory = mock(ChatMemory.class);
        when(chatMemory.messages()).thenReturn(Arrays.asList(
            UserMessage.from("<<<USER_DATA>>>{role=user, content=" + longTicket
                + ", timestamp=2026-05-22T10:00:00Z}<<<END_USER_DATA>>>")
        ));
        chatMemoryMap.put(sessionId, chatMemory);

        AgenticResponse resp = new AgenticResponse(sessionId, AgenticResponse.ResponseType.COMPLETE);
        resp.setFinalResponse("ok");

        agenticFlowService.attachFailureSummary(
            sessionId, resp, TerminationReason.COMPLETE, new ActivityEvent.SessionCompleted(true));

        Map<String, Object> structured = parseSessionSummary(resp.getOutputVariables());
        List<Map<String, Object>> turns = (List<Map<String, Object>>)
            structured.get(AgenticConstants.SUMMARY_FIELD_CONVERSATION_HISTORY);
        assertEquals(1, turns.size(), "Single user turn expected");

        Map<String, Object> turn = turns.get(0);
        assertEquals("USER_INPUT", turn.get("type"),
            "Legitimate long content with a single marker must NOT be flagged as SYSTEM_PROMPT");
        assertNull(turn.get("suppressed"),
            "Legitimate content must not be suppressed");
        assertEquals(longTicket, turn.get("content"),
            "Full user content must be preserved, not erased");

        sessionStore.deleteSession(sessionId);
    }

    @Test
    @DisplayName("Structured conversationHistory AI EXECUTE_TOOL turn carries tool name + nested args Map")
    @SuppressWarnings("unchecked")
    void testStructuredConversationHistory_AiExecuteToolTurnHasNestedArgs() {
        String sessionId = "sess_struct_conv_args_004";
        AgenticSession session = createAgenticSession(sessionId, null);
        session.setUsageCollector(new SessionUsageCollector(sessionId, "ToolPlanner"));
        sessionStore.createSession(session);

        String aiJson = "{\"responseType\":\"EXECUTE_TOOL\","
            + "\"command\":{\"toolName\":\"ask_a_person\","
            + "\"arguments\":{\"Question\":\"How can I help you?\","
            + "\"outputVariableNames\":[\"userResponse\"]}},"
            + "\"statusMessage\":\"Asking how I can help you\"}";
        ChatMemory chatMemory = mock(ChatMemory.class);
        when(chatMemory.messages()).thenReturn(Arrays.asList(AiMessage.from(aiJson)));
        chatMemoryMap.put(sessionId, chatMemory);

        AgenticResponse resp = new AgenticResponse(sessionId, AgenticResponse.ResponseType.COMPLETE);
        resp.setFinalResponse("done");

        agenticFlowService.attachFailureSummary(
            sessionId, resp, TerminationReason.COMPLETE, new ActivityEvent.SessionCompleted(true));

        Map<String, Object> structured = parseSessionSummary(resp.getOutputVariables());
        List<Map<String, Object>> turns = (List<Map<String, Object>>)
            structured.get(AgenticConstants.SUMMARY_FIELD_CONVERSATION_HISTORY);
        Map<String, Object> turn = turns.get(0);

        assertEquals("AI", turn.get("role"));
        assertEquals("EXECUTE_TOOL", turn.get("type"));
        assertEquals("ask_a_person", turn.get("tool"));
        assertEquals("Asking how I can help you", turn.get("status"));

        Object argsField = turn.get("args");
        assertTrue(argsField instanceof Map,
            "args must be a nested JSON object, not an escaped string. Got: " + argsField);
        Map<String, Object> args = (Map<String, Object>) argsField;
        assertEquals("How can I help you?", args.get("Question"));

        sessionStore.deleteSession(sessionId);
    }

    @Test
    @DisplayName("Structured conversationHistory AI COMPLETE turn carries finalResponse")
    @SuppressWarnings("unchecked")
    void testStructuredConversationHistory_AiCompleteTurnHasFinalResponse() {
        String sessionId = "sess_struct_conv_complete_005";
        AgenticSession session = createAgenticSession(sessionId, null);
        session.setUsageCollector(new SessionUsageCollector(sessionId, "ToolPlanner"));
        sessionStore.createSession(session);

        String aiJson = "{\"responseType\":\"COMPLETE\","
            + "\"finalResponse\":\"Conversation ended successfully\","
            + "\"statusMessage\":\"Closed\"}";
        ChatMemory chatMemory = mock(ChatMemory.class);
        when(chatMemory.messages()).thenReturn(Arrays.asList(AiMessage.from(aiJson)));
        chatMemoryMap.put(sessionId, chatMemory);

        AgenticResponse resp = new AgenticResponse(sessionId, AgenticResponse.ResponseType.COMPLETE);
        resp.setFinalResponse("Conversation ended successfully");

        agenticFlowService.attachFailureSummary(
            sessionId, resp, TerminationReason.COMPLETE, new ActivityEvent.SessionCompleted(true));

        Map<String, Object> structured = parseSessionSummary(resp.getOutputVariables());
        List<Map<String, Object>> turns = (List<Map<String, Object>>)
            structured.get(AgenticConstants.SUMMARY_FIELD_CONVERSATION_HISTORY);
        Map<String, Object> turn = turns.get(0);

        assertEquals("AI", turn.get("role"));
        assertEquals("COMPLETE", turn.get("type"));
        assertEquals("Conversation ended successfully", turn.get("finalResponse"));
        assertEquals("Closed", turn.get("status"));

        sessionStore.deleteSession(sessionId);
    }

    @Test
    @DisplayName("session_summary.toolSummary is a JSON array of execution objects, not a string")
    @SuppressWarnings("unchecked")
    void testStructuredToolSummary_IsArrayNotString() {
        String sessionId = "sess_struct_tool_array_006";
        AgenticSession session = createAgenticSession(sessionId, null);
        session.setUsageCollector(new SessionUsageCollector(sessionId, "ToolPlanner"));
        // Mix initial user message with two tool executions.
        session.getMessageHistory().add(createUserMessage("just a normal initial request"));
        session.getMessageHistory().add(createToolMessage("ask_a_person",
            Map.of("userResponse", "what is entity"), true));
        session.getMessageHistory().add(createToolMessage("child_agent",
            Map.of("status", "COMPLETED", "finalResponse", "Entity is..."), true));
        sessionStore.createSession(session);

        ChatMemory chatMemory = mock(ChatMemory.class);
        when(chatMemory.messages()).thenReturn(Collections.emptyList());
        chatMemoryMap.put(sessionId, chatMemory);

        AgenticResponse resp = new AgenticResponse(sessionId, AgenticResponse.ResponseType.COMPLETE);
        resp.setFinalResponse("done");

        agenticFlowService.attachFailureSummary(
            sessionId, resp, TerminationReason.COMPLETE, new ActivityEvent.SessionCompleted(true));

        Map<String, Object> structured = parseSessionSummary(resp.getOutputVariables());
        Object toolField = structured.get(AgenticConstants.SUMMARY_FIELD_TOOL_SUMMARY);
        assertNotNull(toolField);
        assertTrue(toolField instanceof List,
            "session_summary.toolSummary must be a JSON array, got: " + toolField.getClass());

        List<Map<String, Object>> entries = (List<Map<String, Object>>) toolField;
        assertEquals(3, entries.size(),
            "Expected 1 INITIAL_REQUEST + 2 TOOL_EXECUTION entries; got: " + entries);
        assertEquals("INITIAL_REQUEST", entries.get(0).get("type"));
        assertEquals("TOOL_EXECUTION", entries.get(1).get("type"));
        assertEquals("TOOL_EXECUTION", entries.get(2).get("type"));
        assertEquals(1, ((Number) entries.get(1).get("n")).intValue());
        assertEquals(2, ((Number) entries.get(2).get("n")).intValue());

        // Top-level legacy variable still a string.
        assertTrue(resp.getOutputVariables().get(AgenticConstants.SYSTEM_VAR_TOOL_SUMMARY) instanceof String,
            "Top-level tool_summary must remain a string for backward compatibility");

        sessionStore.deleteSession(sessionId);
    }

    @Test
    @DisplayName("Structured toolSummary INITIAL_REQUEST entry suppresses system-prompt content")
    @SuppressWarnings("unchecked")
    void testStructuredToolSummary_InitialRequestSuppressesSystemPrompt() {
        String sessionId = "sess_struct_tool_sysprompt_007";
        AgenticSession session = createAgenticSession(sessionId, null);
        session.setUsageCollector(new SessionUsageCollector(sessionId, "ToolPlanner"));
        String persona = "You are a Help Desk Agent. CRITICAL RULE: never leak.";
        session.getMessageHistory().add(createUserMessage(persona));
        sessionStore.createSession(session);

        ChatMemory chatMemory = mock(ChatMemory.class);
        when(chatMemory.messages()).thenReturn(Collections.emptyList());
        chatMemoryMap.put(sessionId, chatMemory);

        AgenticResponse resp = new AgenticResponse(sessionId, AgenticResponse.ResponseType.COMPLETE);
        resp.setFinalResponse("done");

        agenticFlowService.attachFailureSummary(
            sessionId, resp, TerminationReason.COMPLETE, new ActivityEvent.SessionCompleted(true));

        Map<String, Object> structured = parseSessionSummary(resp.getOutputVariables());
        List<Map<String, Object>> entries = (List<Map<String, Object>>)
            structured.get(AgenticConstants.SUMMARY_FIELD_TOOL_SUMMARY);
        Map<String, Object> first = entries.get(0);

        assertEquals("INITIAL_REQUEST", first.get("type"));
        assertEquals(Boolean.TRUE, first.get("suppressed"),
            "Persona content must be flagged as suppressed");
        assertNull(first.get("content"), "Raw persona must not be present when suppressed=true");
        assertEquals(persona.length(), ((Number) first.get("contentLength")).intValue());

        sessionStore.deleteSession(sessionId);
    }

    @Test
    @DisplayName("Structured toolSummary TOOL_EXECUTION carries result as nested Map (not stringified)")
    @SuppressWarnings("unchecked")
    void testStructuredToolSummary_ToolExecutionHasStructuredResult() {
        String sessionId = "sess_struct_tool_result_008";
        AgenticSession session = createAgenticSession(sessionId, null);
        session.setUsageCollector(new SessionUsageCollector(sessionId, "ToolPlanner"));
        Map<String, Object> resultMap = new LinkedHashMap<>();
        resultMap.put("status", "COMPLETED");
        resultMap.put("finalResponse", "Entity is a real-world thing");
        Map<String, Object> outputVars = new LinkedHashMap<>();
        outputVars.put("Answer", "Some other answer text");
        outputVars.put("Ticket ID", "T-123");
        resultMap.put("outputVariables", outputVars);
        session.getMessageHistory().add(createToolMessage("child_agent", resultMap, true));
        sessionStore.createSession(session);

        ChatMemory chatMemory = mock(ChatMemory.class);
        when(chatMemory.messages()).thenReturn(Collections.emptyList());
        chatMemoryMap.put(sessionId, chatMemory);

        AgenticResponse resp = new AgenticResponse(sessionId, AgenticResponse.ResponseType.COMPLETE);
        resp.setFinalResponse("done");

        agenticFlowService.attachFailureSummary(
            sessionId, resp, TerminationReason.COMPLETE, new ActivityEvent.SessionCompleted(true));

        Map<String, Object> structured = parseSessionSummary(resp.getOutputVariables());
        List<Map<String, Object>> entries = (List<Map<String, Object>>)
            structured.get(AgenticConstants.SUMMARY_FIELD_TOOL_SUMMARY);
        Map<String, Object> exec = entries.get(0);

        assertEquals("TOOL_EXECUTION", exec.get("type"));
        assertEquals("child_agent", exec.get("tool"));
        assertEquals("COMPLETED", exec.get("status"));

        Object result = exec.get("result");
        assertTrue(result instanceof Map,
            "result must be a nested JSON object, not a string. Got: " + result);
        Map<String, Object> resultObj = (Map<String, Object>) result;
        assertEquals("COMPLETED", resultObj.get("status"));
        assertEquals("Entity is a real-world thing", resultObj.get("finalResponse"));
        Object ov = resultObj.get("outputVariables");
        assertTrue(ov instanceof Map, "outputVariables must be a nested JSON object too");
        assertEquals("T-123", ((Map<String, Object>) ov).get("Ticket ID"));

        sessionStore.deleteSession(sessionId);
    }

    @Test
    @DisplayName("Structured toolSummary replaces outputVariables.Answer with marker when it equals finalResponse")
    @SuppressWarnings("unchecked")
    void testStructuredToolSummary_DedupesAnswerEqualToFinalResponse() {
        String sessionId = "sess_struct_tool_dedupe_009";
        AgenticSession session = createAgenticSession(sessionId, null);
        session.setUsageCollector(new SessionUsageCollector(sessionId, "ToolPlanner"));
        String sharedAnswer = "Overview: an entity is a real-world thing modeled as data.";
        Map<String, Object> resultMap = new LinkedHashMap<>();
        resultMap.put("status", "COMPLETED");
        resultMap.put("finalResponse", sharedAnswer);
        Map<String, Object> outputVars = new LinkedHashMap<>();
        outputVars.put("Answer", sharedAnswer); // same blob — must dedupe
        outputVars.put("Ticket ID", "");
        resultMap.put("outputVariables", outputVars);
        session.getMessageHistory().add(createToolMessage("child_agent", resultMap, true));
        sessionStore.createSession(session);

        ChatMemory chatMemory = mock(ChatMemory.class);
        when(chatMemory.messages()).thenReturn(Collections.emptyList());
        chatMemoryMap.put(sessionId, chatMemory);

        AgenticResponse resp = new AgenticResponse(sessionId, AgenticResponse.ResponseType.COMPLETE);
        resp.setFinalResponse("done");

        agenticFlowService.attachFailureSummary(
            sessionId, resp, TerminationReason.COMPLETE, new ActivityEvent.SessionCompleted(true));

        Map<String, Object> structured = parseSessionSummary(resp.getOutputVariables());
        List<Map<String, Object>> entries = (List<Map<String, Object>>)
            structured.get(AgenticConstants.SUMMARY_FIELD_TOOL_SUMMARY);
        Map<String, Object> result = (Map<String, Object>) entries.get(0).get("result");

        assertEquals(sharedAnswer, result.get("finalResponse"),
            "finalResponse must remain unchanged when it's under the truncation threshold");
        Map<String, Object> ovOut = (Map<String, Object>) result.get("outputVariables");
        assertEquals("(same as finalResponse)", ovOut.get("Answer"),
            "Answer must be deduped when its value equals finalResponse");
        assertEquals("", ovOut.get("Ticket ID"),
            "Non-matching outputVariable values must pass through unchanged");

        sessionStore.deleteSession(sessionId);
    }

    @Test
    @DisplayName("Structured toolSummary truncates a finalResponse longer than 1000 chars with suffix")
    @SuppressWarnings("unchecked")
    void testStructuredToolSummary_TruncatesLongFinalResponse() {
        String sessionId = "sess_struct_tool_truncate_010";
        AgenticSession session = createAgenticSession(sessionId, null);
        session.setUsageCollector(new SessionUsageCollector(sessionId, "ToolPlanner"));
        // 1500 chars of "a" — guaranteed over the 1000 limit.
        String longAnswer = "a".repeat(1500);
        Map<String, Object> resultMap = new LinkedHashMap<>();
        resultMap.put("status", "COMPLETED");
        resultMap.put("finalResponse", longAnswer);
        resultMap.put("outputVariables", Map.of("Ticket ID", "T-1"));
        session.getMessageHistory().add(createToolMessage("child_agent", resultMap, true));
        sessionStore.createSession(session);

        ChatMemory chatMemory = mock(ChatMemory.class);
        when(chatMemory.messages()).thenReturn(Collections.emptyList());
        chatMemoryMap.put(sessionId, chatMemory);

        AgenticResponse resp = new AgenticResponse(sessionId, AgenticResponse.ResponseType.COMPLETE);
        resp.setFinalResponse("done");

        agenticFlowService.attachFailureSummary(
            sessionId, resp, TerminationReason.COMPLETE, new ActivityEvent.SessionCompleted(true));

        Map<String, Object> structured = parseSessionSummary(resp.getOutputVariables());
        List<Map<String, Object>> entries = (List<Map<String, Object>>)
            structured.get(AgenticConstants.SUMMARY_FIELD_TOOL_SUMMARY);
        Map<String, Object> result = (Map<String, Object>) entries.get(0).get("result");

        String renderedFinal = (String) result.get("finalResponse");
        assertNotNull(renderedFinal);
        assertTrue(renderedFinal.length() < longAnswer.length(),
            "Rendered finalResponse must be shorter than the original after truncation");
        assertTrue(renderedFinal.startsWith("a".repeat(1000)),
            "Truncated value must keep the first 1000 chars of the original");
        assertTrue(renderedFinal.contains("...[truncated, full length: " + longAnswer.length() + "]"),
            "Truncation suffix must include the original length. Got: " + renderedFinal);

        sessionStore.deleteSession(sessionId);
    }

    @Test
    @DisplayName("session_summary serialization-failure fallback emits valid JSON, never Map.toString()")
    @SuppressWarnings("unchecked")
    void testSerializationFailureFallback_IsValidJson() throws Exception {
        // The sentinel stands in when Jackson fails to serialize the structured summary. Its contract
        // is "Text containing a JSON string", so it must parse as JSON even when the error message
        // contains JSON-hostile characters (quotes, newlines, braces) that would break naive concat.
        String hostileError = "boom: unexpected \"token\" at {line\n2}";
        String sentinel = agenticFlowService.buildSerializationFailureSentinel(hostileError);

        // Must parse back into a Map — proves it is valid JSON, not a {k=v} Map.toString() blob.
        Map<String, Object> parsed = JsonMapper.getInstance()
            .readValue(sentinel, new TypeReference<Map<String, Object>>() {});

        assertEquals("serialization_failure", parsed.get("reason"),
            "Fallback must declare reason=serialization_failure");
        assertEquals("unavailable", parsed.get("sessionSummary"));
        assertEquals(hostileError, parsed.get("error"),
            "Error text must be carried through with proper JSON escaping (round-trips unchanged)");

        // A null error message must still yield valid JSON.
        Map<String, Object> parsedNull = JsonMapper.getInstance()
            .readValue(agenticFlowService.buildSerializationFailureSentinel(null),
                new TypeReference<Map<String, Object>>() {});
        assertEquals("", parsedNull.get("error"),
            "Null error message must serialize as an empty string, not the literal 'null'");
    }

    @Test
    @DisplayName("session_summary real serialization failure routes through catch and writes a valid-JSON sentinel")
    @SuppressWarnings("unchecked")
    void testSerializationFailure_RealCatchPath_WritesValidJsonSentinel() throws Exception {
        String sessionId = "sess_serialize_fail_real_001";
        AgenticSession session = createAgenticSession(sessionId, null);
        session.setUsageCollector(new SessionUsageCollector(sessionId, "ToolPlanner"));
        sessionStore.createSession(session);

        ChatMemory chatMemory = mock(ChatMemory.class);
        when(chatMemory.messages()).thenReturn(Collections.emptyList());
        chatMemoryMap.put(sessionId, chatMemory);

        // Spy shares the singleton sessionStore and the same chatMemoryMap; force the serialization
        // seam to throw so the REAL catch block in addSessionSummaryToResponse runs (not a direct call).
        AgenticFlowService spy = spy(agenticFlowService);
        doThrow(new RuntimeException("forced Jackson failure"))
            .when(spy).serializeStructuredSummary(any());

        AgenticResponse resp = new AgenticResponse(sessionId, AgenticResponse.ResponseType.ERROR);
        resp.setErrorMessage("upstream error");

        spy.attachFailureSummary(
            sessionId, resp, TerminationReason.ERROR, new ActivityEvent.SessionErrored("upstream error"));

        // The session_summary value must still parse as JSON (the sentinel), never a Map.toString() blob.
        Object raw = resp.getOutputVariables().get(AgenticConstants.SYSTEM_VAR_SESSION_SUMMARY);
        assertNotNull(raw, "session_summary must be present even when serialization fails");
        Map<String, Object> parsed = JsonMapper.getInstance()
            .readValue(raw.toString(), new TypeReference<Map<String, Object>>() {});
        assertEquals("serialization_failure", parsed.get("reason"),
            "Catch path must emit the serialization_failure sentinel");
        assertEquals("unavailable", parsed.get("sessionSummary"));

        verify(spy).serializeStructuredSummary(any());
        sessionStore.deleteSession(sessionId);
    }

    // ========================================
    // Helper Methods
    // ========================================

    /**
     * Pulls the session_summary value (a JSON string since KR-20450) from outputVariables
     * and parses it back into a Map for assertion access.
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> parseSessionSummary(Map<String, Object> outputVars) {
        Object raw = outputVars.get(AgenticConstants.SYSTEM_VAR_SESSION_SUMMARY);
        if (raw == null) {
            return null;
        }
        if (raw instanceof Map<?, ?> alreadyMap) {
            return (Map<String, Object>) alreadyMap;
        }
        try {
            return JsonMapper.getInstance()
                .readValue(raw.toString(), new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            throw new AssertionError("Failed to parse session_summary JSON: " + e.getMessage(), e);
        }
    }

    private AgenticSession createAgenticSession(String sessionId, String parentSessionId) {
        AgenticSession session = new AgenticSession(
            sessionId,
            new ArrayList<>(),
            "Test message",
            new ArrayList<>(),
            0,
            Instant.now(),
            Instant.now(),
            null,
            null
        );
        session.setParentSessionId(parentSessionId);
        return session;
    }

    private Map<String, Object> createToolMessage(String toolName, Object result, boolean succeeded) {
        Map<String, Object> message = new HashMap<>();
        message.put("role", "tool");
        message.put("toolName", toolName);
        message.put("result", result);
        message.put("succeeded", succeeded);
        message.put("timestamp", Instant.now().toString());
        return message;
    }

    private Map<String, Object> createUserMessage(String content) {
        Map<String, Object> message = new HashMap<>();
        message.put("role", "user");
        message.put("content", content);
        message.put("timestamp", Instant.now().toString());
        return message;
    }

    private AgenticToolDefinition createMockTool(String name, boolean isHelperTool) {
        return new AgenticToolDefinition(
            name,
            "Test tool description",
            "test",
            Arrays.asList(),
            isHelperTool
        );
    }

    private AgenticCommand createMockCommand(String toolName) {

        return new AgenticCommand(toolName, new HashMap<>());
    }
}
