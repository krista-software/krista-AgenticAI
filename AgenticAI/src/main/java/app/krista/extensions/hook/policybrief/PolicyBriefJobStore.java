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

package app.krista.extensions.hook.policybrief;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.RemovalCause;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

/**
 * Singleton in-memory store for {@link PolicyBriefJob} instances, backed by a Caffeine cache.
 *
 * <p>Jobs expire after 30 minutes of inactivity and the cache holds at most 1,000 entries.
 * Thread-safe with high-performance concurrent access provided by Caffeine.</p>
 *
 * <p>Uses double-checked locking for lazy singleton initialization.</p>
 */
public class PolicyBriefJobStore {

    private static final Logger LOGGER = LoggerFactory.getLogger(PolicyBriefJobStore.class);

    private static final int MAX_JOBS = 1000;
    private static final long EXPIRE_AFTER_ACCESS_MINUTES = 30;

    private static volatile PolicyBriefJobStore instance;

    private final Cache<String, PolicyBriefJob> cache;

    private PolicyBriefJobStore() {

        this.cache = Caffeine.newBuilder()
                .maximumSize(MAX_JOBS)
                .expireAfterAccess(EXPIRE_AFTER_ACCESS_MINUTES, TimeUnit.MINUTES)
                .removalListener((String key, PolicyBriefJob job, RemovalCause cause) -> {

                    if (job != null) {

                        LOGGER.debug("PolicyBriefJob evicted: jobId={}, cause={}, status={}",
                                key, cause, job.getStatus());
                        PolicyBriefGeneratorFactory.cleanupMemory(key);
                    }
                })
                .build();

        LOGGER.info("PolicyBriefJobStore initialized (maxSize={}, expireAfterAccess={}min)",
                MAX_JOBS, EXPIRE_AFTER_ACCESS_MINUTES);
    }

    /**
     * Returns the singleton instance of the job store.
     * Thread-safe via double-checked locking.
     *
     * @return the singleton {@link PolicyBriefJobStore}
     */
    public static PolicyBriefJobStore getInstance() {

        if (instance == null) {

            synchronized (PolicyBriefJobStore.class) {

                if (instance == null) {

                    instance = new PolicyBriefJobStore();
                }
            }
        }
        return instance;
    }

    /**
     * Retrieves a job by its identifier.
     *
     * @param jobId the job identifier
     * @return the {@link PolicyBriefJob}, or {@code null} if not found or expired
     */
    public PolicyBriefJob get(String jobId) {

        if (jobId == null) {

            return null;
        }
        return cache.getIfPresent(jobId);
    }

    /**
     * Stores a job in the cache.
     *
     * @param jobId the job identifier
     * @param job   the job to store
     */
    public void put(String jobId, PolicyBriefJob job) {

        if (jobId == null || job == null) {

            throw new IllegalArgumentException("jobId and job must not be null");
        }
        cache.put(jobId, job);
    }

    /**
     * Removes a job from the cache.
     *
     * @param jobId the job identifier
     */
    public void remove(String jobId) {

        if (jobId == null) {

            return;
        }
        cache.invalidate(jobId);
        LOGGER.debug("PolicyBriefJob removed: jobId={}", jobId);
    }
}
