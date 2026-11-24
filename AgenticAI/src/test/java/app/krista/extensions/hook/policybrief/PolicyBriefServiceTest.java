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

import app.krista.extension.executor.*;
import app.krista.extension.authorization.AuthorizationException;
import app.krista.extension.authorization.RequestAuthenticator;
import app.krista.extension.event.EventSubscription;
import app.krista.extension.event.WaitForEventListener;
import app.krista.extension.request.ProtoRequest;
import app.krista.extension.request.ProtoResponse;
import app.krista.extension.request.RoutingInfo;
import app.krista.model.entity.EntityType;
import org.junit.jupiter.api.*;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Lifecycle and behavioral tests for {@link PolicyBriefService}.
 *
 * <p>Uses a {@link FakePolicyBriefGenerator} to control LLM responses without
 * requiring real API keys or network calls. The fake generator implements the
 * {@link PolicyBriefGenerator} interface directly, following the same pattern
 * as {@code FakePolicyChecker} in the existing test suite.</p>
 *
 * <p>Because {@link PolicyBriefService} obtains the generator via the singleton
 * {@link PolicyBriefGeneratorFactory}, these tests use a reflective approach
 * to inject the fake. The factory's {@code reset()} method is called in tearDown
 * to restore a clean state.</p>
 */
public class PolicyBriefServiceTest {

    private static final String NO_SEARCH_RESULTS_MARKER = "POLICY_BRIEF_NO_SEARCH_RESULTS:";

    /**
     * Tool definitions JSON array that includes the required {@code search_policy_docset} tool
     * along with a sample step tool. The service's {@code splitToolDefinitions} method requires
     * the search tool to be present.
     */
    private static final String TOOL_DEFINITIONS = """
            [{"name":"refundTool","description":"Process refunds"},{"name":"search_policy_docset","description":"Search policy documents"}]""";

    // =========================================================================
    // Fake PolicyBriefGenerator
    // =========================================================================

    /**
     * A controllable fake of {@link PolicyBriefGenerator} that returns preconfigured
     * responses in sequence. Tracks call count and arguments for verification.
     */
    static class FakePolicyBriefGenerator implements PolicyBriefGenerator {

        private final List<String> responses = new ArrayList<>();
        private final AtomicInteger callCount = new AtomicInteger(0);
        private final List<String> capturedUserInputs = Collections.synchronizedList(new ArrayList<>());
        private final List<String> capturedRagResults = Collections.synchronizedList(new ArrayList<>());
        private RuntimeException exceptionToThrow;

        FakePolicyBriefGenerator withResponse(String jsonResponse) {

            responses.add(jsonResponse);
            return this;
        }

        FakePolicyBriefGenerator withException(RuntimeException exception) {

            this.exceptionToThrow = exception;
            return this;
        }

        int getCallCount() {

            return callCount.get();
        }

        List<String> getCapturedUserInputs() {

            return List.copyOf(capturedUserInputs);
        }

        List<String> getCapturedRagResults() {

            return List.copyOf(capturedRagResults);
        }

        @Override
        public String generateOrRefine(String sessionId, String ragResults,
                                        String intent, String toolDefinitions,
                                        String searchToolDefinition, String userInput) {

            int idx = callCount.getAndIncrement();
            capturedUserInputs.add(userInput);
            capturedRagResults.add(ragResults);

            if (exceptionToThrow != null) {

                throw exceptionToThrow;
            }

            if (idx < responses.size()) {

                return responses.get(idx);
            }

            // Default: return a valid AWAITING_INPUT response
            return """
                    {"status":"AWAITING_INPUT","operations":[{"op":"replace_brief","replacementRules":["default rule"]}],"message":"default message"}
                    """;
        }
    }

    // =========================================================================
    // Stub Invoker for PolicyBriefService constructor
    // =========================================================================

    /**
     * Minimal stub Invoker that provides empty sibling invokers.
     * The RAG path in PolicyBriefService will gracefully fall back when
     * no AIQA invoker is found.
     */
    static class StubInvoker implements Invoker {

        private final Map<String, Invoker> siblings;

        StubInvoker() {

            this.siblings = Map.of();
        }

        StubInvoker(Map<String, Invoker> siblings) {

            this.siblings = siblings;
        }

        @Override
        public Map<String, Invoker> getInvokers() {

            return siblings;
        }

        @Override public String getInvokerId() { return "stub-invoker"; }
        @Override public String getInvokerName() { return "Stub Invoker"; }
        @Override public String getWorkspaceId() { return "ws-test"; }
        @Override public Extension getExtension() { return null; }
        @Override public boolean isRemoved() { return false; }
        @Override public RoutingInfo getRoutingInfo() { return null; }
        @Override public RequestAuthenticator getRequestAuthenticator() { return null; }
        @Override public Map<String, Object> getAttributes() { return Map.of(); }
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

    // =========================================================================
    // Test setup and teardown
    // =========================================================================

    private PolicyBriefService service;
    private FakePolicyBriefGenerator fakeGenerator;

    @BeforeEach
    void setUp() {

        // Reset the factory singleton so we can inject our fake
        PolicyBriefGeneratorFactory.reset();
        fakeGenerator = new FakePolicyBriefGenerator();

        // Inject the fake generator into the factory singleton via reflection.
        // The factory stores the instance in a static volatile field.
        injectFakeGenerator(fakeGenerator);

        service = new PolicyBriefService(new StubInvoker());
    }

    @AfterEach
    void tearDown() {

        PolicyBriefGeneratorFactory.reset();
    }

    /**
     * Injects a fake PolicyBriefGenerator into the factory singleton.
     * This avoids the need to create a real ChatLanguageModel.
     */
    private static void injectFakeGenerator(PolicyBriefGenerator fake) {

        try {

            var instanceField = PolicyBriefGeneratorFactory.class.getDeclaredField("instance");
            instanceField.setAccessible(true);
            instanceField.set(null, fake);

            // Also need to set chatMemoryMap to non-null so cleanupMemory works
            var memoryMapField = PolicyBriefGeneratorFactory.class.getDeclaredField("chatMemoryMap");
            memoryMapField.setAccessible(true);
            memoryMapField.set(null, new ConcurrentHashMap<>());

        } catch (Exception e) {

            throw new RuntimeException("Failed to inject fake generator into factory", e);
        }
    }

    // =========================================================================
    // startGeneration
    // =========================================================================

    @Nested
    @DisplayName("startGeneration")
    class StartGenerationTests {

        @Test
        @DisplayName("Should return a non-null jobId and store job in PENDING state initially")
        void testStartGenerationReturnsJobId() {

            String jobId = service.startGeneration("Process refunds", TOOL_DEFINITIONS);

            assertNotNull(jobId, "startGeneration should return a non-null jobId");
            assertFalse(jobId.isBlank(), "jobId should not be blank");

            // The job should exist in the store
            PolicyBriefJob job = service.getResult(jobId);
            assertNotNull(job, "Job should be retrievable immediately after start");
        }

