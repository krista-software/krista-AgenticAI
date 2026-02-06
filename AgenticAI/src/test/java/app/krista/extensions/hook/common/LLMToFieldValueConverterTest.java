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

import app.krista.extensions.hook.common.llmToField.LLMToFieldValueConverterRegistry;
import app.krista.extensions.hook.common.llmToField.impl.*;
import app.krista.model.base.DateRange;
import app.krista.model.base.PhoneNumber;
import app.krista.model.field.Field;
import app.krista.model.field.NamedField;
import org.junit.Test;
import org.junit.Before;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

@SuppressWarnings("unchecked")
public class LLMToFieldValueConverterTest {

    private MultiFieldLLMToFieldValueConverter multiFieldConverter;
    private ListLLMToFieldValueConverter listConverter;
    private DateLLMToFieldValueConverter dateConverter;
    private DateRangeLLMToFieldValueConverter dateRangeConverter;
    private PhoneNumberLLMToFieldValueConverter phoneConverter;
    private CheckboxLLMToFieldValueConverter checkboxConverter;
    private TimeLLMToFieldValueConverter timeConverter;
    private RatingLLMToFieldValueConverter ratingConverter;
    private SwitchLLMToFieldValueConverter switchConverter;

    @Mock
    private Field multiField;
    @Mock
    private Field listField;
    @Mock
    private Field dateField;
    @Mock
    private Field dateRangeField;
    @Mock
    private Field phoneField;
    @Mock
    private Field checkboxField;
    @Mock
    private Field timeField;
    @Mock
    private Field ratingField;
    @Mock
    private Field switchField;
    @Mock
    private Field nestedTextField;
    @Mock
    private Field nestedEmailField;

    // NamedField versions for use in MultiField contexts
    @Mock
    private NamedField namedTextField;
    @Mock
    private NamedField namedEmailField;
    @Mock
    private NamedField namedPhoneField;
    @Mock
    private NamedField namedCheckboxField;
    @Mock
    private NamedField namedRatingField;

