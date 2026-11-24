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

package app.krista.extensions.hook.agentic.converter;

import app.krista.extension.executor.ExtensionResponse;
import app.krista.extension.executor.RemediationActions;
import app.krista.extension.executor.impl.ExecuteToolAction;
import app.krista.extension.executor.impl.FlowCompletionAction;
import app.krista.extension.executor.impl.InformStepType;
import app.krista.extension.executor.impl.InformAPersonAction;
import app.krista.extensions.hook.agentic.AgenticConstants;
import app.krista.extensions.hook.agentic.dto.AgenticCommand;
import app.krista.extensions.hook.agentic.dto.AgenticErrorCode;
import app.krista.extensions.hook.agentic.dto.AgenticResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import app.krista.extensions.hook.agentic.dto.AgenticToolDefinition;
import app.krista.extensions.hook.agentic.service.AgenticFlowService;
import app.krista.extensions.hook.agentic.session.AgenticSession;
import app.krista.extensions.hook.agentic.usagecollector.SessionUsageCollector;
import app.krista.extensions.hook.agentic.usagecollector.SessionUsageCollector.SessionStatus;
import dev.langchain4j.memory.ChatMemory;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for AgenticResponseConverter — stateful converter that translates
 * AgenticResponse objects to ExtensionResponse objects while applying
 * parent-child session routing logic.
 *
 * Tests are structured in three sections:
 *   1. Session ID helper methods (pure logic, no I/O)
 *   2. toExtensionResponse with null / malformed input
 *   3. toExtensionResponse for EXECUTE_TOOL, COMPLETE, and ERROR response types
 */
@DisplayName("AgenticResponseConverter")
class AgenticResponseConverterTest {

    private AgenticFlowService agenticFlowService;
    private Map<Object, ChatMemory> chatMemoryMap;
    private AgenticResponseConverter converter;

    // Tool name JSON constants for status message tests
    private static final String TOOL_CONVERSATION = "{\"toolType\":\"conversation\",\"conversationName\":\"HR Onboarding\",\"channelName\":\"ch1\"}";
    private static final String TOOL_CONVERSATION_NO_NAME = "{\"toolType\":\"conversation\",\"channelName\":\"ch1\"}";
    private static final String TOOL_CATALOG_REQUEST = "{\"toolType\":\"catalog_request\",\"catalogRequestId\":\"REQ-42\",\"invokerId\":\"inv-1\"}";
    private static final String TOOL_CATALOG_REQUEST_NO_ID = "{\"toolType\":\"catalog_request\",\"invokerId\":\"inv-1\"}";
    private static final String TOOL_INVOKER_LONG = "{\"toolType\":\"invoker\",\"invokerId\":\"abcdefgh12345\"}";
    private static final String TOOL_INVOKER_SHORT = "{\"toolType\":\"invoker\",\"invokerId\":\"abc\"}";
    private static final String TOOL_INVOKER_EXACT8 = "{\"toolType\":\"invoker\",\"invokerId\":\"abcdefgh\"}";
    private static final String TOOL_INVOKER_NO_ID = "{\"toolType\":\"invoker\"}";
    private static final String TOOL_ENTITY_ADD = "{\"toolType\":\"entity\",\"entityId\":\"Employee\",\"operationType\":\"ADD\"}";
    private static final String TOOL_ENTITY_QUERY = "{\"toolType\":\"entity\",\"entityId\":\"Customer\"}";
    private static final String TOOL_ENTITY_UPDATE = "{\"toolType\":\"entity\",\"entityId\":\"Order\",\"operationType\":\"UPDATE\"}";
    private static final String TOOL_ENTITY_DELETE = "{\"toolType\":\"entity\",\"entityId\":\"Record\",\"operationType\":\"DELETE\"}";
    private static final String TOOL_ENTITY_CREATE = "{\"toolType\":\"entity\",\"entityId\":\"User\",\"operationType\":\"CREATE\"}";
    private static final String TOOL_ENTITY_EDIT = "{\"toolType\":\"entity\",\"entityId\":\"Profile\",\"operationType\":\"EDIT\"}";
    private static final String TOOL_ENTITY_REMOVE = "{\"toolType\":\"entity\",\"entityId\":\"Entry\",\"operationType\":\"REMOVE\"}";
    private static final String TOOL_ENTITY_UNKNOWN_OP = "{\"toolType\":\"entity\",\"entityId\":\"Item\",\"operationType\":\"MERGE\"}";
    private static final String TOOL_ENTITY_NO_ID = "{\"toolType\":\"entity\",\"operationType\":\"ADD\"}";
    private static final String TOOL_ASK_PERSON = "{\"toolType\":\"ask_a_person\",\"humanInteractionId\":\"hi-1\",\"description\":\"ask\"}";
    private static final String TOOL_INFORM_PERSON = "{\"toolType\":\"inform_a_person\"}";
    private static final String TOOL_ENTITY_BLANK_OP = "{\"toolType\":\"entity\",\"entityId\":\"Foo\",\"operationType\":\"  \"}";
    private static final String TOOL_ENTITY_LOWERCASE_OP = "{\"toolType\":\"entity\",\"entityId\":\"Widget\",\"operationType\":\"delete\"}";

    @BeforeEach
    void setUp() {

        agenticFlowService = mock(AgenticFlowService.class);
        chatMemoryMap = new ConcurrentHashMap<>();
        converter = new AgenticResponseConverter(agenticFlowService, chatMemoryMap);
    }

    // =========================================================================
    // Section 1 — Session ID helpers (pure logic, no mocks needed)
    // =========================================================================

    @Test
    @DisplayName("isChildSession: null sessionId returns false")
    void isChildSession_nullSessionId_returnsFalse() {

        assertFalse(converter.isChildSession(null),
            "null sessionId must not be classified as a child session");
    }

    @Test
    @DisplayName("isChildSession: plain session ID without .child returns false")
    void isChildSession_plainSessionId_returnsFalse() {

        assertFalse(converter.isChildSession("abc"),
            "'abc' does not contain '.child' and must not be classified as child");
    }

    @Test
    @DisplayName("isChildSession: session ID containing .child_suffix returns true")
    void isChildSession_sessionIdWithChildSuffix_returnsTrue() {

        assertTrue(converter.isChildSession("abc.child_xyz"),
            "'abc.child_xyz' contains '.child' and must be classified as a child session");
    }

    @Test
    @DisplayName("extractParentSessionId: strips .child_123 suffix from parentId.child_123")
    void extractParentSessionId_stripsChildSuffix() {

        String result = converter.extractParentSessionId("parentId.child_123");

        assertEquals("parentId", result,
            "Parent session ID must be everything before the first '.child'");
    }

    @Test
    @DisplayName("extractParentSessionId: strips .child_uuid suffix from sess_abc.child_uuid")
    void extractParentSessionId_complexSessionId_stripsCorrectly() {

        String result = converter.extractParentSessionId("sess_abc.child_uuid");

        assertEquals("sess_abc", result,
            "Parent session ID must be 'sess_abc'");
    }

    @Test
    @DisplayName("isInvokerTool: null toolName returns false")
    void isInvokerTool_nullToolName_returnsFalse() {

        assertFalse(converter.isInvokerTool(null),
            "null toolName must not be identified as an invoker tool");
    }

    @Test
    @DisplayName("isInvokerTool: toolType=invoker JSON returns true")
    void isInvokerTool_invokerToolType_returnsTrue() {

        assertTrue(converter.isInvokerTool("{\"toolType\":\"invoker\"}"),
            "JSON containing toolType=invoker must be identified as an invoker tool");
    }

    @Test
    @DisplayName("isInvokerTool: toolType=standard JSON returns false")
    void isInvokerTool_standardToolType_returnsFalse() {

        assertFalse(converter.isInvokerTool("{\"toolType\":\"standard\"}"),
            "JSON containing toolType=standard must NOT be identified as an invoker tool");
    }

    @Test
    @DisplayName("isHelperTool: null toolName returns false")
    void isHelperTool_nullToolName_returnsFalse() {

        // Null toolName short-circuits before any session lookup
        assertFalse(converter.isHelperTool("sess_helper_null", null),
            "null toolName must not be identified as a helper tool");
    }

    @Test
    @DisplayName("isHelperTool: toolName not found in session tool list returns false")
    void isHelperTool_malformedJson_returnsFalse() {

        // Session exists but its tools don't match the provided toolName
        String sessionId = "sess_helper_notfound";
        AgenticSession mockSession = mock(AgenticSession.class);
        when(agenticFlowService.getSession(sessionId)).thenReturn(mockSession);
        when(mockSession.getTools()).thenReturn(List.of());

        assertFalse(converter.isHelperTool(sessionId, "{broken"),
            "Tool not found in session must not be identified as a helper tool");
    }

    @Test
    @DisplayName("isHelperTool: null session defaults to non-helper")
    void isHelperTool_nonJsonString_returnsFalse() {

        // No session mock — getSession returns null by default (Mockito default)
        assertFalse(converter.isHelperTool("sess_no_session", "plain_string"),
            "Null session must default to non-helper (false)");
    }

    @Test
    @DisplayName("isHelperTool: builtin ask_a_person tool with isHelperTool=true returns true")
    void isHelperTool_askAPerson_returnsTrue() {

        String sessionId = "sess_helper_ask";
        AgenticToolDefinition helperTool = new AgenticToolDefinition(
            TOOL_ASK_PERSON, "Ask a person", "ask_a_person", List.of(), Boolean.TRUE);

        AgenticSession mockSession = mock(AgenticSession.class);
        when(agenticFlowService.getSession(sessionId)).thenReturn(mockSession);
        when(mockSession.getTools()).thenReturn(List.of(helperTool));

        assertTrue(converter.isHelperTool(sessionId, TOOL_ASK_PERSON),
            "ask_a_person with isHelperTool=true must be identified as a helper tool");
    }

    @Test
    @DisplayName("isHelperTool: inform_a_person tool with isHelperTool=true returns true")
    void isHelperTool_informAPerson_returnsTrue() {

        String sessionId = "sess_helper_inform";
        AgenticToolDefinition helperTool = new AgenticToolDefinition(
            TOOL_INFORM_PERSON, "Inform a person", "inform_a_person", List.of(), Boolean.TRUE);

        AgenticSession mockSession = mock(AgenticSession.class);
        when(agenticFlowService.getSession(sessionId)).thenReturn(mockSession);
        when(mockSession.getTools()).thenReturn(List.of(helperTool));

        assertTrue(converter.isHelperTool(sessionId, TOOL_INFORM_PERSON),
            "inform_a_person with isHelperTool=true must be identified as a helper tool");
    }