        @Test
        @DisplayName("Should transition job to AWAITING_INPUT after async LLM completes with valid JSON")
        void testJobTransitionsToAwaitingInput() throws Exception {

            fakeGenerator.withResponse("""
                    {"status":"AWAITING_INPUT","operations":[{"op":"replace_brief","replacementRules":["No PII exposure","All writes logged"]}],"message":"Here is your draft."}
                    """);

            String jobId = service.startGeneration("Process refunds", TOOL_DEFINITIONS);

            // Wait for the virtual thread to complete
            PolicyBriefJob job = pollForStatus(jobId, PolicyBriefJob.Status.AWAITING_INPUT, 5000);

            assertNotNull(job, "Job should be found after generation");
            assertEquals(PolicyBriefJob.Status.AWAITING_INPUT, job.getStatus());
            assertEquals(List.of("No PII exposure", "All writes logged"), job.getPolicyBrief());
            assertEquals("Here is your draft.", job.getMessage());
            assertNull(job.getErrorMessage(), "No error should be set on success");
        }

        @Test
        @DisplayName("Should transition job to FAILED when LLM throws an exception")
        void testJobTransitionsToFailedOnLlmError() throws Exception {

            fakeGenerator.withException(new RuntimeException("Connection refused"));

            String jobId = service.startGeneration("Process refunds", TOOL_DEFINITIONS);

            PolicyBriefJob job = pollForStatus(jobId, PolicyBriefJob.Status.FAILED, 5000);

            assertNotNull(job, "Job should be found after generation failure");
            assertEquals(PolicyBriefJob.Status.FAILED, job.getStatus());
            assertNotNull(job.getErrorMessage(), "Error message should be set on failure");
            assertTrue(job.getErrorMessage().contains("Connection refused"),
                    "Error message should contain the exception detail");
        }

        @Test
        @DisplayName("Should handle missing AIQA invoker gracefully and still produce brief")
        void testMissingAiqaInvokerFallsBackGracefully() throws Exception {

            fakeGenerator.withResponse("""
                    {"status":"AWAITING_INPUT","operations":[{"op":"replace_brief","replacementRules":["best practice rule"]}],"message":"Generated from general practices."}
                    """);

            // aiqaInvokerId is null -- RAG will fall back
            String jobId = service.startGeneration("Process refunds", TOOL_DEFINITIONS);

            PolicyBriefJob job = pollForStatus(jobId, PolicyBriefJob.Status.AWAITING_INPUT, 5000);

            assertEquals(PolicyBriefJob.Status.AWAITING_INPUT, job.getStatus());
            assertEquals(1, job.getPolicyBrief().size());
        }

        @Test
        @DisplayName("Should handle blank policyDocset gracefully")
        void testBlankPolicyDocsetFallsBack() throws Exception {

            fakeGenerator.withResponse("""
                    {"status":"AWAITING_INPUT","operations":[{"op":"replace_brief","replacementRules":["general rule"]}],"message":"No docset specified."}
                    """);

            String jobId = service.startGeneration("Process refunds", TOOL_DEFINITIONS);

            PolicyBriefJob job = pollForStatus(jobId, PolicyBriefJob.Status.AWAITING_INPUT, 5000);
            assertEquals(PolicyBriefJob.Status.AWAITING_INPUT, job.getStatus());
        }
    }

    // =========================================================================
    // submitFeedback
    // =========================================================================

    @Nested
    @DisplayName("submitFeedback")
    class SubmitFeedbackTests {

        @Test
        @DisplayName("Should set job to PENDING then transition to AWAITING_INPUT with updated brief")
        void testFeedbackUpdatesJobState() throws Exception {

            // First response: initial generation
            fakeGenerator
                    .withResponse("""
                            {"status":"AWAITING_INPUT","operations":[{"op":"replace_brief","replacementRules":["Rule 1"]}],"message":"Draft ready."}
                            """)
                    // Second response: after feedback
                    .withResponse("""
                            {"status":"AWAITING_INPUT","operations":[
                              {"op":"update_rule","ruleId":"R1","text":"Rule 1 revised"},
                              {"op":"add_rule","afterRuleId":"R1","text":"Rule 2"}
                            ],"message":"Updated brief."}
                            """);

            String jobId = service.startGeneration("Process refunds", TOOL_DEFINITIONS);
            pollForStatus(jobId, PolicyBriefJob.Status.AWAITING_INPUT, 5000);

            // Submit feedback
            service.submitFeedback(jobId, "Please add a rule about data retention");

            PolicyBriefJob job = pollForStatus(jobId, PolicyBriefJob.Status.AWAITING_INPUT, 5000);

            assertEquals(List.of("Rule 1 revised", "Rule 2"), job.getPolicyBrief());
            assertEquals("Updated brief.", job.getMessage());
            assertEquals(2, fakeGenerator.getCallCount(), "LLM should be called twice (initial + feedback)");
        }

        @Test
        @DisplayName("Should transition to COMPLETE when author confirms")
        void testConfirmationCompletesJob() throws Exception {

            fakeGenerator
                    .withResponse("""
                            {"status":"AWAITING_INPUT","operations":[{"op":"replace_brief","replacementRules":["Rule 1"]}],"message":"Draft ready."}
                            """)
                    .withResponse("""
                            {"status":"COMPLETE","message":"Brief finalized."}
                            """);

            String jobId = service.startGeneration("Process refunds", TOOL_DEFINITIONS);
            pollForStatus(jobId, PolicyBriefJob.Status.AWAITING_INPUT, 5000);

            service.submitFeedback(jobId, "confirm");

            PolicyBriefJob job = pollForStatus(jobId, PolicyBriefJob.Status.COMPLETE, 5000);

            assertEquals(PolicyBriefJob.Status.COMPLETE, job.getStatus());
            assertEquals("Brief finalized.", job.getMessage());
            assertEquals(List.of("Rule 1"), job.getPolicyBrief(),
                    "COMPLETE without operations should preserve the existing brief");
        }

        @Test
        @DisplayName("Should use replace_brief when tool results produce the first cold-start draft")
        void testColdStartToolResultsReturnReplaceBrief() throws Exception {

            fakeGenerator
                    .withResponse("""
                            {"status":"EXECUTE_TOOL","toolName":"search_policy_docset","arguments":{"Question":"refund compliance"}}
                            """)
                    .withResponse("""
                            {"status":"AWAITING_INPUT","operations":[{"op":"replace_brief","replacementRules":["Rule 1","Rule 2"]}],"message":"Draft ready from search."}
                            """);

            String jobId = service.startGeneration("Process refunds", TOOL_DEFINITIONS);
            pollForStatus(jobId, PolicyBriefJob.Status.EXECUTE_TOOL, 5000);

            service.submitFeedback(jobId, "Search results: refund rules and retention rules");

            PolicyBriefJob job = pollForStatus(jobId, PolicyBriefJob.Status.AWAITING_INPUT, 5000);

            assertEquals(List.of("Rule 1", "Rule 2"), job.getPolicyBrief());
            assertEquals("Draft ready from search.", job.getMessage());
            assertEquals(2, fakeGenerator.getCallCount(), "Initial tool request plus tool-results refinement");
        }

