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

package app.krista.extensions.hook.agentic.guardrails;

import app.krista.extensions.hook.agentic.dto.AgenticCommand;
import app.krista.extensions.hook.agentic.dto.AgenticResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link OutputGuardrails} — validates LLM-generated response structure
 * before it reaches the converter and session persistence layers.
 *
 * Coverage areas relevant to KR-20396:
 *
 *   1. EXECUTE_TOOL with ask_a_person toolName is accepted — the LLM correctly selects
 *      ask_a_person when parameters are missing; the output guardrails must not block it.
 *   2. EXECUTE_TOOL with ask_a_person carries arbitrary arguments — after the fix the LLM
 *      may place any set of outputVariableNames in its arguments map; guardrails must accept
 *      the response regardless of what keys the arguments map contains.
 *   3. EXECUTE_TOOL with invalid toolType is blocked — ensures the guardrail rejects unknown
 *      tool types that should never appear in a well-formed LLM response.
 *   4. COMPLETE and ERROR happy/sad paths to confirm the broader guard is intact.
 *
 * All tests use only plain Java objects — no mocks, no containers, no I/O.
 */
@DisplayName("OutputGuardrails — response structure validation")
class OutputGuardrailsTest {

    // -----------------------------------------------------------------------
    // Tool name constants (JSON strings, matching the format the LLM produces)
    // -----------------------------------------------------------------------

    private static final String TOOL_ASK_A_PERSON =
        "{\"toolType\":\"ask_a_person\",\"humanInteractionId\":\"builtin_ask_person\"}";

    private static final String TOOL_CATALOG_REQUEST =
        "{\"toolType\":\"catalog_request\",\"invokerId\":\"INV_001\",\"catalogRequestId\":\"req_xyz\"}";

    private static final String TOOL_INVOKER =
        "{\"toolType\":\"invoker\",\"invokerId\":\"inv_abc\"}";

    private static final String TOOL_ENTITY_ADD =
        "{\"toolType\":\"entity\",\"entityId\":\"Customer\",\"operationType\":\"ADD\"}";

    private static final String TOOL_INFORM_A_PERSON =
        "{\"toolType\":\"inform_a_person\"}";

    private static final String TOOL_CONVERSATION =
        "{\"toolType\":\"conversation\",\"channelName\":\"ch1\",\"conversationName\":\"Onboarding\"}";

    // -----------------------------------------------------------------------
    // TC-OG-1: null response is blocked
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("TC-OG-1: null AgenticResponse is blocked")
    void validateResponse_nullResponse_isBlocked() {

        GuardrailResults result = OutputGuardrails.validateResponse(null);

        assertTrue(result.isBlocked(),
            "null response must be blocked");
    }

    // -----------------------------------------------------------------------
    // TC-OG-2: response with null responseType is blocked
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("TC-OG-2: AgenticResponse with null responseType is blocked")
    void validateResponse_nullResponseType_isBlocked() {

        AgenticResponse response = new AgenticResponse();
        response.setSessionId("sess_001");
        // responseType left null

        GuardrailResults result = OutputGuardrails.validateResponse(response);

        assertTrue(result.isBlocked(),
            "Response with null responseType must be blocked");
    }

    // -----------------------------------------------------------------------
    // TC-OG-3: EXECUTE_TOOL with ask_a_person is accepted (KR-20396 core case)
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("TC-OG-3: EXECUTE_TOOL for ask_a_person is accepted by output guardrails")
    void validateResponse_executeTool_askAPerson_isAccepted() {

        // This is the primary output guardrail test for KR-20396.
        // The LLM selects ask_a_person to gather missing parameters.
        // The output guardrail must accept this response without modification.
        AgenticResponse response = buildExecuteToolResponse(
            "sess_og_003",
            TOOL_ASK_A_PERSON,
            Map.of("question", "What product would you like to order?")
        );

        GuardrailResults result = OutputGuardrails.validateResponse(response);

        assertFalse(result.isBlocked(),
            "EXECUTE_TOOL with ask_a_person toolType must be accepted by output guardrails. "
            + "The LLM legitimately selects this tool when parameters are missing");
    }

