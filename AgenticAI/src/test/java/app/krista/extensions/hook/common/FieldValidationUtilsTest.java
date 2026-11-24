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

import app.krista.extensions.hook.common.validator.FieldValidatorRegistry;
import app.krista.extensions.hook.common.validator.impl.*;
import app.krista.model.field.NamedField;
import app.krista.model.base.DateRange;
import app.krista.model.base.PhoneNumber;
import org.junit.Test;
import org.junit.Before;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;
import java.util.Map;
import java.util.Arrays;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

@SuppressWarnings("unchecked")
public class FieldValidationUtilsTest {

    private TextFieldValidator textFieldValidator;
    private EmailValidator emailValidator;
    private NumberFieldValidator numberFieldValidator;
    private PickOneValidator pickOneValidator;
    private CheckboxesValidator checkboxesValidator;
    private IdentifierValidator identifierValidator;
    private DateValidator dateValidator;
    private DateRangeValidator dateRangeValidator;
    private DateTimeRangeValidator dateTimeRangeValidator;
    private TimeValidator timeValidator;
    private SwitchValidator switchValidator;
    private RatingValidator ratingValidator;
    private PhoneValidator phoneValidator;
    private MultiFieldValidator multiFieldValidator;

    @Mock
    private NamedField nameField;
    @Mock
    private NamedField emailField;
    @Mock
    private NamedField phoneField;
    @Mock
    private NamedField panField;
    @Mock
    private NamedField addressField;
    @Mock
    private NamedField numberField;
    @Mock
    private NamedField currencyField;
    @Mock
    private NamedField dropdownField;
    @Mock
    private NamedField checkboxField;
    @Mock
    private NamedField checkboxFieldMinSelection;
    @Mock
    private NamedField identifierField;
    @Mock
    private NamedField dateField;
    @Mock
    private NamedField dateRangeField;
    @Mock
    private NamedField dateTimeRangeField;
    @Mock
    private NamedField timeField;
    @Mock
    private NamedField switchField;
    @Mock
    private NamedField ratingField;
    @Mock
    private NamedField phoneValidatorField;
    @Mock
    private NamedField dateLLMField;

    // Complex 3-level deep multifield structure with 7 total fields
    @Mock
    private NamedField complexRootMultiField;
    @Mock
    private NamedField level1PersonalInfoField;
    @Mock
    private NamedField level1WorkInfoField;
    @Mock
    private NamedField level2AddressField;
    @Mock
    private NamedField level2ProjectsField;
    @Mock
    private NamedField level3StreetField;
    @Mock
    private NamedField level3CityField;
    @Mock
    private NamedField level3ProjectNameField;
    @Mock
    private NamedField simpleEmailField;
    @Mock
    private NamedField simplePhoneField;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.openMocks(this);

        // Initialize validators
        textFieldValidator = new TextFieldValidator();
        emailValidator = new EmailValidator();
        numberFieldValidator = new NumberFieldValidator();
        pickOneValidator = new PickOneValidator();
        checkboxesValidator = new CheckboxesValidator();
        identifierValidator = new IdentifierValidator();
        dateValidator = new DateValidator();
        dateRangeValidator = new DateRangeValidator();
        dateTimeRangeValidator = new DateTimeRangeValidator();
        timeValidator = new TimeValidator();
        switchValidator = new SwitchValidator();
        ratingValidator = new RatingValidator();
        phoneValidator = new PhoneValidator();
        multiFieldValidator = new MultiFieldValidator(FieldValidatorRegistry.create());