    @Test
    @DisplayName("isHelperTool: invoker tool with isHelperTool=false returns false")
    void isHelperTool_invokerToolType_returnsFalse() {

        String sessionId = "sess_helper_invoker";
        String invokerToolName = "{\"toolType\":\"invoker\"}";
        AgenticToolDefinition businessTool = new AgenticToolDefinition(
            invokerToolName, "Invoker", "invoker", List.of(), Boolean.FALSE);

        AgenticSession mockSession = mock(AgenticSession.class);
        when(agenticFlowService.getSession(sessionId)).thenReturn(mockSession);
        when(mockSession.getTools()).thenReturn(List.of(businessTool));

        assertFalse(converter.isHelperTool(sessionId, invokerToolName),
            "Invoker tool with isHelperTool=false must NOT be identified as a helper tool");
    }

    @Test
    @DisplayName("generateChildSessionId: result starts with parentId.child_")
    void generateChildSessionId_startsWithParentIdChildPrefix() {

        String childId = converter.generateChildSessionId("parent123");

        assertTrue(childId.startsWith("parent123.child_"),
            "Generated child session ID must start with 'parent123.child_'");
    }

    @RepeatedTest(5)
    @DisplayName("generateChildSessionId: generates unique IDs across calls")
    void generateChildSessionId_isUniqueAcrossCalls() {

        Set<String> generated = new HashSet<>();
        for (int i = 0; i < 10; i++) {
            generated.add(converter.generateChildSessionId("parent"));
        }

        assertEquals(10, generated.size(),
            "All 10 generated child session IDs must be unique");
    }

    // =========================================================================
    // Section 2 — toExtensionResponse with null / malformed input
    // =========================================================================

    @Test
    @DisplayName("toExtensionResponse: null AgenticResponse returns fallback with InformAPersonAction")
    void toExtensionResponse_nullResponse_returnsFallbackWithInformAction() {

        ExtensionResponse response = converter.toExtensionResponse(null, null);

        assertNotNull(response, "Response must not be null for null input");
        assertEquals(ExtensionResponse.Result.SUCCESS, response.getResult(),
            "Fallback response must have SUCCESS result");

        RemediationActions remediationActions = response.getRemediationActions();
        assertNotNull(remediationActions, "RemediationActions must not be null in fallback");
        assertNotNull(remediationActions.getRemediationActionList(),
            "RemediationAction list must not be null");
        assertFalse(remediationActions.getRemediationActionList().isEmpty(),
            "Fallback must contain at least one action");

        Object action = remediationActions.getRemediationActionList().get(0);
        assertInstanceOf(InformAPersonAction.class, action,
            "Fallback action must be an InformAPersonAction");
    }

    @Test
    @DisplayName("toExtensionResponse: AgenticResponse with null responseType returns fallback")
    void toExtensionResponse_nullResponseType_returnsFallback() {

        AgenticResponse malformed = new AgenticResponse();
        malformed.setSessionId("sess_malformed");
        // responseType intentionally left null

        ExtensionResponse response = converter.toExtensionResponse(malformed, null);

        assertNotNull(response, "Response must not be null for malformed input");
        assertEquals(ExtensionResponse.Result.SUCCESS, response.getResult(),
            "Fallback response must have SUCCESS result");

        RemediationActions remediationActions = response.getRemediationActions();
        assertNotNull(remediationActions);
        assertFalse(remediationActions.getRemediationActionList().isEmpty());

        Object action = remediationActions.getRemediationActionList().get(0);
        assertInstanceOf(InformAPersonAction.class, action,
            "Fallback action must be an InformAPersonAction for malformed response");
    }

    // =========================================================================
    // Section 3 — toExtensionResponse EXECUTE_TOOL
    // =========================================================================

    @Test
    @DisplayName("toExtensionResponse EXECUTE_TOOL: parent selects non-invoker tool uses same sessionId")
    void toExtensionResponse_executeTool_parentNonInvokerTool_usesSameSessionId() {

        // Given: parent session, non-invoker tool
        String sessionId = "sess_parent_001";
        String nonInvokerToolName = "{\"toolId\":\"lookup_tool\",\"toolType\":\"standard\"}";
        Map<String, Object> args = Map.of("param1", "value1");
        Map<String, Object> outputVars = Map.of("key", "val");

        AgenticResponse agenticResponse = buildExecuteToolResponse(sessionId, nonInvokerToolName, args, outputVars);

        // When
        ExtensionResponse response = converter.toExtensionResponse(agenticResponse, null);

        // Then
        assertNotNull(response);
        assertEquals(ExtensionResponse.Result.SUCCESS, response.getResult());

        RemediationActions remediationActions = response.getRemediationActions();
        assertNotNull(remediationActions);
        assertEquals(1, remediationActions.getRemediationActionList().size());

        Object action = remediationActions.getRemediationActionList().get(0);
        assertInstanceOf(ExecuteToolAction.class, action,
            "Action must be ExecuteToolAction for EXECUTE_TOOL response");

        ExecuteToolAction executeAction = (ExecuteToolAction) action;
        assertEquals(sessionId, executeAction.getSessionId(),
            "Parent non-invoker tool execution must use the parent session ID unchanged");
    }

    @Test
    @DisplayName("toExtensionResponse EXECUTE_TOOL: parent selects invoker tool generates child session ID")
    void toExtensionResponse_executeTool_parentInvokerTool_generatesChildSessionId() {

        // Given: parent session, invoker tool
        String parentSessionId = "sess_parent_002";
        String invokerToolName = "{\"toolId\":\"sub_agent\",\"toolType\":\"invoker\"}";
        Map<String, Object> args = Map.of("task", "do something");
        Map<String, Object> outputVars = new HashMap<>();

        AgenticResponse agenticResponse = buildExecuteToolResponse(parentSessionId, invokerToolName, args, outputVars);

        // When
        ExtensionResponse response = converter.toExtensionResponse(agenticResponse, null);

        // Then
        assertNotNull(response);
        assertEquals(ExtensionResponse.Result.SUCCESS, response.getResult());

        RemediationActions remediationActions = response.getRemediationActions();
        assertNotNull(remediationActions);
        assertEquals(1, remediationActions.getRemediationActionList().size());

        Object action = remediationActions.getRemediationActionList().get(0);
        assertInstanceOf(ExecuteToolAction.class, action);

        ExecuteToolAction executeAction = (ExecuteToolAction) action;
        String usedSessionId = executeAction.getSessionId();
        assertTrue(usedSessionId.contains(".child_"),
            "Invoker tool execution from parent must use a child session ID (contains '.child_'), got: " + usedSessionId);
        assertTrue(usedSessionId.startsWith(parentSessionId + ".child_"),
            "Child session ID must be prefixed with the parent session ID");
    }

    @Test
    @DisplayName("toExtensionResponse EXECUTE_TOOL: child session selects tool uses its own sessionId")
    void toExtensionResponse_executeTool_childSessionSelectsTool_usesChildSessionId() {

        // Given: child session, any tool
        String childSessionId = "sess_parent_003.child_abc123";
        String toolName = "{\"toolId\":\"some_tool\",\"toolType\":\"standard\"}";
        Map<String, Object> args = new HashMap<>();
        Map<String, Object> outputVars = new HashMap<>();

        AgenticResponse agenticResponse = buildExecuteToolResponse(childSessionId, toolName, args, outputVars);

        // When
        ExtensionResponse response = converter.toExtensionResponse(agenticResponse, null);

        // Then
        assertNotNull(response);

        RemediationActions remediationActions = response.getRemediationActions();
        assertNotNull(remediationActions);
        assertEquals(1, remediationActions.getRemediationActionList().size());

        Object action = remediationActions.getRemediationActionList().get(0);
        assertInstanceOf(ExecuteToolAction.class, action);

        ExecuteToolAction executeAction = (ExecuteToolAction) action;
        assertEquals(childSessionId, executeAction.getSessionId(),
            "Child session tool execution must use the child's own session ID");
    }

    // =========================================================================
    // Section 4 — toExtensionResponse COMPLETE
    // =========================================================================

    @Test
    @DisplayName("toExtensionResponse COMPLETE: child session returns FlowCompletionAction targeting parentSessionId")
    void toExtensionResponse_complete_childSession_returnsFlowCompletionActionWithParentId() {

        // Given: child session completes
        String parentSessionId = "sess_parent_complete";
        String childSessionId = parentSessionId + ".child_xyz";
        Map<String, Object> outputVars = Map.of("result", "done");

        AgenticResponse agenticResponse = new AgenticResponse();
        agenticResponse.setSessionId(childSessionId);
        agenticResponse.setResponseType(AgenticResponse.ResponseType.COMPLETE);
        agenticResponse.setFinalResponse("Child task completed successfully");
        agenticResponse.setOutputVariables(outputVars);

        // When
        ExtensionResponse response = converter.toExtensionResponse(agenticResponse, null);

        // Then
        assertNotNull(response);
        assertEquals(ExtensionResponse.Result.SUCCESS, response.getResult());

        RemediationActions remediationActions = response.getRemediationActions();
        assertNotNull(remediationActions);
        assertEquals(1, remediationActions.getRemediationActionList().size());

        Object action = remediationActions.getRemediationActionList().get(0);
        assertInstanceOf(FlowCompletionAction.class, action,
            "Child COMPLETE must produce a FlowCompletionAction");

        FlowCompletionAction completionAction = (FlowCompletionAction) action;
        assertEquals(parentSessionId, completionAction.getSessionId(),
            "FlowCompletionAction must target the parent session ID");
        assertTrue(completionAction.isReturnToParent(),
            "FlowCompletionAction for child complete must have isReturnToParent=true");
    }

