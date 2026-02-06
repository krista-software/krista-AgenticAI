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
import app.krista.model.field.Field;
import app.krista.model.field.NamedValuedField;
import org.junit.Test;
import org.junit.Before;

import java.util.*;

import static org.junit.Assert.*;

/**
 * Test class for FreeFormHelper reference comparison methods:
 * - hasMissingValuesFromReference
 * - hasOnlyReferenceFieldValues
 */
@SuppressWarnings("unchecked")
public class FreeFormHelperReferenceComparisonTest {

    private FreeForm referenceFreeForm;
    private FreeForm updatedFreeForm;

    @Before
    public void setUp() {
        // Initialize forms for each test
        referenceFreeForm = new FreeForm();
        updatedFreeForm = new FreeForm();
    }

    private Field createField(String fieldType, Map<String, Object> attributes, boolean isMandatory) {
        Map<String, NamedValuedField> options = new HashMap<>();
        NamedValuedField requiredOption = new NamedValuedField("required", "Boolean", isMandatory, Map.of(), Map.of());
        options.put("required", requiredOption);
        return new Field(fieldType, attributes, options);
    }

    // ========== Tests for hasMissingValuesFromReference method ==========

    @Test
    public void testHasMissingValuesFromReference_WithMissingMandatoryFields_ReturnsTrue() {
        // Arrange - Create reference form with mandatory fields
        Field mandatoryField1 = createField("com.krista.fields.Text", Map.of(), true);
        referenceFreeForm.put("field1", "Field 1", mandatoryField1, "Reference Value 1");
        
        Field mandatoryField2 = createField("com.krista.fields.Number", Map.of(), true);
        referenceFreeForm.put("field2", "Field 2", mandatoryField2, 100);

        // Create updated form missing one mandatory field
        updatedFreeForm.put("field1", "Field 1", mandatoryField1, "Updated Value 1");
        // field2 is missing

        // Act
        boolean result = FreeFormDefaultsHelper.hasMissingValuesFromReference(updatedFreeForm, referenceFreeForm, true);

        // Assert
        assertTrue(result); // Should return true because field2 is missing
    }

    @Test
    public void testHasMissingValuesFromReference_WithAllFieldsPresent_ReturnsFalse() {
        // Arrange - Create reference form with mandatory fields
        Field mandatoryField1 = createField("com.krista.fields.Text", Map.of(), true);
        referenceFreeForm.put("field1", "Field 1", mandatoryField1, "Reference Value 1");
        
        Field mandatoryField2 = createField("com.krista.fields.Number", Map.of(), true);
        referenceFreeForm.put("field2", "Field 2", mandatoryField2, 100);

        // Create updated form with all fields present
        updatedFreeForm.put("field1", "Field 1", mandatoryField1, "Updated Value 1");
        updatedFreeForm.put("field2", "Field 2", mandatoryField2, 200);

        // Act
        boolean result = FreeFormDefaultsHelper.hasMissingValuesFromReference(updatedFreeForm, referenceFreeForm, true);

        // Assert
        assertFalse(result); // Should return false because all fields are present
    }

    @Test
    public void testHasMissingValuesFromReference_WithNullValues_ReturnsTrue() {
        // Arrange - Create reference form with mandatory fields
        Field mandatoryField1 = createField("com.krista.fields.Text", Map.of(), true);
        referenceFreeForm.put("field1", "Field 1", mandatoryField1, "Reference Value 1");

        // Create updated form with null value
        updatedFreeForm.put("field1", "Field 1", mandatoryField1, null);

        // Act
        boolean result = FreeFormDefaultsHelper.hasMissingValuesFromReference(updatedFreeForm, referenceFreeForm, true);

        // Assert
        assertTrue(result); // Should return true because field1 has null value
    }

    @Test
    public void testHasMissingValuesFromReference_WithEmptyValues_ReturnsTrue() {
        // Arrange - Create reference form with mandatory fields
        Field mandatoryField1 = createField("com.krista.fields.Text", Map.of(), true);
        referenceFreeForm.put("field1", "Field 1", mandatoryField1, "Reference Value 1");

        // Create updated form with empty string value
        updatedFreeForm.put("field1", "Field 1", mandatoryField1, "");

        // Act
        boolean result = FreeFormDefaultsHelper.hasMissingValuesFromReference(updatedFreeForm, referenceFreeForm, true);

        // Assert
        assertTrue(result); // Should return true because field1 has empty value
    }

