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
import app.krista.extensions.hook.agentic.persistence.AgenticPersistentSessionStore;
import app.krista.extensions.hook.agentic.planner.ToolPlanner;
import app.krista.extensions.hook.agentic.session.AgenticSession;
import app.krista.extensions.hook.agentic.session.AgenticSessionStore;
import app.krista.extensions.util.KeyValueStore;
import dev.langchain4j.memory.ChatMemory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Regression tests for session lifecycle fixes (KR-20447).
 *
 * These tests verify that the identified loopholes have been properly fixed
 * and guard against regressions.
 *
 * Loophole A (FIXED): deleteSession() now removes from both Caffeine cache AND KeyValueStore.
 * Loophole B: NOT a production bug -- KristaGrand controls request sequencing and cannot
 *             send concurrent requests for the same parent session. Tests removed.
 * Loophole C: BY DESIGN -- child complete returns parentSessionId intentionally.
 * Loophole D: NOT a production bug -- waitForPersistenceWithRetry() blocks until persistence
 *             confirms, and the parent cannot complete until the child result returns. Tests removed.
 */
class SessionLifecycleLoopholeTest {

    private AgenticFlowService agenticFlowService;
    private AgenticSessionStore sessionStore;
    private AgenticPersistentSessionStore persistentStore;
    private ToolPlanner toolPlanner;
    private Map<Object, ChatMemory> chatMemoryMap;
    private TestKeyValueStore testKeyValueStore;

    // ========================================
    // Test KeyValueStore Implementation
    // ========================================

    /**
     * In-memory KeyValueStore that tracks all operations for verification.
     * Provides controllable delays for concurrency testing.
     */
    private static class TestKeyValueStore implements KeyValueStore {

        private final Map<String, String> store = new ConcurrentHashMap<>();
        private final com.google.gson.Gson gson = new com.google.gson.Gson();
        final List<String> operationsLog = new ArrayList<>();

        @Override
        public void put(String key, Object value) {

            synchronized (operationsLog) {

                operationsLog.add("PUT:" + key);
            }
            if (value instanceof String s) {

                store.put(key, s);
            } else {

                store.put(key, gson.toJson(value));
            }
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T get(String key, Class<T> clazz) {

            synchronized (operationsLog) {

                operationsLog.add("GET:" + key);
            }
            String json = store.get(key);
            if (json == null) return null;
            if (clazz == Map.class) {

                return (T) gson.fromJson(json, Map.class);
            }
            return (T) json;
        }

        @Override
        public String get(String key) {

            return store.get(key);
        }

        @Override
        public void remove(String key) {

            synchronized (operationsLog) {

                operationsLog.add("REMOVE:" + key);
            }
            store.remove(key);
        }

        @Override
        public boolean contains(String key) {

            return store.containsKey(key);
        }

        @Override
        public boolean supports(Class<?> clazz) {

            return true;
        }

        @Override
        public Iterator<Map.Entry<String, Object>> iterator() {

            Map<String, Object> temp = new HashMap<>();
            temp.putAll(store);
            return temp.entrySet().iterator();
        }

        @Override
        public Set<String> getKeys() {

            return store.keySet();
        }

        public void clear() {

            store.clear();
            synchronized (operationsLog) {

                operationsLog.clear();
            }
        }

        /**
         * Returns the raw JSON stored under the all-sessions key,
         * so tests can inspect what is actually persisted in KV.
         */
        public String getRawSessionsJson() {

            return store.get("agentic:session:v1:all");
        }
    }

    // ========================================
    // Setup
    // ========================================

    @BeforeEach
    void setUp() {

        testKeyValueStore = new TestKeyValueStore();
        sessionStore = AgenticSessionStore.getInstance();
        sessionStore.clear();

        toolPlanner = mock(ToolPlanner.class);
        chatMemoryMap = new ConcurrentHashMap<>();

        // Create service with REAL stores (persistent store backed by test KV)
        agenticFlowService = new AgenticFlowService(sessionStore, testKeyValueStore, toolPlanner, chatMemoryMap);
        persistentStore = AgenticPersistentSessionStore.getInstance();
    }

    // ========================================
    // Loophole A (FIXED): deleteSession() now removes from both cache and KeyValueStore
    // ========================================

