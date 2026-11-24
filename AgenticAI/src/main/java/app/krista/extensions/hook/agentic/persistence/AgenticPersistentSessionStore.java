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

package app.krista.extensions.hook.agentic.persistence;

import app.krista.extensions.hook.agentic.AgenticConstants;
import app.krista.extensions.hook.agentic.security.SecurityLogger;
import app.krista.extensions.hook.agentic.session.AgenticSession;
import app.krista.extensions.hook.agentic.session.AgenticSessionStore;
import app.krista.extensions.hook.util.GsonProvider;
import app.krista.extensions.util.KeyValueStore;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Type;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Persistent session store with single-key architecture and synchronized locking.
 *
 * Architecture:
 * - All sessions stored under ONE key in KeyValueStore as Map<String, AgenticSession>
 * - Local cache (AgenticSessionStore) for fast reads
 * - Synchronized locking for thread-safe write operations (single instance only)
 * - Strict consistency - operations fail if persistence fails
 */
public class AgenticPersistentSessionStore {
    private static final Logger LOGGER = LoggerFactory.getLogger(AgenticPersistentSessionStore.class);
    
    private static final String ALL_SESSIONS_KEY = "agentic:session:v1:all";
    private static final Duration MAX_RETRY_DURATION = Duration.ofSeconds(3);
    private static final int MAX_RETRIES = 5;
    private static final long BASE_RETRY_DELAY_MS = 100;
    
    // Singleton instance holder for global access (e.g., from AgenticChatLanguageModel)
    private static volatile AgenticPersistentSessionStore instance;
    
    // Lock object for synchronized operations (single instance locking)
    private static final Object STORE_LOCK = new Object();
    
    private static final Gson GSON = GsonProvider.getInstance();

    private static final Type SESSION_MAP_TYPE = new TypeToken<Map<String, AgenticSession>>(){}.getType();
    
    private final KeyValueStore keyValueStore;
    private final AgenticSessionStore localCache;
    
    public AgenticPersistentSessionStore(KeyValueStore keyValueStore) {
        this.keyValueStore = keyValueStore;
        this.localCache = AgenticSessionStore.getInstance();
        // Set as global instance for access from other components
        instance = this;
    }
    
    /**
     * Gets the singleton instance of the persistent session store.
     * This allows other components (like AgenticChatLanguageModel) to access
     * the persistent store for session lookups after restart scenarios.
     * 
     * @return The global persistent session store instance, or null if not initialized
     */
    public static AgenticPersistentSessionStore getInstance() {
        return instance;
    }
    
    /**
     * Saves session to both local cache and KeyValueStore with retry logic.
     * Uses synchronized locking for thread-safe updates.
     * This method MUST succeed before proceeding - strict consistency.
     * 
     * @param session The session to save
     * @return PersistenceResult indicating success or detailed failure
     */
    public PersistenceResult saveSession(AgenticSession session) {
        if (session == null) {
            throw new IllegalArgumentException("Session cannot be null");
        }
        
        // Note: Local cache is updated by AgenticFlowService BEFORE calling this method
        // This method ONLY handles persistence to KeyValueStore (durability layer)
        // Flow Service -> Cache (immediate) -> Persistence (async background)
        
        // Persist to KeyValueStore with synchronized locking
        PersistenceResult result = persistWithRetry(session);
        
        if (!result.isSuccess()) {
            // Log detailed error internally (not exposed to user)
            String errorMsg = String.format(
                "PERSISTENCE_FAILURE: Session %s failed after %d retries. Duration: %dms. Last error: %s",
                session.getSessionId(),
                result.getAttemptCount(),
                result.getDurationMs(),
                result.getLastError() != null ? result.getLastError().getMessage() : "Unknown"
            );

            LOGGER.error(errorMsg);
            SecurityLogger.logGuardrailViolation(
                session.getSessionId(),
                "PERSISTENCE_FAILURE",
                "HIGH",
                "BLOCKED",
                errorMsg,
                "AgenticPersistentSessionStore",
                "Session persistence failed after max retries"
            );
        }
        
        return result;
    }
    
