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

import app.krista.extensions.hook.common.fieldToLLM.FieldAttributeFilter;
import app.krista.extensions.hook.common.fieldToLLM.FieldMetadataExtractor;
import app.krista.extensions.hook.common.fieldToLLM.FieldToLLMFieldConverter;
import app.krista.extensions.hook.common.fieldToLLM.FieldToLLMFieldConverterRegistry;
import app.krista.extensions.hook.common.fieldToLLM.valueConverter.FieldValueToLLMValueConverter;
import app.krista.extensions.hook.common.fieldToLLM.valueConverter.FieldValueToLLMValueConverterRegistry;
import app.krista.extensions.hook.common.llmToField.LLMToFieldValueConverter;
import app.krista.extensions.hook.common.llmToField.LLMToFieldValueConverterRegistry;
import app.krista.extensions.hook.common.models.LLMField;
import app.krista.model.base.FreeForm;
import app.krista.model.field.Field;
import app.krista.model.field.NamedField;

import java.util.*;

@SuppressWarnings("unchecked")
public class FreeFormHelper {

    private static final FieldToLLMFieldConverterRegistry fieldConverterRegistry = FieldToLLMFieldConverterRegistry.create();
    private static final FieldValueToLLMValueConverterRegistry valueConverterRegistry = FieldValueToLLMValueConverterRegistry.create();
    private static final LLMToFieldValueConverterRegistry llmToFieldRegistry = LLMToFieldValueConverterRegistry.create();

    private FreeFormHelper(){}


    public static FreeForm updateFreeFormValues(
            FreeForm freeForm, Map<String, Object> outputFieldValueMap, Map<String, Object> validationErrors) {

        // Create a new FreeForm from scratch
        FreeForm updatedFreeForm = new FreeForm();

        // Get all typed values from the original FreeForm
        Map<String, FreeForm.TypedValue> originalTypedValues = extractTypedValues(freeForm);

        // Process each field and create updated TypedValues
        for (Map.Entry<String, FreeForm.TypedValue> entry : originalTypedValues.entrySet()) {
            String fieldName = entry.getKey();
            FreeForm.TypedValue originalTypedValue = entry.getValue();

            FreeForm.TypedValue updatedTypedValue;

            // Check if this field has validation errors (could be nested)
            Object fieldValidationErrors = validationErrors.get(fieldName);

            if (fieldValidationErrors != null && hasValidationErrors(fieldValidationErrors)) {
                // Handle partial updates for nested structures
                if (outputFieldValueMap.containsKey(fieldName) && isNestedStructure(originalTypedValue.getField())) {
                    Object llmValue = outputFieldValueMap.get(fieldName);
                    Object partiallyUpdatedValue = applyPartialUpdates(
                        originalTypedValue.getValue(), llmValue, fieldValidationErrors, originalTypedValue.getField());
                    updatedTypedValue = createUpdatedTypedValue(originalTypedValue, partiallyUpdatedValue);
                } else {
                    // For simple fields or complete validation failures, keep original
                    updatedTypedValue = originalTypedValue;
                }
            }
            // Update field value if present in outputFieldValueMap and no validation errors
            else if (outputFieldValueMap.containsKey(fieldName)) {
                Object llmValue = outputFieldValueMap.get(fieldName);
                Object originalValue = originalTypedValue.getValue();
                Field fieldDef = originalTypedValue.getField();
                
                // Special handling for MultiField to merge values
                if (isNestedStructure(fieldDef) && originalValue instanceof Map && llmValue instanceof Map) {
                    // Merge the original and new values
                    Map<String, Object> mergedValue = mergeMultiFieldValues(
                        (Map<String, Object>)originalValue, 
                        (Map<String, Object>)llmValue, fieldDef);
                    
                    // Convert the merged value
                    Object convertedFieldValue = convertLLMValueToFieldValue(fieldDef, mergedValue);
                    updatedTypedValue = createUpdatedTypedValue(originalTypedValue, convertedFieldValue);
                } else {
                    // For non-MultiField types, just convert and replace
                    Object convertedFieldValue = convertLLMValueToFieldValue(fieldDef, llmValue);
                    updatedTypedValue = createUpdatedTypedValue(originalTypedValue, convertedFieldValue);
                }
            }
            // Keep original value if no update needed
            else {
                updatedTypedValue = originalTypedValue;
            }

            // Put the TypedValue directly into FreeForm
            // FreeForm accepts TypedValue objects directly in its put method
            updatedFreeForm.put(fieldName, updatedTypedValue);
        }

        return updatedFreeForm;
    }