        @Test
        @DisplayName("Should normalize leaked rule prefixes in replace_brief operations")
        void testReplaceBriefNormalizesLeakedPrefixes() throws Exception {

            fakeGenerator.withResponse("""
                    {"status":"AWAITING_INPUT","operations":[{"op":"replace_brief","replacementRules":["R1. Start with customer verification","R2: Log all approval decisions"]}],"message":"Here is your draft."}
                    """);

            String jobId = service.startGeneration("Process refunds", TOOL_DEFINITIONS);
            PolicyBriefJob job = pollForStatus(jobId, PolicyBriefJob.Status.AWAITING_INPUT, 5000);

            assertEquals(List.of("Start with customer verification", "Log all approval decisions"),
                    job.getPolicyBrief());
            assertEquals(List.of("R1", "R2"),
                    job.getPolicyBriefRules().stream().map(PolicyBriefRule::id).toList());
        }

        @Test
        @DisplayName("Should preserve non-prefixed text and later R1 mentions in replace_brief operations")
        void testReplaceBriefLeavesPlainTextAndLaterMentionsUnchanged() throws Exception {

            fakeGenerator.withResponse("""
                    {"status":"AWAITING_INPUT","operations":[{"op":"replace_brief","replacementRules":["No PII exposure","Escalate using rule R1 if needed"]}],"message":"Here is your draft."}
                    """);

            String jobId = service.startGeneration("Process refunds", TOOL_DEFINITIONS);
            PolicyBriefJob job = pollForStatus(jobId, PolicyBriefJob.Status.AWAITING_INPUT, 5000);

            assertEquals(List.of("No PII exposure", "Escalate using rule R1 if needed"),
                    job.getPolicyBrief());
        }

        @Test
        @DisplayName("Should normalize leaked rule prefixes in add_rule operations")
        void testAddRuleNormalizesLeakedPrefix() throws Exception {

            fakeGenerator
                    .withResponse("""
                            {"status":"AWAITING_INPUT","operations":[{"op":"replace_brief","replacementRules":["Rule 1","Rule 2"]}],"message":"Draft ready."}
                            """)
                    .withResponse("""
                            {"status":"AWAITING_INPUT","operations":[{"op":"add_rule","text":"R3) New rule"}],"message":"Added a rule."}
                            """);

            String jobId = service.startGeneration("Process refunds", TOOL_DEFINITIONS);
            pollForStatus(jobId, PolicyBriefJob.Status.AWAITING_INPUT, 5000);

            service.submitFeedback(jobId, "Add another rule");

            PolicyBriefJob job = pollForStatus(jobId, PolicyBriefJob.Status.AWAITING_INPUT, 5000);

            assertEquals(List.of("Rule 1", "Rule 2", "New rule"), job.getPolicyBrief());
            assertEquals(List.of("R1", "R2", "R3"),
                    job.getPolicyBriefRules().stream().map(PolicyBriefRule::id).toList());
        }

        @Test
        @DisplayName("Should normalize leaked update text while preserving the canonical rule ID")
        void testUpdateRuleNormalizesLeakedPrefixAndPreservesRuleId() throws Exception {

            fakeGenerator
                    .withResponse("""
                            {"status":"AWAITING_INPUT","operations":[{"op":"replace_brief","replacementRules":["Rule 1","Rule 2","Rule 3","Rule 4"]}],"message":"Draft ready."}
                            """)
                    .withResponse("""
                            {"status":"AWAITING_INPUT","operations":[{"op":"update_rule","ruleId":"R4","text":"R4 - Revised rule"}],"message":"Updated."}
                            """);

            String jobId = service.startGeneration("Process refunds", TOOL_DEFINITIONS);
            pollForStatus(jobId, PolicyBriefJob.Status.AWAITING_INPUT, 5000);

            service.submitFeedback(jobId, "Revise rule 4");

            PolicyBriefJob job = pollForStatus(jobId, PolicyBriefJob.Status.AWAITING_INPUT, 5000);

            assertEquals("Revised rule", job.getPolicyBriefRules().get(3).text());
            assertEquals("R4", job.getPolicyBriefRules().get(3).id());
        }

        @Test
        @DisplayName("Should throw IllegalArgumentException for unknown jobId")
        void testFeedbackForUnknownJobThrows() {

            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> service.submitFeedback("nonexistent-id", "some feedback"),
                    "submitFeedback for unknown jobId should throw"
            );
            assertTrue(exception.getMessage().contains("nonexistent-id"),
                    "Exception message should contain the unknown jobId");
        }

        @Test
        @DisplayName("Should transition to FAILED when LLM throws during refinement")
        void testRefinementFailureTransitionsToFailed() throws Exception {

            // Initial generation succeeds
            fakeGenerator.withResponse("""
                    {"status":"AWAITING_INPUT","operations":[{"op":"replace_brief","replacementRules":["Rule 1"]}],"message":"Draft ready."}
                    """);

            String jobId = service.startGeneration("Process refunds", TOOL_DEFINITIONS);
            pollForStatus(jobId, PolicyBriefJob.Status.AWAITING_INPUT, 5000);

            // Now make the generator throw on the next call
            fakeGenerator.withException(new RuntimeException("Rate limit exceeded"));

            service.submitFeedback(jobId, "revise this");

            PolicyBriefJob job = pollForStatus(jobId, PolicyBriefJob.Status.FAILED, 5000);

            assertEquals(PolicyBriefJob.Status.FAILED, job.getStatus());
            assertTrue(job.getErrorMessage().contains("Rate limit exceeded"));
        }

        @Test
        @DisplayName("Should support multiple feedback rounds")
        void testMultipleFeedbackRounds() throws Exception {

            fakeGenerator
                    .withResponse("""
                            {"status":"AWAITING_INPUT","operations":[{"op":"replace_brief","replacementRules":["R1"]}],"message":"Round 1"}
                            """)
                    .withResponse("""
                            {"status":"AWAITING_INPUT","operations":[
                              {"op":"add_rule","afterRuleId":"R1","text":"R2"}
                            ],"message":"Round 2"}
                            """)
                    .withResponse("""
                            {"status":"AWAITING_INPUT","operations":[
                              {"op":"add_rule","afterRuleId":"R2","text":"R3"}
                            ],"message":"Round 3"}
                            """)
                    .withResponse("""
                            {"status":"COMPLETE","message":"Done"}
                            """);

            String jobId = service.startGeneration("intent", TOOL_DEFINITIONS);
            pollForStatus(jobId, PolicyBriefJob.Status.AWAITING_INPUT, 5000);

            service.submitFeedback(jobId, "Add rule 2");
            pollForStatus(jobId, PolicyBriefJob.Status.AWAITING_INPUT, 5000);

            service.submitFeedback(jobId, "Add rule 3");
            pollForStatus(jobId, PolicyBriefJob.Status.AWAITING_INPUT, 5000);

            service.submitFeedback(jobId, "confirm");
            PolicyBriefJob job = pollForStatus(jobId, PolicyBriefJob.Status.COMPLETE, 5000);

            assertEquals(PolicyBriefJob.Status.COMPLETE, job.getStatus());
            assertEquals(3, job.getPolicyBrief().size());
            assertEquals(4, fakeGenerator.getCallCount());
        }
    }

    // =========================================================================
    // getResult
    // =========================================================================

    @Nested
    @DisplayName("getResult")
    class GetResultTests {

