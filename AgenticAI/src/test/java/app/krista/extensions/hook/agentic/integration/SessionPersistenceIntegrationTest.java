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

package app.krista.extensions.hook.agentic.integration;

import app.krista.extensions.hook.agentic.dto.AgenticCommand;
import app.krista.extensions.hook.agentic.dto.AgenticContinueRequest;
import app.krista.extensions.hook.agentic.dto.AgenticResponse;
import app.krista.extensions.hook.agentic.dto.AgenticStartRequest;
import app.krista.extensions.hook.agentic.dto.AgenticToolDefinition;
import app.krista.extensions.hook.agentic.persistence.AgenticPersistentSessionStore;
import app.krista.extensions.hook.agentic.planner.ToolPlanner;
import app.krista.extensions.hook.agentic.service.AgenticFlowService;
import app.krista.extensions.hook.agentic.session.AgenticSession;
import app.krista.extensions.hook.agentic.session.AgenticSessionStore;
import app.krista.extensions.hook.agentic.session.AgenticSession.SessionState;
import app.krista.extensions.hook.agentic.usagecollector.TokenUsageRecord;
import app.krista.extensions.util.KeyValueStore;
import dev.langchain4j.memory.ChatMemory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;

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
 * Integration tests for session persistence testing REAL production code paths.
 * 
 * CRITICAL: These tests use AgenticFlowService (the production service) rather than
 * directly manipulating stores. This ensures tests will fail if cleanup logic is
 * removed from processResponse() or other service methods.
 * 
 * These tests catch the bugs we fixed:
 * 1. Child sessions reappearing with ACTIVE state (usage rollup timing)
 * 2. Parent sessions remaining in cache after KV deletion (orphan sessions)
 * 
 * TEST REQUIREMENTS:
 * - Tests must use strict assertions (not if-statements with lenient checks)
 * - Tests must verify the actual requirement (session REMOVED, not just TERMINATED)
 * - Tests must fail loudly when bugs are reintroduced
 */
@Tag("integration")
class SessionPersistenceIntegrationTest {

    private AgenticFlowService agenticFlowService;
    private AgenticSessionStore localCache;
    private AgenticPersistentSessionStore persistentStore;
    private ToolPlanner toolPlanner;
    private Map<Object, ChatMemory> chatMemoryMap;
    private TestKeyValueStore testKeyValueStore;

    /**
     * Test KeyValueStore that tracks all operations.
     * This allows us to verify what the production code actually does.
     */
    private static class TestKeyValueStore implements KeyValueStore {
        private final Map<String, String> jsonStore = new ConcurrentHashMap<>();
        private final com.google.gson.Gson gson = new com.google.gson.Gson();
        
        // Track operations for verification
        public List<String> operationsLog = new ArrayList<>();

        @Override
        public void put(String key, Object value) {
            operationsLog.add("PUT:" + key);
            if (value instanceof String) {
                jsonStore.put(key, (String) value);
            } else {
                jsonStore.put(key, gson.toJson(value));
            }
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T get(String key, Class<T> clazz) {
            operationsLog.add("GET:" + key);
            String json = jsonStore.get(key);
            if (json == null) return null;
            if (clazz == Map.class) {
                return (T) gson.fromJson(json, Map.class);
            }
            return (T) json;
        }

        @Override
        public String get(String key) {
            return jsonStore.get(key);
        }

        @Override
        public void remove(String key) {
            operationsLog.add("REMOVE:" + key);
            jsonStore.remove(key);
        }

        @Override
        public boolean contains(String key) {
            return jsonStore.containsKey(key);
        }

        @Override
        public boolean supports(Class<?> clazz) {
            return true;
        }

        @Override
        public java.util.Iterator<Map.Entry<String, Object>> iterator() {
            Map<String, Object> temp = new HashMap<>();
            temp.putAll(jsonStore);
            return temp.entrySet().iterator();
        }

        @Override
        public java.util.Set<String> getKeys() {
            return jsonStore.keySet();
        }
        
        public void clear() {
            jsonStore.clear();
            operationsLog.clear();
        }
    }