    public static Map<String, Object> convertFreeFormToFieldValueMap(FreeForm freeForm) throws Exception {
        return convertFreeFormToFieldValueMap(freeForm, FieldAttributeFilter.PromptContext.DEFAULT);
    }

    public static Map<String, Object> convertFreeFormToFieldValueMap(FreeForm freeForm, FieldAttributeFilter.PromptContext context) throws Exception {
        Map<String, Object> result = new LinkedHashMap<>();

        // Extract all typed values from the FreeForm
        Map<String, FreeForm.TypedValue> typedValues = extractTypedValues(freeForm);

        // DEBUG: Log all fields in FreeForm with complete details
        System.out.println("=== DEBUG: FreeForm contains " + typedValues.size() + " fields ===");
        for (Map.Entry<String, FreeForm.TypedValue> entry : typedValues.entrySet()) {
            String fieldName = entry.getKey();
            FreeForm.TypedValue typedValue = entry.getValue();
            Field field = typedValue.getField();
            Object fieldValue = typedValue.getValue();

            System.out.println("Field: " + fieldName);
            System.out.println("  FieldType: " + (field != null ? field.getFieldType() : "null"));
            System.out.println("  ValueType: " + (fieldValue != null ? fieldValue.getClass().getSimpleName() : "null"));
            if (fieldValue != null && fieldValue.toString().contains("EntityReference")) {
                System.out.println("  Value: [EntityReference - details hidden for security]");
            } else {
                System.out.println("  Value: " + fieldValue);
            }

            // Log complete field attributes and options
            if (field != null) {
                System.out.println("  Attributes: " + field.getAttributeValues());
                System.out.println("  Options: " + field.getOptions());

                // Check isSecured specifically
                boolean isSecured = FieldMetadataExtractor.extractIsSecured(field);
                System.out.println("  isSecured: " + isSecured);
            }
            System.out.println("---");
        }

        for (Map.Entry<String, FreeForm.TypedValue> entry : typedValues.entrySet()) {
            String fieldName = entry.getKey();
            FreeForm.TypedValue typedValue = entry.getValue();

            // Get the field definition from TypedValue
            Field field = typedValue.getField();
            Object fieldValue = typedValue.getValue();

            if (field != null) {
                // Check if field is secured - secured fields cannot be rendered as text
                if (FieldMetadataExtractor.extractIsSecured(field)) {
                    throw new Exception("Secured field '" + fieldName + "' cannot be rendered as text. " +
                        "No converter registered for field type: " + field.getFieldType());
                }

                // Convert field definition using FieldToLLMFieldConverter with context
                FieldToLLMFieldConverter fieldConverter = fieldConverterRegistry.get(field.getFieldType());
                LLMField llmField = fieldConverter.extract(field, context);

                Object convertedValue = null;

                // Convert field value using FieldValueToLLMValueConverter if value exists
                if (fieldValue != null) {
                    try {
                        FieldValueToLLMValueConverter<?> valueConverter = valueConverterRegistry.get(field.getFieldType());
                        if (valueConverter != null) {
                            convertedValue = valueConverter.extract(field, fieldValue);
                        } else {
                            convertedValue = fieldValue; // Use raw value if no converter
                        }
                    } catch (Exception e) {
                        // If conversion fails, use null value
                        System.out.println("Failed to convert field value for " + fieldName + ": " + e.getMessage());
                    }
                }

                // Create field value map entry
                Map<String, Object> fieldEntry = new LinkedHashMap<>();
                fieldEntry.put("fieldType", llmField.fieldType());
                fieldEntry.put("isMandatoryField", llmField.isMandatory());
                fieldEntry.put("attributes", llmField.attributeValues());
                fieldEntry.put("value", convertedValue);

                result.put(fieldName, fieldEntry);
            }
        }

        return result;
    }

    public static boolean isFreeFormFilled(FreeForm freeForm, boolean checkOnlyMandatoryFields) {
        Map<String, FreeForm.TypedValue> typedValues = extractTypedValues(freeForm);

        for (Map.Entry<String, FreeForm.TypedValue> entry : typedValues.entrySet()) {
            FreeForm.TypedValue typedValue = entry.getValue();
            Field field = typedValue.getField();
            Object fieldValue = typedValue.getValue();

            if (field != null) {
                boolean isMandatory = FieldMetadataExtractor.extractIsMandatory(field);

                if (!checkOnlyMandatoryFields || isMandatory) {
                    if (fieldValue == null || isValueEmpty(fieldValue)) {
                        return false;
                    }
                    
                    // Check nested structures recursively
                    if (isNestedStructure(field)) {
                        if (!isNestedStructureFilled(fieldValue, field, checkOnlyMandatoryFields)) {
                            return false;
                        }
                    }
                }
            }
        }
        return true;
    }

