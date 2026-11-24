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
import org.junit.Test;
import org.junit.Before;

import java.util.*;

import static org.junit.Assert.*;

@SuppressWarnings("unchecked")
public class FreeFormFromFreeFormUpdateTest {

    private FreeForm targetForm;
    private FreeForm sourceForm;
    private FreeForm emptyTargetForm;
    private FreeForm emptySourceForm;

    @Before
    public void setUp() throws Exception {
        createTestFreeFormObjects();
    }

    private void createTestFreeFormObjects() {
        // Create target FreeForm with various field types
        targetForm = new FreeForm();
        
        // Text field
        Field textField = createField("com.krista.fields.Text", Map.of("minLength", 2, "maxLength", 50), false);
        targetForm.put("name", "Name Field", textField, "Original Name");
        
        // Number field
        Field numberField = createField("com.krista.fields.Number", Map.of("minValue", 0, "maxValue", 100), false);
        targetForm.put("age", "Age Field", numberField, 25);
        
        // Switch field
        Field switchField = createField("com.krista.fields.Switch", Map.of(), false);
        targetForm.put("isActive", "Active Field", switchField, false);
        
        // Email field with null value
        Field emailField = createField("com.krista.fields.Email", Map.of(), false);
        targetForm.put("email", "Email Field", emailField, null);

        // Create source FreeForm with updated values
        sourceForm = new FreeForm();
        sourceForm.put("name", "Name Field", textField, "Updated Name");
        sourceForm.put("age", "Age Field", numberField, 30);
        sourceForm.put("isActive", "Active Field", switchField, true);
        sourceForm.put("email", "Email Field", emailField, "updated@example.com");
        
        // Additional field in source that doesn't exist in target
        Field phoneField = createField("com.krista.fields.Phone", Map.of(), false);
        sourceForm.put("phone", "Phone Field", phoneField, new PhoneNumber(1, 5551234567L));

        // Create empty forms
        emptyTargetForm = new FreeForm();
        emptySourceForm = new FreeForm();
    }

    private Field createField(String fieldType, Map<String, Object> attributes, boolean isMandatory) {
        Map<String, NamedValuedField> options = new HashMap<>();
        NamedValuedField requiredOption = new NamedValuedField("required", "Boolean", isMandatory, Map.of(), Map.of());
        options.put("required", requiredOption);
        return new Field(fieldType, attributes, options);
    }

    private Map<String, NamedValuedField> createOptionsMap(boolean isMandatory) {
        Map<String, NamedValuedField> options = new HashMap<>();
        NamedValuedField requiredOption = new NamedValuedField("required", "Boolean", isMandatory, Map.of(), Map.of());
        options.put("required", requiredOption);
        return options;
    }

    // Basic functionality tests
    @Test
    public void testUpdateFreeFormFromFreeForm_WithSimpleFields_ShouldOverwriteTrue_UpdatesAllMatchingFields() {
        // Act
        FreeForm result = FreeFormDefaultsHelper.updateFreeFormFromFreeForm(targetForm, sourceForm, true);

        // Assert
        assertNotNull(result);
        assertEquals("Updated Name", result.getTypedValues().get("name").getValue());
        assertEquals(30, result.getTypedValues().get("age").getValue());
        assertEquals(true, result.getTypedValues().get("isActive").getValue());
        assertEquals("updated@example.com", result.getTypedValues().get("email").getValue());
        
        // Non-matching field should not be added
        assertFalse(result.getTypedValues().containsKey("phone"));
    }

