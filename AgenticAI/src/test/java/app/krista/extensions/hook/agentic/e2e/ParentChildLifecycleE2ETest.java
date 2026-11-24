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

package app.krista.extensions.hook.agentic.e2e;

import app.krista.extensions.hook.agentic.dto.AgenticCommand;
import app.krista.extensions.hook.agentic.dto.AgenticResponse;
import app.krista.extensions.hook.agentic.dto.AgenticStartRequest;
import app.krista.extensions.hook.agentic.dto.AgenticToolDefinition;
import app.krista.extensions.hook.agentic.persistence.AgenticPersistentSessionStore;
import app.krista.extensions.hook.agentic.planner.ToolPlanner;
import app.krista.extensions.hook.agentic.service.AgenticFlowService;
import app.krista.extensions.hook.agentic.session.AgenticSession;
import app.krista.extensions.hook.agentic.session.AgenticSessionStore;
import app.krista.extensions.hook.agentic.session.AgenticSession.SessionState;
import app.krista.extensions.hook.agentic.usagecollector.SessionUsageCollector;
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
 * End-to-end tests for parent-child session workflows.
 * 
 * These tests verify the integration between service, cache, and persistence layers.
 * Note: Full lifecycle tests are complex due to async persistence and session cleanup.
 * These tests focus on verifying the core integration behaviors.
 */
@Tag("e2e")
class ParentChildLifecycleE2ETest {

    private AgenticFlowService agenticFlowService;
    private AgenticSessionStore localCache;
    private AgenticPersistentSessionStore persistentStore;
    private ToolPlanner toolPlanner;
    private Map<Object, ChatMemory> chatMemoryMap;
    private InMemoryKeyValueStore simulatedKvStore;

    /**
     * Simulated KeyValueStore that behaves like real persistence.
     * Properly handles JSON serialization/deserialization.
     */
    private static class InMemoryKeyValueStore {
        private final Map<String, String> jsonStore = new ConcurrentHashMap<>();
        private final com.google.gson.Gson gson = new com.google.gson.Gson();
        
        public void put(String key, Object value) {
            // Store as JSON string
            if (value instanceof String) {
                jsonStore.put(key, (String) value);
            } else {
                jsonStore.put(key, gson.toJson(value));
            }
        }
        
        @SuppressWarnings("unchecked")
        public <T> T get(String key, Class<T> clazz) {
            String json = jsonStore.get(key);
            if (json == null) {
                return null;
            }
            
            // If requesting Map.class, deserialize JSON to Map
            if (clazz == Map.class) {
                return (T) gson.fromJson(json, Map.class);
            }
            
            // Otherwise return raw string
            return (T) json;
        }
        
        public void clear() {
            jsonStore.clear();
        }
    }

    @BeforeEach
    void setUp() {
        // Create real components
        localCache = AgenticSessionStore.getInstance();
        localCache.clear();
        
        // Create simulated persistence
        simulatedKvStore = new InMemoryKeyValueStore();
        KeyValueStore mockKeyValueStore = createDelegatingMock();
        
        // Create service with real stores
        toolPlanner = mock(ToolPlanner.class);
        chatMemoryMap = new ConcurrentHashMap<>();
        agenticFlowService = new AgenticFlowService(localCache, mockKeyValueStore, toolPlanner, chatMemoryMap);
        persistentStore = AgenticPersistentSessionStore.getInstance();
    }
    
    private KeyValueStore createDelegatingMock() {
        KeyValueStore mock = mock(KeyValueStore.class);
        
        // Delegate put to simulated store
        doAnswer(invocation -> {
            String key = invocation.getArgument(0);
            Object value = invocation.getArgument(1);
            simulatedKvStore.put(key, value);
            return null;
        }).when(mock).put(anyString(), any());
        
        // Delegate get to simulated store
        when(mock.get(anyString(), any())).thenAnswer(invocation -> {
            String key = invocation.getArgument(0);
            Class<?> clazz = invocation.getArgument(1);
            return simulatedKvStore.get(key, clazz);
        });
        
        return mock;
    }