    @Test
    public void testHasMissingValuesFromReference_WithOptionalFieldsOnly_ReturnsFalse() {
        // Arrange - Create reference form with optional fields only
        Field optionalField1 = createField("com.krista.fields.Text", Map.of(), false);
        referenceFreeForm.put("field1", "Field 1", optionalField1, "Reference Value 1");

        // Create updated form missing the optional field
        // field1 is missing but it's optional

        // Act - checking only mandatory fields
        boolean result = FreeFormDefaultsHelper.hasMissingValuesFromReference(updatedFreeForm, referenceFreeForm, true);

        // Assert
        assertFalse(result); // Should return false because we're only checking mandatory fields
    }

    @Test
    public void testHasMissingValuesFromReference_WithOptionalFieldsIncluded_ReturnsTrue() {
        // Arrange - Create reference form with optional fields
        Field optionalField1 = createField("com.krista.fields.Text", Map.of(), false);
        referenceFreeForm.put("field1", "Field 1", optionalField1, "Reference Value 1");

        // Create updated form missing the optional field
        // field1 is missing

        // Act - checking all fields (including optional)
        boolean result = FreeFormDefaultsHelper.hasMissingValuesFromReference(updatedFreeForm, referenceFreeForm, false);

        // Assert
        assertTrue(result); // Should return true because field1 is missing and we're checking all fields
    }

    @Test
    public void testHasMissingValuesFromReference_WithEmptyReferenceFreeForm_ReturnsFalse() {
        // Arrange - Create empty reference form
        // referenceFreeForm is already empty

        // Create updated form with some fields
        Field field1 = createField("com.krista.fields.Text", Map.of(), true);
        updatedFreeForm.put("field1", "Field 1", field1, "Value 1");

        // Act
        boolean result = FreeFormDefaultsHelper.hasMissingValuesFromReference(updatedFreeForm, referenceFreeForm, true);

        // Assert
        assertFalse(result); // Should return false because reference form is empty
    }

    @Test
    public void testHasMissingValuesFromReference_WithEmptyCollectionValues_ReturnsTrue() {
        // Arrange - Create reference form with mandatory collection field
        Field mandatoryListField = createField("com.krista.fields.Checkboxes", Map.of(), true);
        referenceFreeForm.put("skills", "Skills Field", mandatoryListField, List.of("Java", "Python"));

        // Create updated form with empty collection
        updatedFreeForm.put("skills", "Skills Field", mandatoryListField, new ArrayList<>());

        // Act
        boolean result = FreeFormDefaultsHelper.hasMissingValuesFromReference(updatedFreeForm, referenceFreeForm, true);

        // Assert
        assertTrue(result); // Should return true because skills field has empty collection
    }

    @Test
    public void testHasMissingValuesFromReference_WithWhitespaceValues_ReturnsTrue() {
        // Arrange - Create reference form with mandatory text field
        Field mandatoryTextField = createField("com.krista.fields.Text", Map.of(), true);
        referenceFreeForm.put("name", "Name Field", mandatoryTextField, "John Doe");

        // Create updated form with whitespace-only value
        updatedFreeForm.put("name", "Name Field", mandatoryTextField, "   ");

        // Act
        boolean result = FreeFormDefaultsHelper.hasMissingValuesFromReference(updatedFreeForm, referenceFreeForm, true);

        // Assert
        assertTrue(result); // Should return true because name field has whitespace-only value
    }

    // ========== Tests for hasOnlyReferenceFieldValues method ==========

    @Test
    public void testHasOnlyReferenceFieldValues_WithOnlyReferenceFields_ReturnsTrue() {
        // Arrange - Create reference form with mandatory fields
        Field mandatoryField1 = createField("com.krista.fields.Text", Map.of(), true);
        referenceFreeForm.put("field1", "Field 1", mandatoryField1, "Reference Value 1");

        Field mandatoryField2 = createField("com.krista.fields.Number", Map.of(), true);
        referenceFreeForm.put("field2", "Field 2", mandatoryField2, 100);

        // Create updated form with only reference fields
        updatedFreeForm.put("field1", "Field 1", mandatoryField1, "Updated Value 1");
        updatedFreeForm.put("field2", "Field 2", mandatoryField2, 200);

        // Act
        boolean result = FreeFormDefaultsHelper.hasOnlyReferenceFieldValues(updatedFreeForm, referenceFreeForm, true);

        // Assert
        assertTrue(result); // Should return true because updated form only has reference fields
    }