        @Test
        @DisplayName("Should return null for unknown jobId")
        void testGetResultReturnsNullForUnknownId() {

            PolicyBriefJob result = service.getResult("nonexistent-job");

            assertNull(result, "getResult should return null for unknown jobId");
        }

        @Test
        @DisplayName("Should return job with current state")
        void testGetResultReturnsCurrentState() throws Exception {

            fakeGenerator.withResponse("""
                    {"status":"AWAITING_INPUT","operations":[{"op":"replace_brief","replacementRules":["Rule 1"]}],"message":"Ready."}
                    """);

            String jobId = service.startGeneration("intent", TOOL_DEFINITIONS);
            pollForStatus(jobId, PolicyBriefJob.Status.AWAITING_INPUT, 5000);

            PolicyBriefJob result = service.getResult(jobId);

            assertNotNull(result);
            assertEquals(jobId, result.getJobId());
            assertEquals(PolicyBriefJob.Status.AWAITING_INPUT, result.getStatus());
        }
    }

    // =========================================================================
    // LLM response parsing (exercised via lifecycle)
    // =========================================================================

    @Nested
    @DisplayName("LLM Response Parsing")
    class LlmResponseParsingTests {

        @Test
        @DisplayName("Should parse valid JSON response correctly")
        void testValidJsonParsing() throws Exception {

            fakeGenerator.withResponse("""
                    {"status":"AWAITING_INPUT","operations":[{"op":"replace_brief","replacementRules":["Rule A","Rule B"]}],"message":"Two rules extracted."}
                    """);

            String jobId = service.startGeneration("intent", TOOL_DEFINITIONS);
            PolicyBriefJob job = pollForStatus(jobId, PolicyBriefJob.Status.AWAITING_INPUT, 5000);

            assertEquals(List.of("Rule A", "Rule B"), job.getPolicyBrief());
            assertEquals("Two rules extracted.", job.getMessage());
        }

        @Test
        @DisplayName("Should handle markdown-wrapped JSON (```json ... ```)")
        void testMarkdownWrappedJson() throws Exception {

            fakeGenerator.withResponse("""
                    ```json
                    {"status":"AWAITING_INPUT","operations":[{"op":"replace_brief","replacementRules":["Rule 1"]}],"message":"Wrapped."}
                    ```""");

            String jobId = service.startGeneration("intent", TOOL_DEFINITIONS);
            PolicyBriefJob job = pollForStatus(jobId, PolicyBriefJob.Status.AWAITING_INPUT, 5000);

            assertEquals(PolicyBriefJob.Status.AWAITING_INPUT, job.getStatus());
            assertEquals(List.of("Rule 1"), job.getPolicyBrief());
        }

        @Test
        @DisplayName("Should handle markdown-wrapped JSON without language tag (``` ... ```)")
        void testMarkdownWrappedJsonNoLanguageTag() throws Exception {

            fakeGenerator.withResponse("""
                    ```
                    {"status":"COMPLETE","operations":[{"op":"replace_brief","replacementRules":["Final rule"]}],"message":"Done."}
                    ```""");

            String jobId = service.startGeneration("intent", TOOL_DEFINITIONS);
            PolicyBriefJob job = pollForStatus(jobId, PolicyBriefJob.Status.COMPLETE, 5000);

            assertEquals(PolicyBriefJob.Status.COMPLETE, job.getStatus());
            assertEquals(List.of("Final rule"), job.getPolicyBrief());
        }

        @Test
        @DisplayName("Should self-correct invalid JSON into an operations-only response")
        void testInvalidJsonTriggersSelfCorrection() throws Exception {

            fakeGenerator.withResponse("This is not valid JSON at all");

            String jobId = service.startGeneration("intent", TOOL_DEFINITIONS);
            PolicyBriefJob job = pollForStatus(jobId, PolicyBriefJob.Status.AWAITING_INPUT, 5000);

            assertEquals(PolicyBriefJob.Status.AWAITING_INPUT, job.getStatus());
            assertEquals(List.of("default rule"), job.getPolicyBrief(),
                    "The retry should recover by returning the default operations-only draft");
            assertEquals("default message", job.getMessage());
            assertNull(job.getErrorMessage(), "Successful self-correction should clear validation errors");
            assertEquals(2, fakeGenerator.getCallCount(),
                    "Invalid JSON should trigger one self-correction retry");
        }

        @Test
        @DisplayName("Should treat unknown status values as AWAITING_INPUT")
        void testUnknownStatusDefaultsToAwaitingInput() throws Exception {

            fakeGenerator.withResponse("""
                    {"status":"SOME_UNKNOWN_STATUS","operations":[{"op":"replace_brief","replacementRules":["Rule"]}],"message":"Hello"}
                    """);

            String jobId = service.startGeneration("intent", TOOL_DEFINITIONS);
            PolicyBriefJob job = pollForStatus(jobId, PolicyBriefJob.Status.AWAITING_INPUT, 5000);

            assertEquals(PolicyBriefJob.Status.AWAITING_INPUT, job.getStatus(),
                    "Unknown status should default to AWAITING_INPUT");
        }

        @Test
        @DisplayName("Should handle COMPLETE status case-insensitively")
        void testCompleteStatusCaseInsensitive() throws Exception {

            fakeGenerator.withResponse("""
                    {"status":"complete","operations":[{"op":"replace_brief","replacementRules":["Rule"]}],"message":"Finalized"}
                    """);

            String jobId = service.startGeneration("intent", TOOL_DEFINITIONS);
            PolicyBriefJob job = pollForStatus(jobId, PolicyBriefJob.Status.COMPLETE, 5000);

            assertEquals(PolicyBriefJob.Status.COMPLETE, job.getStatus());
        }

        @Test
        @DisplayName("Should downgrade COMPLETE with no operations on an empty brief to AWAITING_INPUT")
        void testCompleteWithoutOperationsOnEmptyBriefDowngrades() throws Exception {

            fakeGenerator.withResponse("""
                    {"status":"COMPLETE","message":"Finalized"}
                    """);

            String jobId = service.startGeneration("intent", TOOL_DEFINITIONS);
            PolicyBriefJob job = pollForStatus(jobId, PolicyBriefJob.Status.AWAITING_INPUT, 5000);

            assertNotNull(job.getPolicyBrief(), "policyBrief should not be null");
            assertTrue(job.getPolicyBrief().isEmpty(),
                    "Empty-brief COMPLETE should keep the brief empty");
            assertEquals("I do not have a draft yet. Please provide a replacement draft using replace_brief or search for additional policy documents.",
                    job.getMessage());
        }

        @Test
        @DisplayName("Should handle missing message field in JSON gracefully")
        void testMissingMessageFieldKeepsNull() throws Exception {

            fakeGenerator.withResponse("""
                    {"status":"AWAITING_INPUT","operations":[{"op":"replace_brief","replacementRules":["Rule 1"]}]}
                    """);

            String jobId = service.startGeneration("intent", TOOL_DEFINITIONS);
            PolicyBriefJob job = pollForStatus(jobId, PolicyBriefJob.Status.AWAITING_INPUT, 5000);

            assertEquals(List.of("Rule 1"), job.getPolicyBrief());
            // message was not in the JSON, so it stays null (or previous value)
        }

