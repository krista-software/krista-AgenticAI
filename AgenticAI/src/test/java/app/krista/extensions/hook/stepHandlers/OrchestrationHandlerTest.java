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
import app.krista.extensions.hook.agentic.service.AgenticFlowService;
import app.krista.extensions.hook.common.OrchestrationContext;
import app.krista.model.base.FreeForm;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link OrchestrationHandler} error-handling paths.
 *
 * <p>OrchestrationHandler's constructor wires real LLM infrastructure
 * (ToolPlannerFactory, ModelFactory) that requires live API credentials.
 * Rather than pulling in those dependencies, this test class creates a
 * Mockito mock of {@code OrchestrationHandler} with
 * {@code doCallRealMethod()} on {@code processRequest}, then injects
 * mock collaborators ({@code AgenticFlowService}, {@code AgenticResponseConverter})
 * into the private final fields via reflection.
 *
 * <p>This pattern tests the real catch-block logic in {@code processRequest}
 * without exercising the LLM stack.
 *
 * <p>Phase 3 coverage target:
 * <ul>
 *   <li>{@code UNEXPECTED_ERROR} — raw {@link RuntimeException} from
 *       {@code agenticFlowService} is caught by the generic {@code catch (Exception e)}
 *       block and routes to {@code responseConverter.toExtensionResponse(..., UNEXPECTED_ERROR)}.
 * </ul>
 */
public class OrchestrationHandlerTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(OrchestrationHandlerTest.class);

    // -----------------------------------------------------------------------
    // Collaborator mocks injected into the handler under test
    // -----------------------------------------------------------------------

    @Mock
    private AgenticFlowService agenticFlowService;

    @Mock
    private AgenticResponseConverter responseConverter;

    /**
     * The handler under test — created as a Mockito partial mock so the
     * real {@code processRequest} logic executes while the private fields
     * can be replaced with our mocks.
     */
    private OrchestrationHandler handler;

    // -----------------------------------------------------------------------
    // Shared test fixtures
    // -----------------------------------------------------------------------

    /** A well-formed orchestration context that routes to handleNewRequest(). */
    private OrchestrationContext newRequestContext;

    @BeforeEach
    void setUp() throws Exception {

        MockitoAnnotations.openMocks(this);

        // Create a partial mock: the constructor is NOT called (Mockito bypasses it),
        // but processRequest() will execute the real implementation via doCallRealMethod.
        handler = mock(OrchestrationHandler.class);
        doCallRealMethod().when(handler).processRequest(
            any(), any(), any(), any(), any(), any(), any(), any());

        // Inject our mocks into the two private final fields via reflection.
        injectField("agenticFlowService", agenticFlowService);
        injectField("responseConverter", responseConverter);

        // Build a new-request orchestration context (sessionId = null → isNewRequest() = true).
        // toolDefinitions must be non-null so isOrchestration() returns true.
        newRequestContext = OrchestrationContext.of(
            List.of(Map.of("name", "test_tool")),  // non-null → isOrchestration() = true
            null,                                   // null sessionId → isNewRequest() = true
            null,
            null,
            "What can I help you with?",
            List.of(),
            List.of(),
            null                                    // no policy brief in these tests
        );
    }

    // -----------------------------------------------------------------------
    // Tests
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("UNEXPECTED_ERROR — raw RuntimeException from agenticFlowService maps to UNEXPECTED_ERROR via responseConverter")
    void testUnexpectedRuntimeException_MapsToUnexpectedError() throws Exception {

        // Given: agenticFlowService.startFlow(...) throws an unhandled RuntimeException
        when(agenticFlowService.hasSession(any())).thenReturn(false);
        when(agenticFlowService.startFlow(any()))
            .thenThrow(new RuntimeException("boom — simulated unexpected failure"));

        // Given: responseConverter.toExtensionResponse(...) returns a non-null response
        //        so processRequest can return without NPE
        AgenticResponse errorResponse = new AgenticResponse(null, AgenticResponse.ResponseType.ERROR);
        ExtensionResponse stubExtensionResponse = mock(ExtensionResponse.class);
        when(responseConverter.isChildSession(any())).thenReturn(false);
        when(responseConverter.toExtensionResponse(any(), eq(AgenticErrorCode.UNEXPECTED_ERROR)))
            .thenReturn(stubExtensionResponse);

        // When
        LOGGER.debug("Invoking processRequest; expecting UNEXPECTED_ERROR routing");
        handler.processRequest(
            AgentAction.ORCHESTRATE,
            new FreeForm(),
            "form message",
            "user message",
            null,
            null,
            "en",
            newRequestContext
        );

        // Then: the converter must have been called with UNEXPECTED_ERROR as the error code
        verify(responseConverter).toExtensionResponse(any(), eq(AgenticErrorCode.UNEXPECTED_ERROR));
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    /**
     * Uses reflection to set a private final field on {@code handler}.
     *
     * @param fieldName  the name of the declared field in {@link OrchestrationHandler}
     * @param value      the mock to inject
     */
    private void injectField(String fieldName, Object value) throws Exception {

        Field field = OrchestrationHandler.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(handler, value);
    }
}
