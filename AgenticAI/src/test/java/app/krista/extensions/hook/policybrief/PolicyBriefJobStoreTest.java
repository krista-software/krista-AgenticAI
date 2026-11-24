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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link PolicyBriefJobStore} — the singleton Caffeine-backed job cache.
 *
 * <p>Uses unique job IDs per test to avoid cross-test interference through
 * the shared singleton instance.</p>
 */
public class PolicyBriefJobStoreTest {

    private PolicyBriefJobStore store;

    @BeforeEach
    void setUp() {

        store = PolicyBriefJobStore.getInstance();
    }

    // =========================================================================
    // Singleton behavior
    // =========================================================================

    @Test
    @DisplayName("Should return the same singleton instance across multiple calls")
    void testSingletonConsistency() {

        PolicyBriefJobStore first = PolicyBriefJobStore.getInstance();
        PolicyBriefJobStore second = PolicyBriefJobStore.getInstance();

        assertSame(first, second, "getInstance() should return the same instance");
    }

    // =========================================================================
    // Put and Get round-trip
    // =========================================================================

    @Nested
    @DisplayName("Put and Get Operations")
    class PutGetTests {

        @Test
        @DisplayName("Should store and retrieve a job by jobId")
        void testPutGetRoundTrip() {

            String jobId = uniqueId();
            PolicyBriefJob job = createJob(jobId);

            store.put(jobId, job);
            PolicyBriefJob retrieved = store.get(jobId);

            assertNotNull(retrieved, "Should retrieve a stored job");
            assertSame(job, retrieved, "Should return the exact same job instance");
            assertEquals(jobId, retrieved.getJobId());
        }

        @Test
        @DisplayName("Should return null for an unknown jobId")
        void testGetReturnsNullForUnknownId() {

            PolicyBriefJob result = store.get("nonexistent-" + UUID.randomUUID());

            assertNull(result, "Should return null for a jobId that was never stored");
        }

        @Test
        @DisplayName("Should return null when jobId is null")
        void testGetWithNullJobIdReturnsNull() {

            PolicyBriefJob result = store.get(null);

            assertNull(result, "Should return null for null jobId");
        }

        @Test
        @DisplayName("Should overwrite existing job when put is called with same jobId")
        void testPutOverwritesExistingJob() {

            String jobId = uniqueId();
            PolicyBriefJob original = createJob(jobId);
            PolicyBriefJob replacement = createJob(jobId);

            store.put(jobId, original);
            store.put(jobId, replacement);

            PolicyBriefJob retrieved = store.get(jobId);
            assertSame(replacement, retrieved, "Should return the replacement job, not the original");
        }
    }

    // =========================================================================
    // Remove
    // =========================================================================

    @Nested
    @DisplayName("Remove Operations")
    class RemoveTests {

        @Test
        @DisplayName("Should remove a stored job so subsequent get returns null")
        void testRemoveInvalidatesEntry() {

            String jobId = uniqueId();
            PolicyBriefJob job = createJob(jobId);

            store.put(jobId, job);
            assertNotNull(store.get(jobId), "Precondition: job should exist before removal");

            store.remove(jobId);

            assertNull(store.get(jobId), "Job should be null after removal");
        }

        @Test
        @DisplayName("Should handle removing a non-existent jobId without error")
        void testRemoveNonExistentJobIsNoop() {

            assertDoesNotThrow(() -> store.remove("never-stored-" + UUID.randomUUID()),
                    "Removing a non-existent job should not throw");
        }

        @Test
        @DisplayName("Should handle removing null jobId without error")
        void testRemoveNullJobIdIsNoop() {

            assertDoesNotThrow(() -> store.remove(null),
                    "Removing null jobId should not throw");
        }
    }

    // =========================================================================
    // Null argument validation
    // =========================================================================

    @Nested
    @DisplayName("Null Argument Validation")
    class NullValidationTests {

        @Test
        @DisplayName("Should throw IllegalArgumentException when putting null jobId")
        void testPutNullJobIdThrows() {

            PolicyBriefJob job = createJob("any-id");

            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> store.put(null, job),
                    "put(null, job) should throw IllegalArgumentException"
            );
            assertTrue(exception.getMessage().contains("null"),
                    "Exception message should mention null");
        }

        @Test
        @DisplayName("Should throw IllegalArgumentException when putting null job")
        void testPutNullJobThrows() {

            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> store.put("some-id", null),
                    "put(id, null) should throw IllegalArgumentException"
            );
            assertTrue(exception.getMessage().contains("null"),
                    "Exception message should mention null");
        }
    }

    // =========================================================================
    // Multiple independent entries
    // =========================================================================

    @Test
    @DisplayName("Should store and retrieve multiple independent jobs")
    void testMultipleJobs() {

        String jobId1 = uniqueId();
        String jobId2 = uniqueId();
        PolicyBriefJob job1 = createJob(jobId1);
        PolicyBriefJob job2 = createJob(jobId2);

        store.put(jobId1, job1);
        store.put(jobId2, job2);

        assertSame(job1, store.get(jobId1), "Should retrieve job1 by its id");
        assertSame(job2, store.get(jobId2), "Should retrieve job2 by its id");

        store.remove(jobId1);
        assertNull(store.get(jobId1), "job1 should be gone after removal");
        assertNotNull(store.get(jobId2), "job2 should remain after removing job1");
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private static String uniqueId() {

        return "test-" + UUID.randomUUID();
    }

    private static PolicyBriefJob createJob(String jobId) {

        return new PolicyBriefJob(
                jobId,
                PolicyBriefJob.Status.PENDING,
                "Test intent",
                "[{\"name\":\"testTool\"}]"
        );
    }
}
