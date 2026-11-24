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

import app.krista.extension.authorization.RequestAuthenticator;
import app.krista.extension.event.EventSubscription;
import app.krista.extension.event.WaitForEventListener;
import app.krista.extension.executor.Extension;
import app.krista.extension.executor.ExtensionCatalogRequest;
import app.krista.extension.executor.ExtensionEntityDefinition;
import app.krista.extension.executor.ExtensionResponse;
import app.krista.extension.executor.ExtensionSubCatalogRequest;
import app.krista.extension.executor.Invoker;
import app.krista.extension.executor.InvokerEntityStore;
import app.krista.extension.executor.InvokerEventHandler;
import app.krista.extension.request.ProtoRequest;
import app.krista.extension.request.ProtoResponse;
import app.krista.extension.request.RoutingInfo;
import app.krista.extensions.hook.Constants;
import app.krista.model.entity.EntityType;
import com.fasterxml.jackson.core.type.TypeReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;

import static app.krista.extensions.hook.util.JsonMapper.getInstance;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Live integration tests for {@link PolicyBriefService} using a real LLM and a local
 * policy-doc corpus to service EXECUTE_TOOL turns.
 *
 * <p>These tests intentionally assert semantic invariants instead of exact wording.
 * They are gated behind {@code OPENAI_API_KEY} and skipped automatically when that
 * environment variable is not present.</p>
 */
@Tag("live")
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
public class PolicyBriefServiceLiveTest {

    private static final String TOOL_DEFINITIONS = """
            [
              {"name":"lookup_vendor","description":"Look up vendor onboarding and compliance status before any vendor-side action."},
              {"name":"create_vendor","description":"Create a new vendor record in the procurement system."},
              {"name":"soft_delete_ticket","description":"Soft delete a support ticket while preserving audit history."},
              {"name":"hard_delete_ticket","description":"Permanently delete a support ticket and its history."},
              {"name":"search_policy_docset","description":"Search policy documents relevant to the current workflow and summarize the results."}
            ]
            """;

    private static final String BASE_POLICY_DOCS = """
            Procurement Control Policy 2026
            - Before creating or modifying any vendor record, verify the vendor onboarding packet is complete.
            - New vendor creation requires manager approval and evidence of sanctions screening.
            - All vendor changes must be logged with actor, timestamp, and justification.

            Records Retention and Audit Policy
            - Customer-support records must not be hard deleted during active retention periods.
            - Soft delete is permitted when the record remains recoverable and the audit trail is preserved.
            - Permanent deletion requires legal hold review and explicit retention-expiry confirmation.

            Customer Support Operations Policy
            - Workflows should prioritize customer response speed, but mandatory controls remain in effect.
            - Auditability and recoverability are non-negotiable for case and ticket data.
            - Manager approval is required for operations that materially affect customer records or vendor master data.
            """;

    private static final String PROCUREMENT_INTENT =
            "Create and manage vendors for procurement workflows while following enterprise controls.";
    private static final String SUPPORT_INTENT =
            "Manage customer support tickets while preserving required audit and retention controls.";

    private PolicyBriefService service;
    private LiveInvoker invoker;
    private final List<String> createdJobIds = new ArrayList<>();

    @BeforeEach
    void setUp() {

        PolicyBriefGeneratorFactory.reset();
        invoker = new LiveInvoker(System.getenv("OPENAI_API_KEY"));
        service = new PolicyBriefService(invoker);
    }

    @AfterEach
    void tearDown() {

        for (String jobId : createdJobIds) {
            PolicyBriefJobStore.getInstance().remove(jobId);
            PolicyBriefGeneratorFactory.cleanupMemory(jobId);
        }
        createdJobIds.clear();
        PolicyBriefGeneratorFactory.reset();
    }

    @Nested
    @DisplayName("Cold Start Generation")
    class ColdStartGenerationTests {

        @Test
        @DisplayName("Cold-start generation should request search and produce a grounded vendor-control brief")
        void coldStartGenerationProducesGroundedBrief() throws Exception {

            PolicyBriefJob job = runJobToAwaitingInput(PROCUREMENT_INTENT, List.of(), BASE_POLICY_DOCS, 3);

            assertTrue(invoker.getSearchQueries().size() >= 1,
                    "Expected the live authoring flow to request at least one policy-doc search");
            assertContainsConcepts(job.getPolicyBrief(),
                    "vendor verification",
                    new String[] {"verify vendor", "vendor onboarding", "sanctions"},
                    new String[] {"manager approval", "approval"},
                    new String[] {"audit", "logged", "justification"});
        }
    }

    @Nested
    @DisplayName("Seeded Refinement")
    class SeededRefinementTests {

