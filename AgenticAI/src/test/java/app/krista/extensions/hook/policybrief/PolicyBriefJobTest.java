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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link PolicyBriefJob} state model.
 *
 * <p>Validates construction defaults, field accessors, status transitions,
 * and volatile field visibility across threads.</p>
 */
public class PolicyBriefJobTest {

    private static final String JOB_ID = "job-001";
    private static final String INTENT = "Process refund requests";
    private static final String TOOL_DEFINITIONS = "[{\"name\":\"refundTool\"}]";

    // =========================================================================
    // Construction and defaults
    // =========================================================================

    @Nested
    @DisplayName("Construction and Defaults")
    class ConstructionTests {

        @Test
        @DisplayName("Should initialize with provided values and correct defaults")
        void testConstructorSetsFieldsCorrectly() {

            PolicyBriefJob job = new PolicyBriefJob(
                    JOB_ID,
                    PolicyBriefJob.Status.PENDING,
                    INTENT,
                    TOOL_DEFINITIONS
            );

            assertEquals(JOB_ID, job.getJobId(), "jobId should match constructor argument");
            assertEquals(PolicyBriefJob.Status.PENDING, job.getStatus(), "status should be PENDING");
            assertEquals(INTENT, job.getIntent(), "intent should match");
            assertEquals(TOOL_DEFINITIONS, job.getToolDefinitions(), "toolDefinitions should match");
        }

        @Test
        @DisplayName("Should initialize policyBrief as empty list")
        void testPolicyBriefDefaultsToEmptyList() {

            PolicyBriefJob job = new PolicyBriefJob(
                    JOB_ID, PolicyBriefJob.Status.PENDING,
                    INTENT, TOOL_DEFINITIONS
            );

            assertNotNull(job.getPolicyBrief(), "policyBrief should not be null");
            assertTrue(job.getPolicyBrief().isEmpty(), "policyBrief should be empty by default");
        }

        @Test
        @DisplayName("Should initialize message and errorMessage as null")
        void testMessageFieldsDefaultToNull() {

            PolicyBriefJob job = new PolicyBriefJob(
                    JOB_ID, PolicyBriefJob.Status.PENDING,
                    INTENT, TOOL_DEFINITIONS
            );

            assertNull(job.getMessage(), "message should be null by default");
            assertNull(job.getErrorMessage(), "errorMessage should be null by default");
        }

        @Test
        @DisplayName("Should accept null values for optional context fields")
        void testNullContextFields() {

            PolicyBriefJob job = new PolicyBriefJob(
                    JOB_ID, PolicyBriefJob.Status.PENDING,
                    null, null
            );

            assertNull(job.getIntent());
            assertNull(job.getToolDefinitions());
        }
    }

    // =========================================================================
    // Status transitions
    // =========================================================================

    @Nested
    @DisplayName("Status Transitions")
    class StatusTransitionTests {

        @Test
        @DisplayName("Should transition from PENDING to AWAITING_INPUT")
        void testPendingToAwaitingInput() {

            PolicyBriefJob job = new PolicyBriefJob(
                    JOB_ID, PolicyBriefJob.Status.PENDING,
                    INTENT, TOOL_DEFINITIONS
            );

            job.setStatus(PolicyBriefJob.Status.AWAITING_INPUT);

            assertEquals(PolicyBriefJob.Status.AWAITING_INPUT, job.getStatus());
        }

        @Test
        @DisplayName("Should transition from PENDING to FAILED on error")
        void testPendingToFailed() {

            PolicyBriefJob job = new PolicyBriefJob(
                    JOB_ID, PolicyBriefJob.Status.PENDING,
                    INTENT, TOOL_DEFINITIONS
            );

            job.setStatus(PolicyBriefJob.Status.FAILED);
            job.setErrorMessage("LLM call timed out");

            assertEquals(PolicyBriefJob.Status.FAILED, job.getStatus());
            assertEquals("LLM call timed out", job.getErrorMessage());
        }

        @Test
        @DisplayName("Should transition from AWAITING_INPUT back to PENDING on feedback")
        void testAwaitingInputBackToPending() {

            PolicyBriefJob job = new PolicyBriefJob(
                    JOB_ID, PolicyBriefJob.Status.PENDING,
                    INTENT, TOOL_DEFINITIONS
            );

            job.setStatus(PolicyBriefJob.Status.AWAITING_INPUT);
            job.setStatus(PolicyBriefJob.Status.PENDING);

            assertEquals(PolicyBriefJob.Status.PENDING, job.getStatus());
        }

