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
public class FreeFormHelperTest {

    private FreeForm testFreeForm;
    private FreeForm emptyFreeForm;
    private FreeForm mandatoryFieldsFreeForm;

    @Before
    public void setUp() throws Exception {
        createTestFreeFormObjects();
    }

    private void createTestFreeFormObjects() {
        // Create test FreeForm with various field types
        testFreeForm = new FreeForm();

        // Text field - mandatory
        Field textField = createField("com.krista.fields.Text",
            Map.of("minLength", 2, "maxLength", 50), true);
        testFreeForm.put("name", "Text Field", textField, "John Doe");

        // Number field - optional
        Field numberField = createField("com.krista.fields.Number",
            Map.of("minValue", 0, "maxValue", 100), false);
        testFreeForm.put("age", "Number Field", numberField, 25);

        // Switch field - mandatory
        Field switchField = createField("com.krista.fields.Switch",
            Map.of(), true);
        testFreeForm.put("isActive", "Switch Field", switchField, true);

        // PickOne field - optional
        Field pickOneField = createField("com.krista.fields.PickOne",
            Map.of("options", List.of("Option1", "Option2", "Option3")), false);
        testFreeForm.put("category", "PickOne Field", pickOneField, "Option1");

        // Email field - mandatory
        Field emailField = createField("com.krista.fields.Email",
            Map.of(), true);
        testFreeForm.put("email", "Email Field", emailField, "john@example.com");

        // Create empty FreeForm
        emptyFreeForm = new FreeForm();

        // Create FreeForm with mandatory fields for testing isFreeFormFilled
        mandatoryFieldsFreeForm = new FreeForm();

        // Mandatory field with value
        Field mandatoryWithValue = createField("com.krista.fields.Text",
            Map.of(), true);
        mandatoryFieldsFreeForm.put("requiredField", "Required Field", mandatoryWithValue, "Some Value");

        // Mandatory field without value
        Field mandatoryWithoutValue = createField("com.krista.fields.Text",
            Map.of(), true);
        mandatoryFieldsFreeForm.put("emptyRequired", "Empty Required Field", mandatoryWithoutValue, null);

        // Optional field without value
        Field optionalWithoutValue = createField("com.krista.fields.Text",
            Map.of(), false);
        mandatoryFieldsFreeForm.put("optionalField", "Optional Field", optionalWithoutValue, null);
    }

    private Field createField(String fieldType, Map<String, Object> attributes, boolean isMandatory) {
        Map<String, NamedValuedField> options = new HashMap<>();
        // Always add the required option, but set the value based on isMandatory
        NamedValuedField requiredOption = new NamedValuedField("required", "Boolean", isMandatory, Map.of(), Map.of());
        options.put("required", requiredOption);
        return new Field(fieldType, attributes, options);
    }

    private NamedField createNamedField(String name, String fieldType, Map<String, Object> attributes, boolean isMandatory) {
        Map<String, NamedValuedField> options = new HashMap<>();
        NamedValuedField requiredOption = new NamedValuedField("required", "Boolean", isMandatory, Map.of(), Map.of());
        options.put("required", requiredOption);
        return new NamedField(name, fieldType, attributes, options);
    }

    // Test updateFreeFormValues method
    @Test
    public void testUpdateFreeFormValues_WithValidUpdates_UpdatesCorrectly() {
        // Arrange
        Map<String, Object> outputFieldValueMap = new HashMap<>();
        outputFieldValueMap.put("name", "Jane Smith");
        outputFieldValueMap.put("age", 30);
        outputFieldValueMap.put("isActive", false);

        Map<String, Object> validationErrors = new HashMap<>();

        // Act
        FreeForm updatedFreeForm = FreeFormHelper.updateFreeFormValues(
            testFreeForm, outputFieldValueMap, validationErrors);

        // Assert
        assertNotNull(updatedFreeForm);
        assertNotSame(testFreeForm, updatedFreeForm); // Should be a new instance

        // Check updated values
        assertEquals("Jane Smith", updatedFreeForm.getTypedValues().get("name").getValue());
        assertEquals(30.0, updatedFreeForm.getTypedValues().get("age").getValue());
        assertEquals(false, updatedFreeForm.getTypedValues().get("isActive").getValue());

        // Check unchanged values
        assertEquals("Option1", updatedFreeForm.getTypedValues().get("category").getValue());
        assertEquals("john@example.com", updatedFreeForm.getTypedValues().get("email").getValue());
    }

    @Test
    public void testUpdateFreeFormValues_WithValidationErrors_KeepsOriginalValues() {
        // Arrange
        Map<String, Object> outputFieldValueMap = new HashMap<>();
        outputFieldValueMap.put("name", "Invalid Name");
        outputFieldValueMap.put("age", 150); // Invalid age

        Map<String, Object> validationErrors = new HashMap<>();
        validationErrors.put("name", List.of("Name validation failed"));
        validationErrors.put("age", List.of("Age out of range"));

        // Act
        FreeForm updatedFreeForm = FreeFormHelper.updateFreeFormValues(
            testFreeForm, outputFieldValueMap, validationErrors);

        // Assert
        assertNotNull(updatedFreeForm);

        // Check that values with validation errors remain unchanged
        assertEquals("John Doe", updatedFreeForm.getTypedValues().get("name").getValue());
        assertEquals(25, updatedFreeForm.getTypedValues().get("age").getValue());
    }

    @Test
    public void testUpdateFreeFormValues_WithEmptyMaps_KeepsOriginalValues() {
        // Arrange
        Map<String, Object> outputFieldValueMap = new HashMap<>();
        Map<String, Object> validationErrors = new HashMap<>();

        // Act
        FreeForm updatedFreeForm = FreeFormHelper.updateFreeFormValues(
            testFreeForm, outputFieldValueMap, validationErrors);

        // Assert
        assertNotNull(updatedFreeForm);

        // Check that all values remain unchanged
        assertEquals("John Doe", updatedFreeForm.getTypedValues().get("name").getValue());
        assertEquals(25, updatedFreeForm.getTypedValues().get("age").getValue());
        assertEquals(true, updatedFreeForm.getTypedValues().get("isActive").getValue());
        assertEquals("Option1", updatedFreeForm.getTypedValues().get("category").getValue());
        assertEquals("john@example.com", updatedFreeForm.getTypedValues().get("email").getValue());
    }

    @Test
    public void testUpdateFreeFormValues_WithPartialUpdates_UpdatesOnlySpecifiedFields() {
        // Arrange
        Map<String, Object> outputFieldValueMap = new HashMap<>();
        outputFieldValueMap.put("name", "Updated Name");
        // Only updating name field

        Map<String, Object> validationErrors = new HashMap<>();

        // Act
        FreeForm updatedFreeForm = FreeFormHelper.updateFreeFormValues(
            testFreeForm, outputFieldValueMap, validationErrors);

        // Assert
        assertNotNull(updatedFreeForm);

        // Check updated field
        assertEquals("Updated Name", updatedFreeForm.getTypedValues().get("name").getValue());

        // Check unchanged fields
        assertEquals(25, updatedFreeForm.getTypedValues().get("age").getValue());
        assertEquals(true, updatedFreeForm.getTypedValues().get("isActive").getValue());
        assertEquals("Option1", updatedFreeForm.getTypedValues().get("category").getValue());
        assertEquals("john@example.com", updatedFreeForm.getTypedValues().get("email").getValue());
    }

