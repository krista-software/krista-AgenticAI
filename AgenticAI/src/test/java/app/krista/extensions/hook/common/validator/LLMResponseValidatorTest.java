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

package app.krista.extensions.hook.common.validator;

import app.krista.model.base.FreeForm;
import app.krista.model.field.Field;
import app.krista.model.field.NamedField;
import app.krista.model.field.NamedValuedField;
import org.junit.Before;
import org.junit.Test;

import java.util.*;

import static org.junit.Assert.*;

@SuppressWarnings("unchecked")
public class LLMResponseValidatorTest {

    private FreeForm complexFreeForm;
    private Map<String, Object> validOutputFieldValueMap;
    private Map<String, Object> invalidOutputFieldValueMap;
    private Map<String, Object> partiallyValidOutputFieldValueMap;

    @Before
    public void setUp() throws Exception {
        createComplexFreeFormWithNestedFields();
        createTestOutputFieldValueMaps();
    }

    private void createComplexFreeFormWithNestedFields() {
        complexFreeForm = new FreeForm();

        // Level 1: Basic fields
        // Text field - mandatory with constraints
        Field nameField = createField("com.krista.fields.Text",
            Map.of("minLength", 2, "maxLength", 50), true);
        complexFreeForm.put("name", "Full Name", nameField, "John Doe");

        // Number field - optional with range
        Field ageField = createField("com.krista.fields.Number",
            Map.of("minValue", 18.0, "maxValue", 100.0), false);
        complexFreeForm.put("age", "Age", ageField, 25.0);

        // Email field - mandatory with domain restriction
        Field emailField = createField("com.krista.fields.Email",
            Map.of("allowedDomains", List.of("company.com", "example.org")), true);
        complexFreeForm.put("email", "Email Address", emailField, "john@company.com");

        // Phone field - optional with countryCode (expects Number input for PhoneNumber object)
        Field phoneField = createField("com.krista.fields.Phone",
            Map.of("countryCode", 1), false);
        complexFreeForm.put("phone", "Phone Number", phoneField, 5551234567L);

        // Switch field - mandatory
        Field activeField = createField("com.krista.fields.Switch", Map.of(), true);
        complexFreeForm.put("isActive", "Active Status", activeField, true);

        // PickOne field - mandatory with options
        Field categoryField = createField("com.krista.fields.PickOne",
            Map.of("options", List.of("Employee", "Contractor", "Intern")), true);
        complexFreeForm.put("category", "Employee Category", categoryField, "Employee");

        // Checkboxes field - optional with multiple selection
        Field skillsField = createField("com.krista.fields.Checkboxes",
            Map.of("options", List.of("Java", "Python", "JavaScript", "C++"),
                   "minSelection", 1, "maxSelection", 3), false);
        complexFreeForm.put("skills", "Programming Skills", skillsField, List.of("Java", "Python"));

        // Currency field - optional with format
        Field salaryField = createField("com.krista.fields.Currency",
            Map.of("formatPattern", "$#,##0.00", "minValue", 30000.0, "maxValue", 200000.0), false);
        complexFreeForm.put("salary", "Annual Salary", salaryField, 75000.0);

        // Date field - mandatory
        Field hireDateField = createField("com.krista.fields.Date", Map.of(), true);
        complexFreeForm.put("hireDate", "Hire Date", hireDateField, 1609459200000L); // Jan 1, 2021

        // Level 2: List field with nested Text elements
        Field listElementField = createField("com.krista.fields.Text",
            Map.of("minLength", 3, "maxLength", 30), true);
        Field projectsListField = createField("com.krista.fields.List",
            Map.of("field", listElementField), false);
        complexFreeForm.put("projects", "Project Names", projectsListField,
            List.of("Project Alpha", "Project Beta", "Project Gamma"));

        // Level 2: List field with nested MultiField elements (for testing nested validation)
        Field teamMemberMultiField = createTeamMemberMultiField();
        Field teamListField = createField("com.krista.fields.List",
            Map.of("field", teamMemberMultiField), false);
        complexFreeForm.put("teamMembers", "Team Members", teamListField,
            List.of(
                Map.of("memberName", "Alice Smith", "memberEmail", "alice@company.com", "memberRole", "Developer"),
                Map.of("memberName", "Bob Johnson", "memberEmail", "bob@company.com", "memberRole", "Designer")
            ));

        // Level 2: First level MultiField - Contact Information
        Field contactMultiField = createContactMultiField();
        complexFreeForm.put("contactInfo", "Contact Information", contactMultiField,
            Map.of(
                "primaryEmail", "john.primary@company.com",
                "secondaryEmail", "john.secondary@example.org",
                "workPhone", "+1 (555) 987-6543",
                "homeAddress", Map.of(
                    "street", "123 Main Street",
                    "city", "New York",
                    "state", "NY",
                    "zipCode", "10001",
                    "country", "USA"
                )
            ));

        // Level 2: Second level MultiField - Employment Details
        Field employmentMultiField = createEmploymentMultiField();
        complexFreeForm.put("employment", "Employment Details", employmentMultiField,
            Map.of(
                "department", "Engineering",
                "position", "Senior Developer",
                "manager", "Jane Smith",
                "startDate", 1609459200000L,
                "benefits", Map.of(
                    "healthInsurance", true,
                    "dentalInsurance", false,
                    "visionInsurance", true,
                    "retirementPlan", "401k",
                    "vacationDays", 25,
                    "sickDays", 10
                )
            ));
    }