    // -----------------------------------------------------------------------
    // TC-OG-4: EXECUTE_TOOL for ask_a_person with multiple outputVariableNames in
    //          arguments is accepted — this is the KR-20396 regression scenario
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("TC-OG-4: EXECUTE_TOOL for ask_a_person with multiple outputVariableNames in arguments is accepted")
    void validateResponse_executeTool_askAPerson_multipleOutputVarNamesInArgs_isAccepted() {

        // Before KR-20396 the LLM would supply multiple outputVariableNames expecting them to
        // be returned as separate keys. The fix teaches the LLM that only userResponse is
        // ever returned. The output guardrails must accept this regardless of argument content
        // — argument semantics are a concern for the downstream converter, not the guardrail.
        AgenticResponse response = buildExecuteToolResponse(
            "sess_og_004",
            TOOL_ASK_A_PERSON,
            Map.of(
                "outputVariableNames", java.util.List.of("productName", "quantity", "customerRef"),
                "question", "Please provide product name, quantity, and customer reference"
            )
        );

        GuardrailResults result = OutputGuardrails.validateResponse(response);

        assertFalse(result.isBlocked(),
            "EXECUTE_TOOL for ask_a_person with multiple outputVariableNames in arguments must "
            + "not be blocked by output guardrails. The guardrail validates structure, not "
            + "argument semantics — the converter handles outputVariableNames downstream");
    }

    // -----------------------------------------------------------------------
    // TC-OG-5: EXECUTE_TOOL for ask_a_person with empty arguments map is accepted
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("TC-OG-5: EXECUTE_TOOL for ask_a_person with empty arguments map is accepted")
    void validateResponse_executeTool_askAPerson_emptyArguments_isAccepted() {

        AgenticResponse response = buildExecuteToolResponse(
            "sess_og_005",
            TOOL_ASK_A_PERSON,
            Map.of()
        );

        GuardrailResults result = OutputGuardrails.validateResponse(response);

        assertFalse(result.isBlocked(),
            "EXECUTE_TOOL for ask_a_person with empty arguments must be accepted — "
            + "arguments can be an empty map per the spec");
    }

    // -----------------------------------------------------------------------
    // TC-OG-6: EXECUTE_TOOL for each valid toolType is accepted
    // -----------------------------------------------------------------------

    @ParameterizedTest(name = "TC-OG-6 [{index}]: EXECUTE_TOOL for {0} is accepted")
    @ValueSource(strings = {
        "{\"toolType\":\"catalog_request\",\"invokerId\":\"INV_001\",\"catalogRequestId\":\"req_xyz\"}",
        "{\"toolType\":\"invoker\",\"invokerId\":\"inv_abc\"}",
        "{\"toolType\":\"entity\",\"entityId\":\"Customer\",\"operationType\":\"ADD\"}",
        "{\"toolType\":\"inform_a_person\"}",
        "{\"toolType\":\"conversation\",\"channelName\":\"ch1\",\"conversationName\":\"HR\"}",
        "{\"toolType\":\"ask_a_person\",\"humanInteractionId\":\"builtin_ask_person\"}"
    })
    @DisplayName("TC-OG-6: EXECUTE_TOOL for each known valid toolType is accepted")
    void validateResponse_executeTool_allValidToolTypes_areAccepted(String toolNameJson) {

        AgenticResponse response = buildExecuteToolResponse(
            "sess_og_006",
            toolNameJson,
            Map.of("param", "value")
        );

        GuardrailResults result = OutputGuardrails.validateResponse(response);

        assertFalse(result.isBlocked(),
            "EXECUTE_TOOL for toolName '" + toolNameJson + "' must be accepted");
    }