        @Test
        @DisplayName("Should transition from AWAITING_INPUT to COMPLETE on confirmation")
        void testAwaitingInputToComplete() {

            PolicyBriefJob job = new PolicyBriefJob(
                    JOB_ID, PolicyBriefJob.Status.PENDING,
                    INTENT, TOOL_DEFINITIONS
            );

            job.setStatus(PolicyBriefJob.Status.AWAITING_INPUT);
            job.setPolicyBrief(List.of("Rule 1", "Rule 2"));
            job.setMessage("Here is the draft brief.");

            job.setStatus(PolicyBriefJob.Status.COMPLETE);

            assertEquals(PolicyBriefJob.Status.COMPLETE, job.getStatus());
            assertEquals(2, job.getPolicyBrief().size());
        }

        @Test
        @DisplayName("Should support full lifecycle: PENDING -> AWAITING_INPUT -> PENDING -> AWAITING_INPUT -> COMPLETE")
        void testFullLifecycleTransition() {

            PolicyBriefJob job = new PolicyBriefJob(
                    JOB_ID, PolicyBriefJob.Status.PENDING,
                    INTENT, TOOL_DEFINITIONS
            );

            // Initial generation
            assertEquals(PolicyBriefJob.Status.PENDING, job.getStatus());

            // LLM produces initial brief
            job.setStatus(PolicyBriefJob.Status.AWAITING_INPUT);
            job.setPolicyBrief(List.of("Rule 1"));
            job.setMessage("Here is a draft.");

            assertEquals(PolicyBriefJob.Status.AWAITING_INPUT, job.getStatus());

            // Author provides feedback
            job.setStatus(PolicyBriefJob.Status.PENDING);

            // LLM refines brief
            job.setStatus(PolicyBriefJob.Status.AWAITING_INPUT);
            job.setPolicyBrief(List.of("Rule 1 (revised)", "Rule 2"));
            job.setMessage("Updated based on your feedback.");

            // Author confirms
            job.setStatus(PolicyBriefJob.Status.COMPLETE);

            assertEquals(PolicyBriefJob.Status.COMPLETE, job.getStatus());
            assertEquals(List.of("Rule 1 (revised)", "Rule 2"), job.getPolicyBrief());
        }
    }

    // =========================================================================
    // Mutable field setters
    // =========================================================================

    @Nested
    @DisplayName("Mutable Field Setters")
    class MutableFieldTests {

        @Test
        @DisplayName("Should replace policyBrief list entirely")
        void testSetPolicyBriefReplacesExisting() {

            PolicyBriefJob job = new PolicyBriefJob(
                    JOB_ID, PolicyBriefJob.Status.PENDING,
                    INTENT, TOOL_DEFINITIONS
            );

            job.setPolicyBrief(List.of("Rule A", "Rule B"));
            assertEquals(List.of("Rule A", "Rule B"), job.getPolicyBrief());

            job.setPolicyBrief(List.of("Rule X"));
            assertEquals(List.of("Rule X"), job.getPolicyBrief());
        }

        @Test
        @DisplayName("Should assign stable rule IDs when policyBrief is set")
        void testRuleIdsAssigned() {

            PolicyBriefJob job = new PolicyBriefJob(
                    JOB_ID, PolicyBriefJob.Status.PENDING,
                    INTENT, TOOL_DEFINITIONS
            );

            job.setPolicyBrief(List.of("Rule A", "Rule B"));

            assertEquals(List.of("R1", "R2"),
                    job.getPolicyBriefRules().stream().map(PolicyBriefRule::id).toList());
        }

        @Test
        @DisplayName("Should allocate new IDs after existing rules")
        void testCreateRuleAllocatesNextId() {

            PolicyBriefJob job = new PolicyBriefJob(
                    JOB_ID, PolicyBriefJob.Status.PENDING,
                    INTENT, TOOL_DEFINITIONS
            );

            job.setPolicyBrief(List.of("Rule A", "Rule B"));

            PolicyBriefRule newRule = job.createRule("Rule C");
            assertEquals("R3", newRule.id());
        }

        @Test
        @DisplayName("Should allow setting message to null")
        void testSetMessageToNull() {

            PolicyBriefJob job = new PolicyBriefJob(
                    JOB_ID, PolicyBriefJob.Status.PENDING,
                    INTENT, TOOL_DEFINITIONS
            );

            job.setMessage("Some message");
            assertNotNull(job.getMessage());

            job.setMessage(null);
            assertNull(job.getMessage());
        }

        @Test
        @DisplayName("Should allow setting errorMessage independently of status")
        void testErrorMessageIndependentOfStatus() {

            PolicyBriefJob job = new PolicyBriefJob(
                    JOB_ID, PolicyBriefJob.Status.PENDING,
                    INTENT, TOOL_DEFINITIONS
            );

            job.setErrorMessage("Parse error: invalid JSON");

            assertEquals("Parse error: invalid JSON", job.getErrorMessage());
            assertEquals(PolicyBriefJob.Status.PENDING, job.getStatus(),
                    "Setting errorMessage should not change status");
        }
    }

    // =========================================================================
    // Volatile field cross-thread visibility
    // =========================================================================

    @Nested
    @DisplayName("Cross-Thread Visibility")
    class CrossThreadVisibilityTests {