    @Test
    @DisplayName("toExtensionResponse COMPLETE: parent session triggers usage logging")
    void toExtensionResponse_complete_parentSession_triggersUsageLogging() {

        // Given: parent session (no .child in ID)
        String parentSessionId = "sess_parent_log_001";
        Map<String, Object> outputVars = new HashMap<>();

        AgenticResponse agenticResponse = new AgenticResponse();
        agenticResponse.setSessionId(parentSessionId);
        agenticResponse.setResponseType(AgenticResponse.ResponseType.COMPLETE);
        agenticResponse.setFinalResponse("Parent flow complete");
        agenticResponse.setOutputVariables(outputVars);

        // Use a real SessionUsageCollector so UsageLogger.logSessionUsage does not NPE
        // when it calls getSessionSummary() internally.
        SessionUsageCollector realCollector = new SessionUsageCollector(parentSessionId, "ToolPlanner");

        AgenticSession mockSession = mock(AgenticSession.class);
        when(agenticFlowService.getSession(parentSessionId)).thenReturn(mockSession);
        when(mockSession.getUsageCollector()).thenReturn(realCollector);

        // When
        ExtensionResponse response = converter.toExtensionResponse(agenticResponse, null);

        // Then
        assertNotNull(response);
        assertEquals(ExtensionResponse.Result.SUCCESS, response.getResult(),
            "Parent COMPLETE must produce a SUCCESS response");

        // Verify the usage collector status was updated to COMPLETED
        assertEquals(SessionStatus.COMPLETED, realCollector.getStatus(),
            "Usage collector must be marked COMPLETED after parent session completes");
    }

    // =========================================================================
    // Section 5 — toExtensionResponse ERROR
    // =========================================================================

    @Test
    @DisplayName("toExtensionResponse ERROR: child session returns FlowCompletionAction targeting parentSessionId")
    void toExtensionResponse_error_childSession_returnsFlowCompletionActionWithParentId() {

        // Given: child session errors
        String parentSessionId = "sess_parent_error";
        String childSessionId = parentSessionId + ".child_err01";

        AgenticResponse agenticResponse = new AgenticResponse();
        agenticResponse.setSessionId(childSessionId);
        agenticResponse.setResponseType(AgenticResponse.ResponseType.ERROR);
        agenticResponse.setErrorMessage("Child agent encountered an error");
        agenticResponse.setOutputVariables(new HashMap<>());

        // When
        ExtensionResponse response = converter.toExtensionResponse(agenticResponse, null);

        // Then
        assertNotNull(response);
        assertEquals(ExtensionResponse.Result.SUCCESS, response.getResult());

        RemediationActions remediationActions = response.getRemediationActions();
        assertNotNull(remediationActions);
        assertEquals(1, remediationActions.getRemediationActionList().size());

        Object action = remediationActions.getRemediationActionList().get(0);
        assertInstanceOf(FlowCompletionAction.class, action,
            "Child ERROR must produce a FlowCompletionAction");

        FlowCompletionAction completionAction = (FlowCompletionAction) action;
        assertEquals(parentSessionId, completionAction.getSessionId(),
            "FlowCompletionAction for child error must target the parent session ID");
        assertTrue(completionAction.isReturnToParent(),
            "FlowCompletionAction for child error must have isReturnToParent=true");
    }

    @Test
    @DisplayName("toExtensionResponse ERROR: parent session triggers usage logging with markError")
    void toExtensionResponse_error_parentSession_triggersUsageLoggingWithMarkError() {

        // Given: parent session errors
        String parentSessionId = "sess_parent_err_log";
        String errorMsg = "Something went wrong";

        AgenticResponse agenticResponse = new AgenticResponse();
        agenticResponse.setSessionId(parentSessionId);
        agenticResponse.setResponseType(AgenticResponse.ResponseType.ERROR);
        agenticResponse.setErrorMessage(errorMsg);
        agenticResponse.setOutputVariables(new HashMap<>());

        // Use a real SessionUsageCollector so UsageLogger.logSessionUsage does not NPE
        // when it calls getSessionSummary() internally.
        SessionUsageCollector realCollector = new SessionUsageCollector(parentSessionId, "ToolPlanner");

        AgenticSession mockSession = mock(AgenticSession.class);
        when(agenticFlowService.getSession(parentSessionId)).thenReturn(mockSession);
        when(mockSession.getUsageCollector()).thenReturn(realCollector);

        // When
        ExtensionResponse response = converter.toExtensionResponse(agenticResponse, null);

        // Then
        assertNotNull(response);
        assertEquals(ExtensionResponse.Result.SUCCESS, response.getResult(),
            "Parent ERROR must still produce a SUCCESS-result ExtensionResponse (not a raw failure)");

        // Verify the usage collector status was updated to ERROR with the correct message
        assertEquals(SessionStatus.ERROR, realCollector.getStatus(),
            "Usage collector must be marked ERROR after parent session errors");
        assertEquals(errorMsg, realCollector.getErrorMessage(),
            "Error message must be recorded on the usage collector");
    }

    @Test
    @DisplayName("toExtensionResponse ERROR: parent error FlowCompletionAction carries session_summary in outputVariables (KR-20450)")
    void error_parentCarriesSessionSummary() {

        // Given: a failing parent response whose outputVariables already carry the structured
        // session_summary. KR-20450's whole point is that the ERROR path must not drop it.
        String parentSessionId = "sess_parent_err_summary";
        String summaryJson = "{\"terminationReason\":\"ERROR\",\"activityFeed\":[]}";
        Map<String, Object> outputVars = new HashMap<>();
        outputVars.put(AgenticConstants.SYSTEM_VAR_SESSION_SUMMARY, summaryJson);

        AgenticResponse agenticResponse = new AgenticResponse();
        agenticResponse.setSessionId(parentSessionId);
        agenticResponse.setResponseType(AgenticResponse.ResponseType.ERROR);
        agenticResponse.setErrorMessage("boom");
        agenticResponse.setOutputVariables(outputVars);

        // Real collector so the parent error usage-logging path does not NPE.
        SessionUsageCollector realCollector = new SessionUsageCollector(parentSessionId, "ToolPlanner");
        AgenticSession mockSession = mock(AgenticSession.class);
        when(agenticFlowService.getSession(parentSessionId)).thenReturn(mockSession);
        when(mockSession.getUsageCollector()).thenReturn(realCollector);

        // When
        ExtensionResponse response = converter.toExtensionResponse(agenticResponse, null);

        // Then: session_summary must survive end-to-end into FlowCompletionAction.outputVariables.
        FlowCompletionAction action = (FlowCompletionAction) response.getRemediationActions()
            .getRemediationActionList().get(0);
        assertNotNull(action.getOutputVariables(),
            "Parent error FlowCompletionAction must carry outputVariables, not drop them");
        assertTrue(action.getOutputVariables().containsKey(AgenticConstants.SYSTEM_VAR_SESSION_SUMMARY),
            "session_summary must survive into FlowCompletionAction.outputVariables on the parent error path");
        assertEquals(summaryJson,
            action.getOutputVariables().get(AgenticConstants.SYSTEM_VAR_SESSION_SUMMARY),
            "session_summary value must be carried through unchanged");
    }

    @Test
    @DisplayName("toExtensionResponse ERROR: child error FlowCompletionAction carries session_summary in outputVariables (KR-20450)")
    void error_childCarriesSessionSummary() {

        // Given: a failing child response carrying session_summary in its outputVariables.
        String parentSessionId = "sess_parent_err_child_summary";
        String childSessionId = parentSessionId + ".child_err02";
        String summaryJson = "{\"terminationReason\":\"ERROR\"}";
        Map<String, Object> outputVars = new HashMap<>();
        outputVars.put(AgenticConstants.SYSTEM_VAR_SESSION_SUMMARY, summaryJson);

        AgenticResponse agenticResponse = new AgenticResponse();
        agenticResponse.setSessionId(childSessionId);
        agenticResponse.setResponseType(AgenticResponse.ResponseType.ERROR);
        agenticResponse.setErrorMessage("child boom");
        agenticResponse.setOutputVariables(outputVars);

        // When
        ExtensionResponse response = converter.toExtensionResponse(agenticResponse, null);

        // Then: the error returned to the parent must still carry session_summary in the action.
        FlowCompletionAction action = (FlowCompletionAction) response.getRemediationActions()
            .getRemediationActionList().get(0);
        assertEquals(parentSessionId, action.getSessionId(),
            "Child error FlowCompletionAction must target the parent session ID");
        assertNotNull(action.getOutputVariables(),
            "Child error FlowCompletionAction must carry outputVariables, not drop them");
        assertTrue(action.getOutputVariables().containsKey(AgenticConstants.SYSTEM_VAR_SESSION_SUMMARY),
            "session_summary must survive into FlowCompletionAction.outputVariables on the child error path");
        assertEquals(summaryJson,
            action.getOutputVariables().get(AgenticConstants.SYSTEM_VAR_SESSION_SUMMARY),
            "session_summary value must be carried through unchanged");
    }

    // =========================================================================
    // Section 6 — EXECUTE_TOOL: LLM statusMessage preference
    // =========================================================================

    @Test
    @DisplayName("toExtensionResponse EXECUTE_TOOL: LLM statusMessage is used as-is when present")
    void toExtensionResponse_executeTool_llmStatusMessage_usedAsIs() {

        AgenticResponse response = buildResponseWithToolAndStatus(
            AgenticResponse.ResponseType.EXECUTE_TOOL, "sess_status_001",
            "{\"toolType\":\"entity\",\"entityId\":\"Foo\"}", Map.of(), "Searching docs...");

        ExtensionResponse result = converter.toExtensionResponse(response, null);

        ExecuteToolAction action = (ExecuteToolAction) result.getRemediationActions()
            .getRemediationActionList().get(0);
        assertEquals("Searching docs...", action.getStatusMessage(),
            "LLM-provided statusMessage must be used as-is");
        assertEquals(InformStepType.STATUS, action.getInformStepType(),
            "EXECUTE_TOOL must always use InformStepType.STATUS");
    }

    @Test
    @DisplayName("toExtensionResponse EXECUTE_TOOL: null statusMessage falls back to tool-type derivation")
    void toExtensionResponse_executeTool_nullStatusMessage_fallsBackToToolType() {

        AgenticResponse response = buildResponseWithToolAndStatus(
            AgenticResponse.ResponseType.EXECUTE_TOOL, "sess_status_002",
            TOOL_CONVERSATION, Map.of(), null);

        ExtensionResponse result = converter.toExtensionResponse(response, null);

        ExecuteToolAction action = (ExecuteToolAction) result.getRemediationActions()
            .getRemediationActionList().get(0);
        assertEquals("Calling conversation \"HR Onboarding\"...", action.getStatusMessage(),
            "Null statusMessage must fall back to tool-type derived message");
    }