    // -----------------------------------------------------------------------
    // TC-OG-7: EXECUTE_TOOL with unknown toolType is blocked
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("TC-OG-7: EXECUTE_TOOL with unknown toolType is blocked")
    void validateResponse_executeTool_unknownToolType_isBlocked() {

        AgenticResponse response = buildExecuteToolResponse(
            "sess_og_007",
            "{\"toolType\":\"user_defined_custom_tool\"}",
            Map.of("param", "value")
        );

        GuardrailResults result = OutputGuardrails.validateResponse(response);

        assertTrue(result.isBlocked(),
            "EXECUTE_TOOL with an unrecognized toolType must be blocked to prevent the LLM "
            + "from hallucinating tool types that do not exist in the system");
    }

    // -----------------------------------------------------------------------
    // TC-OG-8: EXECUTE_TOOL with null command is blocked
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("TC-OG-8: EXECUTE_TOOL with null command is blocked")
    void validateResponse_executeTool_nullCommand_isBlocked() {

        AgenticResponse response = new AgenticResponse();
        response.setSessionId("sess_og_008");
        response.setResponseType(AgenticResponse.ResponseType.EXECUTE_TOOL);
        response.setCommand(null);

        GuardrailResults result = OutputGuardrails.validateResponse(response);

        assertTrue(result.isBlocked(),
            "EXECUTE_TOOL with null command must be blocked");
    }

    // -----------------------------------------------------------------------
    // TC-OG-9: EXECUTE_TOOL with null toolName is blocked
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("TC-OG-9: EXECUTE_TOOL with null toolName is blocked")
    void validateResponse_executeTool_nullToolName_isBlocked() {

        AgenticResponse response = new AgenticResponse();
        response.setSessionId("sess_og_009");
        response.setResponseType(AgenticResponse.ResponseType.EXECUTE_TOOL);
        response.setCommand(new AgenticCommand(null, Map.of()));

        GuardrailResults result = OutputGuardrails.validateResponse(response);

        assertTrue(result.isBlocked(),
            "EXECUTE_TOOL with null toolName must be blocked");
    }

    // -----------------------------------------------------------------------
    // TC-OG-10: EXECUTE_TOOL with null arguments is blocked
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("TC-OG-10: EXECUTE_TOOL with null arguments map is blocked")
    void validateResponse_executeTool_nullArguments_isBlocked() {

        AgenticResponse response = new AgenticResponse();
        response.setSessionId("sess_og_010");
        response.setResponseType(AgenticResponse.ResponseType.EXECUTE_TOOL);
        response.setCommand(new AgenticCommand(TOOL_ASK_A_PERSON, null));

        GuardrailResults result = OutputGuardrails.validateResponse(response);

        assertTrue(result.isBlocked(),
            "EXECUTE_TOOL with null arguments must be blocked — arguments must be at minimum an empty map");
    }

    // -----------------------------------------------------------------------
    // TC-OG-11: EXECUTE_TOOL with malformed toolName JSON is blocked
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("TC-OG-11: EXECUTE_TOOL with malformed toolName JSON is blocked")
    void validateResponse_executeTool_malformedToolNameJson_isBlocked() {

        AgenticResponse response = buildExecuteToolResponse(
            "sess_og_011",
            "{\"toolType\":",       // truncated JSON — parse failure
            Map.of()
        );

        GuardrailResults result = OutputGuardrails.validateResponse(response);

        assertTrue(result.isBlocked(),
            "EXECUTE_TOOL with malformed JSON toolName must be blocked");
    }

    // -----------------------------------------------------------------------
    // TC-OG-12: EXECUTE_TOOL with toolName missing toolType field is blocked
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("TC-OG-12: EXECUTE_TOOL with toolName JSON missing 'toolType' field is blocked")
    void validateResponse_executeTool_toolNameMissingToolType_isBlocked() {

        AgenticResponse response = buildExecuteToolResponse(
            "sess_og_012",
            "{\"humanInteractionId\":\"builtin_ask_person\"}",   // no toolType
            Map.of()
        );

        GuardrailResults result = OutputGuardrails.validateResponse(response);

        assertTrue(result.isBlocked(),
            "EXECUTE_TOOL with toolName JSON that lacks 'toolType' must be blocked");
    }

