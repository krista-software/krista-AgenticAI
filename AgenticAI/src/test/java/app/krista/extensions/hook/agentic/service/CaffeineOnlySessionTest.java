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
import app.krista.extensions.hook.agentic.dto.AgenticContinueRequest;
import app.krista.extensions.hook.agentic.dto.AgenticErrorCode;
import app.krista.extensions.hook.agentic.dto.AgenticResponse;
import app.krista.extensions.hook.agentic.dto.AgenticStartRequest;
import app.krista.extensions.hook.agentic.dto.AgenticToolDefinition;
import app.krista.extensions.hook.agentic.guardrails.AgenticFlowException;
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
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests for KR-20581: verifies that persistence is disabled (3-arg constructor)
 * and that {@code chatMemoryMap} entries are cleaned up on session completion/error,
 * preventing heap leaks.
 *
 * <p>Uses the same test patterns as {@link ChildSessionFlowTest}: real
 * {@link AgenticSessionStore} (Caffeine cache), mocked {@link ToolPlanner},
 * and a {@link ConcurrentHashMap} for chatMemoryMap.</p>
 *
 * @see AgenticFlowService#performSessionCleanup
 */
class CaffeineOnlySessionTest {

    private AgenticFlowService flowService;
    private AgenticSessionStore sessionStore;
    private ToolPlanner toolPlanner;
    private Map<Object, ChatMemory> chatMemoryMap;

    @BeforeEach
    void setUp() {

        sessionStore = AgenticSessionStore.getInstance();
        sessionStore.clear();

        toolPlanner = mock(ToolPlanner.class);
        chatMemoryMap = new ConcurrentHashMap<>();
        flowService = new AgenticFlowService(sessionStore, toolPlanner, chatMemoryMap);
    }

    // ========================================
    // TC-A: chatMemoryMap cleared on parent COMPLETE
    // ========================================

    @Test
    @DisplayName("TC-A: chatMemoryMap entry removed when parent session completes")
    void testChatMemoryMapClearedOnParentComplete() {

        String sessionId = "sess_complete_cleanup";

        // Pre-populate chatMemoryMap with a mock entry
        ChatMemory mockMemory = mock(ChatMemory.class);
        when(mockMemory.messages()).thenReturn(List.of());
        chatMemoryMap.put(sessionId, mockMemory);

        // Mock ToolPlanner to return COMPLETE
        AgenticResponse completeResponse = createCompleteResponse(sessionId, "Done");
        when(toolPlanner.planNextStep(any(), any(), any(), any(), any(), any(), any()))
            .thenReturn(completeResponse);

        // Precondition: chatMemoryMap contains the session entry
        assertTrue(chatMemoryMap.containsKey(sessionId),
            "chatMemoryMap should contain sessionId before startFlow");

        // Build start request
        AgenticStartRequest request = createStartRequest(sessionId, "Complete this task");

        // Execute
        AgenticResponse result = flowService.startFlow(request);

        // Postcondition: chatMemoryMap entry removed after COMPLETE
        assertFalse(chatMemoryMap.containsKey(sessionId),
            "chatMemoryMap should NOT contain sessionId after COMPLETE cleanup");
        assertEquals(AgenticResponse.ResponseType.COMPLETE, result.getResponseType());
    }

    // ========================================
    // TC-B: chatMemoryMap cleared on parent ERROR
    // ========================================

