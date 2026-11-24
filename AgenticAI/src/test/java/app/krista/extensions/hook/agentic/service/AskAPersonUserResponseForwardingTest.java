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
import app.krista.extensions.hook.agentic.dto.AgenticToolDefinition;
import app.krista.extensions.hook.agentic.planner.ToolPlanner;
import app.krista.extensions.hook.agentic.session.AgenticSession;
import app.krista.extensions.hook.agentic.session.AgenticSessionStore;
import dev.langchain4j.memory.ChatMemory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests that verify the KR-20396 fix — when {@code ask_a_person} returns
 * {@code {"userResponse": "<text>"}} the value is correctly stored in session history
 * and forwarded to the LLM planner as {@code previousToolResult} on the next turn.
 *
 * The bug before KR-20396: the LLM supplied multiple {@code outputVariableNames} but
 * only the first was consumed; the LLM was also not taught that the return format is
 * always {@code {"userResponse": "..."}}. The fix updates the system prompt, but the
 * catalog-side contracts tested here ensure the plumbing is correct regardless of what
 * the LLM expects.
 *
 * Tests in this class focus on the {@code continueFlow()} code path:
 *
 *   AgenticFlowService.continueFlow()
 *     → session.addToolResult(toolName, toolResult)            — stores history entry
 *     → callLlmWithValidation(... previousToolResult ...)      — reads last history entry
 *     → ToolPlanner.planNextStep(... previousToolResult ...)   — receives it
 *
 * The critical assertion is that the {@code previousToolResult} map passed to
 * {@code planNextStep} contains the {@code userResponse} key with the correct value.
 *
 * Mocks are used only where unavoidable (ToolPlanner, AgenticSessionStore) — the session
 * and flow service are real objects so session history manipulation is tested at the real
 * implementation level.
 */
@DisplayName("ask_a_person userResponse forwarding in continueFlow()")
class AskAPersonUserResponseForwardingTest {

    // -----------------------------------------------------------------------
    // Tool name constants
    // -----------------------------------------------------------------------

    private static final String TOOL_ASK_A_PERSON =
        "{\"toolType\":\"ask_a_person\",\"humanInteractionId\":\"builtin_ask_person\"}";

    private static final String TOOL_CATALOG_REQUEST =
        "{\"toolType\":\"catalog_request\",\"invokerId\":\"INV_001\",\"catalogRequestId\":\"req_xyz\"}";

    private static final String SESSION_ID = "sess_user_response_test_001";

    // -----------------------------------------------------------------------
    // Test infrastructure
    // -----------------------------------------------------------------------

    @Mock
    private AgenticSessionStore sessionStore;

    @Mock
    private ToolPlanner toolPlanner;

    @Mock
    private ChatMemory chatMemory;

    private Map<Object, ChatMemory> chatMemoryMap;
    private AgenticFlowService flowService;

