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

package app.krista.extensions.hook.api;

import app.krista.extension.executor.*;
import app.krista.extension.authorization.RequestAuthenticator;
import app.krista.extension.event.EventSubscription;
import app.krista.extension.event.WaitForEventListener;
import app.krista.extension.request.ProtoRequest;
import app.krista.extension.request.ProtoResponse;
import app.krista.extension.request.RoutingInfo;
import app.krista.extensions.hook.policybrief.*;
import app.krista.extensions.hook.util.JsonMapper;
import app.krista.extensions.util.KeyValueStore;
import app.krista.model.entity.EntityType;
import org.junit.jupiter.api.*;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for the {@code generatePolicyBrief} catalog request on
 * {@link CatalogRequestHandler}, validating the cross-layer path:
 *
 * <pre>
 *     CatalogRequestHandler.generatePolicyBrief(intent, toolDefs, policyBriefJson)
 *         -> parsePolicyBrief(policyBriefJson)           // private helper
 *         -> PolicyBriefService.startGeneration(intent, toolDefs, parsedBrief)  // 3-arg overload
 * </pre>
 *
 * <p>This test class exercises the extension-level integration that the unit-level
 * {@link PolicyBriefServiceTest} cannot cover: the JSON parsing of the {@code "Policy Brief"}
 * catalog request field and its correct routing to the service layer.</p>
 *
 * <p>Uses the same {@link FakePolicyBriefGenerator} injection pattern established in
 * {@link PolicyBriefServiceTest}. No mocks — just fakes implementing real interfaces.</p>
 */
public class CatalogRequestHandlerPolicyBriefTest {

    private static final String TOOL_DEFINITIONS = """
            [{"name":"refundTool","description":"Process refunds"},{"name":"search_policy_docset","description":"Search policy documents"}]""";

    private CatalogRequestHandler handler;
    private FakePolicyBriefGenerator fakeGenerator;

    // =========================================================================
    // Fake infrastructure
    // =========================================================================

    /**
     * Controllable fake of {@link PolicyBriefGenerator}.
     * Identical to the one in {@link PolicyBriefServiceTest} — duplicated here to keep
     * this test self-contained (no cross-test-class dependency).
     */
    static class FakePolicyBriefGenerator implements PolicyBriefGenerator {

        private final List<String> responses = new ArrayList<>();
        private final AtomicInteger callCount = new AtomicInteger(0);
        private final List<String> capturedUserInputs = Collections.synchronizedList(new ArrayList<>());
        private final List<String> capturedRagResults = Collections.synchronizedList(new ArrayList<>());

        FakePolicyBriefGenerator withResponse(String jsonResponse) {

            responses.add(jsonResponse);
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

            if (idx < responses.size()) {

                return responses.get(idx);
            }

            return """
                    {"status":"AWAITING_INPUT","operations":[{"op":"replace_brief","replacementRules":["default rule"]}],"message":"default message"}
                    """;
        }
    }

    /**
     * Minimal stub Invoker.
     */
    static class StubInvoker implements Invoker {

        @Override public Map<String, Invoker> getInvokers() { return Map.of(); }
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

    /**
     * Minimal stub KeyValueStore that stores nothing (not needed for policy brief path).
     */
    static class StubKeyValueStore implements KeyValueStore {

        private final Map<String, Object> store = new HashMap<>();

        @Override public Object get(String key) { return store.get(key); }
        @Override public void remove(String key) { store.remove(key); }
        @Override public boolean contains(String key) { return store.containsKey(key); }
        @Override public void put(String key, Object value) { store.put(key, value); }
        @Override public boolean supports(Class<?> typeClass) { return true; }
        @Override public Set<String> getKeys() { return store.keySet(); }
        @Override public Iterator<Map.Entry<String, Object>> iterator() { return store.entrySet().iterator(); }
    }

    // =========================================================================
    // Setup / Teardown
    // =========================================================================

    @BeforeEach
    void setUp() {

        PolicyBriefGeneratorFactory.reset();
        fakeGenerator = new FakePolicyBriefGenerator();
        injectFakeGenerator(fakeGenerator);

        // Third arg (Workspace) is only used for telemetry identity, which AgenticRequestContext
        // resolves null-safely; null is fine for these policy-brief tests.
        handler = new CatalogRequestHandler(new StubInvoker(), new StubKeyValueStore(), null);
    }