    // -----------------------------------------------------------------------
    // TC-OG-13: COMPLETE with finalResponse is accepted
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("TC-OG-13: COMPLETE with non-empty finalResponse is accepted")
    void validateResponse_complete_withFinalResponse_isAccepted() {

        AgenticResponse response = new AgenticResponse();
        response.setSessionId("sess_og_013");
        response.setResponseType(AgenticResponse.ResponseType.COMPLETE);
        response.setFinalResponse("Order placed successfully for John with Widget A");
        response.setOutputVariables(Map.of("orderId", "12345"));

        GuardrailResults result = OutputGuardrails.validateResponse(response);

        assertFalse(result.isBlocked(),
            "COMPLETE with a non-empty finalResponse must be accepted");
    }

    // -----------------------------------------------------------------------
    // TC-OG-14: COMPLETE with null finalResponse is blocked
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("TC-OG-14: COMPLETE with null finalResponse is blocked")
    void validateResponse_complete_nullFinalResponse_isBlocked() {

        AgenticResponse response = new AgenticResponse();
        response.setSessionId("sess_og_014");
        response.setResponseType(AgenticResponse.ResponseType.COMPLETE);
        response.setFinalResponse(null);

        GuardrailResults result = OutputGuardrails.validateResponse(response);

        assertTrue(result.isBlocked(),
            "COMPLETE with null finalResponse must be blocked");
    }

    // -----------------------------------------------------------------------
    // TC-OG-15: COMPLETE with blank finalResponse is blocked
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("TC-OG-15: COMPLETE with blank finalResponse is blocked")
    void validateResponse_complete_blankFinalResponse_isBlocked() {

        AgenticResponse response = new AgenticResponse();
        response.setSessionId("sess_og_015");
        response.setResponseType(AgenticResponse.ResponseType.COMPLETE);
        response.setFinalResponse("   ");

        GuardrailResults result = OutputGuardrails.validateResponse(response);

        assertTrue(result.isBlocked(),
            "COMPLETE with blank finalResponse must be blocked");
    }

    // -----------------------------------------------------------------------
    // TC-OG-16: ERROR with errorMessage is accepted
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("TC-OG-16: ERROR with non-empty errorMessage is accepted")
    void validateResponse_error_withErrorMessage_isAccepted() {

        AgenticResponse response = new AgenticResponse();
        response.setSessionId("sess_og_016");
        response.setResponseType(AgenticResponse.ResponseType.ERROR);
        response.setErrorMessage("Cannot proceed: product catalog is unavailable");

        GuardrailResults result = OutputGuardrails.validateResponse(response);

        assertFalse(result.isBlocked(),
            "ERROR with a non-empty errorMessage must be accepted");
    }

    // -----------------------------------------------------------------------
    // TC-OG-17: ERROR with null errorMessage is blocked
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("TC-OG-17: ERROR with null errorMessage is blocked")
    void validateResponse_error_nullErrorMessage_isBlocked() {

        AgenticResponse response = new AgenticResponse();
        response.setSessionId("sess_og_017");
        response.setResponseType(AgenticResponse.ResponseType.ERROR);
        response.setErrorMessage(null);

        GuardrailResults result = OutputGuardrails.validateResponse(response);

        assertTrue(result.isBlocked(),
            "ERROR with null errorMessage must be blocked");
    }