    /**
     * Persists session with exponential backoff retry.
     * Uses synchronized locking for thread-safe access to shared KeyValueStore.
     * Blocks until success or max retry duration reached.
     */
    private PersistenceResult persistWithRetry(AgenticSession session) {
        Instant start = Instant.now();
        Exception lastError = null;
        int attempts = 0;
        long totalDelay = 0;
        
        for (int i = 0; i < MAX_RETRIES; i++) {
            attempts++;
            
            try {
                // Synchronized block for thread-safe access
                synchronized (STORE_LOCK) {
                    // Load current state
                    Map<String, AgenticSession> allSessions = loadAllSessions();
                    
                    // Update this session
                    allSessions.put(session.getSessionId(), session);
                    
                    // Serialize and save entire map
                    String json = GSON.toJson(allSessions);
                    keyValueStore.put(ALL_SESSIONS_KEY, json);
                }
                
                // Success!
                return PersistenceResult.success(
                    Duration.between(start, Instant.now()).toMillis(),
                    attempts
                );
                
            } catch (Exception e) {
                lastError = e;
                
                // Calculate delay for next retry (exponential backoff)
                long delayMs = BASE_RETRY_DELAY_MS * (1L << i); // 100, 200, 400, 800, 1600
                totalDelay += delayMs;
                
                // Check if we've exceeded max retry duration
                if (Duration.between(start, Instant.now()).plusMillis(delayMs).compareTo(MAX_RETRY_DURATION) > 0) {
                    LOGGER.warn("PERSISTENCE_RETRY_TIMEOUT: Giving up after {} attempts. Total time would exceed {}s",
                        attempts, MAX_RETRY_DURATION.getSeconds());
                    break;
                }

                // Log retry attempt (debug level)
                LOGGER.debug("PERSISTENCE_RETRY: Attempt {}/{} failed for session {}. Retrying in {}ms. Error: {}",
                    (i + 1), MAX_RETRIES, session.getSessionId(), delayMs, e.getMessage());
                
                // Wait before retry
                try {
                    Thread.sleep(delayMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return PersistenceResult.failure(
                        Duration.between(start, Instant.now()).toMillis(),
                        attempts,
                        new Exception("Retry interrupted", ie)
                    );
                }
            }
        }
        
        // All retries exhausted
        return PersistenceResult.failure(
            Duration.between(start, Instant.now()).toMillis(),
            attempts,
            lastError != null ? lastError : new Exception("Unknown persistence failure")
        );
    }
    
    /**
     * Loads all sessions from KeyValueStore.
     * Thread-safe read operation.
     * 
     * Note: KeyValueStore auto-deserializes JSON into Map, so we retrieve as Map.class
     * then convert to JSON string for proper type deserialization with Gson.
     */
    @SuppressWarnings("unchecked")
    private Map<String, AgenticSession> loadAllSessions() {
        try {
            // KeyValueStore returns auto-deserialized Map, not raw JSON string
            Object sessionsData = keyValueStore.get(ALL_SESSIONS_KEY, Map.class);
            if (sessionsData == null) {
                return new ConcurrentHashMap<>();
            }
            
            // Convert to JSON string first, then deserialize with proper type information
            String json = GSON.toJson(sessionsData);
            if (json == null || json.isEmpty()) {
                return new ConcurrentHashMap<>();
            }
            
            Map<String, AgenticSession> sessions = GSON.fromJson(json, SESSION_MAP_TYPE);
            return sessions != null ? sessions : new ConcurrentHashMap<>();
        } catch (Exception e) {
            LOGGER.error("Failed to load sessions from KeyValueStore: {}", e.getMessage(), e);
            return new ConcurrentHashMap<>();
        }
    }
    
    /**
     * Retrieves session from local cache or KeyValueStore.
     * First checks local cache, then falls back to KeyValueStore.
     * Tracks cache miss metrics for restart scenario monitoring.
     */
    public AgenticSession getSession(String sessionId) {
        // 1. Try local cache first (fast)
        AgenticSession session = localCache.getSession(sessionId);
        if (session != null) {
            return session;  // Cache hit - fast path
        }
        
        // 2. Cache miss - load from KeyValueStore
        // This typically happens after extension restart
        synchronized (STORE_LOCK) {
            try {
                Map<String, AgenticSession> allSessions = loadAllSessions();
                session = allSessions.get(sessionId);
                
                if (session != null) {
                    // Populate local cache for future fast access
                    localCache.createSession(session);

                    // Log cache miss for monitoring (important for tracking restart scenarios)
                    LOGGER.info("CACHE_MISS_RESTORED: Session {} not found in local cache but restored from KeyValueStore. " +
                        "This typically indicates an extension restart occurred. " +
                        "Session repopulated in local cache for subsequent fast access.", sessionId);

                    return session;
                }
            } catch (Exception e) {
                LOGGER.error("CACHE_MISS_FAILED: Failed to restore session {} from KeyValueStore after cache miss. Error: {}",
                    sessionId, e.getMessage(), e);
            }
        }
        
        return null;
    }
    
    /**
     * Deletes a session from both local cache and KeyValueStore.
     * Uses synchronized locking for thread-safe updates.
     */
    public void deleteSession(String sessionId) {
        // Delete from local cache
        localCache.deleteSession(sessionId);
        
        // Delete from KeyValueStore with synchronized locking
        synchronized (STORE_LOCK) {
            try {
                Map<String, AgenticSession> allSessions = loadAllSessions();
                allSessions.remove(sessionId);
                
                String json = GSON.toJson(allSessions);
                keyValueStore.put(ALL_SESSIONS_KEY, json);
            } catch (Exception e) {
                LOGGER.error("Failed to delete session from KeyValueStore: {}. Error: {}", sessionId, e.getMessage(), e);
            }
        }
    }
    
    /**
     * Deletes a session group (parent + all children) from KeyValueStore.
     * Called when parent session terminates (COMPLETE or ERROR).
     * Children are identified by session IDs starting with "parentId.child_".
     * 
     * @param parentSessionId The parent session ID whose group should be deleted
     */
    public void deleteSessionGroup(String parentSessionId) {
        if (parentSessionId == null) {
            return;
        }
        
        synchronized (STORE_LOCK) {
            try {
                Map<String, AgenticSession> allSessions = loadAllSessions();
                int deletedCount = 0;
                
                // Remove parent
                if (allSessions.remove(parentSessionId) != null) {
                    deletedCount++;
                    LOGGER.info("PERSISTENCE_DELETE: Removed parent session {}", parentSessionId);
                }
                
                // Remove all children (session IDs starting with "parentId.")
                // Child session format: "{parentId}.child_{uuid}"
                java.util.List<String> childrenToRemove = allSessions.keySet().stream()
                    .filter(key -> key.startsWith(parentSessionId + AgenticConstants.SESSION_CHILD_PREFIX))
                    .toList();
                
                for (String childId : childrenToRemove) {
                    allSessions.remove(childId);
                    deletedCount++;
                }
                
                if (deletedCount > 0) {
                    // Save updated map back to KeyValueStore
                    String json = GSON.toJson(allSessions);
                    keyValueStore.put(ALL_SESSIONS_KEY, json);
                    LOGGER.info("PERSISTENCE_DELETE: Deleted {} sessions (parent + {} children) for group {}",
                        deletedCount, (deletedCount - 1), parentSessionId);
                }
            } catch (Exception e) {
                // Log error but don't throw - cleanup failure shouldn't block the flow
                LOGGER.error("PERSISTENCE_DELETE_ERROR: Failed to delete session group {}: {}",
                    parentSessionId, e.getMessage(), e);
            }
        }
    }
    
    /**
     * Checks if a session exists in either local cache or KeyValueStore.
     */
    public boolean hasSession(String sessionId) {
        // Check local cache first
        if (localCache.getSession(sessionId) != null) {
            return true;
        }
        
        // Check KeyValueStore
        synchronized (STORE_LOCK) {
            try {
                Map<String, AgenticSession> allSessions = loadAllSessions();
                return allSessions.containsKey(sessionId);
            } catch (Exception e) {
                return false;
            }
        }
    }
    
    /**
     * Asynchronously saves session to KeyValueStore using Java 21 Virtual Threads.
     * Virtual threads are lightweight and efficient for I/O-bound operations like persistence.
     * Returns immediately, executes in background virtual thread.
     * This allows parallel execution with LLM calls for better performance.
     *
     * @param session The session to save
     * @return CompletableFuture that will complete with the persistence result
     */
    public java.util.concurrent.CompletableFuture<PersistenceResult> saveSessionAsync(AgenticSession session) {

        java.util.concurrent.CompletableFuture<PersistenceResult> future = new java.util.concurrent.CompletableFuture<>();

        // Use Java 21 Virtual Threads for lightweight async I/O. Persistence is
        // observability-internal — no SigNoz span is emitted; success/failure is surfaced
        // via the returned PersistenceResult future.
        Thread.ofVirtual().start(() -> {

            try {

                PersistenceResult result = saveSession(session);
                future.complete(result);

            } catch (Exception e) {

                future.completeExceptionally(e);
            }
        });

        return future;
    }
    
    /**
     * Result of persistence operation.
     */
    public static class PersistenceResult {
        private final boolean success;
        private final long durationMs;
        private final int attemptCount;
        private final Exception lastError;
        
        private PersistenceResult(boolean success, long durationMs, int attemptCount, Exception lastError) {
            this.success = success;
            this.durationMs = durationMs;
            this.attemptCount = attemptCount;
            this.lastError = lastError;
        }
        
        public static PersistenceResult success(long durationMs, int attemptCount) {
            return new PersistenceResult(true, durationMs, attemptCount, null);
        }
        
        public static PersistenceResult failure(long durationMs, int attemptCount, Exception lastError) {
            return new PersistenceResult(false, durationMs, attemptCount, lastError);
        }
        
        public boolean isSuccess() { return success; }
        public long getDurationMs() { return durationMs; }
        public int getAttemptCount() { return attemptCount; }
        public Exception getLastError() { return lastError; }
    }
}