    @Test
    @DisplayName("TC-B: chatMemoryMap entry removed when parent session errors")
    void testChatMemoryMapClearedOnParentError() {

        String sessionId = "sess_error_cleanup";

        // Pre-populate chatMemoryMap
        ChatMemory mockMemory = mock(ChatMemory.class);
        when(mockMemory.messages()).thenReturn(List.of());
        chatMemoryMap.put(sessionId, mockMemory);

        // Mock ToolPlanner to return ERROR
        AgenticResponse errorResponse = createErrorResponse(sessionId, "Test error");
        when(toolPlanner.planNextStep(any(), any(), any(), any(), any(), any(), any()))
            .thenReturn(errorResponse);

        // Precondition
        assertTrue(chatMemoryMap.containsKey(sessionId),
            "chatMemoryMap should contain sessionId before startFlow");

        // Build start request
        AgenticStartRequest request = createStartRequest(sessionId, "This will fail");

        // Execute
        AgenticResponse result = flowService.startFlow(request);

        // Postcondition: chatMemoryMap entry removed after ERROR
        assertFalse(chatMemoryMap.containsKey(sessionId),
            "chatMemoryMap should NOT contain sessionId after ERROR cleanup");
        assertEquals(AgenticResponse.ResponseType.ERROR, result.getResponseType());
    }

    // ========================================
    // TC-C: chatMemoryMap cleared on child COMPLETE, parent untouched
    // ========================================

    @Test
    @DisplayName("TC-C: child chatMemoryMap entry removed on COMPLETE while parent entry remains")
    void testChatMemoryMapClearedOnChildComplete_ParentUntouched() {

        String parentId = "sess_parent_mem";
        String childId = parentId + ".child_mem_test";

        // Create parent session with UsageCollector (required by startChildFlow)
        AgenticSession parentSession = createAgenticSession(parentId, null);
        parentSession.setUsageCollector(new SessionUsageCollector(parentId, "ToolPlanner"));
        sessionStore.createSession(parentSession);

        // Pre-populate chatMemoryMap for BOTH parent and child
        ChatMemory parentMemory = mock(ChatMemory.class);
        when(parentMemory.messages()).thenReturn(List.of());
        chatMemoryMap.put(parentId, parentMemory);

        ChatMemory childMemory = mock(ChatMemory.class);
        when(childMemory.messages()).thenReturn(List.of());
        chatMemoryMap.put(childId, childMemory);

        // Mock ToolPlanner to return COMPLETE for the child
        AgenticResponse childComplete = createCompleteResponse(childId, "Child done");
        when(toolPlanner.planNextStep(any(), any(), any(), any(), any(), any(), any()))
            .thenReturn(childComplete);

        // Build child request
        AgenticStartRequest childRequest = new AgenticStartRequest(
            List.of(),
            "Child task",
            new HashMap<>(),
            null,
            null,
            childId
        );

        // Execute
        AgenticResponse result = flowService.startChildFlow(childRequest, parentId);

        // Child chatMemoryMap entry removed
        assertFalse(chatMemoryMap.containsKey(childId),
            "chatMemoryMap should NOT contain childId after child COMPLETE");

        // Parent chatMemoryMap entry still present
        assertTrue(chatMemoryMap.containsKey(parentId),
            "chatMemoryMap should still contain parentId after child COMPLETE");

        assertEquals(AgenticResponse.ResponseType.COMPLETE, result.getResponseType());
    }

    // ========================================
    // TC-D: chatMemoryMap.remove is safe no-op when key absent
    // ========================================

    @Test
    @DisplayName("TC-D: chatMemoryMap.remove is a safe no-op when session key is absent")
    void testChatMemoryMapRemoveSafeWhenKeyAbsent() {

        String sessionId = "sess_no_memory_entry";

        // Do NOT put anything in chatMemoryMap for this sessionId

        // Mock ToolPlanner to return COMPLETE
        AgenticResponse completeResponse = createCompleteResponse(sessionId, "Done");
        when(toolPlanner.planNextStep(any(), any(), any(), any(), any(), any(), any()))
            .thenReturn(completeResponse);

        AgenticStartRequest request = createStartRequest(sessionId, "Task without memory");

        // Should not throw any exception
        assertDoesNotThrow(() -> flowService.startFlow(request),
            "startFlow should not throw when chatMemoryMap has no entry for sessionId");

        // Confirm still absent (no phantom entry created)
        assertFalse(chatMemoryMap.containsKey(sessionId),
            "chatMemoryMap should still not contain sessionId after cleanup");
    }