    @AfterEach
    void tearDown() {

        PolicyBriefGeneratorFactory.reset();
    }

    private static void injectFakeGenerator(PolicyBriefGenerator fake) {

        try {

            var instanceField = PolicyBriefGeneratorFactory.class.getDeclaredField("instance");
            instanceField.setAccessible(true);
            instanceField.set(null, fake);

            var memoryMapField = PolicyBriefGeneratorFactory.class.getDeclaredField("chatMemoryMap");
            memoryMapField.setAccessible(true);
            memoryMapField.set(null, new ConcurrentHashMap<>());

        } catch (Exception e) {

            throw new RuntimeException("Failed to inject fake generator into factory", e);
        }
    }

    // =========================================================================
    // Tests: generatePolicyBrief with populated Policy Brief field
    // =========================================================================

    @Test
    @DisplayName("Should parse populated Policy Brief JSON and seed the service — no LLM call, immediate AWAITING_INPUT")
    void testPopulatedPolicyBriefFieldSeedsService() throws Exception {

        String policyBriefJson = "[\"No PII exposure\",\"All writes logged\",\"Encrypt at rest\"]";

        ExtensionResponse response = handler.generatePolicyBrief(
                "Process refunds", TOOL_DEFINITIONS, policyBriefJson);

        // Verify successful response with a jobId
        assertNotNull(response, "Response should not be null");
        assertEquals(ExtensionResponse.Result.SUCCESS, response.getResult(),
                "Response should indicate success");
        assertNotNull(response.getResponseValue(), "Response value map should not be null");
        String jobId = (String) response.getResponseValue().get("Job Id");
        assertNotNull(jobId, "Job Id should be present in the response");
        assertFalse(jobId.isBlank(), "Job Id should not be blank");

        // No LLM call should be made on seeded generate
        assertEquals(0, fakeGenerator.getCallCount(),
                "No LLM call should be made for seeded generate");

        // Job should be AWAITING_INPUT immediately with exact seed rules
        PolicyBriefService service = new PolicyBriefService(new StubInvoker());
        PolicyBriefJob job = service.getResult(jobId);
        assertNotNull(job, "Job should be retrievable immediately");
        assertEquals(PolicyBriefJob.Status.AWAITING_INPUT, job.getStatus(),
                "Seeded job should be AWAITING_INPUT immediately");
        assertEquals(List.of("No PII exposure", "All writes logged", "Encrypt at rest"),
                job.getPolicyBrief(),
                "Seed rules should be preserved exactly as parsed from JSON");
        assertTrue(job.isSeeded(), "Seeded flag should be true");
    }

    // =========================================================================
    // Tests: generatePolicyBrief with empty Policy Brief field
    // =========================================================================

    @Test
    @DisplayName("Should take cold-start path when Policy Brief field is empty array")
    void testEmptyArrayPolicyBriefTakesColdStartPath() throws Exception {

        fakeGenerator.withResponse("""
                {"status":"AWAITING_INPUT","operations":[{"op":"replace_brief","replacementRules":["generated"]}],"message":"Draft ready."}
                """);

        ExtensionResponse response = handler.generatePolicyBrief(
                "Process refunds", TOOL_DEFINITIONS, "[]");

        assertNotNull(response);
        assertEquals(ExtensionResponse.Result.SUCCESS, response.getResult());

        Thread.sleep(500);

        List<String> ragResults = fakeGenerator.getCapturedRagResults();
        assertFalse(ragResults.isEmpty());
        assertTrue(ragResults.getFirst().contains("No policy documents retrieved yet"),
                "Cold-start ragResults should be used for empty Policy Brief JSON array");
    }

    // =========================================================================
    // Tests: generatePolicyBrief with null/missing Policy Brief field
    // =========================================================================