    @Test
    @DisplayName("toExtensionResponse EXECUTE_TOOL: blank statusMessage falls back to tool-type derivation")
    void toExtensionResponse_executeTool_blankStatusMessage_fallsBackToToolType() {

        AgenticResponse response = buildResponseWithToolAndStatus(
            AgenticResponse.ResponseType.EXECUTE_TOOL, "sess_status_003",
            TOOL_CONVERSATION, Map.of(), "   ");

        ExtensionResponse result = converter.toExtensionResponse(response, null);

        ExecuteToolAction action = (ExecuteToolAction) result.getRemediationActions()
            .getRemediationActionList().get(0);
        assertEquals("Calling conversation \"HR Onboarding\"...", action.getStatusMessage(),
            "Blank statusMessage must fall back to tool-type derived message");
    }

    @Test
    @DisplayName("toExtensionResponse EXECUTE_TOOL: empty statusMessage falls back to tool-type derivation")
    void toExtensionResponse_executeTool_emptyStatusMessage_fallsBackToToolType() {

        AgenticResponse response = buildResponseWithToolAndStatus(
            AgenticResponse.ResponseType.EXECUTE_TOOL, "sess_status_004",
            TOOL_CONVERSATION, Map.of(), "");

        ExtensionResponse result = converter.toExtensionResponse(response, null);

        ExecuteToolAction action = (ExecuteToolAction) result.getRemediationActions()
            .getRemediationActionList().get(0);
        assertEquals("Calling conversation \"HR Onboarding\"...", action.getStatusMessage(),
            "Empty statusMessage must fall back to tool-type derived message");
    }

    // =========================================================================
    // Section 7 — EXECUTE_TOOL: Type-based fallback derivation
    // =========================================================================

    @Test
    @DisplayName("toExtensionResponse EXECUTE_TOOL fallback: conversation with name")
    void toExtensionResponse_executeTool_fallback_conversationWithName() {

        AgenticResponse response = buildResponseWithToolAndStatus(
            AgenticResponse.ResponseType.EXECUTE_TOOL, "sess_fb_001",
            TOOL_CONVERSATION, Map.of(), null);

        ExecuteToolAction action = extractExecuteToolAction(response);
        assertEquals("Calling conversation \"HR Onboarding\"...", action.getStatusMessage());
    }

    @Test
    @DisplayName("toExtensionResponse EXECUTE_TOOL fallback: conversation without name")
    void toExtensionResponse_executeTool_fallback_conversationWithoutName() {

        AgenticResponse response = buildResponseWithToolAndStatus(
            AgenticResponse.ResponseType.EXECUTE_TOOL, "sess_fb_002",
            TOOL_CONVERSATION_NO_NAME, Map.of(), null);

        ExecuteToolAction action = extractExecuteToolAction(response);
        assertEquals("Calling conversation...", action.getStatusMessage());
    }

    @Test
    @DisplayName("toExtensionResponse EXECUTE_TOOL fallback: catalog request with ID")
    void toExtensionResponse_executeTool_fallback_catalogRequestWithId() {

        AgenticResponse response = buildResponseWithToolAndStatus(
            AgenticResponse.ResponseType.EXECUTE_TOOL, "sess_fb_003",
            TOOL_CATALOG_REQUEST, Map.of(), null);

        ExecuteToolAction action = extractExecuteToolAction(response);
        assertEquals("Executing request \"REQ-42\"...", action.getStatusMessage());
    }

    @Test
    @DisplayName("toExtensionResponse EXECUTE_TOOL fallback: catalog request without ID")
    void toExtensionResponse_executeTool_fallback_catalogRequestWithoutId() {

        AgenticResponse response = buildResponseWithToolAndStatus(
            AgenticResponse.ResponseType.EXECUTE_TOOL, "sess_fb_004",
            TOOL_CATALOG_REQUEST_NO_ID, Map.of(), null);

        ExecuteToolAction action = extractExecuteToolAction(response);
        assertEquals("Executing request...", action.getStatusMessage());
    }

    @Test
    @DisplayName("toExtensionResponse EXECUTE_TOOL fallback: invoker with long ID (>8 chars)")
    void toExtensionResponse_executeTool_fallback_invokerLongId() {

        // invoker is a special case - parent session + invoker tool → child session
        // Use a child session to avoid child-session generation so we get the tool action directly
        AgenticResponse response = buildResponseWithToolAndStatus(
            AgenticResponse.ResponseType.EXECUTE_TOOL, "sess_fb_005.child_existing",
            TOOL_INVOKER_LONG, Map.of(), null);

        ExecuteToolAction action = extractExecuteToolAction(response);
        assertEquals("Connecting to invoker abcdefgh***...", action.getStatusMessage());
    }

    @Test
    @DisplayName("toExtensionResponse EXECUTE_TOOL fallback: invoker with short ID (<=8 chars)")
    void toExtensionResponse_executeTool_fallback_invokerShortId() {

        AgenticResponse response = buildResponseWithToolAndStatus(
            AgenticResponse.ResponseType.EXECUTE_TOOL, "sess_fb_006.child_existing",
            TOOL_INVOKER_SHORT, Map.of(), null);

        ExecuteToolAction action = extractExecuteToolAction(response);
        assertEquals("Connecting to invoker abc***...", action.getStatusMessage());
    }

    @Test
    @DisplayName("toExtensionResponse EXECUTE_TOOL fallback: invoker with exact 8-char ID")
    void toExtensionResponse_executeTool_fallback_invokerExact8Id() {

        AgenticResponse response = buildResponseWithToolAndStatus(
            AgenticResponse.ResponseType.EXECUTE_TOOL, "sess_fb_007.child_existing",
            TOOL_INVOKER_EXACT8, Map.of(), null);

        ExecuteToolAction action = extractExecuteToolAction(response);
        assertEquals("Connecting to invoker abcdefgh***...", action.getStatusMessage());
    }

    @Test
    @DisplayName("toExtensionResponse EXECUTE_TOOL fallback: invoker without ID")
    void toExtensionResponse_executeTool_fallback_invokerNoId() {

        AgenticResponse response = buildResponseWithToolAndStatus(
            AgenticResponse.ResponseType.EXECUTE_TOOL, "sess_fb_008.child_existing",
            TOOL_INVOKER_NO_ID, Map.of(), null);

        ExecuteToolAction action = extractExecuteToolAction(response);
        assertEquals("Connecting to invoker...", action.getStatusMessage());
    }

    @Test
    @DisplayName("toExtensionResponse EXECUTE_TOOL fallback: entity ADD")
    void toExtensionResponse_executeTool_fallback_entityAdd() {

        AgenticResponse response = buildResponseWithToolAndStatus(
            AgenticResponse.ResponseType.EXECUTE_TOOL, "sess_fb_009",
            TOOL_ENTITY_ADD, Map.of(), null);

        ExecuteToolAction action = extractExecuteToolAction(response);
        assertEquals("Adding Employee...", action.getStatusMessage());
    }

    @Test
    @DisplayName("toExtensionResponse EXECUTE_TOOL fallback: entity QUERY (no operationType)")
    void toExtensionResponse_executeTool_fallback_entityQuery() {

        AgenticResponse response = buildResponseWithToolAndStatus(
            AgenticResponse.ResponseType.EXECUTE_TOOL, "sess_fb_010",
            TOOL_ENTITY_QUERY, Map.of(), null);

        ExecuteToolAction action = extractExecuteToolAction(response);
        assertEquals("Looking up Customer...", action.getStatusMessage());
    }

    @Test
    @DisplayName("toExtensionResponse EXECUTE_TOOL fallback: entity UPDATE")
    void toExtensionResponse_executeTool_fallback_entityUpdate() {

        AgenticResponse response = buildResponseWithToolAndStatus(
            AgenticResponse.ResponseType.EXECUTE_TOOL, "sess_fb_011",
            TOOL_ENTITY_UPDATE, Map.of(), null);

        ExecuteToolAction action = extractExecuteToolAction(response);
        assertEquals("Updating Order...", action.getStatusMessage());
    }

    @Test
    @DisplayName("toExtensionResponse EXECUTE_TOOL fallback: entity DELETE")
    void toExtensionResponse_executeTool_fallback_entityDelete() {

        AgenticResponse response = buildResponseWithToolAndStatus(
            AgenticResponse.ResponseType.EXECUTE_TOOL, "sess_fb_012",
            TOOL_ENTITY_DELETE, Map.of(), null);

        ExecuteToolAction action = extractExecuteToolAction(response);
        assertEquals("Deleting Record...", action.getStatusMessage());
    }

    @Test
    @DisplayName("toExtensionResponse EXECUTE_TOOL fallback: entity CREATE (maps to Adding)")
    void toExtensionResponse_executeTool_fallback_entityCreate() {

        AgenticResponse response = buildResponseWithToolAndStatus(
            AgenticResponse.ResponseType.EXECUTE_TOOL, "sess_fb_013",
            TOOL_ENTITY_CREATE, Map.of(), null);

        ExecuteToolAction action = extractExecuteToolAction(response);
        assertEquals("Adding User...", action.getStatusMessage());
    }

    @Test
    @DisplayName("toExtensionResponse EXECUTE_TOOL fallback: entity EDIT (maps to Updating)")
    void toExtensionResponse_executeTool_fallback_entityEdit() {

        AgenticResponse response = buildResponseWithToolAndStatus(
            AgenticResponse.ResponseType.EXECUTE_TOOL, "sess_fb_014",
            TOOL_ENTITY_EDIT, Map.of(), null);

        ExecuteToolAction action = extractExecuteToolAction(response);
        assertEquals("Updating Profile...", action.getStatusMessage());
    }

    @Test
    @DisplayName("toExtensionResponse EXECUTE_TOOL fallback: entity REMOVE (maps to Deleting)")
    void toExtensionResponse_executeTool_fallback_entityRemove() {

        AgenticResponse response = buildResponseWithToolAndStatus(
            AgenticResponse.ResponseType.EXECUTE_TOOL, "sess_fb_015",
            TOOL_ENTITY_REMOVE, Map.of(), null);

        ExecuteToolAction action = extractExecuteToolAction(response);
        assertEquals("Deleting Entry...", action.getStatusMessage());
    }

    @Test
    @DisplayName("toExtensionResponse EXECUTE_TOOL fallback: entity unknown operationType (maps to Looking up)")
    void toExtensionResponse_executeTool_fallback_entityUnknownOp() {

        AgenticResponse response = buildResponseWithToolAndStatus(
            AgenticResponse.ResponseType.EXECUTE_TOOL, "sess_fb_016",
            TOOL_ENTITY_UNKNOWN_OP, Map.of(), null);

        ExecuteToolAction action = extractExecuteToolAction(response);
        assertEquals("Looking up Item...", action.getStatusMessage());
    }