    private static boolean isNestedStructureFilled(Object value, Field field, boolean checkOnlyMandatoryFields) {
        if (value == null || isValueEmpty(value)) {
            return false;
        }
        
        String fieldType = field.getFieldType();
        
        if ("com.krista.fields.MultiField".equals(fieldType) && value instanceof Map) {
            Map<String, Object> multiFieldMap = (Map<String, Object>) value;
            
            // For MultiField, check each nested field
            for (Map.Entry<String, Object> nestedEntry : multiFieldMap.entrySet()) {
                Object nestedValue = nestedEntry.getValue();
                Field nestedField = getNestedField(field, nestedEntry.getKey());
                
                if (nestedField != null) {
                    boolean isNestedMandatory = FieldMetadataExtractor.extractIsMandatory(nestedField);
                    
                    if (!checkOnlyMandatoryFields || isNestedMandatory) {
                        if (nestedValue == null || isValueEmpty(nestedValue)) {
                            return false;
                        }
                        
                        // Recursively check nested structures
                        if (isNestedStructure(nestedField)) {
                            if (!isNestedStructureFilled(nestedValue, nestedField, checkOnlyMandatoryFields)) {
                                return false;
                            }
                        }
                    }
                }
            }
        } else if ("com.krista.fields.List".equals(fieldType) && value instanceof List) {
            List<?> listValue = (List<?>) value;
            if (listValue.isEmpty()) {
                return false;
            }
            
            // For List, check if elements are properly filled
            Field elementField = getListElementField(field);
            if (elementField != null && isNestedStructure(elementField)) {
                for (Object element : listValue) {
                    if (!isNestedStructureFilled(element, elementField, checkOnlyMandatoryFields)) {
                        return false;
                    }
                }
            }
        }
        
        return true;
    }

    public static boolean hasAtLeastOneValue(FreeForm freeForm, boolean checkOnlyMandatoryFields) {
        // Extract all typed values from the FreeForm
        Map<String, FreeForm.TypedValue> typedValues = extractTypedValues(freeForm);

        for (Map.Entry<String, FreeForm.TypedValue> entry : typedValues.entrySet()) {
            FreeForm.TypedValue typedValue = entry.getValue();

            // Get the field definition from TypedValue
            Field field = typedValue.getField();
            Object fieldValue = typedValue.getValue();

            if (field != null) {
                // Check if field is mandatory
                boolean isMandatory = FieldMetadataExtractor.extractIsMandatory(field);

                // If checkOnlyMandatoryFields is true, only consider mandatory fields
                if (checkOnlyMandatoryFields && !isMandatory) {
                    continue; // Skip non-mandatory fields
                }

                // If we find any non-null and non-empty value, return true
                if (fieldValue != null && !isValueEmpty(fieldValue)) {
                    return true;
                }
            }
        }

        return false;
    }



    // Helper methods

    /**
     * Extracts all typed values from FreeForm
     */
    public static Map<String, FreeForm.TypedValue> extractTypedValues(FreeForm freeForm) {
        return freeForm.getTypedValues();
    }

    /**
     * Creates a new TypedValue with updated value
     */
    public static FreeForm.TypedValue createUpdatedTypedValue(FreeForm.TypedValue originalTypedValue, Object newFieldValue) {
        Field field = originalTypedValue.getField();
        String typeDescription = originalTypedValue.getTypeDescription();

        if (field != null) {
            // The newFieldValue is already converted from LLM format to field format
            // so we can directly use it to create the new TypedValue
            return new FreeForm.TypedValue(typeDescription, field, newFieldValue);
        }
        return originalTypedValue;
    }

    /**
     * Converts LLM value back to field value using LLMToFieldValueConverter
     */
    private static Object convertLLMValueToFieldValue(Field field, Object llmValue) {
        if (field == null || llmValue == null) {
            return llmValue;
        }

        try {
            String fieldType = field.getFieldType();
            
            // Special handling for MultiField to merge values
            if ("com.krista.fields.MultiField".equals(fieldType) && llmValue instanceof Map) {
                return mergeMultiFieldValues(field, llmValue);
            }
            
            LLMToFieldValueConverter<?> converter = llmToFieldRegistry.get(fieldType);
            if (converter != null) {
                return converter.extract(field, llmValue);
            } else {
                // If no converter available, return the LLM value as-is
                return llmValue;
            }
        } catch (Exception e) {
            // If conversion fails, return the original LLM value
            return llmValue;
        }
    }