        createTestFieldObjects();
    }

    private void createTestFieldObjects() throws Exception {
        // Create mock NamedField objects for testing individual validators
        // This approach tests the validation logic directly without needing FreeForm

        // Name field: Text field with maxLength constraint
        when(nameField.getName()).thenReturn("Name");
        when(nameField.getFieldType()).thenReturn("com.krista.fields.Text");
        when(nameField.getAttributeValues()).thenReturn(
            Map.of("visualWidth", "S", "trim", true, "maxLength", 20));

        // Email field: Email field with domain validation
        when(emailField.getName()).thenReturn("Email");
        when(emailField.getFieldType()).thenReturn("com.krista.fields.Email");
        when(emailField.getAttributeValues()).thenReturn(
            Map.of("visualWidth", "S", "allowedDomains", List.of("kristasoft.com", "kristaai.com")));

        // Phone field: Phone field with country code
        when(phoneField.getName()).thenReturn("Phone number");
        when(phoneField.getFieldType()).thenReturn("com.krista.fields.Phone");
        when(phoneField.getAttributeValues()).thenReturn(
            Map.of("countryCode", 91, "visualWidth", "M"));

        // PAN field: Text field with min/max length constraints
        when(panField.getName()).thenReturn("PAN");
        when(panField.getFieldType()).thenReturn("com.krista.fields.Text");
        when(panField.getAttributeValues()).thenReturn(
            Map.of("visualWidth", "S", "trim", true, "minLength", 2, "maxLength", 20));

        // Address field: Paragraph field with line count and length constraints
        when(addressField.getName()).thenReturn("Permanenet Address");
        when(addressField.getFieldType()).thenReturn("com.krista.fields.Paragraph");
        when(addressField.getAttributeValues()).thenReturn(
            Map.of("visualWidth", "L", "maxLineCount", 5, "minLength", 15));

        // Number field: Number field with min/max value constraints
        when(numberField.getName()).thenReturn("Age");
        when(numberField.getFieldType()).thenReturn("com.krista.fields.Number");
        when(numberField.getAttributeValues()).thenReturn(
            Map.of("minValue", 18, "maxValue", 100));

        // Currency field: Currency field with min/max value constraints
        when(currencyField.getName()).thenReturn("Salary");
        when(currencyField.getFieldType()).thenReturn("com.krista.fields.Currency");
        when(currencyField.getAttributeValues()).thenReturn(
            Map.of("minValue", 1000.0, "maxValue", 500000.0));

        // Dropdown field: PickOne field with options
        when(dropdownField.getName()).thenReturn("Department");
        when(dropdownField.getFieldType()).thenReturn("com.krista.fields.PickOne");
        when(dropdownField.getAttributeValues()).thenReturn(
            Map.of("options", List.of("Engineering", "Marketing", "Sales", "HR")));

        // Checkbox field: Checkboxes field with options and max selection constraint only
        when(checkboxField.getName()).thenReturn("Skills");
        when(checkboxField.getFieldType()).thenReturn("com.krista.fields.Checkboxes");
        when(checkboxField.getAttributeValues()).thenReturn(
            Map.of("options", List.of("Java", "Python", "JavaScript", "C++"),
                   "maxSelection", 3));

        // Checkbox field with min selection: Checkboxes field with min selection constraint
        when(checkboxFieldMinSelection.getName()).thenReturn("Required Skills");
        when(checkboxFieldMinSelection.getFieldType()).thenReturn("com.krista.fields.Checkboxes");
        when(checkboxFieldMinSelection.getAttributeValues()).thenReturn(
            Map.of("options", List.of("Java", "Python", "JavaScript", "C++"),
                   "minSelection", 2, "maxSelection", 3));

        // Identifier field: Identifier field with pattern validation
        when(identifierField.getName()).thenReturn("Employee ID");
        when(identifierField.getFieldType()).thenReturn("com.krista.fields.Identifier");
        when(identifierField.getAttributeValues()).thenReturn(
            Map.of("pattern", "EMP[0-9]{4}"));

        // Date field: Date field with allowed range and time constraints
        when(dateField.getName()).thenReturn("Birth Date");
        when(dateField.getFieldType()).thenReturn("com.krista.fields.Date");
        when(dateField.getAttributeValues()).thenReturn(
            Map.of("allowedRange", Map.of("from", 946684800000L, "to", 1735689600000L), // 2000-2025
                   "allowPast", true, "allowToday", true, "allowFuture", false));

        // Date range field: Date range field with duration and range constraints
        when(dateRangeField.getName()).thenReturn("Vacation Period");
        when(dateRangeField.getFieldType()).thenReturn("com.krista.fields.DateRange");
        when(dateRangeField.getAttributeValues()).thenReturn(
            Map.of("maxDurationInDays", 30, "minDurationInDays", 1,
                   "allowedRange", Map.of("from", System.currentTimeMillis(), "to", System.currentTimeMillis() + 365L * 24 * 60 * 60 * 1000),
                   "allowPast", false, "allowFuture", true));

        // Date time range field: Date time range field with hour-based duration constraints
        when(dateTimeRangeField.getName()).thenReturn("Meeting Duration");
        when(dateTimeRangeField.getFieldType()).thenReturn("com.krista.fields.DateTimeRange");
        when(dateTimeRangeField.getAttributeValues()).thenReturn(
            Map.of("maxDurationInHours", 8, "minDurationInHours", 1,
                   "allowPast", false, "allowFuture", true));

        // Time field: Time field with time range constraints
        when(timeField.getName()).thenReturn("Meeting Time");
        when(timeField.getFieldType()).thenReturn("com.krista.fields.Time");

        // Switch field: Boolean switch field
        when(switchField.getName()).thenReturn("Is Active");
        when(switchField.getFieldType()).thenReturn("com.krista.fields.Switch");
        when(switchField.getAttributeValues()).thenReturn(Map.of());

        // Rating field: Rating field with custom scale
        when(ratingField.getName()).thenReturn("Service Rating");
        when(ratingField.getFieldType()).thenReturn("com.krista.fields.Rating");
        when(ratingField.getAttributeValues()).thenReturn(
            Map.of("minRating", 1, "maxRating", 10));

        // Phone validator field: Phone field for PhoneValidator testing
        when(phoneValidatorField.getName()).thenReturn("Contact Number");
        when(phoneValidatorField.getFieldType()).thenReturn("com.krista.fields.Phone");
        when(phoneValidatorField.getAttributeValues()).thenReturn(Map.of());

        // Date LLM field: Date field for DateLLMToFieldValueConverter testing
        when(dateLLMField.getName()).thenReturn("Event Date");
        when(dateLLMField.getFieldType()).thenReturn("com.krista.fields.Date");
        when(dateLLMField.getAttributeValues()).thenReturn(Map.of());

        // Setup complex 3-level deep multifield structure
        setupComplexMultiFieldStructure();
    }

    private void setupComplexMultiFieldStructure() throws Exception {
        // Level 3 fields (deepest level) - 3 fields total at this level
        when(level3StreetField.getName()).thenReturn("Street");
        when(level3StreetField.getFieldType()).thenReturn("com.krista.fields.Text");
        when(level3StreetField.getAttributeValues()).thenReturn(
            Map.of("minLength", 5, "maxLength", 100));

        when(level3CityField.getName()).thenReturn("City");
        when(level3CityField.getFieldType()).thenReturn("com.krista.fields.Text");
        when(level3CityField.getAttributeValues()).thenReturn(
            Map.of("minLength", 2, "maxLength", 50));

        when(level3ProjectNameField.getName()).thenReturn("ProjectName");
        when(level3ProjectNameField.getFieldType()).thenReturn("com.krista.fields.Text");
        when(level3ProjectNameField.getAttributeValues()).thenReturn(
            Map.of("minLength", 3, "maxLength", 80));

        // Level 2 fields (middle level) - 2 multifields containing level 3 fields
        when(level2AddressField.getName()).thenReturn("Address");
        when(level2AddressField.getFieldType()).thenReturn("com.krista.fields.MultiField");
        when(level2AddressField.getAttributeValues()).thenReturn(
            Map.of("fields", List.of(level3StreetField, level3CityField)));

        when(level2ProjectsField.getName()).thenReturn("Projects");
        when(level2ProjectsField.getFieldType()).thenReturn("com.krista.fields.MultiField");
        when(level2ProjectsField.getAttributeValues()).thenReturn(
            Map.of("fields", List.of(level3ProjectNameField)));

        // Simple fields for the root level
        when(simpleEmailField.getName()).thenReturn("Email");
        when(simpleEmailField.getFieldType()).thenReturn("com.krista.fields.Email");
        when(simpleEmailField.getAttributeValues()).thenReturn(
            Map.of("allowedDomains", List.of("company.com", "test.com")));

        when(simplePhoneField.getName()).thenReturn("Phone");
        when(simplePhoneField.getFieldType()).thenReturn("com.krista.fields.Phone");
        when(simplePhoneField.getAttributeValues()).thenReturn(
            Map.of("countryCode", 1));

        // Level 1 fields (second level) - 2 multifields containing level 2 fields
        when(level1PersonalInfoField.getName()).thenReturn("PersonalInfo");
        when(level1PersonalInfoField.getFieldType()).thenReturn("com.krista.fields.MultiField");
        when(level1PersonalInfoField.getAttributeValues()).thenReturn(
            Map.of("fields", List.of(level2AddressField)));

        when(level1WorkInfoField.getName()).thenReturn("WorkInfo");
        when(level1WorkInfoField.getFieldType()).thenReturn("com.krista.fields.MultiField");
        when(level1WorkInfoField.getAttributeValues()).thenReturn(
            Map.of("fields", List.of(level2ProjectsField)));

        // Root field (top level) - contains 7 fields total across 3 levels:
        // Level 1: PersonalInfo (MultiField), WorkInfo (MultiField), Email (simple), Phone (simple)
        // Level 2: Address (MultiField), Projects (MultiField)
        // Level 3: Street (Text), City (Text), ProjectName (Text)
        // Total: 7 fields (4 at root + 2 at level 2 + 3 at level 3, but 2 level 2 are containers)
        // Actual field count: Street, City, ProjectName, Email, Phone = 5 simple fields + 2 multifield containers = 7 total
        when(complexRootMultiField.getName()).thenReturn("EmployeeProfile");
        when(complexRootMultiField.getFieldType()).thenReturn("com.krista.fields.MultiField");
        when(complexRootMultiField.getAttributeValues()).thenReturn(
            Map.of("fields", List.of(
                level1PersonalInfoField,
                level1WorkInfoField,
                simpleEmailField,
                simplePhoneField)));
    }



    @Test
    public void testTextFieldValidator_WithValidNameField_ReturnsNoErrors() throws Exception {
        // Arrange
        String validName = "John Doe";

        // Act
        Map<String, Object> result = textFieldValidator.validate(nameField, validName);

        // Assert
        assertNull("Validator should return null when there are no errors", result);
    }

    @Test
    public void testTextFieldValidator_WithInvalidNameFieldTooLong_ReturnsValidationErrors() throws Exception {
        // Arrange
        String longName = "a".repeat(25); // Too long (max is 20)

        // Act
        Map<String, Object> result = textFieldValidator.validate(nameField, longName);

        // Assert
        assertNotNull(result);
        assertTrue(result.containsKey("Name"));
        List<?> errors = (List<?>) result.get("Name");
        assertFalse(errors.isEmpty());
        assertTrue(errors.get(0).toString().contains("Maximum length allowed is 20"));
    }

    @Test
    public void testEmailValidator_WithValidEmailField_ReturnsNoErrors() throws Exception {
        // Arrange
        String validEmail = "user@kristasoft.com";

        // Act
        Map<String, Object> result = emailValidator.validate(emailField, validEmail);

        // Assert
        assertNull("Validator should return null when there are no errors", result);
    }

    @Test
    public void testEmailValidator_WithInvalidEmailDomain_ReturnsValidationErrors() throws Exception {
        // Arrange
        String invalidEmail = "user@invalid.com"; // Invalid domain

        // Act
        Map<String, Object> result = emailValidator.validate(emailField, invalidEmail);

        // Assert
        assertNotNull(result);
        assertTrue(result.containsKey("Email"));
        List<?> errors = (List<?>) result.get("Email");
        assertFalse(errors.isEmpty());
        assertTrue(errors.get(0).toString().contains("Invalid email domain: invalid.com"));
    }

    @Test
    public void testTextFieldValidator_WithValidPANField_ReturnsNoErrors() throws Exception {
        // Arrange
        String validPAN = "ABCDE1234F";

        // Act
        Map<String, Object> result = textFieldValidator.validate(panField, validPAN);

        // Assert
        assertNull("Validator should return null when there are no errors", result);
    }

    @Test
    public void testTextFieldValidator_WithInvalidPANFieldTooShort_ReturnsValidationErrors() throws Exception {
        // Arrange
        String shortPAN = "A"; // Too short (min is 2)

        // Act
        Map<String, Object> result = textFieldValidator.validate(panField, shortPAN);

        // Assert
        assertNotNull(result);
        assertTrue(result.containsKey("PAN"));
        List<?> errors = (List<?>) result.get("PAN");
        assertFalse(errors.isEmpty());
        assertTrue(errors.get(0).toString().contains("Minimum length required is 2"));
    }

    @Test
    public void testTextFieldValidator_WithInvalidPANFieldTooLong_ReturnsValidationErrors() throws Exception {
        // Arrange
        String longPAN = "a".repeat(25); // Too long (max is 20)

        // Act
        Map<String, Object> result = textFieldValidator.validate(panField, longPAN);

        // Assert
        assertNotNull(result);
        assertTrue(result.containsKey("PAN"));
        List<?> errors = (List<?>) result.get("PAN");
        assertFalse(errors.isEmpty());
        assertTrue(errors.get(0).toString().contains("Maximum length allowed is 20"));
    }

    @Test
    public void testParagraphValidator_WithValidParagraphField_ReturnsNoErrors() throws Exception {
        // Arrange
        String validAddress = "This is a valid address with sufficient length";

        // Act
        Map<String, Object> result = textFieldValidator.validate(addressField, validAddress);

        // Assert
        assertNull("Validator should return null when there are no errors", result);
    }

    @Test
    public void testParagraphValidator_WithInvalidParagraphFieldTooShort_ReturnsValidationErrors() throws Exception {
        // Arrange
        String shortAddress = "Short"; // Too short (min is 15)

        // Act
        Map<String, Object> result = textFieldValidator.validate(addressField, shortAddress);

        // Assert
        assertNotNull(result);
        assertTrue(result.containsKey("Permanenet Address"));
        List<?> errors = (List<?>) result.get("Permanenet Address");
        assertFalse(errors.isEmpty());
        assertTrue(errors.get(0).toString().contains("Minimum length required is 15"));
    }

    @Test
    public void testTextFieldValidator_WithNullValue_HandlesGracefully() {
        // Act
        Map<String, Object> result = textFieldValidator.validate(nameField, null);

        // Assert
        assertNull("Validator should return null when there are no errors", result);
    }

    @Test
    public void testEmailValidator_WithNullValue_HandlesGracefully() {
        // Act
        Map<String, Object> result = emailValidator.validate(emailField, null);

        // Assert
        assertNull("Validator should return null when there are no errors", result);
    }

    // ========== NumberFieldValidator Tests ==========

    @Test
    public void testNumberFieldValidator_WithValidAge_ReturnsNoErrors() throws Exception {
        // Arrange
        Integer validAge = 25;

        // Act
        Map<String, Object> result = numberFieldValidator.validate(numberField, validAge);

        // Assert
        assertNull("Validator should return null when there are no errors", result);
    }

    @Test
    public void testNumberFieldValidator_WithAgeTooLow_ReturnsValidationErrors() throws Exception {
        // Arrange
        Integer lowAge = 15; // Below minimum of 18

        // Act
        Map<String, Object> result = numberFieldValidator.validate(numberField, lowAge);

        // Assert
        assertNotNull(result);
        assertTrue(result.containsKey("Age"));
        List<?> errors = (List<?>) result.get("Age");
        assertFalse(errors.isEmpty());
        assertTrue(errors.get(0).toString().contains("Value must be at least 18"));
    }

    @Test
    public void testNumberFieldValidator_WithAgeTooHigh_ReturnsValidationErrors() throws Exception {
        // Arrange
        Integer highAge = 150; // Above maximum of 100

        // Act
        Map<String, Object> result = numberFieldValidator.validate(numberField, highAge);

        // Assert
        assertNotNull(result);
        assertTrue(result.containsKey("Age"));
        List<?> errors = (List<?>) result.get("Age");
        assertFalse(errors.isEmpty());
        assertTrue(errors.get(0).toString().contains("Value must not exceed 100"));
    }

    @Test
    public void testNumberFieldValidator_WithValidSalary_ReturnsNoErrors() throws Exception {
        // Arrange
        Double validSalary = 50000.0;

        // Act
        Map<String, Object> result = numberFieldValidator.validate(currencyField, validSalary);

        // Assert
        assertNull("Validator should return null when there are no errors", result);
    }

    @Test
    public void testNumberFieldValidator_WithSalaryTooLow_ReturnsValidationErrors() throws Exception {
        // Arrange
        Double lowSalary = 500.0; // Below minimum of 1000.0

        // Act
        Map<String, Object> result = numberFieldValidator.validate(currencyField, lowSalary);

        // Assert
        assertNotNull(result);
        assertTrue(result.containsKey("Salary"));
        List<?> errors = (List<?>) result.get("Salary");
        assertFalse(errors.isEmpty());
        assertTrue(errors.get(0).toString().contains("Value must be at least 1000.0"));
    }

    @Test
    public void testNumberFieldValidator_WithInvalidFormat_ReturnsValidationErrors() {
        // Arrange
        String invalidNumber = "not a number";

        // Act
        Map<String, Object> result = numberFieldValidator.validate(numberField, invalidNumber);

        // Assert
        assertNotNull(result);
        assertTrue(result.containsKey("Age"));
        List<?> errors = (List<?>) result.get("Age");
        assertFalse(errors.isEmpty());
        assertTrue(errors.get(0).toString().contains("Invalid number format"));
    }

    // ========== PickOneValidator Tests ==========

    @Test
    public void testPickOneValidator_WithValidDepartment_ReturnsNoErrors() throws Exception {
        // Arrange
        String validDepartment = "Engineering";

        // Act
        Map<String, Object> result = pickOneValidator.validate(dropdownField, validDepartment);

        // Assert
        assertNull("Validator should return null when there are no errors", result);
    }

    @Test
    public void testPickOneValidator_WithInvalidDepartment_ReturnsValidationErrors() throws Exception {
        // Arrange
        String invalidDepartment = "Finance"; // Not in the options list

        // Act
        Map<String, Object> result = pickOneValidator.validate(dropdownField, invalidDepartment);

        // Assert
        assertNotNull(result);
        assertTrue(result.containsKey("Department"));
        List<?> errors = (List<?>) result.get("Department");
        assertFalse(errors.isEmpty());
        assertTrue(errors.get(0).toString().contains("Invalid value: Finance"));
        assertTrue(errors.get(0).toString().contains("Must be one of:"));
    }

    @Test
    public void testPickOneValidator_WithInvalidFormat_ReturnsValidationErrors() {
        // Arrange
        Integer invalidValue = 123; // Not a string

        // Act
        Map<String, Object> result = pickOneValidator.validate(dropdownField, invalidValue);

        // Assert
        assertNotNull(result);
        assertTrue(result.containsKey("Department"));
        List<?> errors = (List<?>) result.get("Department");
        assertFalse(errors.isEmpty());
        assertTrue(errors.get(0).toString().contains("Invalid PickOne format"));
    }

    // ========== CheckboxesValidator Tests ==========

    @Test
    public void testCheckboxesValidator_WithValidSkills_ReturnsNoErrors() throws Exception {
        // Arrange
        List<String> validSkills = Arrays.asList("Java", "Python");

        // Act
        Map<String, Object> result = checkboxesValidator.validate(checkboxField, validSkills);

        // Assert
        assertNull("Validator should return null when there are no errors", result);
    }

    @Test
    public void testCheckboxesValidator_WithInvalidSkill_ReturnsValidationErrors() throws Exception {
        // Arrange
        List<String> invalidSkills = Arrays.asList("Java", "Ruby"); // Ruby not in options

        // Act
        Map<String, Object> result = checkboxesValidator.validate(checkboxField, invalidSkills);

        // Assert
        assertNotNull(result);
        assertTrue(result.containsKey("Skills"));
        List<?> errors = (List<?>) result.get("Skills");
        assertFalse(errors.isEmpty());
        assertTrue(errors.get(0).toString().contains("Invalid value: Ruby"));
    }

    @Test
    public void testCheckboxesValidator_WithEmptyList_ReturnsNoErrors() {
        // Arrange
        List<String> emptySkills = Arrays.asList(); // Empty list

        // Act
        Map<String, Object> result = checkboxesValidator.validate(checkboxField, emptySkills);

        // Assert
        assertNull("Validator should return null when there are no errors", result);
    }

    @Test
    public void testCheckboxesValidator_WithTooFewSelections_ReturnsValidationErrors() throws Exception {
        // Arrange
        List<String> tooFewSkills = Arrays.asList("Java"); // Only 1 selection, but minSelection is 2

        // Act
        Map<String, Object> result = checkboxesValidator.validate(checkboxFieldMinSelection, tooFewSkills);

        // Assert
        assertNotNull(result);
        assertTrue(result.containsKey("Required Skills"));
        List<?> errors = (List<?>) result.get("Required Skills");
        assertFalse(errors.isEmpty());
        assertTrue(errors.get(0).toString().contains("At least 2 selections required"));
    }

    @Test
    public void testCheckboxesValidator_WithTooManySelections_ReturnsValidationErrors() throws Exception {
        // Arrange
        List<String> tooManySkills = Arrays.asList("Java", "Python", "JavaScript", "C++"); // 4 items, but maxSelection is 3

        // Act
        Map<String, Object> result = checkboxesValidator.validate(checkboxField, tooManySkills);

        // Assert
        assertNotNull(result);
        assertTrue(result.containsKey("Skills"));
        List<?> errors = (List<?>) result.get("Skills");
        assertFalse(errors.isEmpty());
        assertTrue(errors.get(0).toString().contains("At most 3 selections allowed"));
    }

    @Test
    public void testCheckboxesValidator_WithInvalidFormat_ReturnsValidationErrors() {
        // Arrange
        String invalidValue = "not a list"; // Not a list

        // Act
        Map<String, Object> result = checkboxesValidator.validate(checkboxField, invalidValue);

        // Assert
        assertNotNull(result);
        assertTrue(result.containsKey("Skills"));
        List<?> errors = (List<?>) result.get("Skills");
        assertFalse(errors.isEmpty());
        assertTrue(errors.get(0).toString().contains("Invalid checkboxes format"));
    }

    // ========== IdentifierValidator Tests ==========

    @Test
    public void testIdentifierValidator_WithValidEmployeeId_ReturnsNoErrors() throws Exception {
        // Arrange
        String validEmployeeId = "EMP1234";

        // Act
        Map<String, Object> result = identifierValidator.validate(identifierField, validEmployeeId);

        // Assert
        assertNull("Validator should return null when there are no errors", result);
    }

    @Test
    public void testIdentifierValidator_WithInvalidEmployeeIdPattern_ReturnsValidationErrors() throws Exception {
        // Arrange
        String invalidEmployeeId = "EMPLOYEE1234"; // Doesn't match pattern EMP[0-9]{4}

        // Act
        Map<String, Object> result = identifierValidator.validate(identifierField, invalidEmployeeId);

        // Assert
        assertNotNull(result);
        assertTrue(result.containsKey("Employee ID"));
        List<?> errors = (List<?>) result.get("Employee ID");
        assertFalse(errors.isEmpty());
        assertTrue(errors.get(0).toString().contains("Value does not match the required pattern"));
    }

    @Test
    public void testIdentifierValidator_WithInvalidEmployeeIdTooShort_ReturnsValidationErrors() throws Exception {
        // Arrange
        String invalidEmployeeId = "EMP123"; // Too short, should be EMP + 4 digits

        // Act
        Map<String, Object> result = identifierValidator.validate(identifierField, invalidEmployeeId);

        // Assert
        assertNotNull(result);
        assertTrue(result.containsKey("Employee ID"));
        List<?> errors = (List<?>) result.get("Employee ID");
        assertFalse(errors.isEmpty());
        assertTrue(errors.get(0).toString().contains("Value does not match the required pattern"));
    }

    @Test
    public void testIdentifierValidator_WithInvalidFormat_ReturnsValidationErrors() {
        // Arrange
        Integer invalidValue = 123; // Not a string

        // Act
        Map<String, Object> result = identifierValidator.validate(identifierField, invalidValue);

        // Assert
        assertNotNull(result);
        assertTrue(result.containsKey("Employee ID"));
        List<?> errors = (List<?>) result.get("Employee ID");
        assertFalse(errors.isEmpty());
        assertTrue(errors.get(0).toString().contains("Invalid identifier format"));
    }

    // ========== DateValidator Tests ==========

    @Test
    public void testDateValidator_WithValidDate_ReturnsNoErrors() throws Exception {
        // Arrange
        Long validDate = 1609459200000L; // January 1, 2021

        // Act
        Map<String, Object> result = dateValidator.validate(dateField, validDate);

        // Assert
        assertNull("Validator should return null when there are no errors", result);
    }

    @Test
    public void testDateValidator_WithDateBeforeAllowedRange_ReturnsValidationErrors() throws Exception {
        // Arrange
        Long dateBeforeRange = 915148800000L; // January 1, 1999 (before 2000)

        // Act
        Map<String, Object> result = dateValidator.validate(dateField, dateBeforeRange);

        // Assert
        assertNotNull(result);
        assertTrue(result.containsKey("Birth Date"));
        List<?> errors = (List<?>) result.get("Birth Date");
        assertFalse(errors.isEmpty());
        assertTrue(errors.get(0).toString().contains("Date is before the allowed minimum date"));
    }

    @Test
    public void testDateValidator_WithDateAfterAllowedRange_ReturnsValidationErrors() throws Exception {
        // Arrange
        Long dateAfterRange = 1767225600000L; // January 1, 2026 (after 2025)

        // Act
        Map<String, Object> result = dateValidator.validate(dateField, dateAfterRange);

        // Assert
        assertNotNull(result);
        assertTrue(result.containsKey("Birth Date"));
        List<?> errors = (List<?>) result.get("Birth Date");
        assertFalse(errors.isEmpty());
        assertTrue(errors.get(0).toString().contains("Date is after the allowed maximum date"));
    }

    @Test
    public void testDateValidator_WithFutureDate_ReturnsValidationErrors() throws Exception {
        // Arrange
        Long futureDate = System.currentTimeMillis() + 2 * 24 * 60 * 60 * 1000L; // 2 days from now

        // Act
        Map<String, Object> result = dateValidator.validate(dateField, futureDate);

        // Assert
        assertNotNull(result);
        assertTrue(result.containsKey("Birth Date"));
        List<?> errors = (List<?>) result.get("Birth Date");
        assertFalse(errors.isEmpty());
        assertTrue(errors.toString().contains("Future dates are not allowed"));
    }

    @Test
    public void testDateValidator_WithInvalidFormat_ReturnsValidationErrors() {
        // Arrange
        String invalidDate = "not a timestamp";

        // Act
        Map<String, Object> result = dateValidator.validate(dateField, invalidDate);

        // Assert
        assertNotNull(result);
        assertTrue(result.containsKey("Birth Date"));
        List<?> errors = (List<?>) result.get("Birth Date");
        assertFalse(errors.isEmpty());
        assertTrue(errors.get(0).toString().contains("Invalid date format"));
    }

    // ========== DateRangeValidator Tests ==========

    @Test
    public void testDateRangeValidator_WithValidDateRange_ReturnsNoErrors() throws Exception {
        // Arrange
        long startTime = System.currentTimeMillis() + 24 * 60 * 60 * 1000L; // Tomorrow
        long endTime = startTime + 7 * 24 * 60 * 60 * 1000L; // 7 days later
        DateRange validDateRange = new DateRange(startTime, endTime);

        // Act
        Map<String, Object> result = dateRangeValidator.validate(dateRangeField, validDateRange);

        // Assert
        assertNull("Validator should return null when there are no errors", result);
    }

    @Test
    public void testDateRangeValidator_WithStartAfterEnd_ReturnsValidationErrors() throws Exception {
        // Arrange
        long startTime = System.currentTimeMillis() + 7 * 24 * 60 * 60 * 1000L; // 7 days from now
        long endTime = System.currentTimeMillis() + 24 * 60 * 60 * 1000L; // Tomorrow
        DateRange invalidDateRange = new DateRange(startTime, endTime);

        // Act
        Map<String, Object> result = dateRangeValidator.validate(dateRangeField, invalidDateRange);

        // Assert
        assertNotNull(result);
        assertTrue(result.containsKey("Vacation Period"));
        List<?> errors = (List<?>) result.get("Vacation Period");
        assertFalse(errors.isEmpty());
        assertTrue(errors.get(0).toString().contains("Start date must be before end date"));
    }

    @Test
    public void testDateRangeValidator_WithDurationTooLong_ReturnsValidationErrors() throws Exception {
        // Arrange
        long startTime = System.currentTimeMillis() + 24 * 60 * 60 * 1000L; // Tomorrow
        long endTime = startTime + 35L * 24 * 60 * 60 * 1000L; // 35 days later (exceeds max 30)
        DateRange longDateRange = new DateRange(startTime, endTime);

        // Act
        Map<String, Object> result = dateRangeValidator.validate(dateRangeField, longDateRange);

        // Assert
        assertNotNull(result);
        assertTrue(result.containsKey("Vacation Period"));
        List<?> errors = (List<?>) result.get("Vacation Period");
        assertFalse(errors.isEmpty());
        assertTrue(errors.get(0).toString().contains("Duration cannot exceed 30 days"));
    }

    @Test
    public void testDateRangeValidator_WithDurationTooShort_ReturnsValidationErrors() throws Exception {
        // Arrange
        long startTime = System.currentTimeMillis() + 24 * 60 * 60 * 1000L; // Tomorrow
        long endTime = startTime + 12 * 60 * 60 * 1000L; // 12 hours later (less than 1 day)
        DateRange shortDateRange = new DateRange(startTime, endTime);

        // Act
        Map<String, Object> result = dateRangeValidator.validate(dateRangeField, shortDateRange);

        // Assert
        assertNotNull(result);
        assertTrue(result.containsKey("Vacation Period"));
        List<?> errors = (List<?>) result.get("Vacation Period");
        assertFalse(errors.isEmpty());
        assertTrue(errors.get(0).toString().contains("Duration must be at least 1 days"));
    }

    @Test
    public void testDateRangeValidator_WithPastDates_ReturnsValidationErrors() throws Exception {
        // Arrange
        long startTime = System.currentTimeMillis() - 7 * 24 * 60 * 60 * 1000L; // 7 days ago
        long endTime = System.currentTimeMillis() - 2 * 24 * 60 * 60 * 1000L; // 2 days ago
        DateRange pastDateRange = new DateRange(startTime, endTime);

        // Act
        Map<String, Object> result = dateRangeValidator.validate(dateRangeField, pastDateRange);

        // Assert
        assertNotNull(result);
        assertTrue(result.containsKey("Vacation Period"));
        List<?> errors = (List<?>) result.get("Vacation Period");
        assertFalse(errors.isEmpty());
        assertTrue(errors.toString().contains("Past dates are not allowed"));
    }

    @Test
    public void testDateRangeValidator_WithInvalidFormat_ReturnsValidationErrors() {
        // Arrange
        String invalidValue = "not a date range";

        // Act
        Map<String, Object> result = dateRangeValidator.validate(dateRangeField, invalidValue);

        // Assert
        assertNotNull(result);
        assertTrue(result.containsKey("Vacation Period"));
        List<?> errors = (List<?>) result.get("Vacation Period");
        assertFalse(errors.isEmpty());
        assertTrue(errors.get(0).toString().contains("Invalid date range format"));
    }

    // ========== DateTimeRangeValidator Tests ==========

    @Test
    public void testDateTimeRangeValidator_WithValidDateTimeRange_ReturnsNoErrors() throws Exception {
        // Arrange
        long startTime = System.currentTimeMillis() + 60 * 60 * 1000L; // 1 hour from now
        long endTime = startTime + 4 * 60 * 60 * 1000L; // 4 hours later
        DateRange validDateTimeRange = new DateRange(startTime, endTime);

        // Act
        Map<String, Object> result = dateTimeRangeValidator.validate(dateTimeRangeField, validDateTimeRange);

        // Assert
        assertNull("Validator should return null when there are no errors", result);
    }

    @Test
    public void testDateTimeRangeValidator_WithStartAfterEnd_ReturnsValidationErrors() throws Exception {
        // Arrange
        long startTime = System.currentTimeMillis() + 4 * 60 * 60 * 1000L; // 4 hours from now
        long endTime = System.currentTimeMillis() + 60 * 60 * 1000L; // 1 hour from now
        DateRange invalidDateTimeRange = new DateRange(startTime, endTime);

        // Act
        Map<String, Object> result = dateTimeRangeValidator.validate(dateTimeRangeField, invalidDateTimeRange);

        // Assert
        assertNotNull(result);
        assertTrue(result.containsKey("Meeting Duration"));
        List<?> errors = (List<?>) result.get("Meeting Duration");
        assertFalse(errors.isEmpty());
        assertTrue(errors.get(0).toString().contains("Start date/time must be before end date/time"));
    }

    @Test
    public void testDateTimeRangeValidator_WithDurationTooLong_ReturnsValidationErrors() throws Exception {
        // Arrange
        long startTime = System.currentTimeMillis() + 60 * 60 * 1000L; // 1 hour from now
        long endTime = startTime + 10 * 60 * 60 * 1000L; // 10 hours later (exceeds max 8)
        DateRange longDateTimeRange = new DateRange(startTime, endTime);

        // Act
        Map<String, Object> result = dateTimeRangeValidator.validate(dateTimeRangeField, longDateTimeRange);

        // Assert
        assertNotNull(result);
        assertTrue(result.containsKey("Meeting Duration"));
        List<?> errors = (List<?>) result.get("Meeting Duration");
        assertFalse(errors.isEmpty());
        assertTrue(errors.get(0).toString().contains("Duration cannot exceed 8 hours"));
    }

    @Test
    public void testDateTimeRangeValidator_WithDurationTooShort_ReturnsValidationErrors() throws Exception {
        // Arrange
        long startTime = System.currentTimeMillis() + 60 * 60 * 1000L; // 1 hour from now
        long endTime = startTime + 30 * 60 * 1000L; // 30 minutes later (less than 1 hour)
        DateRange shortDateTimeRange = new DateRange(startTime, endTime);

        // Act
        Map<String, Object> result = dateTimeRangeValidator.validate(dateTimeRangeField, shortDateTimeRange);

        // Assert
        assertNotNull(result);
        assertTrue(result.containsKey("Meeting Duration"));
        List<?> errors = (List<?>) result.get("Meeting Duration");
        assertFalse(errors.isEmpty());
        assertTrue(errors.get(0).toString().contains("Duration must be at least 1 hours"));
    }

    @Test
    public void testDateTimeRangeValidator_WithPastDateTime_ReturnsValidationErrors() throws Exception {
        // Arrange
        long startTime = System.currentTimeMillis() - 2 * 60 * 60 * 1000L; // 2 hours ago
        long endTime = System.currentTimeMillis() - 60 * 60 * 1000L; // 1 hour ago
        DateRange pastDateTimeRange = new DateRange(startTime, endTime);

        // Act
        Map<String, Object> result = dateTimeRangeValidator.validate(dateTimeRangeField, pastDateTimeRange);

        // Assert
        assertNotNull(result);
        assertTrue(result.containsKey("Meeting Duration"));
        List<?> errors = (List<?>) result.get("Meeting Duration");
        assertFalse(errors.isEmpty());
        assertTrue(errors.get(0).toString().contains("Past date/times are not allowed"));
    }

    @Test
    public void testDateTimeRangeValidator_WithInvalidFormat_ReturnsValidationErrors() {
        // Arrange
        String invalidValue = "not a date time range";

        // Act
        Map<String, Object> result = dateTimeRangeValidator.validate(dateTimeRangeField, invalidValue);

        // Assert
        assertNotNull(result);
        assertTrue(result.containsKey("Meeting Duration"));
        List<?> errors = (List<?>) result.get("Meeting Duration");
        assertFalse(errors.isEmpty());
        assertTrue(errors.get(0).toString().contains("Invalid date/time range format"));
    }

    // ========== TimeValidator Tests ==========

    @Test
    public void testTimeValidator_WithValidTime_ReturnsNoErrors() throws Exception {
        // Arrange
        Long validTime = 10 * 60 * 60 * 1000L; // 10 AM

        // Act
        Map<String, Object> result = timeValidator.validate(timeField, validTime);

        // Assert
        assertNull(result);
    }

    @Test
    public void testTimeValidator_WithTime_ReturnsNoErrors() throws Exception {
        // Arrange
        Long earlyTime = 8 * 60 * 60 * 1000L; // 8 AM

        // Act
        Map<String, Object> result = timeValidator.validate(timeField, earlyTime);

        // Assert
        assertNull(result);
    }

    @Test
    public void testTimeValidator_WithNegativeTime_ReturnsValidationErrors() throws Exception {
        // Arrange
        Long negativeTime = -1000L; // Negative time

        // Act
        Map<String, Object> result = timeValidator.validate(timeField, negativeTime);

        // Assert
        assertNotNull(result);
        assertTrue(result.containsKey("Meeting Time"));
        List<?> errors = (List<?>) result.get("Meeting Time");
        assertFalse(errors.isEmpty());
        assertTrue(errors.get(0).toString().contains("Time cannot be negative"));
    }

    @Test
    public void testTimeValidator_WithTimeExceeding24Hours_ReturnsValidationErrors() throws Exception {
        // Arrange
        Long excessiveTime = 25 * 60 * 60 * 1000L; // 25 hours

        // Act
        Map<String, Object> result = timeValidator.validate(timeField, excessiveTime);

        // Assert
        assertNotNull(result);
        assertTrue(result.containsKey("Meeting Time"));
        List<?> errors = (List<?>) result.get("Meeting Time");
        assertFalse(errors.isEmpty());
        assertTrue(errors.get(0).toString().contains("Time cannot exceed 24 hours"));
    }

    @Test
    public void testTimeValidator_WithInvalidFormat_ReturnsValidationErrors() {
        // Arrange
        String invalidTime = "not a time";

        // Act
        Map<String, Object> result = timeValidator.validate(timeField, invalidTime);

        // Assert
        assertNotNull(result);
        assertTrue(result.containsKey("Meeting Time"));
        List<?> errors = (List<?>) result.get("Meeting Time");
        assertFalse(errors.isEmpty());
        assertTrue(errors.get(0).toString().contains("Invalid time format"));
    }

    // ========== SwitchValidator Tests ==========

    @Test
    public void testSwitchValidator_WithValidTrueValue_ReturnsNoErrors() throws Exception {
        // Arrange
        Boolean validValue = true;

        // Act
        Map<String, Object> result = switchValidator.validate(switchField, validValue);

        // Assert
        assertNull("Validator should return null when there are no errors", result);
    }

    @Test
    public void testSwitchValidator_WithValidFalseValue_ReturnsNoErrors() throws Exception {
        // Arrange
        Boolean validValue = false;

        // Act
        Map<String, Object> result = switchValidator.validate(switchField, validValue);

        // Assert
        assertNull("Validator should return null when there are no errors", result);
    }

    @Test
    public void testSwitchValidator_WithInvalidFormat_ReturnsValidationErrors() {
        // Arrange
        String invalidValue = "not a boolean";

        // Act
        Map<String, Object> result = switchValidator.validate(switchField, invalidValue);

        // Assert
        assertNotNull(result);
        assertTrue(result.containsKey("Is Active"));
        List<?> errors = (List<?>) result.get("Is Active");
        assertFalse(errors.isEmpty());
        assertTrue(errors.get(0).toString().contains("Invalid switch format"));
    }

    @Test
    public void testSwitchValidator_WithIntegerValue_ReturnsValidationErrors() {
        // Arrange
        Integer invalidValue = 1;

        // Act
        Map<String, Object> result = switchValidator.validate(switchField, invalidValue);

        // Assert
        assertNotNull(result);
        assertTrue(result.containsKey("Is Active"));
        List<?> errors = (List<?>) result.get("Is Active");
        assertFalse(errors.isEmpty());
        assertTrue(errors.get(0).toString().contains("Invalid switch format"));
    }

    // ========== RatingValidator Tests ==========

    @Test
    public void testRatingValidator_WithValidRating_ReturnsNoErrors() throws Exception {
        // Arrange
        Integer validRating = 5;

        // Act
        Map<String, Object> result = ratingValidator.validate(ratingField, validRating);

        // Assert
        assertNull("Validator should return null when there are no errors", result);
    }

    @Test
    public void testRatingValidator_WithRatingTooLow_ReturnsValidationErrors() throws Exception {
        // Arrange
        Integer lowRating = 0; // Below minimum of 1

        // Act
        Map<String, Object> result = ratingValidator.validate(ratingField, lowRating);

        // Assert
        assertNotNull(result);
        assertTrue(result.containsKey("Service Rating"));
        List<?> errors = (List<?>) result.get("Service Rating");
        assertFalse(errors.isEmpty());
        assertTrue(errors.get(0).toString().contains("Rating must be at least 1"));
    }

    @Test
    public void testRatingValidator_WithRatingTooHigh_ReturnsValidationErrors() throws Exception {
        // Arrange
        Integer highRating = 15; // Above maximum of 10

        // Act
        Map<String, Object> result = ratingValidator.validate(ratingField, highRating);

        // Assert
        assertNotNull(result);
        assertTrue(result.containsKey("Service Rating"));
        List<?> errors = (List<?>) result.get("Service Rating");
        assertFalse(errors.isEmpty());
        assertTrue(errors.get(0).toString().contains("Rating cannot exceed 10"));
    }

    @Test
    public void testRatingValidator_WithDoubleValue_ReturnsNoErrors() throws Exception {
        // Arrange
        Double validRating = 7.5;

        // Act
        Map<String, Object> result = ratingValidator.validate(ratingField, validRating);

        // Assert
        assertNull("Validator should return null when there are no errors", result);
    }

    @Test
    public void testRatingValidator_WithInvalidFormat_ReturnsValidationErrors() {
        // Arrange
        String invalidRating = "not a number";

        // Act
        Map<String, Object> result = ratingValidator.validate(ratingField, invalidRating);

        // Assert
        assertNotNull(result);
        assertTrue(result.containsKey("Service Rating"));
        List<?> errors = (List<?>) result.get("Service Rating");
        assertFalse(errors.isEmpty());
        assertTrue(errors.get(0).toString().contains("Invalid rating format"));
    }

    // ========== PhoneValidator Tests ==========
    // Note: PhoneValidator now expects PhoneNumber objects

    @Test
    public void testPhoneValidator_WithInvalidFormat_ReturnsValidationErrors() {
        // Arrange
        Integer invalidPhone = 1234567890; // Not a PhoneNumber object

        // Act
        Map<String, Object> result = phoneValidator.validate(phoneValidatorField, invalidPhone);

        // Assert
        assertNotNull(result);
        assertTrue(result.containsKey("Contact Number"));
        List<?> errors = (List<?>) result.get("Contact Number");
        assertFalse(errors.isEmpty());
        assertTrue(errors.get(0).toString().contains("Invalid phone number format"));
    }

    @Test
    public void testPhoneValidator_WithStringInput_ReturnsValidationErrors() {
        // Arrange
        String phoneString = "+1 (555) 123-4567"; // String input instead of PhoneNumber

        // Act
        Map<String, Object> result = phoneValidator.validate(phoneValidatorField, phoneString);

        // Assert
        assertNotNull(result);
        assertTrue(result.containsKey("Contact Number"));
        List<?> errors = (List<?>) result.get("Contact Number");
        assertFalse(errors.isEmpty());
        assertTrue(errors.get(0).toString().contains("Invalid phone number format"));
    }

    // ========== Complex 3-Level Deep MultiField Test ==========

    @Test
    public void testComplexThreeLevelDeepMultiField_WithValidData_ReturnsNoErrors() {
        // Arrange - Create a complex 3-level deep structure with 7 total fields
        // Structure:
        // EmployeeProfile (root)
        //   ├── PersonalInfo (level 1)
        //   │   └── Address (level 2)
        //   │       ├── Street (level 3 - Text field)
        //   │       └── City (level 3 - Text field)
        //   ├── WorkInfo (level 1)
        //   │   └── Projects (level 2)
        //   │       └── ProjectName (level 3 - Text field)
        //   ├── Email (level 1 - Email field)
        //   └── Phone (level 1 - Phone field)
        // Total: 7 fields (3 at level 3 + 2 at level 1 + 2 multifield containers)

        // Create test data that matches the structure
        Map<String, Object> validComplexData = Map.of(
            "PersonalInfo", Map.of(
                "Address", Map.of(
                    "Street", "123 Main Street",
                    "City", "New York"
                )
            ),
            "WorkInfo", Map.of(
                "Projects", Map.of(
                    "ProjectName", "Enterprise System"
                )
            ),
            "Email", "john.doe@company.com",
            "Phone", new PhoneNumber(1, 5551234567L)
        );

        // Act - Test with MultiFieldValidator
        Map<String, Object> result = multiFieldValidator.validate(complexRootMultiField, validComplexData);

        // Assert
        assertNull("Validator should return null when there are no errors", result);
    }

    @Test
    public void testComplexThreeLevelDeepMultiField_WithMixedValidAndInvalidData_ReturnsPartialErrors() {
        // Arrange - Create a complex 3-level deep structure with 7 total fields
        // Structure:
        // EmployeeProfile (root)
        //   ├── PersonalInfo (level 1)
        //   │   └── Address (level 2)
        //   │       ├── Street (level 3 - Text field)
        //   │       └── City (level 3 - Text field)
        //   ├── WorkInfo (level 1)
        //   │   └── Projects (level 2)
        //   │       └── ProjectName (level 3 - Text field)
        //   ├── Email (level 1 - Email field)
        //   └── Phone (level 1 - Phone field)
        // Total: 7 fields (3 at level 3 + 2 at level 1 + 2 multifield containers)

        // Create test data that matches the structure - mixed valid/invalid data
        Map<String, Object> mixedValidInvalidData = Map.of(
                "PersonalInfo", Map.of(
                        "Address", Map.of(
                                "Street", "123 Main Street", // Valid
                                "City", "N" // Invalid - too short (minLength: 2)
                        )
                ),
                "WorkInfo", Map.of(
                        "Projects", Map.of(
                                "ProjectName", "Enterprise System" // Valid
                        )
                ),
                "Email", "john.doe@company.com", // Valid
                "Phone", new PhoneNumber(1, 5551234567L) // Valid - PhoneNumber object for validation
        );

        // Act - Test with MultiFieldValidator
        Map<String, Object> result = multiFieldValidator.validate(complexRootMultiField, mixedValidInvalidData);

        // Assert
        assertNotNull(result);
        assertTrue(result.containsKey("EmployeeProfile"));

        // With the new nested error structure, errors should be a Map, not a List
        Object errorsObj = result.get("EmployeeProfile");
        assertTrue("MultiField should have validation errors as nested Map", errorsObj instanceof Map);

        Map<String, Object> errors = (Map<String, Object>) errorsObj;
        assertFalse("MultiField should have validation errors", errors.isEmpty());

        // Navigate through the nested error structure to find the City error
        // Structure: EmployeeProfile -> PersonalInfo -> Address -> City
        assertTrue("Should have PersonalInfo errors", errors.containsKey("PersonalInfo"));
        Map<String, Object> personalInfoErrors = (Map<String, Object>) errors.get("PersonalInfo");

        assertTrue("Should have Address errors", personalInfoErrors.containsKey("Address"));
        Map<String, Object> addressErrors = (Map<String, Object>) personalInfoErrors.get("Address");

        assertTrue("Should have City errors", addressErrors.containsKey("City"));
        List<String> cityErrors = (List<String>) addressErrors.get("City");

        assertFalse("City should have validation errors", cityErrors.isEmpty());
        assertTrue("City error should mention minimum length",
                cityErrors.get(0).contains("Minimum length required is 2"));

        // Verify that other fields (Email, Phone, etc.) should NOT have errors since they're valid
        assertFalse("Email should not have errors (it's valid)", errors.containsKey("Email"));
        assertFalse("Phone should not have errors (it's valid)", errors.containsKey("Phone"));
        assertFalse("WorkInfo should not have errors (ProjectName is valid)", errors.containsKey("WorkInfo"));

    }

}