    @Test
    public void testUpdateFreeFormFromFreeForm_WithSimpleFields_ShouldOverwriteFalse_UpdatesOnlyEmptyFields() {
        // Act
        FreeForm result = FreeFormDefaultsHelper.updateFreeFormFromFreeForm(targetForm, sourceForm, false);

        // Assert
        assertNotNull(result);
        assertEquals("Original Name", result.getTypedValues().get("name").getValue()); // Should not be updated
        assertEquals(25, result.getTypedValues().get("age").getValue()); // Should not be updated
        assertEquals(false, result.getTypedValues().get("isActive").getValue()); // Should not be updated
        assertEquals("updated@example.com", result.getTypedValues().get("email").getValue()); // Should be updated (was null)
        
        // Non-matching field should not be added
        assertFalse(result.getTypedValues().containsKey("phone"));
    }

    @Test
    public void testUpdateFreeFormFromFreeForm_WithNonMatchingFields_IgnoresNonMatchingFields() {
        // Arrange
        FreeForm limitedTargetForm = new FreeForm();
        Field textField = createField("com.krista.fields.Text", Map.of(), false);
        Field numberField = createField("com.krista.fields.Number", Map.of(), false);
        
        limitedTargetForm.put("name", "Name Field", textField, "Original Name");
        limitedTargetForm.put("age", "Age Field", numberField, 25);

        FreeForm limitedSourceForm = new FreeForm();
        limitedSourceForm.put("name", "Name Field", textField, "Updated Name");
        limitedSourceForm.put("email", "Email Field", createField("com.krista.fields.Email", Map.of(), false), "test@example.com");
        // Note: sourceForm doesn't have "age" field

        // Act
        FreeForm result = FreeFormDefaultsHelper.updateFreeFormFromFreeForm(limitedTargetForm, limitedSourceForm, true);

        // Assert
        assertNotNull(result);
        assertEquals("Updated Name", result.getTypedValues().get("name").getValue()); // Should be updated
        assertEquals(25, result.getTypedValues().get("age").getValue()); // Should remain original (not in source)
        assertFalse(result.getTypedValues().containsKey("email")); // Should not be added
    }

    @Test
    public void testUpdateFreeFormFromFreeForm_WithIncompatibleFieldTypes_KeepsOriginalValues() {
        // Arrange
        FreeForm incompatibleTargetForm = new FreeForm();
        Field textField = createField("com.krista.fields.Text", Map.of(), false);
        incompatibleTargetForm.put("field1", "Text Field", textField, "Original Text");

        FreeForm incompatibleSourceForm = new FreeForm();
        Field numberField = createField("com.krista.fields.Number", Map.of(), false);
        incompatibleSourceForm.put("field1", "Number Field", numberField, 42); // Different field type

        // Act
        FreeForm result = FreeFormDefaultsHelper.updateFreeFormFromFreeForm(incompatibleTargetForm, incompatibleSourceForm, true);

        // Assert
        assertNotNull(result);
        assertEquals("Original Text", result.getTypedValues().get("field1").getValue()); // Should remain original
    }

    // Edge case tests
    @Test
    public void testUpdateFreeFormFromFreeForm_WithEmptyTarget_ReturnsEmptyForm() {
        // Act
        FreeForm result = FreeFormDefaultsHelper.updateFreeFormFromFreeForm(emptyTargetForm, sourceForm, true);

        // Assert
        assertNotNull(result);
        assertTrue(result.getTypedValues().isEmpty());
    }

    @Test
    public void testUpdateFreeFormFromFreeForm_WithEmptySource_ReturnsOriginalTarget() {
        // Act
        FreeForm result = FreeFormDefaultsHelper.updateFreeFormFromFreeForm(targetForm, emptySourceForm, true);

        // Assert
        assertNotNull(result);
        assertEquals(4, result.getTypedValues().size()); // Same as original target
        assertEquals("Original Name", result.getTypedValues().get("name").getValue());
        assertEquals(25, result.getTypedValues().get("age").getValue());
        assertEquals(false, result.getTypedValues().get("isActive").getValue());
        assertNull(result.getTypedValues().get("email").getValue());
    }