        @Test
        @DisplayName("Seeded confirmation should complete without dropping existing core controls")
        void seededLooksGoodCompletesWithoutDroppingCoreRules() throws Exception {

            List<String> seed = List.of(
                    "Verify vendor onboarding and sanctions status before creating a vendor.",
                    "Log all vendor changes with actor, timestamp, and justification."
            );

            String jobId = startJob(PROCUREMENT_INTENT, seed);
            PolicyBriefJob initial = pollForStatus(jobId, PolicyBriefJob.Status.AWAITING_INPUT, 10000);
            assertEquals(seed, initial.getPolicyBrief(), "Seeded generation should preserve the saved brief exactly");

            service.submitFeedback(jobId, "looks good");
            PolicyBriefJob completed = driveToStatus(jobId, PolicyBriefJob.Status.COMPLETE, BASE_POLICY_DOCS, 2);

            assertContainsConcepts(completed.getPolicyBrief(),
                    "seeded confirmation",
                    new String[] {"vendor onboarding", "verify vendor", "sanctions"},
                    new String[] {"log", "logged", "audit", "justification"});
            assertNoHardDeleteEmphasis(completed.getPolicyBrief(),
                    "Vendor confirmation brief should stay focused on vendor controls");
        }

        @Test
        @DisplayName("Targeted add should introduce approval guidance without dropping the existing vendor-verification rule")
        void targetedAddIntroducesApprovalGuidance() throws Exception {

            List<String> seed = List.of(
                    "Verify vendor onboarding and sanctions status before creating a vendor.",
                    "Log all vendor changes with actor, timestamp, and justification."
            );

            String jobId = startJob(PROCUREMENT_INTENT, seed);
            pollForStatus(jobId, PolicyBriefJob.Status.AWAITING_INPUT, 10000);

            service.submitFeedback(jobId, "You missed adding that manager approval is required before creating a vendor.");
            PolicyBriefJob updated = driveToStatus(jobId, PolicyBriefJob.Status.AWAITING_INPUT, BASE_POLICY_DOCS, 2);

            assertContainsConcepts(updated.getPolicyBrief(),
                    "targeted add",
                    new String[] {"manager approval", "approval"},
                    new String[] {"vendor onboarding", "verify vendor", "sanctions"},
                    new String[] {"log", "audit", "justification"});
        }

        @Test
        @DisplayName("Targeted soften should allow soft delete while preserving auditability and avoiding hard-delete language")
        void targetedSoftenAllowsSoftDelete() throws Exception {

            List<String> seed = List.of(
                    "Never delete support tickets under any circumstance.",
                    "Maintain an audit trail for all support-ticket changes."
            );

            String jobId = startJob(SUPPORT_INTENT, seed);
            pollForStatus(jobId, PolicyBriefJob.Status.AWAITING_INPUT, 10000);

            service.submitFeedback(jobId,
                    "The delete restriction is too strong. Allow soft delete for tickets if the record stays recoverable and the audit trail remains intact.");
            PolicyBriefJob updated = driveToStatus(jobId, PolicyBriefJob.Status.AWAITING_INPUT, BASE_POLICY_DOCS, 2);

            assertContainsConcepts(updated.getPolicyBrief(),
                    "targeted soften",
                    new String[] {"soft delete", "recoverable"},
                    new String[] {"audit", "audit trail", "logged"});
            assertNotContainsAny(updated.getPolicyBrief(),
                    "targeted soften should remove absolute hard-delete prohibition wording",
                    "never delete under any circumstance",
                    "no deletion under any circumstance");
        }

        @Test
        @DisplayName("Broad rewrite should rebalance toward support operations while retaining mandatory audit and retention controls")
        void broadRewriteRebalancesButRetainsControls() throws Exception {

            List<String> seed = List.of(
                    "Treat every support-ticket action as a high-risk security event requiring maximum restriction.",
                    "Do not prioritize response speed over strict control language.",
                    "Maintain an audit trail for all support-ticket changes."
            );

            String jobId = startJob(SUPPORT_INTENT, seed);
            pollForStatus(jobId, PolicyBriefJob.Status.AWAITING_INPUT, 10000);

            service.submitFeedback(jobId,
                    "This is too security-heavy. Rebalance it for customer-support workflows, but keep the mandatory audit and retention controls.");
            PolicyBriefJob updated = driveToStatus(jobId, PolicyBriefJob.Status.AWAITING_INPUT, BASE_POLICY_DOCS, 2);

            assertContainsConcepts(updated.getPolicyBrief(),
                    "broad rewrite",
                    new String[] {"customer support", "response speed", "workflow"},
                    new String[] {"audit", "audit trail", "logged"},
                    new String[] {"retention", "recoverable", "legal hold"});
            assertNotContainsAny(updated.getPolicyBrief(),
                    "broad rewrite should reduce the old maximalist framing",
                    "maximum restriction",
                    "high-risk security event");
        }
    }

