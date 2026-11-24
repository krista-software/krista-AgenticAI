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

import com.github.benmanes.caffeine.cache.stats.CacheStats;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Scheduler that periodically logs cache statistics for monitoring.
 * Note: Caffeine cache handles eviction automatically. This scheduler only provides visibility.
 */
public class CacheMetricsLogger {

    private static final Logger LOGGER = LoggerFactory.getLogger(CacheMetricsLogger.class);

    private static final long STATS_INTERVAL_MINUTES = 5;
    private static CacheMetricsLogger instance;

    private ScheduledExecutorService scheduler;
    private boolean started = false;

    private CacheMetricsLogger() {
    }

    public static synchronized CacheMetricsLogger getInstance() {
        if (instance == null) {
            instance = new CacheMetricsLogger();
        }
        return instance;
    }

    /**
     * Starts the statistics logging scheduler.
     * Caffeine handles eviction automatically; this just provides monitoring.
     */
    public synchronized void start() {
        if (started) {
            LOGGER.info("CacheMetricsLogger already started");
            return;
        }

        AgenticSessionStore sessionStore = AgenticSessionStore.getInstance();

        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "AgenticSessionStats");
            thread.setDaemon(true);
            return thread;
        });

        scheduler.scheduleAtFixedRate(
            () -> {
                try {
                    CacheStats stats = sessionStore.getStats();
                    long size = sessionStore.getSessionCount();
                    LOGGER.info("CACHE_STATS: size={}, hitRate={}%, hitCount={}, missCount={}, evictionCount={}, loadCount={}",
                        size,
                        String.format("%.2f", stats.hitRate() * 100),
                        stats.hitCount(),
                        stats.missCount(),
                        stats.evictionCount(),
                        stats.loadCount());
                } catch (Exception e) {
                    LOGGER.error("Error logging cache stats", e);
                }
            },
            STATS_INTERVAL_MINUTES,
            STATS_INTERVAL_MINUTES,
            TimeUnit.MINUTES
        );

        started = true;
        LOGGER.info("CacheMetricsLogger started (monitoring interval: {} minutes)", STATS_INTERVAL_MINUTES);
    }

    /**
     * Stops the scheduler.
     */
    public synchronized void stop() {
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(10, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
                LOGGER.info("CacheMetricsLogger stopped");
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
            started = false;
        }
    }
}
