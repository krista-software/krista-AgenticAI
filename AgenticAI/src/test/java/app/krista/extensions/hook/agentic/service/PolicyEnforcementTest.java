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

package app.krista.extensions.hook.agentic.service;

import app.krista.extensions.hook.agentic.AgenticConstants;
import app.krista.extensions.hook.agentic.dto.AgenticCommand;
import app.krista.extensions.hook.agentic.dto.AgenticContinueRequest;
import app.krista.extensions.hook.agentic.dto.AgenticResponse;
import app.krista.extensions.hook.agentic.dto.AgenticStartRequest;
import app.krista.extensions.hook.agentic.dto.AgenticToolDefinition;
import app.krista.extensions.hook.agentic.dto.PolicyCheckResult;
import app.krista.extensions.hook.agentic.guardrails.PolicyCheckerFailurePolicy;
import app.krista.extensions.hook.agentic.planner.PolicyChecker;
import app.krista.extensions.hook.agentic.planner.ToolPlanner;
import app.krista.extensions.hook.agentic.session.AgenticSession;
import app.krista.extensions.hook.agentic.session.AgenticSessionStore;
import dev.langchain4j.memory.ChatMemory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Integration tests for policy enforcement in AgenticFlowService.
 * Uses a FakePolicyChecker to control policy evaluation outcomes.
 */
public class PolicyEnforcementTest {

    private static final String SESSION_ID = "sess_policy_test_001";
    private static final String VALID_TOOL = "{\"toolType\":\"catalog_request\",\"invokerId\":\"INV_abc\",\"catalogRequestId\":\"localDomainRequest_123\"}";
    private static final String ASK_A_PERSON_TOOL = "{\"toolType\":\"ask_a_person\",\"humanInteractionId\":\"builtin\"}";

    /**
     * Fake PolicyChecker that returns preconfigured results in sequence.
     * Implements the PolicyChecker interface directly without LangChain4j annotations.
     */
    static class FakePolicyChecker implements PolicyChecker {

        private final List<PolicyCheckResult> results = new ArrayList<>();
        private final AtomicInteger callCount = new AtomicInteger(0);
        private RuntimeException exceptionToThrow;

        FakePolicyChecker withResult(PolicyCheckResult result) {

            results.add(result);
            return this;
        }

        FakePolicyChecker withException(RuntimeException exception) {

            this.exceptionToThrow = exception;
            return this;
        }

        int getCallCount() {

            return callCount.get();
        }

        private final List<String> receivedToolHistories = new ArrayList<>();

        List<String> getReceivedToolHistories() {

            return receivedToolHistories;
        }

        @Override
        public PolicyCheckResult evaluate(String userGoal, String policyBrief,
                                          String plannedTool, String plannedArguments,
                                          String toolHistory) {

            int idx = callCount.getAndIncrement();
            receivedToolHistories.add(toolHistory);

            if (exceptionToThrow != null) {

                throw exceptionToThrow;
            }

            if (idx < results.size()) {

                return results.get(idx);
            }

            // Default to ALLOWED if no more configured results
            return PolicyCheckResult.allowed("Default allowed");
        }
    }

    // Shared helper methods

    static AgenticToolDefinition createBusinessTool(String name, String description) {

        return new AgenticToolDefinition(name, description, "catalog_request", new ArrayList<>(), false);
    }

    static AgenticToolDefinition createAskAPersonTool() {

        return new AgenticToolDefinition(ASK_A_PERSON_TOOL, "Ask a human for help", "ask_a_person", new ArrayList<>(), true);
    }

    static List<AgenticToolDefinition> createToolListWithAskAPerson() {

        List<AgenticToolDefinition> tools = new ArrayList<>();
        tools.add(createBusinessTool(VALID_TOOL, "Process catalog request"));
        tools.add(createAskAPersonTool());
        return tools;
    }

    static List<AgenticToolDefinition> createToolListWithoutAskAPerson() {

        List<AgenticToolDefinition> tools = new ArrayList<>();
        tools.add(createBusinessTool(VALID_TOOL, "Process catalog request"));
        return tools;
    }

    static AgenticResponse createExecuteToolResponse(String toolName) {

        AgenticResponse response = new AgenticResponse();
        response.setSessionId(SESSION_ID);
        response.setResponseType(AgenticResponse.ResponseType.EXECUTE_TOOL);
        response.setConfidence(0.95);
        response.setCommand(new AgenticCommand(toolName, Map.of("param1", "value1")));
        return response;
    }

    static AgenticSession createSession(String sessionId, List<AgenticToolDefinition> tools,
                                         List<String> policyBrief, String stepGoal) {

        Instant now = Instant.now();
        AgenticSession session = new AgenticSession(
                sessionId, tools, "Test user message", new ArrayList<>(), 0,
                now, now, List.of(), List.of()
        );
        session.setPolicyBrief(policyBrief);
        session.setStepGoal(stepGoal);
        return session;
    }

    // =========================================================================
    // Test: No policy enforcement when policyBrief is null/empty
    // =========================================================================

    @Nested
    @DisplayName("PolicyEnforcementNoopTest - No enforcement when no policy configured")
    class PolicyEnforcementNoopTest {

        @Mock
        private AgenticSessionStore sessionStore;

        @Mock
        private ToolPlanner toolPlanner;

        @Mock
        private ChatMemory chatMemory;

