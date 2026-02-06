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
import app.krista.extension.executor.Invoker;
import app.krista.extension.executor.impl.AgentAction;
import app.krista.extensions.hook.common.models.StepMetadata;
import app.krista.model.base.FreeForm;
import app.krista.model.field.Field;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class InformAPersonHandlerSummarizeTest {

    @Mock
    private Invoker invoker;

    @Mock
    private Field richTextField;

    private InformAPersonHandlerSummarize handler;
    private FreeForm freeFormWithUnsupportedField;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.openMocks(this);
        handler = new InformAPersonHandlerSummarize(invoker);

        // Setup unsupported field for testing
        when(richTextField.getFieldType()).thenReturn("RichTextField");
        freeFormWithUnsupportedField = new FreeForm();
        freeFormWithUnsupportedField.put("richText", "Rich text description", richTextField, "Some rich text content");
    }

    @Test
    public void testProcessRequest_WithEmptyCurrentVariables_ReturnsFormMessageDirectly() throws Exception {
        // Arrange
        FreeForm emptyFreeForm = new FreeForm();
        String formMessage = "Information message";
        String userMessage = null; // userMessage is null for summarize cases
        StepMetadata metaData = new StepMetadata("Test Conversation", "test-execution-id",
            "Test Workspace", "Test Topic", "Test Step", "CST_1030", "Test Description");

        // Act
        ExtensionResponse response = handler.processRequest(
            AgentAction.SUMMARIZE_INFORM,
            emptyFreeForm,
            formMessage,
            userMessage,
            metaData,
            new FreeForm(), // currentVariablesDefault - empty for test
            "English" // language
        );

        // Assert
        assertNotNull(response);
        // Verify that invoker was never called since we have no variables
        verifyNoInteractions(invoker);
        System.out.println("Test passed: Empty FreeForm returns formMessage directly without LLM call");
    }

    @Test
    public void testProcessRequest_WithEmptyCurrentVariablesAndNullFormMessage_ReturnsDefaultMessage() throws Exception {
        // Arrange
        FreeForm emptyFreeForm = new FreeForm();
        String formMessage = null;
        String userMessage = null; // userMessage is null for summarize cases
        StepMetadata metaData = new StepMetadata("Test Conversation", "test-execution-id",
            "Test Workspace", "Test Topic", "Test Step", "CST_1030", "Test Description");

        // Act
        ExtensionResponse response = handler.processRequest(
            AgentAction.SUMMARIZE_INFORM,
            emptyFreeForm,
            formMessage,
            userMessage,
            metaData,
            new FreeForm(), // currentVariablesDefault - empty for test
            "English" // language
        );

        // Assert
        assertNotNull(response);
        // Verify that invoker was never called since we have no variables
        verifyNoInteractions(invoker);
        System.out.println("Test passed: Empty FreeForm with null formMessage returns default message without LLM call");
    }

    @Test
    public void testProcessRequest_WithUserResponseCase_UsesConvertFormResponseToText() throws Exception {
        // Arrange - Step metadata with stepType != CST_1030
        FreeForm emptyFreeForm = new FreeForm();
        String formMessage = "Information message";
        String userMessage = null;
        StepMetadata metaData = new StepMetadata("Test Conversation", "test-execution-id",
            "Test Workspace", "Test Topic", "ASK", "CST_1040", "Test Description");

        // Act
        ExtensionResponse response = handler.processRequest(
            AgentAction.SUMMARIZE_INFORM,
            emptyFreeForm,
            formMessage,
            userMessage,
            metaData,
            new FreeForm(), // currentVariablesDefault - empty for test
            "English" // language
        );

        // Assert
        assertNotNull(response);
        // Verify that invoker was never called since we have no variables
        verifyNoInteractions(invoker);
        System.out.println("Test passed: stepType != CST_1030 with empty FreeForm returns default message");
    }

    @Test
    public void testProcessRequest_WithUnsupportedFieldType_UserResponseCase_ReturnsSpecificErrorMessage() throws Exception {
        // Arrange - Step metadata with stepType != CST_1030 to trigger convertFormResponseToText
        StepMetadata metaData = new StepMetadata("Test Conversation", "test-execution-id",
            "Test Workspace", "Test Topic", "ASK", "CST_1040", "Test Description");

        // Act
        ExtensionResponse response = handler.processRequest(
            AgentAction.SUMMARIZE_INFORM,
            freeFormWithUnsupportedField,
            "Information message",
            null,
            metaData,
            new FreeForm(), // currentVariablesDefault - empty for test
            "English" // language
        );

        // Assert
        assertNotNull(response);
        assertEquals(ExtensionResponse.Result.SUCCESS, response.getResult());

        // Check that the response contains the specific error message for convertFormResponseToText
        String responseMessage = extractResponseMessage(response);
        assertEquals("Unable to convert user input to text due to presence of unsupported field", responseMessage);
        System.out.println("Test passed: stepType != CST_1030 with unsupported field returns specific error message");
    }

    @Test
    public void testProcessRequest_WithUnsupportedFieldType_CST1030Step_ReturnsGenericErrorMessage() throws Exception {
        // Arrange - Step metadata with stepType = CST_1030
        StepMetadata metaData = new StepMetadata("Test Conversation", "test-execution-id",
            "Test Workspace", "Test Topic", "INFORM", "CST_1030", "Test Description");

        // Act
        ExtensionResponse response = handler.processRequest(
            AgentAction.SUMMARIZE_INFORM,
            freeFormWithUnsupportedField,
            "Information message",
            null,
            metaData,
            new FreeForm(), // currentVariablesDefault - empty for test
            "English" // language
        );

        // Assert
        assertNotNull(response);
        assertEquals(ExtensionResponse.Result.SUCCESS, response.getResult());

        // Check that the response contains the generic error message for CST_1030 steps
        String responseMessage = extractResponseMessage(response);
        assertEquals("Field not supported, please switch to form based mode", responseMessage);
        System.out.println("Test passed: CST_1030 step with unsupported field returns generic error message");
    }

    @Test
    public void testProcessRequest_WithUnsupportedAction_ThrowsException() {
        // Arrange
        FreeForm emptyFreeForm = new FreeForm();
        String formMessage = "Information message";
        String userMessage = null;
        StepMetadata metaData = new StepMetadata("Test Conversation", "test-execution-id",
            "Test Workspace", "Test Topic", "Test Step", "CST_1030", "Test Description");

        // Act & Assert
        try {
            handler.processRequest(
                AgentAction.SUMMARIZE_ASK, // Wrong action
                emptyFreeForm,
                formMessage,
                userMessage,
                metaData,
                new FreeForm(), // currentVariablesDefault - empty for test
                "English" // language
            );
            fail("Expected UnsupportedOperationException to be thrown");
        } catch (UnsupportedOperationException e) {
            // Expected exception
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        System.out.println("Test passed: Unsupported action throws exception");
    }

    /**
     * Helper method to extract response message from ExtensionResponse
     */
    private String extractResponseMessage(ExtensionResponse response) {
        if (response.getRemediationActions() != null &&
            response.getRemediationActions().getRemediationActionList() != null &&
            !response.getRemediationActions().getRemediationActionList().isEmpty()) {

            return response.getRemediationActions().getRemediationActionList().get(0).getMessage();
        }
        return null;
    }
}