    @Test
    public void testHasOnlyReferenceFieldValues_WithExtraFields_ReturnsFalse() {
        // Arrange - Create reference form with mandatory fields
        Field mandatoryField1 = createField("com.krista.fields.Text", Map.of(), true);
        referenceFreeForm.put("field1", "Field 1", mandatoryField1, "Reference Value 1");

        // Create updated form with extra field
        updatedFreeForm.put("field1", "Field 1", mandatoryField1, "Updated Value 1");

        Field extraField = createField("com.krista.fields.Text", Map.of(), true);
        updatedFreeForm.put("extraField", "Extra Field", extraField, "Extra Value");

        // Act
        boolean result = FreeFormDefaultsHelper.hasOnlyReferenceFieldValues(updatedFreeForm, referenceFreeForm, true);

        // Assert
        assertFalse(result); // Should return false because updated form has extra field
    }

    @Test
    public void testHasOnlyReferenceFieldValues_WithNullValues_ReturnsTrue() {
        // Arrange - Create reference form with mandatory fields
        Field mandatoryField1 = createField("com.krista.fields.Text", Map.of(), true);
        referenceFreeForm.put("field1", "Field 1", mandatoryField1, "Reference Value 1");

        // Create updated form with null values (should be ignored)
        updatedFreeForm.put("field1", "Field 1", mandatoryField1, null);

        Field extraField = createField("com.krista.fields.Text", Map.of(), true);
        updatedFreeForm.put("extraField", "Extra Field", extraField, null); // null value should be ignored

        // Act
        boolean result = FreeFormDefaultsHelper.hasOnlyReferenceFieldValues(updatedFreeForm, referenceFreeForm, true);

        // Assert
        assertTrue(result); // Should return true because null values are ignored
    }

    @Test
    public void testHasOnlyReferenceFieldValues_WithEmptyValues_ReturnsTrue() {
        // Arrange - Create reference form with mandatory fields
        Field mandatoryField1 = createField("com.krista.fields.Text", Map.of(), true);
        referenceFreeForm.put("field1", "Field 1", mandatoryField1, "Reference Value 1");

        // Create updated form with empty values (should be ignored)
        updatedFreeForm.put("field1", "Field 1", mandatoryField1, "Valid Value");

        Field extraField = createField("com.krista.fields.Text", Map.of(), true);
        updatedFreeForm.put("extraField", "Extra Field", extraField, ""); // empty value should be ignored

        // Act
        boolean result = FreeFormDefaultsHelper.hasOnlyReferenceFieldValues(updatedFreeForm, referenceFreeForm, true);

        // Assert
        assertTrue(result); // Should return true because empty values are ignored
    }

    @Test
    public void testHasOnlyReferenceFieldValues_WithOptionalFieldsOnly_ReturnsTrue() {
        // Arrange - Create reference form with optional fields
        Field optionalField1 = createField("com.krista.fields.Text", Map.of(), false);
        referenceFreeForm.put("field1", "Field 1", optionalField1, "Reference Value 1");

        // Create updated form with extra optional field
        updatedFreeForm.put("field1", "Field 1", optionalField1, "Updated Value 1");

        Field extraOptionalField = createField("com.krista.fields.Text", Map.of(), false);
        updatedFreeForm.put("extraField", "Extra Field", extraOptionalField, "Extra Value");

        // Act - checking only mandatory fields
        boolean result = FreeFormDefaultsHelper.hasOnlyReferenceFieldValues(updatedFreeForm, referenceFreeForm, true);

        // Assert
        assertTrue(result); // Should return true because we're only checking mandatory fields
    }

    @Test
    public void testHasOnlyReferenceFieldValues_WithOptionalFieldsIncluded_ReturnsFalse() {
        // Arrange - Create reference form with optional fields
        Field optionalField1 = createField("com.krista.fields.Text", Map.of(), false);
        referenceFreeForm.put("field1", "Field 1", optionalField1, "Reference Value 1");

        // Create updated form with extra optional field
        updatedFreeForm.put("field1", "Field 1", optionalField1, "Updated Value 1");

        Field extraOptionalField = createField("com.krista.fields.Text", Map.of(), false);
        updatedFreeForm.put("extraField", "Extra Field", extraOptionalField, "Extra Value");

        // Act - checking all fields (including optional)
        boolean result = FreeFormDefaultsHelper.hasOnlyReferenceFieldValues(updatedFreeForm, referenceFreeForm, false);

        // Assert
        assertFalse(result); // Should return false because extraField is not in reference form
    }

