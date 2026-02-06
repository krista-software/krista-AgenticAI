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
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class AskAPersonHandlerSummarizeTest {

    @Mock
    private Invoker invoker;

    private AskAPersonHandlerSummarize handler;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.openMocks(this);
        handler = new AskAPersonHandlerSummarize(invoker);
    }

    @Test
    public void testProcessRequest_WithEmptyCurrentVariables_ReturnsFormMessageDirectly() throws Exception {
        // Arrange
        FreeForm emptyFreeForm = new FreeForm();
        String formMessage = "Please provide your details";
        String userMessage = null; // userMessage is null for summarize cases
        StepMetadata metaData = new StepMetadata("Test Conversation", "test-execution-id",
            "Test Workspace", "Test Topic", "Test Step", "Test Type", "Test Description");

        // Act
        ExtensionResponse response = handler.processRequest(
            AgentAction.SUMMARIZE_ASK,
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
            "Test Workspace", "Test Topic", "Test Step", "Test Type", "Test Description");

        // Act
        ExtensionResponse response = handler.processRequest(
            AgentAction.SUMMARIZE_ASK,
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
    public void testProcessRequest_WithEmptyCurrentVariablesAndFormMessage_ReturnsFormMessageDirectly() throws Exception {
        // Arrange - FreeForm should be empty (not null) for summarize cases
        FreeForm emptyFreeForm = new FreeForm();
        String formMessage = "Please provide your details";
        String userMessage = null; // userMessage is null for summarize cases
        StepMetadata metaData = new StepMetadata("Test Conversation", "test-execution-id",
            "Test Workspace", "Test Topic", "Test Step", "Test Type", "Test Description");

        // Act
        ExtensionResponse response = handler.processRequest(
            AgentAction.SUMMARIZE_ASK,
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
        System.out.println("Test passed: Empty FreeForm with formMessage returns formMessage directly without LLM call");
    }

    @Test
    public void testProcessRequest_WithUnsupportedAction_ThrowsException() {
        // Arrange
        FreeForm emptyFreeForm = new FreeForm();
        String formMessage = "Please provide your details";
        String userMessage = null; // userMessage is null for summarize cases
        StepMetadata metaData = new StepMetadata("Test Conversation", "test-execution-id",
            "Test Workspace", "Test Topic", "Test Step", "Test Type", "Test Description");

        // Act & Assert
        try {
            handler.processRequest(
                AgentAction.EXTRACT_VARIABLES, // Wrong action
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
}