        private Map<Object, ChatMemory> chatMemoryMap;
        private FakePolicyChecker fakePolicyChecker;

        @BeforeEach
        void setUp() {

            MockitoAnnotations.openMocks(this);
            chatMemoryMap = new ConcurrentHashMap<>();
            chatMemoryMap.put(SESSION_ID, chatMemory);
            fakePolicyChecker = new FakePolicyChecker();
        }

        @Test
        @DisplayName("Should not invoke PolicyChecker when policyBrief is null")
        void testNullPolicyBriefSkipsChecker() {

            // Given
            List<AgenticToolDefinition> tools = createToolListWithAskAPerson();
            AgenticSession session = createSession(SESSION_ID, tools, null, null);
            when(sessionStore.getSession(SESSION_ID)).thenReturn(session);

            when(toolPlanner.planNextStep(any(), any(), any(), eq(SESSION_ID), any(), anyString(), anyString()))
                    .thenReturn(createExecuteToolResponse(VALID_TOOL));

            AgenticFlowService service = new AgenticFlowService(
                    sessionStore, null, toolPlanner, chatMemoryMap,
                    fakePolicyChecker, PolicyCheckerFailurePolicy.FAIL_CLOSED
            );

            AgenticStartRequest request = new AgenticStartRequest(
                    tools, "Test message", new HashMap<>(), List.of(), List.of(), SESSION_ID
            );
            request.setPolicyBrief(null);

            // When
            AgenticResponse response = service.startFlow(request);

            // Then
            assertEquals(0, fakePolicyChecker.getCallCount(),
                    "PolicyChecker should NOT be invoked when policyBrief is null");
            assertEquals(AgenticResponse.ResponseType.EXECUTE_TOOL, response.getResponseType());
        }

        @Test
        @DisplayName("Should not invoke PolicyChecker when policyBrief is empty list")
        void testEmptyPolicyBriefSkipsChecker() {

            // Given
            List<AgenticToolDefinition> tools = createToolListWithAskAPerson();
            AgenticSession session = createSession(SESSION_ID, tools, List.of(), null);
            when(sessionStore.getSession(SESSION_ID)).thenReturn(session);

            when(toolPlanner.planNextStep(any(), any(), any(), eq(SESSION_ID), any(), anyString(), anyString()))
                    .thenReturn(createExecuteToolResponse(VALID_TOOL));

            AgenticFlowService service = new AgenticFlowService(
                    sessionStore, null, toolPlanner, chatMemoryMap,
                    fakePolicyChecker, PolicyCheckerFailurePolicy.FAIL_CLOSED
            );

            AgenticStartRequest request = new AgenticStartRequest(
                    tools, "Test message", new HashMap<>(), List.of(), List.of(), SESSION_ID
            );
            request.setPolicyBrief(List.of());

            // When
            AgenticResponse response = service.startFlow(request);

            // Then
            assertEquals(0, fakePolicyChecker.getCallCount(),
                    "PolicyChecker should NOT be invoked when policyBrief is empty");
            assertEquals(AgenticResponse.ResponseType.EXECUTE_TOOL, response.getResponseType());
        }

        @Test
        @DisplayName("Should not invoke PolicyChecker when policyChecker is null")
        void testNullPolicyCheckerSkipsEnforcement() {

            // Given
            List<AgenticToolDefinition> tools = createToolListWithAskAPerson();
            List<String> policyBrief = List.of("Some policy");
            AgenticSession session = createSession(SESSION_ID, tools, policyBrief, "some goal");
            when(sessionStore.getSession(SESSION_ID)).thenReturn(session);

            when(toolPlanner.planNextStep(any(), any(), any(), eq(SESSION_ID), any(), anyString(), anyString()))
                    .thenReturn(createExecuteToolResponse(VALID_TOOL));

            // policyChecker is null (3-arg constructor)
            AgenticFlowService service = new AgenticFlowService(
                    sessionStore, toolPlanner, chatMemoryMap
            );

            AgenticStartRequest request = new AgenticStartRequest(
                    tools, "Test message", new HashMap<>(), List.of(), List.of(), SESSION_ID
            );
            request.setPolicyBrief(policyBrief);
            request.setStepGoal("some goal");

            // When
            AgenticResponse response = service.startFlow(request);

            // Then - no crash, normal flow
            assertEquals(AgenticResponse.ResponseType.EXECUTE_TOOL, response.getResponseType());
        }
    }

    // =========================================================================
    // Test: Policy allows tool execution
    // =========================================================================

    @Nested
    @DisplayName("PolicyEnforcementAllowedTest - Allowed actions pass through")
    class PolicyEnforcementAllowedTest {

        @Mock
        private AgenticSessionStore sessionStore;

        @Mock
        private ToolPlanner toolPlanner;

        @Mock
        private ChatMemory chatMemory;

        private Map<Object, ChatMemory> chatMemoryMap;
        private FakePolicyChecker fakePolicyChecker;

        @BeforeEach
        void setUp() {

            MockitoAnnotations.openMocks(this);
            chatMemoryMap = new ConcurrentHashMap<>();
            chatMemoryMap.put(SESSION_ID, chatMemory);
            fakePolicyChecker = new FakePolicyChecker()
                    .withResult(PolicyCheckResult.allowed("Action complies with all policies"));
        }