        @Test
        @DisplayName("Should apply targeted refinement operations without rewriting unrelated rules")
        void testRefinementOperationsOnlyMutateTargetedRules() throws Exception {

            fakeGenerator
                    .withResponse("""
                            {"status":"AWAITING_INPUT","operations":[{"op":"replace_brief","replacementRules":["Rule A","Rule B","Rule C"]}],"message":"Initial"}
                            """)
                    .withResponse("""
                            {"status":"AWAITING_INPUT","operations":[
                              {"op":"update_rule","ruleId":"R2","text":"Rule B softened"},
                              {"op":"add_rule","afterRuleId":"R2","text":"Rule B.1"},
                              {"op":"delete_rule","ruleId":"R3"}
                            ],"message":"Updated"}
                            """);

            String jobId = service.startGeneration("intent", TOOL_DEFINITIONS);
            pollForStatus(jobId, PolicyBriefJob.Status.AWAITING_INPUT, 5000);

            service.submitFeedback(jobId, "Lighten rule B, add B.1, remove C");
            PolicyBriefJob job = pollForStatus(jobId, PolicyBriefJob.Status.AWAITING_INPUT, 5000);

            assertEquals(List.of("Rule A", "Rule B softened", "Rule B.1"), job.getPolicyBrief());
        }

        @Test
        @DisplayName("Should request one refinement self-correction on invalid ops and keep brief unchanged on repeated failure")
        void testInvalidRefinementOpsLeaveBriefUnchanged() throws Exception {

            fakeGenerator
                    .withResponse("""
                            {"status":"AWAITING_INPUT","operations":[{"op":"replace_brief","replacementRules":["Rule A","Rule B"]}],"message":"Initial"}
                            """)
                    .withResponse("""
                            {"status":"AWAITING_INPUT","operations":[
                              {"op":"update_rule","ruleId":"R999","text":"Broken"}
                            ],"message":"Broken update"}
                            """)
                    .withResponse("""
                            {"status":"AWAITING_INPUT","operations":[
                              {"op":"update_rule","ruleId":"R999","text":"Still broken"}
                            ],"message":"Still broken"}
                            """);

            String jobId = service.startGeneration("intent", TOOL_DEFINITIONS);
            pollForStatus(jobId, PolicyBriefJob.Status.AWAITING_INPUT, 5000);

            service.submitFeedback(jobId, "Update missing rule");
            PolicyBriefJob job = pollForStatus(jobId, PolicyBriefJob.Status.AWAITING_INPUT, 5000);

            assertEquals(List.of("Rule A", "Rule B"), job.getPolicyBrief(),
                    "Invalid refinement ops must not mutate the stored brief");
            assertNotNull(job.getErrorMessage(), "A validation error should be recorded");
            assertTrue(job.getErrorMessage().contains("R999"));
            assertEquals(3, fakeGenerator.getCallCount(), "Initial call + invalid refinement + self-correction");
        }

        @Test
        @DisplayName("Should allow explicit replace_brief for broad rewrites")
        void testReplaceBriefOperation() throws Exception {

            fakeGenerator
                    .withResponse("""
                            {"status":"AWAITING_INPUT","operations":[{"op":"replace_brief","replacementRules":["Rule A","Rule B"]}],"message":"Initial"}
                            """)
                    .withResponse("""
                            {"status":"AWAITING_INPUT","operations":[
                              {"op":"replace_brief","replacementRules":["New Rule 1","New Rule 2","New Rule 3"]}
                            ],"message":"Broad rewrite applied"}
                            """);

            String jobId = service.startGeneration("intent", TOOL_DEFINITIONS);
            pollForStatus(jobId, PolicyBriefJob.Status.AWAITING_INPUT, 5000);

            service.submitFeedback(jobId, "Rethink the whole brief");
            PolicyBriefJob job = pollForStatus(jobId, PolicyBriefJob.Status.AWAITING_INPUT, 5000);

            assertEquals(List.of("New Rule 1", "New Rule 2", "New Rule 3"), job.getPolicyBrief());
        }
    }

    // =========================================================================
    // Concurrent job isolation
    // =========================================================================

    @Nested
    @DisplayName("Concurrent Job Isolation")
    class ConcurrentJobTests {

        @Test
        @DisplayName("Should handle multiple concurrent jobs independently")
        void testMultipleConcurrentJobs() throws Exception {

            // The fake generator returns default responses after exhausting the queue
            fakeGenerator
                    .withResponse("""
                            {"status":"AWAITING_INPUT","operations":[{"op":"replace_brief","replacementRules":["Job1 Rule"]}],"message":"Job 1"}
                            """)
                    .withResponse("""
                            {"status":"AWAITING_INPUT","operations":[{"op":"replace_brief","replacementRules":["Job2 Rule"]}],"message":"Job 2"}
                            """);

            String jobId1 = service.startGeneration("intent-1", TOOL_DEFINITIONS);
            String jobId2 = service.startGeneration("intent-2", TOOL_DEFINITIONS);

            assertNotEquals(jobId1, jobId2, "Each job should have a unique ID");

            // Wait for both to complete
            PolicyBriefJob job1 = pollForNonPending(jobId1, 5000);
            PolicyBriefJob job2 = pollForNonPending(jobId2, 5000);

            assertNotNull(job1);
            assertNotNull(job2);

            // Both should have transitioned out of PENDING
            assertNotEquals(PolicyBriefJob.Status.PENDING, job1.getStatus());
            assertNotEquals(PolicyBriefJob.Status.PENDING, job2.getStatus());
        }
    }

    // =========================================================================
    // Seeded brief (refine saved brief later)
    // =========================================================================

    @Nested
    @DisplayName("Seeded Brief (Refine Saved Brief)")
    class SeededBriefTests {

        @Test
        @DisplayName("Seeded generate should go directly to AWAITING_INPUT with zero LLM calls and exact seed preserved")
        void testSeededHappyPath() throws Exception {

            List<String> seed = List.of("A", "B", "C");
            String jobId = service.startGeneration("Process refunds", TOOL_DEFINITIONS, seed);

            // Job should be AWAITING_INPUT immediately — no virtual thread, no LLM call
            PolicyBriefJob job = service.getResult(jobId);
            assertNotNull(job, "Job should exist immediately");
            assertEquals(PolicyBriefJob.Status.AWAITING_INPUT, job.getStatus(),
                    "Seeded job should be AWAITING_INPUT immediately without LLM call");
            assertEquals(seed, job.getPolicyBrief(),
                    "Seed rules should be preserved exactly as provided");
            assertTrue(job.isSeeded(), "Seeded flag should be true");
            assertEquals(0, fakeGenerator.getCallCount(),
                    "No LLM call should be made on seeded generate");
            assertNotNull(job.getMessage(), "Canned message should be set");
            assertTrue(job.getMessage().contains("3 rules"),
                    "Canned message should contain the rule count");
        }