    @Test
    @DisplayName("Should take cold-start path when Policy Brief field is null (old client backward compat)")
    void testNullPolicyBriefTakesColdStartPath() throws Exception {

        fakeGenerator.withResponse("""
                {"status":"AWAITING_INPUT","operations":[{"op":"replace_brief","replacementRules":["generated"]}],"message":"Draft ready."}
                """);

        ExtensionResponse response = handler.generatePolicyBrief(
                "Process refunds", TOOL_DEFINITIONS, null);

        assertNotNull(response);
        assertEquals(ExtensionResponse.Result.SUCCESS, response.getResult());

        Thread.sleep(500);

        List<String> ragResults = fakeGenerator.getCapturedRagResults();
        assertFalse(ragResults.isEmpty());
        assertTrue(ragResults.getFirst().contains("No policy documents retrieved yet"),
                "Cold-start ragResults should be used when Policy Brief is null (backward compat)");
    }

    @Test
    @DisplayName("Should take cold-start path when Policy Brief field is empty string")
    void testEmptyStringPolicyBriefTakesColdStartPath() throws Exception {

        fakeGenerator.withResponse("""
                {"status":"AWAITING_INPUT","operations":[{"op":"replace_brief","replacementRules":["generated"]}],"message":"Draft ready."}
                """);

        ExtensionResponse response = handler.generatePolicyBrief(
                "Process refunds", TOOL_DEFINITIONS, "");

        assertNotNull(response);
        assertEquals(ExtensionResponse.Result.SUCCESS, response.getResult());

        Thread.sleep(500);

        List<String> ragResults = fakeGenerator.getCapturedRagResults();
        assertFalse(ragResults.isEmpty());
        assertTrue(ragResults.getFirst().contains("No policy documents retrieved yet"),
                "Cold-start ragResults should be used when Policy Brief is empty string");
    }

    // =========================================================================
    // Tests: malformed Policy Brief JSON
    // =========================================================================

    @Test
    @DisplayName("Should gracefully fall back to cold-start when Policy Brief JSON is malformed")
    void testMalformedPolicyBriefJsonFallsToColdStart() throws Exception {

        fakeGenerator.withResponse("""
                {"status":"AWAITING_INPUT","operations":[{"op":"replace_brief","replacementRules":["generated"]}],"message":"Draft ready."}
                """);

        // Invalid JSON — parsePolicyBrief should catch the parse error and return List.of()
        ExtensionResponse response = handler.generatePolicyBrief(
                "Process refunds", TOOL_DEFINITIONS, "this is not valid JSON");

        assertNotNull(response);
        assertEquals(ExtensionResponse.Result.SUCCESS, response.getResult(),
                "Malformed JSON should not cause a failure — should degrade to cold-start");

        Thread.sleep(500);

        List<String> ragResults = fakeGenerator.getCapturedRagResults();
        assertFalse(ragResults.isEmpty());
        assertTrue(ragResults.getFirst().contains("No policy documents retrieved yet"),
                "Malformed Policy Brief should fall back to cold-start path");
    }

    @Test
    @DisplayName("Should gracefully fall back to cold-start when Policy Brief is a JSON object instead of array")
    void testJsonObjectPolicyBriefFallsToColdStart() throws Exception {

        fakeGenerator.withResponse("""
                {"status":"AWAITING_INPUT","operations":[{"op":"replace_brief","replacementRules":["generated"]}],"message":"Draft ready."}
                """);

        // JSON object instead of array — parsePolicyBrief should catch the type mismatch
        ExtensionResponse response = handler.generatePolicyBrief(
                "Process refunds", TOOL_DEFINITIONS, "{\"key\":\"value\"}");

        assertNotNull(response);
        assertEquals(ExtensionResponse.Result.SUCCESS, response.getResult(),
                "Wrong JSON type should not cause a failure");

        Thread.sleep(500);

        List<String> ragResults = fakeGenerator.getCapturedRagResults();
        assertFalse(ragResults.isEmpty());
        assertTrue(ragResults.getFirst().contains("No policy documents retrieved yet"),
                "JSON object Policy Brief should fall back to cold-start path");
    }

    // =========================================================================
    // Tests: pre-population UX via the handler path
    // =========================================================================