    @Test
    public void testHasOnlyReferenceFieldValues_WithEmptyUpdatedFreeForm_ReturnsTrue() {
        // Arrange - Create reference form with fields
        Field field1 = createField("com.krista.fields.Text", Map.of(), true);
        referenceFreeForm.put("field1", "Field 1", field1, "Reference Value 1");

        // Create empty updated form
        // updatedFreeForm is already empty

        // Act
        boolean result = FreeFormDefaultsHelper.hasOnlyReferenceFieldValues(updatedFreeForm, referenceFreeForm, true);

        // Assert
        assertTrue(result); // Should return true because empty form has no extra fields
    }

    @Test
    public void testHasOnlyReferenceFieldValues_WithEmptyReferenceFreeForm_ReturnsFalse() {
        // Arrange - Create empty reference form
        // referenceFreeForm is already empty

        // Create updated form with some fields
        Field field1 = createField("com.krista.fields.Text", Map.of(), true);
        updatedFreeForm.put("field1", "Field 1", field1, "Value 1");

        // Act
        boolean result = FreeFormDefaultsHelper.hasOnlyReferenceFieldValues(updatedFreeForm, referenceFreeForm, true);

        // Assert
        assertFalse(result); // Should return false because updated form has fields not in empty reference
    }

    @Test
    public void testHasOnlyReferenceFieldValues_WithWhitespaceValues_ReturnsTrue() {
        // Arrange - Create reference form with mandatory field
        Field mandatoryField1 = createField("com.krista.fields.Text", Map.of(), true);
        referenceFreeForm.put("field1", "Field 1", mandatoryField1, "Reference Value 1");

        // Create updated form with whitespace values (should be ignored)
        updatedFreeForm.put("field1", "Field 1", mandatoryField1, "Valid Value");

        Field extraField = createField("com.krista.fields.Text", Map.of(), true);
        updatedFreeForm.put("extraField", "Extra Field", extraField, "   "); // whitespace value should be ignored

        // Act
        boolean result = FreeFormDefaultsHelper.hasOnlyReferenceFieldValues(updatedFreeForm, referenceFreeForm, true);

        // Assert
        assertTrue(result); // Should return true because whitespace values are ignored
    }

    @Test
    public void testHasOnlyReferenceFieldValues_WithEmptyCollectionValues_ReturnsTrue() {
        // Arrange - Create reference form with mandatory field
        Field mandatoryField1 = createField("com.krista.fields.Text", Map.of(), true);
        referenceFreeForm.put("field1", "Field 1", mandatoryField1, "Reference Value 1");

        // Create updated form with empty collection values (should be ignored)
        updatedFreeForm.put("field1", "Field 1", mandatoryField1, "Valid Value");

        Field extraField = createField("com.krista.fields.Checkboxes", Map.of(), true);
        updatedFreeForm.put("extraField", "Extra Field", extraField, new ArrayList<>()); // empty collection should be ignored

        // Act
        boolean result = FreeFormDefaultsHelper.hasOnlyReferenceFieldValues(updatedFreeForm, referenceFreeForm, true);

        // Assert
        assertTrue(result); // Should return true because empty collection values are ignored
    }

    @Test
    public void testHasOnlyReferenceFieldValues_WithMixedMandatoryAndOptionalFields_ReturnsCorrectResult() {
        // Arrange - Create reference form with mixed field types
        Field mandatoryField1 = createField("com.krista.fields.Text", Map.of(), true);
        referenceFreeForm.put("mandatoryField", "Mandatory Field", mandatoryField1, "Reference Value 1");

        Field optionalField1 = createField("com.krista.fields.Text", Map.of(), false);
        referenceFreeForm.put("optionalField", "Optional Field", optionalField1, "Reference Value 2");

        // Create updated form with extra mandatory field not in reference
        updatedFreeForm.put("mandatoryField", "Mandatory Field", mandatoryField1, "Updated Value 1");
        updatedFreeForm.put("optionalField", "Optional Field", optionalField1, "Updated Value 2");

        Field extraMandatoryField = createField("com.krista.fields.Text", Map.of(), true);
        updatedFreeForm.put("extraMandatory", "Extra Mandatory Field", extraMandatoryField, "Extra Value");

        // Act - checking only mandatory fields
        boolean result = FreeFormDefaultsHelper.hasOnlyReferenceFieldValues(updatedFreeForm, referenceFreeForm, true);

        // Assert
        assertFalse(result); // Should return false because extraMandatory field is not in reference form
    }

