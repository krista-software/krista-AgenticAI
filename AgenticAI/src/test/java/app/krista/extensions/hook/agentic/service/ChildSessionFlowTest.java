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

import app.krista.extensions.hook.agentic.dto.AgenticCommand;
import app.krista.extensions.hook.agentic.dto.AgenticResponse;
import app.krista.extensions.hook.agentic.dto.AgenticStartRequest;
import app.krista.extensions.hook.agentic.dto.AgenticToolDefinition;
import app.krista.extensions.hook.agentic.planner.ToolPlanner;
import app.krista.extensions.hook.agentic.session.AgenticSession;
import app.krista.extensions.hook.agentic.session.AgenticSessionStore;
import app.krista.extensions.hook.agentic.usagecollector.SessionUsageCollector;
import dev.langchain4j.memory.ChatMemory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for child session lifecycle and parent-child relationship management.
 *
 * @see AgenticFlowService
 */
class ChildSessionFlowTest {

    private AgenticFlowService agenticFlowService;
    private AgenticSessionStore sessionStore;
    private ToolPlanner toolPlanner;
    private Map<Object, ChatMemory> chatMemoryMap;

    @BeforeEach
    void setUp() {
        // Reset session store to ensure clean state
        sessionStore = AgenticSessionStore.getInstance();
        // Clean up any existing test sessions
        cleanupTestSessions();
        
        toolPlanner = mock(ToolPlanner.class);
        chatMemoryMap = new ConcurrentHashMap<>();
        agenticFlowService = new AgenticFlowService(sessionStore, toolPlanner, chatMemoryMap);
    }

    private void cleanupTestSessions() {
        // Remove any test sessions that might exist from previous runs
        // This is a best-effort cleanup
        try {
            // Note: In real implementation, you might want to track test session IDs
            // and clean them up specifically
        } catch (Exception e) {
            // Ignore cleanup errors
        }
    }

    // ========================================
    // Section 1: Child Flow Initialization Tests
    // ========================================

    @Test
    @DisplayName("Start child flow creates session with correct ID format")
    void testStartChildFlow_SessionCreation() {
        // Setup: Create parent session first
        String parentId = "sess_parent_test";
        AgenticSession parentSession = createAgenticSession(parentId, null);
        parentSession.setUsageCollector(new SessionUsageCollector(parentId, "ToolPlanner"));
        sessionStore.createSession(parentSession);

        // Setup: Create child request with a tool so validation passes
        AgenticToolDefinition tool = createMockTool("test_tool", false);
        AgenticStartRequest childRequest = new AgenticStartRequest(
            Arrays.asList(tool),
            "Child task",
            new HashMap<>(),
            null,
            null,
            parentId + ".child_" + UUID.randomUUID().toString()
        );

        // Setup: Mock LLM response
        AgenticResponse mockResponse = new AgenticResponse();
        mockResponse.setSessionId(childRequest.getSessionId());
        mockResponse.setResponseType(AgenticResponse.ResponseType.EXECUTE_TOOL);
        mockResponse.setCommand(createMockCommand("test_tool"));
        when(toolPlanner.planNextStep(any(), any(), any(), any(), any(), any(), any()))
            .thenReturn(mockResponse);

        // Execute
        AgenticResponse result = agenticFlowService.startChildFlow(childRequest, parentId);

        // Verify: Session created (EXECUTE_TOOL does not trigger cleanup)
        assertNotNull(result);
        assertTrue(agenticFlowService.hasSession(childRequest.getSessionId()));

        // Verify: Session ID format is parent.child_uuid
        String childSessionId = childRequest.getSessionId();
        assertTrue(childSessionId.startsWith(parentId + ".child_"));
    }

    @Test
    @DisplayName("Child session has parent reference set correctly")
    void testStartChildFlow_ParentSessionIdLinkage() {
        // Setup: Create parent session
        String parentId = "sess_parent_link";
        AgenticSession parentSession = createAgenticSession(parentId, null);
        parentSession.setUsageCollector(new SessionUsageCollector(parentId, "ToolPlanner"));
        sessionStore.createSession(parentSession);

        // Setup: Create child request with a tool so validation passes
        AgenticToolDefinition tool = createMockTool("test_tool", false);
        String childId = parentId + ".child_link_test";
        AgenticStartRequest childRequest = new AgenticStartRequest(
            Arrays.asList(tool),
            "Child task",
            new HashMap<>(),
            null,
            null,
            childId
        );

        // Setup: Mock LLM response (EXECUTE_TOOL so session is not cleaned up)
        AgenticResponse mockResponse = new AgenticResponse();
        mockResponse.setSessionId(childId);
        mockResponse.setResponseType(AgenticResponse.ResponseType.EXECUTE_TOOL);
        mockResponse.setCommand(createMockCommand("test_tool"));
        when(toolPlanner.planNextStep(any(), any(), any(), any(), any(), any(), any()))
            .thenReturn(mockResponse);

        // Execute
        agenticFlowService.startChildFlow(childRequest, parentId);

        // Verify: Retrieve child session and check parent reference
        AgenticSession childSession = agenticFlowService.getSession(childId);
        assertNotNull(childSession);
        assertEquals(parentId, childSession.getParentSessionId());
    }

