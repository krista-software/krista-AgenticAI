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

import java.util.HashMap;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class UnsupportedFieldTypeTest {

    @Mock
    private Invoker invoker;

    @Mock
    private Field richTextField;
    
    private AskAPersonHandlerSummarize summarizeHandler;
    private AskAPersonHandlerExtractVariables extractVariablesHandler;
    private InformAPersonHandlerSummarize informHandler;
    private FreeForm freeFormWithUnsupportedField;
    private StepMetadata metaData;
    private StepMetadata askStepMetaData;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.openMocks(this);
        
        // Create handlers
        summarizeHandler = new AskAPersonHandlerSummarize(invoker);
        extractVariablesHandler = new AskAPersonHandlerExtractVariables(invoker);
        informHandler = new InformAPersonHandlerSummarize(invoker);

        // Setup metadata
        metaData = new StepMetadata("Test Conversation", "test-execution-id",
            "Test Workspace", "Test Topic", "Test Step", "CST_1030", "Test Description");
        askStepMetaData = new StepMetadata("Test Conversation", "test-execution-id",
            "Test Workspace", "Test Topic", "ASK", "CST_1040", "Test Description");
        
        // Setup mock field with unsupported type
        when(richTextField.getFieldType()).thenReturn("com.krista.fields.RichText");
        when(richTextField.getAttributeValues()).thenReturn(new HashMap<>());
        when(richTextField.getOptions()).thenReturn(new HashMap<>());
        
        // Create FreeForm with unsupported field
        freeFormWithUnsupportedField = new FreeForm();
        FreeForm.TypedValue typedValue = new FreeForm.TypedValue("Rich text field", richTextField, "Some rich text content");
        freeFormWithUnsupportedField.put("richTextField", typedValue);
    }

    @Test
    public void testSummarizeHandler_WithUnsupportedFieldType_ConvertEntireFormToQuestion_ReturnsStaticErrorMessage() throws Exception {
        // Act - Test convertEntireFormToQuestion case (formMessage is not null)
        ExtensionResponse response = summarizeHandler.processRequest(
            AgentAction.SUMMARIZE_ASK,
            freeFormWithUnsupportedField,
            "Please provide your details",
            null,
            metaData,
            new FreeForm(), // currentVariablesDefault - empty for test
            "English" // language
        );

        // Assert
        assertNotNull(response);
        assertEquals(ExtensionResponse.Result.SUCCESS, response.getResult());

        // Check that the response contains the static error message for convertEntireFormToQuestion
        String responseMessage = extractResponseMessage(response);
        assertEquals("Field not supported, please switch to form based mode", responseMessage);
    }

    @Test
    public void testSummarizeHandler_WithUnsupportedFieldType_ConvertFormResponseToText_ReturnsErrorMessage() throws Exception {
        // Act - Test case where formMessage is null and there's an unsupported field
        // The exception should be caught and return the error message
        ExtensionResponse response = summarizeHandler.processRequest(
            AgentAction.SUMMARIZE_ASK,
            freeFormWithUnsupportedField,
            null, // formMessage is null
            null,
            metaData,
            new FreeForm(), // currentVariablesDefault - empty for test
            "English" // language
        );

        // Assert
        assertNotNull(response);
        assertEquals(ExtensionResponse.Result.SUCCESS, response.getResult());

        // Check that the response contains the error message for unsupported field
        String responseMessage = extractResponseMessage(response);
        assertEquals("Field not supported, please switch to form based mode", responseMessage);
    }

    @Test
    public void testExtractVariablesHandler_WithUnsupportedFieldType_ReturnsStaticErrorMessage() {
        // Act
        ExtensionResponse response = extractVariablesHandler.processRequest(
            AgentAction.EXTRACT_VARIABLES,
            freeFormWithUnsupportedField,
            "Please provide your details",
            "I want to fill the form",
            metaData,
            new FreeForm(), // currentVariablesDefault - empty for test
            "English" // language
        );

        // Assert
        assertNotNull(response);
        assertEquals(ExtensionResponse.Result.SUCCESS, response.getResult());
        
        // Check that the response contains the static error message
        String responseMessage = extractResponseMessage(response);
        assertEquals("Field not supported, please switch to form based mode", responseMessage);
    }

    @Test
    public void testInformHandler_WithUnsupportedFieldType_UserResponseCase_ReturnsSpecificErrorMessage() throws Exception {
        // Act - Test InformAPersonHandlerSummarize with stepType != CST_1030 (should use convertFormResponseToText)
        ExtensionResponse response = informHandler.processRequest(
            AgentAction.SUMMARIZE_INFORM,
            freeFormWithUnsupportedField,
            "Information message",
            null,
            askStepMetaData, // stepType = CST_1040 (not CST_1030)
            new FreeForm(), // currentVariablesDefault - empty for test
            "English" // language
        );

        // Assert
        assertNotNull(response);
        assertEquals(ExtensionResponse.Result.SUCCESS, response.getResult());

        // Check that the response contains the specific error message for convertFormResponseToText
        String responseMessage = extractResponseMessage(response);
        assertEquals("Unable to convert user input to text due to presence of unsupported field", responseMessage);
    }

    @Test
    public void testInformHandler_WithUnsupportedFieldType_CST1030Step_ReturnsGenericErrorMessage() throws Exception {
        // Act - Test InformAPersonHandlerSummarize with CST_1030 step
        ExtensionResponse response = informHandler.processRequest(
            AgentAction.SUMMARIZE_INFORM,
            freeFormWithUnsupportedField,
            "Information message",
            null,
            metaData, // stepType = CST_1030
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

    private String extractResponseMessage(ExtensionResponse response) {
        if (response.getRemediationActions() != null &&
            response.getRemediationActions().getRemediationActionList() != null &&
            !response.getRemediationActions().getRemediationActionList().isEmpty()) {

            return response.getRemediationActions().getRemediationActionList().getFirst().getMessage();
        }
        return null;
    }
}
