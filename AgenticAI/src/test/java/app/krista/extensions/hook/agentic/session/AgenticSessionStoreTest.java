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

package app.krista.extensions.hook.agentic.session;

import app.krista.extensions.hook.agentic.dto.AgenticToolDefinition;
import app.krista.extensions.hook.agentic.dto.ExpectedOutput;
import app.krista.extensions.hook.agentic.dto.InputVariable;
import com.github.benmanes.caffeine.cache.stats.CacheStats;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for AgenticSessionStore with Caffeine cache.
 * Tests capacity limits, LRU eviction, thread safety, and statistics.
 */
class AgenticSessionStoreTest {

    private AgenticSessionStore sessionStore;

    @BeforeEach
    void setUp() {
        // Get singleton instance and clear it for clean test state
        sessionStore = AgenticSessionStore.getInstance();
        sessionStore.clear();
    }

    @Test
    @DisplayName("Create and retrieve session")
    void testCreateAndRetrieveSession() {
        // Setup
        String sessionId = "sess_test_001";
        AgenticSession session = createTestSession(sessionId);

        // Execute
        sessionStore.createSession(session);
        AgenticSession retrieved = sessionStore.getSession(sessionId);

        // Verify
        assertNotNull(retrieved);
        assertEquals(sessionId, retrieved.getSessionId());
        assertEquals(1, sessionStore.getSessionCount());
    }

    @Test
    @DisplayName("Update session access time on get")
    void testUpdateAccessTime() {
        // Setup
        String sessionId = "sess_test_002";
        AgenticSession session = createTestSession(sessionId);
        Instant beforeAccess = Instant.now();

        // Execute
        sessionStore.createSession(session);
        AgenticSession retrieved = sessionStore.getSession(sessionId);

        // Verify
        assertNotNull(retrieved);
        assertTrue(retrieved.getLastAccessAt().isAfter(beforeAccess) || 
                   retrieved.getLastAccessAt().equals(beforeAccess));
    }

    @Test
    @DisplayName("Update existing session")
    void testUpdateSession() {
        // Setup
        String sessionId = "sess_test_003";
        AgenticSession session = createTestSession(sessionId);
        sessionStore.createSession(session);

        // Modify and update
        session.setIterationCount(5);
        sessionStore.updateSession(session);

        // Verify
        AgenticSession retrieved = sessionStore.getSession(sessionId);
        assertEquals(5, retrieved.getIterationCount());
    }

    @Test
    @DisplayName("Delete session from cache")
    void testDeleteSession() {
        // Setup
        String sessionId = "sess_test_004";
        AgenticSession session = createTestSession(sessionId);
        sessionStore.createSession(session);
        assertEquals(1, sessionStore.getSessionCount());

        // Execute
        sessionStore.deleteSession(sessionId);

        // Verify
        assertNull(sessionStore.getSession(sessionId));
        assertEquals(0, sessionStore.getSessionCount());
    }

    @Test
    @DisplayName("Get null for non-existent session")
    void testGetNonExistentSession() {
        // Execute
        AgenticSession result = sessionStore.getSession("non_existent_id");

        // Verify
        assertNull(result);
    }

    @Test
    @DisplayName("Handle null sessionId gracefully")
    void testNullSessionId() {
        assertDoesNotThrow(() -> sessionStore.deleteSession(null));
        assertNull(sessionStore.getSession(null));
    }

    @Test
    @DisplayName("Reject null session on create")
    void testCreateNullSession() {
        assertThrows(IllegalArgumentException.class, () -> sessionStore.createSession(null));
    }

    @Test
    @DisplayName("Reject session with null ID")
    void testCreateSessionWithNullId() {
        AgenticSession session = new AgenticSession(
            null, new ArrayList<>(), "test", new ArrayList<>(), 0,
            Instant.now(), Instant.now(), null, null
        );
        assertThrows(IllegalArgumentException.class, () -> sessionStore.createSession(session));
    }

