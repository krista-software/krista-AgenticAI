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
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests for KR-20447: graceful handling when a child flow is started but the
 * parent session is missing or terminated.
 *
 * <p>Before the fix, {@code OrchestrationHandler.handleChildStart()} threw an
 * {@link IllegalStateException} when the parent session was absent.  After the
 * fix, {@code AgenticFlowService.startChildFlow()} catches the condition early
 * and returns an {@link AgenticResponse} with
 * {@link AgenticResponse.ResponseType#ERROR} instead of propagating an
 * exception.</p>
 *
 * @see AgenticFlowService#startChildFlow(AgenticStartRequest, String)
 */
class ChildSessionFlowParentNotFoundTest {

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
    // TC-1: Parent session not found returns ERROR, not exception
    // ========================================

    @Test
    @DisplayName("[Regression] KR-20447 — Parent session not found throws AgenticFlowException with PARENT_SESSION_NOT_FOUND")
    void testStartChildFlow_ParentNotFound_ThrowsAgenticFlowException() {

        // Given: No parent session exists in the session store
        String parentId = "sess_missing_parent_" + UUID.randomUUID();
        String childId = parentId + ".child_" + UUID.randomUUID();

        AgenticStartRequest childRequest = new AgenticStartRequest(
            Arrays.asList(createMockTool("test_tool", false)),
            "Delegated task for child agent",
            new HashMap<>(),
            null,
            null,
            childId
        );

        // When / Then: startChildFlow() throws AgenticFlowException with correct error code
        AgenticFlowException ex = assertThrows(AgenticFlowException.class,
            () -> agenticFlowService.startChildFlow(childRequest, parentId),
            "startChildFlow must throw AgenticFlowException when the parent session is missing"
        );

        assertEquals(AgenticErrorCode.PARENT_SESSION_NOT_FOUND, ex.getErrorCode(),
            "Error code must be PARENT_SESSION_NOT_FOUND");
        assertTrue(ex.getMessage().toLowerCase().contains("parent session not found"),
            "Error message should indicate the parent session was not found, got: " + ex.getMessage());

        // Then: No child session was leaked into the store
        assertNull(sessionStore.getSession(childId),
            "No child session should be created when the parent is missing");

        // Then: ToolPlanner was never called — the request was rejected before reaching LLM
        verifyNoInteractions(toolPlanner);
    }

    // ========================================
    // TC-2: Happy-path sanity check — child start with existing parent succeeds
    // ========================================

    @Test
    @DisplayName("[Happy path] Child start with existing parent succeeds")
    void testStartChildFlow_ParentExists_Succeeds() {

        // Given: A valid, active parent session
        String parentId = "sess_parent_happy_" + UUID.randomUUID();
        AgenticSession parentSession = createAgenticSession(parentId, null);
        parentSession.setUsageCollector(new SessionUsageCollector(parentId, "ToolPlanner"));
        sessionStore.createSession(parentSession);

        String childId = parentId + ".child_happy_" + UUID.randomUUID();
        AgenticToolDefinition tool = createMockTool("lookup_tool", false);

        AgenticStartRequest childRequest = new AgenticStartRequest(
            Arrays.asList(tool),
            "Look up customer data",
            new HashMap<>(),
            null,
            null,
            childId
        );

        // Given: ToolPlanner returns EXECUTE_TOOL so the child session stays alive
        AgenticResponse mockResponse = new AgenticResponse();
        mockResponse.setSessionId(childId);
        mockResponse.setResponseType(AgenticResponse.ResponseType.EXECUTE_TOOL);
        mockResponse.setCommand(createMockCommand("lookup_tool"));
        when(toolPlanner.planNextStep(any(), any(), any(), any(), any(), any(), any()))
            .thenReturn(mockResponse);

        // When
        AgenticResponse result = agenticFlowService.startChildFlow(childRequest, parentId);

        // Then: Successful response — not an error
        assertNotNull(result);
        assertEquals(AgenticResponse.ResponseType.EXECUTE_TOOL, result.getResponseType(),
            "Happy-path child start should return EXECUTE_TOOL");

        // Then: Child session was created and linked to parent
        AgenticSession childSession = sessionStore.getSession(childId);
        assertNotNull(childSession, "Child session must exist in the store");
        assertEquals(parentId, childSession.getParentSessionId(),
            "Child session must reference its parent");
    }

    // ========================================
    // TC-3: Parent session exists but is TERMINATED
    // ========================================

    @Test
    @DisplayName("[Edge case] Parent session exists but is TERMINATED — throws AgenticFlowException with PARENT_SESSION_TERMINATED")
    void testStartChildFlow_ParentTerminated_ThrowsAgenticFlowException() {

        // Given: Parent session exists but has been terminated
        String parentId = "sess_terminated_parent_" + UUID.randomUUID();
        AgenticSession parentSession = createAgenticSession(parentId, null);
        parentSession.setUsageCollector(new SessionUsageCollector(parentId, "ToolPlanner"));
        parentSession.markTerminated();
        sessionStore.createSession(parentSession);

        String childId = parentId + ".child_terminated_" + UUID.randomUUID();
        AgenticStartRequest childRequest = new AgenticStartRequest(
            Arrays.asList(createMockTool("some_tool", false)),
            "Task after parent is done",
            new HashMap<>(),
            null,
            null,
            childId
        );

        // When / Then: Throws AgenticFlowException with correct error code
        AgenticFlowException ex = assertThrows(AgenticFlowException.class,
            () -> agenticFlowService.startChildFlow(childRequest, parentId),
            "startChildFlow must throw AgenticFlowException when the parent session is terminated"
        );

        assertEquals(AgenticErrorCode.PARENT_SESSION_TERMINATED, ex.getErrorCode(),
            "Error code must be PARENT_SESSION_TERMINATED");
        assertTrue(ex.getMessage().toLowerCase().contains("terminated"),
            "Error message should mention 'terminated', got: " + ex.getMessage());

        // Then: No child session was leaked into the store
        assertNull(sessionStore.getSession(childId),
            "No child session should be created when the parent is terminated");

        // Then: ToolPlanner was never invoked
        verifyNoInteractions(toolPlanner);
    }

    // ========================================
    // TC-4: Repeated calls with missing parent return same error consistently
    // ========================================

    @Test
    @DisplayName("[Idempotency] Repeated calls with missing parent throw consistent AgenticFlowExceptions")
    void testStartChildFlow_ParentNotFound_RepeatedCalls_ConsistentError() {

        // Given: No parent session exists
        String parentId = "sess_repeated_missing_" + UUID.randomUUID();

        for (int i = 1; i <= 3; i++) {

            String childId = parentId + ".child_attempt_" + i;
            AgenticStartRequest childRequest = new AgenticStartRequest(
                Arrays.asList(createMockTool("tool_" + i, false)),
                "Attempt " + i,
                new HashMap<>(),
                null,
                null,
                childId
            );

            // When / Then: Each call throws AgenticFlowException
            AgenticFlowException ex = assertThrows(AgenticFlowException.class,
                () -> agenticFlowService.startChildFlow(childRequest, parentId),
                "Attempt " + i + ": startChildFlow must throw AgenticFlowException"
            );

            assertEquals(AgenticErrorCode.PARENT_SESSION_NOT_FOUND, ex.getErrorCode(),
                "Attempt " + i + ": Error code must be PARENT_SESSION_NOT_FOUND");
            assertTrue(ex.getMessage().toLowerCase().contains("parent session not found"),
                "Attempt " + i + ": Error message should indicate parent not found, got: "
                    + ex.getMessage());

            // Then: No child session leaked into the store
            assertNull(sessionStore.getSession(childId),
                "Attempt " + i + ": No child session should exist after a failed start");
        }

        // Then: ToolPlanner was never called across all three attempts
        verifyNoInteractions(toolPlanner);
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