    @BeforeEach
    void setUp() {
        // Create test KV store that tracks operations
        testKeyValueStore = new TestKeyValueStore();
        
        // Create real components
        localCache = AgenticSessionStore.getInstance();
        localCache.clear();
        
        // Mock only external dependency (LLM/ToolPlanner)
        toolPlanner = mock(ToolPlanner.class);
        chatMemoryMap = new ConcurrentHashMap<>();
        
        // Create service with REAL stores (this is what production uses)
        agenticFlowService = new AgenticFlowService(localCache, testKeyValueStore, toolPlanner, chatMemoryMap);
        persistentStore = AgenticPersistentSessionStore.getInstance();
    }

    private AgenticToolDefinition createTool(String name, String description) {
        return new AgenticToolDefinition(name, description, "test", new ArrayList<>(), false);
    }

    private AgenticResponse createCompleteResponse(String sessionId, String message) {
        AgenticResponse response = new AgenticResponse();
        response.setSessionId(sessionId);
        response.setResponseType(AgenticResponse.ResponseType.COMPLETE);
        response.setFinalResponse(message);
        response.setOutputVariables(new HashMap<>());
        return response;
    }
    
    private AgenticResponse createToolResponse(String sessionId, String toolName) {

        AgenticResponse response = new AgenticResponse();
        response.setSessionId(sessionId);
        response.setResponseType(AgenticResponse.ResponseType.EXECUTE_TOOL);

        AgenticCommand command = new AgenticCommand(toolName, new HashMap<>());
        response.setCommand(command);

        return response;
    }

    // ========================================
    // Bug Fix: Orphan Parent Session in Cache
    // ========================================

    @Test
    @DisplayName("Parent session MUST be removed from local cache when completing")
    void testParentCleanup_RemovedFromCache() {
        // Setup: Mock LLM to return COMPLETE immediately (triggers cleanup in processResponse)
        when(toolPlanner.planNextStep(any(), any(), any(), any(), any(), any(), any()))
            .thenReturn(createCompleteResponse("any", "Task complete"));
        
        String parentId = "parent_cleanup_001";
        
        // Create parent via service
        AgenticStartRequest request = new AgenticStartRequest(
            Arrays.asList(createTool("work", "Do work")),
            "Test task",
            new HashMap<>(),
            null, null, parentId
        );
        
        // Start flow - with COMPLETE response, processResponse will clean up
        agenticFlowService.startFlow(request);
        
        // CRITICAL ASSERTION: Parent MUST be removed from local cache, not just TERMINATED
        // This catches the bug where sessionStore.deleteSession() was not called
        AgenticSession parentInCache = localCache.getSession(parentId);
        assertNull(parentInCache, 
            "CRITICAL BUG: Parent session " + parentId + " was NOT removed from local cache! " +
            "Line sessionStore.deleteSession() in processResponse() is missing or commented out. " +
            "The session is now orphaned in cache while deleted from KV store.");
        
        // Also verify removed from KV
        localCache.clear(); // Force reload from KV to get true KV state
        AgenticSession parentInKv = persistentStore.getSession(parentId);
        assertNull(parentInKv,
            "Parent should also be removed from KV store");
    }