    private Field createTeamMemberMultiField() {
        // Team member fields
        NamedField memberNameField = new NamedField("memberName", "com.krista.fields.Text",
            Map.of("minLength", 2, "maxLength", 50), createOptionsMap(true));
        NamedField memberEmailField = new NamedField("memberEmail", "com.krista.fields.Email",
            Map.of("allowedDomains", List.of("company.com", "contractor.com")), createOptionsMap(true));
        NamedField memberRoleField = new NamedField("memberRole", "com.krista.fields.PickOne",
            Map.of("options", List.of("Developer", "Designer", "Manager", "Tester")), createOptionsMap(true));

        List<NamedField> teamMemberFields = List.of(memberNameField, memberEmailField, memberRoleField);
        return createField("com.krista.fields.MultiField",
            Map.of("fields", teamMemberFields), false);
    }

    private Field createContactMultiField() {
        Map<String, Object> attributes = new HashMap<>();
        List<NamedField> nestedFields = new ArrayList<>();

        // Primary email
        NamedField primaryEmailField = new NamedField("primaryEmail", "com.krista.fields.Email",
            Map.of("allowedDomains", List.of("company.com")), createOptionsMap(true));
        nestedFields.add(primaryEmailField);

        // Secondary email
        NamedField secondaryEmailField = new NamedField("secondaryEmail", "com.krista.fields.Email",
            Map.of("allowedDomains", List.of("example.org", "gmail.com")), createOptionsMap(false));
        nestedFields.add(secondaryEmailField);

        // Work phone
        NamedField workPhoneField = new NamedField("workPhone", "com.krista.fields.Phone",
            Map.of("countryCode", 1), createOptionsMap(false));
        nestedFields.add(workPhoneField);

        // Level 3: Nested MultiField - Home Address
        NamedField homeAddressField = createHomeAddressMultiField();
        nestedFields.add(homeAddressField);

        attributes.put("fields", nestedFields);
        return createField("com.krista.fields.MultiField", attributes, true);
    }

    private NamedField createHomeAddressMultiField() {
        Map<String, Object> attributes = new HashMap<>();
        List<NamedField> addressFields = new ArrayList<>();

        // Street
        NamedField streetField = new NamedField("street", "com.krista.fields.Street",
            Map.of("minLength", 5, "maxLength", 100), createOptionsMap(true));
        addressFields.add(streetField);

        // City
        NamedField cityField = new NamedField("city", "com.krista.fields.City",
            Map.of("minLength", 2, "maxLength", 50), createOptionsMap(true));
        addressFields.add(cityField);

        // State
        NamedField stateField = new NamedField("state", "com.krista.fields.State",
            Map.of("minLength", 2, "maxLength", 2), createOptionsMap(true));
        addressFields.add(stateField);

        // Zip Code
        NamedField zipCodeField = new NamedField("zipCode", "com.krista.fields.PostalCode",
            Map.of("pattern", "\\d{5}"), createOptionsMap(true));
        addressFields.add(zipCodeField);

        // Country
        NamedField countryField = new NamedField("country", "com.krista.fields.Country",
            Map.of(), createOptionsMap(false));
        addressFields.add(countryField);

        attributes.put("fields", addressFields);
        return new NamedField("homeAddress", "com.krista.fields.MultiField",
            attributes, createOptionsMap(true));
    }