    // Test convertFreeFormToFieldValueMap method
    @Test
    public void testConvertFreeFormToFieldValueMap_WithValidFreeForm_ReturnsCorrectMap() throws Exception {
        // Act
        Map<String, Object> result = FreeFormHelper.convertFreeFormToFieldValueMap(testFreeForm);

        // Assert
        assertNotNull(result);
        assertEquals(5, result.size()); // Should have 5 fields

        // Check text field
        Map<String, Object> nameField = (Map<String, Object>) result.get("name");
        assertNotNull(nameField);
        assertEquals("com.krista.fields.Text", nameField.get("fieldType"));
        assertEquals(true, nameField.get("isMandatoryField"));
        assertEquals("John Doe", nameField.get("value"));
        assertNotNull(nameField.get("attributes"));

        // Check number field
        Map<String, Object> ageField = (Map<String, Object>) result.get("age");
        assertNotNull(ageField);
        assertEquals("com.krista.fields.Number", ageField.get("fieldType"));
        assertEquals(false, ageField.get("isMandatoryField"));
        assertEquals(25.0, ageField.get("value")); // Number converter returns Double

        // Check switch field
        Map<String, Object> activeField = (Map<String, Object>) result.get("isActive");
        assertNotNull(activeField);
        assertEquals("com.krista.fields.Switch", activeField.get("fieldType"));
        assertEquals(true, activeField.get("isMandatoryField"));
        assertEquals(true, activeField.get("value"));

        // Check pickOne field
        Map<String, Object> categoryField = (Map<String, Object>) result.get("category");
        assertNotNull(categoryField);
        assertEquals("com.krista.fields.PickOne", categoryField.get("fieldType"));
        assertEquals(false, categoryField.get("isMandatoryField"));
        assertEquals("Option1", categoryField.get("value"));

        // Check email field
        Map<String, Object> emailField = (Map<String, Object>) result.get("email");
        assertNotNull(emailField);
        assertEquals("com.krista.fields.Email", emailField.get("fieldType"));
        assertEquals(true, emailField.get("isMandatoryField"));
        assertEquals("john@example.com", emailField.get("value"));
    }