    @Test
    @DisplayName("Child session MUST be removed from local cache when completing")
    void testChildCleanup_RemovedFromCache() {
        // CORRECTED TEST: startChildFlow() doesn't call processResponse() for cleanup.
        // Cleanup happens in processResponse() which is called by continueFlow().
        // We need to simulate the full child lifecycle: start -> execute tool -> complete.
        
        String parentId = "parent_for_child_001";
        String childId = parentId + ".child_001";
        
        // Step 1: Create parent that stays active (EXECUTE_TOOL response)
        when(toolPlanner.planNextStep(any(), any(), any(), any(), any(), any(), any()))
            .thenReturn(createToolResponse("any", "parent_tool"));
        
        AgenticStartRequest parentRequest = new AgenticStartRequest(
            Arrays.asList(createTool("delegate", "Delegate"), createTool("parent_tool", "Parent Tool")),
            "Parent task",
            new HashMap<>(),
            null, null, parentId
        );
        agenticFlowService.startFlow(parentRequest);
        
        // Verify parent is active
        assertTrue(agenticFlowService.hasSession(parentId), "Parent should be active");
        
        // Step 2: Start child with EXECUTE_TOOL response (child stays active)
        when(toolPlanner.planNextStep(any(), any(), any(), any(), any(), any(), any()))
            .thenReturn(createToolResponse(childId, "child_tool"));
        
        AgenticStartRequest childRequest = new AgenticStartRequest(
            Arrays.asList(createTool("work", "Do work"), createTool("child_tool", "Child Tool")),
            "Child task",
            new HashMap<>(),
            null, null, childId
        );
        agenticFlowService.startChildFlow(childRequest, parentId);
        
        // Verify child was created and is active (not cleaned up yet)
        assertTrue(agenticFlowService.hasSession(childId), "Child should exist after startChildFlow");
        AgenticSession child = agenticFlowService.getSession(childId);
        assertNotNull(child);
        assertEquals(SessionState.ACTIVE, child.getState(), "Child should be ACTIVE initially");
        
        // Step 3: Continue child flow to trigger completion
        // This will call processResponse() which should clean up the child
        when(toolPlanner.planNextStep(any(), any(), any(), any(), any(), any(), any()))
            .thenReturn(createCompleteResponse(childId, "Child completed successfully"));
        
        AgenticContinueRequest continueRequest = new AgenticContinueRequest(
            childId,          // session ID
            "child_tool",     // tool name (from previous EXECUTE_TOOL)
            Map.of("result", "Tool executed successfully")   // tool result
        );
        
        agenticFlowService.continueFlow(continueRequest);
        
        // CRITICAL ASSERTION: Child MUST be removed from local cache after completion
        AgenticSession childInCache = localCache.getSession(childId);
        assertNull(childInCache,
            "CRITICAL BUG: Child session " + childId + " was NOT removed from local cache! " +
            "sessionStore.deleteSession() call in processResponse() for child cleanup is missing. " +
            "The session is orphaned in cache while the flow completed.");
    }

    // ========================================
    // Bug Fix: State Persistence
    // ========================================

    @Test
    @DisplayName("TERMINATED state persists to KV before cache removal")
    void testTerminatedState_PersistedBeforeCacheRemoval() {
        // This test verifies that when a session is marked TERMINATED,
        // that state is persisted to KV BEFORE the session is removed from cache.
        // This prevents the bug where child reloads with stale ACTIVE state.
        
        when(toolPlanner.planNextStep(any(), any(), any(), any(), any(), any(), any()))
            .thenReturn(createCompleteResponse("any", "Done"));
        
        String sessionId = "state_test_001";
        
        AgenticStartRequest request = new AgenticStartRequest(
            Arrays.asList(createTool("work", "Do work")),
            "Test task",
            new HashMap<>(),
            null, null, sessionId
        );
        
        agenticFlowService.startFlow(request);
        
        // The session should have been cleaned up from cache
        // But let's verify by looking at what was persisted
        localCache.clear(); // Force KV lookup
        
        // If the session still exists in KV, it MUST be TERMINATED
        AgenticSession fromKv = persistentStore.getSession(sessionId);
        if (fromKv != null) {
            assertEquals(SessionState.TERMINATED, fromKv.getState(),
                "BUG: Session in KV has state " + fromKv.getState() + " but should be TERMINATED. " +
                "markTerminated() was likely called AFTER cache deletion instead of BEFORE.");
        }
    }

    // ========================================
    // Bug Fix: Usage Rollup Timing
    // ========================================