    /**
     * Merges MultiField values from LLM with existing values
     */
    private static Object mergeMultiFieldValues(Field field, Object llmValue) {
        if (!(llmValue instanceof Map)) {
            return llmValue;
        }

        // Get the converter for MultiField
        LLMToFieldValueConverter<?> converter = llmToFieldRegistry.get("com.krista.fields.MultiField");
        if (converter == null) {
            return llmValue;
        }
        
        try {
            // Convert the LLM value to a field value (this will be a Map for MultiField)
            Object convertedValue = converter.extract(field, llmValue);
            
            if (convertedValue instanceof Map) {
                return convertedValue;
            }
        } catch (Exception e) {
            System.out.println("Error converting MultiField value: " + e.getMessage());
        }
        
        return llmValue;
    }

    /**
     * Merges original MultiField values with new values from LLM
     */
    private static Map<String, Object> mergeMultiFieldValues(
            Map<String, Object> originalMap, Map<String, Object> llmMap, Field parentField) {
        // Create a new map with all original values converted to LLM format
        Map<String, Object> mergedMap = new HashMap<>();
        
        // Convert original field objects to LLM representation first
        for (Map.Entry<String, Object> entry : originalMap.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            
            if (value != null) {
                // Get nested field definition to use proper converter
                Field nestedField = getNestedField(parentField, key);
                Object llmValue = convertFieldValueToLLMValue(key, value, nestedField);
                mergedMap.put(key, llmValue);
            } else {
                mergedMap.put(key, null);
            }
        }
        
        // Add or update with values from LLM
        for (Map.Entry<String, Object> entry : llmMap.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            
            // If the value is null, don't overwrite existing value
            if (value != null) {
                // If both original and new values are maps, recursively merge them
                if (value instanceof Map && mergedMap.get(key) instanceof Map) {
                    Field nestedField = getNestedField(parentField, key);
                    mergedMap.put(key, mergeMultiFieldValues(
                        (Map<String, Object>)mergedMap.get(key), 
                        (Map<String, Object>)value, nestedField));
                } else {
                    // Otherwise just update the value
                    mergedMap.put(key, value);
                }
            }
        }
        
        return mergedMap;
    }

    private static Object convertFieldValueToLLMValue(String fieldName, Object fieldValue, Field field) {
        if (field == null || fieldValue == null) {
            return fieldValue;
        }

        try {
            String fieldType = field.getFieldType();
            FieldValueToLLMValueConverter<?> converter = valueConverterRegistry.get(fieldType);

            if (converter != null) {
                return converter.extract(field, fieldValue);
            }
        } catch (Exception e) {
            System.out.println("Failed to convert field value to LLM value for field " + fieldName + ": " + e.getMessage());
        }

        // If no converter or conversion fails, return original value
        return fieldValue;
    }

    /**
     * Checks if a value is considered empty
     */
    public static boolean isValueEmpty(Object value) {
        return switch (value) {
            case null -> true;
            case String s -> s.trim().isEmpty();
            case Collection<?> collection -> collection.isEmpty();
            case Map<?, ?> map -> map.isEmpty();
            default -> false;
        };

    }

    private static boolean hasValidationErrors(Object validationErrors) {
        if (validationErrors instanceof List<?>) {
            return !((List<?>) validationErrors).isEmpty();
        } else if (validationErrors instanceof Map<?, ?>) {
            return !((Map<?, ?>) validationErrors).isEmpty();
        }
        return false;
    }

    public static boolean isNestedStructure(Field field) {
        return field != null && (
            "com.krista.fields.MultiField".equals(field.getFieldType()) ||
            "com.krista.fields.List".equals(field.getFieldType())
        );
    }

    private static Object applyPartialUpdates(Object originalValue, Object llmValue,
                                            Object validationErrors, Field field) {
        if ("com.krista.fields.MultiField".equals(field.getFieldType())) {
            return applyPartialMultiFieldUpdates(originalValue, llmValue, validationErrors, field);
        } else if ("com.krista.fields.List".equals(field.getFieldType())) {
            return applyPartialListUpdates(originalValue, llmValue, validationErrors, field);
        }
        return originalValue;
    }