    @Test
    public void testUpdateFreeFormFromFreeForm_WithBothEmpty_ReturnsEmptyForm() {
        // Act
        FreeForm result = FreeFormDefaultsHelper.updateFreeFormFromFreeForm(emptyTargetForm, emptySourceForm, true);

        // Assert
        assertNotNull(result);
        assertTrue(result.getTypedValues().isEmpty());
    }

    @Test
    public void testUpdateFreeFormFromFreeForm_WithNullValues_HandlesCorrectly() {
        // Arrange
        FreeForm nullTargetForm = new FreeForm();
        Field textField = createField("com.krista.fields.Text", Map.of(), false);
        nullTargetForm.put("field1", "Field 1", textField, "Original Value");
        nullTargetForm.put("field2", "Field 2", textField, null);

        FreeForm nullSourceForm = new FreeForm();
        nullSourceForm.put("field1", "Field 1", textField, null); // Null source value
        nullSourceForm.put("field2", "Field 2", textField, "New Value");

        // Act - shouldOverwrite = true
        FreeForm result1 = FreeFormDefaultsHelper.updateFreeFormFromFreeForm(nullTargetForm, nullSourceForm, true);
        
        // Act - shouldOverwrite = false
        FreeForm result2 = FreeFormDefaultsHelper.updateFreeFormFromFreeForm(nullTargetForm, nullSourceForm, false);

        // Assert
        assertNotNull(result1);
        assertEquals("Original Value", result1.getTypedValues().get("field1").getValue()); // Null source shouldn't overwrite
        assertEquals("New Value", result1.getTypedValues().get("field2").getValue());

        assertNotNull(result2);
        assertEquals("Original Value", result2.getTypedValues().get("field1").getValue()); // Shouldn't overwrite when false
        assertEquals("New Value", result2.getTypedValues().get("field2").getValue()); // Should update null target
    }

    // Helper methods for creating nested field structures
    private Field createMultiField() {
        // Create a MultiField with nested Text and Number fields
        Map<String, Object> attributes = new HashMap<>();

        // Create actual NamedField objects for the nested fields
        List<NamedField> nestedFields = new ArrayList<>();

        // Create firstName field
        NamedField firstNameField = new NamedField("firstName", "com.krista.fields.Text",
            Map.of(), createOptionsMap(true));
        nestedFields.add(firstNameField);

        // Create lastName field
        NamedField lastNameField = new NamedField("lastName", "com.krista.fields.Text",
            Map.of(), createOptionsMap(false));
        nestedFields.add(lastNameField);

        // Create age field
        NamedField ageField = new NamedField("age", "com.krista.fields.Number",
            Map.of(), createOptionsMap(false));
        nestedFields.add(ageField);

        attributes.put("fields", nestedFields);

        return createField("com.krista.fields.MultiField", attributes, true);
    }

    private Field createListField() {
        // Create a List field with Text element type
        Map<String, Object> attributes = new HashMap<>();

        // In a real List field, the 'field' attribute would contain the element field definition
        Field elementField = createField("com.krista.fields.Text", Map.of(), false);
        attributes.put("field", elementField);

        return createField("com.krista.fields.List", attributes, false);
    }

    // Tests for nested structures (MultiField)
    @Test
    public void testUpdateFreeFormFromFreeForm_WithMultiField_ShouldOverwriteTrue_UpdatesNestedFields() {
        // Arrange
        FreeForm targetFormWithMulti = new FreeForm();
        Field multiField = createMultiField();

        Map<String, Object> targetNestedValue = new HashMap<>();
        targetNestedValue.put("firstName", "John");
        targetNestedValue.put("lastName", "Doe");
        targetNestedValue.put("age", 30);
        targetFormWithMulti.put("person", "Person MultiField", multiField, targetNestedValue);

        FreeForm sourceFormWithMulti = new FreeForm();
        Map<String, Object> sourceNestedValue = new HashMap<>();
        sourceNestedValue.put("firstName", "Jane");
        sourceNestedValue.put("lastName", "Smith");
        sourceNestedValue.put("age", 25);
        sourceFormWithMulti.put("person", "Person MultiField", multiField, sourceNestedValue);

        // Act
        FreeForm result = FreeFormDefaultsHelper.updateFreeFormFromFreeForm(targetFormWithMulti, sourceFormWithMulti, true);

        // Assert
        assertNotNull(result);
        Map<String, Object> updatedPersonValue = (Map<String, Object>)
            result.getTypedValues().get("person").getValue();

        assertEquals("Jane", updatedPersonValue.get("firstName"));
        assertEquals("Smith", updatedPersonValue.get("lastName"));
        assertEquals(25, updatedPersonValue.get("age"));
    }