        @Test
        @DisplayName("Should return tool response as-is when policy check passes")
        void testAllowedPolicyReturnsResponse() {

            // Given
            List<AgenticToolDefinition> tools = createToolListWithAskAPerson();
            List<String> policyBrief = List.of("No deletions allowed");
            AgenticSession session = createSession(SESSION_ID, tools, policyBrief, "Process order");
            when(sessionStore.getSession(SESSION_ID)).thenReturn(session);

            when(toolPlanner.planNextStep(any(), any(), any(), eq(SESSION_ID), any(), anyString(), anyString()))
                    .thenReturn(createExecuteToolResponse(VALID_TOOL));

            AgenticFlowService service = new AgenticFlowService(
                    sessionStore, null, toolPlanner, chatMemoryMap,
                    fakePolicyChecker, PolicyCheckerFailurePolicy.FAIL_CLOSED
            );

            AgenticStartRequest request = new AgenticStartRequest(
                    tools, "Test message", new HashMap<>(), List.of(), List.of(), SESSION_ID
            );
            request.setPolicyBrief(policyBrief);
            request.setStepGoal("Process order");

            // When
            AgenticResponse response = service.startFlow(request);

            // Then
            assertEquals(AgenticResponse.ResponseType.EXECUTE_TOOL, response.getResponseType());
            assertEquals(VALID_TOOL, response.getCommand().toolName());
            assertEquals(1, fakePolicyChecker.getCallCount(),
                    "PolicyChecker should be called exactly once");
        }
    }

    // =========================================================================
    // Test: First violation triggers retry, second call succeeds
    // =========================================================================

    @Nested
    @DisplayName("PolicyEnforcementFirstViolationRetryTest - Retry on first violation")
    class PolicyEnforcementFirstViolationRetryTest {

        @Mock
        private AgenticSessionStore sessionStore;

        @Mock
        private ToolPlanner toolPlanner;

        @Mock
        private ChatMemory chatMemory;

        private Map<Object, ChatMemory> chatMemoryMap;
        private FakePolicyChecker fakePolicyChecker;

        @BeforeEach
        void setUp() {

            MockitoAnnotations.openMocks(this);
            chatMemoryMap = new ConcurrentHashMap<>();
            chatMemoryMap.put(SESSION_ID, chatMemory);

            // First check: BLOCKED, second check: ALLOWED
            fakePolicyChecker = new FakePolicyChecker()
                    .withResult(PolicyCheckResult.blocked("No external calls", "Tool calls external API", "HIGH"))
                    .withResult(PolicyCheckResult.allowed("Revised action complies"));
        }

        @Test
        @DisplayName("Should retry and succeed when first policy check is blocked then allowed")
        void testFirstViolationRetrySuccess() {

            // Given
            List<AgenticToolDefinition> tools = createToolListWithAskAPerson();
            List<String> policyBrief = List.of("No external API calls");
            AgenticSession session = createSession(SESSION_ID, tools, policyBrief, "Process order");
            when(sessionStore.getSession(SESSION_ID)).thenReturn(session);

            // Both LLM calls return EXECUTE_TOOL (planner replans after policy feedback)
            when(toolPlanner.planNextStep(any(), any(), any(), eq(SESSION_ID), any(), anyString(), anyString()))
                    .thenReturn(createExecuteToolResponse(VALID_TOOL));

            AgenticFlowService service = new AgenticFlowService(
                    sessionStore, null, toolPlanner, chatMemoryMap,
                    fakePolicyChecker, PolicyCheckerFailurePolicy.FAIL_CLOSED
            );

            AgenticStartRequest request = new AgenticStartRequest(
                    tools, "Test message", new HashMap<>(), List.of(), List.of(), SESSION_ID
            );
            request.setPolicyBrief(policyBrief);
            request.setStepGoal("Process order");

            // When
            AgenticResponse response = service.startFlow(request);

            // Then
            assertEquals(AgenticResponse.ResponseType.EXECUTE_TOOL, response.getResponseType());
            assertEquals(2, fakePolicyChecker.getCallCount(),
                    "PolicyChecker should be called twice (first blocked, then allowed)");

            // ToolPlanner should be called twice: original + retry after policy violation
            verify(toolPlanner, times(2))
                    .planNextStep(any(), any(), any(), eq(SESSION_ID), any(), anyString(), anyString());
        }
    }

    // =========================================================================
    // Test: Double violation causes hard block
    // =========================================================================

    @Nested
    @DisplayName("PolicyEnforcementDoubleViolationHardBlockTest - Hard block after max retries")
    class PolicyEnforcementDoubleViolationHardBlockTest {

        @Mock
        private AgenticSessionStore sessionStore;

        @Mock
        private ToolPlanner toolPlanner;

        @Mock
        private ChatMemory chatMemory;

        private Map<Object, ChatMemory> chatMemoryMap;
        private FakePolicyChecker fakePolicyChecker;

        @BeforeEach
        void setUp() {

            MockitoAnnotations.openMocks(this);
            chatMemoryMap = new ConcurrentHashMap<>();
            chatMemoryMap.put(SESSION_ID, chatMemory);

            // All checks return BLOCKED (MAX_POLICY_RETRIES = 2, so first + 2 retries = 3 checks)
            fakePolicyChecker = new FakePolicyChecker()
                    .withResult(PolicyCheckResult.blocked("No external calls", "Calls external API", "HIGH"))
                    .withResult(PolicyCheckResult.blocked("No external calls", "Still calls external API", "HIGH"))
                    .withResult(PolicyCheckResult.blocked("No external calls", "Still calls external API on final retry", "HIGH"));
        }