    @Test
    @DisplayName("toExtensionResponse EXECUTE_TOOL fallback: entity without entityId")
    void toExtensionResponse_executeTool_fallback_entityNoId() {

        AgenticResponse response = buildResponseWithToolAndStatus(
            AgenticResponse.ResponseType.EXECUTE_TOOL, "sess_fb_017",
            TOOL_ENTITY_NO_ID, Map.of(), null);

        ExecuteToolAction action = extractExecuteToolAction(response);
        assertEquals("Adding entity...", action.getStatusMessage());
    }

    @Test
    @DisplayName("toExtensionResponse EXECUTE_TOOL fallback: entity with blank operationType (maps to Looking up)")
    void toExtensionResponse_executeTool_fallback_entityBlankOp() {

        AgenticResponse response = buildResponseWithToolAndStatus(
            AgenticResponse.ResponseType.EXECUTE_TOOL, "sess_fb_020",
            TOOL_ENTITY_BLANK_OP, Map.of(), null);

        ExecuteToolAction action = extractExecuteToolAction(response);
        assertEquals("Looking up Foo...", action.getStatusMessage());
    }

    @Test
    @DisplayName("toExtensionResponse EXECUTE_TOOL fallback: entity with lowercase operationType is case-insensitive")
    void toExtensionResponse_executeTool_fallback_entityLowercaseOp() {

        AgenticResponse response = buildResponseWithToolAndStatus(
            AgenticResponse.ResponseType.EXECUTE_TOOL, "sess_fb_021",
            TOOL_ENTITY_LOWERCASE_OP, Map.of(), null);

        ExecuteToolAction action = extractExecuteToolAction(response);
        assertEquals("Deleting Widget...", action.getStatusMessage());
    }

    @Test
    @DisplayName("toExtensionResponse EXECUTE_TOOL: ask_a_person helper skips status message")
    void toExtensionResponse_executeTool_askPerson_skipsStatusMessage() {

        String sessionId = "sess_fb_018";
        AgenticSession mockSession = mock(AgenticSession.class);
        when(agenticFlowService.getSession(sessionId)).thenReturn(mockSession);
        when(mockSession.getTools()).thenReturn(List.of(
            new AgenticToolDefinition(TOOL_ASK_PERSON, "Ask a person", "ask_a_person", List.of(), Boolean.TRUE)
        ));

        AgenticResponse response = buildResponseWithToolAndStatus(
            AgenticResponse.ResponseType.EXECUTE_TOOL, sessionId,
            TOOL_ASK_PERSON, Map.of(), null);

        ExtensionResponse result = converter.toExtensionResponse(response, null);
        ExecuteToolAction action = (ExecuteToolAction) result.getRemediationActions()
            .getRemediationActionList().get(0);
        assertNull(action.getStatusMessage(),
            "Helper tool ask_a_person must have null statusMessage");
    }

    @Test
    @DisplayName("toExtensionResponse EXECUTE_TOOL: inform_a_person helper skips status message")
    void toExtensionResponse_executeTool_informPerson_skipsStatusMessage() {

        String sessionId = "sess_fb_019";
        AgenticSession mockSession = mock(AgenticSession.class);
        when(agenticFlowService.getSession(sessionId)).thenReturn(mockSession);
        when(mockSession.getTools()).thenReturn(List.of(
            new AgenticToolDefinition(TOOL_INFORM_PERSON, "Inform a person", "inform_a_person", List.of(), Boolean.TRUE)
        ));

        AgenticResponse response = buildResponseWithToolAndStatus(
            AgenticResponse.ResponseType.EXECUTE_TOOL, sessionId,
            TOOL_INFORM_PERSON, Map.of(), null);

        ExtensionResponse result = converter.toExtensionResponse(response, null);
        ExecuteToolAction action = (ExecuteToolAction) result.getRemediationActions()
            .getRemediationActionList().get(0);
        assertNull(action.getStatusMessage(),
            "Helper tool inform_a_person must have null statusMessage");
    }

    @Test
    @DisplayName("toExtensionResponse EXECUTE_TOOL: helper tool with LLM statusMessage still gets null (unconditional skip)")
    void toExtensionResponse_executeTool_helperWithLlmStatus_stillSkipped() {

        String sessionId = "sess_fb_022";
        AgenticSession mockSession = mock(AgenticSession.class);
        when(agenticFlowService.getSession(sessionId)).thenReturn(mockSession);
        when(mockSession.getTools()).thenReturn(List.of(
            new AgenticToolDefinition(TOOL_ASK_PERSON, "Ask a person", "ask_a_person", List.of(), Boolean.TRUE)
        ));

        AgenticResponse response = buildResponseWithToolAndStatus(
            AgenticResponse.ResponseType.EXECUTE_TOOL, sessionId,
            TOOL_ASK_PERSON, Map.of(), "LLM provided this status");

        ExtensionResponse result = converter.toExtensionResponse(response, null);
        ExecuteToolAction action = (ExecuteToolAction) result.getRemediationActions()
            .getRemediationActionList().get(0);
        assertNull(action.getStatusMessage(),
            "Helper tool must have null statusMessage even when LLM provides one");
    }

    // =========================================================================
    // Section 7b — isHelperTool: session-aware classification (KR-20399)
    // =========================================================================

    @Test
    @DisplayName("isHelperTool: business ask_a_person (isHelperTool=false) preserves statusMessage")
    void isHelperTool_businessAskAPerson_preservesStatusMessage() {

        // The business manager-approval tool shares the ask_a_person toolType but
        // must NOT suppress its statusMessage because isHelperTool=false.
        String sessionId = "sess_business_ask";
        String businessToolName = "{\"toolType\":\"ask_a_person\",\"humanInteractionId\":\"approval-42\",\"description\":\"manager approval\"}";
        String llmStatus = "Awaiting manager approval...";

        AgenticToolDefinition businessTool = new AgenticToolDefinition(
            businessToolName, "Manager Approval", "ask_a_person", List.of(), Boolean.FALSE);

        AgenticSession mockSession = mock(AgenticSession.class);
        when(agenticFlowService.getSession(sessionId)).thenReturn(mockSession);
        when(mockSession.getTools()).thenReturn(List.of(businessTool));

        AgenticResponse response = buildResponseWithToolAndStatus(
            AgenticResponse.ResponseType.EXECUTE_TOOL, sessionId,
            businessToolName, Map.of(), llmStatus);

        ExtensionResponse result = converter.toExtensionResponse(response, null);
        ExecuteToolAction action = (ExecuteToolAction) result.getRemediationActions()
            .getRemediationActionList().get(0);

        assertNotNull(action.getStatusMessage(),
            "Business ask_a_person (isHelperTool=false) must NOT suppress statusMessage");
        assertEquals(llmStatus, action.getStatusMessage(),
            "Business ask_a_person must use the LLM-provided statusMessage as-is");
    }

    @Test
    @DisplayName("isHelperTool: null session defaults to non-helper (false)")
    void isHelperTool_nullSession_defaultsToFalse() {

        // getSession returns null by default in the Mockito mock — no explicit stubbing needed
        assertFalse(converter.isHelperTool("sess_no_exist", TOOL_ASK_PERSON),
            "Null session must default to non-helper (false)");
    }

    @Test
    @DisplayName("isHelperTool: isHelperTool=null on DTO defaults to false")
    void isHelperTool_nullFlagOnDto_defaultsToFalse() {

        String sessionId = "sess_null_flag";
        AgenticToolDefinition toolWithNullFlag = new AgenticToolDefinition(
            TOOL_ASK_PERSON, "Ask a person", "ask_a_person", List.of(), null);

        AgenticSession mockSession = mock(AgenticSession.class);
        when(agenticFlowService.getSession(sessionId)).thenReturn(mockSession);
        when(mockSession.getTools()).thenReturn(List.of(toolWithNullFlag));

        assertFalse(converter.isHelperTool(sessionId, TOOL_ASK_PERSON),
            "Null isHelperTool flag on DTO must be treated as false (non-helper)");
    }

    @Test
    @DisplayName("isHelperTool: tool name not found in session defaults to false")
    void isHelperTool_toolNotInSession_defaultsToFalse() {

        String sessionId = "sess_tool_missing";
        AgenticToolDefinition otherTool = new AgenticToolDefinition(
            TOOL_INFORM_PERSON, "Inform a person", "inform_a_person", List.of(), Boolean.TRUE);

        AgenticSession mockSession = mock(AgenticSession.class);
        when(agenticFlowService.getSession(sessionId)).thenReturn(mockSession);
        when(mockSession.getTools()).thenReturn(List.of(otherTool));

        // TOOL_ASK_PERSON is not in the session's tool list
        assertFalse(converter.isHelperTool(sessionId, TOOL_ASK_PERSON),
            "Tool name not found in session tool list must default to false");
    }