    @Test
    public void testUpdateFreeFormFromFreeForm_WithNullField_ShouldOverwriteFalse() {
        // Arrange - Simple test with null field
        FreeForm targetForm = new FreeForm();
        Field textField = createField("com.krista.fields.Text", Map.of(), false);

        targetForm.put("field1", "Field 1", textField, "Original");
        targetForm.put("field2", "Field 2", textField, null); // This should be updated

        FreeForm sourceForm = new FreeForm();
        sourceForm.put("field1", "Field 1", textField, "Updated");
        sourceForm.put("field2", "Field 2", textField, "NewValue");

        // Act - shouldOverwrite = false
        FreeForm result = FreeFormDefaultsHelper.updateFreeFormFromFreeForm(targetForm, sourceForm, false);

        // Assert
        assertNotNull(result);
        assertEquals("Original", result.getTypedValues().get("field1").getValue()); // Should not be updated
        assertEquals("NewValue", result.getTypedValues().get("field2").getValue()); // Should be updated (was null)
    }

    @Test
    public void testUpdateFreeFormFromFreeForm_WithSimpleMultiField_ShouldOverwriteFalse() {
        // Arrange - Simple MultiField test
        FreeForm targetForm = new FreeForm();
        Field multiField = createMultiField();

        Map<String, Object> targetNestedValue = new HashMap<>();
        targetNestedValue.put("firstName", "John");
        targetNestedValue.put("lastName", null); // This should be updated
        targetNestedValue.put("age", 30);
        targetForm.put("person", "Person MultiField", multiField, targetNestedValue);

        FreeForm sourceForm = new FreeForm();
        Map<String, Object> sourceNestedValue = new HashMap<>();
        sourceNestedValue.put("firstName", "Jane"); // This should NOT be updated (target not empty)
        sourceNestedValue.put("lastName", "Smith"); // This should be updated (target is null)
        sourceNestedValue.put("age", 25); // This should NOT be updated (target not empty)
        sourceForm.put("person", "Person MultiField", multiField, sourceNestedValue);

        // Act - shouldOverwrite = false
        FreeForm result = FreeFormDefaultsHelper.updateFreeFormFromFreeForm(targetForm, sourceForm, false);

        // Assert
        assertNotNull(result);
        Map<String, Object> updatedPersonValue = (Map<String, Object>)
            result.getTypedValues().get("person").getValue();

        assertEquals("John", updatedPersonValue.get("firstName")); // Should not be updated (not empty)
        assertEquals("Smith", updatedPersonValue.get("lastName")); // Should be updated (was null)
        assertEquals(30, updatedPersonValue.get("age")); // Should not be updated (not empty)
    }