    // ========== Tests for MultiField scenarios ==========

    @Test
    public void testHasMissingValuesFromReference_WithMultiFieldMissing_ReturnsTrue() {
        // Arrange - Create reference form with MultiField
        Field multiField = createField("com.krista.fields.MultiField", Map.of(), true);
        Map<String, Object> referenceMultiValue = new HashMap<>();
        referenceMultiValue.put("name", "John Doe");
        referenceMultiValue.put("age", 30);
        referenceMultiValue.put("email", "john@example.com");
        referenceFreeForm.put("person", "Person Details", multiField, referenceMultiValue);

        // Create updated form WITHOUT the MultiField (top-level field is missing)
        // updatedFreeForm is empty - the "person" field is completely missing

        // Act
        boolean result = FreeFormDefaultsHelper.hasMissingValuesFromReference(updatedFreeForm, referenceFreeForm, true);

        // Assert
        assertTrue(result); // Should return true because the entire "person" MultiField is missing
    }

    @Test
    public void testHasMissingValuesFromReference_WithMultiFieldNestedValuesMissing_ReturnsTrue() {
        // Arrange - Create reference form with MultiField containing nested values
        Field multiField = createField("com.krista.fields.MultiField", Map.of(), true);
        Map<String, Object> referenceMultiValue = new HashMap<>();
        referenceMultiValue.put("name", "John Doe");
        referenceMultiValue.put("age", 30);
        referenceMultiValue.put("email", "john@example.com");
        referenceFreeForm.put("person", "Person Details", multiField, referenceMultiValue);

        // Create updated form with MultiField missing some nested values
        Map<String, Object> updatedMultiValue = new HashMap<>();
        updatedMultiValue.put("name", "Jane Smith");
        // age and email are missing from nested structure
        updatedFreeForm.put("person", "Person Details", multiField, updatedMultiValue);

        // Act
        boolean result = FreeFormDefaultsHelper.hasMissingValuesFromReference(updatedFreeForm, referenceFreeForm, false);

        // Assert
        assertTrue(result); // Should return true because nested values (age, email) are missing
    }

    @Test
    public void testHasMissingValuesFromReference_WithMultiFieldAllNestedValuesPresent_ReturnsFalse() {
        // Arrange - Create reference form with MultiField
        Field multiField = createField("com.krista.fields.MultiField", Map.of(), true);
        Map<String, Object> referenceMultiValue = new HashMap<>();
        referenceMultiValue.put("name", "John Doe");
        referenceMultiValue.put("age", 30);
        referenceFreeForm.put("person", "Person Details", multiField, referenceMultiValue);

        // Create updated form with all nested values present (plus extra)
        Map<String, Object> updatedMultiValue = new HashMap<>();
        updatedMultiValue.put("name", "Jane Smith");
        updatedMultiValue.put("age", 25);
        updatedMultiValue.put("email", "jane@example.com"); // Extra nested field
        updatedFreeForm.put("person", "Person Details", multiField, updatedMultiValue);

        // Act
        boolean result = FreeFormDefaultsHelper.hasMissingValuesFromReference(updatedFreeForm, referenceFreeForm, false);

        // Assert
        assertFalse(result); // Should return false because all reference nested values are present
    }

    @Test
    public void testHasMissingValuesFromReference_WithMultiFieldNull_ReturnsTrue() {
        // Arrange - Create reference form with MultiField
        Field multiField = createField("com.krista.fields.MultiField", Map.of(), true);
        Map<String, Object> referenceMultiValue = new HashMap<>();
        referenceMultiValue.put("name", "John Doe");
        referenceMultiValue.put("age", 30);
        referenceFreeForm.put("person", "Person Details", multiField, referenceMultiValue);

        // Create updated form with null MultiField value
        updatedFreeForm.put("person", "Person Details", multiField, null);

        // Act
        boolean result = FreeFormDefaultsHelper.hasMissingValuesFromReference(updatedFreeForm, referenceFreeForm, true);

        // Assert
        assertTrue(result); // Should return true because MultiField has null value
    }