    // ========================================
    // TC-E: continueFlow with unknown session returns ERROR, not NPE
    // ========================================

    @Test
    @DisplayName("TC-E: continueFlow with non-existent session throws AgenticFlowException, not NPE")
    void testContinueFlowUnknownSessionThrowsAgenticFlowException() {

        String unknownSessionId = "sess_does_not_exist_99999";

        AgenticContinueRequest continueRequest = new AgenticContinueRequest(
            unknownSessionId,
            "some_tool",
            Map.of("status", "success")
        );

        // Should throw AgenticFlowException with SESSION_NOT_FOUND, not NPE
        AgenticFlowException ex = assertThrows(AgenticFlowException.class,
            () -> flowService.continueFlow(continueRequest),
            "continueFlow should throw AgenticFlowException for non-existent session, not NPE"
        );

        assertEquals(AgenticErrorCode.SESSION_NOT_FOUND, ex.getErrorCode());
        assertTrue(ex.getMessage().contains("Session not found"),
            "Error message should indicate session not found, got: " + ex.getMessage());
    }

    // ========================================
    // TC-F: 3-arg constructor wiring — continueFlow reads from sessionStore only
    // ========================================

    @Test
    @DisplayName("TC-F: 3-arg constructor — continueFlow resolves session from Caffeine cache")
    void testThreeArgConstructorContinueFlowUsesSessionStore() {

        String sessionId = "sess_caffeine_only";

        // Create session directly in the real AgenticSessionStore
        AgenticSession session = createAgenticSession(sessionId, null);
        AgenticToolDefinition tool = createMockTool("test_tool", false);
        session.setTools(List.of(tool));
        session.setUsageCollector(new SessionUsageCollector(sessionId, "ToolPlanner"));
        sessionStore.createSession(session);

        // Add ChatMemory entry to chatMemoryMap
        ChatMemory mockMemory = mock(ChatMemory.class);
        when(mockMemory.messages()).thenReturn(List.of());
        chatMemoryMap.put(sessionId, mockMemory);

        // Mock ToolPlanner to return EXECUTE_TOOL
        AgenticResponse executeResponse = new AgenticResponse();
        executeResponse.setSessionId(sessionId);
        executeResponse.setResponseType(AgenticResponse.ResponseType.EXECUTE_TOOL);
        executeResponse.setCommand(createMockCommand("test_tool"));
        when(toolPlanner.planNextStep(any(), any(), any(), any(), any(), any(), any()))
            .thenReturn(executeResponse);

        // Build continue request with a tool result
        AgenticContinueRequest continueRequest = new AgenticContinueRequest(
            sessionId,
            "test_tool",
            Map.of("status", "success", "data", "result_value")
        );

        // Execute
        AgenticResponse result = flowService.continueFlow(continueRequest);

        // Assert response is NOT an error — session was found via Caffeine cache
        assertNotNull(result, "Response should not be null");
        assertNotEquals(AgenticResponse.ResponseType.ERROR, result.getResponseType(),
            "Session should have been found in Caffeine cache; got ERROR instead");
        assertEquals(AgenticResponse.ResponseType.EXECUTE_TOOL, result.getResponseType());

        // ChatMemory should still be present since EXECUTE_TOOL does not trigger cleanup
        assertTrue(chatMemoryMap.containsKey(sessionId),
            "chatMemoryMap should still contain sessionId after EXECUTE_TOOL (no cleanup)");
    }

    // ========================================
    // TC3: deleteSession removes chatMemoryMap entry
    // ========================================

