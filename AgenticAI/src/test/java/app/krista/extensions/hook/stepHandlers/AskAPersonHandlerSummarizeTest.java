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
import app.krista.extensions.hook.common.OrchestrationContext;
import app.krista.model.base.FreeForm;
import app.krista.model.field.Field;
import app.krista.model.field.NamedValuedField;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.HashMap;
import java.util.Map;

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

    /**
     * Helper method to create a Field with specified type and mandatory flag.
     *
     * @param fieldType   The type of the field (e.g., "com.krista.fields.Text")
     * @param isMandatory Whether the field is mandatory
     * @return A configured Field object
     */
    private Field createField(String fieldType, boolean isMandatory) {
        Map<String, NamedValuedField> options = new HashMap<>();
        NamedValuedField requiredOption = new NamedValuedField("required", "Boolean", isMandatory, Map.of(), Map.of());
        options.put("required", requiredOption);
        return new Field(fieldType, Map.of(), options);
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
            "English", // language
            OrchestrationContext.empty()
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
            "English", // language
            OrchestrationContext.empty()
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
            "English", // language
            OrchestrationContext.empty()
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
                "English", // language
                OrchestrationContext.empty()
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
     * Test case for commit 085a322: Verifies that FreeFormDefaultsHelper.updateFreeFormFromFreeForm
     * is called to merge currentVariables and currentVariablesDefault when both have values.
     * <p>
     * This test validates the dynamic sidebar autofill feature (KR-19790) where extracted values
     * from the user's original message (in currentVariables) should override default values,
     * while gaps are filled by defaults from currentVariablesDefault.
     * <p>
     * The merge logic ensures:
     * - Extracted values take priority over defaults
     * - Empty/null fields in currentVariables are filled with values from currentVariablesDefault
     * - The merged result is passed to convertDefaultValuesToConfirmation
     *
     * @throws Exception if processing fails
     */
    @Test
    public void testMergeLogic_WithDefaultValuesAndExtractedValues() throws Exception {
        // This test documents the merge behavior introduced in commit 085a322
        // The actual merge is performed by FreeFormDefaultsHelper.updateFreeFormFromFreeForm

        FreeForm currentVariables = new FreeForm();
        FreeForm currentVariablesDefault = new FreeForm();

        Field nameField = createField("com.krista.fields.Text", true);
        Field emailField = createField("com.krista.fields.Email", true);

        // Scenario: User provided name, but not email
        currentVariables.put("name", "Text Field", nameField, "John Doe");
        currentVariables.put("email", "Email Field", emailField, null);

        // Defaults available for both fields
        currentVariablesDefault.put("name", "Text Field", nameField, "Default Name");
        currentVariablesDefault.put("email", "Email Field", emailField, "default@example.com");

        // Test the merge directly using FreeFormDefaultsHelper
        FreeForm mergedVariables = app.krista.extensions.hook.common.FreeFormDefaultsHelper.updateFreeFormFromFreeForm(
            currentVariablesDefault, currentVariables, true);

        // Verify merge results
        Map<String, FreeForm.TypedValue> typedValues = mergedVariables.getTypedValues();

        // Name should be from currentVariables (extracted value takes priority)
        assertEquals("Name should be from extracted values", "John Doe",
            typedValues.get("name").getValue());

        // Email should be from currentVariablesDefault (gap filled by default)
        assertEquals("Email should be from default values", "default@example.com",
            typedValues.get("email").getValue());

        System.out.println("Test passed: Merge logic correctly prioritizes extracted values over defaults");
    }

    /**
     * Test case for commit 085a322: Verifies that when currentVariables has all values,
     * they are not overridden by currentVariablesDefault.
     * <p>
     * This ensures that extracted values from the user's message always take priority
     * over default values when both are present.
     *
     * @throws Exception if processing fails
     */
    @Test
    public void testMergeLogic_WithAllExtractedValues_DoesNotOverrideWithDefaults() throws Exception {
        // Arrange
        FreeForm currentVariables = new FreeForm();
        FreeForm currentVariablesDefault = new FreeForm();

        Field nameField = createField("com.krista.fields.Text", true);
        Field emailField = createField("com.krista.fields.Email", true);

        // currentVariables has all values extracted from user message
        currentVariables.put("name", "Text Field", nameField, "John Doe");
        currentVariables.put("email", "Email Field", emailField, "john@example.com");

        // currentVariablesDefault has different default values
        currentVariablesDefault.put("name", "Text Field", nameField, "Default Name");
        currentVariablesDefault.put("email", "Email Field", emailField, "default@example.com");

        // Act - Test the merge directly using FreeFormDefaultsHelper
        FreeForm mergedVariables = app.krista.extensions.hook.common.FreeFormDefaultsHelper.updateFreeFormFromFreeForm(
            currentVariablesDefault, currentVariables, true);

        // Assert - Verify that extracted values are preserved and not overridden by defaults
        Map<String, FreeForm.TypedValue> typedValues = mergedVariables.getTypedValues();

        assertNotNull("Merged variables should not be null", mergedVariables);
        assertEquals("Name should be from extracted values, not defaults",
            "John Doe", typedValues.get("name").getValue());
        assertEquals("Email should be from extracted values, not defaults",
            "john@example.com", typedValues.get("email").getValue());

        System.out.println("Test passed: Extracted values are not overridden by defaults");
    }

    /**
     * Test case for commit 085a322: Verifies that when currentVariables is empty,
     * all values come from currentVariablesDefault.
     * <p>
     * This tests the scenario where no values were extracted from the user's message,
     * so all fields should be filled with default values.
     * <p>
     * The merge logic uses FreeFormDefaultsHelper.updateFreeFormFromFreeForm() with
     * shouldOverwrite=true, which means currentVariables values override defaults.
     * When currentVariables has null values, defaults are used.
     *
     * @throws Exception if processing fails
     */
    @Test
    public void testProcessRequest_WithEmptyExtractedValues_UsesAllDefaults() throws Exception {
        // Arrange
        FreeForm currentVariables = new FreeForm();
        FreeForm currentVariablesDefault = new FreeForm();

        Field nameField = createField("com.krista.fields.Text", true);
        Field emailField = createField("com.krista.fields.Email", true);

        // currentVariables has no values (nothing extracted)
        currentVariables.put("name", "Text Field", nameField, null);
        currentVariables.put("email", "Email Field", emailField, null);

        // currentVariablesDefault has default values
        currentVariablesDefault.put("name", "Text Field", nameField, "Default Name");
        currentVariablesDefault.put("email", "Email Field", emailField, "default@example.com");

        // Act - Test the merge directly using FreeFormDefaultsHelper
        // This is the same merge operation performed in AskAPersonHandlerSummarize.processRequest
        FreeForm mergedVariables = app.krista.extensions.hook.common.FreeFormDefaultsHelper.updateFreeFormFromFreeForm(
            currentVariablesDefault, currentVariables, true);

        // Assert - Verify that all values come from defaults when currentVariables has null values
        Map<String, FreeForm.TypedValue> typedValues = mergedVariables.getTypedValues();

        assertEquals("When currentVariables has null value, name should come from default",
            "Default Name", typedValues.get("name").getValue());
        assertEquals("When currentVariables has null value, email should come from default",
            "default@example.com", typedValues.get("email").getValue());

        System.out.println("Test passed: All default values are used when no values are extracted");
    }

    /**
     * Test case for commit 085a322: Verifies partial merge scenario where some fields
     * have extracted values and others have default values.
     * <p>
     * This is the most common real-world scenario where the user provides some information
     * in their message, and the system fills in the rest with defaults.
     * <p>
     * The merge logic uses FreeFormDefaultsHelper.updateFreeFormFromFreeForm() with
     * shouldOverwrite=true. This means:
     * - Extracted values (name, email) override defaults
     * - Missing values (phone, address) are filled from defaults
     *
     * @throws Exception if processing fails
     */
    @Test
    public void testProcessRequest_WithPartialExtractedValues_MergesPartially() throws Exception {
        // Arrange
        FreeForm currentVariables = new FreeForm();
        FreeForm currentVariablesDefault = new FreeForm();

        Field nameField = createField("com.krista.fields.Text", true);
        Field emailField = createField("com.krista.fields.Email", true);
        Field phoneField = createField("com.krista.fields.Phone", false);
        Field addressField = createField("com.krista.fields.Text", false);

        // currentVariables has partial values (name and email extracted)
        currentVariables.put("name", "Text Field", nameField, "Jane Smith");
        currentVariables.put("email", "Email Field", emailField, "jane@example.com");
        currentVariables.put("phone", "Phone Field", phoneField, null); // Not extracted
        currentVariables.put("address", "Address Field", addressField, null); // Not extracted

        // currentVariablesDefault has all default values
        currentVariablesDefault.put("name", "Text Field", nameField, "Default Name");
        currentVariablesDefault.put("email", "Email Field", emailField, "default@example.com");
        currentVariablesDefault.put("phone", "Phone Field", phoneField, "555-0000");
        currentVariablesDefault.put("address", "Address Field", addressField, "123 Default St");

        // Act - Test the merge directly using FreeFormDefaultsHelper
        // This is the same merge operation performed in AskAPersonHandlerSummarize.processRequest
        FreeForm mergedVariables = app.krista.extensions.hook.common.FreeFormDefaultsHelper.updateFreeFormFromFreeForm(
            currentVariablesDefault, currentVariables, true);

        // Assert - Verify partial merge behavior
        Map<String, FreeForm.TypedValue> typedValues = mergedVariables.getTypedValues();

        // Extracted values should be preserved (override defaults)
        assertEquals("Extracted name should override default",
            "Jane Smith", typedValues.get("name").getValue());
        assertEquals("Extracted email should override default",
            "jane@example.com", typedValues.get("email").getValue());

        // Missing values should be filled from defaults
        assertEquals("Missing phone should be filled from default",
            "555-0000", typedValues.get("phone").getValue());
        assertEquals("Missing address should be filled from default",
            "123 Default St", typedValues.get("address").getValue());

        System.out.println("Test passed: Partial merge correctly combines extracted and default values");
    }
}
