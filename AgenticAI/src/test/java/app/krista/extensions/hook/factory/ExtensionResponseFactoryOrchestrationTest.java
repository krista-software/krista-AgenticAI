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

package app.krista.extensions.hook.factory;

import app.krista.extension.executor.ExtensionResponse;
import app.krista.extension.executor.RemediationActions;
import app.krista.extension.executor.impl.ExecuteToolAction;
import app.krista.extension.executor.impl.FlowCompletionAction;
import app.krista.extension.executor.impl.InformAPersonAction;
import app.krista.extension.executor.impl.InformStepType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the six new orchestration / agentic-flow methods on
 * {@link ExtensionResponseFactory}.
 *
 * For every method the contract is:
 * <ol>
 *   <li>Returns a non-null {@link ExtensionResponse}</li>
 *   <li>{@link ExtensionResponse#getResult()} is {@code SUCCESS}</li>
 *   <li>{@link ExtensionResponse#getRemediationActions()} is non-null with exactly one action</li>
 *   <li>The action is the expected concrete type</li>
 *   <li>Output variables passed in are carried on the response (where applicable)</li>
 * </ol>
 *
 * No external dependencies required; all tests operate on pure in-memory data.
 */
@DisplayName("ExtensionResponseFactory — orchestration methods")
class ExtensionResponseFactoryOrchestrationTest {

    // =========================================================================
    // createFallbackErrorResponse
    // =========================================================================

    @Test
    @DisplayName("createFallbackErrorResponse: returns non-null SUCCESS response")
    void createFallbackErrorResponse_returnsSuccessResponse() {

        ExtensionResponse response = ExtensionResponseFactory.createFallbackErrorResponse();

        assertNotNull(response, "Response must not be null");
        assertEquals(ExtensionResponse.Result.SUCCESS, response.getResult(),
            "Fallback error response must have result SUCCESS");
    }

    @Test
    @DisplayName("createFallbackErrorResponse: has exactly one InformAPersonAction")
    void createFallbackErrorResponse_hasExactlyOneInformAPersonAction() {

        ExtensionResponse response = ExtensionResponseFactory.createFallbackErrorResponse();

        RemediationActions remediationActions = response.getRemediationActions();
        assertNotNull(remediationActions, "RemediationActions must not be null");
        assertNotNull(remediationActions.getRemediationActionList(),
            "RemediationAction list must not be null");
        assertEquals(1, remediationActions.getRemediationActionList().size(),
            "Exactly one action expected in fallback response");

        Object action = remediationActions.getRemediationActionList().get(0);
        assertInstanceOf(InformAPersonAction.class, action,
            "Fallback action must be an InformAPersonAction");
    }

    @Test
    @DisplayName("createFallbackErrorResponse: outputVars is null")
    void createFallbackErrorResponse_outputVarsIsNull() {

        ExtensionResponse response = ExtensionResponseFactory.createFallbackErrorResponse();

        assertNull(response.getResponseValue(),
            "Fallback response must have null outputVars (responseValue)");
    }

    // =========================================================================
    // createExecuteToolResponse
    // =========================================================================

    @Test
    @DisplayName("createExecuteToolResponse: returns non-null SUCCESS response")
    void createExecuteToolResponse_returnsSuccessResponse() {

        ExtensionResponse response = ExtensionResponseFactory.createExecuteToolResponse(
            "sess_001", "my_tool", Map.of("arg1", "val1"), Map.of("out1", "result1"));

        assertNotNull(response, "Response must not be null");
        assertEquals(ExtensionResponse.Result.SUCCESS, response.getResult());
    }

    @Test
    @DisplayName("createExecuteToolResponse: has exactly one ExecuteToolAction")
    void createExecuteToolResponse_hasExactlyOneExecuteToolAction() {

        ExtensionResponse response = ExtensionResponseFactory.createExecuteToolResponse(
            "sess_001", "my_tool", Map.of(), Map.of());

        RemediationActions remediationActions = response.getRemediationActions();
        assertNotNull(remediationActions, "RemediationActions must not be null");
        assertEquals(1, remediationActions.getRemediationActionList().size(),
            "Exactly one action expected");

        Object action = remediationActions.getRemediationActionList().get(0);
        assertInstanceOf(ExecuteToolAction.class, action,
            "Action must be an ExecuteToolAction");
    }

    @Test
    @DisplayName("createExecuteToolResponse: outputVars present on response")
    void createExecuteToolResponse_outputVarsPresentOnResponse() {

        Map<String, Object> outputVars = Map.of("myKey", "myValue");

        ExtensionResponse response = ExtensionResponseFactory.createExecuteToolResponse(
            "sess_001", "my_tool", Map.of(), outputVars);

        assertNotNull(response.getResponseValue(),
            "ResponseValue (outputVars) must not be null");
        assertEquals("myValue", response.getResponseValue().get("myKey"),
            "OutputVars must be present on the response responseValue");
    }

    // =========================================================================
    // createParentCompleteResponse
    // =========================================================================

    @Test
    @DisplayName("createParentCompleteResponse: returns non-null SUCCESS response")
    void createParentCompleteResponse_returnsSuccessResponse() {

        ExtensionResponse response = ExtensionResponseFactory.createParentCompleteResponse(
            "sess_parent", "All done", Map.of("output", "value"));

        assertNotNull(response, "Response must not be null");
        assertEquals(ExtensionResponse.Result.SUCCESS, response.getResult());
    }

    @Test
    @DisplayName("createParentCompleteResponse: has exactly one FlowCompletionAction")
    void createParentCompleteResponse_hasExactlyOneFlowCompletionAction() {

        ExtensionResponse response = ExtensionResponseFactory.createParentCompleteResponse(
            "sess_parent", "All done", Map.of());

        RemediationActions remediationActions = response.getRemediationActions();
        assertNotNull(remediationActions, "RemediationActions must not be null");
        assertEquals(1, remediationActions.getRemediationActionList().size(),
            "Exactly one action expected");

        Object action = remediationActions.getRemediationActionList().get(0);
        assertInstanceOf(FlowCompletionAction.class, action,
            "Action must be a FlowCompletionAction");
    }

    @Test
    @DisplayName("createParentCompleteResponse: outputVars present on response")
    void createParentCompleteResponse_outputVarsPresentOnResponse() {

        Map<String, Object> outputVars = Map.of("summary", "done");

        ExtensionResponse response = ExtensionResponseFactory.createParentCompleteResponse(
            "sess_parent", "All done", outputVars);

        assertNotNull(response.getResponseValue(),
            "ResponseValue (outputVars) must not be null");
        assertEquals("done", response.getResponseValue().get("summary"),
            "OutputVars must be present on the response");
    }

    @Test
    @DisplayName("createParentCompleteResponse: FlowCompletionAction is NOT returnToParent")
    void createParentCompleteResponse_flowCompletionAction_isNotReturnToParent() {

        ExtensionResponse response = ExtensionResponseFactory.createParentCompleteResponse(
            "sess_parent", "All done", Map.of());

        FlowCompletionAction action =
            (FlowCompletionAction) response.getRemediationActions().getRemediationActionList().get(0);

        assertFalse(action.isReturnToParent(),
            "Parent complete action must NOT be return-to-parent");
    }

    // =========================================================================
    // createChildCompleteResponse
    // =========================================================================

    @Test
    @DisplayName("createChildCompleteResponse: returns non-null SUCCESS response")
    void createChildCompleteResponse_returnsSuccessResponse() {

        ExtensionResponse response = ExtensionResponseFactory.createChildCompleteResponse(
            "sess_parent", "Child done", Map.of("childKey", "childVal"));

        assertNotNull(response, "Response must not be null");
        assertEquals(ExtensionResponse.Result.SUCCESS, response.getResult());
    }

    @Test
    @DisplayName("createChildCompleteResponse: has exactly one FlowCompletionAction")
    void createChildCompleteResponse_hasExactlyOneFlowCompletionAction() {

        ExtensionResponse response = ExtensionResponseFactory.createChildCompleteResponse(
            "sess_parent", "Child done", Map.of());

        RemediationActions remediationActions = response.getRemediationActions();
        assertNotNull(remediationActions, "RemediationActions must not be null");
        assertEquals(1, remediationActions.getRemediationActionList().size(),
            "Exactly one action expected");

        Object action = remediationActions.getRemediationActionList().get(0);
        assertInstanceOf(FlowCompletionAction.class, action,
            "Action must be a FlowCompletionAction");
    }

    @Test
    @DisplayName("createChildCompleteResponse: outputVars present on response")
    void createChildCompleteResponse_outputVarsPresentOnResponse() {

        Map<String, Object> outputVars = Map.of("childResult", "success");

        ExtensionResponse response = ExtensionResponseFactory.createChildCompleteResponse(
            "sess_parent", "Child done", outputVars);

        assertNotNull(response.getResponseValue());
        assertEquals("success", response.getResponseValue().get("childResult"),
            "OutputVars must be carried forward to the response");
    }

    @Test
    @DisplayName("createChildCompleteResponse: FlowCompletionAction isReturnToParent=true")
    void createChildCompleteResponse_flowCompletionAction_isReturnToParent() {

        ExtensionResponse response = ExtensionResponseFactory.createChildCompleteResponse(
            "sess_parent", "Child done", Map.of());

        FlowCompletionAction action =
            (FlowCompletionAction) response.getRemediationActions().getRemediationActionList().get(0);

        assertTrue(action.isReturnToParent(),
            "Child complete action must be return-to-parent");
    }

    // =========================================================================
    // createParentErrorResponse
    // =========================================================================

    @Test
    @DisplayName("createParentErrorResponse: returns non-null SUCCESS response")
    void createParentErrorResponse_returnsSuccessResponse() {

        ExtensionResponse response = ExtensionResponseFactory.createParentErrorResponse(
            "sess_parent_err", "Timeout occurred", Map.of());

        assertNotNull(response, "Response must not be null");
        assertEquals(ExtensionResponse.Result.SUCCESS, response.getResult());
    }

    @Test
    @DisplayName("createParentErrorResponse: has exactly one FlowCompletionAction")
    void createParentErrorResponse_hasExactlyOneFlowCompletionAction() {

        ExtensionResponse response = ExtensionResponseFactory.createParentErrorResponse(
            "sess_parent_err", "Timeout occurred", Map.of());

        RemediationActions remediationActions = response.getRemediationActions();
        assertNotNull(remediationActions, "RemediationActions must not be null");
        assertEquals(1, remediationActions.getRemediationActionList().size(),
            "Exactly one action expected");

        Object action = remediationActions.getRemediationActionList().get(0);
        assertInstanceOf(FlowCompletionAction.class, action,
            "Action must be a FlowCompletionAction");
    }

    @Test
    @DisplayName("createParentErrorResponse: outputVars present on response")
    void createParentErrorResponse_outputVarsPresentOnResponse() {

        Map<String, Object> outputVars = Map.of("errorContext", "partial_data");

        ExtensionResponse response = ExtensionResponseFactory.createParentErrorResponse(
            "sess_parent_err", "Timeout occurred", outputVars);

        assertNotNull(response.getResponseValue());
        assertEquals("partial_data", response.getResponseValue().get("errorContext"),
            "OutputVars must be carried on the error response");
    }

    @Test
    @DisplayName("createParentErrorResponse: FlowCompletionAction is NOT returnToParent")
    void createParentErrorResponse_flowCompletionAction_isNotReturnToParent() {

        ExtensionResponse response = ExtensionResponseFactory.createParentErrorResponse(
            "sess_parent_err", "Timeout occurred", Map.of());

        FlowCompletionAction action =
            (FlowCompletionAction) response.getRemediationActions().getRemediationActionList().get(0);

        assertFalse(action.isReturnToParent(),
            "Parent error action must NOT be return-to-parent");
    }

    // =========================================================================
    // createChildErrorResponse
    // =========================================================================

    @Test
    @DisplayName("createChildErrorResponse: returns non-null SUCCESS response")
    void createChildErrorResponse_returnsSuccessResponse() {

        ExtensionResponse response = ExtensionResponseFactory.createChildErrorResponse(
            "sess_parent_001", "Child tool failed", Map.of());

        assertNotNull(response, "Response must not be null");
        assertEquals(ExtensionResponse.Result.SUCCESS, response.getResult());
    }

    @Test
    @DisplayName("createChildErrorResponse: has exactly one FlowCompletionAction")
    void createChildErrorResponse_hasExactlyOneFlowCompletionAction() {

        ExtensionResponse response = ExtensionResponseFactory.createChildErrorResponse(
            "sess_parent_001", "Child tool failed", Map.of());

        RemediationActions remediationActions = response.getRemediationActions();
        assertNotNull(remediationActions, "RemediationActions must not be null");
        assertEquals(1, remediationActions.getRemediationActionList().size(),
            "Exactly one action expected");

        Object action = remediationActions.getRemediationActionList().get(0);
        assertInstanceOf(FlowCompletionAction.class, action,
            "Action must be a FlowCompletionAction");
    }

    @Test
    @DisplayName("createChildErrorResponse: outputVars present on response")
    void createChildErrorResponse_outputVarsPresentOnResponse() {

        Map<String, Object> outputVars = Map.of("partialOutput", "some data");

        ExtensionResponse response = ExtensionResponseFactory.createChildErrorResponse(
            "sess_parent_001", "Child tool failed", outputVars);

        assertNotNull(response.getResponseValue());
        assertEquals("some data", response.getResponseValue().get("partialOutput"),
            "OutputVars must be carried on the child error response");
    }

    @Test
    @DisplayName("createChildErrorResponse: FlowCompletionAction isReturnToParent=true")
    void createChildErrorResponse_flowCompletionAction_isReturnToParent() {

        ExtensionResponse response = ExtensionResponseFactory.createChildErrorResponse(
            "sess_parent_001", "Child tool failed", Map.of());

        FlowCompletionAction action =
            (FlowCompletionAction) response.getRemediationActions().getRemediationActionList().get(0);

        assertTrue(action.isReturnToParent(),
            "Child error action must be return-to-parent");
    }

    // =========================================================================
    // Status message overloads
    // =========================================================================

    @Test
    @DisplayName("createExecuteToolResponse 6-param: statusMessage and informStepType carried on action")
    void createExecuteToolResponse_6param_statusMessageCarried() {

        ExtensionResponse response = ExtensionResponseFactory.createExecuteToolResponse(
            "s1", "t1", Map.of(), Map.of(), "Running...", InformStepType.STATUS);

        ExecuteToolAction action =
            (ExecuteToolAction) response.getRemediationActions().getRemediationActionList().get(0);

        assertEquals("Running...", action.getStatusMessage(),
            "Status message must be carried through to the ExecuteToolAction");
        assertEquals(InformStepType.STATUS, action.getInformStepType(),
            "InformStepType must be STATUS when explicitly provided");
    }

    @Test
    @DisplayName("createExecuteToolResponse 6-param: null statusMessage and null informStepType default to STANDARD")
    void createExecuteToolResponse_6param_nullStatusMessage() {

        ExtensionResponse response = ExtensionResponseFactory.createExecuteToolResponse(
            "s1", "t1", Map.of(), Map.of(), null, null);

        ExecuteToolAction action =
            (ExecuteToolAction) response.getRemediationActions().getRemediationActionList().get(0);

        assertNull(action.getStatusMessage(),
            "Status message must be null when null was passed");
        assertEquals(InformStepType.STANDARD, action.getInformStepType(),
            "InformStepType must default to STANDARD when null is passed");
    }

    @Test
    @DisplayName("createParentCompleteResponse 5-param: statusMessage carried on FlowCompletionAction")
    void createParentCompleteResponse_5param_statusMessageCarried() {

        ExtensionResponse response = ExtensionResponseFactory.createParentCompleteResponse(
            "s1", "done", Map.of(), "Done", InformStepType.STANDARD);

        FlowCompletionAction action =
            (FlowCompletionAction) response.getRemediationActions().getRemediationActionList().get(0);

        assertEquals("Done", action.getStatusMessage(),
            "Status message must be carried through to the FlowCompletionAction");
        assertEquals(InformStepType.STANDARD, action.getInformStepType(),
            "InformStepType must be STANDARD when explicitly provided");
    }

    @Test
    @DisplayName("createChildCompleteResponse 5-param: statusMessage carried on FlowCompletionAction")
    void createChildCompleteResponse_5param_statusMessageCarried() {

        ExtensionResponse response = ExtensionResponseFactory.createChildCompleteResponse(
            "s1", "done", Map.of(), "Sub-task done", InformStepType.STANDARD);

        FlowCompletionAction action =
            (FlowCompletionAction) response.getRemediationActions().getRemediationActionList().get(0);

        assertEquals("Sub-task done", action.getStatusMessage(),
            "Status message must be carried through to the child FlowCompletionAction");
        assertEquals(InformStepType.STANDARD, action.getInformStepType(),
            "InformStepType must be STANDARD when explicitly provided");
    }

    @Test
    @DisplayName("createParentErrorResponse 5-param: statusMessage carried on FlowCompletionAction")
    void createParentErrorResponse_5param_statusMessageCarried() {

        ExtensionResponse response = ExtensionResponseFactory.createParentErrorResponse(
            "s1", "err", Map.of(), "Error", InformStepType.STANDARD);

        FlowCompletionAction action =
            (FlowCompletionAction) response.getRemediationActions().getRemediationActionList().get(0);

        assertEquals("Error", action.getStatusMessage(),
            "Status message must be carried through to the parent error FlowCompletionAction");
        assertEquals(InformStepType.STANDARD, action.getInformStepType(),
            "InformStepType must be STANDARD when explicitly provided");
    }

    @Test
    @DisplayName("createChildErrorResponse 5-param: statusMessage carried on FlowCompletionAction")
    void createChildErrorResponse_5param_statusMessageCarried() {

        ExtensionResponse response = ExtensionResponseFactory.createChildErrorResponse(
            "s1", "err", Map.of(), "Sub-error", InformStepType.STANDARD);

        FlowCompletionAction action =
            (FlowCompletionAction) response.getRemediationActions().getRemediationActionList().get(0);

        assertEquals("Sub-error", action.getStatusMessage(),
            "Status message must be carried through to the child error FlowCompletionAction");
        assertEquals(InformStepType.STANDARD, action.getInformStepType(),
            "InformStepType must be STANDARD when explicitly provided");
    }

    @Test
    @DisplayName("createExecuteToolResponse 4-param: delegates with null statusMessage and informStepType")
    void createExecuteToolResponse_4param_delegatesWithNulls() {

        ExtensionResponse response = ExtensionResponseFactory.createExecuteToolResponse(
            "s1", "t1", Map.of(), Map.of());

        ExecuteToolAction action =
            (ExecuteToolAction) response.getRemediationActions().getRemediationActionList().get(0);

        assertNull(action.getStatusMessage(),
            "4-param overload must delegate with null statusMessage");
        assertEquals(InformStepType.STANDARD, action.getInformStepType(),
            "4-param overload must default informStepType to STANDARD");
    }

    @Test
    @DisplayName("createParentCompleteResponse 3-param: delegates with null statusMessage")
    void createParentCompleteResponse_3param_delegatesWithNulls() {

        ExtensionResponse response = ExtensionResponseFactory.createParentCompleteResponse(
            "s1", "done", Map.of());

        FlowCompletionAction action =
            (FlowCompletionAction) response.getRemediationActions().getRemediationActionList().get(0);

        assertNull(action.getStatusMessage(),
            "3-param overload must delegate with null statusMessage");
        assertEquals(InformStepType.STANDARD, action.getInformStepType(),
            "3-param overload must default informStepType to STANDARD");
    }

    @Test
    @DisplayName("createChildCompleteResponse 3-param: delegates with null statusMessage")
    void createChildCompleteResponse_3param_delegatesWithNulls() {

        ExtensionResponse response = ExtensionResponseFactory.createChildCompleteResponse(
            "s1", "done", Map.of());

        FlowCompletionAction action =
            (FlowCompletionAction) response.getRemediationActions().getRemediationActionList().get(0);

        assertNull(action.getStatusMessage(),
            "3-param overload must delegate with null statusMessage");
        assertEquals(InformStepType.STANDARD, action.getInformStepType(),
            "3-param overload must default informStepType to STANDARD");
    }

    @Test
    @DisplayName("createParentErrorResponse 3-param: delegates with null statusMessage")
    void createParentErrorResponse_3param_delegatesWithNulls() {

        ExtensionResponse response = ExtensionResponseFactory.createParentErrorResponse(
            "s1", "err", Map.of());

        FlowCompletionAction action =
            (FlowCompletionAction) response.getRemediationActions().getRemediationActionList().get(0);

        assertNull(action.getStatusMessage(),
            "3-param overload must delegate with null statusMessage");
        assertEquals(InformStepType.STANDARD, action.getInformStepType(),
            "3-param overload must default informStepType to STANDARD");
    }

    @Test
    @DisplayName("createChildErrorResponse 3-param: delegates with null statusMessage")
    void createChildErrorResponse_3param_delegatesWithNulls() {

        ExtensionResponse response = ExtensionResponseFactory.createChildErrorResponse(
            "s1", "err", Map.of());

        FlowCompletionAction action =
            (FlowCompletionAction) response.getRemediationActions().getRemediationActionList().get(0);

        assertNull(action.getStatusMessage(),
            "3-param overload must delegate with null statusMessage");
        assertEquals(InformStepType.STANDARD, action.getInformStepType(),
            "3-param overload must default informStepType to STANDARD");
    }

    @Test
    @DisplayName("createParentCompleteResponse 5-param: null statusMessage and null informStepType default to STANDARD")
    void createParentCompleteResponse_5param_nullStatusMessage() {

        ExtensionResponse response = ExtensionResponseFactory.createParentCompleteResponse(
            "s1", "done", Map.of(), null, null);

        FlowCompletionAction action =
            (FlowCompletionAction) response.getRemediationActions().getRemediationActionList().get(0);

        assertNull(action.getStatusMessage(),
            "Status message must be null when null was passed");
        assertEquals(InformStepType.STANDARD, action.getInformStepType(),
            "InformStepType must default to STANDARD when null is passed");
    }

    @Test
    @DisplayName("createChildCompleteResponse 5-param: null statusMessage and null informStepType default to STANDARD")
    void createChildCompleteResponse_5param_nullStatusMessage() {

        ExtensionResponse response = ExtensionResponseFactory.createChildCompleteResponse(
            "s1", "done", Map.of(), null, null);

        FlowCompletionAction action =
            (FlowCompletionAction) response.getRemediationActions().getRemediationActionList().get(0);

        assertNull(action.getStatusMessage(),
            "Status message must be null when null was passed");
        assertEquals(InformStepType.STANDARD, action.getInformStepType(),
            "InformStepType must default to STANDARD when null is passed");
    }

    @Test
    @DisplayName("createParentErrorResponse 5-param: null statusMessage and null informStepType default to STANDARD")
    void createParentErrorResponse_5param_nullStatusMessage() {

        ExtensionResponse response = ExtensionResponseFactory.createParentErrorResponse(
            "s1", "err", Map.of(), null, null);

        FlowCompletionAction action =
            (FlowCompletionAction) response.getRemediationActions().getRemediationActionList().get(0);

        assertNull(action.getStatusMessage(),
            "Status message must be null when null was passed");
        assertEquals(InformStepType.STANDARD, action.getInformStepType(),
            "InformStepType must default to STANDARD when null is passed");
    }

    @Test
    @DisplayName("createChildErrorResponse 5-param: null statusMessage and null informStepType default to STANDARD")
    void createChildErrorResponse_5param_nullStatusMessage() {

        ExtensionResponse response = ExtensionResponseFactory.createChildErrorResponse(
            "s1", "err", Map.of(), null, null);

        FlowCompletionAction action =
            (FlowCompletionAction) response.getRemediationActions().getRemediationActionList().get(0);

        assertNull(action.getStatusMessage(),
            "Status message must be null when null was passed");
        assertEquals(InformStepType.STANDARD, action.getInformStepType(),
            "InformStepType must default to STANDARD when null is passed");
    }
}