    @Test
    public void testHasMissingValuesFromReference_WithMultiFieldEmpty_ReturnsTrue() {
        // Arrange - Create reference form with MultiField
        Field multiField = createField("com.krista.fields.MultiField", Map.of(), true);
        Map<String, Object> referenceMultiValue = new HashMap<>();
        referenceMultiValue.put("name", "John Doe");
        referenceMultiValue.put("age", 30);
        referenceFreeForm.put("person", "Person Details", multiField, referenceMultiValue);

        // Create updated form with empty MultiField value
        updatedFreeForm.put("person", "Person Details", multiField, new HashMap<>());

        // Act
        boolean result = FreeFormDefaultsHelper.hasMissingValuesFromReference(updatedFreeForm, referenceFreeForm, true);

        // Assert
        assertTrue(result); // Should return true because MultiField has empty value
    }

    @Test
    public void testHasMissingValuesFromReference_WithMultiFieldPresent_ReturnsFalse() {
        // Arrange - Create reference form with MultiField
        Field multiField = createField("com.krista.fields.MultiField", Map.of(), true);
        Map<String, Object> referenceMultiValue = new HashMap<>();
        referenceMultiValue.put("name", "John Doe");
        referenceMultiValue.put("age", 30);
        referenceFreeForm.put("person", "Person Details", multiField, referenceMultiValue);

        // Create updated form with MultiField having some value (content doesn't matter for top-level comparison)
        Map<String, Object> updatedMultiValue = new HashMap<>();
        updatedMultiValue.put("name", "Jane Smith");
        updatedMultiValue.put("department", "Engineering"); // Different content, but field is present
        updatedFreeForm.put("person", "Person Details", multiField, updatedMultiValue);

        // Act
        boolean result = FreeFormDefaultsHelper.hasMissingValuesFromReference(updatedFreeForm, referenceFreeForm, true);

        // Assert
        assertFalse(result); // Should return false because MultiField is present with non-empty value
    }

    @Test
    public void testHasOnlyReferenceFieldValues_WithMultiFieldOnlyReferenceNestedFields_ReturnsTrue() {
        // Arrange - Create reference form with MultiField
        Field multiField = createField("com.krista.fields.MultiField", Map.of(), true);
        Map<String, Object> referenceMultiValue = new HashMap<>();
        referenceMultiValue.put("name", "John Doe");
        referenceMultiValue.put("age", 30);
        referenceMultiValue.put("email", "john@example.com");
        referenceFreeForm.put("person", "Person Details", multiField, referenceMultiValue);

        // Create updated form with only reference nested fields (subset of reference)
        Map<String, Object> updatedMultiValue = new HashMap<>();
        updatedMultiValue.put("name", "Jane Smith");
        updatedMultiValue.put("age", 25);
        // email is not included, but that's okay - we're checking for extra fields, not missing ones
        updatedFreeForm.put("person", "Person Details", multiField, updatedMultiValue);

        // Act
        boolean result = FreeFormDefaultsHelper.hasOnlyReferenceFieldValues(updatedFreeForm, referenceFreeForm, false);

        // Assert
        assertTrue(result); // Should return true because no extra nested fields beyond reference
    }

    @Test
    public void testHasOnlyReferenceFieldValues_WithMultiFieldExtraNestedFields_ReturnsFalse() {
        // Arrange - Create reference form with MultiField
        Field multiField = createField("com.krista.fields.MultiField", Map.of(), true);
        Map<String, Object> referenceMultiValue = new HashMap<>();
        referenceMultiValue.put("name", "John Doe");
        referenceMultiValue.put("age", 30);
        referenceFreeForm.put("person", "Person Details", multiField, referenceMultiValue);

        // Create updated form with extra nested fields not in reference
        Map<String, Object> updatedMultiValue = new HashMap<>();
        updatedMultiValue.put("name", "Jane Smith");
        updatedMultiValue.put("age", 25);
        updatedMultiValue.put("email", "jane@example.com"); // Extra nested field not in reference
        updatedMultiValue.put("phone", "123-456-7890"); // Another extra nested field
        updatedFreeForm.put("person", "Person Details", multiField, updatedMultiValue);

        // Act
        boolean result = FreeFormDefaultsHelper.hasOnlyReferenceFieldValues(updatedFreeForm, referenceFreeForm, false);

        // Assert
        assertFalse(result); // Should return false because of extra nested fields (email, phone)
    }