    @Test
    @DisplayName("Child session has separate usage collector created")
    void testStartChildFlow_UsageCollectorCreated() {
        // Setup: Create parent session
        String parentId = "sess_parent_usage";
        AgenticSession parentSession = createAgenticSession(parentId, null);
        parentSession.setUsageCollector(new SessionUsageCollector(parentId, "ToolPlanner"));
        sessionStore.createSession(parentSession);

        // Setup: Create child request with a tool so validation passes
        AgenticToolDefinition tool = createMockTool("test_tool", false);
        String childId = parentId + ".child_usage_test";
        AgenticStartRequest childRequest = new AgenticStartRequest(
            Arrays.asList(tool),
            "Child task",
            new HashMap<>(),
            null,
            null,
            childId
        );

        // Setup: Mock LLM response (EXECUTE_TOOL so session is not cleaned up)
        AgenticResponse mockResponse = new AgenticResponse();
        mockResponse.setSessionId(childId);
        mockResponse.setResponseType(AgenticResponse.ResponseType.EXECUTE_TOOL);
        mockResponse.setCommand(createMockCommand("test_tool"));
        when(toolPlanner.planNextStep(any(), any(), any(), any(), any(), any(), any()))
            .thenReturn(mockResponse);

        // Execute
        agenticFlowService.startChildFlow(childRequest, parentId);

        // Verify: Child has usage collector
        AgenticSession childSession = agenticFlowService.getSession(childId);
        assertNotNull(childSession);
        assertNotNull(childSession.getUsageCollector());
    }

    @Test
    @DisplayName("Child flow calls LLM with validation")
    void testStartChildFlow_LlmCall() {
        // Setup: Create parent session
        String parentId = "sess_parent_llm";
        AgenticSession parentSession = createAgenticSession(parentId, null);
        parentSession.setUsageCollector(new SessionUsageCollector(parentId, "ToolPlanner"));
        sessionStore.createSession(parentSession);

        // Setup: Create child request with tools
        AgenticToolDefinition businessTool = createMockTool("business_tool", false);
        AgenticToolDefinition helperTool = createMockTool("helper_tool", true);
        
        String childId = parentId + ".child_llm_test";
        AgenticStartRequest childRequest = new AgenticStartRequest(
            Arrays.asList(businessTool, helperTool),
            "Child task",
            new HashMap<>(),
            null,
            null,
            childId
        );

        // Setup: Mock LLM response
        AgenticResponse mockResponse = new AgenticResponse();
        mockResponse.setSessionId(childId);
        mockResponse.setResponseType(AgenticResponse.ResponseType.EXECUTE_TOOL);
        mockResponse.setCommand(createMockCommand("business_tool"));
        when(toolPlanner.planNextStep(any(), any(), any(), any(), any(), any(), any()))
            .thenReturn(mockResponse);

        // Execute
        AgenticResponse result = agenticFlowService.startChildFlow(childRequest, parentId);

        // Verify: Tool planner was called
        verify(toolPlanner, times(1)).planNextStep(
            any(), // businessTools
            any(), // helperTools
            eq("Child task"), // userMessage
            eq(childId), // sessionId
            any(), // previousResult
            any(), // inputVarsFormatted
            any()  // expectedOutputsFormatted
        );
        
        assertNotNull(result);
        assertEquals(AgenticResponse.ResponseType.EXECUTE_TOOL, result.getResponseType());
    }

    // ========================================
    // Section 2: Response Cleanliness Tests
    // ========================================