    @Test
    @DisplayName("Loophole A fix: deleteSession() removes from both Caffeine cache and KeyValueStore")
    void deleteSession_removesFromBothCacheAndKeyValueStore() {

        // SETUP: Create a session through the normal flow so it exists in both cache AND KV store.
        // We use startFlow which persists to KV via saveSessionAsync, then mock the LLM to return
        // EXECUTE_TOOL so the session stays alive (not cleaned up by processResponse).
        String sessionId = "loophole_a_ghost_session";

        AgenticResponse toolResponse = new AgenticResponse();
        toolResponse.setSessionId(sessionId);
        toolResponse.setResponseType(AgenticResponse.ResponseType.EXECUTE_TOOL);
        toolResponse.setCommand(new AgenticCommand("test_tool", new HashMap<>()));

        when(toolPlanner.planNextStep(any(), any(), any(), any(), any(), any(), any()))
            .thenReturn(toolResponse);

        AgenticStartRequest request = new AgenticStartRequest(
            Arrays.asList(new AgenticToolDefinition("test_tool", "Test", "test", new ArrayList<>(), false)),
            "Test task for loophole A",
            new HashMap<>(),
            null, null, sessionId
        );

        agenticFlowService.startFlow(request);

        // PRECONDITION: Session must exist in both cache and KV after startFlow.
        assertNotNull(sessionStore.getSession(sessionId),
            "Precondition: session must exist in Caffeine cache");
        // Clear cache and check KV directly to confirm persistence happened
        sessionStore.clear();
        AgenticSession fromKv = persistentStore.getSession(sessionId);
        assertNotNull(fromKv,
            "Precondition: session must exist in KeyValueStore after startFlow");

        // Restore session back into cache (getSession populates cache from KV)
        // so deleteSession has something to remove from cache.
        assertNotNull(agenticFlowService.getSession(sessionId),
            "Precondition: session should be retrievable after KV restore");

        // ACT: Call deleteSession() -- this is the method called by OrchestrationHandler
        // error handlers (ExecutionLimitExceededException, catch-all Exception).
        agenticFlowService.deleteSession(sessionId);

        // ASSERT: Session is gone from Caffeine cache...
        assertNull(sessionStore.getSession(sessionId),
            "Session should be removed from Caffeine cache");

        // ...AND the session is also gone from KeyValueStore (fix verified).
        // Clear cache so getSession falls through to KV lookup.
        sessionStore.clear();
        AgenticSession ghost = persistentStore.getSession(sessionId);

        assertNull(ghost,
            "FIX VERIFIED (Loophole A): deleteSession() now removes from KeyValueStore too. " +
            "The session should be fully cleaned up from both cache and persistent store.");
    }

    @Test
    @DisplayName("Loophole A fix: deleted session does not resurface after simulated restart (cache clear)")
    void deleteSession_sessionDoesNotResurfaceAfterRestart() {

        // SETUP: Create a session, then delete it via the now-fixed deleteSession path.
        String sessionId = "loophole_a_restart_ghost";

        AgenticResponse toolResponse = new AgenticResponse();
        toolResponse.setSessionId(sessionId);
        toolResponse.setResponseType(AgenticResponse.ResponseType.EXECUTE_TOOL);
        toolResponse.setCommand(new AgenticCommand("test_tool", new HashMap<>()));

        when(toolPlanner.planNextStep(any(), any(), any(), any(), any(), any(), any()))
            .thenReturn(toolResponse);

        AgenticStartRequest request = new AgenticStartRequest(
            Arrays.asList(new AgenticToolDefinition("test_tool", "Test", "test", new ArrayList<>(), false)),
            "Test task for restart ghost",
            new HashMap<>(),
            null, null, sessionId
        );

        agenticFlowService.startFlow(request);

        // Delete via the fixed public method (OrchestrationHandler error path)
        agenticFlowService.deleteSession(sessionId);

        // Confirm session is gone from cache
        assertNull(sessionStore.getSession(sessionId),
            "Precondition: session should be gone from cache after deleteSession");

        // SIMULATE RESTART: Clear the entire Caffeine cache (mimics extension restart).
        // After restart, the persistent store is the source of truth.
        sessionStore.clear();

        // ACT: Try to get the session again -- this simulates what happens when
        // KristaGrand sends a continuation request after a restart.
        AgenticSession resurrected = agenticFlowService.getSession(sessionId);

        // ASSERT: The session does NOT come back -- the fix ensures KV is also cleaned.
        assertNull(resurrected,
            "FIX VERIFIED (Loophole A): Deleted session does not resurface after restart. " +
            "deleteSession() now clears both cache and KV, so no ghost can be restored.");
    }

    // ========================================
    // Loophole B: NOT a production bug
    // ========================================

    // Loophole B (concurrent parent cleanup vs child creation) was determined NOT to be
    // a production bug. KristaGrand controls request sequencing and cannot send concurrent
    // requests for the same parent session. Tests removed per CTO review.

    // ========================================
    // Loophole C: Child complete returns parentSessionId
    // ========================================

    // Loophole C is BY DESIGN, not a bug.
    //
    // When a child session completes, AgenticResponseConverter.handleComplete() returns
    // createChildCompleteResponse(parentSessionId, ...). This is intentional behavior:
    // it tells KristaGrand to continue the PARENT session with the child's results.
    //
    // The parentSessionId in the response is the routing mechanism for the platform
    // to know which parent session should receive the child's completion output.
    // Without this, KristaGrand would have no way to resume the parent flow.
    //
    // No test written -- this is correct architectural behavior.

    // ========================================
    // Loophole D: NOT a production bug
    // ========================================

    // Loophole D (deleteSessionGroup races with child saveSessionAsync) was determined
    // NOT to be a production bug. waitForPersistenceWithRetry() blocks until persistence
    // confirms, and the parent cannot complete until the child result returns.
    // Tests removed per CTO review.

}