    @Test
    @DisplayName("TC3: deleteSession removes chatMemoryMap entry for the deleted session")
    void deleteSession_removesChatMemory() {

        String sessionId = "sess_delete_memory";

        // Pre-populate chatMemoryMap with a real-ish memory entry
        ChatMemory mockMemory = mock(ChatMemory.class);
        when(mockMemory.messages()).thenReturn(List.of());
        chatMemoryMap.put(sessionId, mockMemory);

        // Create a session in the store so deleteSession has something to clean up
        AgenticSession session = createAgenticSession(sessionId, null);
        sessionStore.createSession(session);

        // Precondition: entry exists
        assertTrue(chatMemoryMap.containsKey(sessionId),
            "chatMemoryMap should contain sessionId before deleteSession");

        // Execute
        flowService.deleteSession(sessionId);

        // Postcondition: entry removed
        assertFalse(chatMemoryMap.containsKey(sessionId),
            "chatMemoryMap should NOT contain sessionId after deleteSession");
    }

    // ========================================
    // TC-G: deleteSession is safe when no chatMemoryMap entry exists
    // ========================================

    @Test
    @DisplayName("TC-G: deleteSession does not throw when chatMemoryMap has no entry for the session")
    void deleteSession_safeWhenNoChatMemoryEntry() {

        String sessionId = "sess_delete_no_memory";

        // Create a session in the store but do NOT add anything to chatMemoryMap
        AgenticSession session = createAgenticSession(sessionId, null);
        sessionStore.createSession(session);

        // Should not throw any exception
        assertDoesNotThrow(() -> flowService.deleteSession(sessionId),
            "deleteSession should not throw when chatMemoryMap has no entry for sessionId");

        // Map should still be empty — no phantom entry created
        assertFalse(chatMemoryMap.containsKey(sessionId),
            "chatMemoryMap should remain empty after deleteSession with no prior entry");
    }

    // ========================================
    // TC-H: deleteSession does not affect other sessions' chatMemoryMap entries
    // ========================================

    @Test
    @DisplayName("TC-H: deleteSession removes only the target session's chatMemoryMap entry, leaving others intact")
    void deleteSession_doesNotAffectOtherSessions() {

        String sessionIdA = "sessionId-A";
        String sessionIdB = "sessionId-B";

        // Pre-populate chatMemoryMap for both sessions
        ChatMemory memoryA = mock(ChatMemory.class);
        when(memoryA.messages()).thenReturn(List.of());
        chatMemoryMap.put(sessionIdA, memoryA);

        ChatMemory memoryB = mock(ChatMemory.class);
        when(memoryB.messages()).thenReturn(List.of());
        chatMemoryMap.put(sessionIdB, memoryB);

        // Create sessions in the store for both
        sessionStore.createSession(createAgenticSession(sessionIdA, null));
        sessionStore.createSession(createAgenticSession(sessionIdB, null));

        // Delete only session A
        flowService.deleteSession(sessionIdA);

        // Session A's entry must be gone
        assertFalse(chatMemoryMap.containsKey(sessionIdA),
            "chatMemoryMap should NOT contain sessionId-A after deleteSession(sessionId-A)");

        // Session B's entry must remain untouched
        assertTrue(chatMemoryMap.containsKey(sessionIdB),
            "chatMemoryMap should still contain sessionId-B after deleteSession(sessionId-A)");
    }

    // ========================================
    // Helper Methods
    // ========================================

    private AgenticStartRequest createStartRequest(String sessionId, String userMessage) {

        return new AgenticStartRequest(
            List.of(),
            userMessage,
            new HashMap<>(),
            null,
            null,
            sessionId
        );
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

    private AgenticResponse createCompleteResponse(String sessionId, String finalResponse) {

        AgenticResponse response = new AgenticResponse();
        response.setSessionId(sessionId);
        response.setResponseType(AgenticResponse.ResponseType.COMPLETE);
        response.setFinalResponse(finalResponse);
        return response;
    }

    private AgenticResponse createErrorResponse(String sessionId, String errorMessage) {

        AgenticResponse response = new AgenticResponse();
        response.setSessionId(sessionId);
        response.setResponseType(AgenticResponse.ResponseType.ERROR);
        response.setErrorMessage(errorMessage);
        return response;
    }
}