        @Test
        @DisplayName("Should return hard ERROR when policy violation exhausts retries")
        void testDoubleViolationReturnsHardError() {

            // Given
            List<AgenticToolDefinition> tools = createToolListWithAskAPerson();
            List<String> policyBrief = List.of("No external API calls");
            AgenticSession session = createSession(SESSION_ID, tools, policyBrief, "Process order");
            when(sessionStore.getSession(SESSION_ID)).thenReturn(session);

            when(toolPlanner.planNextStep(any(), any(), any(), eq(SESSION_ID), any(), anyString(), anyString()))
                    .thenReturn(createExecuteToolResponse(VALID_TOOL));

            AgenticFlowService service = new AgenticFlowService(
                    sessionStore, null, toolPlanner, chatMemoryMap,
                    fakePolicyChecker, PolicyCheckerFailurePolicy.FAIL_CLOSED
            );

            AgenticStartRequest request = new AgenticStartRequest(
                    tools, "Test message", new HashMap<>(), List.of(), List.of(), SESSION_ID
            );
            request.setPolicyBrief(policyBrief);
            request.setStepGoal("Process order");

            // When
            AgenticResponse response = service.startFlow(request);

            // Then - should be a hard ERROR, no user override
            assertEquals(AgenticResponse.ResponseType.ERROR, response.getResponseType(),
                    "Policy exhaustion should return hard ERROR, not escalate to user");
            assertTrue(response.getErrorMessage().contains("No external calls"),
                    "Error message should include the violated policy rule");
            assertTrue(response.getErrorMessage().contains("compliant alternative"),
                    "Error message should state no compliant alternative was found");
        }
    }

    // =========================================================================
    // Test: Hard block regardless of available helper tools
    // =========================================================================

    @Nested
    @DisplayName("PolicyEnforcementHardBlockIndependentOfToolsTest - Hard block is independent of available helper tools")
    class PolicyEnforcementHardBlockIndependentOfToolsTest {

        @Mock
        private AgenticSessionStore sessionStore;

        @Mock
        private ToolPlanner toolPlanner;

        @Mock
        private ChatMemory chatMemory;

        private Map<Object, ChatMemory> chatMemoryMap;
        private FakePolicyChecker fakePolicyChecker;

        @BeforeEach
        void setUp() {

            MockitoAnnotations.openMocks(this);
            chatMemoryMap = new ConcurrentHashMap<>();
            chatMemoryMap.put(SESSION_ID, chatMemory);

            // All checks return BLOCKED (MAX_POLICY_RETRIES = 2, so first + 2 retries = 3 checks)
            fakePolicyChecker = new FakePolicyChecker()
                    .withResult(PolicyCheckResult.blocked("No external calls", "Calls API", "HIGH"))
                    .withResult(PolicyCheckResult.blocked("No external calls", "Still calls API", "HIGH"))
                    .withResult(PolicyCheckResult.blocked("No external calls", "Still calls API on final retry", "HIGH"));
        }

        @Test
        @DisplayName("Should return hard ERROR regardless of available helper tools")
        void testHardBlockRegardlessOfHelperTools() {

            // Given - NO ask_a_person tool in the list
            List<AgenticToolDefinition> tools = createToolListWithoutAskAPerson();
            List<String> policyBrief = List.of("No external API calls");
            AgenticSession session = createSession(SESSION_ID, tools, policyBrief, "Process order");
            when(sessionStore.getSession(SESSION_ID)).thenReturn(session);

            when(toolPlanner.planNextStep(any(), any(), any(), eq(SESSION_ID), any(), anyString(), anyString()))
                    .thenReturn(createExecuteToolResponse(VALID_TOOL));

            AgenticFlowService service = new AgenticFlowService(
                    sessionStore, null, toolPlanner, chatMemoryMap,
                    fakePolicyChecker, PolicyCheckerFailurePolicy.FAIL_CLOSED
            );

            AgenticStartRequest request = new AgenticStartRequest(
                    tools, "Test message", new HashMap<>(), List.of(), List.of(), SESSION_ID
            );
            request.setPolicyBrief(policyBrief);
            request.setStepGoal("Process order");

            // When
            AgenticResponse response = service.startFlow(request);

            // Then - should be a hard ERROR with policy details
            assertEquals(AgenticResponse.ResponseType.ERROR, response.getResponseType(),
                    "Should return hard ERROR when policy retries exhausted");
            assertTrue(response.getErrorMessage().contains("No external calls"),
                    "Error message should include the violated policy rule");
            assertTrue(response.getErrorMessage().contains("compliant alternative"),
                    "Error message should state no compliant alternative was found");
        }
    }

    // =========================================================================
    // Test: PolicyChecker exception with FAIL_CLOSED
    // =========================================================================

    @Nested
    @DisplayName("PolicyCheckerExceptionFailClosedTest - Exception with FAIL_CLOSED blocks execution")
    class PolicyCheckerExceptionFailClosedTest {

        @Mock
        private AgenticSessionStore sessionStore;

        @Mock
        private ToolPlanner toolPlanner;

        @Mock
        private ChatMemory chatMemory;

        private Map<Object, ChatMemory> chatMemoryMap;
        private FakePolicyChecker fakePolicyChecker;