        @Test
        @DisplayName("Seeded generate should normalize leaked leading rule prefixes only")
        void testSeededBriefNormalization() {

            String jobId = service.startGeneration(
                    "Process refunds",
                    TOOL_DEFINITIONS,
                    List.of("R1. Start with customer verification", "Escalate using rule R1 if needed"));

            PolicyBriefJob job = service.getResult(jobId);

            assertEquals(List.of("Start with customer verification", "Escalate using rule R1 if needed"),
                    job.getPolicyBrief());
            assertEquals(List.of("R1", "R2"),
                    job.getPolicyBriefRules().stream().map(PolicyBriefRule::id).toList());
            assertEquals(0, fakeGenerator.getCallCount(),
                    "Seed normalization should still avoid an LLM call");
        }

        @Test
        @DisplayName("Empty-list seed should behave identically to 2-arg cold-start path")
        void testEmptyListSeedBehavesAsColdStart() throws Exception {

            fakeGenerator.withResponse("""
                    {"status":"AWAITING_INPUT","operations":[{"op":"replace_brief","replacementRules":["generated rule"]}],"message":"Draft ready."}
                    """);

            String jobId = service.startGeneration("Process refunds", TOOL_DEFINITIONS, List.of());

            PolicyBriefJob job = pollForStatus(jobId, PolicyBriefJob.Status.AWAITING_INPUT, 5000);

            assertEquals(List.of("generated rule"), job.getPolicyBrief());

            // Verify cold-start ragResults were used
            List<String> ragResults = fakeGenerator.getCapturedRagResults();
            assertTrue(ragResults.getFirst().contains("No policy documents retrieved yet"),
                    "Cold-start ragResults should be used for empty seed");

            List<String> userInputs = fakeGenerator.getCapturedUserInputs();
            assertTrue(userInputs.getFirst().contains("Generate a policy brief"),
                    "Cold-start userInput should be used for empty seed");
        }

        @Test
        @DisplayName("Seeded job: first feedback triggers EXECUTE_TOOL, tool results flow normally")
        void testSeededJobWithExecuteTool() throws Exception {

            // First LLM call (seeded first feedback with "search") returns EXECUTE_TOOL
            fakeGenerator
                    .withResponse("""
                            {"status":"EXECUTE_TOOL","toolName":"search_policy_docset","arguments":{"Question":"compliance rules"}}
                            """)
                    .withResponse("""
                            {"status":"AWAITING_INPUT","operations":[
                              {"op":"add_rule","afterRuleId":"R3","text":"D"}
                            ],"message":"Found more rules."}
                            """);

            List<String> seed = List.of("A", "B", "C");
            String jobId = service.startGeneration("Process refunds", TOOL_DEFINITIONS, seed);

            // Seeded generate: immediate AWAITING_INPUT, no LLM call
            PolicyBriefJob job = service.getResult(jobId);
            assertEquals(PolicyBriefJob.Status.AWAITING_INPUT, job.getStatus());
            assertEquals(0, fakeGenerator.getCallCount());

            // Author asks to search — first feedback triggers LLM which returns EXECUTE_TOOL
            service.submitFeedback(jobId, "search for compliance rules");

            job = pollForStatus(jobId, PolicyBriefJob.Status.EXECUTE_TOOL, 5000);
            assertNotNull(job.getToolName(), "Tool name should be set");
            assertEquals("search_policy_docset", job.getToolName());
            assertEquals(1, fakeGenerator.getCallCount(), "One LLM call for the first feedback");

            // Submit tool results (platform does this)
            service.submitFeedback(jobId, "Here are some compliance documents...");

            job = pollForStatus(jobId, PolicyBriefJob.Status.AWAITING_INPUT, 5000);
            assertEquals(List.of("A", "B", "C", "D"), job.getPolicyBrief());
            assertEquals(2, fakeGenerator.getCallCount(), "Two LLM calls total");
        }

        @Test
        @DisplayName("Tool failure feedback returns EXECUTE_TOOL job to AWAITING_INPUT without LLM retry")
        void testToolFailureFeedbackRecoversToAwaitingInput() throws Exception {

            fakeGenerator.withResponse("""
                    {"status":"EXECUTE_TOOL","toolName":"search_policy_docset","arguments":{"Question":"compliance rules"}}
                    """);

            List<String> seed = List.of("A", "B", "C");
            String jobId = service.startGeneration("Process refunds", TOOL_DEFINITIONS, seed);

            service.submitFeedback(jobId, "search for compliance rules");

            PolicyBriefJob job = pollForStatus(jobId, PolicyBriefJob.Status.EXECUTE_TOOL, 5000);
            assertEquals(1, fakeGenerator.getCallCount(), "Only the initial EXECUTE_TOOL LLM call should run");

            String toolFailurePayload = """
                    POLICY_BRIEF_TOOL_FAILURE: search_policy_docset failed
                    jobId=%s
                    question=compliance rules
                    detail=AIQA search timed out
                    """.formatted(jobId);

            service.submitFeedback(jobId, toolFailurePayload);

            job = service.getResult(jobId);
            assertEquals(PolicyBriefJob.Status.AWAITING_INPUT, job.getStatus());
            assertEquals(List.of("A", "B", "C"), job.getPolicyBrief(),
                    "Existing brief should remain unchanged after tool failure");
            assertEquals(1, fakeGenerator.getCallCount(),
                    "Synthetic tool failure feedback should not invoke the LLM again");
            assertTrue(job.getMessage().contains("Policy document search failed"),
                    "User-facing message should explain the search failure");
            assertTrue(job.getErrorMessage().contains("AIQA search timed out"),
                    "Error message should surface the platform failure detail");
            assertNull(job.getToolName(), "Tool request should be cleared after recovery");
            assertNull(job.getToolArguments(), "Tool arguments should be cleared after recovery");
        }

        @Test
        @DisplayName("Zero-results feedback returns EXECUTE_TOOL job to AWAITING_INPUT without LLM retry")
        void testNoSearchResultsFeedbackRecoversToAwaitingInput() throws Exception {

            fakeGenerator.withResponse("""
                    {"status":"EXECUTE_TOOL","toolName":"search_policy_docset","arguments":{"Question":"compliance rules"}}
                    """);

            List<String> seed = List.of("A", "B", "C");
            String jobId = service.startGeneration("Process refunds", TOOL_DEFINITIONS, seed);

            service.submitFeedback(jobId, "search for compliance rules");

            PolicyBriefJob job = pollForStatus(jobId, PolicyBriefJob.Status.EXECUTE_TOOL, 5000);
            assertEquals(1, fakeGenerator.getCallCount(), "Only the initial EXECUTE_TOOL LLM call should run");

            String noSearchResultsPayload = """
                    %s search_policy_docset returned no policy documents
                    jobId=%s
                    question=compliance rules
                    detail=AIQA search succeeded but returned no usable policy documents for the configured docset
                    """.formatted(NO_SEARCH_RESULTS_MARKER, jobId);

            service.submitFeedback(jobId, noSearchResultsPayload);

            job = service.getResult(jobId);
            assertEquals(PolicyBriefJob.Status.AWAITING_INPUT, job.getStatus());
            assertEquals(List.of("A", "B", "C"), job.getPolicyBrief(),
                    "Existing brief should remain unchanged after zero-result search recovery");
            assertEquals(1, fakeGenerator.getCallCount(),
                    "Synthetic zero-results feedback should not invoke the LLM again");
            assertEquals("No policy documents were found in the configured docset for this search. " +
                            "The brief was not updated from policy-doc search results.",
                    job.getMessage());
            assertTrue(job.getErrorMessage().contains("no usable policy documents"),
                    "Error message should surface the platform zero-results detail");
            assertNull(job.getToolName(), "Tool request should be cleared after recovery");
            assertNull(job.getToolArguments(), "Tool arguments should be cleared after recovery");
        }