    @Test
    @DisplayName("isHelperTool dual-tool regression: builtin discards status, business preserves it")
    void isHelperTool_dualToolRegression_builtinDiscardsStatusBusinessPreservesIt() {

        // This is the core regression test for KR-20399:
        // Both tools have toolType=ask_a_person in their JSON name, but only the
        // builtin one has isHelperTool=true. The business tool must preserve its statusMessage.
        String builtinToolName = TOOL_ASK_PERSON;
        String businessToolName = "{\"toolType\":\"ask_a_person\",\"humanInteractionId\":\"approval-99\",\"description\":\"approval\"}";
        String llmStatus = "Waiting for manager sign-off...";

        AgenticToolDefinition builtinTool = new AgenticToolDefinition(
            builtinToolName, "Ask a person (builtin)", "ask_a_person", List.of(), Boolean.TRUE);
        AgenticToolDefinition businessTool = new AgenticToolDefinition(
            businessToolName, "Manager Approval (business)", "ask_a_person", List.of(), Boolean.FALSE);

        // Session for the builtin helper tool
        String builtinSessionId = "sess_dual_builtin";
        AgenticSession builtinSession = mock(AgenticSession.class);
        when(agenticFlowService.getSession(builtinSessionId)).thenReturn(builtinSession);
        when(builtinSession.getTools()).thenReturn(List.of(builtinTool, businessTool));

        // Session for the business tool
        String businessSessionId = "sess_dual_business";
        AgenticSession businessSession = mock(AgenticSession.class);
        when(agenticFlowService.getSession(businessSessionId)).thenReturn(businessSession);
        when(businessSession.getTools()).thenReturn(List.of(builtinTool, businessTool));

        // Builtin helper tool — status MUST be null (suppressed)
        AgenticResponse builtinResponse = buildResponseWithToolAndStatus(
            AgenticResponse.ResponseType.EXECUTE_TOOL, builtinSessionId,
            builtinToolName, Map.of(), llmStatus);

        ExecuteToolAction builtinAction = (ExecuteToolAction) converter.toExtensionResponse(builtinResponse, null)
            .getRemediationActions().getRemediationActionList().get(0);

        assertNull(builtinAction.getStatusMessage(),
            "Builtin ask_a_person (isHelperTool=true) must suppress statusMessage");

        // Business tool — status MUST be preserved
        AgenticResponse businessResponse = buildResponseWithToolAndStatus(
            AgenticResponse.ResponseType.EXECUTE_TOOL, businessSessionId,
            businessToolName, Map.of(), llmStatus);

        ExecuteToolAction businessAction = (ExecuteToolAction) converter.toExtensionResponse(businessResponse, null)
            .getRemediationActions().getRemediationActionList().get(0);

        assertNotNull(businessAction.getStatusMessage(),
            "Business ask_a_person (isHelperTool=false) must preserve statusMessage");
        assertEquals(llmStatus, businessAction.getStatusMessage(),
            "Business ask_a_person must use the exact LLM-provided statusMessage");
    }

    // =========================================================================
    // Section 8 — EXECUTE_TOOL: Fallback edge cases
    // =========================================================================

    @Test
    @DisplayName("toExtensionResponse EXECUTE_TOOL fallback: null tool name")
    void toExtensionResponse_executeTool_fallback_nullToolName() {

        AgenticResponse response = buildResponseWithToolAndStatus(
            AgenticResponse.ResponseType.EXECUTE_TOOL, "sess_edge_001",
            null, Map.of(), null);

        ExecuteToolAction action = extractExecuteToolAction(response);
        assertEquals("Processing...", action.getStatusMessage(),
            "Null tool name must produce generic Processing... message");
    }

    @Test
    @DisplayName("toExtensionResponse EXECUTE_TOOL fallback: blank tool name")
    void toExtensionResponse_executeTool_fallback_blankToolName() {

        AgenticResponse response = buildResponseWithToolAndStatus(
            AgenticResponse.ResponseType.EXECUTE_TOOL, "sess_edge_002",
            "   ", Map.of(), null);

        ExecuteToolAction action = extractExecuteToolAction(response);
        assertEquals("Processing...", action.getStatusMessage());
    }

    @Test
    @DisplayName("toExtensionResponse EXECUTE_TOOL fallback: non-JSON tool name")
    void toExtensionResponse_executeTool_fallback_nonJsonToolName() {

        AgenticResponse response = buildResponseWithToolAndStatus(
            AgenticResponse.ResponseType.EXECUTE_TOOL, "sess_edge_003",
            "plain_string", Map.of(), null);

        ExecuteToolAction action = extractExecuteToolAction(response);
        assertEquals("Processing...", action.getStatusMessage());
    }

    @Test
    @DisplayName("toExtensionResponse EXECUTE_TOOL fallback: malformed JSON tool name")
    void toExtensionResponse_executeTool_fallback_malformedJson() {

        AgenticResponse response = buildResponseWithToolAndStatus(
            AgenticResponse.ResponseType.EXECUTE_TOOL, "sess_edge_004",
            "{\"toolType\":", Map.of(), null);

        ExecuteToolAction action = extractExecuteToolAction(response);
        assertEquals("Processing...", action.getStatusMessage());
    }

    @Test
    @DisplayName("toExtensionResponse EXECUTE_TOOL fallback: JSON without toolType field")
    void toExtensionResponse_executeTool_fallback_jsonWithoutToolType() {

        AgenticResponse response = buildResponseWithToolAndStatus(
            AgenticResponse.ResponseType.EXECUTE_TOOL, "sess_edge_005",
            "{\"name\":\"something\"}", Map.of(), null);

        ExecuteToolAction action = extractExecuteToolAction(response);
        assertEquals("Processing...", action.getStatusMessage());
    }

    @Test
    @DisplayName("toExtensionResponse EXECUTE_TOOL fallback: unknown toolType")
    void toExtensionResponse_executeTool_fallback_unknownToolType() {

        AgenticResponse response = buildResponseWithToolAndStatus(
            AgenticResponse.ResponseType.EXECUTE_TOOL, "sess_edge_006",
            "{\"toolType\":\"custom_widget\"}", Map.of(), null);

        ExecuteToolAction action = extractExecuteToolAction(response);
        assertEquals("Processing...", action.getStatusMessage());
    }

    // =========================================================================
    // Section 9 — COMPLETE: Status message preference + fallback
    // =========================================================================

    @Test
    @DisplayName("toExtensionResponse COMPLETE: child with LLM statusMessage prefixes it")
    void toExtensionResponse_complete_child_llmStatusMessage() {

        AgenticResponse response = buildResponseWithToolAndStatus(
            AgenticResponse.ResponseType.COMPLETE, "sess_comp_001.child_abc",
            null, null, "Done");

        ExtensionResponse result = converter.toExtensionResponse(response, null);

        FlowCompletionAction action = (FlowCompletionAction) result.getRemediationActions()
            .getRemediationActionList().get(0);
        assertEquals("Sub-task update \u2014 Done", action.getStatusMessage(),
            "Child COMPLETE statusMessage must be prefixed with 'Sub-task update'");
        assertEquals("sess_comp_001", action.getSessionId(),
            "Child COMPLETE must target the parent session ID");
        assertTrue(action.isReturnToParent(),
            "Child COMPLETE must be return-to-parent");
        assertEquals("COMPLETED", action.getStatus(),
            "COMPLETE must have status COMPLETED");
    }

    @Test
    @DisplayName("toExtensionResponse COMPLETE: child with null statusMessage uses default")
    void toExtensionResponse_complete_child_nullStatusMessage() {

        AgenticResponse response = buildResponseWithToolAndStatus(
            AgenticResponse.ResponseType.COMPLETE, "sess_comp_002.child_abc",
            null, null, null);

        ExtensionResponse result = converter.toExtensionResponse(response, null);

        FlowCompletionAction action = (FlowCompletionAction) result.getRemediationActions()
            .getRemediationActionList().get(0);
        assertEquals("Sub-task update \u2014 Sub-task completed", action.getStatusMessage());
        assertEquals("sess_comp_002", action.getSessionId(),
            "Child COMPLETE must target the parent session ID");
        assertTrue(action.isReturnToParent(),
            "Child COMPLETE must be return-to-parent");
    }

    @Test
    @DisplayName("toExtensionResponse COMPLETE: child with blank statusMessage uses default")
    void toExtensionResponse_complete_child_blankStatusMessage() {

        AgenticResponse response = buildResponseWithToolAndStatus(
            AgenticResponse.ResponseType.COMPLETE, "sess_comp_003.child_abc",
            null, null, "   ");

        ExtensionResponse result = converter.toExtensionResponse(response, null);

        FlowCompletionAction action = (FlowCompletionAction) result.getRemediationActions()
            .getRemediationActionList().get(0);
        assertEquals("Sub-task update \u2014 Sub-task completed", action.getStatusMessage());
        assertEquals("sess_comp_003", action.getSessionId(),
            "Child COMPLETE must target the parent session ID");
        assertTrue(action.isReturnToParent(),
            "Child COMPLETE must be return-to-parent");
    }

    @Test
    @DisplayName("toExtensionResponse COMPLETE: parent with LLM statusMessage uses it as-is")
    void toExtensionResponse_complete_parent_llmStatusMessage() {

        String parentId = "sess_comp_004";
        SessionUsageCollector realCollector = new SessionUsageCollector(parentId, "ToolPlanner");
        AgenticSession mockSession = mock(AgenticSession.class);
        when(agenticFlowService.getSession(parentId)).thenReturn(mockSession);
        when(mockSession.getUsageCollector()).thenReturn(realCollector);

        AgenticResponse response = buildResponseWithToolAndStatus(
            AgenticResponse.ResponseType.COMPLETE, parentId,
            null, null, "All done");

        ExtensionResponse result = converter.toExtensionResponse(response, null);

        FlowCompletionAction action = (FlowCompletionAction) result.getRemediationActions()
            .getRemediationActionList().get(0);
        assertEquals("All done", action.getStatusMessage(),
            "LLM-provided statusMessage must be used for parent COMPLETE");
        assertEquals("sess_comp_004", action.getSessionId(),
            "Parent COMPLETE must use the parent session ID");
        assertFalse(action.isReturnToParent(),
            "Parent COMPLETE must NOT be return-to-parent");
        assertEquals("COMPLETED", action.getStatus(),
            "COMPLETE must have status COMPLETED");
    }

    @Test
    @DisplayName("toExtensionResponse COMPLETE: parent with null statusMessage uses default")
    void toExtensionResponse_complete_parent_nullStatusMessage() {

        // Need to mock agenticFlowService.getSession for parent complete (usage logging)
        String parentId = "sess_comp_005";
        SessionUsageCollector realCollector = new SessionUsageCollector(parentId, "ToolPlanner");
        AgenticSession mockSession = mock(AgenticSession.class);
        when(agenticFlowService.getSession(parentId)).thenReturn(mockSession);
        when(mockSession.getUsageCollector()).thenReturn(realCollector);

        AgenticResponse response = buildResponseWithToolAndStatus(
            AgenticResponse.ResponseType.COMPLETE, parentId,
            null, null, null);

        ExtensionResponse result = converter.toExtensionResponse(response, null);

        FlowCompletionAction action = (FlowCompletionAction) result.getRemediationActions()
            .getRemediationActionList().get(0);
        assertEquals("Task completed successfully", action.getStatusMessage());
        assertEquals("sess_comp_005", action.getSessionId(),
            "Parent COMPLETE must use the parent session ID");
        assertFalse(action.isReturnToParent(),
            "Parent COMPLETE must NOT be return-to-parent");
    }