        @BeforeEach
        void setUp() {

            MockitoAnnotations.openMocks(this);
            chatMemoryMap = new ConcurrentHashMap<>();
            chatMemoryMap.put(SESSION_ID, chatMemory);

            fakePolicyChecker = new FakePolicyChecker()
                    .withException(new RuntimeException("LLM timeout after 30s"));
        }

        @Test
        @DisplayName("Should return hard ERROR when PolicyChecker throws with FAIL_CLOSED policy")
        void testExceptionFailClosedBlocksExecution() {

            // Given
            List<AgenticToolDefinition> tools = createToolListWithAskAPerson();
            List<String> policyBrief = List.of("No external API calls");
            AgenticSession session = createSession(SESSION_ID, tools, policyBrief, "Process order");
            when(sessionStore.getSession(SESSION_ID)).thenReturn(session);

            when(toolPlanner.planNextStep(any(), any(), any(), eq(SESSION_ID), any(), anyString(), anyString()))
                    .thenReturn(createExecuteToolResponse(VALID_TOOL));

            AgenticFlowService service = new AgenticFlowService(
                    sessionStore, null, toolPlanner, chatMemoryMap,
                    fakePolicyChecker, PolicyCheckerFailurePolicy.FAIL_CLOSED
            );

            AgenticStartRequest request = new AgenticStartRequest(
                    tools, "Test message", new HashMap<>(), List.of(), List.of(), SESSION_ID
            );
            request.setPolicyBrief(policyBrief);
            request.setStepGoal("Process order");

            // When
            AgenticResponse response = service.startFlow(request);

            // Then - FAIL_CLOSED should return hard ERROR, not escalate to user
            assertEquals(AgenticResponse.ResponseType.ERROR, response.getResponseType(),
                    "FAIL_CLOSED should return hard ERROR, not escalate via ask_a_person");
            assertNotNull(response.getErrorMessage(),
                    "Error response should include an error message");
        }
    }

    // =========================================================================
    // Test: PolicyChecker exception with FAIL_OPEN
    // =========================================================================

    @Nested
    @DisplayName("PolicyCheckerExceptionFailOpenTest - Exception with FAIL_OPEN allows execution")
    class PolicyCheckerExceptionFailOpenTest {

        @Mock
        private AgenticSessionStore sessionStore;

        @Mock
        private ToolPlanner toolPlanner;

        @Mock
        private ChatMemory chatMemory;

        private Map<Object, ChatMemory> chatMemoryMap;
        private FakePolicyChecker fakePolicyChecker;

        @BeforeEach
        void setUp() {

            MockitoAnnotations.openMocks(this);
            chatMemoryMap = new ConcurrentHashMap<>();
            chatMemoryMap.put(SESSION_ID, chatMemory);

            fakePolicyChecker = new FakePolicyChecker()
                    .withException(new RuntimeException("LLM timeout after 30s"));
        }

        @Test
        @DisplayName("Should allow execution when PolicyChecker throws with FAIL_OPEN policy")
        void testExceptionFailOpenAllowsExecution() {

            // Given
            List<AgenticToolDefinition> tools = createToolListWithAskAPerson();
            List<String> policyBrief = List.of("No external API calls");
            AgenticSession session = createSession(SESSION_ID, tools, policyBrief, "Process order");
            when(sessionStore.getSession(SESSION_ID)).thenReturn(session);

            when(toolPlanner.planNextStep(any(), any(), any(), eq(SESSION_ID), any(), anyString(), anyString()))
                    .thenReturn(createExecuteToolResponse(VALID_TOOL));

            AgenticFlowService service = new AgenticFlowService(
                    sessionStore, null, toolPlanner, chatMemoryMap,
                    fakePolicyChecker, PolicyCheckerFailurePolicy.FAIL_OPEN
            );

            AgenticStartRequest request = new AgenticStartRequest(
                    tools, "Test message", new HashMap<>(), List.of(), List.of(), SESSION_ID
            );
            request.setPolicyBrief(policyBrief);
            request.setStepGoal("Process order");

            // When
            AgenticResponse response = service.startFlow(request);

            // Then - FAIL_OPEN should let the original response through
            assertEquals(AgenticResponse.ResponseType.EXECUTE_TOOL, response.getResponseType());
            assertEquals(VALID_TOOL, response.getCommand().toolName(),
                    "FAIL_OPEN should return the original tool execution response");
        }
    }

    // =========================================================================
    // Test: policyRetryCount and attemptNumber are independent
    // =========================================================================

    @Nested
    @DisplayName("PolicyRetryCountIsolationTest - Independent retry counters")
    class PolicyRetryCountIsolationTest {

        @Mock
        private AgenticSessionStore sessionStore;

        @Mock
        private ToolPlanner toolPlanner;

        @Mock
        private ChatMemory chatMemory;

        private Map<Object, ChatMemory> chatMemoryMap;

        @BeforeEach
        void setUp() {

            MockitoAnnotations.openMocks(this);
            chatMemoryMap = new ConcurrentHashMap<>();
            chatMemoryMap.put(SESSION_ID, chatMemory);
        }