    private static Object applyPartialListUpdates(Object originalValue, Object llmValue,
                                                Object validationErrors, Field field) {
        // Check if validation errors are for the list itself (simple list errors)
        if (validationErrors instanceof List) {
            // Simple list validation errors - discard the entire list
            return originalValue;
        }

        // Check if validation errors are nested (for list elements)
        if (validationErrors instanceof Map && originalValue instanceof List && llmValue instanceof List) {
            List<Object> originalList = (List<Object>) originalValue;
            List<Object> llmList = (List<Object>) llmValue;
            List<Object> resultList = new ArrayList<>();

            // Apply partial updates to each list element
            for (int i = 0; i < originalList.size() && i < llmList.size(); i++) {
                Object originalElement = originalList.get(i);
                Object llmElement = llmList.get(i);

                // Check if this element has validation errors
                Map<String, Object> errorMap = (Map<String, Object>) validationErrors;
                Field elementField = getListElementField(field);
                if (errorMap.isEmpty()) {
                    // No validation errors for any elements - convert the LLM element
                    Object convertedElement = convertLLMValueToFieldValue(elementField, llmElement);
                    resultList.add(convertedElement);
                } else {
                    // There are validation errors for nested fields - apply partial updates
                    if (elementField != null && "com.krista.fields.MultiField".equals(elementField.getFieldType())) {
                        Object partiallyUpdatedElement = applyPartialMultiFieldUpdates(
                            originalElement, llmElement, validationErrors, elementField);
                        resultList.add(partiallyUpdatedElement);
                    } else {
                        // For non-MultiField elements with errors, keep original
                        resultList.add(originalElement);
                    }
                }
            }

            return resultList;
        }

        // Default: discard the entire list if we can't handle the validation errors
        return originalValue;
    }

    private static Field getListElementField(Field listField) {
        try {
            Map<String, Object> attributes = listField.getAttributeValues();
            Object fieldObj = attributes.get("field");
            if (fieldObj instanceof Field) {
                return (Field) fieldObj;
            }
        } catch (Exception e) {
            // If we can't find the element field, return null
        }
        return null;
    }

    private static Object applyPartialMultiFieldUpdates(Object originalValue, Object llmValue,
                                                       Object validationErrors, Field field) {
        if (!(originalValue instanceof Map) || !(llmValue instanceof Map) || !(validationErrors instanceof Map)) {
            return originalValue;
        }

        Map<String, Object> originalMap = (Map<String, Object>) originalValue;
        Map<String, Object> llmMap = (Map<String, Object>) llmValue;
        Map<String, Object> errorMap = (Map<String, Object>) validationErrors;

        Map<String, Object> result = new HashMap<>(originalMap);

        // Update fields that don't have validation errors
        for (Map.Entry<String, Object> entry : llmMap.entrySet()) {
            String fieldName = entry.getKey();
            if (!errorMap.containsKey(fieldName)) {
                // No validation error for this field, so update it
                Field nestedField = getNestedField(field, fieldName);
                Object convertedValue = convertLLMValueToFieldValue(nestedField, entry.getValue());
                result.put(fieldName, convertedValue);
            } else {
                // Field has validation errors - check if it's a nested structure that can be partially updated
                Object nestedErrors = errorMap.get(fieldName);
                if (nestedErrors instanceof Map && entry.getValue() instanceof Map) {
                    // Recursively apply partial updates to nested structure
                    Field nestedField = getNestedField(field, fieldName);
                    assert nestedField != null;
                    Object partiallyUpdated = applyPartialUpdates(
                        originalMap.get(fieldName), entry.getValue(), nestedErrors, nestedField);
                    result.put(fieldName, partiallyUpdated);
                }
                // If it's a simple field with errors or a list with errors, keep original value
            }
        }

        return result;
    }

    public static Field getNestedField(Field parentField, String fieldName) {
        try {
            Map<String, Object> attributes = parentField.getAttributeValues();
            Object fieldsObj = attributes.get("fields");

            if (fieldsObj instanceof List<?> fieldsList) {
                for (Object fieldObj : fieldsList) {
                    // Convert to app.krista.model.field.NamedField
                    NamedField namedField = NamedFieldConverter.convertToAppNamedField(fieldObj);

                    if (namedField != null && fieldName.equals(namedField.getName())) {
                        return namedField;
                    }
                }
            }
        } catch (Exception e) {
            // If we can't find the nested field, return null and let the caller handle it
        }
        return null;
    }




}