    // -----------------------------------------------------------------------
    // TC-OG-18: sanitizeResponse trims string fields and preserves command arguments
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("TC-OG-18: sanitizeResponse trims finalResponse and errorMessage whitespace")
    void sanitizeResponse_trimsStringFields() {

        AgenticResponse response = new AgenticResponse();
        response.setSessionId("sess_og_018");
        response.setResponseType(AgenticResponse.ResponseType.COMPLETE);
        response.setFinalResponse("  Task done  ");
        response.setErrorMessage("  Some error  ");

        AgenticResponse sanitized = OutputGuardrails.sanitizeResponse(response);

        assertEquals("Task done", sanitized.getFinalResponse(),
            "sanitizeResponse must trim leading/trailing whitespace from finalResponse");
        assertEquals("Some error", sanitized.getErrorMessage(),
            "sanitizeResponse must trim leading/trailing whitespace from errorMessage");
    }

    // -----------------------------------------------------------------------
    // TC-OG-19: sanitizeResponse trims toolName in command
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("TC-OG-19: sanitizeResponse trims whitespace from command toolName")
    void sanitizeResponse_trimsCommandToolName() {

        AgenticResponse response = new AgenticResponse();
        response.setSessionId("sess_og_019");
        response.setResponseType(AgenticResponse.ResponseType.EXECUTE_TOOL);
        response.setCommand(new AgenticCommand("  " + TOOL_ASK_A_PERSON + "  ", Map.of("q", "v")));

        AgenticResponse sanitized = OutputGuardrails.sanitizeResponse(response);

        assertNotNull(sanitized.getCommand(),
            "Command must not be null after sanitization");
        assertEquals(TOOL_ASK_A_PERSON, sanitized.getCommand().toolName(),
            "sanitizeResponse must trim whitespace from command toolName");
        assertEquals(Map.of("q", "v"), sanitized.getCommand().arguments(),
            "Arguments must be preserved unchanged during sanitization");
    }

    // -----------------------------------------------------------------------
    // TC-OG-20: sanitizeResponse with null response returns null
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("TC-OG-20: sanitizeResponse with null input returns null (no NPE)")
    void sanitizeResponse_nullInput_returnsNull() {

        AgenticResponse result = OutputGuardrails.sanitizeResponse(null);

        assertNull(result,
            "sanitizeResponse must return null when input is null, not throw NPE");
    }

    // -----------------------------------------------------------------------
    // TC-OG-21: conversation toolType missing channelName is blocked
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("TC-OG-21: EXECUTE_TOOL conversation toolType without channelName is blocked")
    void validateResponse_executeTool_conversationMissingChannelName_isBlocked() {

        AgenticResponse response = buildExecuteToolResponse(
            "sess_og_021",
            "{\"toolType\":\"conversation\",\"conversationName\":\"HR Onboarding\"}",  // no channelName
            Map.of()
        );

        GuardrailResults result = OutputGuardrails.validateResponse(response);

        assertTrue(result.isBlocked(),
            "conversation toolType without channelName must be blocked");
    }

    // -----------------------------------------------------------------------
    // TC-OG-22: conversation toolType missing conversationName is blocked
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("TC-OG-22: EXECUTE_TOOL conversation toolType without conversationName is blocked")
    void validateResponse_executeTool_conversationMissingConversationName_isBlocked() {

        AgenticResponse response = buildExecuteToolResponse(
            "sess_og_022",
            "{\"toolType\":\"conversation\",\"channelName\":\"ch1\"}",  // no conversationName
            Map.of()
        );

        GuardrailResults result = OutputGuardrails.validateResponse(response);

        assertTrue(result.isBlocked(),
            "conversation toolType without conversationName must be blocked");
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private AgenticResponse buildExecuteToolResponse(String sessionId,
                                                     String toolName,
                                                     Map<String, Object> arguments) {

        AgenticResponse response = new AgenticResponse();
        response.setSessionId(sessionId);
        response.setResponseType(AgenticResponse.ResponseType.EXECUTE_TOOL);
        response.setConfidence(0.9);
        response.setCommand(new AgenticCommand(toolName, arguments));
        return response;
    }
}