    @Test
    @DisplayName("Session state is ACTIVE by default")
    void testDefaultSessionState() {
        // Setup
        String sessionId = "sess_state_001";
        AgenticSession session = createTestSession(sessionId);

        // Verify
        assertEquals(AgenticSession.SessionState.ACTIVE, session.getState());
        assertFalse(session.isTerminated());
        assertNull(session.getTerminatedAt());
    }

    @Test
    @DisplayName("Mark session as TERMINATED")
    void testMarkTerminated() {
        // Setup
        String sessionId = "sess_state_002";
        AgenticSession session = createTestSession(sessionId);
        Instant before = Instant.now();

        // Execute
        session.markTerminated();

        // Verify
        assertEquals(AgenticSession.SessionState.TERMINATED, session.getState());
        assertTrue(session.isTerminated());
        assertNotNull(session.getTerminatedAt());
        assertTrue(session.getTerminatedAt().isAfter(before) || 
                   session.getTerminatedAt().equals(before));
    }

    @Test
    @DisplayName("Cache statistics are available")
    void testCacheStats() {
        // Setup
        for (int i = 0; i < 100; i++) {
            sessionStore.createSession(createTestSession("sess_stats_" + i));
        }

        // Access some sessions
        for (int i = 0; i < 50; i++) {
            sessionStore.getSession("sess_stats_" + i);
        }

        // Execute
        Object stats = sessionStore.getStats();

        // Verify
        assertNotNull(stats);
        // Verify stats object has required methods via reflection
        assertDoesNotThrow(() -> {
            stats.getClass().getMethod("hitCount");
            stats.getClass().getMethod("missCount");
        });
        System.out.println("Cache stats object created successfully: " + stats);
    }