        @Test
        @DisplayName("Should keep policyRetryCount independent from tool validation attemptNumber")
        void testRetryCountIsolation() {

            // Given - PolicyChecker blocks first, allows second
            FakePolicyChecker fakePolicyChecker = new FakePolicyChecker()
                    .withResult(PolicyCheckResult.blocked("No external calls", "Blocked", "HIGH"))
                    .withResult(PolicyCheckResult.allowed("OK on second try"));

            List<AgenticToolDefinition> tools = createToolListWithAskAPerson();
            List<String> policyBrief = List.of("No external API calls");
            AgenticSession session = createSession(SESSION_ID, tools, policyBrief, "Process order");
            when(sessionStore.getSession(SESSION_ID)).thenReturn(session);

            // Both LLM calls return valid tool (no tool validation retries needed)
            when(toolPlanner.planNextStep(any(), any(), any(), eq(SESSION_ID), any(), anyString(), anyString()))
                    .thenReturn(createExecuteToolResponse(VALID_TOOL));

            AgenticFlowService service = new AgenticFlowService(
                    sessionStore, null, toolPlanner, chatMemoryMap,
                    fakePolicyChecker, PolicyCheckerFailurePolicy.FAIL_CLOSED
            );

            AgenticStartRequest request = new AgenticStartRequest(
                    tools, "Test message", new HashMap<>(), List.of(), List.of(), SESSION_ID
            );
            request.setPolicyBrief(policyBrief);
            request.setStepGoal("Process order");

            // When
            AgenticResponse response = service.startFlow(request);

            // Then - tool planner called twice (initial + policy retry), both at attempt 1
            // because policy retries don't increment the tool validation attempt number
            assertEquals(AgenticResponse.ResponseType.EXECUTE_TOOL, response.getResponseType());
            assertEquals(2, fakePolicyChecker.getCallCount(),
                    "PolicyChecker should be called twice");
            verify(toolPlanner, times(2))
                    .planNextStep(any(), any(), any(), eq(SESSION_ID), any(), anyString(), anyString());
        }
    }

    // =========================================================================
    // Test: Policy brief threading from startFlow to continueFlow
    // =========================================================================

    @Nested
    @DisplayName("ContinueFlowBriefThreadingTest - Policy brief persists across flow")
    class ContinueFlowBriefThreadingTest {

        @Mock
        private AgenticSessionStore sessionStore;

        @Mock
        private ToolPlanner toolPlanner;

        @Mock
        private ChatMemory chatMemory;

        private Map<Object, ChatMemory> chatMemoryMap;
        private FakePolicyChecker fakePolicyChecker;

        @BeforeEach
        void setUp() {

            MockitoAnnotations.openMocks(this);
            chatMemoryMap = new ConcurrentHashMap<>();
            chatMemoryMap.put(SESSION_ID, chatMemory);
        }

        @Test
        @DisplayName("Should use session policyBrief in continueFlow policy check")
        void testPolicyBriefAvailableInContinueFlow() {

            // Given - session already has policyBrief set (from startFlow)
            fakePolicyChecker = new FakePolicyChecker()
                    .withResult(PolicyCheckResult.allowed("Compliant action"));

            List<AgenticToolDefinition> tools = createToolListWithAskAPerson();
            List<String> policyBrief = List.of("No deletions allowed", "All writes must be logged");
            AgenticSession session = createSession(SESSION_ID, tools, policyBrief, "Process order");

            // Simulate that session already has history (from startFlow)
            session.addToolResult(VALID_TOOL, Map.of("status", "success", "orderId", "123"));
            when(sessionStore.getSession(SESSION_ID)).thenReturn(session);

            when(toolPlanner.planNextStep(any(), any(), any(), eq(SESSION_ID), any(), anyString(), anyString()))
                    .thenReturn(createExecuteToolResponse(VALID_TOOL));

            AgenticFlowService service = new AgenticFlowService(
                    sessionStore, null, toolPlanner, chatMemoryMap,
                    fakePolicyChecker, PolicyCheckerFailurePolicy.FAIL_CLOSED
            );

            AgenticContinueRequest continueRequest = new AgenticContinueRequest(
                    SESSION_ID, VALID_TOOL, Map.of("status", "success")
            );

            // When
            AgenticResponse response = service.continueFlow(continueRequest);

            // Then - PolicyChecker should have been invoked (proving policyBrief was threaded)
            assertEquals(1, fakePolicyChecker.getCallCount(),
                    "PolicyChecker should be invoked during continueFlow when session has policyBrief");
            assertEquals(AgenticResponse.ResponseType.EXECUTE_TOOL, response.getResponseType());
        }
    }

    // =========================================================================
    // Test: Tool history is threaded to PolicyChecker
    // =========================================================================

    @Nested
    @DisplayName("ToolHistoryThreadingTest - Tool history passed to PolicyChecker")
    class ToolHistoryThreadingTest {

        @Mock
        private AgenticSessionStore sessionStore;

        @Mock
        private ToolPlanner toolPlanner;

        @Mock
        private ChatMemory chatMemory;

        private Map<Object, ChatMemory> chatMemoryMap;
        private FakePolicyChecker fakePolicyChecker;

        @BeforeEach
        void setUp() {

            MockitoAnnotations.openMocks(this);
            chatMemoryMap = new ConcurrentHashMap<>();
            chatMemoryMap.put(SESSION_ID, chatMemory);
        }

