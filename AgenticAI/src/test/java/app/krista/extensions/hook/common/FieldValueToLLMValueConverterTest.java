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

import app.krista.extensions.hook.common.fieldToLLM.valueConverter.FieldValueToLLMValueConverterRegistry;
import app.krista.extensions.hook.common.fieldToLLM.valueConverter.impl.*;
import app.krista.model.base.DateRange;
import app.krista.model.base.PhoneNumber;
import app.krista.model.field.Field;
import app.krista.model.field.NamedField;
import org.junit.Test;
import org.junit.Before;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.*;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

@SuppressWarnings("unchecked")
public class FieldValueToLLMValueConverterTest {

    private TextFieldValueToLLMValueConverter textConverter;
    private NumberFieldValueToLLMValueConverter numberConverter;
    private DateFieldValueToLLMValueConverter dateConverter;
    private TimeFieldValueToLLMValueConverter timeConverter;
    private DateRangeFieldValueToLLMValueConverter dateRangeConverter;
    private RatingFieldValueToLLMValueConverter ratingConverter;
    private SwitchFieldValueToLLMValueConverter switchConverter;
    private CheckboxFieldValueToLLMValueConverter checkboxConverter;
    private PhoneNumberFieldValueToLLMValueConverter phoneConverter;
    private MultiFieldValueToLLMValueConverter multiFieldConverter;
    private ListFieldValueToLLMValueConverter listConverter;

    @Mock
    private Field textField;
    @Mock
    private Field numberField;
    @Mock
    private Field dateField;
    @Mock
    private Field timeField;
    @Mock
    private Field dateRangeField;
    @Mock
    private Field ratingField;
    @Mock
    private Field switchField;
    @Mock
    private Field checkboxField;
    @Mock
    private Field phoneField;
    @Mock
    private Field multiField;
    @Mock
    private Field listField;
    @Mock
    private NamedField nestedTextField;
    @Mock
    private NamedField nestedEmailField;

    // Additional mock fields for complex test
    @Mock
    private NamedField complexRootField;
    @Mock
    private NamedField personalInfoField;
    @Mock
    private NamedField skillsListField;
    @Mock
    private NamedField skillItemField;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.openMocks(this);

        // Initialize registry and converters
        FieldValueToLLMValueConverterRegistry registry = FieldValueToLLMValueConverterRegistry.create();
        textConverter = new TextFieldValueToLLMValueConverter();
        numberConverter = new NumberFieldValueToLLMValueConverter();
        dateConverter = new DateFieldValueToLLMValueConverter();
        timeConverter = new TimeFieldValueToLLMValueConverter();
        dateRangeConverter = new DateRangeFieldValueToLLMValueConverter();
        ratingConverter = new RatingFieldValueToLLMValueConverter();
        switchConverter = new SwitchFieldValueToLLMValueConverter();
        checkboxConverter = new CheckboxFieldValueToLLMValueConverter();
        phoneConverter = new PhoneNumberFieldValueToLLMValueConverter();
        multiFieldConverter = new MultiFieldValueToLLMValueConverter(registry);
        listConverter = new ListFieldValueToLLMValueConverter(registry);