    private Field createEmploymentMultiField() {
        Map<String, Object> attributes = new HashMap<>();
        List<NamedField> nestedFields = new ArrayList<>();

        // Department
        NamedField departmentField = new NamedField("department", "com.krista.fields.Text",
            Map.of("minLength", 2, "maxLength", 50), createOptionsMap(true));
        nestedFields.add(departmentField);

        // Position
        NamedField positionField = new NamedField("position", "com.krista.fields.Text",
            Map.of("minLength", 3, "maxLength", 100), createOptionsMap(true));
        nestedFields.add(positionField);

        // Manager
        NamedField managerField = new NamedField("manager", "com.krista.fields.Person",
            Map.of("minLength", 2, "maxLength", 100), createOptionsMap(false));
        nestedFields.add(managerField);

        // Start Date
        NamedField startDateField = new NamedField("startDate", "com.krista.fields.Date",
            Map.of(), createOptionsMap(true));
        nestedFields.add(startDateField);

        // Level 3: Nested MultiField - Benefits
        NamedField benefitsField = createBenefitsMultiField();
        nestedFields.add(benefitsField);

        attributes.put("fields", nestedFields);
        return createField("com.krista.fields.MultiField", attributes, true);
    }

    private NamedField createBenefitsMultiField() {
        Map<String, Object> attributes = new HashMap<>();
        List<NamedField> benefitFields = new ArrayList<>();

        // Health Insurance
        NamedField healthInsuranceField = new NamedField("healthInsurance", "com.krista.fields.Switch",
            Map.of(), createOptionsMap(true));
        benefitFields.add(healthInsuranceField);

        // Dental Insurance
        NamedField dentalInsuranceField = new NamedField("dentalInsurance", "com.krista.fields.Switch",
            Map.of(), createOptionsMap(false));
        benefitFields.add(dentalInsuranceField);

        // Vision Insurance
        NamedField visionInsuranceField = new NamedField("visionInsurance", "com.krista.fields.Switch",
            Map.of(), createOptionsMap(false));
        benefitFields.add(visionInsuranceField);

        // Retirement Plan
        NamedField retirementPlanField = new NamedField("retirementPlan", "com.krista.fields.PickOne",
            Map.of("options", List.of("401k", "403b", "Pension", "None")), createOptionsMap(false));
        benefitFields.add(retirementPlanField);

        // Vacation Days
        NamedField vacationDaysField = new NamedField("vacationDays", "com.krista.fields.Number",
            Map.of("minValue", 0.0, "maxValue", 50.0), createOptionsMap(false));
        benefitFields.add(vacationDaysField);

        // Sick Days
        NamedField sickDaysField = new NamedField("sickDays", "com.krista.fields.Number",
            Map.of("minValue", 0.0, "maxValue", 30.0), createOptionsMap(false));
        benefitFields.add(sickDaysField);

        attributes.put("fields", benefitFields);
        return new NamedField("benefits", "com.krista.fields.MultiField",
            attributes, createOptionsMap(false));
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

    private void createTestOutputFieldValueMaps() {
        // Valid output field value map - all fields pass validation
        validOutputFieldValueMap = new HashMap<>();
        validOutputFieldValueMap.put("name", "Alice Johnson");
        validOutputFieldValueMap.put("age", 30.0);
        validOutputFieldValueMap.put("email", "alice@company.com");
        validOutputFieldValueMap.put("phone", 5559876543L);
        validOutputFieldValueMap.put("isActive", true);
        validOutputFieldValueMap.put("category", "Employee");
        validOutputFieldValueMap.put("skills", List.of("Java", "Python"));
        validOutputFieldValueMap.put("salary", 85000.0);
        validOutputFieldValueMap.put("hireDate", 1640995200000L); // Jan 1, 2022
        validOutputFieldValueMap.put("projects", List.of("Project Delta", "Project Echo"));
        validOutputFieldValueMap.put("teamMembers", List.of(
            Map.of("memberName", "Alice Smith", "memberEmail", "alice@company.com", "memberRole", "Developer"),
            Map.of("memberName", "Bob Johnson", "memberEmail", "bob@company.com", "memberRole", "Designer")
        ));

        // Valid nested contact info
        validOutputFieldValueMap.put("contactInfo", Map.of(
            "primaryEmail", "alice.primary@company.com",
            "secondaryEmail", "alice.secondary@example.org",
            "workPhone", 5551112222L,
            "homeAddress", Map.of(
                "street", "456 Oak Avenue",
                "city", "Boston",
                "state", "MA",
                "zipCode", "02101",
                "country", "USA"
            )
        ));

        // Valid nested employment info
        validOutputFieldValueMap.put("employment", Map.of(
            "department", "Product Development",
            "position", "Lead Software Engineer",
            "manager", "Bob Wilson",
            "startDate", 1640995200000L,
            "benefits", Map.of(
                "healthInsurance", true,
                "dentalInsurance", true,
                "visionInsurance", false,
                "retirementPlan", "401k",
                "vacationDays", 20.0,
                "sickDays", 12.0
            )
        ));

        // Invalid output field value map - multiple validation errors with attribute violations
        invalidOutputFieldValueMap = new HashMap<>();
        invalidOutputFieldValueMap.put("name", "A"); // Too short (minLength: 2)
        invalidOutputFieldValueMap.put("age", 150.0); // Too high (maxValue: 100)
        invalidOutputFieldValueMap.put("email", "invalid@wrongdomain.com"); // Wrong domain (allowedDomains)
        invalidOutputFieldValueMap.put("phone", "invalid-phone"); // Wrong type (should be Number for PhoneNumber conversion)
        invalidOutputFieldValueMap.put("isActive", "not-boolean"); // Wrong type
        invalidOutputFieldValueMap.put("category", "InvalidCategory"); // Not in options
        invalidOutputFieldValueMap.put("skills", List.of("Java", "Python", "JavaScript", "C++")); // Too many selections (maxSelection: 3)
        invalidOutputFieldValueMap.put("salary", 250000.0); // Too high (maxValue: 200000)
        invalidOutputFieldValueMap.put("hireDate", "invalid-date"); // Wrong type
        invalidOutputFieldValueMap.put("projects", List.of("AB", "CD")); // Elements too short (minLength: 3)
        invalidOutputFieldValueMap.put("teamMembers", List.of(
            Map.of("memberName", "A", "memberEmail", "invalid@wrongdomain.com", "memberRole", "InvalidRole"), // Multiple errors
            Map.of("memberName", "Bob Johnson", "memberEmail", "bob@company.com", "memberRole", "Designer") // Valid element
        ));

        // Invalid nested contact info with attribute violations
        invalidOutputFieldValueMap.put("contactInfo", Map.of(
            "primaryEmail", "invalid@wrongdomain.com", // Wrong domain (allowedDomains)
            "secondaryEmail", "also-invalid@baddomain.com", // Wrong domain (allowedDomains)
            "workPhone", "bad-phone-format", // Wrong type (should be Number for PhoneNumber conversion)
            "homeAddress", Map.of(
                "street", "123", // Too short (minLength: 5)
                "city", "X", // Too short (minLength: 2)
                "state", "INVALID", // Too long (maxLength: 2)
                "zipCode", "invalid-zip", // Wrong pattern (pattern: \\d{5})
                "country", ""
            )
        ));

        // Invalid nested employment info with attribute violations
        invalidOutputFieldValueMap.put("employment", Map.of(
            "department", "X", // Too short (minLength: 2)
            "position", "AB", // Too short (minLength: 3)
            "manager", "X", // Too short (minLength: 2)
            "startDate", "invalid-date",
            "benefits", Map.of(
                "healthInsurance", "not-boolean", // Wrong type
                "dentalInsurance", "also-not-boolean", // Wrong type
                "visionInsurance", "still-not-boolean", // Wrong type
                "retirementPlan", "InvalidPlan", // Not in options
                "vacationDays", 100.0, // Too high (maxValue: 50)
                "sickDays", 50.0 // Too high (maxValue: 30)
            )
        ));

        // Partially valid output field value map - mix of valid and invalid with attribute testing
        partiallyValidOutputFieldValueMap = new HashMap<>();
        partiallyValidOutputFieldValueMap.put("name", "Charlie Brown"); // Valid
        partiallyValidOutputFieldValueMap.put("age", 200.0); // Invalid - too high (maxValue: 100)
        partiallyValidOutputFieldValueMap.put("email", "charlie@company.com"); // Valid
        partiallyValidOutputFieldValueMap.put("phone", "invalid-format"); // Invalid type (should be Number for PhoneNumber conversion)
        partiallyValidOutputFieldValueMap.put("isActive", true); // Valid
        partiallyValidOutputFieldValueMap.put("category", "Employee"); // Valid
        partiallyValidOutputFieldValueMap.put("skills", List.of("Java")); // Valid (within minSelection: 1, maxSelection: 3)
        partiallyValidOutputFieldValueMap.put("salary", 65000.0); // Valid
        partiallyValidOutputFieldValueMap.put("hireDate", 1609459200000L); // Valid
        partiallyValidOutputFieldValueMap.put("projects", List.of("Valid Project", "AB")); // Mixed - one invalid element (too short)

        // Partially valid nested contact info
        partiallyValidOutputFieldValueMap.put("contactInfo", Map.of(
            "primaryEmail", "charlie.primary@company.com", // Valid
            "secondaryEmail", "charlie@wrongdomain.com", // Invalid domain (allowedDomains)
            "workPhone", 5553334444L, // Valid
            "homeAddress", Map.of(
                "street", "789 Pine Street", // Valid
                "city", "Chicago", // Valid
                "state", "IL", // Valid
                "zipCode", "invalid", // Invalid pattern (pattern: \\d{5})
                "country", "USA" // Valid
            )
        ));

        // Partially valid nested employment info
        partiallyValidOutputFieldValueMap.put("employment", Map.of(
            "department", "Marketing", // Valid
            "position", "Marketing Manager", // Valid
            "manager", "X", // Invalid - too short (minLength: 2)
            "startDate", 1609459200000L, // Valid
            "benefits", Map.of(
                "healthInsurance", true, // Valid
                "dentalInsurance", false, // Valid
                "visionInsurance", true, // Valid
                "retirementPlan", "InvalidPlan", // Invalid - not in options
                "vacationDays", 15.0, // Valid
                "sickDays", 8.0 // Valid
            )
        ));
    }

    // ========== Test Methods ==========

    @Test
    public void testValidate_WithAllValidFields_ReturnsNoErrors() {
        // Act
        Map<String, Object> result = LLMResponseValidator.validate(complexFreeForm, validOutputFieldValueMap);

        // Assert
        assertNotNull(result);

        // Check that no fields are present in validation result when there are no errors
        // (validators now return null instead of empty maps/lists when there are no errors)
        assertTrue("Validation result should be empty when all fields are valid", result.isEmpty());
    }

    @Test
    public void testValidate_WithAllInvalidFields_ReturnsMultipleErrors() {
        // Act
        Map<String, Object> result = LLMResponseValidator.validate(complexFreeForm, invalidOutputFieldValueMap);

        // Assert
        assertNotNull(result);

        // Check specific field validation errors with attribute violations

        // Text field with minLength violation
        assertTrue(result.containsKey("name"));
        List<?> nameErrors = (List<?>) result.get("name");
        assertFalse("Name field should have validation errors", nameErrors.isEmpty());

        // Number field with maxValue violation
        assertTrue(result.containsKey("age"));
        List<?> ageErrors = (List<?>) result.get("age");
        assertFalse("Age field should have validation errors", ageErrors.isEmpty());

        // Email field with allowedDomains violation
        assertTrue(result.containsKey("email"));
        List<?> emailErrors = (List<?>) result.get("email");
        assertFalse("Email field should have validation errors", emailErrors.isEmpty());

        // PickOne field with options violation
        assertTrue(result.containsKey("category"));
        List<?> categoryErrors = (List<?>) result.get("category");
        assertFalse("Category field should have validation errors", categoryErrors.isEmpty());

        // Checkboxes field with maxSelection violation
        assertTrue(result.containsKey("skills"));
        List<?> skillsErrors = (List<?>) result.get("skills");
        assertFalse("Skills field should have validation errors", skillsErrors.isEmpty());

        // Currency field with maxValue violation
        assertTrue(result.containsKey("salary"));
        List<?> salaryErrors = (List<?>) result.get("salary");
        assertFalse("Salary field should have validation errors", salaryErrors.isEmpty());
    }

    @Test
    public void testValidate_WithNestedMultiFieldErrors_ReturnsNestedErrorStructure() {
        // Act
        Map<String, Object> result = LLMResponseValidator.validate(complexFreeForm, invalidOutputFieldValueMap);

        // Assert
        assertNotNull(result);

        // Check nested MultiField validation errors
        assertTrue(result.containsKey("contactInfo"));
        Object contactInfoErrors = result.get("contactInfo");
        assertTrue("ContactInfo should have nested error structure", contactInfoErrors instanceof Map);

        Map<String, Object> contactErrors = (Map<String, Object>) contactInfoErrors;
        assertFalse("ContactInfo should have validation errors", contactErrors.isEmpty());

        // Check 3-level nested MultiField (homeAddress within contactInfo)
        if (contactErrors.containsKey("homeAddress")) {
            Object homeAddressErrors = contactErrors.get("homeAddress");
            assertTrue("HomeAddress should have nested error structure", homeAddressErrors instanceof Map);

            Map<String, Object> addressErrors = (Map<String, Object>) homeAddressErrors;
            assertFalse("HomeAddress should have validation errors", addressErrors.isEmpty());

            // Check specific address field errors with attribute violations
            if (addressErrors.containsKey("street")) {
                List<?> streetErrors = (List<?>) addressErrors.get("street");
                assertFalse("Street field should have minLength validation errors", streetErrors.isEmpty());
            }

            if (addressErrors.containsKey("zipCode")) {
                List<?> zipErrors = (List<?>) addressErrors.get("zipCode");
                assertFalse("ZipCode field should have pattern validation errors", zipErrors.isEmpty());
            }
        }

        // Check employment MultiField with nested benefits MultiField
        assertTrue(result.containsKey("employment"));
        Object employmentErrors = result.get("employment");
        assertTrue("Employment should have nested error structure", employmentErrors instanceof Map);

        Map<String, Object> empErrors = (Map<String, Object>) employmentErrors;
        assertFalse("Employment should have validation errors", empErrors.isEmpty());

        // Check 3-level nested benefits MultiField
        if (empErrors.containsKey("benefits")) {
            Object benefitsErrors = empErrors.get("benefits");
            assertTrue("Benefits should have nested error structure", benefitsErrors instanceof Map);

            Map<String, Object> benErrors = (Map<String, Object>) benefitsErrors;
            assertFalse("Benefits should have validation errors", benErrors.isEmpty());

            // Check specific benefits field errors
            if (benErrors.containsKey("retirementPlan")) {
                List<?> retirementErrors = (List<?>) benErrors.get("retirementPlan");
                assertFalse("RetirementPlan should have options validation errors", retirementErrors.isEmpty());
            }

            if (benErrors.containsKey("vacationDays")) {
                List<?> vacationErrors = (List<?>) benErrors.get("vacationDays");
                assertFalse("VacationDays should have maxValue validation errors", vacationErrors.isEmpty());
            }
        }
    }

    @Test
    public void testValidate_WithPartiallyValidFields_ReturnsMixedResults() {
        // Act
        Map<String, Object> result = LLMResponseValidator.validate(complexFreeForm, partiallyValidOutputFieldValueMap);

        // Assert
        assertNotNull(result);

        // Check that invalid fields have errors
        assertTrue(result.containsKey("age"));
        List<?> ageErrors = (List<?>) result.get("age");
        assertFalse("Age field should have validation errors", ageErrors.isEmpty());

        // Check mixed validation in nested structures
        assertTrue(result.containsKey("contactInfo"));
        Object contactInfoErrors = result.get("contactInfo");
        assertTrue("ContactInfo should have nested error structure", contactInfoErrors instanceof Map);

        Map<String, Object> contactErrors = (Map<String, Object>) contactInfoErrors;
        // Should have some errors but not all fields will have errors

        // Check that some nested fields are valid while others are invalid
        if (contactErrors.containsKey("homeAddress")) {
            Map<String, Object> addressErrors = (Map<String, Object>) contactErrors.get("homeAddress");
            // zipCode should have errors (invalid pattern) but other fields should be valid
            if (addressErrors.containsKey("zipCode")) {
                List<?> zipErrors = (List<?>) addressErrors.get("zipCode");
                assertFalse("ZipCode should have pattern validation errors", zipErrors.isEmpty());
            }
        }
    }

    @Test
    public void testValidate_WithListFieldValidation_ValidatesElementsCorrectly() {
        // Act
        Map<String, Object> result = LLMResponseValidator.validate(complexFreeForm, partiallyValidOutputFieldValueMap);

        // Assert
        assertNotNull(result);

        // Check List field validation - projects list has mixed valid/invalid elements
        assertTrue(result.containsKey("projects"));
        List<?> projectsErrors = (List<?>) result.get("projects");
        // List validation should catch elements that are too short (minLength: 3)
        assertFalse("Projects list should have validation errors for short elements", projectsErrors.isEmpty());
    }

    @Test
    public void testValidate_WithListContainingMultiField_ValidatesNestedStructuresCorrectly() {
        // Act
        Map<String, Object> result = LLMResponseValidator.validate(complexFreeForm, invalidOutputFieldValueMap);

        // Assert
        assertNotNull(result);

        // Check List field with nested MultiField validation - teamMembers list
        assertTrue(result.containsKey("teamMembers"));
        List<?> teamMembersErrors = (List<?>) result.get("teamMembers");

        // Should have validation errors for the first element with multiple field violations
        assertFalse("TeamMembers list should have validation errors for nested MultiField elements", teamMembersErrors.isEmpty());

        // Check that errors include index information and nested field paths
        boolean foundNameError = false;
        boolean foundEmailError = false;
        boolean foundRoleError = false;

        for (Object error : teamMembersErrors) {
            String errorMsg = error.toString();
            if (errorMsg.contains("Element at index 0") && errorMsg.contains("memberName")) {
                foundNameError = true;
            }
            if (errorMsg.contains("Element at index 0") && errorMsg.contains("memberEmail")) {
                foundEmailError = true;
            }
            if (errorMsg.contains("Element at index 0") && errorMsg.contains("memberRole")) {
                foundRoleError = true;
            }
        }

        assertTrue("Should find memberName validation error with index information", foundNameError);
        assertTrue("Should find memberEmail validation error with index information", foundEmailError);
        assertTrue("Should find memberRole validation error with index information", foundRoleError);
    }

    @Test
    public void testValidate_WithAttributeConstraints_ValidatesCorrectly() {
        // Act
        Map<String, Object> result = LLMResponseValidator.validate(complexFreeForm, invalidOutputFieldValueMap);

        // Assert
        assertNotNull(result);

        // Test various attribute constraints

        // minLength/maxLength constraints on Text fields
        assertTrue(result.containsKey("name"));
        List<?> nameErrors = (List<?>) result.get("name");
        assertFalse("Name should fail minLength constraint", nameErrors.isEmpty());

        // minValue/maxValue constraints on Number fields
        assertTrue(result.containsKey("age"));
        List<?> ageErrors = (List<?>) result.get("age");
        assertFalse("Age should fail maxValue constraint", ageErrors.isEmpty());

        // allowedDomains constraint on Email fields
        assertTrue(result.containsKey("email"));
        List<?> emailErrors = (List<?>) result.get("email");
        assertFalse("Email should fail allowedDomains constraint", emailErrors.isEmpty());

        // options constraint on PickOne fields
        assertTrue(result.containsKey("category"));
        List<?> categoryErrors = (List<?>) result.get("category");
        assertFalse("Category should fail options constraint", categoryErrors.isEmpty());

        // minSelection/maxSelection constraints on Checkboxes fields
        assertTrue(result.containsKey("skills"));
        List<?> skillsErrors = (List<?>) result.get("skills");
        assertFalse("Skills should fail maxSelection constraint", skillsErrors.isEmpty());
    }

    @Test
    public void testValidate_WithEmptyOutputMap_HandlesGracefully() {
        // Arrange
        Map<String, Object> emptyOutputMap = new HashMap<>();

        // Act
        Map<String, Object> result = LLMResponseValidator.validate(complexFreeForm, emptyOutputMap);

        // Assert
        assertNotNull(result);
        // Should handle missing values gracefully - converters should handle null/missing values
        for (String fieldName : complexFreeForm.getTypedValues().keySet()) {
            assertFalse("Field " + fieldName + " should be absent in validation result", result.containsKey(fieldName));
        }
    }
}