        @Test
        @DisplayName("Should pass 'none' when session has no message history")
        void testEmptyHistoryPassesNone() {

            // Given
            fakePolicyChecker = new FakePolicyChecker()
                    .withResult(PolicyCheckResult.allowed("No history needed"));

            List<AgenticToolDefinition> tools = createToolListWithAskAPerson();
            List<String> policyBrief = List.of("All actions require prior approval");
            AgenticSession session = createSession(SESSION_ID, tools, policyBrief, "Process order");
            // No tool results added — empty message history
            when(sessionStore.getSession(SESSION_ID)).thenReturn(session);

            when(toolPlanner.planNextStep(any(), any(), any(), eq(SESSION_ID), any(), anyString(), anyString()))
                    .thenReturn(createExecuteToolResponse(VALID_TOOL));

            AgenticFlowService service = new AgenticFlowService(
                    sessionStore, null, toolPlanner, chatMemoryMap,
                    fakePolicyChecker, PolicyCheckerFailurePolicy.FAIL_CLOSED
            );

            AgenticStartRequest request = new AgenticStartRequest(
                    tools, "Test message", new HashMap<>(), List.of(), List.of(), SESSION_ID
            );
            request.setPolicyBrief(policyBrief);
            request.setStepGoal("Process order");

            // When
            service.startFlow(request);

            // Then
            assertEquals(1, fakePolicyChecker.getCallCount());
            assertEquals("none", fakePolicyChecker.getReceivedToolHistories().get(0),
                    "Empty message history should produce 'none'");
        }

        @Test
        @DisplayName("Should include all entries as recent when history has 2 tool calls")
        void testTwoToolCallsAllRecent() {

            // Given
            fakePolicyChecker = new FakePolicyChecker()
                    .withResult(PolicyCheckResult.allowed("History looks good"));

            List<AgenticToolDefinition> tools = createToolListWithAskAPerson();
            List<String> policyBrief = List.of("Vendor must be verified before PO");
            AgenticSession session = createSession(SESSION_ID, tools, policyBrief, "Create PO");

            // Add 2 tool results (below RECENT_HISTORY_COUNT of 3)
            session.addToolResult("lookup_vendor", Map.of("status", "found", "vendor_id", "V-100"));
            session.addToolResult("check_approved", Map.of("approved", true));
            when(sessionStore.getSession(SESSION_ID)).thenReturn(session);

            when(toolPlanner.planNextStep(any(), any(), any(), eq(SESSION_ID), any(), anyString(), anyString()))
                    .thenReturn(createExecuteToolResponse(VALID_TOOL));

            AgenticFlowService service = new AgenticFlowService(
                    sessionStore, null, toolPlanner, chatMemoryMap,
                    fakePolicyChecker, PolicyCheckerFailurePolicy.FAIL_CLOSED
            );

            AgenticStartRequest request = new AgenticStartRequest(
                    tools, "Test message", new HashMap<>(), List.of(), List.of(), SESSION_ID
            );
            request.setPolicyBrief(policyBrief);
            request.setStepGoal("Create PO");

            // When
            service.startFlow(request);

            // Then
            String history = fakePolicyChecker.getReceivedToolHistories().get(0);
            assertFalse(history.contains("PRIOR ACTIONS"),
                    "With only 2 entries, there should be no PRIOR ACTIONS section");
            assertTrue(history.contains("RECENT ACTIONS"),
                    "Should contain RECENT ACTIONS section");
            assertTrue(history.contains("[1] CALL: lookup_vendor"),
                    "First tool call should be in recent actions");
            assertTrue(history.contains("[2] CALL: check_approved"),
                    "Second tool call should be in recent actions");
            assertTrue(history.contains("RESULT:"),
                    "Recent entries should include RESULT");
        }

        @Test
        @DisplayName("Should split 8 tool calls into 3 prior + 5 recent")
        void testSixToolCallsSplitCorrectly() {

            // Given
            fakePolicyChecker = new FakePolicyChecker()
                    .withResult(PolicyCheckResult.allowed("All checks passed"));

            List<AgenticToolDefinition> tools = createToolListWithAskAPerson();
            List<String> policyBrief = List.of("Verify vendor before creating PO");
            AgenticSession session = createSession(SESSION_ID, tools, policyBrief, "Full workflow");

            // Add 8 tool results so 3 go to prior and 5 go to recent (RECENT_HISTORY_COUNT = 5)
            session.addToolResult("lookup_vendor", Map.of("status", "found"));
            session.addToolResult("get_budget", Map.of("remaining", 50000));
            session.addToolResult("check_approved", Map.of("approved", true));
            session.addToolResult("draft_justification", Map.of("draft_id", "JUST-001"));
            session.addToolResult("get_approval", Map.of("approved", true, "approver", "Jane"));
            session.addToolResult("review_justification", Map.of("reviewed", true));
            session.addToolResult("create_po", Map.of("po_id", "PO-001"));
            session.addToolResult("send_confirmation", Map.of("sent", true));
            when(sessionStore.getSession(SESSION_ID)).thenReturn(session);

            when(toolPlanner.planNextStep(any(), any(), any(), eq(SESSION_ID), any(), anyString(), anyString()))
                    .thenReturn(createExecuteToolResponse(VALID_TOOL));

            AgenticFlowService service = new AgenticFlowService(
                    sessionStore, null, toolPlanner, chatMemoryMap,
                    fakePolicyChecker, PolicyCheckerFailurePolicy.FAIL_CLOSED
            );

            AgenticStartRequest request = new AgenticStartRequest(
                    tools, "Test message", new HashMap<>(), List.of(), List.of(), SESSION_ID
            );
            request.setPolicyBrief(policyBrief);
            request.setStepGoal("Full workflow");

            // When
            service.startFlow(request);

            // Then
            String history = fakePolicyChecker.getReceivedToolHistories().get(0);
            assertTrue(history.contains("PRIOR ACTIONS"),
                    "Should have PRIOR ACTIONS section for first 3 entries");
            assertTrue(history.contains("RECENT ACTIONS"),
                    "Should have RECENT ACTIONS section for last 3 entries");

            // Prior entries are summary-only (no RESULT line)
            assertTrue(history.contains("[1] lookup_vendor -> completed"),
                    "First prior entry should be summary format");
            assertTrue(history.contains("[2] get_budget -> completed"),
                    "Second prior entry should be summary format");
            assertTrue(history.contains("[3] check_approved -> completed"),
                    "Third prior entry should be summary format");

            // Recent entries have full results
            assertTrue(history.contains("[4] CALL: draft_justification"),
                    "Fourth entry should be in recent with CALL prefix");
            assertTrue(history.contains("[5] CALL: get_approval"),
                    "Fifth entry should be in recent with CALL prefix");
            assertTrue(history.contains("[6] CALL: review_justification"),
                    "Sixth entry should be in recent with CALL prefix");
        }

