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

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.RemovalCause;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.concurrent.TimeUnit;

/**
 * In-memory session store using Caffeine cache with capacity-based eviction.
 *
 * Architecture:
 * - Maximum 5,000 sessions in memory (configurable)
 * - LRU eviction: Least recently accessed sessions evicted first
 * - Only TERMINATED sessions are eligible for eviction
 * - ACTIVE sessions are protected from eviction
 * - Thread-safe with high-performance concurrent access
 *
 * Note: This is a singleton. All OrchestrationHandler instances share the same cache.
 */
public class AgenticSessionStore {
    private static final Logger LOGGER = LoggerFactory.getLogger(AgenticSessionStore.class);
    private static final int MAX_SESSIONS = 5000;

    private static volatile AgenticSessionStore instance;
    private final Cache<String, AgenticSession> sessions;

    private AgenticSessionStore() {
        // Use weighted eviction: TERMINATED sessions count toward max, ACTIVE sessions don't
        // This ensures ACTIVE sessions are never evicted due to capacity pressure
        this.sessions = Caffeine.newBuilder()
            .maximumWeight(MAX_SESSIONS)
            .weigher((String key, AgenticSession session) -> {
                // TERMINATED sessions have weight 1 (eligible for eviction)
                // ACTIVE sessions have weight 0 (protected from capacity-based eviction)
                return session.isTerminated() ? 1 : 0;
            })
            .recordStats()
            .removalListener((String key, AgenticSession session, RemovalCause cause) -> {
                if (session != null) {
                    LOGGER.debug("CACHE_EVICTION: Session {} evicted (cause: {}, state: {})",
                        key, cause, session.getState());
                }
            })
            .build();
        LOGGER.info("AgenticSessionStore initialized with Caffeine cache (max: {})", MAX_SESSIONS);
    }

    /**
     * Gets the singleton instance of AgenticSessionStore.
     * Thread-safe using double-checked locking.
     */
    public static AgenticSessionStore getInstance() {
        if (instance == null) {
            synchronized (AgenticSessionStore.class) {
                if (instance == null) {
                    instance = new AgenticSessionStore();
                }
            }
        }
        return instance;
    }

    /**
     * Creates a new session in the cache.
     * If cache is full, LRU eviction removes the least recently accessed TERMINATED session.
     * ACTIVE sessions are never evicted due to Caffeine's weight-based eviction policy.
     */
    public void createSession(AgenticSession session) {
        if (session == null || session.getSessionId() == null) {
            throw new IllegalArgumentException("Session and sessionId cannot be null");
        }
        sessions.put(session.getSessionId(), session);
        LOGGER.debug("Session cached: {} (cache size: {})",
            session.getSessionId(), sessions.estimatedSize());
    }

    /**
     * Retrieves a session by ID and updates its access time.
     * Also updates the session's lastAccessAt field.
     */
    public AgenticSession getSession(String sessionId) {
        if (sessionId == null) {
            return null;
        }
        AgenticSession session = sessions.getIfPresent(sessionId);
        if (session != null) {
            session.setLastAccessAt(Instant.now());
        }
        return session;
    }

    /**
     * Updates an existing session in the cache.
     */
    public void updateSession(AgenticSession session) {
        if (session == null || session.getSessionId() == null) {
            throw new IllegalArgumentException("Session and sessionId cannot be null");
        }
        sessions.put(session.getSessionId(), session);
    }

    /**
     * Deletes a session from the cache.
     */
    public void deleteSession(String sessionId) {
        if (sessionId == null) {
            return;
        }
        sessions.invalidate(sessionId);
        LOGGER.debug("Session removed from cache: {}", sessionId);
    }

    /**
     * Returns the current number of cached sessions.
     */
    public long getSessionCount() {
        return sessions.estimatedSize();
    }

    /**
     * Gets all cached sessions.
     * Note: Returns a copy of the cache contents. Use with caution on large caches.
     */
    public java.util.Map<String, AgenticSession> getAllSessions() {
        return sessions.asMap();
    }

    /**
     * Gets cache statistics for monitoring.
     * Includes hit rate, load time, eviction count, etc.
     */
    public com.github.benmanes.caffeine.cache.stats.CacheStats getStats() {
        return sessions.stats();
    }

    /**
     * Logs cache performance metrics.
     * Call this periodically to monitor cache health.
     */
    public void logCacheMetrics() {
        com.github.benmanes.caffeine.cache.stats.CacheStats stats = sessions.stats();
        long size = sessions.estimatedSize();

        LOGGER.info("Cache Metrics - Size: {}/{}, Hit Rate: {:.2f}%, Hits: {}, Misses: {}, Evictions: {}, Load Success: {}, Load Failure: {}",
            size,
            MAX_SESSIONS,
            stats.hitRate() * 100,
            stats.hitCount(),
            stats.missCount(),
            stats.evictionCount(),
            stats.loadSuccessCount(),
            stats.loadFailureCount()
        );
    }

    /**
     * Clears all sessions from the cache.
     * Use with caution - primarily for testing.
     */
    public void clear() {
        sessions.invalidateAll();
        LOGGER.info("Cache cleared");
    }
}
