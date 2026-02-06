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

import app.krista.extensions.hook.common.fieldToLLM.FieldToLLMFieldConverter;
import app.krista.extensions.hook.common.fieldToLLM.FieldToLLMFieldConverterRegistry;
import app.krista.extensions.hook.common.fieldToLLM.impl.DefaultFieldToLLMFieldConverter;
import app.krista.extensions.hook.common.fieldToLLM.impl.ListFieldToLLMFieldConverter;
import app.krista.extensions.hook.common.fieldToLLM.impl.MultiFieldToLLMFieldConverter;
import app.krista.extensions.hook.common.models.LLMField;
import app.krista.model.field.Field;
import app.krista.model.field.NamedField;
import app.krista.model.field.NamedValuedField;
import org.junit.Test;
import org.junit.Before;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

@SuppressWarnings("unchecked")
public class FieldToLLMFieldConverterTest {

    private FieldToLLMFieldConverterRegistry registry;
    private DefaultFieldToLLMFieldConverter defaultConverter;
    private MultiFieldToLLMFieldConverter multiFieldConverter;
    private ListFieldToLLMFieldConverter listConverter;

    // Mock fields for all supported field types
    @Mock private Field textField;
    @Mock private Field numberField;
    @Mock private Field dateField;
    @Mock private Field timeField;
    @Mock private Field dateRangeField;
    @Mock private Field dateTimeRangeField;
    @Mock private Field ratingField;
    @Mock private Field switchField;
    @Mock private Field checkboxField;
    @Mock private Field phoneField;
    @Mock private Field pickOneField;
    @Mock private Field emailField;
    @Mock private Field identifierField;
    @Mock private Field streetField;
    @Mock private Field stateField;
    @Mock private Field personField;
    @Mock private Field countryField;
    @Mock private Field paragraphField;
    @Mock private Field street2Field;
    @Mock private Field properNounField;
    @Mock private Field cityField;
    @Mock private Field postalCodeField;
    @Mock private Field percentageField;
    @Mock private Field unitField;
    @Mock private Field currencyField;
    @Mock private Field multiField;
    @Mock private Field listField;

    // Mock nested fields
    @Mock private NamedField nestedTextField;
    @Mock private NamedField nestedEmailField;
    @Mock private NamedField nestedRatingField;

    // Mock options
    @Mock private NamedValuedField requiredOption;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.openMocks(this);

        // Initialize registry and converters
        registry = FieldToLLMFieldConverterRegistry.create();
        defaultConverter = new DefaultFieldToLLMFieldConverter();
        multiFieldConverter = new MultiFieldToLLMFieldConverter(registry);
        listConverter = new ListFieldToLLMFieldConverter(registry);