        createTestFieldObjects();
    }

    private void createTestFieldObjects() throws Exception {
        // Text field
        when(textField.getFieldType()).thenReturn("com.krista.fields.Text");
        when(textField.getAttributeValues()).thenReturn(Map.of());

        // Number field
        when(numberField.getFieldType()).thenReturn("com.krista.fields.Number");
        when(numberField.getAttributeValues()).thenReturn(Map.of());

        // Date field
        when(dateField.getFieldType()).thenReturn("com.krista.fields.Date");
        when(dateField.getAttributeValues()).thenReturn(Map.of());

        // Time field
        when(timeField.getFieldType()).thenReturn("com.krista.fields.Time");
        when(timeField.getAttributeValues()).thenReturn(Map.of());

        // DateRange field
        when(dateRangeField.getFieldType()).thenReturn("com.krista.fields.DateRange");
        when(dateRangeField.getAttributeValues()).thenReturn(Map.of());

        // Rating field
        when(ratingField.getFieldType()).thenReturn("com.krista.fields.Rating");
        when(ratingField.getAttributeValues()).thenReturn(Map.of());

        // Switch field
        when(switchField.getFieldType()).thenReturn("com.krista.fields.Switch");
        when(switchField.getAttributeValues()).thenReturn(Map.of());

        // Checkbox field
        when(checkboxField.getFieldType()).thenReturn("com.krista.fields.Checkboxes");
        when(checkboxField.getAttributeValues()).thenReturn(Map.of());

        // Phone field
        when(phoneField.getFieldType()).thenReturn("com.krista.fields.Phone");
        when(phoneField.getAttributeValues()).thenReturn(Map.of());

        // Setup nested fields for MultiField and List tests
        when(nestedTextField.getName()).thenReturn("Name");
        when(nestedTextField.getFieldType()).thenReturn("com.krista.fields.Text");
        when(nestedTextField.getAttributeValues()).thenReturn(Map.of());

        when(nestedEmailField.getName()).thenReturn("Email");
        when(nestedEmailField.getFieldType()).thenReturn("com.krista.fields.Email");
        when(nestedEmailField.getAttributeValues()).thenReturn(Map.of());

        // MultiField: Contact Details with nested fields
        when(multiField.getFieldType()).thenReturn("com.krista.fields.MultiField");
        when(multiField.getAttributeValues()).thenReturn(
                Map.of("fields", List.of(nestedTextField, nestedEmailField)));

        // List field: List of text items
        when(listField.getFieldType()).thenReturn("com.krista.fields.List");
        when(listField.getAttributeValues()).thenReturn(
                Map.of("field", nestedTextField));
    }

    @Test
    public void testTextConverter_WithValidInput_ReturnsString() throws Exception {
        // Arrange
        String input = "John Doe";

        // Act
        String result = textConverter.extract(textField, input);

        // Assert
        assertNotNull(result);
        assertEquals("John Doe", result);
    }

    @Test
    public void testTextConverter_WithInvalidInput_ThrowsException() throws Exception {
        // Arrange
        Integer invalidInput = 123;

        // Act & Assert
        Exception exception = assertThrows(Exception.class, () -> {
            textConverter.extract(textField, invalidInput);
        });
        assertTrue(exception.getMessage().contains("Failed to extract text value"));
    }

    @Test
    public void testNumberConverter_WithValidInput_ReturnsDouble() throws Exception {
        // Arrange
        Integer input = 42;

        // Act
        Double result = numberConverter.extract(numberField, input);

        // Assert
        assertNotNull(result);
        assertEquals(Double.valueOf(42.0), result);
    }

    @Test
    public void testNumberConverter_WithDoubleInput_ReturnsDouble() throws Exception {
        // Arrange
        Double input = 42.5;

        // Act
        Double result = numberConverter.extract(numberField, input);

        // Assert
        assertNotNull(result);
        assertEquals(Double.valueOf(42.5), result);
    }

    @Test
    public void testNumberConverter_WithInvalidInput_ThrowsException() throws Exception {
        // Arrange
        String invalidInput = "not a number";

        // Act & Assert
        Exception exception = assertThrows(Exception.class, () -> {
            numberConverter.extract(numberField, invalidInput);
        });
        assertTrue(exception.getMessage().contains("Failed to extract number value"));
    }

    @Test
    public void testDateConverter_WithValidInput_ReturnsFormattedString() throws Exception {
        // Arrange
        // Create timestamp for system default timezone
        LocalDateTime dateTime = LocalDateTime.of(2024, 1, 1, 10, 0, 0);
        Long timestamp = dateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();

        // Act
        String result = dateConverter.extract(dateField, timestamp);

        // Assert
        assertNotNull(result);
        assertEquals("01/01/2024 10:00:00", result);
    }

    @Test
    public void testDateConverter_WithInvalidInput_ThrowsException() throws Exception {
        // Arrange
        String invalidInput = "not a timestamp";

        // Act & Assert
        Exception exception = assertThrows(Exception.class, () -> {
            dateConverter.extract(dateField, invalidInput);
        });
        assertTrue(exception.getMessage().contains("Failed to extract Date value"));
    }

    @Test
    public void testTimeConverter_WithValidInput_ReturnsFormattedString() throws Exception {
        // Arrange
        // Create a timestamp for 14:30:00 today in system default timezone
        LocalDateTime todayAt1430 = LocalDateTime.of(LocalDate.now(), LocalTime.of(14, 30, 0));
        Long timeInMillis = todayAt1430.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();

        // Act
        String result = timeConverter.extract(timeField, timeInMillis);

        // Assert
        assertNotNull(result);
        assertEquals("14:30:00", result);
    }

    @Test
    public void testTimeConverter_WithInvalidInput_ThrowsException() throws Exception {
        // Arrange
        String invalidInput = "not a time";

        // Act & Assert
        Exception exception = assertThrows(Exception.class, () -> {
            timeConverter.extract(timeField, invalidInput);
        });
        assertTrue(exception.getMessage().contains("Failed to extract Time value from type"));
    }

    @Test
    public void testDateRangeConverter_WithValidInput_ReturnsLLMDateRange() throws Exception {
        // Arrange
        LocalDateTime startDateTime = LocalDateTime.of(2024, 1, 1, 10, 0, 0);
        LocalDateTime endDateTime = LocalDateTime.of(2024, 1, 5, 18, 0, 0);
        Long startTimestamp = startDateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        Long endTimestamp = endDateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        DateRange dateRange = new DateRange(startTimestamp, endTimestamp);

        // Act
        Map<String, String> result = dateRangeConverter.extract(dateRangeField, dateRange);

        // Assert
        assertNotNull(result);
        assertEquals("01/01/2024 10:00:00", result.get("startOfDateRange"));
        assertEquals("05/01/2024 18:00:00", result.get("endOfDateRange"));
    }

    @Test
    public void testDateRangeConverter_WithInvalidInput_ThrowsException() throws Exception {
        // Arrange
        String invalidInput = "not a date range";

        // Act & Assert
        Exception exception = assertThrows(Exception.class, () -> {
            dateRangeConverter.extract(dateRangeField, invalidInput);
        });
        assertTrue(exception.getMessage().contains("Failed to extract DateRange value from type"));
    }

    @Test
    public void testRatingConverter_WithValidInput_ReturnsInteger() throws Exception {
        // Arrange
        Integer rating = 4;

        // Act
        Integer result = ratingConverter.extract(ratingField, rating);

        // Assert
        assertNotNull(result);
        assertEquals(Integer.valueOf(4), result);
    }

    @Test
    public void testRatingConverter_WithInvalidInput_ThrowsException() throws Exception {
        // Arrange
        String invalidInput = "not a rating";

        // Act & Assert
        Exception exception = assertThrows(Exception.class, () -> {
            ratingConverter.extract(ratingField, invalidInput);
        });
        assertTrue(exception.getMessage().contains("Failed to extract Rating value"));
    }

    @Test
    public void testSwitchConverter_WithValidInput_ReturnsBoolean() throws Exception {
        // Arrange
        Boolean switchValue = true;

        // Act
        Boolean result = switchConverter.extract(switchField, switchValue);

        // Assert
        assertNotNull(result);
        assertTrue(result);
    }

    @Test
    public void testSwitchConverter_WithInvalidInput_ThrowsException() throws Exception {
        // Arrange
        String invalidInput = "not a boolean";

        // Act & Assert
        Exception exception = assertThrows(Exception.class, () -> {
            switchConverter.extract(switchField, invalidInput);
        });
        assertTrue(exception.getMessage().contains("Failed to extract Switch value"));
    }

    @Test
    public void testCheckboxConverter_WithValidInput_ReturnsStringList() throws Exception {
        // Arrange
        List<String> input = List.of("Java", "Python", "JavaScript");

        // Act
        List<String> result = checkboxConverter.extract(checkboxField, input);

        // Assert
        assertNotNull(result);
        assertEquals(3, result.size());
        assertEquals("Java", result.get(0));
        assertEquals("Python", result.get(1));
        assertEquals("JavaScript", result.get(2));
    }

    @Test
    public void testCheckboxConverter_WithMixedTypeList_ReturnsStringList() throws Exception {
        // Arrange
        List<Object> input = List.of("Java", 123, true);

        // Act
        List<String> result = checkboxConverter.extract(checkboxField, input);

        // Assert
        assertNotNull(result);
        assertEquals(3, result.size()); // null items are skipped
        assertEquals("Java", result.get(0));
        assertEquals("123", result.get(1));
        assertEquals("true", result.get(2));
    }

    @Test
    public void testCheckboxConverter_WithInvalidInput_ThrowsException() throws Exception {
        // Arrange
        String invalidInput = "not a list";

        // Act & Assert
        Exception exception = assertThrows(Exception.class, () -> {
            checkboxConverter.extract(checkboxField, invalidInput);
        });
        assertTrue(exception.getMessage().contains("Failed to extract Checkboxes value from type"));
    }

    @Test
    public void testPhoneConverter_WithValidInput_ReturnsLong() throws Exception {
        // Arrange
        PhoneNumber phoneNumber = new PhoneNumber(91, 9876543210L);

        // Act
        Long result = phoneConverter.extract(phoneField, phoneNumber);

        // Assert
        assertNotNull(result);
        assertEquals(Long.valueOf(9876543210L), result);
    }

    @Test
    public void testPhoneConverter_WithInvalidInput_ThrowsException() throws Exception {
        // Arrange
        String invalidInput = "not a phone number";

        // Act & Assert
        Exception exception = assertThrows(Exception.class, () -> {
            phoneConverter.extract(phoneField, invalidInput);
        });
        assertTrue(exception.getMessage().contains("Failed to extract Phone number"));
    }

    @Test
    public void testMultiFieldConverter_WithValidInput_ReturnsExtractedValues() throws Exception {
        // Arrange
        Map<String, Object> input = Map.of(
                "Name", "John Doe",
                "Email", "john.doe@example.com"
        );

        // Act
        Map<String, Object> result = multiFieldConverter.extract(multiField, input);

        // Assert
        assertNotNull(result);
        assertEquals("John Doe", result.get("Name"));
        assertEquals("john.doe@example.com", result.get("Email"));
    }

    @Test
    public void testMultiFieldConverter_WithNullInput_ReturnsEmptyMap() throws Exception {
        // Act
        Map<String, Object> result = multiFieldConverter.extract(multiField, null);

        // Assert
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    public void testMultiFieldConverter_WithInvalidInput_ThrowsException() throws Exception {
        // Arrange
        String invalidInput = "not a map";

        // Act & Assert
        Exception exception = assertThrows(Exception.class, () -> {
            multiFieldConverter.extract(multiField, invalidInput);
        });
        assertTrue(exception.getMessage().contains("Failed to extract MultiField value: input must be a Map"));
    }

    @Test
    public void testListConverter_WithValidInput_ReturnsExtractedList() throws Exception {
        // Arrange
        List<String> input = List.of("Java", "Python", "JavaScript");

        // Act
        List<?> result = listConverter.extract(listField, input);

        // Assert
        assertNotNull(result);
        assertEquals(3, result.size());
        assertEquals("Java", result.get(0));
        assertEquals("Python", result.get(1));
        assertEquals("JavaScript", result.get(2));
    }

    @Test
    public void testListConverter_WithNullInput_ReturnsEmptyList() throws Exception {
        // Act
        List<?> result = listConverter.extract(listField, null);

        // Assert
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    public void testListConverter_WithInvalidInput_ThrowsException() throws Exception {
        // Arrange
        String invalidInput = "not a list";

        // Act & Assert
        Exception exception = assertThrows(Exception.class, () -> {
            listConverter.extract(listField, invalidInput);
        });
        assertTrue(exception.getMessage().contains("Failed to extract List value from type"));
    }

    @Test
    public void testListConverter_WithFieldObject_ReturnsExtractedList() throws Exception {
        // Arrange - Create a list field with a Field object (not NamedField) as element
        Field elementField = mock(Field.class);
        when(elementField.getFieldType()).thenReturn("com.krista.fields.Text");
        when(elementField.getAttributeValues()).thenReturn(Map.of());

        Field listFieldWithField = mock(Field.class);
        when(listFieldWithField.getFieldType()).thenReturn("com.krista.fields.List");
        when(listFieldWithField.getAttributeValues()).thenReturn(Map.of("field", elementField));

        List<String> input = List.of("Java", "Python", "JavaScript");

        // Act
        List<?> result = listConverter.extract(listFieldWithField, input);

        // Assert
        assertNotNull(result);
        assertEquals(3, result.size());
        assertEquals("Java", result.get(0));
        assertEquals("Python", result.get(1));
        assertEquals("JavaScript", result.get(2));
    }

    @Test
    public void testConverters_WithNullInput_HandleGracefully() throws Exception {
        // Test that converters handle null input appropriately
        try {
            textConverter.extract(textField, null);
            fail("Should have thrown exception for null input");
        } catch (Exception e) {
            assertTrue(e.getMessage().contains("Failed to extract text value"));
        }

        try {
            numberConverter.extract(numberField, null);
            fail("Should have thrown exception for null input");
        } catch (Exception e) {
            assertTrue(e.getMessage().contains("Failed to extract number value"));
        }

        try {
            dateConverter.extract(dateField, null);
            fail("Should have thrown exception for null input");
        } catch (Exception e) {
            assertTrue(e.getMessage().contains("Failed to extract Date value"));
        }

        try {
            ratingConverter.extract(ratingField, null);
            fail("Should have thrown exception for null input");
        } catch (Exception e) {
            assertTrue(e.getMessage().contains("Failed to extract Rating value"));
        }

        try {
            switchConverter.extract(switchField, null);
            fail("Should have thrown exception for null input");
        } catch (Exception e) {
            assertTrue(e.getMessage().contains("Failed to extract Switch value"));
        }

        try {
            phoneConverter.extract(phoneField, null);
            fail("Should have thrown exception for null input");
        } catch (Exception e) {
            assertTrue(e.getMessage().contains("Failed to extract Phone number"));
        }
    }

    @Test
    public void testComplexMultiFieldConverter_WithNestedFieldsAndList_ReturnsExtractedValues() throws Exception {
        // Arrange - Setup complex field structure
        when(skillItemField.getName()).thenReturn("Skill");
        when(skillItemField.getFieldType()).thenReturn("com.krista.fields.Text");
        when(skillItemField.getAttributeValues()).thenReturn(Map.of());

        when(skillsListField.getName()).thenReturn("Skills");
        when(skillsListField.getFieldType()).thenReturn("com.krista.fields.List");
        when(skillsListField.getAttributeValues()).thenReturn(Map.of("field", skillItemField));

        when(personalInfoField.getName()).thenReturn("PersonalInfo");
        when(personalInfoField.getFieldType()).thenReturn("com.krista.fields.MultiField");
        when(personalInfoField.getAttributeValues()).thenReturn(
                Map.of("fields", List.of(nestedTextField, nestedEmailField)));

        // Create a NamedField for rating since MultiField needs named fields
        NamedField namedRatingField = mock(NamedField.class);
        when(namedRatingField.getName()).thenReturn("Rating");
        when(namedRatingField.getFieldType()).thenReturn("com.krista.fields.Rating");
        when(namedRatingField.getAttributeValues()).thenReturn(Map.of());

        when(complexRootField.getName()).thenReturn("EmployeeProfile");
        when(complexRootField.getFieldType()).thenReturn("com.krista.fields.MultiField");
        when(complexRootField.getAttributeValues()).thenReturn(
                Map.of("fields", List.of(personalInfoField, skillsListField, namedRatingField)));

        // Complex input with nested structure
        Map<String, Object> input = Map.of(
                "PersonalInfo", Map.of(
                        "Name", "Alice Johnson",
                        "Email", "alice.johnson@company.com"
                ),
                "Skills", List.of("Java", "Python", "React"),
                "Rating", 5
        );

        // Act
        Map<String, Object> result = multiFieldConverter.extract(complexRootField, input);

        // Assert
        assertNotNull(result);

        // Verify PersonalInfo extraction (nested MultiField)
        assertTrue(result.containsKey("PersonalInfo"));
        Map<String, Object> personalInfo = (Map<String, Object>) result.get("PersonalInfo");
        assertNotNull(personalInfo);
        assertEquals("Alice Johnson", personalInfo.get("Name"));
        assertEquals("alice.johnson@company.com", personalInfo.get("Email"));

        // Verify Skills list extraction
        assertTrue(result.containsKey("Skills"));
        List<?> skills = (List<?>) result.get("Skills");
        assertNotNull(skills);
        assertEquals(3, skills.size());
        assertEquals("Java", skills.get(0));
        assertEquals("Python", skills.get(1));
        assertEquals("React", skills.get(2));

        // Verify Rating extraction
        assertTrue(result.containsKey("Rating"));
        assertEquals(Integer.valueOf(5), result.get("Rating"));

        // Verify total field count at root level
        assertEquals(3, result.size());
    }
}
