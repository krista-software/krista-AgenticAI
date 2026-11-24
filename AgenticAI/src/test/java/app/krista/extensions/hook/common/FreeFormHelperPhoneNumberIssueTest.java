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

package app.krista.extensions.hook.common;

import app.krista.model.base.FreeForm;
import app.krista.model.base.PhoneNumber;
import app.krista.model.field.Field;
import app.krista.model.field.NamedField;
import app.krista.model.field.NamedValuedField;
import org.junit.Before;
import org.junit.Test;

import java.util.*;

import static org.junit.Assert.*;

/**
 * Test to verify the fix for the PhoneNumber object conversion issue.
 * This reproduces the exact scenario from the logs where a PhoneNumber object
 * was being passed to LLMToFieldValueConverter instead of being converted to LLM format first.
 */
@SuppressWarnings("unchecked")
public class FreeFormHelperPhoneNumberIssueTest {

    private FreeForm testFreeForm;

    @Before
    public void setUp() throws Exception {
        createTestFreeFormWithPhoneNumberIssue();
    }

    private void createTestFreeFormWithPhoneNumberIssue() {
        testFreeForm = new FreeForm();

        // Create Department field (Text)
        Field departmentField = createField("com.krista.fields.Text", Map.of(), true);
        testFreeForm.put("Department", "Department", departmentField, "sales");

        // Create Address MultiField with Street (Text) and Phone Number (Phone)
        Field addressField = createMultiFieldWithPhoneNumber();
        
        // Create the nested value map with PhoneNumber object (as it exists in the system)
        Map<String, Object> addressValue = new HashMap<>();
        addressValue.put("Street", null);
        addressValue.put("Phone Number", new PhoneNumber(0, 8105681198L)); // PhoneNumber object as in logs
        
        testFreeForm.put("Address", "Address", addressField, addressValue);
    }

    private Field createMultiFieldWithPhoneNumber() {
        // Create nested fields as NamedField objects (not HashMap)
        List<NamedField> fields = new ArrayList<>();
        
        // Street field
        Map<String, NamedValuedField> streetOptions = new HashMap<>();
        NamedValuedField streetRequiredOption = new NamedValuedField("required", "Boolean", true, Map.of(), Map.of());
        streetOptions.put("required", streetRequiredOption);
        NamedField streetField = new NamedField("Street", "com.krista.fields.Text", Map.of(), streetOptions);
        fields.add(streetField);
        
        // Phone Number field  
        Map<String, NamedValuedField> phoneOptions = new HashMap<>();
        NamedValuedField phoneRequiredOption = new NamedValuedField("required", "Boolean", true, Map.of(), Map.of());
        phoneOptions.put("required", phoneRequiredOption);
        NamedField phoneField = new NamedField("Phone Number", "com.krista.fields.Phone", Map.of(), phoneOptions);
        fields.add(phoneField);
        
        Map<String, Object> attributes = Map.of("fields", fields);
        Map<String, NamedValuedField> options = new HashMap<>();
        NamedValuedField requiredOption = new NamedValuedField("required", "Boolean", true, Map.of(), Map.of());
        options.put("required", requiredOption);
        
        return new Field("com.krista.fields.MultiField", attributes, options);
    }

    private Field createField(String fieldType, Map<String, Object> attributes, boolean isMandatory) {
        Map<String, NamedValuedField> options = new HashMap<>();
        NamedValuedField requiredOption = new NamedValuedField("required", "Boolean", isMandatory, Map.of(), Map.of());
        options.put("required", requiredOption);
        return new Field(fieldType, attributes, options);
    }

    private NamedValuedField createNamedValuedField(String name, Object value) {
        return new NamedValuedField(name, "Boolean", value, Map.of(), Map.of());
    }

    /**
     * Test the exact scenario from logs:
     * - FreeForm has Address MultiField with PhoneNumber object
     * - LLM response updates only Street field
     * - updateFreeFormValues should handle PhoneNumber object correctly
     */
    @Test
    public void testUpdateFreeFormValues_WithPhoneNumberObject_ShouldNotPassObjectToLLMConverter() {
        // Arrange - Simulate LLM response that only updates Street field
        Map<String, Object> llmResponse = new HashMap<>();
        Map<String, Object> addressUpdate = new HashMap<>();
        addressUpdate.put("Street", "Ratu road"); // Only Street is updated, Phone Number is not in LLM response
        llmResponse.put("Address", addressUpdate);

        Map<String, Object> validationErrors = new HashMap<>(); // No validation errors

        // Act - This should not throw "Expected Number or String but got: PhoneNumber"
        FreeForm updatedFreeForm = null;
        Exception caughtException = null;
        
        try {
            updatedFreeForm = FreeFormHelper.updateFreeFormValues(testFreeForm, llmResponse, validationErrors);
        } catch (Exception e) {
            caughtException = e;
        }

        // Assert - Should not throw exception
        assertNull("Should not throw exception when handling PhoneNumber objects", caughtException);
        assertNotNull("Updated FreeForm should not be null", updatedFreeForm);

        // Verify the update worked correctly
        Map<String, Object> updatedAddressValue = (Map<String, Object>) 
            updatedFreeForm.getTypedValues().get("Address").getValue();
        
        assertNotNull("Address value should not be null", updatedAddressValue);
        assertEquals("Street should be updated", "Ratu road", updatedAddressValue.get("Street"));
        
        // Phone Number should be preserved as PhoneNumber object or converted properly
        Object phoneValue = updatedAddressValue.get("Phone Number");
        assertNotNull("Phone Number should be preserved", phoneValue);
        
        // Phone Number should either be preserved as PhoneNumber object or converted to Long
        assertTrue("Phone Number should be PhoneNumber object or Long", 
            phoneValue instanceof PhoneNumber || phoneValue instanceof Long);
        
        if (phoneValue instanceof PhoneNumber) {
            assertEquals("Phone Number should preserve original value", 8105681198L, 
                ((PhoneNumber) phoneValue).getNationalNumber());
        } else if (phoneValue instanceof Long) {
            assertEquals("Phone Number should preserve original value", Long.valueOf(8105681198L), phoneValue);
        }
    }