        @Test
        @DisplayName("Seeded job preserves seed verbatim — no LLM overwrite on generate")
        void testConcurrentPollReturnsSeedThenRefined() throws Exception {

            List<String> seed = List.of("A", "B");
            String jobId = service.startGeneration("intent", TOOL_DEFINITIONS, seed);

            // Immediate poll should return exact seed — no LLM called, no overwrite
            PolicyBriefJob immediate = service.getResult(jobId);
            assertEquals(PolicyBriefJob.Status.AWAITING_INPUT, immediate.getStatus(),
                    "Seeded job should be AWAITING_INPUT immediately");
            assertEquals(seed, immediate.getPolicyBrief(),
                    "Seed should be preserved verbatim — no LLM overwrite");
            assertEquals(0, fakeGenerator.getCallCount(),
                    "No LLM call on seeded generate");

            // Poll again after a small delay — seed should still be identical
            Thread.sleep(200);
            PolicyBriefJob delayed = service.getResult(jobId);
            assertEquals(seed, delayed.getPolicyBrief(),
                    "Seed should remain unchanged on subsequent polls");
        }

        @Test
        @DisplayName("Size guard should truncate seed with >200 rules to 200")
        void testSizeGuardTruncatesLargeRuleCount() throws Exception {

            // Build a seed with 500 rules
            List<String> largeSeed = new ArrayList<>();
            for (int i = 0; i < 500; i++) {

                largeSeed.add("Rule " + i);
            }

            String jobId = service.startGeneration("intent", TOOL_DEFINITIONS, largeSeed);

            // Immediate poll should return truncated seed (200 rules max) and AWAITING_INPUT
            PolicyBriefJob immediate = service.getResult(jobId);
            assertEquals(PolicyBriefJob.Status.AWAITING_INPUT, immediate.getStatus(),
                    "Seeded job should be AWAITING_INPUT immediately");
            assertTrue(immediate.getPolicyBrief().size() <= 200,
                    "Seed should be truncated to at most 200 rules, but was " + immediate.getPolicyBrief().size());
            assertEquals("Rule 0", immediate.getPolicyBrief().getFirst(),
                    "First rule should be preserved after truncation");
            assertTrue(immediate.isSeeded(), "Seeded flag should be true");
            assertEquals(0, fakeGenerator.getCallCount(),
                    "No LLM call on seeded generate (even when truncated)");
        }

        @Test
        @DisplayName("2-arg startGeneration should delegate to 3-arg with empty list (backward compat)")
        void testTwoArgDelegatesToThreeArg() throws Exception {

            fakeGenerator.withResponse("""
                    {"status":"AWAITING_INPUT","operations":[{"op":"replace_brief","replacementRules":["cold start rule"]}],"message":"Draft."}
                    """);

            // Use the original 2-arg method
            String jobId = service.startGeneration("intent", TOOL_DEFINITIONS);

            PolicyBriefJob job = pollForStatus(jobId, PolicyBriefJob.Status.AWAITING_INPUT, 5000);

            assertEquals(List.of("cold start rule"), job.getPolicyBrief());

            // Verify cold-start path was used
            List<String> ragResults = fakeGenerator.getCapturedRagResults();
            assertTrue(ragResults.getFirst().contains("No policy documents retrieved yet"),
                    "2-arg method should use cold-start path");
        }

        @Test
        @DisplayName("Cold-start zero-results returns AWAITING_INPUT with empty brief and no LLM retry")
        void testColdStartNoSearchResultsDoesNotSynthesizeBrief() throws Exception {

            fakeGenerator.withResponse("""
                    {"status":"EXECUTE_TOOL","toolName":"search_policy_docset","arguments":{"Question":"refund compliance"}}
                    """);

            String jobId = service.startGeneration("Process refunds", TOOL_DEFINITIONS);

            PolicyBriefJob job = pollForStatus(jobId, PolicyBriefJob.Status.EXECUTE_TOOL, 5000);
            assertEquals(1, fakeGenerator.getCallCount(), "Cold-start should perform only the initial EXECUTE_TOOL LLM call");

            String noSearchResultsPayload = """
                    %s search_policy_docset returned no policy documents
                    jobId=%s
                    question=refund compliance
                    detail=AIQA search succeeded but returned no usable policy documents for the configured docset
                    """.formatted(NO_SEARCH_RESULTS_MARKER, jobId);

            service.submitFeedback(jobId, noSearchResultsPayload);

            job = service.getResult(jobId);
            assertEquals(PolicyBriefJob.Status.AWAITING_INPUT, job.getStatus());
            assertTrue(job.getPolicyBrief().isEmpty(),
                    "Cold-start zero-results should preserve the empty brief instead of synthesizing one");
            assertEquals(1, fakeGenerator.getCallCount(),
                    "Zero-results recovery should not invoke the LLM again");
            assertTrue(job.getMessage().contains("No policy documents were found in the configured docset"),
                    "Recovery message should explain that no policy documents were found");
            assertTrue(job.getMessage().contains("brief was not updated"),
                    "Recovery message should explain that the brief was not updated");
            assertTrue(job.getErrorMessage().contains("configured docset"),
                    "Technical detail should be surfaced to the author");
        }

        @Test
        @DisplayName("Seeded first feedback should send canonical rule IDs plus tagged feedback")
        void testSeededFirstFeedbackUsesCanonicalRuleContext() throws Exception {

            fakeGenerator.withResponse("""
                    {"status":"AWAITING_INPUT","operations":[
                      {"op":"delete_rule","ruleId":"R2"}
                    ],"message":"Removed rule 2."}
                    """);

            List<String> seed = List.of("A", "B", "C");
            String jobId = service.startGeneration("Process refunds", TOOL_DEFINITIONS, seed);

            // Verify immediate AWAITING_INPUT, no LLM call
            assertEquals(PolicyBriefJob.Status.AWAITING_INPUT, service.getResult(jobId).getStatus());
            assertEquals(0, fakeGenerator.getCallCount());

            // Submit first feedback
            service.submitFeedback(jobId, "remove rule 2");
            pollForStatus(jobId, PolicyBriefJob.Status.AWAITING_INPUT, 5000);

            // Verify the prepended input format
            List<String> userInputs = fakeGenerator.getCapturedUserInputs();
            assertEquals(1, userInputs.size(), "Only one LLM call should have been made");
            String input = userInputs.getFirst();
            assertTrue(input.contains("CURRENT POLICY BRIEF WITH RULE IDS (3 rules)"),
                    "Should contain brief header with rule count");
            assertTrue(input.contains("- R1: A"),
                    "Should contain rule ID + rule text for A");
            assertTrue(input.contains("- R2: B"),
                    "Should contain rule ID + rule text for B");
            assertTrue(input.contains("- R3: C"),
                    "Should contain rule ID + rule text for C");
            assertTrue(input.contains("<current_policy_brief>"),
                    "Should contain structured current brief tags");
            assertTrue(input.contains("LATEST AUTHOR FEEDBACK:"),
                    "Should contain feedback label");
            assertTrue(input.contains("<feedback>\nremove rule 2\n</feedback>"),
                    "Should contain author feedback in XML tags");
        }