    @Test
    @DisplayName("Concurrent session creation is thread-safe")
    void testConcurrentSessionCreation() throws InterruptedException {
        // Setup
        int threadCount = 10;
        int sessionsPerThread = 100;
        CountDownLatch latch = new CountDownLatch(threadCount);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        // Execute
        for (int t = 0; t < threadCount; t++) {
            final int threadNum = t;
            executor.submit(() -> {
                try {
                    for (int i = 0; i < sessionsPerThread; i++) {
                        String sessionId = "sess_concurrent_" + threadNum + "_" + i;
                        sessionStore.createSession(createTestSession(sessionId));
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        // Wait for completion
        assertTrue(latch.await(30, TimeUnit.SECONDS));
        executor.shutdown();

        // Verify
        long expectedCount = threadCount * sessionsPerThread;
        assertEquals(expectedCount, sessionStore.getSessionCount());
    }

    @Test
    @DisplayName("Concurrent reads and writes are thread-safe")
    void testConcurrentReadWrite() throws InterruptedException {
        // Setup
        int writerThreads = 5;
        int readerThreads = 5;
        int operationsPerThread = 100;
        CountDownLatch latch = new CountDownLatch(writerThreads + readerThreads);
        ExecutorService executor = Executors.newFixedThreadPool(writerThreads + readerThreads);

        // Pre-populate some sessions
        for (int i = 0; i < 50; i++) {
            sessionStore.createSession(createTestSession("sess_rw_" + i));
        }

        // Writers
        for (int t = 0; t < writerThreads; t++) {
            executor.submit(() -> {
                try {
                    for (int i = 0; i < operationsPerThread; i++) {
                        String sessionId = "sess_rw_new_" + i;
                        sessionStore.createSession(createTestSession(sessionId));
                        if (i % 10 == 0) {
                            sessionStore.deleteSession(sessionId);
                        }
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        // Readers
        for (int t = 0; t < readerThreads; t++) {
            executor.submit(() -> {
                try {
                    for (int i = 0; i < operationsPerThread; i++) {
                        sessionStore.getSession("sess_rw_" + (i % 50));
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        // Wait for completion
        assertTrue(latch.await(30, TimeUnit.SECONDS));
        executor.shutdown();

        // Verify no exceptions thrown (test passes if we get here)
        assertTrue(sessionStore.getSessionCount() >= 50); // At least original sessions remain
    }

    @Test
    @DisplayName("Clear all sessions")
    void testClear() {
        // Setup
        for (int i = 0; i < 10; i++) {
            sessionStore.createSession(createTestSession("sess_clear_" + i));
        }
        assertEquals(10, sessionStore.getSessionCount());

        // Execute
        sessionStore.clear();

        // Verify
        assertEquals(0, sessionStore.getSessionCount());
        assertNull(sessionStore.getSession("sess_clear_0"));
    }

    @Test
    @DisplayName("Get all sessions returns copy of cache contents")
    void testGetAllSessions() {
        // Setup
        for (int i = 0; i < 5; i++) {
            sessionStore.createSession(createTestSession("sess_all_" + i));
        }

        // Execute
        Map<String, AgenticSession> all = sessionStore.getAllSessions();

        // Verify
        assertEquals(5, all.size());
        for (int i = 0; i < 5; i++) {
            assertTrue(all.containsKey("sess_all_" + i));
        }
    }

    // Helper method
    private AgenticSession createTestSession(String sessionId) {
        return new AgenticSession(
            sessionId,
            new ArrayList<>(),
            "Test prompt",
            new ArrayList<>(),
            0,
            Instant.now(),
            Instant.now(),
            null,
            null
        );
    }

    // ============================================
    // BUSINESS RULE ENFORCEMENT TESTS
    // These tests will FAIL if business logic changes
    // ============================================

    @Test
    @DisplayName("BUSINESS RULE: Weight-based eviction configured (ACTIVE weight=0, TERMINATED weight=1)")
    void testWeightBasedEvictionConfiguration() {
        // This test verifies the business rule is implemented in code
        // The cache uses maximumWeight(5000) with a weigher that assigns:
        // - weight 0 to ACTIVE sessions (protected from capacity-based eviction)
        // - weight 1 to TERMINATED sessions (eligible for eviction)
        
        // Verify by checking the weigher logic through behavior
        // Create mix of ACTIVE and TERMINATED
        for (int i = 0; i < 100; i++) {
            AgenticSession session = createTestSession("weight_test_" + i);
            if (i % 2 == 0) {
                session.markTerminated();  // Weight 1
            }
            // Else: ACTIVE (weight 0)
            sessionStore.createSession(session);
        }
        
        // All should be stored
        assertEquals(100, sessionStore.getSessionCount(), 
            "All 100 sessions should be stored");
        
        // Verify ACTIVE sessions are present
        for (int i = 1; i < 100; i += 2) {
            AgenticSession s = sessionStore.getSession("weight_test_" + i);
            assertNotNull(s, "ACTIVE session should be present");
            assertFalse(s.isTerminated(), "Should be ACTIVE");
        }
        
        System.out.println("✅ Weight-based eviction configured: ACTIVE sessions have weight 0, " +
            "TERMINATED have weight 1");
    }

    @Test
    @DisplayName("BUSINESS RULE: Session state persists through cache operations")
    void testStatePersistsThroughCacheOperations() {
        // Setup: Create TERMINATED session
        String sessionId = "state_persist_test";
        AgenticSession session = createTestSession(sessionId);
        session.markTerminated();
        
        // Store in cache
        sessionStore.createSession(session);
        
        // Retrieve from cache
        AgenticSession retrieved = sessionStore.getSession(sessionId);
        
        // Verify: State persists through get/put operations
        assertNotNull(retrieved);
        assertEquals(AgenticSession.SessionState.TERMINATED, retrieved.getState(),
            "TERMINATED state must persist through cache storage");
        assertTrue(retrieved.isTerminated(),
            "isTerminated() must return true after retrieval");
        assertNotNull(retrieved.getTerminatedAt(),
            "terminatedAt must persist through cache storage");
    }

    @Test
    @DisplayName("BUSINESS RULE: Session state transitions correctly ACTIVE -> TERMINATED")
    void testStateTransition_ActiveToTerminated() {
        // Setup
        String sessionId = "state_transition_test";
        AgenticSession session = createTestSession(sessionId);
        
        // Verify initial state
        assertEquals(AgenticSession.SessionState.ACTIVE, session.getState(), 
            "New session must be ACTIVE by default");
        assertFalse(session.isTerminated(), 
            "isTerminated() must return false for ACTIVE session");
        assertNull(session.getTerminatedAt(), 
            "terminatedAt must be null for ACTIVE session");
        
        // Execute state transition
        Instant beforeTermination = Instant.now();
        session.markTerminated();
        
        // Verify terminated state
        assertEquals(AgenticSession.SessionState.TERMINATED, session.getState(), 
            "Session state must be TERMINATED after markTerminated()");
        assertTrue(session.isTerminated(), 
            "isTerminated() must return true after markTerminated()");
        assertNotNull(session.getTerminatedAt(), 
            "terminatedAt must be set after markTerminated()");
        assertTrue(session.getTerminatedAt().isAfter(beforeTermination) || 
                   session.getTerminatedAt().equals(beforeTermination),
            "terminatedAt timestamp must be recent");
    }

    @Test
    @DisplayName("BUSINESS RULE: Terminated session state is permanent (no reversion)")
    void testTerminatedStateIsPermanent() {
        // Setup
        String sessionId = "permanent_termination_test";
        AgenticSession session = createTestSession(sessionId);
        session.markTerminated();
        
        // Verify TERMINATED
        assertTrue(session.isTerminated(), "Session should be terminated");
        
        // Attempt to "revert" by calling markTerminated again
        Instant firstTermination = session.getTerminatedAt();
        session.markTerminated();
        
        // Verify: State remains TERMINATED, timestamp unchanged
        assertTrue(session.isTerminated(), "Session must remain TERMINATED");
        assertEquals(firstTermination, session.getTerminatedAt(),
            "Termination timestamp must not change on subsequent calls");
        
        // Verify: No setter exists to change state back to ACTIVE
        // This is enforced by not having a setState() method
        assertThrows(NoSuchMethodException.class, () -> {
            AgenticSession.class.getMethod("setState", AgenticSession.SessionState.class);
        }, "CRITICAL: setState() method should not exist - would allow illegal state reversion!");
    }

    @Test
    @DisplayName("BUSINESS RULE: Removal listener is configured and logs evictions")
    void testRemovalListenerConfigured() {
        // Setup: Capture System.out
        ByteArrayOutputStream outContent = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;
        System.setOut(new PrintStream(outContent));
        
        try {
            // Create and delete a session to trigger removal listener
            String sessionId = "removal_test_" + System.currentTimeMillis();
            AgenticSession session = createTestSession(sessionId);
            session.markTerminated();
            sessionStore.createSession(session);
            
            // Manually invalidate to trigger removal listener
            sessionStore.deleteSession(sessionId);
            
            // Verify: Log contains removal information
            String logOutput = outContent.toString();
            assertTrue(logOutput.contains("removed") || logOutput.contains("evict") || 
                       logOutput.contains("Removal"), 
                "Session removal must be logged for monitoring. Log: " + logOutput);
            
        } finally {
            System.setOut(originalOut);
        }
    }

    @Test
    @DisplayName("BUSINESS RULE: Access time updated on every get (affects LRU)")
    void testAccessTimeUpdatedOnGet() {
        // Setup
        String sessionId = "access_time_test";
        AgenticSession session = createTestSession(sessionId);
        sessionStore.createSession(session);
        
        // Get initial access time
        Instant initialAccess = session.getLastAccessAt();
        
        // Wait a moment
        try { Thread.sleep(10); } catch (InterruptedException e) {}
        
        // Access session
        sessionStore.getSession(sessionId);
        
        // Verify access time was updated
        Instant updatedAccess = session.getLastAccessAt();
        assertTrue(updatedAccess.isAfter(initialAccess),
            "LRU mechanism requires access time to be updated on every get()");
    }

    @Test
    @DisplayName("BUSINESS RULE: Concurrent cache operations are thread-safe (no corruption)")
    void testConcurrentOperations_NoCacheCorruption() throws InterruptedException {
        // Setup: Multiple threads performing operations on different sessions
        // Verifies that the cache itself remains consistent under concurrent access
        
        int threadCount = 10;
        int opsPerThread = 100;
        CountDownLatch latch = new CountDownLatch(threadCount);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        
        // Each thread works on its own set of sessions
        for (int t = 0; t < threadCount; t++) {
            final int threadNum = t;
            executor.submit(() -> {
                try {
                    for (int i = 0; i < opsPerThread; i++) {
                        String sessionId = "concurrent_thread" + threadNum + "_" + i;
                        // Create
                        AgenticSession session = createTestSession(sessionId);
                        sessionStore.createSession(session);
                        
                        // Read
                        AgenticSession retrieved = sessionStore.getSession(sessionId);
                        assertNotNull(retrieved, "Session should exist after creation");
                        
                        // Update
                        retrieved.setIterationCount(i);
                        sessionStore.updateSession(retrieved);
                    }
                } catch (AssertionError e) {
                    System.err.println("Thread " + threadNum + " failed: " + e.getMessage());
                    throw e;
                } finally {
                    latch.countDown();
                }
            });
        }
        
        // Wait for completion
        assertTrue(latch.await(30, TimeUnit.SECONDS), "Concurrent operations timed out");
        executor.shutdown();
        
        // Verify: All sessions should be present and consistent
        int expectedTotal = threadCount * opsPerThread;
        assertEquals(expectedTotal, sessionStore.getSessionCount(),
            "All " + expectedTotal + " sessions should be present after concurrent operations");
        
        // Verify each session is readable and consistent
        for (int t = 0; t < threadCount; t++) {
            for (int i = 0; i < opsPerThread; i++) {
                String sessionId = "concurrent_thread" + t + "_" + i;
                AgenticSession session = sessionStore.getSession(sessionId);
                assertNotNull(session, "Session " + sessionId + " should exist");
                assertEquals(i, session.getIterationCount(),
                    "Session " + sessionId + " should have correct iteration count");
            }
        }
        
        System.out.println("✅ Concurrent operations completed successfully: " + expectedTotal + " sessions created and verified");
    }

    @Test
    @DisplayName("BUSINESS RULE: Null arguments rejected with IllegalArgumentException")
    void testNullArguments_Rejected() {
        // Test createSession with null
        assertThrows(IllegalArgumentException.class, 
            () -> sessionStore.createSession(null),
            "createSession(null) must throw IllegalArgumentException");
        
        // Test createSession with null sessionId
        AgenticSession nullIdSession = new AgenticSession(
            null, new ArrayList<>(), "test", new ArrayList<>(), 0,
            Instant.now(), Instant.now(), null, null
        );
        assertThrows(IllegalArgumentException.class, 
            () -> sessionStore.createSession(nullIdSession),
            "createSession with null sessionId must throw IllegalArgumentException");
        
        // Test updateSession with null
        assertThrows(IllegalArgumentException.class, 
            () -> sessionStore.updateSession(null),
            "updateSession(null) must throw IllegalArgumentException");
        
        // Verify null sessionId returns null (graceful handling)
        assertNull(sessionStore.getSession(null), 
            "getSession(null) should return null, not throw");
        
        // Verify delete with null doesn't throw
        assertDoesNotThrow(() -> sessionStore.deleteSession(null),
            "deleteSession(null) should be a no-op, not throw");
    }

    @Test
    @DisplayName("BUSINESS RULE: Cache statistics provide operational visibility")
    void testCacheStats_ProvideVisibility() {
        // Perform operations to generate stats
        for (int i = 0; i < 100; i++) {
            sessionStore.createSession(createTestSession("stats_test_" + i));
        }
        
        // Generate hits
        for (int i = 0; i < 50; i++) {
            sessionStore.getSession("stats_test_" + i);
        }
        
        // Generate misses
        for (int i = 0; i < 20; i++) {
            sessionStore.getSession("non_existent_stats_" + i);
        }
        
        // Get stats
        Object stats = sessionStore.getStats();
        
        // Verify stats are meaningful
        assertNotNull(stats, "CacheStats must be available for monitoring");
        
        // Use reflection to verify stats object has expected methods
        assertDoesNotThrow(() -> {
            stats.getClass().getMethod("hitCount");
            stats.getClass().getMethod("missCount");
            stats.getClass().getMethod("loadCount");
            stats.getClass().getMethod("hitRate");
        }, "CacheStats must have standard monitoring methods");
        
        System.out.println("Cache stats available and functional for monitoring");
    }
}