    @Test
    public void testHasOnlyReferenceFieldValues_WithMultiFieldAndExtraField_ReturnsFalse() {
        // Arrange - Create reference form with MultiField
        Field multiField = createField("com.krista.fields.MultiField", Map.of(), true);
        Map<String, Object> referenceMultiValue = new HashMap<>();
        referenceMultiValue.put("name", "John Doe");
        referenceFreeForm.put("person", "Person Details", multiField, referenceMultiValue);

        // Create updated form with MultiField plus extra top-level field
        Map<String, Object> updatedMultiValue = new HashMap<>();
        updatedMultiValue.put("name", "Jane Smith");
        updatedFreeForm.put("person", "Person Details", multiField, updatedMultiValue);

        Field extraField = createField("com.krista.fields.Text", Map.of(), true);
        updatedFreeForm.put("extraField", "Extra Field", extraField, "Extra Value");

        // Act
        boolean result = FreeFormDefaultsHelper.hasOnlyReferenceFieldValues(updatedFreeForm, referenceFreeForm, true);

        // Assert
        assertFalse(result); // Should return false because of extra top-level field
    }

    // ========== Tests for List field scenarios ==========

    @Test
    public void testHasMissingValuesFromReference_WithListFieldEmpty_ReturnsTrue() {
        // Arrange - Create reference form with List field
        Field listField = createField("com.krista.fields.List", Map.of(), true);
        List<String> referenceListValue = List.of("Java", "Python", "JavaScript");
        referenceFreeForm.put("skills", "Programming Skills", listField, referenceListValue);

        // Create updated form with empty List (empty collections are considered missing values)
        List<String> updatedListValue = new ArrayList<>();
        updatedFreeForm.put("skills", "Programming Skills", listField, updatedListValue);

        // Act
        boolean result = FreeFormDefaultsHelper.hasMissingValuesFromReference(updatedFreeForm, referenceFreeForm, true);

        // Assert
        assertTrue(result); // Should return true because List field is empty
    }

    @Test
    public void testHasMissingValuesFromReference_WithListFieldMissing_ReturnsTrue() {
        // Arrange - Create reference form with List field
        Field listField = createField("com.krista.fields.List", Map.of(), true);
        List<String> referenceListValue = List.of("Java", "Python", "JavaScript");
        referenceFreeForm.put("skills", "Programming Skills", listField, referenceListValue);

        // Create updated form WITHOUT the List field (top-level field is missing)
        // updatedFreeForm is empty - the "skills" field is completely missing

        // Act
        boolean result = FreeFormDefaultsHelper.hasMissingValuesFromReference(updatedFreeForm, referenceFreeForm, true);

        // Assert
        assertTrue(result); // Should return true because the entire "skills" List field is missing
    }

    @Test
    public void testHasMissingValuesFromReference_WithListFieldPresent_ReturnsFalse() {
        // Arrange - Create reference form with List field
        Field listField = createField("com.krista.fields.List", Map.of(), true);
        List<String> referenceListValue = List.of("Java", "Python");
        referenceFreeForm.put("skills", "Programming Skills", listField, referenceListValue);

        // Create updated form with List field having some values
        List<String> updatedListValue = List.of("C++", "Go"); // Different content, but field is present and non-empty
        updatedFreeForm.put("skills", "Programming Skills", listField, updatedListValue);

        // Act
        boolean result = FreeFormDefaultsHelper.hasMissingValuesFromReference(updatedFreeForm, referenceFreeForm, true);

        // Assert
        assertFalse(result); // Should return false because List field is present with non-empty value
    }

    @Test
    public void testHasMissingValuesFromReference_WithListFieldWithComplexElements_ReturnsTrue() {
        // Arrange - Create reference form with List field containing complex elements
        Field listField = createField("com.krista.fields.List", Map.of(), true);
        List<Map<String, Object>> referenceListValue = List.of(
            Map.of("name", "Java", "level", "Expert"),
            Map.of("name", "Python", "level", "Intermediate")
        );
        referenceFreeForm.put("skills", "Programming Skills", listField, referenceListValue);

        // Create updated form with empty List
        List<Map<String, Object>> updatedListValue = new ArrayList<>();
        updatedFreeForm.put("skills", "Programming Skills", listField, updatedListValue);

        // Act
        boolean result = FreeFormDefaultsHelper.hasMissingValuesFromReference(updatedFreeForm, referenceFreeForm, true);

        // Assert
        assertTrue(result); // Should return true because List is empty but reference has values
    }