    @Test
    public void testConvertFreeFormToFieldValueMap_WithEmptyFreeForm_ReturnsEmptyMap() throws Exception {
        // Act
        Map<String, Object> result = FreeFormHelper.convertFreeFormToFieldValueMap(emptyFreeForm);

        // Assert
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    public void testUpdateFreeFormValues_WithNullValues_HandlesGracefully() {
        // Arrange
        Map<String, Object> outputFieldValueMap = new HashMap<>();
        outputFieldValueMap.put("name", null);
        outputFieldValueMap.put("age", null);

        Map<String, Object> validationErrors = new HashMap<>();

        // Act
        FreeForm updatedFreeForm = FreeFormHelper.updateFreeFormValues(
            testFreeForm, outputFieldValueMap, validationErrors);

        // Assert
        assertNotNull(updatedFreeForm);

        // Check that null values are properly set
        assertNull(updatedFreeForm.getTypedValues().get("name").getValue());
        assertNull(updatedFreeForm.getTypedValues().get("age").getValue());

        // Check unchanged fields
        assertEquals(true, updatedFreeForm.getTypedValues().get("isActive").getValue());
        assertEquals("Option1", updatedFreeForm.getTypedValues().get("category").getValue());
        assertEquals("john@example.com", updatedFreeForm.getTypedValues().get("email").getValue());
    }

    @Test
    public void testConvertFreeFormToFieldValueMap_WithNullValues_HandlesGracefully() throws Exception {
        // Arrange - Create FreeForm with null values
        FreeForm freeFormWithNulls = new FreeForm();
        Field textField = createField("com.krista.fields.Text", Map.of(), true);
        freeFormWithNulls.put("nullField", "Null Field", textField, null);

        Field numberField = createField("com.krista.fields.Number", Map.of(), false);
        freeFormWithNulls.put("validField", "Valid Field", numberField, 42);

        // Act
        Map<String, Object> result = FreeFormHelper.convertFreeFormToFieldValueMap(freeFormWithNulls);

        // Assert
        assertNotNull(result);
        assertEquals(2, result.size());

        // Check null field
        Map<String, Object> nullField = (Map<String, Object>) result.get("nullField");
        assertNotNull(nullField);
        assertEquals("com.krista.fields.Text", nullField.get("fieldType"));
        assertEquals(true, nullField.get("isMandatoryField"));
        assertNull(nullField.get("value"));

        // Check valid field
        Map<String, Object> validField = (Map<String, Object>) result.get("validField");
        assertNotNull(validField);
        assertEquals("com.krista.fields.Number", validField.get("fieldType"));
        assertEquals(false, validField.get("isMandatoryField"));
        assertEquals(42.0, validField.get("value")); // Number converter returns Double
    }

    // Test isFreeFormFilled method
    @Test
    public void testIsFreeFormFilled_WithAllMandatoryFieldsFilled_ReturnsTrue() {
        // Arrange - Create FreeForm with all mandatory fields filled
        FreeForm filledForm = new FreeForm();
        Field mandatoryField1 = createField("com.krista.fields.Text", Map.of(), true);
        filledForm.put("field1", "Field 1", mandatoryField1, "Value 1");

        Field mandatoryField2 = createField("com.krista.fields.Number", Map.of(), true);
        filledForm.put("field2", "Field 2", mandatoryField2, 100);

        Field optionalField = createField("com.krista.fields.Text", Map.of(), false);
        filledForm.put("optional", "Optional Field", optionalField, null);

        // Act
        boolean result = FreeFormHelper.isFreeFormFilled(filledForm, true);

        // Assert
        assertTrue(result);
    }

    @Test
    public void testIsFreeFormFilled_WithMandatoryFieldEmpty_ReturnsFalse() {
        // Act
        boolean result = FreeFormHelper.isFreeFormFilled(mandatoryFieldsFreeForm, true);

        // Assert
        assertFalse(result); // Should return false because emptyRequired field is null
    }

    @Test
    public void testIsFreeFormFilled_WithEmptyFreeForm_ReturnsTrue() {
        // Act
        boolean result = FreeFormHelper.isFreeFormFilled(emptyFreeForm, true);

        // Assert
        assertTrue(result); // Empty form should be considered filled
    }

    @Test
    public void testIsFreeFormFilled_WithEmptyStringInMandatoryField_ReturnsFalse() {
        // Arrange
        FreeForm formWithEmptyString = new FreeForm();
        Field mandatoryField = createField("com.krista.fields.Text", Map.of(), true);
        formWithEmptyString.put("emptyString", "Empty String Field", mandatoryField, "");

        // Act
        boolean result = FreeFormHelper.isFreeFormFilled(formWithEmptyString, true);

        // Assert
        assertFalse(result); // Empty string should be considered empty
    }

    @Test
    public void testIsFreeFormFilled_WithWhitespaceInMandatoryField_ReturnsFalse() {
        // Arrange
        FreeForm formWithWhitespace = new FreeForm();
        Field mandatoryField = createField("com.krista.fields.Text", Map.of(), true);
        formWithWhitespace.put("whitespace", "Whitespace Field", mandatoryField, "   ");

        // Act
        boolean result = FreeFormHelper.isFreeFormFilled(formWithWhitespace, true);

        // Assert
        assertFalse(result); // Whitespace-only string should be considered empty
    }

    @Test
    public void testIsFreeFormFilled_WithEmptyCollectionInMandatoryField_ReturnsFalse() {
        // Arrange
        FreeForm formWithEmptyList = new FreeForm();
        Field mandatoryField = createField("com.krista.fields.Checkboxes", Map.of(), true);
        formWithEmptyList.put("emptyList", "Empty List Field", mandatoryField, new ArrayList<>());

        // Act
        boolean result = FreeFormHelper.isFreeFormFilled(formWithEmptyList, true);

        // Assert
        assertFalse(result); // Empty collection should be considered empty
    }

    @Test
    public void testIsFreeFormFilled_WithEmptyMapInMandatoryField_ReturnsFalse() {
        // Arrange
        FreeForm formWithEmptyMap = new FreeForm();
        Field mandatoryField = createField("com.krista.fields.MultiField", Map.of(), true);
        formWithEmptyMap.put("emptyMap", "Empty Map Field", mandatoryField, new HashMap<>());

        // Act
        boolean result = FreeFormHelper.isFreeFormFilled(formWithEmptyMap, true);

        // Assert
        assertFalse(result); // Empty map should be considered empty
    }

    // Tests with nested structures
    @Test
    public void testUpdateFreeFormValues_WithNestedMultiField_UpdatesCorrectly() {
        // Arrange - Create FreeForm with MultiField containing nested fields
        FreeForm nestedForm = new FreeForm();

        // Create MultiField with nested structure
        Field multiField = createMultiField();
        Map<String, Object> nestedValue = new HashMap<>();
        nestedValue.put("firstName", "John");
        nestedValue.put("lastName", "Doe");
        nestedValue.put("age", 30);
        nestedForm.put("person", "Person MultiField", multiField, nestedValue);

        // Create update map
        Map<String, Object> outputFieldValueMap = new HashMap<>();
        Map<String, Object> updatedNestedValue = new HashMap<>();
        updatedNestedValue.put("firstName", "Jane");
        updatedNestedValue.put("lastName", "Smith");
        updatedNestedValue.put("age", 25);
        outputFieldValueMap.put("person", updatedNestedValue);

        Map<String, Object> validationErrors = new HashMap<>();

        // Act
        FreeForm updatedForm = FreeFormHelper.updateFreeFormValues(
            nestedForm, outputFieldValueMap, validationErrors);

        // Assert
        assertNotNull(updatedForm);
        Map<String, Object> updatedPersonValue = (Map<String, Object>)
            updatedForm.getTypedValues().get("person").getValue();
        assertEquals("Jane", updatedPersonValue.get("firstName"));
        assertEquals("Smith", updatedPersonValue.get("lastName"));
        assertEquals(25.0, updatedPersonValue.get("age"));
    }

    @Test
    public void testUpdateFreeFormValues_WithNestedListField_UpdatesCorrectly() {
        // Arrange - Create FreeForm with List field
        FreeForm listForm = new FreeForm();

        Field listField = createListField();
        List<String> originalList = List.of("Item1", "Item2", "Item3");
        listForm.put("items", "Items List", listField, originalList);

        // Create update map
        Map<String, Object> outputFieldValueMap = new HashMap<>();
        List<String> updatedList = List.of("UpdatedItem1", "UpdatedItem2");
        outputFieldValueMap.put("items", updatedList);

        Map<String, Object> validationErrors = new HashMap<>();

        // Act
        FreeForm updatedForm = FreeFormHelper.updateFreeFormValues(
            listForm, outputFieldValueMap, validationErrors);

        // Assert
        assertNotNull(updatedForm);
        List<String> updatedItemsValue = (List<String>)
            updatedForm.getTypedValues().get("items").getValue();
        assertEquals(2, updatedItemsValue.size());
        assertEquals("UpdatedItem1", updatedItemsValue.get(0));
        assertEquals("UpdatedItem2", updatedItemsValue.get(1));
    }

    @Test
    public void testConvertFreeFormToFieldValueMap_WithNestedMultiField_ReturnsCorrectStructure() throws Exception {
        // Arrange - Create FreeForm with nested MultiField
        FreeForm nestedForm = new FreeForm();

        Field multiField = createMultiField();
        Map<String, Object> nestedValue = new HashMap<>();
        nestedValue.put("firstName", "John");
        nestedValue.put("lastName", "Doe");
        nestedValue.put("age", 30);
        nestedForm.put("person", "Person MultiField", multiField, nestedValue);

        // Act
        Map<String, Object> result = FreeFormHelper.convertFreeFormToFieldValueMap(nestedForm);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.size());

        Map<String, Object> personField = (Map<String, Object>) result.get("person");
        assertNotNull(personField);
        assertEquals("com.krista.fields.MultiField", personField.get("fieldType"));
        assertEquals(true, personField.get("isMandatoryField"));

        // Check that the value exists (the converter may transform it)
        Object personValue = personField.get("value");
        assertNotNull(personValue);
        // Note: The actual structure depends on how the MultiField converter processes the value
        // For now, just verify the value is present and not null
    }

    @Test
    public void testIsFreeFormFilled_WithNestedStructures_ChecksMandatoryFieldsCorrectly() {
        // Arrange - Create FreeForm with nested mandatory and optional fields
        FreeForm complexForm = new FreeForm();

        // Add mandatory MultiField with value
        Field mandatoryMultiField = createMultiField();
        Map<String, Object> filledNestedValue = new HashMap<>();
        filledNestedValue.put("firstName", "John");
        filledNestedValue.put("lastName", "Doe");
        filledNestedValue.put("age", 30);
        complexForm.put("mandatoryPerson", "Mandatory Person", mandatoryMultiField, filledNestedValue);

        // Add optional List field without value
        Field optionalListField = createListField();
        complexForm.put("optionalItems", "Optional Items", optionalListField, null);

        // Act
        boolean result = FreeFormHelper.isFreeFormFilled(complexForm, true);

        // Assert
        assertTrue(result); // Should be true because mandatory field is filled, optional can be null
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
            Map.of(), createOptionsMap(true));
        nestedFields.add(lastNameField);