    @Test
    @DisplayName("Should create a retrievable seeded job with exact rules and AWAITING_INPUT — no LLM call")
    void testHandlerCreatesRetrievableSeededJob() throws Exception {

        String policyBriefJson = "[\"Rule X\",\"Rule Y\"]";

        ExtensionResponse response = handler.generatePolicyBrief(
                "Process refunds", TOOL_DEFINITIONS, policyBriefJson);

        String jobId = (String) response.getResponseValue().get("Job Id");

        // Use the singleton store to retrieve the job — should be immediately available
        PolicyBriefService service = new PolicyBriefService(new StubInvoker());
        PolicyBriefJob job = service.getResult(jobId);

        assertNotNull(job, "Job should be retrievable via the singleton store");
        assertEquals(PolicyBriefJob.Status.AWAITING_INPUT, job.getStatus(),
                "Seeded job should be AWAITING_INPUT immediately");
        assertEquals(List.of("Rule X", "Rule Y"), job.getPolicyBrief(),
                "Job should contain the exact seed rules — no LLM overwrite");
        assertTrue(job.isSeeded(), "Seeded flag should be true");

        // No LLM call should have been made
        assertEquals(0, fakeGenerator.getCallCount(),
                "No LLM call on seeded generate path");
    }

    @Test
    @DisplayName("Should normalize leaked prefixes in seeded Policy Brief JSON before storage")
    void testSeededPolicyBriefJsonNormalizesPrefixes() throws Exception {

        ExtensionResponse response = handler.generatePolicyBrief(
                "Process refunds",
                TOOL_DEFINITIONS,
                "[\"R1. Start with customer verification\",\"Escalate using rule R1 if needed\"]");

        String jobId = (String) response.getResponseValue().get("Job Id");

        PolicyBriefService service = new PolicyBriefService(new StubInvoker());
        PolicyBriefJob job = service.getResult(jobId);

        assertEquals(List.of("Start with customer verification", "Escalate using rule R1 if needed"),
                job.getPolicyBrief());
        assertEquals(0, fakeGenerator.getCallCount(),
                "Seeded normalization should not invoke the LLM");
    }

    @Test
    @DisplayName("Should return normalized plain strings from getPolicyBriefResult after generated replace_brief output")
    void testGeneratedPolicyBriefResultReturnsNormalizedStrings() throws Exception {

        fakeGenerator.withResponse("""
                {"status":"AWAITING_INPUT","operations":[{"op":"replace_brief","replacementRules":["R1. Start with customer verification","R2: Log all approval decisions"]}],"message":"Draft ready."}
                """);

        ExtensionResponse response = handler.generatePolicyBrief(
                "Process refunds", TOOL_DEFINITIONS, null);

        String jobId = (String) response.getResponseValue().get("Job Id");
        PolicyBriefService service = new PolicyBriefService(new StubInvoker());
        pollForStatus(service, jobId, PolicyBriefJob.Status.AWAITING_INPUT, 5000);

        ExtensionResponse result = handler.getPolicyBriefResult(jobId);
        String serializedBrief = (String) result.getResponseValue().get("Policy Brief");
        List<String> policyBrief = JsonMapper.getInstance().readValue(
                serializedBrief,
                JsonMapper.getInstance().getTypeFactory().constructCollectionType(List.class, String.class));

        assertEquals(List.of("Start with customer verification", "Log all approval decisions"),
                policyBrief);
    }

    // =========================================================================
    // Polling utility
    // =========================================================================

    private PolicyBriefJob pollForStatus(PolicyBriefService service, String jobId,
                                          PolicyBriefJob.Status expectedStatus,
                                          long timeoutMillis) throws InterruptedException {

        long deadline = System.currentTimeMillis() + timeoutMillis;
        while (System.currentTimeMillis() < deadline) {

            PolicyBriefJob job = service.getResult(jobId);
            if (job != null && job.getStatus() == expectedStatus) {

                return job;
            }
            Thread.sleep(50);
        }
        PolicyBriefJob finalJob = service.getResult(jobId);
        fail("Timed out waiting for status " + expectedStatus +
                ". Current status: " + (finalJob != null ? finalJob.getStatus() : "null"));
        return null;
    }
}