    @Test
    public void testUpdateFreeFormFromFreeForm_WithPartialMultiField_UpdatesOnlyMatchingNestedFields() {
        // Arrange
        FreeForm targetFormWithMulti = new FreeForm();
        Field multiField = createMultiField();

        Map<String, Object> targetNestedValue = new HashMap<>();
        targetNestedValue.put("firstName", "John");
        targetNestedValue.put("lastName", "Doe");
        targetNestedValue.put("age", 30);
        targetFormWithMulti.put("person", "Person MultiField", multiField, targetNestedValue);

        FreeForm sourceFormWithMulti = new FreeForm();
        Map<String, Object> sourceNestedValue = new HashMap<>();
        sourceNestedValue.put("firstName", "Jane");
        // Note: lastName and age are missing from source
        sourceFormWithMulti.put("person", "Person MultiField", multiField, sourceNestedValue);

        // Act
        FreeForm result = FreeFormDefaultsHelper.updateFreeFormFromFreeForm(targetFormWithMulti, sourceFormWithMulti, true);

        // Assert
        assertNotNull(result);
        Map<String, Object> updatedPersonValue = (Map<String, Object>)
            result.getTypedValues().get("person").getValue();

        assertEquals("Jane", updatedPersonValue.get("firstName")); // Should be updated
        assertEquals("Doe", updatedPersonValue.get("lastName")); // Should remain original
        assertEquals(30, updatedPersonValue.get("age")); // Should remain original
    }

    // Tests for List fields
    @Test
    public void testUpdateFreeFormFromFreeForm_WithListField_ShouldOverwriteTrue_UpdatesList() {
        // Arrange
        FreeForm targetFormWithList = new FreeForm();
        Field listField = createListField();

        List<String> targetList = new ArrayList<>(List.of("Item1", "Item2", "Item3"));
        targetFormWithList.put("items", "Items List", listField, targetList);

        FreeForm sourceFormWithList = new FreeForm();
        List<String> sourceList = List.of("UpdatedItem1", "UpdatedItem2");
        sourceFormWithList.put("items", "Items List", listField, sourceList);

        // Act
        FreeForm result = FreeFormDefaultsHelper.updateFreeFormFromFreeForm(targetFormWithList, sourceFormWithList, true);

        // Assert
        assertNotNull(result);
        List<String> updatedList = (List<String>) result.getTypedValues().get("items").getValue();

        assertEquals(2, updatedList.size());
        assertEquals("UpdatedItem1", updatedList.get(0));
        assertEquals("UpdatedItem2", updatedList.get(1));
    }

    @Test
    public void testUpdateFreeFormFromFreeForm_WithListField_ShouldOverwriteFalse_KeepsOriginalNonEmptyList() {
        // Arrange
        FreeForm targetFormWithList = new FreeForm();
        Field listField = createListField();

        List<String> targetList = new ArrayList<>(List.of("Item1", "Item2", "Item3"));
        targetFormWithList.put("items", "Items List", listField, targetList);

        FreeForm sourceFormWithList = new FreeForm();
        List<String> sourceList = List.of("UpdatedItem1", "UpdatedItem2");
        sourceFormWithList.put("items", "Items List", listField, sourceList);

        // Act
        FreeForm result = FreeFormDefaultsHelper.updateFreeFormFromFreeForm(targetFormWithList, sourceFormWithList, false);

        // Assert
        assertNotNull(result);
        List<String> updatedList = (List<String>) result.getTypedValues().get("items").getValue();

        assertEquals(3, updatedList.size()); // Should keep original list
        assertEquals("Item1", updatedList.get(0));
        assertEquals("Item2", updatedList.get(1));
        assertEquals("Item3", updatedList.get(2));
    }

    @Test
    public void testUpdateFreeFormFromFreeForm_WithEmptyListField_ShouldOverwriteFalse_UpdatesEmptyList() {
        // Arrange
        FreeForm targetFormWithList = new FreeForm();
        Field listField = createListField();

        List<String> targetList = new ArrayList<>(); // Empty list
        targetFormWithList.put("items", "Items List", listField, targetList);

        FreeForm sourceFormWithList = new FreeForm();
        List<String> sourceList = List.of("UpdatedItem1", "UpdatedItem2");
        sourceFormWithList.put("items", "Items List", listField, sourceList);

        // Act
        FreeForm result = FreeFormDefaultsHelper.updateFreeFormFromFreeForm(targetFormWithList, sourceFormWithList, false);

        // Assert
        assertNotNull(result);
        List<String> updatedList = (List<String>) result.getTypedValues().get("items").getValue();

        assertEquals(2, updatedList.size()); // Should update empty list
        assertEquals("UpdatedItem1", updatedList.get(0));
        assertEquals("UpdatedItem2", updatedList.get(1));
    }