        @Test
        @DisplayName("Should make status changes visible to another thread via volatile")
        void testStatusVisibleAcrossThreads() throws InterruptedException {

            PolicyBriefJob job = new PolicyBriefJob(
                    JOB_ID, PolicyBriefJob.Status.PENDING,
                    INTENT, TOOL_DEFINITIONS
            );

            AtomicReference<PolicyBriefJob.Status> observedStatus = new AtomicReference<>();
            CountDownLatch writerDone = new CountDownLatch(1);
            CountDownLatch readerDone = new CountDownLatch(1);

            // Writer thread
            Thread writer = Thread.startVirtualThread(() -> {

                job.setStatus(PolicyBriefJob.Status.AWAITING_INPUT);
                job.setPolicyBrief(List.of("Rule 1", "Rule 2"));
                job.setMessage("Draft ready");
                writerDone.countDown();
            });

            // Reader thread waits for writer, then reads
            Thread reader = Thread.startVirtualThread(() -> {

                try {

                    writerDone.await(5, TimeUnit.SECONDS);
                    observedStatus.set(job.getStatus());
                    readerDone.countDown();
                } catch (InterruptedException e) {

                    Thread.currentThread().interrupt();
                }
            });

            assertTrue(readerDone.await(5, TimeUnit.SECONDS), "Reader should complete within timeout");
            assertEquals(PolicyBriefJob.Status.AWAITING_INPUT, observedStatus.get(),
                    "Reader thread should see the status written by writer thread");
        }

        @Test
        @DisplayName("Should make policyBrief changes visible to another thread via volatile")
        void testPolicyBriefVisibleAcrossThreads() throws InterruptedException {

            PolicyBriefJob job = new PolicyBriefJob(
                    JOB_ID, PolicyBriefJob.Status.PENDING,
                    INTENT, TOOL_DEFINITIONS
            );

            List<String> expectedRules = List.of("No PII exposure", "All writes must be logged");
            AtomicReference<List<String>> observedBrief = new AtomicReference<>();
            CountDownLatch writerDone = new CountDownLatch(1);
            CountDownLatch readerDone = new CountDownLatch(1);

            Thread.startVirtualThread(() -> {

                job.setPolicyBrief(expectedRules);
                writerDone.countDown();
            });

            Thread.startVirtualThread(() -> {

                try {

                    writerDone.await(5, TimeUnit.SECONDS);
                    observedBrief.set(job.getPolicyBrief());
                    readerDone.countDown();
                } catch (InterruptedException e) {

                    Thread.currentThread().interrupt();
                }
            });

            assertTrue(readerDone.await(5, TimeUnit.SECONDS));
            assertEquals(expectedRules, observedBrief.get(),
                    "Reader thread should see the policyBrief written by writer thread");
        }
    }

    // =========================================================================
    // toString
    // =========================================================================

    @Test
    @DisplayName("Should include key fields in toString output")
    void testToStringContainsKeyFields() {

        PolicyBriefJob job = new PolicyBriefJob(
                JOB_ID, PolicyBriefJob.Status.PENDING,
                INTENT, TOOL_DEFINITIONS
        );
        job.setPolicyBrief(List.of("Rule 1", "Rule 2"));

        String str = job.toString();
        assertTrue(str.contains(JOB_ID), "toString should contain jobId");
        assertTrue(str.contains("PENDING"), "toString should contain status");
        assertTrue(str.contains("2 rules"), "toString should contain rule count");
    }

    @Test
    @DisplayName("Should handle null policyBrief in toString without NPE")
    void testToStringWithNullPolicyBrief() {

        PolicyBriefJob job = new PolicyBriefJob(
                JOB_ID, PolicyBriefJob.Status.PENDING,
                INTENT, TOOL_DEFINITIONS
        );
        job.setPolicyBrief(null);

        String str = job.toString();
        assertTrue(str.contains("null"), "toString should handle null policyBrief");
    }

    // =========================================================================
    // Status enum coverage
    // =========================================================================

    @Test
    @DisplayName("Should have exactly four status values")
    void testStatusEnumValues() {

        PolicyBriefJob.Status[] values = PolicyBriefJob.Status.values();
        assertEquals(5, values.length);
        assertEquals(PolicyBriefJob.Status.PENDING, PolicyBriefJob.Status.valueOf("PENDING"));
        assertEquals(PolicyBriefJob.Status.AWAITING_INPUT, PolicyBriefJob.Status.valueOf("AWAITING_INPUT"));
        assertEquals(PolicyBriefJob.Status.EXECUTE_TOOL, PolicyBriefJob.Status.valueOf("EXECUTE_TOOL"));
        assertEquals(PolicyBriefJob.Status.COMPLETE, PolicyBriefJob.Status.valueOf("COMPLETE"));
        assertEquals(PolicyBriefJob.Status.FAILED, PolicyBriefJob.Status.valueOf("FAILED"));
    }
}
