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

import app.krista.extensions.hook.agentic.session.AgenticSession;
import app.krista.extensions.hook.util.GsonProvider;
import app.krista.extensions.util.KeyValueStore;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Background cleanup job that removes stale sessions from KeyValueStore.
 *
 * Runs daily to delete sessions that have been TERMINATED for more than 60 days.
 * This handles edge cases where parent sessions didn't complete properly (crashes, etc.)
 * and prevents indefinite storage growth.
 *
 * Configuration:
 * - Schedule: Daily at 2:00 AM (configurable)
 * - Retention: 60 days after termination
 * - Scope: All TERMINATED sessions older than retention period
 */
public class PersistentSessionCleanupJob {

    private static final Logger LOGGER = LoggerFactory.getLogger(PersistentSessionCleanupJob.class);

    private static final String ALL_SESSIONS_KEY = "agentic:session:v1:all";
    private static final Duration RETENTION_PERIOD = Duration.ofDays(60);
    private static final long CLEANUP_INTERVAL_HOURS = 24; // Run daily
    private static final int CLEANUP_HOUR_OF_DAY = 2; // 2:00 AM

    private final KeyValueStore keyValueStore;
    private final ScheduledExecutorService scheduler;
    private boolean started = false;

    public PersistentSessionCleanupJob(KeyValueStore keyValueStore) {
        this.keyValueStore = keyValueStore;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "StaleSessionCleanup");
            thread.setDaemon(true);
            return thread;
        });
    }

    /**
     * Starts the cleanup job scheduler.
     * Runs daily at 2:00 AM to clean up stale sessions.
     */
    public synchronized void start() {
        if (started) {
            LOGGER.info("PersistentSessionCleanupJob already started");
            return;
        }

        // Calculate initial delay to next 2:00 AM
        long initialDelay = calculateInitialDelay();

        scheduler.scheduleAtFixedRate(
            this::cleanupStaleSessions,
            initialDelay,
            CLEANUP_INTERVAL_HOURS,
            TimeUnit.HOURS
        );

        started = true;
        LOGGER.info("PersistentSessionCleanupJob started (next run in {} hours, interval: {} hours, retention: {} days)",
            initialDelay, CLEANUP_INTERVAL_HOURS, RETENTION_PERIOD.toDays());
    }

    /**
     * Stops the cleanup job.
     */
    public synchronized void stop() {
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(60, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
                LOGGER.info("PersistentSessionCleanupJob stopped");
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
            started = false;
        }
    }

    /**
     * Performs the actual cleanup of stale sessions.
     * Finds all TERMINATED sessions older than 60 days and removes them.
     */
    private void cleanupStaleSessions() {
        LOGGER.info("[STALE_CLEANUP] Starting daily cleanup of sessions older than {} days",
            RETENTION_PERIOD.toDays());

        try {
            // Load all sessions from KeyValueStore
            Object sessionsData = keyValueStore.get(ALL_SESSIONS_KEY, Map.class);
            if (sessionsData == null) {
                LOGGER.info("[STALE_CLEANUP] No sessions found in KeyValueStore");
                return;
            }

            // Convert to typed map using shared Gson with InstantTypeAdapter
            Gson gson = GsonProvider.getInstance();
            String json = gson.toJson(sessionsData);
            java.lang.reflect.Type type = new TypeToken<Map<String, AgenticSession>>(){}.getType();
            Map<String, AgenticSession> allSessions = gson.fromJson(json, type);

            if (allSessions == null || allSessions.isEmpty()) {
                LOGGER.info("[STALE_CLEANUP] No sessions to clean up");
                return;
            }

            Instant cutoffTime = Instant.now().minus(RETENTION_PERIOD);

            // Find stale sessions (terminated more than 60 days ago)
            java.util.List<String> staleSessions = allSessions.entrySet().stream()
                .filter(entry -> {
                    AgenticSession session = entry.getValue();
                    if (session == null) return false;

                    // Check if terminated
                    if (!session.isTerminated()) return false;

                    // Check if termination time is older than cutoff
                    Instant terminatedAt = session.getTerminatedAt();
                    if (terminatedAt == null) return false;

                    return terminatedAt.isBefore(cutoffTime);
                })
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

            if (staleSessions.isEmpty()) {
                LOGGER.info("[STALE_CLEANUP] No stale sessions found (all within retention period)");
                return;
            }

            // Remove stale sessions
            int removedCount = 0;
            for (String sessionId : staleSessions) {
                AgenticSession removed = allSessions.remove(sessionId);
                if (removed != null) {
                    removedCount++;
                    LOGGER.info("[STALE_CLEANUP] Removed stale session: {} (terminated: {})",
                        sessionId, removed.getTerminatedAt());
                }
            }

            // Save updated sessions back to KeyValueStore
            if (removedCount > 0) {
                String updatedJson = GsonProvider.getInstance().toJson(allSessions);
                keyValueStore.put(ALL_SESSIONS_KEY, updatedJson);
                LOGGER.info("[STALE_CLEANUP] Cleanup complete: removed {} stale sessions, {} remaining",
                    removedCount, allSessions.size());
            }

        } catch (Exception e) {
            LOGGER.error("[STALE_CLEANUP_ERROR] Failed to cleanup stale sessions", e);
        }
    }

    /**
     * Calculates the initial delay to the next scheduled cleanup time (2:00 AM).
     */
    private long calculateInitialDelay() {
        java.time.ZonedDateTime now = java.time.ZonedDateTime.now();
        java.time.ZonedDateTime nextRun = now.withHour(CLEANUP_HOUR_OF_DAY).withMinute(0).withSecond(0);

        if (now.compareTo(nextRun) >= 0) {
            nextRun = nextRun.plusDays(1);
        }

        return java.time.Duration.between(now, nextRun).toHours();
    }
}