    @Test
    @DisplayName("toExtensionResponse COMPLETE: parent with blank statusMessage uses default")
    void toExtensionResponse_complete_parent_blankStatusMessage() {

        String parentId = "sess_comp_006";
        SessionUsageCollector realCollector = new SessionUsageCollector(parentId, "ToolPlanner");
        AgenticSession mockSession = mock(AgenticSession.class);
        when(agenticFlowService.getSession(parentId)).thenReturn(mockSession);
        when(mockSession.getUsageCollector()).thenReturn(realCollector);

        AgenticResponse response = buildResponseWithToolAndStatus(
            AgenticResponse.ResponseType.COMPLETE, parentId,
            null, null, "  ");

        ExtensionResponse result = converter.toExtensionResponse(response, null);

        FlowCompletionAction action = (FlowCompletionAction) result.getRemediationActions()
            .getRemediationActionList().get(0);
        assertEquals("Task completed successfully", action.getStatusMessage());
        assertEquals("sess_comp_006", action.getSessionId(),
            "Parent COMPLETE must use the parent session ID");
        assertFalse(action.isReturnToParent(),
            "Parent COMPLETE must NOT be return-to-parent");
    }

    @Test
    @DisplayName("handleComplete: child with null statusMessage and errorCode uses resolved error message")
    void toExtensionResponse_complete_child_nullStatusMessageWithErrorCode() {

        String parentSessionId = "sess_comp_errcode";
        String childSessionId = parentSessionId + ".child_abc";

        AgenticResponse agenticResponse = new AgenticResponse();
        agenticResponse.setSessionId(childSessionId);
        agenticResponse.setResponseType(AgenticResponse.ResponseType.COMPLETE);
        agenticResponse.setFinalResponse("completed");
        agenticResponse.setOutputVariables(Map.of());
        // statusMessage intentionally null; errorCode passed as parameter

        ExtensionResponse result = converter.toExtensionResponse(agenticResponse, AgenticErrorCode.JSON_PARSE_FAILED);

        FlowCompletionAction action = (FlowCompletionAction) result.getRemediationActions()
            .getRemediationActionList().get(0);
        assertTrue(action.getStatusMessage().contains("Sub-task update"),
            "Child COMPLETE must have 'Sub-task update' prefix");
        assertTrue(action.getStatusMessage().contains("Unable to process AI response"),
            "Child COMPLETE with errorCode must contain the resolved error code message, not the default 'Sub-task completed'");
    }

    @Test
    @DisplayName("toExtensionResponse COMPLETE: informStepType is STATUS")
    void toExtensionResponse_complete_informStepTypeIsStatus() {

        AgenticResponse response = buildResponseWithToolAndStatus(
            AgenticResponse.ResponseType.COMPLETE, "sess_comp_007.child_abc",
            null, null, "Done");

        ExtensionResponse result = converter.toExtensionResponse(response, null);

        FlowCompletionAction action = (FlowCompletionAction) result.getRemediationActions()
            .getRemediationActionList().get(0);
        assertEquals(InformStepType.STATUS, action.getInformStepType(),
            "COMPLETE must always use InformStepType.STATUS");
    }

    // =========================================================================
    // Section 10 — ERROR: Status message preference + fallback
    // =========================================================================

    @Test
    @DisplayName("toExtensionResponse ERROR: child with LLM statusMessage prefixes it")
    void toExtensionResponse_error_child_llmStatusMessage() {

        AgenticResponse response = buildResponseWithToolAndStatus(
            AgenticResponse.ResponseType.ERROR, "sess_err_001.child_abc",
            null, null, "Issue found");

        ExtensionResponse result = converter.toExtensionResponse(response, null);

        FlowCompletionAction action = (FlowCompletionAction) result.getRemediationActions()
            .getRemediationActionList().get(0);
        assertEquals("Sub-task issue \u2014 Issue found", action.getStatusMessage(),
            "Child ERROR statusMessage must be prefixed with 'Sub-task issue'");
        assertEquals("sess_err_001", action.getSessionId(),
            "Child ERROR must target the parent session ID");
        assertTrue(action.isReturnToParent(),
            "Child ERROR must be return-to-parent");
        assertEquals("FAILED", action.getStatus(),
            "ERROR must have status FAILED");
    }

    @Test
    @DisplayName("toExtensionResponse ERROR: child with null statusMessage gets resolved fallback")
    void toExtensionResponse_error_child_nullStatusMessage() {

        AgenticResponse response = buildResponseWithToolAndStatus(
            AgenticResponse.ResponseType.ERROR, "sess_err_002.child_abc",
            null, null, null);

        ExtensionResponse result = converter.toExtensionResponse(response, null);

        FlowCompletionAction action = (FlowCompletionAction) result.getRemediationActions()
            .getRemediationActionList().get(0);
        assertEquals("Sub-task issue \u2014 Task encountered an error", action.getStatusMessage());
        assertEquals("sess_err_002", action.getSessionId(),
            "Child ERROR must target the parent session ID");
        assertTrue(action.isReturnToParent(),
            "Child ERROR must be return-to-parent");
    }

    @Test
    @DisplayName("toExtensionResponse ERROR: child with blank statusMessage gets resolved fallback")
    void toExtensionResponse_error_child_blankStatusMessage() {

        AgenticResponse response = buildResponseWithToolAndStatus(
            AgenticResponse.ResponseType.ERROR, "sess_err_003.child_abc",
            null, null, "   ");

        ExtensionResponse result = converter.toExtensionResponse(response, null);

        FlowCompletionAction action = (FlowCompletionAction) result.getRemediationActions()
            .getRemediationActionList().get(0);
        assertEquals("Sub-task issue \u2014 Task encountered an error", action.getStatusMessage());
        assertEquals("sess_err_003", action.getSessionId(),
            "Child ERROR must target the parent session ID");
        assertTrue(action.isReturnToParent(),
            "Child ERROR must be return-to-parent");
    }

    @Test
    @DisplayName("toExtensionResponse ERROR: parent with LLM statusMessage uses it as-is")
    void toExtensionResponse_error_parent_llmStatusMessage() {

        String parentId = "sess_err_004";
        SessionUsageCollector realCollector = new SessionUsageCollector(parentId, "ToolPlanner");
        AgenticSession mockSession = mock(AgenticSession.class);
        when(agenticFlowService.getSession(parentId)).thenReturn(mockSession);
        when(mockSession.getUsageCollector()).thenReturn(realCollector);

        AgenticResponse response = buildResponseWithToolAndStatus(
            AgenticResponse.ResponseType.ERROR, parentId,
            null, null, "Failed");

        ExtensionResponse result = converter.toExtensionResponse(response, null);

        FlowCompletionAction action = (FlowCompletionAction) result.getRemediationActions()
            .getRemediationActionList().get(0);
        assertEquals("Failed", action.getStatusMessage(),
            "LLM-provided statusMessage must be used for parent ERROR");
        assertEquals("sess_err_004", action.getSessionId(),
            "Parent ERROR must use the parent session ID");
        assertFalse(action.isReturnToParent(),
            "Parent ERROR must NOT be return-to-parent");
        assertEquals("FAILED", action.getStatus(),
            "ERROR must have status FAILED");
    }

    @Test
    @DisplayName("toExtensionResponse ERROR: parent with null statusMessage uses default")
    void toExtensionResponse_error_parent_nullStatusMessage() {

        String parentId = "sess_err_005";
        SessionUsageCollector realCollector = new SessionUsageCollector(parentId, "ToolPlanner");
        AgenticSession mockSession = mock(AgenticSession.class);
        when(agenticFlowService.getSession(parentId)).thenReturn(mockSession);
        when(mockSession.getUsageCollector()).thenReturn(realCollector);

        AgenticResponse response = buildResponseWithToolAndStatus(
            AgenticResponse.ResponseType.ERROR, parentId,
            null, null, null);

        ExtensionResponse result = converter.toExtensionResponse(response, null);

        FlowCompletionAction action = (FlowCompletionAction) result.getRemediationActions()
            .getRemediationActionList().get(0);
        assertEquals("Task encountered an error", action.getStatusMessage());
        assertEquals("sess_err_005", action.getSessionId(),
            "Parent ERROR must use the parent session ID");
        assertFalse(action.isReturnToParent(),
            "Parent ERROR must NOT be return-to-parent");
    }

    @Test
    @DisplayName("toExtensionResponse ERROR: parent with blank statusMessage uses default")
    void toExtensionResponse_error_parent_blankStatusMessage() {

        String parentId = "sess_err_006";
        SessionUsageCollector realCollector = new SessionUsageCollector(parentId, "ToolPlanner");
        AgenticSession mockSession = mock(AgenticSession.class);
        when(agenticFlowService.getSession(parentId)).thenReturn(mockSession);
        when(mockSession.getUsageCollector()).thenReturn(realCollector);

        AgenticResponse response = buildResponseWithToolAndStatus(
            AgenticResponse.ResponseType.ERROR, parentId,
            null, null, "  ");

        ExtensionResponse result = converter.toExtensionResponse(response, null);

        FlowCompletionAction action = (FlowCompletionAction) result.getRemediationActions()
            .getRemediationActionList().get(0);
        assertEquals("Task encountered an error", action.getStatusMessage());
        assertEquals("sess_err_006", action.getSessionId(),
            "Parent ERROR must use the parent session ID");
        assertFalse(action.isReturnToParent(),
            "Parent ERROR must NOT be return-to-parent");
    }

    @Test
    @DisplayName("toExtensionResponse ERROR: child uses STATUS, parent uses STANDARD")
    void toExtensionResponse_error_informStepType_childStatus_parentStandard() {

        // Child error - uses STATUS (ephemeral, as parent will show the final error)
        AgenticResponse childResponse = buildResponseWithToolAndStatus(
            AgenticResponse.ResponseType.ERROR, "sess_err_007.child_abc",
            null, null, "Child issue");

        ExtensionResponse childResult = converter.toExtensionResponse(childResponse, null);
        FlowCompletionAction childAction = (FlowCompletionAction) childResult.getRemediationActions()
            .getRemediationActionList().get(0);
        assertEquals(InformStepType.STATUS, childAction.getInformStepType(),
            "Child ERROR should use InformStepType.STATUS (parent shows the final error)");

        // Parent error - uses STANDARD (persists in UI)
        String parentId = "sess_err_008";
        SessionUsageCollector realCollector = new SessionUsageCollector(parentId, "ToolPlanner");
        AgenticSession mockSession = mock(AgenticSession.class);
        when(agenticFlowService.getSession(parentId)).thenReturn(mockSession);
        when(mockSession.getUsageCollector()).thenReturn(realCollector);

        AgenticResponse parentResponse = buildResponseWithToolAndStatus(
            AgenticResponse.ResponseType.ERROR, parentId,
            null, null, "Parent error");

        ExtensionResponse parentResult = converter.toExtensionResponse(parentResponse, null);
        FlowCompletionAction parentAction = (FlowCompletionAction) parentResult.getRemediationActions()
            .getRemediationActionList().get(0);
        assertEquals(InformStepType.STANDARD, parentAction.getInformStepType(),
            "Parent ERROR must use InformStepType.STANDARD so the error message persists in the UI");
    }