    // Additional mock fields for complex 3-level deep test
    @Mock
    private Field complexRootField;
    @Mock
    private NamedField level1PersonalInfoField;
    @Mock
    private NamedField level1WorkExperienceField;
    @Mock
    private NamedField level2AddressField;
    @Mock
    private NamedField level2ProjectsListField;
    @Mock
    private NamedField level3StreetField;
    @Mock
    private NamedField level3CityField;
    @Mock
    private NamedField projectItemField;

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);

        // Initialize registry and converters
        LLMToFieldValueConverterRegistry registry = LLMToFieldValueConverterRegistry.create();
        multiFieldConverter = new MultiFieldLLMToFieldValueConverter(registry);
        listConverter = new ListLLMToFieldValueConverter(registry);
        dateConverter = new DateLLMToFieldValueConverter();
        dateRangeConverter = new DateRangeLLMToFieldValueConverter();
        phoneConverter = new PhoneNumberLLMToFieldValueConverter();
        checkboxConverter = new CheckboxLLMToFieldValueConverter();
        timeConverter = new TimeLLMToFieldValueConverter();
        ratingConverter = new RatingLLMToFieldValueConverter();
        switchConverter = new SwitchLLMToFieldValueConverter();

        createTestFieldObjects();
    }

    private void createTestFieldObjects() {
        // MultiField: Contact Details with nested Phone and Email fields
        when(multiField.getFieldType()).thenReturn("com.krista.fields.MultiField");

        // Create nested fields for MultiField
        when(nestedTextField.getFieldType()).thenReturn("com.krista.fields.Text");
        when(nestedTextField.getAttributeValues()).thenReturn(Map.of());

        when(nestedEmailField.getFieldType()).thenReturn("com.krista.fields.Email");
        when(nestedEmailField.getAttributeValues()).thenReturn(Map.of());

        // Setup NamedField versions for MultiField use
        when(namedTextField.getName()).thenReturn("Name");
        when(namedTextField.getFieldType()).thenReturn("com.krista.fields.Text");
        when(namedTextField.getAttributeValues()).thenReturn(Map.of());

        when(namedEmailField.getName()).thenReturn("Email");
        when(namedEmailField.getFieldType()).thenReturn("com.krista.fields.Email");
        when(namedEmailField.getAttributeValues()).thenReturn(Map.of());

        when(namedPhoneField.getName()).thenReturn("Phone Number");
        when(namedPhoneField.getFieldType()).thenReturn("com.krista.fields.Phone");
        when(namedPhoneField.getAttributeValues()).thenReturn(
                Map.of("countryCode", 91, "formatPattern", "+91 ##### #####"));

        when(namedCheckboxField.getName()).thenReturn("Skills");
        when(namedCheckboxField.getFieldType()).thenReturn("com.krista.fields.Checkboxes");
        when(namedCheckboxField.getAttributeValues()).thenReturn(
                Map.of("options", List.of("Java", "Python", "JavaScript")));

        when(namedRatingField.getName()).thenReturn("Rating");
        when(namedRatingField.getFieldType()).thenReturn("com.krista.fields.Rating");
        when(namedRatingField.getAttributeValues()).thenReturn(
                Map.of("maxRating", 5, "minRating", 1));

        when(multiField.getAttributeValues()).thenReturn(
                Map.of("fields", List.of(namedTextField, namedEmailField)));

        // List field: List of text items
        when(listField.getFieldType()).thenReturn("com.krista.fields.List");
        when(listField.getAttributeValues()).thenReturn(
                Map.of("field", nestedTextField));

        // Date field: Date input
        when(dateField.getFieldType()).thenReturn("com.krista.fields.Date");
        when(dateField.getAttributeValues()).thenReturn(Map.of());

        // DateRange field: Available Days
        when(dateRangeField.getFieldType()).thenReturn("com.krista.fields.DateRange");
        when(dateRangeField.getAttributeValues()).thenReturn(
                Map.of("allowPast", true, "allowToday", true, "allowFuture", true));

        // Phone field: Phone number with country code
        when(phoneField.getFieldType()).thenReturn("com.krista.fields.Phone");
        when(phoneField.getAttributeValues()).thenReturn(
                Map.of("countryCode", 91, "formatPattern", "+91 ##### #####"));

        // Checkbox field: Multiple selection
        when(checkboxField.getFieldType()).thenReturn("com.krista.fields.Checkboxes");
        when(checkboxField.getAttributeValues()).thenReturn(
                Map.of("options", List.of("Java", "Python", "JavaScript")));

        // Time field: Time input
        when(timeField.getFieldType()).thenReturn("com.krista.fields.Time");
        when(timeField.getAttributeValues()).thenReturn(Map.of());

        // Rating field: Star rating
        when(ratingField.getFieldType()).thenReturn("com.krista.fields.Rating");
        when(ratingField.getAttributeValues()).thenReturn(
                Map.of("maxRating", 5, "minRating", 1));

        // Switch field: Boolean toggle
        when(switchField.getFieldType()).thenReturn("com.krista.fields.Switch");
        when(switchField.getAttributeValues()).thenReturn(Map.of());

        // Setup complex 3-level deep field structure
        setupComplexFieldStructure();
    }

    private void setupComplexFieldStructure() {
        // Level 3 fields (deepest level) - these need to be NamedField for MultiField use
        when(level3StreetField.getName()).thenReturn("Street");
        when(level3StreetField.getFieldType()).thenReturn("com.krista.fields.Text");
        when(level3StreetField.getAttributeValues()).thenReturn(Map.of());

        when(level3CityField.getName()).thenReturn("City");
        when(level3CityField.getFieldType()).thenReturn("com.krista.fields.Text");
        when(level3CityField.getAttributeValues()).thenReturn(Map.of());

        // Project item field for list - this needs to be NamedField for List use
        when(projectItemField.getName()).thenReturn("Project");
        when(projectItemField.getFieldType()).thenReturn("com.krista.fields.Text");
        when(projectItemField.getAttributeValues()).thenReturn(Map.of());

        // Level 2 fields (middle level)
        when(level2AddressField.getName()).thenReturn("Address");
        when(level2AddressField.getFieldType()).thenReturn("com.krista.fields.MultiField");
        when(level2AddressField.getAttributeValues()).thenReturn(
                Map.of("fields", List.of(level3StreetField, level3CityField)));

        when(level2ProjectsListField.getName()).thenReturn("Projects");
        when(level2ProjectsListField.getFieldType()).thenReturn("com.krista.fields.List");
        when(level2ProjectsListField.getAttributeValues()).thenReturn(
                Map.of("field", projectItemField));

        // Level 1 fields (second level)
        when(level1PersonalInfoField.getName()).thenReturn("PersonalInfo");
        when(level1PersonalInfoField.getFieldType()).thenReturn("com.krista.fields.MultiField");
        when(level1PersonalInfoField.getAttributeValues()).thenReturn(
                Map.of("fields", List.of(namedTextField, level2AddressField)));

        when(level1WorkExperienceField.getName()).thenReturn("WorkExperience");
        when(level1WorkExperienceField.getFieldType()).thenReturn("com.krista.fields.MultiField");
        when(level1WorkExperienceField.getAttributeValues()).thenReturn(
                Map.of("fields", List.of(level2ProjectsListField, namedRatingField)));

        // Root field (top level) - contains 7 fields total:
        // 1. PersonalInfo (MultiField containing Name and Address)
        // 2. WorkExperience (MultiField containing Projects list and Rating)
        // 3. Phone (simple field)
        // 4. Email (simple field)
        // 5. Skills (simple field)
        when(complexRootField.getFieldType()).thenReturn("com.krista.fields.MultiField");
        when(complexRootField.getAttributeValues()).thenReturn(
                Map.of("fields", List.of(
                        level1PersonalInfoField,
                        level1WorkExperienceField,
                        namedPhoneField,
                        namedEmailField,
                        namedCheckboxField)));
    }

    @Test
    public void testMultiFieldConverter_WithValidInput_ReturnsExtractedValues() {
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
    public void testMultiFieldConverter_WithInvalidInput_ReturnsNull() {
        // Arrange
        String invalidInput = "not a map";

        // Act
        Map<String, Object> result = multiFieldConverter.extract(multiField, invalidInput);

        // Assert
        assertNull("Should return null for invalid input", result);
    }

    @Test
    public void testListConverter_WithValidInput_ReturnsExtractedList() {
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
    public void testListConverter_WithInvalidInput_ReturnsNull() {
        // Arrange
        String invalidInput = "not a list";

        // Act
        List<?> result = listConverter.extract(listField, invalidInput);

        // Assert
        assertNull("Should return null for invalid input", result);
    }

    @Test
    public void testDateRangeConverter_WithValidInput_ReturnsDateRange() {
        // Arrange
        Map<String, Object> input = Map.of(
                "startOfDateRange", "01/01/2024 10:00:00",
                "endOfDateRange", "05/01/2024 18:00:00"
        );

        // Act
        DateRange result = dateRangeConverter.extract(dateRangeField, input);

        // Assert
        assertNotNull(result);
        assertNotNull(result.getFrom());
        assertNotNull(result.getTo());
        assertTrue(result.getFrom() < result.getTo());
    }

    @Test
    public void testDateRangeConverter_WithMissingFields_ReturnsNull() {
        // Arrange
        Map<String, Object> input = Map.of("startOfDateRange", "01/01/2024 10:00:00");

        // Act
        DateRange result = dateRangeConverter.extract(dateRangeField, input);

        // Assert
        assertNull("Should return null for missing required fields", result);
    }

    @Test
    public void testDateRangeConverter_WithInvalidDateFormat_ReturnsNull() {
        // Arrange
        Map<String, Object> input = Map.of(
                "startOfDateRange", "invalid-date",
                "endOfDateRange", "05/01/2024 18:00:00"
        );

        // Act
        DateRange result = dateRangeConverter.extract(dateRangeField, input);

        // Assert
        assertNull("Should return null for invalid date format", result);
    }

    @Test
    public void testDateRangeConverter_WithMonthBeforeDayFormat_ReturnsDateRange() {
        // Arrange - Test MM/dd/yyyy format (month before day)
        Map<String, Object> input = Map.of(
                "startOfDateRange", "01/15/2024 10:00:00", // January 15, 2024
                "endOfDateRange", "01/20/2024 18:00:00"   // January 20, 2024
        );

        // Act
        DateRange result = dateRangeConverter.extract(dateRangeField, input);

        // Assert
        assertNotNull("Should successfully parse MM/dd/yyyy format", result);
        assertNotNull(result.getFrom());
        assertNotNull(result.getTo());
        assertTrue(result.getFrom() < result.getTo());
    }

    @Test
    public void testDateRangeConverter_WithMixedDateFormats_ReturnsDateRange() {
        // Arrange - Test mixed formats: dd/MM/yyyy and MM/dd/yyyy
        Map<String, Object> input = Map.of(
                "startOfDateRange", "15/01/2024", // dd/MM/yyyy format (15th January)
                "endOfDateRange", "01/20/2024"   // MM/dd/yyyy format (20th January)
        );

        // Act
        DateRange result = dateRangeConverter.extract(dateRangeField, input);

        // Assert
        assertNotNull("Should successfully parse mixed date formats", result);
        assertNotNull(result.getFrom());
        assertNotNull(result.getTo());
        assertTrue(result.getFrom() < result.getTo());
    }

    @Test
    public void testDateConverter_WithValidDayMonthFormat_ReturnsTimestamp() {
        // Arrange - Test dd/MM/yyyy format (primary format)
        String dateInput = "15/01/2024 14:30:00"; // 15th January 2024, 2:30 PM

        // Act
        Long result = dateConverter.extract(dateField, dateInput);

        // Assert
        assertNotNull("Should successfully parse dd/MM/yyyy HH:mm:ss format", result);
        assertTrue("Result should be a valid timestamp", result > 0);
    }

    @Test
    public void testDateConverter_WithValidMonthDayFormat_ReturnsTimestamp() {
        // Arrange - Test MM/dd/yyyy format (fallback format)
        String dateInput = "01/15/2024 14:30:00"; // January 15th 2024, 2:30 PM

        // Act
        Long result = dateConverter.extract(dateField, dateInput);

        // Assert
        assertNotNull("Should successfully parse MM/dd/yyyy HH:mm:ss format", result);
        assertTrue("Result should be a valid timestamp", result > 0);
    }

    @Test
    public void testDateConverter_WithDateOnlyDayMonthFormat_ReturnsTimestamp() {
        // Arrange - Test dd/MM/yyyy format without time (primary format)
        String dateInput = "15/01/2024"; // 15th January 2024

        // Act
        Long result = dateConverter.extract(dateField, dateInput);

        // Assert
        assertNotNull("Should successfully parse dd/MM/yyyy format", result);
        assertTrue("Result should be a valid timestamp", result > 0);
    }

    @Test
    public void testDateConverter_WithDateOnlyMonthDayFormat_ReturnsTimestamp() {
        // Arrange - Test MM/dd/yyyy format without time (fallback format)
        String dateInput = "01/15/2024"; // January 15th 2024

        // Act
        Long result = dateConverter.extract(dateField, dateInput);

        // Assert
        assertNotNull("Should successfully parse MM/dd/yyyy format", result);
        assertTrue("Result should be a valid timestamp", result > 0);
    }

    @Test
    public void testDateConverter_WithTimezone_ReturnsCorrectTimestamp() {
        // Arrange
        String dateInput = "15/01/2024 14:30:00";

        // Mock field with timezone
        when(dateField.getAttributeValues()).thenReturn(Map.of("timezone", "UTC"));

        // Act
        Long result = dateConverter.extract(dateField, dateInput);

        // Assert
        assertNotNull("Should successfully parse date with timezone", result);
        assertTrue("Result should be a valid timestamp", result > 1000000000000L); // After year 2001
    }

    @Test
    public void testDateConverter_WithInvalidFormat_ReturnsNull() {
        // Arrange
        String invalidDate = "invalid-date-format";

        // Act
        Long result = dateConverter.extract(dateField, invalidDate);

        // Assert
        assertNull("Should return null for invalid date format", result);
    }

    @Test
    public void testDateConverter_WithInvalidInput_ReturnsNull() {
        // Arrange
        Integer invalidInput = 12345; // Not a string

        // Act
        Long result = dateConverter.extract(dateField, invalidInput);

        // Assert
        assertNull("Should return null for non-string input", result);
    }

    @Test
    public void testDateConverter_WithNullInput_ReturnsNull() {
        // Arrange
        String nullInput = null;

        // Act
        Long result = dateConverter.extract(dateField, nullInput);

        // Assert
        assertNull("Should return null for null input", result);
    }

    @Test
    public void testPhoneConverter_WithValidInput_ReturnsPhoneNumber() {
        // Arrange
        Long phoneNumber = 9876543210L;

        // Act
        PhoneNumber result = phoneConverter.extract(phoneField, phoneNumber);

        // Assert
        assertNotNull(result);
        assertEquals(91, result.getCountryCode());
        assertEquals(9876543210L, result.getNationalNumber());
    }

    @Test
    public void testPhoneConverter_WithInvalidInput_ReturnsNull() {
        // Arrange
        String invalidInput = "not a number";

        // Act
        PhoneNumber result = phoneConverter.extract(phoneField, invalidInput);

        // Assert
        assertNull("Should return null for invalid input", result);
    }

    @Test
    public void testPhoneConverter_WithStringInput_ReturnsPhoneNumber() {
        // Arrange
        String phoneNumber = "9876543210";

        // Act
        PhoneNumber result = phoneConverter.extract(phoneField, phoneNumber);

        // Assert
        assertNotNull(result);
        assertEquals(91, result.getCountryCode());
        assertEquals(9876543210L, result.getNationalNumber());
    }

    @Test
    public void testPhoneConverter_WithFormattedStringInput_ReturnsPhoneNumber() {
        // Arrange - LLM should only provide national number, not full international format
        String phoneNumber = "98765 43210"; // National number only, formatted

        // Act
        PhoneNumber result = phoneConverter.extract(phoneField, phoneNumber);

        // Assert
        assertNotNull(result);
        assertEquals(91, result.getCountryCode()); // Country code from formatPattern
        assertEquals(9876543210L, result.getNationalNumber());
    }

    @Test
    public void testPhoneConverter_WithFormatPatternOnly_ReturnsPhoneNumber() {
        // Arrange
        Field fieldWithFormatPatternOnly = mock(Field.class);
        when(fieldWithFormatPatternOnly.getFieldType()).thenReturn("com.krista.fields.Phone");
        when(fieldWithFormatPatternOnly.getAttributeValues()).thenReturn(
                Map.of("formatPattern", "+1 ##### #####"));

        Long phoneNumber = 5551234567L;

        // Act
        PhoneNumber result = phoneConverter.extract(fieldWithFormatPatternOnly, phoneNumber);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.getCountryCode());
        assertEquals(5551234567L, result.getNationalNumber());
    }

    @Test
    public void testPhoneConverter_WithInvalidFormatPattern_FallsBackToCountryCode() {
        // Arrange
        Field fieldWithInvalidPattern = mock(Field.class);
        when(fieldWithInvalidPattern.getFieldType()).thenReturn("com.krista.fields.Phone");
        when(fieldWithInvalidPattern.getAttributeValues()).thenReturn(
                Map.of("formatPattern", "invalid pattern", "countryCode", 44));

        Long phoneNumber = 7700900123L;

        // Act
        PhoneNumber result = phoneConverter.extract(fieldWithInvalidPattern, phoneNumber);

        // Assert
        assertNotNull(result);
        assertEquals(44, result.getCountryCode());
        assertEquals(7700900123L, result.getNationalNumber());
    }

    @Test
    public void testPhoneConverter_WithNoCountryCodeInfo_UsesDefaultCountryCode() {
        // Arrange
        Field fieldWithoutCountryCode = mock(Field.class);
        when(fieldWithoutCountryCode.getFieldType()).thenReturn("com.krista.fields.Phone");
        when(fieldWithoutCountryCode.getAttributeValues()).thenReturn(Map.of());

        Long phoneNumber = 9876543210L;

        // Act
        PhoneNumber result = phoneConverter.extract(fieldWithoutCountryCode, phoneNumber);

        // Assert
        assertNotNull("Should return PhoneNumber with default country code when no country code information is available", result);
        assertEquals(0, result.getCountryCode()); // Default US country code
        assertEquals(9876543210L, result.getNationalNumber());
    }

    @Test
    public void testCheckboxConverter_WithValidInput_ReturnsStringList() {
        // Arrange
        List<String> input = List.of("Java", "Python");

        // Act
        List<String> result = checkboxConverter.extract(checkboxField, input);

        // Assert
        assertNotNull(result);
        assertEquals(2, result.size());
        assertEquals("Java", result.get(0));
        assertEquals("Python", result.get(1));
    }

    @Test
    public void testCheckboxConverter_WithInvalidInput_ReturnsNull() {
        // Arrange
        String invalidInput = "not a list";

        // Act
        List<String> result = checkboxConverter.extract(checkboxField, invalidInput);

        // Assert
        assertNull("Should return null for invalid input", result);
    }

    @Test
    public void testTimeConverter_WithValidInput_ReturnsTimeInMilliseconds() {
        // Arrange
        String timeInput = "14:30:00";

        // Act
        Long result = timeConverter.extract(timeField, timeInput);

        // Assert
        assertNotNull(result);
        assertTrue(result > 0);
        // With timezone handling, the result should be a full timestamp for today's date at 14:30:00
        // We can't predict the exact value since it depends on today's date, but we can verify
        // it's a reasonable timestamp (should be much larger than milliseconds since midnight)
        assertTrue("Result should be a full timestamp, not just milliseconds since midnight",
                   result > 24 * 60 * 60 * 1000L); // Should be larger than 24 hours in milliseconds
    }

    @Test
    public void testTimeConverter_WithTimezone_ReturnsCorrectTimestamp() {
        // Arrange
        String timeInput = "14:30:00";

        // Mock field with timezone
        when(timeField.getAttributeValues()).thenReturn(Map.of("timezone", "UTC"));

        // Act
        Long result = timeConverter.extract(timeField, timeInput);

        // Assert
        assertNotNull(result);
        assertTrue(result > 0);
        // Should be a full timestamp for today at 14:30:00 UTC
        assertTrue("Result should be a full timestamp", result > 1000000000000L); // After year 2001
    }

    @Test
    public void testTimeConverter_WithInvalidFormat_ReturnsNull() {
        // Arrange
        String invalidTime = "25:70:90"; // Invalid time

        // Act
        Long result = timeConverter.extract(timeField, invalidTime);

        // Assert
        assertNull("Should return null for invalid time format", result);
    }

    @Test
    public void testRatingConverter_WithValidInput_ReturnsInteger() {
        // Arrange
        Integer rating = 4;

        // Act
        Integer result = ratingConverter.extract(ratingField, rating);

        // Assert
        assertNotNull(result);
        assertEquals(Integer.valueOf(4), result);
    }

    @Test
    public void testRatingConverter_WithInvalidInput_ReturnsNull() {
        // Arrange
        String invalidInput = "not a number";

        // Act
        Integer result = ratingConverter.extract(ratingField, invalidInput);

        // Assert
        assertNull("Should return null for invalid input", result);
    }

    @Test
    public void testSwitchConverter_WithValidInput_ReturnsBoolean() {
        // Arrange
        Boolean switchValue = true;

        // Act
        Boolean result = switchConverter.extract(switchField, switchValue);

        // Assert
        assertNotNull(result);
        assertTrue(result);
    }

    @Test
    public void testSwitchConverter_WithInvalidInput_ReturnsNull() {
        // Arrange
        String invalidInput = "not a boolean";

        // Act
        Boolean result = switchConverter.extract(switchField, invalidInput);

        // Assert
        assertNull("Should return null for invalid input", result);
    }

    @Test
    public void testConverters_WithNullInput_HandleGracefully() {
        // Test that converters handle null input appropriately

        // MultiField converter should return null for null input
        Map<String, Object> multiFieldResult = multiFieldConverter.extract(multiField, null);
        assertNull("MultiField converter should return null for null input", multiFieldResult);

        // Date converter should return null for null input
        Long dateResult = dateConverter.extract(dateField, null);
        assertNull("Date converter should return null for null input", dateResult);

        // DateRange converter should return null for null input
        DateRange dateRangeResult = dateRangeConverter.extract(dateRangeField, null);
        assertNull("DateRange converter should return null for null input", dateRangeResult);

        // Phone converter should return null for null input
        PhoneNumber phoneResult = phoneConverter.extract(phoneField, null);
        assertNull("Phone converter should return null for null input", phoneResult);
    }

    @Test
    public void testComplexMultiFieldConverter_With3LevelDeepFieldsAndList_ReturnsExtractedValues() {
        // Arrange - Complex input with 3-level deep structure including list
        Map<String, Object> input = Map.of(
                // Level 1: PersonalInfo (contains Name and Address)
                "PersonalInfo", Map.of(
                        "Name", "Alice Johnson",
                        // Level 2: Address (contains Street and City)
                        "Address", Map.of(
                                "Street", "123 Main Street",
                                "City", "San Francisco"
                        )
                ),
                // Level 1: WorkExperience (contains Projects list and Rating)
                "WorkExperience", Map.of(
                        // Level 2: Projects (list of project names)
                        "Projects", List.of("E-commerce Platform", "Mobile App", "Data Analytics"),
                        "Rating", 4
                ),
                // Simple fields at root level
                "Phone Number", 9876543210L,
                "Email", "alice.johnson@company.com",
                "Skills", List.of("Java", "Python", "React")
        );

        // Act
        Map<String, Object> result = multiFieldConverter.extract(complexRootField, input);

        // Assert
        assertNotNull(result);

        // Verify PersonalInfo extraction (Level 1)
        assertTrue(result.containsKey("PersonalInfo"));
        Map<String, Object> personalInfo = (Map<String, Object>) result.get("PersonalInfo");
        assertNotNull(personalInfo);
        assertEquals("Alice Johnson", personalInfo.get("Name"));

        // Verify Address extraction (Level 2 within PersonalInfo)
        assertTrue(personalInfo.containsKey("Address"));
        Map<String, Object> address = (Map<String, Object>) personalInfo.get("Address");
        assertNotNull(address);
        assertEquals("123 Main Street", address.get("Street"));
        assertEquals("San Francisco", address.get("City"));

        // Verify WorkExperience extraction (Level 1)
        assertTrue(result.containsKey("WorkExperience"));
        Map<String, Object> workExperience = (Map<String, Object>) result.get("WorkExperience");
        assertNotNull(workExperience);

        // Verify Projects list extraction (Level 2 within WorkExperience)
        assertTrue(workExperience.containsKey("Projects"));
        List<?> projects = (List<?>) workExperience.get("Projects");
        assertNotNull(projects);
        assertEquals(3, projects.size());
        assertEquals("E-commerce Platform", projects.get(0));
        assertEquals("Mobile App", projects.get(1));
        assertEquals("Data Analytics", projects.get(2));

        // Verify Rating extraction (Level 2 within WorkExperience)
        assertTrue(workExperience.containsKey("Rating"));
        assertEquals(Integer.valueOf(4), workExperience.get("Rating"));

        // Verify simple fields at root level
        assertTrue(result.containsKey("Phone Number"));
        assertNotNull(result.get("Phone Number"));

        assertTrue(result.containsKey("Email"));
        assertEquals("alice.johnson@company.com", result.get("Email"));

        assertTrue(result.containsKey("Skills"));
        List<String> skills = (List<String>) result.get("Skills");
        assertNotNull(skills);
        assertEquals(3, skills.size());
        assertEquals("Java", skills.get(0));
        assertEquals("Python", skills.get(1));
        assertEquals("React", skills.get(2));

        // Verify total field count (7 fields as requested)
        // 1. PersonalInfo, 2. WorkExperience, 3. Phone Number, 4. Email, 5. Skills
        // Plus nested: 6. Name, 7. Address (with Street/City), 8. Projects, 9. Rating
        assertEquals(5, result.size()); // Root level fields
    }
}