    // Simple nested structure test (2 levels only)
    @Test
    public void testUpdateFreeFormFromFreeForm_WithSimpleNestedStructure_UpdatesCorrectly() {
        // Arrange - Create simple 2-level nested structure
        FreeForm targetForm = new FreeForm();
        Field multiField = createMultiField();

        Map<String, Object> targetNestedValue = new HashMap<>();
        targetNestedValue.put("firstName", "John");
        targetNestedValue.put("lastName", "Doe");
        targetNestedValue.put("age", 30);
        targetForm.put("person", "Person MultiField", multiField, targetNestedValue);

        FreeForm sourceForm = new FreeForm();
        Map<String, Object> sourceNestedValue = new HashMap<>();
        sourceNestedValue.put("firstName", "Jane"); // Update firstName
        sourceNestedValue.put("age", 25); // Update age
        // lastName not included in source - should remain original
        sourceForm.put("person", "Person MultiField", multiField, sourceNestedValue);

        // Act
        FreeForm result = FreeFormDefaultsHelper.updateFreeFormFromFreeForm(targetForm, sourceForm, true);

        // Assert
        assertNotNull(result);
        Map<String, Object> resultPerson = (Map<String, Object>)
            result.getTypedValues().get("person").getValue();

        assertEquals("Jane", resultPerson.get("firstName")); // Should be updated
        assertEquals("Doe", resultPerson.get("lastName")); // Should remain original
        assertEquals(25, resultPerson.get("age")); // Should be updated
    }



    // Additional edge case tests
    @Test
    public void testUpdateFreeFormFromFreeForm_WithEmptyValues_HandlesCorrectly() {
        // Arrange - Create structure with some empty nested values
        FreeForm targetForm = new FreeForm();
        Field textField = createField("com.krista.fields.Text", Map.of(), false);

        // Simple test with direct fields (no nested MultiField)
        targetForm.put("firstName", "First Name", textField, "John");
        targetForm.put("lastName", "Last Name", textField, ""); // Empty string
        targetForm.put("email", "Email", createField("com.krista.fields.Email", Map.of(), false), null); // Null value

        FreeForm sourceForm = new FreeForm();
        sourceForm.put("firstName", "First Name", textField, "Jane");
        sourceForm.put("lastName", "Last Name", textField, "Smith");
        sourceForm.put("email", "Email", createField("com.krista.fields.Email", Map.of(), false), "test@example.com");

        // Act - shouldOverwrite = false
        FreeForm result = FreeFormDefaultsHelper.updateFreeFormFromFreeForm(targetForm, sourceForm, false);

        // Assert
        assertNotNull(result);
        assertEquals("John", result.getTypedValues().get("firstName").getValue()); // Should not be updated (not empty)
        assertEquals("Smith", result.getTypedValues().get("lastName").getValue()); // Should be updated (empty string)
        assertEquals("test@example.com", result.getTypedValues().get("email").getValue()); // Should be updated (null)
    }