    // =========================================================================
    // Section 11 — resolveStatusMessage and error code integration
    // =========================================================================

    @Test
    @DisplayName("resolveStatusMessage: every AgenticErrorCode maps to expected user-facing message")
    void testResolveStatusMessage_allCodes() throws Exception {

        java.lang.reflect.Method resolveMethod = AgenticResponseConverter.class.getDeclaredMethod(
                "resolveStatusMessage", AgenticResponse.class, AgenticErrorCode.class);
        resolveMethod.setAccessible(true);

        // Map.of() is capped at 10 entries; use Map.ofEntries for 12+ codes.
        Map<AgenticErrorCode, String> expectedMessages = Map.ofEntries(
            Map.entry(AgenticErrorCode.JSON_PARSE_FAILED, "Unable to process AI response"),
            Map.entry(AgenticErrorCode.TOOL_VALIDATION_FAILED, "Unable to select a valid tool"),
            Map.entry(AgenticErrorCode.ARGUMENT_VALIDATION_FAILED, "Tool arguments failed validation"),
            Map.entry(AgenticErrorCode.OUTPUT_VALIDATION_FAILED, "Output variables failed validation"),
            Map.entry(AgenticErrorCode.MALFORMED_RESPONSE, "Unable to process AI response"),
            Map.entry(AgenticErrorCode.SESSION_NOT_FOUND, "Session expired or not found"),
            Map.entry(AgenticErrorCode.PARENT_SESSION_NOT_FOUND, "Parent task session not found"),
            Map.entry(AgenticErrorCode.PARENT_SESSION_TERMINATED, "Parent task has ended"),
            Map.entry(AgenticErrorCode.PERSISTENCE_FAILED, "An internal error occurred"),
            Map.entry(AgenticErrorCode.GUARDRAIL_BLOCKED, "Request blocked by security policy"),
            Map.entry(AgenticErrorCode.EXECUTION_LIMIT_ITERATION, "Task exceeded the maximum number of steps"),
            Map.entry(AgenticErrorCode.CONTEXT_LENGTH_EXCEEDED, "The input data is too large for the AI model to process"),
            Map.entry(AgenticErrorCode.UNEXPECTED_ERROR, "Task encountered an error")
        );

        for (AgenticErrorCode code : AgenticErrorCode.values()) {

            AgenticResponse response = new AgenticResponse();
            response.setSessionId("sess_resolve_all");
            response.setResponseType(AgenticResponse.ResponseType.ERROR);
            response.setErrorMessage("internal error");
            // statusMessage intentionally null — force code-based resolution

            String resolved = (String) resolveMethod.invoke(converter, response, code);
            assertNotNull(resolved, "resolveStatusMessage must not return null for code " + code);
            assertFalse(resolved.isBlank(), "resolveStatusMessage must not return blank for code " + code);

            String expected = expectedMessages.get(code);
            assertNotNull(expected, "Test is missing expected message for code " + code);
            assertEquals(expected, resolved,
                "resolveStatusMessage returned wrong message for " + code);
        }
    }

    @Test
    @DisplayName("handleError: child with errorCode and null statusMessage gets resolved message in prefix")
    void testHandleError_childWithErrorCode() {

        String parentSessionId = "sess_err_child_code";
        String childSessionId = parentSessionId + ".child_abc";

        AgenticResponse agenticResponse = new AgenticResponse();
        agenticResponse.setSessionId(childSessionId);
        agenticResponse.setResponseType(AgenticResponse.ResponseType.ERROR);
        agenticResponse.setErrorMessage("JSON parse failed internally");
        agenticResponse.setOutputVariables(new HashMap<>());
        // statusMessage intentionally null; errorCode passed as parameter

        ExtensionResponse result = converter.toExtensionResponse(agenticResponse, AgenticErrorCode.JSON_PARSE_FAILED);

        FlowCompletionAction action = (FlowCompletionAction) result.getRemediationActions()
            .getRemediationActionList().get(0);
        assertTrue(action.getStatusMessage().contains("Sub-task issue"),
            "Child error must have 'Sub-task issue' prefix");
        assertTrue(action.getStatusMessage().contains("Unable to process AI response"),
            "Child error must contain the resolved error code message");
    }

    @Test
    @DisplayName("handleError: parent with errorCode and null statusMessage gets resolved message")
    void testHandleError_parentWithErrorCode() {

        String parentId = "sess_err_parent_code";
        SessionUsageCollector realCollector = new SessionUsageCollector(parentId, "ToolPlanner");
        AgenticSession mockSession = mock(AgenticSession.class);
        when(agenticFlowService.getSession(parentId)).thenReturn(mockSession);
        when(mockSession.getUsageCollector()).thenReturn(realCollector);

        AgenticResponse agenticResponse = new AgenticResponse();
        agenticResponse.setSessionId(parentId);
        agenticResponse.setResponseType(AgenticResponse.ResponseType.ERROR);
        agenticResponse.setErrorMessage("persistence failure");
        agenticResponse.setOutputVariables(new HashMap<>());
        // statusMessage intentionally null; errorCode passed as parameter

        ExtensionResponse result = converter.toExtensionResponse(agenticResponse, AgenticErrorCode.PERSISTENCE_FAILED);

        FlowCompletionAction action = (FlowCompletionAction) result.getRemediationActions()
            .getRemediationActionList().get(0);
        assertEquals("An internal error occurred", action.getStatusMessage(),
            "Parent error with PERSISTENCE_FAILED code must resolve to 'An internal error occurred'");
    }

    @Test
    @DisplayName("handleError: null errorCode and null statusMessage falls back to generic message")
    void testHandleError_noErrorCode() {

        String parentId = "sess_err_no_code";
        SessionUsageCollector realCollector = new SessionUsageCollector(parentId, "ToolPlanner");
        AgenticSession mockSession = mock(AgenticSession.class);
        when(agenticFlowService.getSession(parentId)).thenReturn(mockSession);
        when(mockSession.getUsageCollector()).thenReturn(realCollector);

        AgenticResponse agenticResponse = new AgenticResponse();
        agenticResponse.setSessionId(parentId);
        agenticResponse.setResponseType(AgenticResponse.ResponseType.ERROR);
        agenticResponse.setErrorMessage("some error");
        agenticResponse.setOutputVariables(new HashMap<>());
        // errorCode and statusMessage intentionally null

        ExtensionResponse result = converter.toExtensionResponse(agenticResponse, null);

        FlowCompletionAction action = (FlowCompletionAction) result.getRemediationActions()
            .getRemediationActionList().get(0);
        assertEquals("Task encountered an error", action.getStatusMessage(),
            "Null errorCode must fall back to 'Task encountered an error'");
    }

    @Test
    @DisplayName("Jackson deserialization: AgenticResponse has no errorCode field")
    void testErrorCode_jacksonDeserializationSafe() throws Exception {

        ObjectMapper mapper = new ObjectMapper();

        // Simulate LLM JSON output — no errorCode field (field removed in Phase 1)
        String llmJson = "{\"sessionId\":\"sess_llm\",\"responseType\":\"ERROR\",\"errorMessage\":\"cannot proceed\"}";

        AgenticResponse deserialized = mapper.readValue(llmJson, AgenticResponse.class);
        assertEquals("sess_llm", deserialized.getSessionId());
        assertEquals(AgenticResponse.ResponseType.ERROR, deserialized.getResponseType());
    }

    @Test
    @DisplayName("errorCode field removed: must not appear in Gson serialized JSON")
    void testErrorCode_notSerializedByGson() {

        Gson gson = new Gson();

        AgenticResponse response = new AgenticResponse();
        response.setSessionId("sess_transient_gson");
        response.setResponseType(AgenticResponse.ResponseType.ERROR);
        response.setErrorMessage("test error");

        String json = gson.toJson(response);

        assertFalse(json.contains("errorCode"),
            "errorCode must not appear in Gson-serialized JSON — field was removed from AgenticResponse");
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private AgenticResponse buildExecuteToolResponse(String sessionId,
                                                     String toolName,
                                                     Map<String, Object> args,
                                                     Map<String, Object> outputVars) {

        AgenticResponse agenticResponse = new AgenticResponse();
        agenticResponse.setSessionId(sessionId);
        agenticResponse.setResponseType(AgenticResponse.ResponseType.EXECUTE_TOOL);
        agenticResponse.setCommand(new AgenticCommand(toolName, args));
        agenticResponse.setOutputVariables(outputVars);
        return agenticResponse;
    }

    private AgenticResponse buildResponseWithToolAndStatus(
            AgenticResponse.ResponseType type, String sessionId,
            String toolName, Map<String, Object> args, String statusMessage) {

        AgenticResponse response = new AgenticResponse();
        response.setSessionId(sessionId);
        response.setResponseType(type);
        response.setStatusMessage(statusMessage);
        if (type == AgenticResponse.ResponseType.EXECUTE_TOOL) {

            response.setCommand(new AgenticCommand(toolName, args != null ? args : Map.of()));
        } else if (type == AgenticResponse.ResponseType.COMPLETE) {

            response.setFinalResponse("completed");
            response.setOutputVariables(Map.of());
        } else if (type == AgenticResponse.ResponseType.ERROR) {

            response.setErrorMessage("something went wrong");
            response.setOutputVariables(Map.of());
        }
        return response;
    }

    private ExecuteToolAction extractExecuteToolAction(AgenticResponse agenticResponse) {

        ExtensionResponse result = converter.toExtensionResponse(agenticResponse, null);
        ExecuteToolAction action = (ExecuteToolAction) result.getRemediationActions()
            .getRemediationActionList().get(0);
        assertEquals(InformStepType.STATUS, action.getInformStepType(),
            "EXECUTE_TOOL must always use InformStepType.STATUS");
        return action;
    }
}