        @Test
        @DisplayName("Seeded first feedback should use suppression ragResults")
        void testSeededFirstFeedbackSuppressesRagResults() throws Exception {

            fakeGenerator.withResponse("""
                    {"status":"AWAITING_INPUT","operations":[
                      {"op":"delete_rule","ruleId":"R2"}
                    ],"message":"Done."}
                    """);

            List<String> seed = List.of("A", "B", "C");
            String jobId = service.startGeneration("Process refunds", TOOL_DEFINITIONS, seed);

            service.submitFeedback(jobId, "remove rule 2");
            pollForStatus(jobId, PolicyBriefJob.Status.AWAITING_INPUT, 5000);

            List<String> ragResults = fakeGenerator.getCapturedRagResults();
            assertEquals(1, ragResults.size());
            assertTrue(ragResults.getFirst().contains("Policy documents provided by the author"),
                    "ragResults should contain suppression text indicating policy docs are provided");
            assertTrue(ragResults.getFirst().contains("see user input below"),
                    "ragResults should redirect LLM to user input for brief content");
        }

        @Test
        @DisplayName("Second feedback on seeded job should use normal empty ragResults")
        void testSecondFeedbackUsesNormalRagResults() throws Exception {

            fakeGenerator
                    .withResponse("""
                            {"status":"AWAITING_INPUT","operations":[
                              {"op":"delete_rule","ruleId":"R2"}
                            ],"message":"Removed rule 2."}
                            """)
                    .withResponse("""
                            {"status":"AWAITING_INPUT","operations":[
                              {"op":"add_rule","afterRuleId":"R3","text":"D"}
                            ],"message":"Added rule D."}
                            """);

            List<String> seed = List.of("A", "B", "C");
            String jobId = service.startGeneration("Process refunds", TOOL_DEFINITIONS, seed);

            // First feedback (seeded)
            service.submitFeedback(jobId, "remove rule 2");
            pollForStatus(jobId, PolicyBriefJob.Status.AWAITING_INPUT, 5000);

            // Second feedback (normal)
            service.submitFeedback(jobId, "add rule D");
            pollForStatus(jobId, PolicyBriefJob.Status.AWAITING_INPUT, 5000);

            List<String> ragResults = fakeGenerator.getCapturedRagResults();
            assertEquals(2, ragResults.size());
            // First call: suppression ragResults
            assertFalse(ragResults.get(0).isEmpty(),
                    "First feedback ragResults should be non-empty (suppression)");
            // Second call: normal empty ragResults
            assertEquals("", ragResults.get(1),
                    "Second feedback ragResults should be empty (normal path)");
        }

        @Test
        @DisplayName("Seeded job with immediate COMPLETE on first feedback should clean up memory")
        void testSeededImmediateComplete() throws Exception {

            fakeGenerator.withResponse("""
                    {"status":"COMPLETE","message":"Brief confirmed."}
                    """);

            List<String> seed = List.of("A", "B", "C");
            String jobId = service.startGeneration("Process refunds", TOOL_DEFINITIONS, seed);

            service.submitFeedback(jobId, "looks good");

            PolicyBriefJob job = pollForStatus(jobId, PolicyBriefJob.Status.COMPLETE, 5000);
            assertEquals(PolicyBriefJob.Status.COMPLETE, job.getStatus());
            assertEquals(List.of("A", "B", "C"), job.getPolicyBrief());
            assertEquals(1, fakeGenerator.getCallCount());
        }

        @Test
        @DisplayName("Seeded path should fail fast with FAILED status when tool definitions are malformed")
        void testSeededEagerToolDefinitionValidation() {

            List<String> seed = List.of("A", "B");
            String jobId = service.startGeneration("intent", "not valid json", seed);

            PolicyBriefJob job = service.getResult(jobId);
            assertNotNull(job);
            assertEquals(PolicyBriefJob.Status.FAILED, job.getStatus(),
                    "Malformed tool definitions should fail fast on seeded path");
            assertNotNull(job.getErrorMessage());
            assertTrue(job.getErrorMessage().contains("Tool definition validation failed"),
                    "Error message should indicate tool definition validation failure");
        }

        @Test
        @DisplayName("Seeded flag should be false on cold-start jobs")
        void testSeededFlagFalseOnColdStart() throws Exception {

            fakeGenerator.withResponse("""
                    {"status":"AWAITING_INPUT","operations":[{"op":"replace_brief","replacementRules":["Rule 1"]}],"message":"Draft."}
                    """);

            String jobId = service.startGeneration("intent", TOOL_DEFINITIONS);

            PolicyBriefJob job = service.getResult(jobId);
            assertFalse(job.isSeeded(), "Cold-start job should have seeded=false");
        }

        @Test
        @DisplayName("Singular rule count in canned message for single-rule seed")
        void testSingularRuleCountMessage() {

            List<String> seed = List.of("Only rule");
            String jobId = service.startGeneration("intent", TOOL_DEFINITIONS, seed);

            PolicyBriefJob job = service.getResult(jobId);
            assertNotNull(job.getMessage());
            assertTrue(job.getMessage().contains("1 rule"),
                    "Message should use singular 'rule' for single-rule seed");
            assertFalse(job.getMessage().contains("1 rules"),
                    "Message should NOT say '1 rules'");
        }
    }

    // =========================================================================
    // Polling utilities
    // =========================================================================

    /**
     * Polls the job store until the job reaches the expected status or times out.
     * This accounts for the asynchronous virtual thread execution in PolicyBriefService.
     */
    private PolicyBriefJob pollForStatus(String jobId, PolicyBriefJob.Status expectedStatus,
                                          long timeoutMillis) throws InterruptedException {

        long deadline = System.currentTimeMillis() + timeoutMillis;

        while (System.currentTimeMillis() < deadline) {

            PolicyBriefJob job = service.getResult(jobId);
            if (job != null && job.getStatus() == expectedStatus) {

                return job;
            }
            Thread.sleep(50);
        }

        // Final attempt for assertion clarity
        PolicyBriefJob finalJob = service.getResult(jobId);
        fail("Timed out waiting for status " + expectedStatus +
                ". Current status: " + (finalJob != null ? finalJob.getStatus() : "null (job not found)"));
        return null;
    }

    /**
     * Polls until the job is no longer in PENDING status.
     */
    private PolicyBriefJob pollForNonPending(String jobId, long timeoutMillis) throws InterruptedException {

        long deadline = System.currentTimeMillis() + timeoutMillis;

        while (System.currentTimeMillis() < deadline) {

            PolicyBriefJob job = service.getResult(jobId);
            if (job != null && job.getStatus() != PolicyBriefJob.Status.PENDING) {

                return job;
            }
            Thread.sleep(50);
        }

        PolicyBriefJob finalJob = service.getResult(jobId);
        fail("Timed out waiting for job to leave PENDING. Current status: " +
                (finalJob != null ? finalJob.getStatus() : "null (job not found)"));
        return null;
    }
}