    @Test
    public void testHasMissingValuesFromReference_WithListFieldWithComplexElementsPresent_ReturnsFalse() {
        // Arrange - Create reference form with List field containing complex elements
        Field listField = createField("com.krista.fields.List", Map.of(), true);
        List<Map<String, Object>> referenceListValue = List.of(
            Map.of("name", "Java", "level", "Expert")
        );
        referenceFreeForm.put("skills", "Programming Skills", listField, referenceListValue);

        // Create updated form with List containing some elements
        List<Map<String, Object>> updatedListValue = List.of(
            Map.of("name", "Python", "level", "Beginner"),
            Map.of("name", "JavaScript", "level", "Intermediate")
        );
        updatedFreeForm.put("skills", "Programming Skills", listField, updatedListValue);

        // Act
        boolean result = FreeFormDefaultsHelper.hasMissingValuesFromReference(updatedFreeForm, referenceFreeForm, true);

        // Assert
        assertFalse(result); // Should return false because List field is present with non-empty values
    }

    @Test
    public void testHasMissingValuesFromReference_WithListFieldNull_ReturnsTrue() {
        // Arrange - Create reference form with List field
        Field listField = createField("com.krista.fields.List", Map.of(), true);
        List<String> referenceListValue = List.of("Java", "Python");
        referenceFreeForm.put("skills", "Programming Skills", listField, referenceListValue);

        // Create updated form with null List
        updatedFreeForm.put("skills", "Programming Skills", listField, null);

        // Act
        boolean result = FreeFormDefaultsHelper.hasMissingValuesFromReference(updatedFreeForm, referenceFreeForm, true);

        // Assert
        assertTrue(result); // Should return true because List field is null
    }

    @Test
    public void testHasOnlyReferenceFieldValues_WithListField_ReturnsTrue() {
        // Arrange - Create reference form with List field
        Field listField = createField("com.krista.fields.List", Map.of(), true);
        List<String> referenceListValue = List.of("Java", "Python");
        referenceFreeForm.put("skills", "Programming Skills", listField, referenceListValue);

        // Create updated form with only List field (no extra fields)
        List<String> updatedListValue = List.of("Java", "Python", "JavaScript", "C++");
        updatedFreeForm.put("skills", "Programming Skills", listField, updatedListValue);

        // Act
        boolean result = FreeFormDefaultsHelper.hasOnlyReferenceFieldValues(updatedFreeForm, referenceFreeForm, true);

        // Assert
        assertTrue(result); // Should return true because no extra top-level fields, only List content
    }

    @Test
    public void testHasOnlyReferenceFieldValues_WithListFieldAndExtraField_ReturnsFalse() {
        // Arrange - Create reference form with List field
        Field listField = createField("com.krista.fields.List", Map.of(), true);
        List<String> referenceListValue = List.of("Java", "Python");
        referenceFreeForm.put("skills", "Programming Skills", listField, referenceListValue);

        // Create updated form with List field plus extra top-level field
        List<String> updatedListValue = List.of("Java", "Python", "JavaScript");
        updatedFreeForm.put("skills", "Programming Skills", listField, updatedListValue);

        Field extraField = createField("com.krista.fields.Text", Map.of(), true);
        updatedFreeForm.put("extraField", "Extra Field", extraField, "Extra Value");

        // Act
        boolean result = FreeFormDefaultsHelper.hasOnlyReferenceFieldValues(updatedFreeForm, referenceFreeForm, true);

        // Assert
        assertFalse(result); // Should return false because of extra top-level field
    }

    @Test
    public void testHasOnlyReferenceFieldValues_WithEmptyListField_ReturnsTrue() {
        // Arrange - Create reference form with List field
        Field listField = createField("com.krista.fields.List", Map.of(), true);
        List<String> referenceListValue = List.of("Java", "Python");
        referenceFreeForm.put("skills", "Programming Skills", listField, referenceListValue);

        // Create updated form with empty List (should be ignored as empty value)
        List<String> updatedListValue = new ArrayList<>();
        updatedFreeForm.put("skills", "Programming Skills", listField, updatedListValue);

        Field extraField = createField("com.krista.fields.Text", Map.of(), true);
        updatedFreeForm.put("extraField", "Extra Field", extraField, ""); // empty value should be ignored

        // Act
        boolean result = FreeFormDefaultsHelper.hasOnlyReferenceFieldValues(updatedFreeForm, referenceFreeForm, true);

        // Assert
        assertTrue(result); // Should return true because empty values are ignored
    }
}
