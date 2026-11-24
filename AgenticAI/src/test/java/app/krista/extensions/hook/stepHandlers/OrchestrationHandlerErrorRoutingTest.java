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

package app.krista.extensions.hook.stepHandlers;

import app.krista.extension.executor.ExtensionResponse;
import app.krista.extension.executor.impl.AgentAction;
import app.krista.extensions.hook.agentic.converter.AgenticResponseConverter;
import app.krista.extensions.hook.agentic.dto.AgenticErrorCode;
import app.krista.extensions.hook.agentic.dto.AgenticResponse;
import app.krista.extensions.hook.agentic.guardrails.ContextLengthExceededException;
import app.krista.extensions.hook.agentic.guardrails.GuardrailViolationException;
import app.krista.extensions.hook.agentic.service.AgenticFlowService;
import app.krista.extensions.hook.agentic.session.AgenticSession;
import app.krista.extensions.hook.agentic.usagecollector.SessionUsageCollector;
import app.krista.extensions.hook.common.OrchestrationContext;
import app.krista.model.base.FreeForm;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;

/**
 * Tests for {@link OrchestrationHandler} error routing and session cleanup paths.
 *
 * <p>Complements {@link OrchestrationHandlerTest} (which covers UNEXPECTED_ERROR routing)
 * by verifying that:
 * <ul>
 *   <li>AgenticFlowException subclasses trigger session cleanup (usage logging + session deletion)</li>
 *   <li>Unexpected exceptions also trigger session cleanup</li>
 *   <li>The error response carries the correct ResponseType and errorMessage</li>
 * </ul>
 *
 * <p>Uses the same partial-mock + reflection-injection pattern as OrchestrationHandlerTest.
 */
public class OrchestrationHandlerErrorRoutingTest {

    @Mock
    private AgenticFlowService agenticFlowService;

    @Mock
    private AgenticResponseConverter responseConverter;

    private OrchestrationHandler handler;

    private OrchestrationContext newRequestContext;

    @BeforeEach
    void setUp() throws Exception {

        MockitoAnnotations.openMocks(this);

        handler = mock(OrchestrationHandler.class);
        doCallRealMethod().when(handler).processRequest(
                any(), any(), any(), any(), any(), any(), any(), any());

        injectField("agenticFlowService", agenticFlowService);
        injectField("responseConverter", responseConverter);

        newRequestContext = OrchestrationContext.of(
                List.of(Map.of("name", "test_tool")),
                null,
                null,
                null,
                "Test prompt",
                List.of(),
                List.of(),
                null
        );
    }

    @Test
    @DisplayName("AgenticFlowException triggers session cleanup — usage marked, session deleted")
    void testAgenticFlowException_CleansUpSession() throws Exception {

        when(agenticFlowService.hasSession(any())).thenReturn(false);
        when(agenticFlowService.startFlow(any()))
                .thenThrow(new ContextLengthExceededException("Input too large"));

        AgenticSession mockSession = mock(AgenticSession.class);
        SessionUsageCollector mockUsage = mock(SessionUsageCollector.class);
        SessionUsageCollector.SessionSummary mockSummary = mock(SessionUsageCollector.SessionSummary.class, RETURNS_DEEP_STUBS);
        when(mockUsage.getSessionSummary()).thenReturn(mockSummary);
        when(mockSession.getUsageCollector()).thenReturn(mockUsage);
        when(agenticFlowService.getSession(any())).thenReturn(mockSession);

        when(responseConverter.isChildSession(any())).thenReturn(false);
        when(responseConverter.toExtensionResponse(any(), eq(AgenticErrorCode.CONTEXT_LENGTH_EXCEEDED)))
                .thenReturn(mock(ExtensionResponse.class));

        handler.processRequest(
                AgentAction.ORCHESTRATE, new FreeForm(), "form", "user msg",
                null, null, "en", newRequestContext);

        verify(mockUsage).markError("Input too large", "CONTEXT_LENGTH_EXCEEDED");
        verify(agenticFlowService).deleteSession(any());
        verify(responseConverter).toExtensionResponse(any(), eq(AgenticErrorCode.CONTEXT_LENGTH_EXCEEDED));
    }

    @Test
    @DisplayName("Unexpected exception triggers session cleanup — usage marked, session deleted, UNEXPECTED_ERROR")
    void testUnexpectedException_CleansUpSession() throws Exception {

        when(agenticFlowService.hasSession(any())).thenReturn(false);
        when(agenticFlowService.startFlow(any()))
                .thenThrow(new NullPointerException("simulated NPE"));

        AgenticSession mockSession = mock(AgenticSession.class);
        SessionUsageCollector mockUsage = mock(SessionUsageCollector.class);
        SessionUsageCollector.SessionSummary mockSummary = mock(SessionUsageCollector.SessionSummary.class, RETURNS_DEEP_STUBS);
        when(mockUsage.getSessionSummary()).thenReturn(mockSummary);
        when(mockSession.getUsageCollector()).thenReturn(mockUsage);
        when(agenticFlowService.getSession(any())).thenReturn(mockSession);

        when(responseConverter.isChildSession(any())).thenReturn(false);
        when(responseConverter.toExtensionResponse(any(), eq(AgenticErrorCode.UNEXPECTED_ERROR)))
                .thenReturn(mock(ExtensionResponse.class));

        handler.processRequest(
                AgentAction.ORCHESTRATE, new FreeForm(), "form", "user msg",
                null, null, "en", newRequestContext);

        verify(mockUsage).markError("simulated NPE", "UNEXPECTED_ERROR");
        verify(agenticFlowService).deleteSession(any());
        verify(responseConverter).toExtensionResponse(any(), eq(AgenticErrorCode.UNEXPECTED_ERROR));
    }

    @Test
    @DisplayName("AgenticFlowException response carries ERROR type and correct message; null session skips cleanup")
    void testAgenticFlowException_ResponseIndicatesError_NullSessionSkipsCleanup() throws Exception {

        when(agenticFlowService.hasSession(any())).thenReturn(false);
        when(agenticFlowService.startFlow(any()))
                .thenThrow(new GuardrailViolationException("blocked content"));

        when(agenticFlowService.getSession(any())).thenReturn(null);

        when(responseConverter.isChildSession(any())).thenReturn(false);
        when(responseConverter.toExtensionResponse(any(), eq(AgenticErrorCode.GUARDRAIL_BLOCKED)))
                .thenReturn(mock(ExtensionResponse.class));

        handler.processRequest(
                AgentAction.ORCHESTRATE, new FreeForm(), "form", "user msg",
                null, null, "en", newRequestContext);

        ArgumentCaptor<AgenticResponse> responseCaptor = ArgumentCaptor.forClass(AgenticResponse.class);
        verify(responseConverter).toExtensionResponse(responseCaptor.capture(), eq(AgenticErrorCode.GUARDRAIL_BLOCKED));

        AgenticResponse captured = responseCaptor.getValue();
        assertEquals(AgenticResponse.ResponseType.ERROR, captured.getResponseType());
        assertEquals("blocked content", captured.getErrorMessage());

        verify(agenticFlowService, never()).deleteSession(any());
    }

    private void injectField(String fieldName, Object value) throws Exception {

        Field field = OrchestrationHandler.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(handler, value);
    }
}