    /**
     * Test convertFreeFormToFieldValueMap with PhoneNumber objects
     * This tests the conversion that happens after updateFreeFormValues
     */
    @Test
    public void testConvertFreeFormToFieldValueMap_WithPhoneNumberObject_ShouldConvertCorrectly() {
        // Act - This should not throw exception when converting PhoneNumber objects
        Map<String, Object> result = null;
        Exception caughtException = null;
        
        try {
            result = FreeFormHelper.convertFreeFormToFieldValueMap(testFreeForm);
        } catch (Exception e) {
            caughtException = e;
        }

        // Assert - Should not throw exception
        assertNull("Should not throw exception when converting PhoneNumber objects", caughtException);
        assertNotNull("Result should not be null", result);

        // Verify Address field conversion
        assertTrue("Should contain Address field", result.containsKey("Address"));
        Map<String, Object> addressField = (Map<String, Object>) result.get("Address");
        assertNotNull("Address field should not be null", addressField);
        
        // Verify nested value conversion
        Object addressValue = addressField.get("value");
        assertNotNull("Address value should not be null", addressValue);
        
        if (addressValue instanceof Map) {
            Map<String, Object> addressValueMap = (Map<String, Object>) addressValue;
            Object phoneValue = addressValueMap.get("Phone Number");
            
            // Phone Number should be converted to Long (LLM representation)
            if (phoneValue != null) {
                assertTrue("Phone Number should be converted to Long for LLM", phoneValue instanceof Long);
                assertEquals("Phone Number should have correct value", Long.valueOf(8105681198L), phoneValue);
            }
        }
    }

    /**
     * Test the complete flow: updateFreeFormValues followed by convertFreeFormToFieldValueMap
     */
    @Test
    public void testCompleteFlow_UpdateThenConvert_ShouldHandlePhoneNumberCorrectly() {
        // Arrange
        Map<String, Object> llmResponse = new HashMap<>();
        Map<String, Object> addressUpdate = new HashMap<>();
        addressUpdate.put("Street", "Ratu road");
        llmResponse.put("Address", addressUpdate);

        Map<String, Object> validationErrors = new HashMap<>();

        // Act - Complete flow as in the logs
        Exception caughtException = null;
        FreeForm updatedFreeForm = null;
        Map<String, Object> convertedResult = null;
        
        try {
            // Step 1: Update FreeForm values
            updatedFreeForm = FreeFormHelper.updateFreeFormValues(testFreeForm, llmResponse, validationErrors);
            
            // Step 2: Convert to field value map (as done in AskAPersonHandlerExtractVariables)
            convertedResult = FreeFormHelper.convertFreeFormToFieldValueMap(updatedFreeForm);
        } catch (Exception e) {
            caughtException = e;
        }

        // Assert - Complete flow should work without exceptions
        assertNull("Complete flow should not throw exception", caughtException);
        assertNotNull("Updated FreeForm should not be null", updatedFreeForm);
        assertNotNull("Converted result should not be null", convertedResult);
        
        // Verify the final result
        assertTrue("Should contain Address field", convertedResult.containsKey("Address"));
        Map<String, Object> addressField = (Map<String, Object>) convertedResult.get("Address");
        Object addressValue = addressField.get("value");
        
        if (addressValue instanceof Map) {
            Map<String, Object> addressValueMap = (Map<String, Object>) addressValue;
            assertEquals("Street should be updated", "Ratu road", addressValueMap.get("Street"));
            
            Object phoneValue = addressValueMap.get("Phone Number");
            if (phoneValue != null) {
                assertTrue("Phone Number should be Long in final result", phoneValue instanceof Long);
                assertEquals("Phone Number should preserve value", Long.valueOf(8105681198L), phoneValue);
            }
        }
    }
}