    @Test
    @DisplayName("Child response does not contain session summaries in outputVariables")
    void testChildResponse_Clean_NoSummaries() {
        // Setup: Create parent session
        String parentId = "sess_parent_clean";
        AgenticSession parentSession = createAgenticSession(parentId, null);
        parentSession.setUsageCollector(new SessionUsageCollector(parentId, "ToolPlanner"));
        sessionStore.createSession(parentSession);

        // Setup: Create child request
        String childId = parentId + ".child_clean_test";
        AgenticStartRequest childRequest = new AgenticStartRequest(
            Arrays.asList(),
            "Child task",
            new HashMap<>(),
            null,
            null,
            childId
        );

        // Setup: Mock ChatMemory for child
        ChatMemory childChatMemory = mock(ChatMemory.class);
        when(childChatMemory.messages()).thenReturn(Arrays.asList());
        chatMemoryMap.put(childId, childChatMemory);

        // Setup: Create COMPLETE response for child
        AgenticResponse mockResponse = new AgenticResponse();
        mockResponse.setSessionId(childId);
        mockResponse.setResponseType(AgenticResponse.ResponseType.COMPLETE);
        mockResponse.setFinalResponse("Child task completed successfully");
        
        // Set clean output variables
        Map<String, Object> cleanOutputVars = new HashMap<>();
        cleanOutputVars.put("childResult", "success");
        cleanOutputVars.put("data", "some data");
        mockResponse.setOutputVariables(cleanOutputVars);

        when(toolPlanner.planNextStep(any(), any(), any(), any(), any(), any(), any()))
            .thenReturn(mockResponse);

        // Execute
        AgenticResponse result = agenticFlowService.startChildFlow(childRequest, parentId);

        // Verify: Response is clean - no session summaries
        Map<String, Object> resultOutputVars = result.getOutputVariables();
        assertNotNull(resultOutputVars);
        
        // Should have the values we set
        assertEquals("success", resultOutputVars.get("childResult"));
        assertEquals("some data", resultOutputVars.get("data"));
        
        // Should NOT have conversation_history or tool_summary
        assertFalse(resultOutputVars.containsKey("conversation_history"));
        assertFalse(resultOutputVars.containsKey("tool_summary"));
    }

    // ========================================
    // Section 3: Session Management Tests
    // ========================================

    @Test
    @DisplayName("Check if child session exists in store")
    void testHasSession_ChildSessionExists() {
        // Setup: Create and store child session
        String childId = "sess_child_exists_test";
        AgenticSession childSession = createAgenticSession(childId, "sess_parent");
        childSession.setUsageCollector(new SessionUsageCollector(childId, "ToolPlanner-Child"));
        agenticFlowService.createSession(childSession);

        // Verify: hasSession returns true
        assertTrue(agenticFlowService.hasSession(childId));
    }

    @Test
    @DisplayName("Check if non-existent child session returns false")
    void testHasSession_ChildSessionNotExists() {
        // Test with session ID that was never created
        String nonExistentId = "sess_nonexistent_12345";
        
        // Verify: hasSession returns false
        assertFalse(agenticFlowService.hasSession(nonExistentId));
    }

    @Test
    @DisplayName("Retrieve child session from store")
    void testGetSession_ChildSessionRetrieved() {
        // Setup: Create and store child session
        String childId = "sess_child_retrieve_test";
        String parentId = "sess_parent_retrieve";
        AgenticSession childSession = createAgenticSession(childId, parentId);
        childSession.setUsageCollector(new SessionUsageCollector(childId, "ToolPlanner-Child"));
        agenticFlowService.createSession(childSession);

        // Execute
        AgenticSession retrieved = agenticFlowService.getSession(childId);

        // Verify
        assertNotNull(retrieved);
        assertEquals(childId, retrieved.getSessionId());
        assertEquals(parentId, retrieved.getParentSessionId());
    }

    @Test
    @DisplayName("Retrieve non-existent child session returns null")
    void testGetSession_ChildSessionNotFound() {
        // Test with session ID that was never created
        String nonExistentId = "sess_not_found_99999";
        
        // Execute
        AgenticSession result = agenticFlowService.getSession(nonExistentId);
        
        // Verify
        assertNull(result);
    }

    @Test
    @DisplayName("Delete child session from store")
    void testDeleteSession_ChildRemoved() {
        // Setup: Create and store child session
        String childId = "sess_child_delete_test";
        AgenticSession childSession = createAgenticSession(childId, "sess_parent");
        childSession.setUsageCollector(new SessionUsageCollector(childId, "ToolPlanner-Child"));
        agenticFlowService.createSession(childSession);
        
        // Verify: Session exists before deletion
        assertTrue(agenticFlowService.hasSession(childId));

        // Execute
        agenticFlowService.deleteSession(childId);

        // Verify: Session no longer exists
        assertFalse(agenticFlowService.hasSession(childId));
        assertNull(agenticFlowService.getSession(childId));
    }