    private String startJob(String intent, List<String> seed) {

        String jobId = service.startGeneration(intent, TOOL_DEFINITIONS, seed);
        createdJobIds.add(jobId);
        return jobId;
    }

    private PolicyBriefJob runJobToAwaitingInput(String intent, List<String> seed,
                                                 String policyDocs, int maxSearchRounds) throws Exception {

        String jobId = startJob(intent, seed);
        int searchRounds = 0;

        while (true) {
            PolicyBriefJob job = waitForInteractiveState(jobId, 20000);
            if (job.getStatus() == PolicyBriefJob.Status.EXECUTE_TOOL) {
                searchRounds++;
                assertTrue(searchRounds <= maxSearchRounds,
                        "Policy-brief job exceeded expected search rounds: " + searchRounds);
                invoker.recordToolArguments(job.getToolArguments());
                service.submitFeedback(jobId, renderSearchResults(job, policyDocs));
                continue;
            }
            assertEquals(PolicyBriefJob.Status.AWAITING_INPUT, job.getStatus(),
                    "Expected the live generation flow to reach AWAITING_INPUT");
            return job;
        }
    }

    private PolicyBriefJob driveToStatus(String jobId, PolicyBriefJob.Status expectedStatus,
                                         String policyDocs, int maxSearchRounds) throws Exception {

        int searchRounds = 0;
        while (true) {
            PolicyBriefJob job = waitForInteractiveState(jobId, 20000);
            if (job.getStatus() == PolicyBriefJob.Status.EXECUTE_TOOL) {
                searchRounds++;
                assertTrue(searchRounds <= maxSearchRounds,
                        "Policy-brief refinement exceeded expected search rounds: " + searchRounds);
                invoker.recordToolArguments(job.getToolArguments());
                service.submitFeedback(jobId, renderSearchResults(job, policyDocs));
                continue;
            }
            assertEquals(expectedStatus, job.getStatus(),
                    "Unexpected terminal status while driving policy-brief refinement");
            return job;
        }
    }

    private PolicyBriefJob waitForInteractiveState(String jobId, long timeoutMillis) throws Exception {

        long deadline = System.currentTimeMillis() + timeoutMillis;
        while (System.currentTimeMillis() < deadline) {
            PolicyBriefJob job = service.getResult(jobId);
            if (job != null && job.getStatus() != PolicyBriefJob.Status.PENDING) {
                return job;
            }
            Thread.sleep(150);
        }

        PolicyBriefJob finalJob = service.getResult(jobId);
        throw new AssertionError("Timed out waiting for non-PENDING state. Final job=" + finalJob);
    }

    private PolicyBriefJob pollForStatus(String jobId, PolicyBriefJob.Status expectedStatus,
                                         long timeoutMillis) throws Exception {

        long deadline = System.currentTimeMillis() + timeoutMillis;
        while (System.currentTimeMillis() < deadline) {
            PolicyBriefJob job = service.getResult(jobId);
            if (job != null && job.getStatus() == expectedStatus) {
                return job;
            }
            if (job != null && job.getStatus() == PolicyBriefJob.Status.FAILED) {
                throw new AssertionError("Job failed unexpectedly: " + job.getErrorMessage());
            }
            Thread.sleep(150);
        }

        PolicyBriefJob finalJob = service.getResult(jobId);
        throw new AssertionError("Timed out waiting for status " + expectedStatus + ". Final job=" + finalJob);
    }

    private String renderSearchResults(PolicyBriefJob job, String policyDocs) {

        String query = invoker.extractLatestQuery();
        return """
                Search Query: %s

                Policy Documents:
                %s
                """.formatted(query, policyDocs);
    }

    private void assertContainsConcepts(List<String> brief, String context, String[]... conceptGroups) {

        String normalized = normalize(joinBrief(brief));
        for (String[] conceptGroup : conceptGroups) {
            assertTrue(List.of(conceptGroup).stream().map(this::normalize).anyMatch(normalized::contains),
                    () -> "Expected concept group " + conceptGroup + " in " + context + " brief.\nBrief: " + brief);
        }
    }

    private void assertNotContainsAny(List<String> brief, String context, String... forbiddenPhrases) {

        String normalized = normalize(joinBrief(brief));
        for (String forbiddenPhrase : forbiddenPhrases) {
            assertFalse(normalized.contains(normalize(forbiddenPhrase)),
                    () -> context + " — forbidden phrase still present: " + forbiddenPhrase + "\nBrief: " + brief);
        }
    }