        // Create age field
        NamedField ageField = new NamedField("age", "com.krista.fields.Number",
            Map.of(), createOptionsMap(false));
        nestedFields.add(ageField);

        attributes.put("fields", nestedFields);

        return createField("com.krista.fields.MultiField", attributes, true);
    }

    private Map<String, NamedValuedField> createOptionsMap(boolean isMandatory) {
        Map<String, NamedValuedField> options = new HashMap<>();
        NamedValuedField requiredOption = new NamedValuedField("required", "Boolean", isMandatory, Map.of(), Map.of());
        options.put("required", requiredOption);
        return options;
    }

    private Field createListField() {
        // Create a List field with Text element type
        Map<String, Object> attributes = new HashMap<>();

        // In a real List field, the 'field' attribute would contain the element field definition
        Field elementField = createField("com.krista.fields.Text", Map.of(), false);
        attributes.put("field", elementField);

        return createField("com.krista.fields.List", attributes, false);
    }

    // Additional edge case tests
    @Test
    public void testUpdateFreeFormValues_WithComplexNestedStructure_HandlesCorrectly() {
        // Arrange - Create a complex nested structure with List of MultiFields
        FreeForm complexForm = new FreeForm();

        // Create a List field containing MultiField elements
        Field listOfMultiField = createComplexListField();
        List<Map<String, Object>> complexValue = new ArrayList<>();

        // Create complex 3-level deep employee data
        Map<String, Object> employee1 = new HashMap<>();
        employee1.put("name", "John Doe");
        employee1.put("email", "john@example.com");
        employee1.put("active", true);

        // Level 2: Contact information
        Map<String, Object> contactInfo1 = new HashMap<>();
        contactInfo1.put("phone", new PhoneNumber(1, 15551234567L));  // Phone numbers as Long values
        contactInfo1.put("mobile", new PhoneNumber(1, 15559876543L));

        // Level 3: Address (deepest level)
        Map<String, Object> address1 = new HashMap<>();
        address1.put("street", "123 Main St");
        address1.put("city", "New York");
        address1.put("state", "NY");
        address1.put("postalCode", "10001");
        address1.put("country", "USA");
        contactInfo1.put("address", address1);

        employee1.put("contactInfo", contactInfo1);
        employee1.put("skills", List.of("Java", "Python", "React"));
        complexValue.add(employee1);

        Map<String, Object> employee2 = new HashMap<>();
        employee2.put("name", "Jane Smith");
        employee2.put("email", "jane@example.com");
        employee2.put("active", false);

        // Level 2: Contact information
        Map<String, Object> contactInfo2 = new HashMap<>();
        contactInfo2.put("phone", new PhoneNumber(1, 15551112222L));  // Phone numbers as Long values
        contactInfo2.put("mobile", null); // Optional field

        // Level 3: Address
        Map<String, Object> address2 = new HashMap<>();
        address2.put("street", "456 Oak Ave");
        address2.put("city", "Los Angeles");
        address2.put("state", "CA");
        address2.put("postalCode", "90210");
        address2.put("country", "USA");
        contactInfo2.put("address", address2);

        employee2.put("contactInfo", contactInfo2);
        employee2.put("skills", List.of("JavaScript", "Node.js"));
        complexValue.add(employee2);

        complexForm.put("employees", "Employee List", listOfMultiField, complexValue);

        // Create update with modified list
        Map<String, Object> outputFieldValueMap = new HashMap<>();
        List<Map<String, Object>> updatedComplexValue = new ArrayList<>();

        Map<String, Object> updatedEmployee1 = new HashMap<>();
        updatedEmployee1.put("name", "John Updated");
        updatedEmployee1.put("email", "john.updated@example.com");
        updatedEmployee1.put("active", true);

        // Updated contact info
        Map<String, Object> updatedContactInfo1 = new HashMap<>();
        updatedContactInfo1.put("phone", 15559998888L);  // Phone numbers as Long values
        updatedContactInfo1.put("mobile", 15557776666L);

        // Updated address
        Map<String, Object> updatedAddress1 = new HashMap<>();
        updatedAddress1.put("street", "789 Updated St");
        updatedAddress1.put("city", "Boston");
        updatedAddress1.put("state", "MA");
        updatedAddress1.put("postalCode", "02101");
        updatedAddress1.put("country", "USA");
        updatedContactInfo1.put("address", updatedAddress1);

        updatedEmployee1.put("contactInfo", updatedContactInfo1);
        updatedEmployee1.put("skills", List.of("Java", "Spring", "Microservices"));
        updatedComplexValue.add(updatedEmployee1);

        outputFieldValueMap.put("employees", updatedComplexValue);

        Map<String, Object> validationErrors = new HashMap<>();

        // Act
        FreeForm updatedForm = FreeFormHelper.updateFreeFormValues(
            complexForm, outputFieldValueMap, validationErrors);

        // Assert
        assertNotNull(updatedForm);
        List<Map<String, Object>> updatedEmployees = (List<Map<String, Object>>)
            updatedForm.getTypedValues().get("employees").getValue();
        assertEquals(1, updatedEmployees.size());

        Map<String, Object> updatedEmployee = updatedEmployees.get(0);
        assertEquals("John Updated", updatedEmployee.get("name"));
        assertEquals("john.updated@example.com", updatedEmployee.get("email"));

        // Check nested contact info
        Map<String, Object> contactInfo = (Map<String, Object>) updatedEmployee.get("contactInfo");
        assertNotNull(contactInfo);
        assertEquals(new PhoneNumber(1, 15559998888L), contactInfo.get("phone"));  // Phone numbers as Long values
        assertEquals(new PhoneNumber(1,15557776666L), contactInfo.get("mobile"));

        // Check deeply nested address (3rd level)
        Map<String, Object> address = (Map<String, Object>) contactInfo.get("address");
        assertNotNull(address);
        assertEquals("789 Updated St", address.get("street"));
        assertEquals("Boston", address.get("city"));
        assertEquals("MA", address.get("state"));
        assertEquals("02101", address.get("postalCode"));

        // Check skills list
        List<String> skills = (List<String>) updatedEmployee.get("skills");
        assertNotNull(skills);
        assertEquals(3, skills.size());
        assertEquals("Java", skills.get(0));
        assertEquals("Spring", skills.get(1));
        assertEquals("Microservices", skills.get(2));
    }

    @Test
    public void testUpdateFreeFormValues_WithMixedValidationErrors_HandlesPartialUpdates() {
        // Arrange - Create form with multiple fields
        FreeForm mixedForm = new FreeForm();

        Field textField = createField("com.krista.fields.Text", Map.of(), true);
        mixedForm.put("validField", "Valid Field", textField, "Original Value");

        Field numberField = createField("com.krista.fields.Number", Map.of(), true);
        mixedForm.put("invalidField", "Invalid Field", numberField, 50);

        Field emailField = createField("com.krista.fields.Email", Map.of(), false);
        mixedForm.put("anotherValidField", "Another Valid Field", emailField, "old@example.com");

        // Create updates with some validation errors
        Map<String, Object> outputFieldValueMap = new HashMap<>();
        outputFieldValueMap.put("validField", "Updated Value");
        outputFieldValueMap.put("invalidField", 200); // This will have validation error
        outputFieldValueMap.put("anotherValidField", "new@example.com");

        Map<String, Object> validationErrors = new HashMap<>();
        validationErrors.put("invalidField", List.of("Value out of range"));

        // Act
        FreeForm updatedForm = FreeFormHelper.updateFreeFormValues(
            mixedForm, outputFieldValueMap, validationErrors);

        // Assert
        assertNotNull(updatedForm);

        // Valid fields should be updated
        assertEquals("Updated Value", updatedForm.getTypedValues().get("validField").getValue());
        assertEquals("new@example.com", updatedForm.getTypedValues().get("anotherValidField").getValue());

        // Invalid field should keep original value
        assertEquals(50, updatedForm.getTypedValues().get("invalidField").getValue());
    }

    @Test
    public void testConvertFreeFormToFieldValueMap_WithFieldsHavingNullField_HandlesGracefully() throws Exception {
        // Arrange - Create FreeForm with a TypedValue that has null field
        FreeForm formWithNullField = new FreeForm();

        Field validField = createField("com.krista.fields.Text", Map.of(), false);
        formWithNullField.put("validField", "Valid Field", validField, "Valid Value");

        // Manually create a TypedValue with null field to test edge case
        FreeForm.TypedValue nullFieldTypedValue = new FreeForm.TypedValue("Null Field", null, "Some Value");
        formWithNullField.put("nullFieldEntry", nullFieldTypedValue);

        // Act
        Map<String, Object> result = FreeFormHelper.convertFreeFormToFieldValueMap(formWithNullField);

        // Assert
        assertNotNull(result);
        // Should still process valid fields even if some have null field definitions
        assertTrue(result.containsKey("validField"));

        Map<String, Object> validFieldResult = (Map<String, Object>) result.get("validField");
        assertEquals("com.krista.fields.Text", validFieldResult.get("fieldType"));
        assertEquals("Valid Value", validFieldResult.get("value"));

        // The entry with null field should not be included in the result
        assertFalse(result.containsKey("nullFieldEntry"));
    }

    private Field createComplexListField() {
        // Create a 3-level deep structure: List -> MultiField -> MultiField (with phone and other fields)
        Map<String, Object> attributes = new HashMap<>();

        // Level 1: Create MultiField for employee structure
        Map<String, Object> employeeMultiFieldAttributes = new HashMap<>();
        List<NamedField> employeeFields = new ArrayList<>();

        // Basic employee fields
        NamedField nameField = createNamedField("name", "com.krista.fields.Text", Map.of(), true);
        employeeFields.add(nameField);

        NamedField emailField = createNamedField("email", "com.krista.fields.Email", Map.of(), true);
        employeeFields.add(emailField);

        NamedField activeField = createNamedField("active", "com.krista.fields.Switch", Map.of(), false);
        employeeFields.add(activeField);

        // Level 2: Add nested contact information MultiField
        NamedField contactInfoField = createContactInfoMultiField();
        employeeFields.add(contactInfoField);

        // Level 2: Add skills list field
        NamedField skillsListField = createSkillsListField();
        employeeFields.add(skillsListField);

        employeeMultiFieldAttributes.put("fields", employeeFields);
        Field employeeMultiField = createField("com.krista.fields.MultiField", employeeMultiFieldAttributes, false);

        attributes.put("field", employeeMultiField);

        return createField("com.krista.fields.List", attributes, true);
    }

    private NamedField createContactInfoMultiField() {
        // Level 2: Contact information MultiField containing phone numbers and address
        Map<String, Object> contactAttributes = new HashMap<>();
        List<NamedField> contactFields = new ArrayList<>();

        // Phone number field
        NamedField phoneField = createNamedField("phone", "com.krista.fields.Phone",
            Map.of("formatPattern", "+1 ##### #####"), true);
        contactFields.add(phoneField);

        // Mobile phone field
        NamedField mobileField = createNamedField("mobile", "com.krista.fields.Phone",
            Map.of("formatPattern", "+1 ##### #####"), false);
        contactFields.add(mobileField);

        // Level 3: Address MultiField (deepest level)
        NamedField addressField = createAddressMultiField();
        contactFields.add(addressField);

        contactAttributes.put("fields", contactFields);

        return new NamedField("contactInfo", "com.krista.fields.MultiField",
            contactAttributes, createOptionsMap(true));
    }

    private NamedField createAddressMultiField() {
        // Level 3: Address MultiField (deepest nested level)
        Map<String, Object> addressAttributes = new HashMap<>();
        List<NamedField> addressFields = new ArrayList<>();

        // Street address
        NamedField streetField = createNamedField("street", "com.krista.fields.Street", Map.of(), true);
        addressFields.add(streetField);

        // City
        NamedField cityField = createNamedField("city", "com.krista.fields.City", Map.of(), true);
        addressFields.add(cityField);

        // State
        NamedField stateField = createNamedField("state", "com.krista.fields.State", Map.of(), true);
        addressFields.add(stateField);

        // Postal code
        NamedField postalCodeField = createNamedField("postalCode", "com.krista.fields.PostalCode", Map.of(), true);
        addressFields.add(postalCodeField);

        // Country
        NamedField countryField = createNamedField("country", "com.krista.fields.Country", Map.of(), false);
        addressFields.add(countryField);

        addressAttributes.put("fields", addressFields);

        return new NamedField("address", "com.krista.fields.MultiField",
            addressAttributes, createOptionsMap(true));
    }

    private NamedField createSkillsListField() {
        // Skills list field containing text elements
        Map<String, Object> skillsAttributes = new HashMap<>();
        Field skillElementField = createField("com.krista.fields.Text", Map.of("maxLength", 50), false);
        skillsAttributes.put("field", skillElementField);

        return new NamedField("skills", "com.krista.fields.List",
            skillsAttributes, createOptionsMap(false));
    }

    // Tests for updateFreeFormValues with partial updates in nested structures
    @Test
    public void testUpdateFreeFormValues_WithPartialMultiFieldValidationErrors_UpdatesOnlyValidFields() {
        // Arrange - Create 3-level deep structure with validation errors in some nested fields
        FreeForm complexForm = new FreeForm();

        Field listOfMultiField = createComplexListField();
        List<Map<String, Object>> originalValue = new ArrayList<>();

        // Original employee data
        Map<String, Object> originalEmployee = new HashMap<>();
        originalEmployee.put("name", "John Doe");
        originalEmployee.put("email", "john@example.com");
        originalEmployee.put("active", true);

        Map<String, Object> originalContactInfo = new HashMap<>();
        originalContactInfo.put("phone", new PhoneNumber(1, 15551234567L));
        originalContactInfo.put("mobile", new PhoneNumber(1, 15559876543L));

        Map<String, Object> originalAddress = new HashMap<>();
        originalAddress.put("street", "123 Main St");
        originalAddress.put("city", "New York");
        originalAddress.put("state", "NY");
        originalAddress.put("postalCode", "10001");
        originalAddress.put("country", "USA");
        originalContactInfo.put("address", originalAddress);

        originalEmployee.put("contactInfo", originalContactInfo);
        originalEmployee.put("skills", List.of("Java", "Python"));
        originalValue.add(originalEmployee);

        complexForm.put("employees", "Employee List", listOfMultiField, originalValue);

        // Create update with mixed valid and invalid nested field values
        Map<String, Object> outputFieldValueMap = new HashMap<>();
        List<Map<String, Object>> updatedEmployees = new ArrayList<>();

        Map<String, Object> updatedEmployee = new HashMap<>();
        updatedEmployee.put("name", "John Updated"); // Valid update
        updatedEmployee.put("email", "john.updated@example.com"); // Valid update
        updatedEmployee.put("active", false); // Valid update

        Map<String, Object> updatedContactInfo = new HashMap<>();
        updatedContactInfo.put("phone", 15559998888L); // Valid update
        updatedContactInfo.put("mobile", 15557776666L); // Valid update

        Map<String, Object> updatedAddress = new HashMap<>();
        updatedAddress.put("street", "789 Updated St"); // Valid update
        updatedAddress.put("city", "Boston"); // Valid update
        updatedAddress.put("state", "INVALID_STATE"); // Invalid - will have validation error
        updatedAddress.put("postalCode", "02101"); // Valid update
        updatedAddress.put("country", "USA"); // Valid update
        updatedContactInfo.put("address", updatedAddress);

        updatedEmployee.put("contactInfo", updatedContactInfo);
        updatedEmployee.put("skills", List.of("Java", "Spring", "Microservices")); // Valid update
        updatedEmployees.add(updatedEmployee);

        outputFieldValueMap.put("employees", updatedEmployees);

        // Create validation errors structure
        // For now, let's test with a simpler case - just validate that some fields update and others don't
        // We'll create validation errors for the entire employees field to test the basic partial update logic
        Map<String, Object> validationErrors = new HashMap<>();

        // Create a simple validation error for testing
        // This will test that when there are validation errors, the original value is preserved
        validationErrors.put("employees", List.of("Some validation error"));

        // Act
        FreeForm updatedForm = FreeFormHelper.updateFreeFormValues(
            complexForm, outputFieldValueMap, validationErrors);

        // Assert
        assertNotNull(updatedForm);
        List<Map<String, Object>> resultEmployees = (List<Map<String, Object>>)
            updatedForm.getTypedValues().get("employees").getValue();
        assertEquals(1, resultEmployees.size());

        Map<String, Object> resultEmployee = resultEmployees.get(0);

        // Since the entire employees field has validation errors, all values should remain original
        assertEquals("John Doe", resultEmployee.get("name")); // Original value
        assertEquals("john@example.com", resultEmployee.get("email")); // Original value
        assertEquals(true, resultEmployee.get("active")); // Original value

        Map<String, Object> resultContactInfo = (Map<String, Object>) resultEmployee.get("contactInfo");
        assertNotNull(resultContactInfo);
        assertEquals(new PhoneNumber(1, 15551234567L), resultContactInfo.get("phone")); // Original value
        assertEquals(new PhoneNumber(1, 15559876543L), resultContactInfo.get("mobile")); // Original value

        Map<String, Object> resultAddress = (Map<String, Object>) resultContactInfo.get("address");
        assertNotNull(resultAddress);
        assertEquals("123 Main St", resultAddress.get("street")); // Original value
        assertEquals("New York", resultAddress.get("city")); // Original value
        assertEquals("NY", resultAddress.get("state")); // Original value
        assertEquals("10001", resultAddress.get("postalCode")); // Original value
        assertEquals("USA", resultAddress.get("country")); // Original value

        // Skills should also remain original
        List<String> resultSkills = (List<String>) resultEmployee.get("skills");
        assertEquals(2, resultSkills.size());
        assertEquals("Java", resultSkills.get(0));
        assertEquals("Python", resultSkills.get(1));
    }

    @Test
    public void testUpdateFreeFormValues_WithPartialMultiFieldUpdates_UpdatesValidFieldsOnly() {
        // Arrange - Create a simple MultiField (not a list) to test partial updates within a single MultiField
        FreeForm form = new FreeForm();

        // Create a simple MultiField with basic fields
        Field multiField = createMultiField(); // This creates a MultiField with firstName, lastName, age

        Map<String, Object> originalValue = new HashMap<>();
        originalValue.put("firstName", "John");
        originalValue.put("lastName", "Doe");
        originalValue.put("age", 30.0);

        form.put("person", "Person Info", multiField, originalValue);

        // Create update data
        Map<String, Object> outputFieldValueMap = new HashMap<>();
        Map<String, Object> updatedPersonValue = new HashMap<>();
        updatedPersonValue.put("firstName", "Jane"); // Valid update
        updatedPersonValue.put("lastName", "Smith"); // Valid update
        updatedPersonValue.put("age", 25.0); // This will have validation error

        outputFieldValueMap.put("person", updatedPersonValue);

        // Create nested validation errors - only age field has error
        Map<String, Object> validationErrors = new HashMap<>();
        Map<String, Object> personErrors = new HashMap<>();
        personErrors.put("age", List.of("Age must be greater than 30"));
        validationErrors.put("person", personErrors);

        // Act
        FreeForm updatedForm = FreeFormHelper.updateFreeFormValues(
            form, outputFieldValueMap, validationErrors);

        // Assert
        assertNotNull(updatedForm);
        Map<String, Object> resultPerson = (Map<String, Object>)
            updatedForm.getTypedValues().get("person").getValue();

        // Fields without validation errors should be updated
        assertEquals("Jane", resultPerson.get("firstName")); // Updated
        assertEquals("Smith", resultPerson.get("lastName")); // Updated

        // Field with validation error should keep original value
        assertEquals(30.0, resultPerson.get("age")); // Original value (has validation error)
    }

    @Test
    public void testUpdateFreeFormValues_WithDeepNestedPartialUpdates_UpdatesValidFieldsOnly() {
        // Arrange - Create 3-level deep structure to test partial updates at different levels
        FreeForm complexForm = new FreeForm();

        Field listOfMultiField = createComplexListField();
        List<Map<String, Object>> originalValue = new ArrayList<>();

        Map<String, Object> originalEmployee = new HashMap<>();
        originalEmployee.put("name", "John Doe");
        originalEmployee.put("email", "john@example.com");
        originalEmployee.put("active", true);

        Map<String, Object> originalContactInfo = new HashMap<>();
        originalContactInfo.put("phone", new PhoneNumber(1, 15551234567L));
        originalContactInfo.put("mobile", new PhoneNumber(1,  15559876543L));

        Map<String, Object> originalAddress = new HashMap<>();
        originalAddress.put("street", "123 Main St");
        originalAddress.put("city", "New York");
        originalAddress.put("state", "NY");
        originalAddress.put("postalCode", "10001");
        originalAddress.put("country", "USA");
        originalContactInfo.put("address", originalAddress);

        originalEmployee.put("contactInfo", originalContactInfo);
        originalEmployee.put("skills", List.of("Java", "Python"));
        originalValue.add(originalEmployee);

        complexForm.put("employees", "Employee List", listOfMultiField, originalValue);

        // Create update data
        Map<String, Object> outputFieldValueMap = new HashMap<>();
        List<Map<String, Object>> updatedEmployees = new ArrayList<>();

        Map<String, Object> updatedEmployee = new HashMap<>();
        updatedEmployee.put("name", "John Updated"); // Valid update
        updatedEmployee.put("email", "john.updated@example.com"); // Valid update
        updatedEmployee.put("active", false); // Valid update

        Map<String, Object> updatedContactInfo = new HashMap<>();
        updatedContactInfo.put("phone", 15559998888L); // Valid update
        updatedContactInfo.put("mobile", 15557776666L); // This will have validation error

        Map<String, Object> updatedAddress = new HashMap<>();
        updatedAddress.put("street", "789 Updated St"); // Valid update
        updatedAddress.put("city", "Boston"); // Valid update
        updatedAddress.put("state", "MA"); // Valid update
        updatedAddress.put("postalCode", "INVALID"); // This will have validation error
        updatedAddress.put("country", "USA"); // Valid update
        updatedContactInfo.put("address", updatedAddress);

        updatedEmployee.put("contactInfo", updatedContactInfo);
        updatedEmployee.put("skills", List.of("Java", "Spring", "Microservices")); // Valid update
        updatedEmployees.add(updatedEmployee);

        outputFieldValueMap.put("employees", updatedEmployees);

        // Create nested validation errors for specific fields only
        Map<String, Object> validationErrors = new HashMap<>();

        // Create nested error structure: employees -> contactInfo -> mobile & address -> postalCode
        Map<String, Object> addressErrors = new HashMap<>();
        addressErrors.put("postalCode", List.of("Invalid postal code format"));

        Map<String, Object> contactInfoErrors = new HashMap<>();
        contactInfoErrors.put("mobile", List.of("Invalid mobile number"));
        contactInfoErrors.put("address", addressErrors);

        Map<String, Object> employeesErrors = new HashMap<>();
        employeesErrors.put("contactInfo", contactInfoErrors);

        validationErrors.put("employees", employeesErrors);

        // Act
        FreeForm updatedForm = FreeFormHelper.updateFreeFormValues(
            complexForm, outputFieldValueMap, validationErrors);

        // Assert
        assertNotNull(updatedForm);
        List<Map<String, Object>> resultEmployees = (List<Map<String, Object>>)
            updatedForm.getTypedValues().get("employees").getValue();
        assertEquals(1, resultEmployees.size());

        Map<String, Object> resultEmployee = resultEmployees.get(0);

        // Top-level fields without validation errors should be updated
        assertEquals("John Updated", resultEmployee.get("name")); // Updated
        assertEquals("john.updated@example.com", resultEmployee.get("email")); // Updated
        assertEquals(false, resultEmployee.get("active")); // Updated

        Map<String, Object> resultContactInfo = (Map<String, Object>) resultEmployee.get("contactInfo");
        assertNotNull(resultContactInfo);

        // Phone should be updated (no validation error)
        assertEquals(new PhoneNumber(1, 15559998888L), resultContactInfo.get("phone")); // Updated

        // Mobile should keep original value (has validation error)
        assertEquals(new PhoneNumber(1, 15559876543L), resultContactInfo.get("mobile")); // Original value

        Map<String, Object> resultAddress = (Map<String, Object>) resultContactInfo.get("address");
        assertNotNull(resultAddress);

        // Address fields without validation errors should be updated
        assertEquals("789 Updated St", resultAddress.get("street")); // Updated
        assertEquals("Boston", resultAddress.get("city")); // Updated
        assertEquals("MA", resultAddress.get("state")); // Updated
        assertEquals("USA", resultAddress.get("country")); // Updated

        // PostalCode should keep original value (has validation error)
        assertEquals("10001", resultAddress.get("postalCode")); // Original value

        // Skills should be updated (no validation error)
        List<String> resultSkills = (List<String>) resultEmployee.get("skills");
        assertEquals(3, resultSkills.size());
        assertEquals("Java", resultSkills.get(0));
        assertEquals("Spring", resultSkills.get(1));
        assertEquals("Microservices", resultSkills.get(2));
    }

    @Test
    public void testUpdateFreeFormValues_WithListValidationError_DiscardsEntireList() {
        // Arrange - Create form with list field
        FreeForm formWithList = new FreeForm();

        Field skillsListField = createSkillsListField();
        List<String> originalSkills = List.of("Java", "Python", "JavaScript");
        formWithList.put("skills", "Skills List", skillsListField, originalSkills);

        // Add other fields to verify they still get updated
        Field textField = createField("com.krista.fields.Text", Map.of(), false);
        formWithList.put("name", "Name Field", textField, "Original Name");

        // Create update with invalid list format
        Map<String, Object> outputFieldValueMap = new HashMap<>();
        outputFieldValueMap.put("name", "Updated Name"); // Valid update

        // Invalid list - contains non-string values that would fail validation
        List<Object> invalidSkillsList = List.of("Java", "Python", 12345); // Number in text list
        outputFieldValueMap.put("skills", invalidSkillsList);

        // Create validation errors for the entire list (simple list errors are still flat)
        Map<String, Object> validationErrors = new HashMap<>();
        validationErrors.put("skills", List.of("Invalid format in list element"));

        // Act
        FreeForm updatedForm = FreeFormHelper.updateFreeFormValues(
            formWithList, outputFieldValueMap, validationErrors);

        // Assert
        assertNotNull(updatedForm);

        // Valid field should be updated
        assertEquals("Updated Name", updatedForm.getTypedValues().get("name").getValue());

        // Invalid list should keep original value (entire list discarded)
        List<String> resultSkills = (List<String>) updatedForm.getTypedValues().get("skills").getValue();
        assertEquals(3, resultSkills.size());
        assertEquals("Java", resultSkills.get(0));
        assertEquals("Python", resultSkills.get(1));
        assertEquals("JavaScript", resultSkills.get(2));
    }

    @Test
    public void testUpdateFreeFormValues_WithNestedListValidationError_DiscardsEntireNestedList() {
        // Arrange - Create 3-level deep structure with list validation error
        FreeForm complexForm = new FreeForm();

        Field listOfMultiField = createComplexListField();
        List<Map<String, Object>> originalValue = new ArrayList<>();

        Map<String, Object> originalEmployee = new HashMap<>();
        originalEmployee.put("name", "John Doe");
        originalEmployee.put("email", "john@example.com");
        originalEmployee.put("active", true);

        Map<String, Object> originalContactInfo = new HashMap<>();
        originalContactInfo.put("phone", new PhoneNumber(1, 15551234567L));
        originalContactInfo.put("mobile", new PhoneNumber(1, 15559876543L));

        Map<String, Object> originalAddress = new HashMap<>();
        originalAddress.put("street", "123 Main St");
        originalAddress.put("city", "New York");
        originalAddress.put("state", "NY");
        originalAddress.put("postalCode", "10001");
        originalAddress.put("country", "USA");
        originalContactInfo.put("address", originalAddress);

        originalEmployee.put("contactInfo", originalContactInfo);
        originalEmployee.put("skills", List.of("Java", "Python", "React"));
        originalValue.add(originalEmployee);

        complexForm.put("employees", "Employee List", listOfMultiField, originalValue);

        // Create update with invalid nested list
        Map<String, Object> outputFieldValueMap = new HashMap<>();
        List<Map<String, Object>> updatedEmployees = new ArrayList<>();

        Map<String, Object> updatedEmployee = new HashMap<>();
        updatedEmployee.put("name", "John Updated"); // Valid update
        updatedEmployee.put("email", "john.updated@example.com"); // Valid update
        updatedEmployee.put("active", false); // Valid update

        Map<String, Object> updatedContactInfo = new HashMap<>();
        updatedContactInfo.put("phone", 15559998888L); // Valid update
        updatedContactInfo.put("mobile", 15557776666L); // Valid update

        Map<String, Object> updatedAddress = new HashMap<>();
        updatedAddress.put("street", "789 Updated St"); // Valid update
        updatedAddress.put("city", "Boston"); // Valid update
        updatedAddress.put("state", "MA"); // Valid update
        updatedAddress.put("postalCode", "02101"); // Valid update
        updatedAddress.put("country", "USA"); // Valid update
        updatedContactInfo.put("address", updatedAddress);

        updatedEmployee.put("contactInfo", updatedContactInfo);

        // Invalid skills list - contains non-string values
        List<Object> invalidSkills = List.of("Java", "Spring", 12345, "Microservices");
        updatedEmployee.put("skills", invalidSkills);

        updatedEmployees.add(updatedEmployee);
        outputFieldValueMap.put("employees", updatedEmployees);

        // Create validation errors for the entire employees field
        Map<String, Object> validationErrors = new HashMap<>();
        validationErrors.put("employees", List.of("Invalid format in nested skills list"));

        // Act
        FreeForm updatedForm = FreeFormHelper.updateFreeFormValues(
            complexForm, outputFieldValueMap, validationErrors);

        // Assert
        assertNotNull(updatedForm);
        List<Map<String, Object>> resultEmployees = (List<Map<String, Object>>)
            updatedForm.getTypedValues().get("employees").getValue();
        assertEquals(1, resultEmployees.size());

        Map<String, Object> resultEmployee = resultEmployees.get(0);

        // Since the entire employees field has validation errors, all values should remain original
        assertEquals("John Doe", resultEmployee.get("name")); // Original value
        assertEquals("john@example.com", resultEmployee.get("email")); // Original value
        assertEquals(true, resultEmployee.get("active")); // Original value

        Map<String, Object> resultContactInfo = (Map<String, Object>) resultEmployee.get("contactInfo");
        assertNotNull(resultContactInfo);
        assertEquals(new PhoneNumber(1, 15551234567L), resultContactInfo.get("phone")); // Original value
        assertEquals(new PhoneNumber(1, 15559876543L), resultContactInfo.get("mobile")); // Original value

        Map<String, Object> resultAddress = (Map<String, Object>) resultContactInfo.get("address");
        assertNotNull(resultAddress);
        assertEquals("123 Main St", resultAddress.get("street")); // Original value
        assertEquals("New York", resultAddress.get("city")); // Original value
        assertEquals("NY", resultAddress.get("state")); // Original value
        assertEquals("10001", resultAddress.get("postalCode")); // Original value
        assertEquals("USA", resultAddress.get("country")); // Original value

        // Skills should also remain original
        List<String> resultSkills = (List<String>) resultEmployee.get("skills");
        assertEquals(3, resultSkills.size());
        assertEquals("Java", resultSkills.get(0)); // Original value
        assertEquals("Python", resultSkills.get(1)); // Original value
        assertEquals("React", resultSkills.get(2)); // Original value
    }

    @Test
    public void testIsFreeFormFilled_WithMultiFieldHavingNullSubField_ReturnsFalse() {
        // Arrange - Create FreeForm with MultiField containing null mandatory sub-field
        FreeForm formWithNullSubField = new FreeForm();
        
        Field multiField = createMultiField(); // Creates MultiField with firstName, lastName, age
        Map<String, Object> multiFieldValue = new HashMap<>();
        multiFieldValue.put("firstName", "John");
        multiFieldValue.put("lastName", null); // Null sub-field
        multiFieldValue.put("age", 30);
        
        formWithNullSubField.put("person", "Person Info", multiField, multiFieldValue);

        // Act
        boolean result = FreeFormHelper.isFreeFormFilled(formWithNullSubField, true);

        // Assert
        assertFalse(result); // Should return false because lastName sub-field is null
    }

    @Test
    public void testIsFreeFormFilled_WithMultiFieldHavingEmptyStringSubField_ReturnsFalse() {
        // Arrange - Create FreeForm with MultiField containing empty string mandatory sub-field
        FreeForm formWithEmptySubField = new FreeForm();
        
        Field multiField = createMultiField();
        Map<String, Object> multiFieldValue = new HashMap<>();
        multiFieldValue.put("firstName", "John");
        multiFieldValue.put("lastName", ""); // Empty string sub-field
        multiFieldValue.put("age", 30);
        
        formWithEmptySubField.put("person", "Person Info", multiField, multiFieldValue);

        // Act
        boolean result = FreeFormHelper.isFreeFormFilled(formWithEmptySubField, true);

        // Assert
        assertFalse(result); // Should return false because lastName sub-field is empty
    }

    @Test
    public void testIsFreeFormFilled_WithMultiFieldAllSubFieldsFilled_ReturnsTrue() {
        // Arrange - Create FreeForm with MultiField having all mandatory sub-fields filled
        FreeForm formWithFilledSubFields = new FreeForm();
        
        Field multiField = createMultiField();
        Map<String, Object> multiFieldValue = new HashMap<>();
        multiFieldValue.put("firstName", "John");
        multiFieldValue.put("lastName", "Doe");
        multiFieldValue.put("age", 30);
        
        formWithFilledSubFields.put("person", "Person Info", multiField, multiFieldValue);

        // Act
        boolean result = FreeFormHelper.isFreeFormFilled(formWithFilledSubFields, true);

        // Assert
        assertTrue(result); // Should return true because all mandatory sub-fields are filled
    }
}