        createTestFieldObjects();
    }

    private void createTestFieldObjects() throws Exception {
        // Setup basic field types with mandatory options
        setupBasicField(textField, "com.krista.fields.Text", true);
        setupBasicField(numberField, "com.krista.fields.Number", false);
        setupBasicField(dateField, "com.krista.fields.Date", true);
        setupBasicField(timeField, "com.krista.fields.Time", false);
        setupBasicField(dateRangeField, "com.krista.fields.DateRange", true);
        setupBasicField(dateTimeRangeField, "com.krista.fields.DateTimeRange", false);
        setupBasicField(ratingField, "com.krista.fields.Rating", true);
        setupBasicField(switchField, "com.krista.fields.Switch", false);
        setupBasicField(checkboxField, "com.krista.fields.Checkboxes", true);
        setupBasicField(phoneField, "com.krista.fields.Phone", false);
        setupBasicField(pickOneField, "com.krista.fields.PickOne", true);
        setupBasicField(emailField, "com.krista.fields.Email", false);
        setupBasicField(identifierField, "com.krista.fields.Identifier", true);
        setupBasicField(streetField, "com.krista.fields.Street", false);
        setupBasicField(stateField, "com.krista.fields.State", true);
        setupBasicField(personField, "com.krista.fields.Person", false);
        setupBasicField(countryField, "com.krista.fields.Country", true);
        setupBasicField(paragraphField, "com.krista.fields.Paragraph", false);
        setupBasicField(street2Field, "com.krista.fields.Street2", true);
        setupBasicField(properNounField, "com.krista.fields.ProperNoun", false);
        setupBasicField(cityField, "com.krista.fields.City", true);
        setupBasicField(postalCodeField, "com.krista.fields.PostalCode", false);
        setupBasicField(percentageField, "com.krista.fields.Percentage", true);
        setupBasicField(unitField, "com.krista.fields.Unit", false);
        setupBasicField(currencyField, "com.krista.fields.Currency", true);

        // Setup nested fields for complex tests
        setupNamedField(nestedTextField, "Name", "com.krista.fields.Text", true);
        setupNamedField(nestedEmailField, "Email", "com.krista.fields.Email", false);
        setupNamedField(nestedRatingField, "Rating", "com.krista.fields.Rating", true);

        // Setup MultiField with nested fields
        when(multiField.getFieldType()).thenReturn("com.krista.fields.MultiField");
        when(multiField.getAttributeValues()).thenReturn(
                Map.of("fields", List.of(nestedTextField, nestedEmailField)));
        setupFieldOptions(multiField, true);

        // Setup List field with element field
        when(listField.getFieldType()).thenReturn("com.krista.fields.List");
        when(listField.getAttributeValues()).thenReturn(
                Map.of("field", nestedTextField));
        setupFieldOptions(listField, false);
    }

    private void setupBasicField(Field field, String fieldType, boolean isMandatory) {
        when(field.getFieldType()).thenReturn(fieldType);
        when(field.getAttributeValues()).thenReturn(Map.of(
                "minLength", 5,
                "maxLength", 100,
                "pattern", "^[A-Za-z]+$",
                "invalidAttribute", "shouldBeFiltered"
        ));
        setupFieldOptions(field, isMandatory);
    }

    private void setupNamedField(NamedField field, String name, String fieldType, boolean isMandatory) {
        when(field.getName()).thenReturn(name);
        when(field.getFieldType()).thenReturn(fieldType);
        when(field.getAttributeValues()).thenReturn(Map.of(
                "minLength", 3,
                "maxLength", 50
        ));
        setupFieldOptions(field, isMandatory);
    }

    private void setupFieldOptions(Field field, boolean isMandatory) {
        NamedValuedField fieldRequiredOption = mock(NamedValuedField.class);
        when(fieldRequiredOption.getValue()).thenReturn(isMandatory);
        when(field.getOptions()).thenReturn(Map.of("required", fieldRequiredOption));
    }

    // Test DefaultFieldToLLMFieldConverter with various field types
    @Test
    public void testDefaultConverter_WithTextField_ReturnsCorrectLLMField() throws Exception {
        // Act
        LLMField result = defaultConverter.extract(textField);

        // Assert
        assertNotNull(result);
        assertEquals("com.krista.fields.Text", result.fieldType());
        assertTrue(result.isMandatory());

        // Verify filtered attributes (only allowed attributes should be present)
        Map<String, Object> attributes = result.attributeValues();
        assertTrue(attributes.containsKey("minLength"));
        assertTrue(attributes.containsKey("maxLength"));
        assertFalse(attributes.containsKey("invalidAttribute")); // Should be filtered out
    }

    @Test
    public void testDefaultConverter_WithNumberField_ReturnsCorrectLLMField() throws Exception {
        // Act
        LLMField result = defaultConverter.extract(numberField);

        // Assert
        assertNotNull(result);
        assertEquals("com.krista.fields.Number", result.fieldType());
        assertFalse(result.isMandatory());
        assertNotNull(result.attributeValues());
    }

    @Test
    public void testDefaultConverter_WithAllBasicFieldTypes_ReturnsCorrectLLMFields() throws Exception {
        // Test a representative sample of all basic field types
        Field[] fields = {
                dateField, timeField, dateRangeField, ratingField, switchField,
                checkboxField, phoneField, pickOneField, emailField, identifierField,
                streetField, stateField, personField, countryField, paragraphField,
                street2Field, properNounField, cityField, postalCodeField,
                percentageField, unitField, currencyField
        };

        String[] expectedTypes = {
                "com.krista.fields.Date", "com.krista.fields.Time", "com.krista.fields.DateRange",
                "com.krista.fields.Rating", "com.krista.fields.Switch", "com.krista.fields.Checkboxes",
                "com.krista.fields.Phone", "com.krista.fields.PickOne", "com.krista.fields.Email",
                "com.krista.fields.Identifier", "com.krista.fields.Street", "com.krista.fields.State",
                "com.krista.fields.Person", "com.krista.fields.Country", "com.krista.fields.Paragraph",
                "com.krista.fields.Street2", "com.krista.fields.ProperNoun", "com.krista.fields.City",
                "com.krista.fields.PostalCode", "com.krista.fields.Percentage", "com.krista.fields.Unit",
                "com.krista.fields.Currency"
        };

        for (int i = 0; i < fields.length; i++) {
            LLMField result = defaultConverter.extract(fields[i]);
            assertNotNull("Result should not be null for " + expectedTypes[i], result);
            assertEquals("Field type should match for " + expectedTypes[i],
                    expectedTypes[i], result.fieldType());
            assertNotNull("Attributes should not be null for " + expectedTypes[i],
                    result.attributeValues());
        }
    }

    // Test MultiFieldToLLMFieldConverter
    @Test
    public void testMultiFieldConverter_WithNestedFields_ReturnsCorrectLLMField() throws Exception {
        // Act
        LLMField result = multiFieldConverter.extract(multiField);

        // Assert
        assertNotNull(result);
        assertEquals("com.krista.fields.MultiField", result.fieldType());
        assertTrue(result.isMandatory());

        // Verify nested fields processing
        Map<String, Object> attributes = result.attributeValues();
        assertTrue(attributes.containsKey("fields"));

        List<Map<String, Object>> processedFields = (List<Map<String, Object>>) attributes.get("fields");
        assertNotNull(processedFields);
        assertEquals(2, processedFields.size());

        // Verify first nested field (Name)
        Map<String, Object> nameField = processedFields.get(0);
        assertEquals("Name", nameField.get("name"));
        assertEquals("com.krista.fields.Text", nameField.get("fieldType"));
        assertEquals(true, nameField.get("isMandatoryField"));
        assertNotNull(nameField.get("attributes"));

        // Verify second nested field (Email)
        Map<String, Object> emailField = processedFields.get(1);
        assertEquals("Email", emailField.get("name"));
        assertEquals("com.krista.fields.Email", emailField.get("fieldType"));
        assertEquals(false, emailField.get("isMandatoryField"));
        assertNotNull(emailField.get("attributes"));
    }

    // Test ListFieldToLLMFieldConverter
    @Test
    public void testListConverter_WithElementField_ReturnsCorrectLLMField() throws Exception {
        // Act
        LLMField result = listConverter.extract(listField);

        // Assert
        assertNotNull(result);
        assertEquals("com.krista.fields.List", result.fieldType());
        assertFalse(result.isMandatory());

        // Verify element field processing
        Map<String, Object> attributes = result.attributeValues();
        assertTrue(attributes.containsKey("field"));

        Map<String, Object> processedField = (Map<String, Object>) attributes.get("field");
        assertNotNull(processedField);
        assertEquals("com.krista.fields.Text", processedField.get("fieldType"));
        assertEquals(true, processedField.get("isMandatoryField"));
        assertNotNull(processedField.get("attributes"));
    }

    // Test Registry functionality
    @Test
    public void testRegistry_GetDefaultConverter_ForBasicFieldTypes() throws Exception {
        // Test that registry returns default converter for basic field types
        FieldToLLMFieldConverter converter = registry.get("com.krista.fields.Text");
        assertNotNull(converter);
        assertTrue(converter instanceof DefaultFieldToLLMFieldConverter);

        converter = registry.get("com.krista.fields.Number");
        assertNotNull(converter);
        assertTrue(converter instanceof DefaultFieldToLLMFieldConverter);

        converter = registry.get("com.krista.fields.Date");
        assertNotNull(converter);
        assertTrue(converter instanceof DefaultFieldToLLMFieldConverter);
    }

    @Test
    public void testRegistry_GetSpecializedConverter_ForComplexFieldTypes() throws Exception {
        // Test that registry returns specialized converters for complex field types
        FieldToLLMFieldConverter converter = registry.get("com.krista.fields.MultiField");
        assertNotNull(converter);
        assertTrue(converter instanceof MultiFieldToLLMFieldConverter);

        converter = registry.get("com.krista.fields.List");
        assertNotNull(converter);
        assertTrue(converter instanceof ListFieldToLLMFieldConverter);
    }

    @Test
    public void testRegistry_WithUnsupportedFieldType_ThrowsException() {
        // Test that registry throws exception for unsupported field types
        Exception exception = assertThrows(Exception.class, () -> {
            registry.get("com.krista.fields.UnsupportedType");
        });
        assertTrue(exception.getMessage().contains("No converter registered for field type"));
        assertTrue(exception.getMessage().contains("com.krista.fields.UnsupportedType"));
    }

    @Test
    public void testRegistry_WithAllSupportedFieldTypes_ReturnsConverters() throws Exception {
        // Test all supported field types from the registry
        String[] supportedTypes = {
                "com.krista.fields.Time", "com.krista.fields.Date", "com.krista.fields.DateRange",
                "com.krista.fields.DateTimeRange", "com.krista.fields.MultiField", "com.krista.fields.List",
                "com.krista.fields.Checkboxes", "com.krista.fields.Switch", "com.krista.fields.Phone",
                "com.krista.fields.Rating", "com.krista.fields.PickOne", "com.krista.fields.Email",
                "com.krista.fields.Identifier", "com.krista.fields.Text", "com.krista.fields.Street",
                "com.krista.fields.State", "com.krista.fields.Person", "com.krista.fields.Country",
                "com.krista.fields.Paragraph", "com.krista.fields.Street2", "com.krista.fields.ProperNoun",
                "com.krista.fields.City", "com.krista.fields.PostalCode", "com.krista.fields.Percentage",
                "com.krista.fields.Unit", "com.krista.fields.Number", "com.krista.fields.Currency"
        };

        for (String fieldType : supportedTypes) {
            FieldToLLMFieldConverter converter = registry.get(fieldType);
            assertNotNull("Converter should not be null for " + fieldType, converter);
        }
    }

    // Test complex nested field case
    @Test
    public void testComplexNestedField_WithMultiFieldContainingList_ReturnsCorrectStructure() throws Exception {
        // Arrange - Create complex nested structure: MultiField containing List and other fields
        NamedField skillItemField = mock(NamedField.class);
        setupNamedField(skillItemField, "Skill", "com.krista.fields.Text", false);

        NamedField skillsListField = mock(NamedField.class);
        when(skillsListField.getName()).thenReturn("Skills");
        when(skillsListField.getFieldType()).thenReturn("com.krista.fields.List");
        when(skillsListField.getAttributeValues()).thenReturn(Map.of("field", skillItemField));
        setupFieldOptions(skillsListField, true);

        NamedField personalInfoField = mock(NamedField.class);
        when(personalInfoField.getName()).thenReturn("PersonalInfo");
        when(personalInfoField.getFieldType()).thenReturn("com.krista.fields.MultiField");
        when(personalInfoField.getAttributeValues()).thenReturn(
                Map.of("fields", List.of(nestedTextField, nestedEmailField)));
        setupFieldOptions(personalInfoField, false);

        Field complexRootField = mock(Field.class);
        when(complexRootField.getFieldType()).thenReturn("com.krista.fields.MultiField");
        when(complexRootField.getAttributeValues()).thenReturn(
                Map.of("fields", List.of(personalInfoField, skillsListField, nestedRatingField)));
        setupFieldOptions(complexRootField, true);

        // Act
        LLMField result = multiFieldConverter.extract(complexRootField);

        // Assert
        assertNotNull(result);
        assertEquals("com.krista.fields.MultiField", result.fieldType());
        assertTrue(result.isMandatory());

        // Verify complex nested structure
        Map<String, Object> attributes = result.attributeValues();
        List<Map<String, Object>> fields = (List<Map<String, Object>>) attributes.get("fields");
        assertEquals(3, fields.size());

        // Verify PersonalInfo (nested MultiField)
        Map<String, Object> personalInfo = fields.get(0);
        assertEquals("PersonalInfo", personalInfo.get("name"));
        assertEquals("com.krista.fields.MultiField", personalInfo.get("fieldType"));
        assertEquals(false, personalInfo.get("isMandatoryField"));

        Map<String, Object> personalInfoAttrs = (Map<String, Object>) personalInfo.get("attributes");
        List<Map<String, Object>> personalInfoFields = (List<Map<String, Object>>) personalInfoAttrs.get("fields");
        assertEquals(2, personalInfoFields.size());

        // Verify Skills (List field)
        Map<String, Object> skills = fields.get(1);
        assertEquals("Skills", skills.get("name"));
        assertEquals("com.krista.fields.List", skills.get("fieldType"));
        assertEquals(true, skills.get("isMandatoryField"));

        Map<String, Object> skillsAttrs = (Map<String, Object>) skills.get("attributes");
        Map<String, Object> skillField = (Map<String, Object>) skillsAttrs.get("field");
        assertEquals("com.krista.fields.Text", skillField.get("fieldType"));

        // Verify Rating (simple field)
        Map<String, Object> rating = fields.get(2);
        assertEquals("Rating", rating.get("name"));
        assertEquals("com.krista.fields.Rating", rating.get("fieldType"));
        assertEquals(true, rating.get("isMandatoryField"));
    }

    // Test edge cases
    @Test
    public void testConverter_WithNullAttributes_HandlesGracefully() throws Exception {
        // Arrange
        Field fieldWithNullAttributes = mock(Field.class);
        when(fieldWithNullAttributes.getFieldType()).thenReturn("com.krista.fields.Text");
        when(fieldWithNullAttributes.getAttributeValues()).thenReturn(null);
        setupFieldOptions(fieldWithNullAttributes, false);

        // Act
        LLMField result = defaultConverter.extract(fieldWithNullAttributes);

        // Assert
        assertNotNull(result);
        assertEquals("com.krista.fields.Text", result.fieldType());
        assertFalse(result.isMandatory());
        assertNotNull(result.attributeValues());
        assertTrue(result.attributeValues().isEmpty());
    }

    @Test
    public void testConverter_WithNullOptions_DefaultsToOptional() throws Exception {
        // Arrange
        Field fieldWithNullOptions = mock(Field.class);
        when(fieldWithNullOptions.getFieldType()).thenReturn("com.krista.fields.Text");
        when(fieldWithNullOptions.getAttributeValues()).thenReturn(Map.of());
        when(fieldWithNullOptions.getOptions()).thenReturn(null);

        // Act
        LLMField result = defaultConverter.extract(fieldWithNullOptions);

        // Assert
        assertNotNull(result);
        assertEquals("com.krista.fields.Text", result.fieldType());
        assertFalse(result.isMandatory()); // Should default to false when options are null
    }

    @Test
    public void testConverter_WithInvalidRequiredOption_DefaultsToOptional() throws Exception {
        // Arrange
        Field fieldWithInvalidOption = mock(Field.class);
        when(fieldWithInvalidOption.getFieldType()).thenReturn("com.krista.fields.Text");
        when(fieldWithInvalidOption.getAttributeValues()).thenReturn(Map.of());

        NamedValuedField invalidRequiredOption = mock(NamedValuedField.class);
        when(invalidRequiredOption.getValue()).thenReturn("invalid_boolean_value");
        when(fieldWithInvalidOption.getOptions()).thenReturn(Map.of("required", invalidRequiredOption));

        // Act
        LLMField result = defaultConverter.extract(fieldWithInvalidOption);

        // Assert
        assertNotNull(result);
        assertEquals("com.krista.fields.Text", result.fieldType());
        assertFalse(result.isMandatory()); // Should default to false when option value is invalid
    }
}