        @Test
        @DisplayName("Should show error outcome for tool entries with error results")
        void testErrorResultShowsErrorOutcome() {

            // Given
            fakePolicyChecker = new FakePolicyChecker()
                    .withResult(PolicyCheckResult.allowed("Error noted"));

            List<AgenticToolDefinition> tools = createToolListWithAskAPerson();
            List<String> policyBrief = List.of("All lookups must succeed");
            AgenticSession session = createSession(SESSION_ID, tools, policyBrief, "Process order");

            // Add 7 entries: 2 prior (one error) + 5 recent (RECENT_HISTORY_COUNT = 5)
            session.addToolResult("lookup_vendor", Map.of("status", "error", "message", "not found"));
            session.addToolResult("retry_lookup", Map.of("status", "found"));
            session.addToolResult("check_approved", Map.of("approved", true));
            session.addToolResult("get_budget", Map.of("remaining", 50000));
            session.addToolResult("draft_po", Map.of("po_id", "PO-001"));
            session.addToolResult("review_po", Map.of("reviewed", true));
            session.addToolResult("finalize_po", Map.of("finalized", true));
            when(sessionStore.getSession(SESSION_ID)).thenReturn(session);

            when(toolPlanner.planNextStep(any(), any(), any(), eq(SESSION_ID), any(), anyString(), anyString()))
                    .thenReturn(createExecuteToolResponse(VALID_TOOL));

            AgenticFlowService service = new AgenticFlowService(
                    sessionStore, null, toolPlanner, chatMemoryMap,
                    fakePolicyChecker, PolicyCheckerFailurePolicy.FAIL_CLOSED
            );

            AgenticStartRequest request = new AgenticStartRequest(
                    tools, "Test message", new HashMap<>(), List.of(), List.of(), SESSION_ID
            );
            request.setPolicyBrief(policyBrief);
            request.setStepGoal("Process order");

            // When
            service.startFlow(request);

            // Then
            String history = fakePolicyChecker.getReceivedToolHistories().get(0);
            assertTrue(history.contains("[1] lookup_vendor -> error"),
                    "Error tool result should show '-> error' in prior section");
            assertTrue(history.contains("[2] retry_lookup -> completed"),
                    "Successful tool result should show '-> completed' in prior section");
        }

        @Test
        @DisplayName("Should handle JSON tool names by extracting toolType")
        void testJsonToolNameExtractsToolType() {

            // Given
            fakePolicyChecker = new FakePolicyChecker()
                    .withResult(PolicyCheckResult.allowed("OK"));

            List<AgenticToolDefinition> tools = createToolListWithAskAPerson();
            List<String> policyBrief = List.of("Some policy");
            AgenticSession session = createSession(SESSION_ID, tools, policyBrief, "Test goal");

            // Add a tool result with JSON tool name (as used by catalog requests)
            session.addToolResult(VALID_TOOL, Map.of("status", "success"));
            when(sessionStore.getSession(SESSION_ID)).thenReturn(session);

            when(toolPlanner.planNextStep(any(), any(), any(), eq(SESSION_ID), any(), anyString(), anyString()))
                    .thenReturn(createExecuteToolResponse(VALID_TOOL));

            AgenticFlowService service = new AgenticFlowService(
                    sessionStore, null, toolPlanner, chatMemoryMap,
                    fakePolicyChecker, PolicyCheckerFailurePolicy.FAIL_CLOSED
            );

            AgenticStartRequest request = new AgenticStartRequest(
                    tools, "Test message", new HashMap<>(), List.of(), List.of(), SESSION_ID
            );
            request.setPolicyBrief(policyBrief);
            request.setStepGoal("Test goal");

            // When
            service.startFlow(request);

            // Then — JSON tool name should be resolved to its toolType
            String history = fakePolicyChecker.getReceivedToolHistories().get(0);
            assertTrue(history.contains("catalog_request"),
                    "JSON tool name should be resolved to toolType 'catalog_request'");
            assertFalse(history.contains("invokerId"),
                    "Raw JSON fields should not appear — toolType extraction should simplify the name");
        }
    }
}