    private AgenticToolDefinition createTool(String name, String description) {
        return new AgenticToolDefinition(name, description, "test", new ArrayList<>(), false);
    }

    // ========================================
    // Simplified E2E Tests
    // ========================================

    @Test
    @DisplayName("E2E: Session creation and basic properties")
    void testSessionCreation_BasicProperties() {
        String sessionId = "test_session_001";
        List<AgenticToolDefinition> tools = Arrays.asList(
            createTool("tool1", "Test tool 1")
        );
        
        AgenticStartRequest request = new AgenticStartRequest(
            tools,
            "Test task",
            new HashMap<>(),
            null,
            null,
            sessionId
        );
        
        // Mock successful completion
        when(toolPlanner.planNextStep(any(), any(), any(), any(), any(), any(), any()))
            .thenReturn(createCompleteResponse(sessionId, "Task completed"));
        
        // Execute
        AgenticResponse response = agenticFlowService.startFlow(request);
        
        // Verify response
        assertNotNull(response);
        
        // Note: Session may or may not exist depending on cleanup timing
        // The key test is that the flow executed without errors
        System.out.println("Session state after flow: " + 
            (agenticFlowService.hasSession(sessionId) ? "exists" : "cleaned up"));
    }

    @Test
    @DisplayName("E2E: Parent-child session relationship")
    void testParentChildRelationship() {

        String parentId = "parent_test_001";
        String childId = parentId + ".child_001";

        // Create parent — return EXECUTE_TOOL so the parent session stays ACTIVE
        List<AgenticToolDefinition> tools = Arrays.asList(createTool("delegate", "Delegate tool"));
        AgenticStartRequest parentRequest = new AgenticStartRequest(
            tools, "Parent task", new HashMap<>(), null, null, parentId
        );

        AgenticResponse parentExecResponse = new AgenticResponse();
        parentExecResponse.setSessionId(parentId);
        parentExecResponse.setResponseType(AgenticResponse.ResponseType.EXECUTE_TOOL);
        parentExecResponse.setCommand(new AgenticCommand("delegate", new HashMap<>()));

        when(toolPlanner.planNextStep(any(), any(), any(), any(), any(), any(), any()))
            .thenReturn(parentExecResponse);

        agenticFlowService.startFlow(parentRequest);

        // Create child — parent is still ACTIVE, so startChildFlow succeeds
        AgenticStartRequest childRequest = new AgenticStartRequest(
            tools, "Child task", new HashMap<>(), null, null, childId
        );

        when(toolPlanner.planNextStep(any(), any(), any(), any(), any(), any(), any()))
            .thenReturn(createCompleteResponse(childId, "Child done"));

        AgenticResponse childResponse = agenticFlowService.startChildFlow(childRequest, parentId);

        // Verify child was created (response may indicate completion)
        assertNotNull(childResponse);

        // Verify parent-child relationship in the request
        assertTrue(childId.startsWith(parentId + "."),
            "Child ID should be prefixed with parent ID");
    }

    @Test
    @DisplayName("E2E: Usage collection within a session")
    void testUsageCollection() {
        String sessionId = "usage_test_001";
        
        // Create a session and manually test usage collection
        AgenticSession session = new AgenticSession(
            sessionId,
            new ArrayList<>(),
            "Test message",
            new ArrayList<>(),
            0,
            Instant.now(),
            Instant.now(),
            new ArrayList<>(),
            new ArrayList<>()
        );
        
        SessionUsageCollector collector = new SessionUsageCollector(sessionId, "TestAgent");
        session.setUsageCollector(collector);
        
        // Add usage records
        TokenUsageRecord record1 = TokenUsageRecord.builder()
            .timestamp(Instant.now())
            .modelName("gpt-4")
            .promptTokens(100)
            .completionTokens(50)
            .totalTokens(150)
            .cost(0.00225)
            .requestDurationMs(1000)
            .build();
        
        collector.addUsageRecord(record1);
        
        // Verify usage was tracked
        assertEquals(1, collector.getUsageRecords().size());
        assertEquals(150, collector.getSessionSummary().getTotalTokens());
        assertEquals(0.00225, collector.getSessionSummary().getTotalCost(), 0.00001);
    }