    @Test
    @DisplayName("Create session without starting flow")
    void testCreateSession_Standalone() {
        // Setup: Create session manually
        String sessionId = "sess_standalone_test";
        AgenticSession session = createAgenticSession(sessionId, null);
        session.setUsageCollector(new SessionUsageCollector(sessionId, "ToolPlanner"));

        // Execute
        agenticFlowService.createSession(session);

        // Verify: Session is stored
        assertTrue(agenticFlowService.hasSession(sessionId));
        AgenticSession retrieved = agenticFlowService.getSession(sessionId);
        assertNotNull(retrieved);
        assertEquals(sessionId, retrieved.getSessionId());
    }

    // ========================================
    // Section 4: Parent-Child Relationship Tests
    // ========================================

    @Test
    @DisplayName("Parent session has null parentSessionId")
    void testParentSession_NullParentId() {
        // Setup: Create parent session
        String parentId = "sess_parent_null";
        AgenticSession parentSession = createAgenticSession(parentId, null);
        parentSession.setUsageCollector(new SessionUsageCollector(parentId, "ToolPlanner"));
        agenticFlowService.createSession(parentSession);

        // Verify
        AgenticSession retrieved = agenticFlowService.getSession(parentId);
        assertNotNull(retrieved);
        assertNull(retrieved.getParentSessionId());
    }

    @Test
    @DisplayName("Multiple child sessions can have same parent")
    void testMultipleChildren_SameParent() {
        // Setup: Create parent
        String parentId = "sess_parent_multiple";
        AgenticSession parentSession = createAgenticSession(parentId, null);
        parentSession.setUsageCollector(new SessionUsageCollector(parentId, "ToolPlanner"));
        agenticFlowService.createSession(parentSession);

        // Setup: Create multiple children
        String child1Id = parentId + ".child_1";
        String child2Id = parentId + ".child_2";
        String child3Id = parentId + ".child_3";

        AgenticSession child1 = createAgenticSession(child1Id, parentId);
        AgenticSession child2 = createAgenticSession(child2Id, parentId);
        AgenticSession child3 = createAgenticSession(child3Id, parentId);

        child1.setUsageCollector(new SessionUsageCollector(child1Id, "ToolPlanner-Child"));
        child2.setUsageCollector(new SessionUsageCollector(child2Id, "ToolPlanner-Child"));
        child3.setUsageCollector(new SessionUsageCollector(child3Id, "ToolPlanner-Child"));

        agenticFlowService.createSession(child1);
        agenticFlowService.createSession(child2);
        agenticFlowService.createSession(child3);

        // Verify: All children exist and have correct parent
        assertTrue(agenticFlowService.hasSession(child1Id));
        assertTrue(agenticFlowService.hasSession(child2Id));
        assertTrue(agenticFlowService.hasSession(child3Id));

        assertEquals(parentId, agenticFlowService.getSession(child1Id).getParentSessionId());
        assertEquals(parentId, agenticFlowService.getSession(child2Id).getParentSessionId());
        assertEquals(parentId, agenticFlowService.getSession(child3Id).getParentSessionId());
    }

    @Test
    @DisplayName("Deleting parent does not automatically delete children")
    void testDeleteParent_ChildrenRemain() {
        // Setup: Create parent and child
        String parentId = "sess_parent_with_child";
        String childId = parentId + ".child_remain";

        AgenticSession parentSession = createAgenticSession(parentId, null);
        parentSession.setUsageCollector(new SessionUsageCollector(parentId, "ToolPlanner"));
        agenticFlowService.createSession(parentSession);

        AgenticSession childSession = createAgenticSession(childId, parentId);
        childSession.setUsageCollector(new SessionUsageCollector(childId, "ToolPlanner-Child"));
        agenticFlowService.createSession(childSession);

        // Verify both exist
        assertTrue(agenticFlowService.hasSession(parentId));
        assertTrue(agenticFlowService.hasSession(childId));

        // Execute: Delete only parent
        agenticFlowService.deleteSession(parentId);

        // Verify: Parent deleted, child still exists
        assertFalse(agenticFlowService.hasSession(parentId));
        assertTrue(agenticFlowService.hasSession(childId));
        assertNotNull(agenticFlowService.getSession(childId));
    }

    // ========================================
    // Helper Methods
    // ========================================

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