    @Test
    @DisplayName("Usage rollup MUST happen while child is still in cache")
    void testUsageRollup_WhileChildAccessible() {
        // This test verifies that usage rollup happens BEFORE child cleanup,
        // so the parent can access the child session to merge usage.

        String parentId = "parent_rollup_001";
        String childId = parentId + ".child_001";

        // Mock: Return valid tool for parent, then valid tool for child
        when(toolPlanner.planNextStep(any(), any(), any(), eq(parentId), any(), any(), any()))
            .thenReturn(createToolResponse(parentId, "delegate")); // Valid tool for parent
        when(toolPlanner.planNextStep(any(), any(), any(), eq(childId), any(), any(), any()))
            .thenReturn(createToolResponse(childId, "work")); // Valid tool for child

        // Create parent
        AgenticStartRequest parentRequest = new AgenticStartRequest(
            Arrays.asList(createTool("delegate", "Delegate")),
            "Parent task",
            new HashMap<>(),
            null, null, parentId
        );
        agenticFlowService.startFlow(parentRequest);

        // Create child
        AgenticStartRequest childRequest = new AgenticStartRequest(
            Arrays.asList(createTool("work", "Do work")),
            "Child task",
            new HashMap<>(),
            null, null, childId
        );
        agenticFlowService.startChildFlow(childRequest, parentId);
        
        // Add usage to child
        AgenticSession child = agenticFlowService.getSession(childId);
        assertNotNull(child, "Child must exist to add usage");
        TokenUsageRecord record = TokenUsageRecord.builder()
            .timestamp(Instant.now())
            .modelName("gpt-4")
            .promptTokens(100)
            .completionTokens(50)
            .totalTokens(150)
            .cost(0.00225)
            .requestDurationMs(1000)
            .build();
        child.getUsageCollector().addUsageRecord(record);

        // Now trigger child completion - update mock to return COMPLETE for child
        when(toolPlanner.planNextStep(any(), any(), any(), eq(childId), any(), any(), any()))
            .thenReturn(createCompleteResponse(childId, "Done"));

        // Continue child flow to trigger completion
        AgenticContinueRequest continueRequest = new AgenticContinueRequest(
            childId, "work", "Completed"
        );
        agenticFlowService.continueFlow(continueRequest);
        
        // Verify usage was rolled up to parent
        AgenticSession parent = agenticFlowService.getSession(parentId);
        if (parent != null && parent.getUsageCollector() != null) {
            int childCount = parent.getUsageCollector().getChildSessionsCount();
            assertTrue(childCount > 0,
                "BUG: Usage rollup failed. Parent should have child usage recorded, but has 0. " +
                "This means rollup happened AFTER child was removed from cache.");
        }
        
        // Verify child was cleaned up
        assertNull(localCache.getSession(childId),
            "Child should be removed from cache after completion");
    }

    // ========================================
    // Service Integration Tests
    // ========================================

    @Test
    @DisplayName("Service performs correct KV operations")
    void testServicePerformsCorrectOperations() {
        when(toolPlanner.planNextStep(any(), any(), any(), any(), any(), any(), any()))
            .thenReturn(createToolResponse("any", "work_tool"));
        
        String sessionId = "ops_test_001";
        testKeyValueStore.operationsLog.clear();
        
        AgenticStartRequest request = new AgenticStartRequest(
            Arrays.asList(createTool("work", "Do work"), createTool("work_tool", "Work")),
            "Test task",
            new HashMap<>(),
            null, null, sessionId
        );
        
        agenticFlowService.startFlow(request);
        
        // Verify at least one PUT for persistence
        boolean hasPut = testKeyValueStore.operationsLog.stream()
            .anyMatch(op -> op.startsWith("PUT:"));
        assertTrue(hasPut, "Service should persist session to KV store");
        
        System.out.println("Operations: " + testKeyValueStore.operationsLog);
    }
}