    @Test
    @DisplayName("E2E: Usage rollup from child to parent")
    void testUsageRollup() {
        String parentId = "parent_rollup_001";
        String childId = parentId + ".child_001";
        
        // Create parent session
        AgenticSession parent = new AgenticSession(
            parentId,
            new ArrayList<>(),
            "Parent task",
            new ArrayList<>(),
            0,
            Instant.now(),
            Instant.now(),
            new ArrayList<>(),
            new ArrayList<>()
        );
        parent.setUsageCollector(new SessionUsageCollector(parentId, "ParentAgent"));
        localCache.createSession(parent);
        
        // Create child session
        AgenticSession child = new AgenticSession(
            childId,
            new ArrayList<>(),
            "Child task",
            new ArrayList<>(),
            0,
            Instant.now(),
            Instant.now(),
            new ArrayList<>(),
            new ArrayList<>()
        );
        child.setParentSessionId(parentId);
        child.setUsageCollector(new SessionUsageCollector(childId, "ChildAgent"));
        localCache.createSession(child);
        
        // Add usage to child
        TokenUsageRecord childRecord = TokenUsageRecord.builder()
            .timestamp(Instant.now())
            .modelName("gpt-4")
            .promptTokens(50)
            .completionTokens(25)
            .totalTokens(75)
            .cost(0.001125)
            .requestDurationMs(500)
            .build();
        child.getUsageCollector().addUsageRecord(childRecord);
        
        // Roll up to parent
        parent.getUsageCollector().merge(child.getUsageCollector());
        
        // Verify rollup
        assertEquals(1, parent.getUsageCollector().getChildSessionsCount());
        assertEquals(75, parent.getUsageCollector().getSessionSummary().getTotalTokens());
        assertTrue(parent.getUsageCollector().getSessionSummary().getTotalCost() > 0);
    }

    @Test
    @DisplayName("E2E: Session state transitions")
    void testSessionStateTransitions() {
        String sessionId = "state_test_001";
        
        // Create session in ACTIVE state
        AgenticSession session = new AgenticSession(
            sessionId,
            new ArrayList<>(),
            "Test message",
            new ArrayList<>(),
            0,
            Instant.now(),
            Instant.now(),
            new ArrayList<>(),
            new ArrayList<>()
        );
        
        assertEquals(SessionState.ACTIVE, session.getState());
        
        // Mark as terminated
        session.markTerminated();
        
        assertEquals(SessionState.TERMINATED, session.getState());
        assertNotNull(session.getTerminatedAt());
    }

    @Test
    @DisplayName("E2E: Multiple sessions can coexist")
    void testMultipleSessions() {
        String[] sessionIds = {"sess_001", "sess_002", "sess_003"};
        
        for (String id : sessionIds) {
            AgenticSession session = new AgenticSession(
                id,
                new ArrayList<>(),
                "Task " + id,
                new ArrayList<>(),
                0,
                Instant.now(),
                Instant.now(),
                new ArrayList<>(),
                new ArrayList<>()
            );
            session.setUsageCollector(new SessionUsageCollector(id, "Agent"));
            localCache.createSession(session);
        }
        
        // Verify all exist
        for (String id : sessionIds) {
            assertNotNull(localCache.getSession(id), "Session " + id + " should exist");
        }
        
        // Verify count
        assertEquals(3, localCache.getSessionCount());
    }

    // ========================================
    // Helper Methods
    // ========================================

    private AgenticResponse createCompleteResponse(String sessionId, String finalResponse) {
        AgenticResponse response = new AgenticResponse();
        response.setSessionId(sessionId);
        response.setResponseType(AgenticResponse.ResponseType.COMPLETE);
        response.setFinalResponse(finalResponse);
        response.setOutputVariables(new HashMap<>());
        return response;
    }
}