    private void assertNoHardDeleteEmphasis(List<String> brief, String context) {

        assertNotContainsAny(brief, context,
                "hard delete ticket",
                "permanently delete support ticket",
                "delete support ticket");
    }

    private String joinBrief(List<String> brief) {

        return String.join("\n", brief);
    }

    private String normalize(String value) {

        return value.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    /**
     * Minimal invoker with real LLM attributes for {@link PolicyBriefGeneratorFactory}.
     */
    static class LiveInvoker implements Invoker {

        private final Map<String, Object> attributes;
        private final ConcurrentLinkedQueue<String> searchQueries = new ConcurrentLinkedQueue<>();

        LiveInvoker(String apiKey) {

            this.attributes = Map.of(
                    Constants.CLOUD_LLM_PROVIDER, "openai",
                    Constants.CLOUD_API_KEY, apiKey
            );
        }

        void recordToolArguments(String toolArgumentsJson) throws Exception {

            Map<String, Object> args = getInstance().readValue(toolArgumentsJson, new TypeReference<>() {});
            Object question = args.get("Question");
            if (question != null) {
                searchQueries.add(question.toString());
            }
        }

        String extractLatestQuery() {

            String latest = null;
            for (String query : searchQueries) {
                latest = query;
            }
            return latest == null ? "(missing search query)" : latest;
        }

        Set<String> getSearchQueries() {

            return new LinkedHashSet<>(searchQueries);
        }

        @Override public Map<String, Invoker> getInvokers() { return Map.of(); }
        @Override public String getInvokerId() { return "live-policy-brief-invoker"; }
        @Override public String getInvokerName() { return "Live Policy Brief Invoker"; }
        @Override public String getWorkspaceId() { return "ws-live"; }
        @Override public Extension getExtension() { return null; }
        @Override public boolean isRemoved() { return false; }
        @Override public RoutingInfo getRoutingInfo() { return null; }
        @Override public RequestAuthenticator getRequestAuthenticator() { return null; }
        @Override public Map<String, Object> getAttributes() { return attributes; }
        @Override public Map<String, String> getCustomTabs() { return Map.of(); }
        @Override public CompletableFuture<Void> validateAttributes(Map<String, Object> m) { return CompletableFuture.completedFuture(null); }
        @Override public CompletableFuture<Void> testConnection() { return CompletableFuture.completedFuture(null); }
        @Override public CompletableFuture<Map<String, Object>> handleCatalogRequest(String id, boolean asUser, Map<String, Object> args) { return CompletableFuture.completedFuture(Map.of()); }
        @Override public CompletableFuture<ExtensionResponse> handleCatalogRequestV2(String id, boolean asUser, Map<String, Object> args) { return CompletableFuture.completedFuture(null); }
        @Override public boolean hasRuntimeDefinedCatalogRequests() { return false; }
        @Override public InvokerEntityStore getEntityStore(EntityType et) { return null; }
        @Override public CompletableFuture<ProtoResponse> handleApiRequest(String id, ProtoRequest arg) { return CompletableFuture.completedFuture(null); }
        @Override public boolean hasRuntimeDefinedApiRequests() { return false; }
        @Override public InvokerEventHandler getEventHandler() { return null; }
        @Override public Map<String, String> getBindings() { return Map.of(); }
        @Override public CompletableFuture<Void> registerEventListener(WaitForEventListener l) { return CompletableFuture.completedFuture(null); }
        @Override public CompletableFuture<Void> unregisterEventListener(String id) { return CompletableFuture.completedFuture(null); }
        @Override public List<WaitForEventListener> listEventListeners() { return List.of(); }
        @Override public List<EventSubscription> listEventSubscriptions() { return List.of(); }
        @Override public void deliverEvent(Map<String, Object> data, String id) {}
        @Override public CompletableFuture<Void> prepareChangeRoutingId(String newId) { return CompletableFuture.completedFuture(null); }
        @Override public Map<String, ? extends ExtensionCatalogRequest> getCatalogRequests() { return Map.of(); }
        @Override public Map<String, ? extends ExtensionSubCatalogRequest> getSubCatalogRequests() { return Map.of(); }
        @Override public List<EntityType> getEntityTypes() { return List.of(); }
        @Override public ExtensionEntityDefinition getEntityDefinition(EntityType et) { return null; }
        @Override public Map<EntityType, ExtensionEntityDefinition> getEntityDefinitions() { return Map.of(); }
    }
}