    @Test
    public void testUpdateFreeFormFromFreeForm_WithMixedFieldTypes_UpdatesOnlyCompatibleFields() {
        // Arrange - Create forms with mix of compatible and incompatible fields
        FreeForm targetForm = new FreeForm();
        targetForm.put("textField", "Text Field", createField("com.krista.fields.Text", Map.of(), false), "Original Text");
        targetForm.put("numberField", "Number Field", createField("com.krista.fields.Number", Map.of(), false), 42);
        targetForm.put("multiField", "Multi Field", createMultiField(), Map.of("firstName", "John"));

        FreeForm sourceForm = new FreeForm();
        sourceForm.put("textField", "Text Field", createField("com.krista.fields.Text", Map.of(), false), "Updated Text");
        sourceForm.put("numberField", "Number Field", createField("com.krista.fields.Email", Map.of(), false), "not-a-number@example.com"); // Incompatible type
        sourceForm.put("multiField", "Multi Field", createMultiField(), Map.of("firstName", "Jane"));

        // Act
        FreeForm result = FreeFormDefaultsHelper.updateFreeFormFromFreeForm(targetForm, sourceForm, true);

        // Assert
        assertNotNull(result);
        assertEquals("Updated Text", result.getTypedValues().get("textField").getValue()); // Should be updated
        assertEquals(42, result.getTypedValues().get("numberField").getValue()); // Should remain original (incompatible)

        Map<String, Object> updatedMultiValue = (Map<String, Object>) result.getTypedValues().get("multiField").getValue();
        assertEquals("Jane", updatedMultiValue.get("firstName")); // Should be updated
    }

    @Test
    public void testUpdateFreeFormFromFreeForm_SimpleCase() {
        // Arrange - Very simple case with just two text fields
        FreeForm targetForm = new FreeForm();
        Field textField = createField("com.krista.fields.Text", Map.of(), false);

        targetForm.put("field1", "Field 1", textField, "Original1");
        targetForm.put("field2", "Field 2", textField, null); // This should be updated

        FreeForm sourceForm = new FreeForm();
        sourceForm.put("field2", "Field 2", textField, "Updated2"); // Only update field2

        // Act - shouldOverwrite = false
        FreeForm result = FreeFormDefaultsHelper.updateFreeFormFromFreeForm(targetForm, sourceForm, false);

        // Assert
        assertNotNull(result);
        assertEquals("Original1", result.getTypedValues().get("field1").getValue()); // Should remain original
        assertEquals("Updated2", result.getTypedValues().get("field2").getValue()); // Should be updated (was null)
    }

    @Test
    public void testUpdateFreeFormFromFreeForm_WithPartialSourceMultiField_ShouldOverwriteFalse_PreservesAllTargetSubFields() {
        // Arrange - Create target with MultiField having multiple subfields with values
        FreeForm targetFormWithMulti = new FreeForm();
        Field multiField = createMultiField();

        Map<String, Object> targetNestedValue = new HashMap<>();
        targetNestedValue.put("firstName", "John");      // Has value - should be preserved
        targetNestedValue.put("lastName", "Doe");        // Has value - should be preserved
        targetNestedValue.put("age", 30);                // Has value - should be preserved
        targetFormWithMulti.put("person", "Person MultiField", multiField, targetNestedValue);

        // Create source with only ONE subfield (partial update)
        FreeForm sourceFormWithMulti = new FreeForm();
        Map<String, Object> sourceNestedValue = new HashMap<>();
        sourceNestedValue.put("firstName", "Jane");      // Only updating firstName
        // lastName and age are NOT in source - they should remain in target
        sourceFormWithMulti.put("person", "Person MultiField", multiField, sourceNestedValue);

        // Act - shouldOverwrite = false
        FreeForm result = FreeFormDefaultsHelper.updateFreeFormFromFreeForm(targetFormWithMulti, sourceFormWithMulti, false);

        // Assert - All original target subfields should be preserved, firstName should NOT be updated
        assertNotNull(result);
        Map<String, Object> updatedPersonValue = (Map<String, Object>)
            result.getTypedValues().get("person").getValue();

        assertEquals("John", updatedPersonValue.get("firstName"));   // Should remain original (not overwritten)
        assertEquals("Doe", updatedPersonValue.get("lastName"));     // Should remain original (preserved from target)
        assertEquals(30, updatedPersonValue.get("age"));             // Should remain original (preserved from target)
        assertEquals(3, updatedPersonValue.size());                  // Should have all 3 subfields
    }
}