    @BeforeEach
    void setUp() {

        MockitoAnnotations.openMocks(this);
        chatMemoryMap = new ConcurrentHashMap<>();
        chatMemoryMap.put(SESSION_ID, chatMemory);
        flowService = new AgenticFlowService(sessionStore, toolPlanner, chatMemoryMap);
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private AgenticSession buildSession(String sessionId) {

        List<AgenticToolDefinition> tools = new ArrayList<>();
        tools.add(new AgenticToolDefinition(
            TOOL_CATALOG_REQUEST,
            "Execute a catalog request",
            "catalog_request",
            new ArrayList<>(),
            false
        ));
        tools.add(new AgenticToolDefinition(
            TOOL_ASK_A_PERSON,
            "Ask a human for input",
            "ask_a_person",
            new ArrayList<>(),
            true
        ));

        return new AgenticSession(
            sessionId,
            tools,
            "Process order for John",
            new ArrayList<>(),
            0,
            Instant.now(),
            Instant.now(),
            List.of(),
            List.of()
        );
    }

    private AgenticResponse buildExecuteToolResponse(String toolName) {

        AgenticResponse response = new AgenticResponse();
        response.setSessionId(SESSION_ID);
        response.setResponseType(AgenticResponse.ResponseType.EXECUTE_TOOL);
        response.setConfidence(0.92);
        response.setCommand(new AgenticCommand(toolName, Map.of("param", "value")));
        return response;
    }

    // -----------------------------------------------------------------------
    // TC-FWD-1: userResponse key is present in previousToolResult when planner is called
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("TC-FWD-1: continueFlow forwards {'userResponse':'...'} to planNextStep as previousToolResult")
    void continueFlow_askAPersonResult_forwardsUserResponseKeyToPlanNextStep() {

        // Given
        AgenticSession session = buildSession(SESSION_ID);
        when(sessionStore.getSession(SESSION_ID)).thenReturn(session);
        when(toolPlanner.planNextStep(any(), any(), any(), eq(SESSION_ID), any(), anyString(), anyString()))
            .thenReturn(buildExecuteToolResponse(TOOL_CATALOG_REQUEST));

        Map<String, Object> toolResult = Map.of("userResponse", "Widget A");
        AgenticContinueRequest request = new AgenticContinueRequest(
            SESSION_ID,
            TOOL_ASK_A_PERSON,
            toolResult
        );

        // Capture the previousToolResult argument to planNextStep
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> previousToolResultCaptor =
            ArgumentCaptor.forClass(Map.class);

        // When
        flowService.continueFlow(request);

        // Then
        verify(toolPlanner).planNextStep(
            any(), any(), any(), eq(SESSION_ID),
            previousToolResultCaptor.capture(),
            anyString(), anyString()
        );

        Map<String, Object> capturedPreviousToolResult = previousToolResultCaptor.getValue();

        assertNotNull(capturedPreviousToolResult,
            "previousToolResult passed to planNextStep must not be null");

        // The planner receives the last entry from messageHistory, which is a wrapper map.
        // The actual tool result is stored under AgenticConstants.FIELD_RESULT.
        assertTrue(
            capturedPreviousToolResult.containsKey(AgenticConstants.FIELD_RESULT)
                || capturedPreviousToolResult.containsKey("userResponse"),
            "The previousToolResult passed to planNextStep must contain either the FIELD_RESULT "
            + "wrapper or the direct userResponse key. Actual keys: "
            + capturedPreviousToolResult.keySet()
        );

        // Walk through the structure: either the result is directly the userResponse map, or
        // it is wrapped under FIELD_RESULT in the history entry.
        Object resultValue = capturedPreviousToolResult.containsKey(AgenticConstants.FIELD_RESULT)
            ? capturedPreviousToolResult.get(AgenticConstants.FIELD_RESULT)
            : capturedPreviousToolResult;

        assertInstanceOf(Map.class, resultValue,
            "The tool result portion of previousToolResult must be a Map");

        @SuppressWarnings("unchecked")
        Map<String, Object> resultMap = (Map<String, Object>) resultValue;

        assertTrue(resultMap.containsKey("userResponse"),
            "The result map forwarded to the planner must contain the 'userResponse' key. "
            + "This is the KR-20396 contract: ask_a_person always returns {userResponse: <text>}. "
            + "Actual keys in result map: " + resultMap.keySet());

        assertEquals("Widget A", resultMap.get("userResponse"),
            "The 'userResponse' value forwarded to the planner must match what was supplied in "
            + "the continue request. Expected 'Widget A' but got: " + resultMap.get("userResponse"));
    }

    // -----------------------------------------------------------------------
    // TC-FWD-2: userResponse with a full sentence (confirmation scenario)
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("TC-FWD-2: continueFlow forwards confirmation userResponse text intact to planNextStep")
    void continueFlow_askAPersonConfirmation_forwardsFullResponseText() {

        // Given — simulates STEP 3 scenario: LLM asked for confirmation, user responded
        AgenticSession session = buildSession(SESSION_ID);
        when(sessionStore.getSession(SESSION_ID)).thenReturn(session);
        when(toolPlanner.planNextStep(any(), any(), any(), eq(SESSION_ID), any(), anyString(), anyString()))
            .thenReturn(buildExecuteToolResponse(TOOL_CATALOG_REQUEST));

        String confirmationText = "Yes, please proceed with the order for Widget A";
        Map<String, Object> toolResult = Map.of("userResponse", confirmationText);
        AgenticContinueRequest request = new AgenticContinueRequest(
            SESSION_ID,
            TOOL_ASK_A_PERSON,
            toolResult
        );

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> previousToolResultCaptor =
            ArgumentCaptor.forClass(Map.class);

        // When
        flowService.continueFlow(request);

        // Then
        verify(toolPlanner).planNextStep(
            any(), any(), any(), eq(SESSION_ID),
            previousToolResultCaptor.capture(),
            anyString(), anyString()
        );

        Map<String, Object> captured = previousToolResultCaptor.getValue();

        // Navigate to the result payload regardless of wrapping
        Object resultValue = captured.containsKey(AgenticConstants.FIELD_RESULT)
            ? captured.get(AgenticConstants.FIELD_RESULT)
            : captured;

        @SuppressWarnings("unchecked")
        Map<String, Object> resultMap = (Map<String, Object>) resultValue;

        assertEquals(confirmationText, resultMap.get("userResponse"),
            "Full confirmation text must survive unmodified through continueFlow into planNextStep. "
            + "The LLM reads this text to determine if the user confirmed (STEP 3 in the prompt)");
    }

    // -----------------------------------------------------------------------
    // TC-FWD-3: userResponse with compound answer (multiple values in one reply)
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("TC-FWD-3: continueFlow forwards compound userResponse (multiple values in one answer) to planNextStep")
    void continueFlow_askAPersonCompoundAnswer_forwardsUserResponseIntact() {

        // Given — simulates STEP 4 scenario: LLM asked a compound question to gather multiple
        // parameter values at once; the user answered in a single sentence.
        // The new prompt teaches the LLM: "You may ask one compound question to gather multiple
        // values at once" and then extract individual values from userResponse.
        AgenticSession session = buildSession(SESSION_ID);
        when(sessionStore.getSession(SESSION_ID)).thenReturn(session);
        when(toolPlanner.planNextStep(any(), any(), any(), eq(SESSION_ID), any(), anyString(), anyString()))
            .thenReturn(buildExecuteToolResponse(TOOL_CATALOG_REQUEST));

        // The user answers the compound question in a single reply
        String compoundAnswer = "I want Widget A, quantity 3, for customer CUST-999";
        Map<String, Object> toolResult = Map.of("userResponse", compoundAnswer);
        AgenticContinueRequest request = new AgenticContinueRequest(
            SESSION_ID,
            TOOL_ASK_A_PERSON,
            toolResult
        );

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> previousToolResultCaptor =
            ArgumentCaptor.forClass(Map.class);

        // When
        flowService.continueFlow(request);

        // Then
        verify(toolPlanner).planNextStep(
            any(), any(), any(), eq(SESSION_ID),
            previousToolResultCaptor.capture(),
            anyString(), anyString()
        );

        Map<String, Object> captured = previousToolResultCaptor.getValue();
        Object resultValue = captured.containsKey(AgenticConstants.FIELD_RESULT)
            ? captured.get(AgenticConstants.FIELD_RESULT)
            : captured;

        @SuppressWarnings("unchecked")
        Map<String, Object> resultMap = (Map<String, Object>) resultValue;

        assertEquals(compoundAnswer, resultMap.get("userResponse"),
            "Compound userResponse (containing multiple parameter values in one string) must be "
            + "forwarded intact. The LLM must extract individual values itself per CRITICAL RULE 10: "
            + "'Read the userResponse key and extract whatever values you need from it'");
    }

    // -----------------------------------------------------------------------
    // TC-FWD-4: planNextStep is called exactly once per continueFlow invocation
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("TC-FWD-4: continueFlow calls planNextStep exactly once with the ask_a_person result")
    void continueFlow_askAPersonResult_callsPlanNextStepExactlyOnce() {

        // Given
        AgenticSession session = buildSession(SESSION_ID);
        when(sessionStore.getSession(SESSION_ID)).thenReturn(session);
        when(toolPlanner.planNextStep(any(), any(), any(), eq(SESSION_ID), any(), anyString(), anyString()))
            .thenReturn(buildExecuteToolResponse(TOOL_CATALOG_REQUEST));

        AgenticContinueRequest request = new AgenticContinueRequest(
            SESSION_ID,
            TOOL_ASK_A_PERSON,
            Map.of("userResponse", "Widget A")
        );

        // When
        flowService.continueFlow(request);

        // Then
        verify(toolPlanner, times(1))
            .planNextStep(any(), any(), any(), eq(SESSION_ID), any(), anyString(), anyString());
    }

    // -----------------------------------------------------------------------
    // TC-FWD-5: Session message history records the ask_a_person result
    //           under FIELD_RESULT with the userResponse key intact
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("TC-FWD-5: continueFlow stores ask_a_person result in session history with userResponse key")
    void continueFlow_askAPersonResult_storedInSessionHistoryWithUserResponseKey() {

        // Given
        AgenticSession session = buildSession(SESSION_ID);
        when(sessionStore.getSession(SESSION_ID)).thenReturn(session);
        when(toolPlanner.planNextStep(any(), any(), any(), eq(SESSION_ID), any(), anyString(), anyString()))
            .thenReturn(buildExecuteToolResponse(TOOL_CATALOG_REQUEST));

        Map<String, Object> toolResult = Map.of("userResponse", "My answer is Widget A");
        AgenticContinueRequest request = new AgenticContinueRequest(
            SESSION_ID,
            TOOL_ASK_A_PERSON,
            toolResult
        );

        // When
        flowService.continueFlow(request);

        // Then — session history must contain the tool result entry
        List<Map<String, Object>> history = session.getMessageHistory();
        assertFalse(history.isEmpty(),
            "Session message history must not be empty after continueFlow");

        // The last entry is the tool result added by addToolResult()
        Map<String, Object> lastEntry = history.getLast();

        assertEquals(AgenticConstants.ROLE_TOOL, lastEntry.get(AgenticConstants.FIELD_ROLE),
            "The last history entry must have role=tool");

        assertEquals(TOOL_ASK_A_PERSON, lastEntry.get(AgenticConstants.FIELD_TOOL_NAME),
            "The last history entry must record the ask_a_person toolName");

        Object storedResult = lastEntry.get(AgenticConstants.FIELD_RESULT);
        assertNotNull(storedResult,
            "The last history entry must have a non-null result");

        assertInstanceOf(Map.class, storedResult,
            "The stored result must be a Map");

        @SuppressWarnings("unchecked")
        Map<String, Object> storedResultMap = (Map<String, Object>) storedResult;

        assertTrue(storedResultMap.containsKey("userResponse"),
            "Stored session history result must contain the 'userResponse' key. "
            + "If this key is absent the LLM will not be able to read the user's reply on the "
            + "next turn, which is the core KR-20396 bug. Actual keys: " + storedResultMap.keySet());

        assertEquals("My answer is Widget A", storedResultMap.get("userResponse"),
            "Stored 'userResponse' value must match the original tool result value");
    }

    // -----------------------------------------------------------------------
    // TC-FWD-6: ask_a_person result with ONLY userResponse key (no extra keys)
    //           is accepted and forwarded — confirms the LLM does not need named keys
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("TC-FWD-6: ask_a_person result containing only 'userResponse' is accepted without validation failure")
    void continueFlow_askAPersonResult_onlyUserResponseKey_isAccepted() {

        // Given — the new contract: ask_a_person returns EXACTLY {"userResponse":"<text>"}
        // This test ensures nothing downstream requires additional named keys
        AgenticSession session = buildSession(SESSION_ID);
        when(sessionStore.getSession(SESSION_ID)).thenReturn(session);
        when(toolPlanner.planNextStep(any(), any(), any(), eq(SESSION_ID), any(), anyString(), anyString()))
            .thenReturn(buildExecuteToolResponse(TOOL_CATALOG_REQUEST));

        // Only userResponse — no outputVariableNames, no product, no quantity, no other keys
        Map<String, Object> minimalToolResult = Map.of("userResponse", "just one answer");
        AgenticContinueRequest request = new AgenticContinueRequest(
            SESSION_ID,
            TOOL_ASK_A_PERSON,
            minimalToolResult
        );

        // When / Then: no exception thrown — the single-key structure is fully valid
        AgenticResponse response = assertDoesNotThrow(
            () -> flowService.continueFlow(request),
            "continueFlow must not throw when ask_a_person returns only {userResponse: <text>}. "
            + "The old system expected separate named keys; the new system must work with only userResponse"
        );

        assertNotNull(response,
            "continueFlow must return a non-null response for a valid ask_a_person result");
    }
}
