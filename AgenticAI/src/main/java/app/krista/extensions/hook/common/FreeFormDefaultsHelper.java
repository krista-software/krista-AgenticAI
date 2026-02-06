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

import app.krista.extensions.hook.common.fieldToLLM.FieldOptionsExtractor;
import app.krista.model.base.FreeForm;
import app.krista.model.field.Field;
import app.krista.model.field.NamedField;

import java.util.*;

@SuppressWarnings("unchecked")
public class FreeFormDefaultsHelper {

    private FreeFormDefaultsHelper() {
    }

    /**
     * Updates the target FreeForm with values from the source FreeForm.
     * Only matching fields and subfields are updated. Non-matching fields are ignored.
     *
     * @param targetFreeForm  The FreeForm to be updated
     * @param sourceFreeForm  The FreeForm containing values to copy from
     * @param shouldOverwrite Whether to overwrite existing values in target FreeForm
     * @return A new FreeForm with updated values
     */
    public static FreeForm updateFreeFormFromFreeForm(
            FreeForm targetFreeForm, FreeForm sourceFreeForm, boolean shouldOverwrite) {

        // Create a new FreeForm from scratch
        FreeForm updatedFreeForm = new FreeForm();

        // Get all typed values from both FreeForm objects
        Map<String, FreeForm.TypedValue> targetTypedValues = FreeFormHelper.extractTypedValues(targetFreeForm);
        Map<String, FreeForm.TypedValue> sourceTypedValues = FreeFormHelper.extractTypedValues(sourceFreeForm);

        // Process each field in the target FreeForm
        for (Map.Entry<String, FreeForm.TypedValue> entry : targetTypedValues.entrySet()) {
            String fieldName = entry.getKey();
            FreeForm.TypedValue targetTypedValue = entry.getValue();

            FreeForm.TypedValue updatedTypedValue;

            // Check if this field exists in the source FreeForm
            if (sourceTypedValues.containsKey(fieldName)) {
                FreeForm.TypedValue sourceTypedValue = sourceTypedValues.get(fieldName);
                Object targetValue = targetTypedValue.getValue();
                Object sourceValue = sourceTypedValue.getValue();
                Field targetField = targetTypedValue.getField();
                Field sourceField = sourceTypedValue.getField();

                // Only proceed if fields are compatible (same field type)
                if (areFieldsCompatible(targetField, sourceField)) {
                    if (sourceValue != null) {
                        // Special handling for nested structures (MultiField and List)
                        if (FreeFormHelper.isNestedStructure(targetField) && targetValue instanceof Map && sourceValue instanceof Map) {
                            // For nested structures, always merge - let the nested merge logic handle shouldOverwrite
                            Map<String, Object> mergedValue = mergeFreeFormValues(
                                    (Map<String, Object>) targetValue,
                                    (Map<String, Object>) sourceValue,
                                    targetField,
                                    shouldOverwrite);
                            updatedTypedValue = FreeFormHelper.createUpdatedTypedValue(targetTypedValue, mergedValue);
                        } else if (FreeFormHelper.isNestedStructure(targetField) && "com.krista.fields.List".equals(targetField.getFieldType())) {
                            // For List fields, handle merging of list elements
                            Object mergedListValue = mergeListValues(targetValue, sourceValue, shouldOverwrite);
                            updatedTypedValue = FreeFormHelper.createUpdatedTypedValue(targetTypedValue, mergedListValue);
                        } else {
                            // For simple fields, decide whether to update based on shouldOverwrite and existing value
                            boolean shouldUpdate = shouldOverwrite || FreeFormHelper.isValueEmpty(targetValue);

                            if (shouldUpdate) {
                                updatedTypedValue = FreeFormHelper.createUpdatedTypedValue(targetTypedValue, sourceValue);
                            } else {
                                // Keep original target value
                                updatedTypedValue = targetTypedValue;
                            }
                        }
                    } else {
                        // Keep original target value
                        updatedTypedValue = targetTypedValue;
                    }
                } else {
                    // Fields are not compatible, keep original target value
                    updatedTypedValue = targetTypedValue;
                }
            } else {
                // Field doesn't exist in source, keep original target value
                updatedTypedValue = targetTypedValue;
            }

            // Put the TypedValue directly into FreeForm
            updatedFreeForm.put(fieldName, updatedTypedValue);
        }

        return updatedFreeForm;
    }

    /**
     * Checks if the updated FreeForm does NOT have values for at least one of the fields present in the reference FreeForm.
     * This is used to determine if some fields from the reference form are missing values in the updated form.
     * Supports nested field comparison for MultiField and List structures.
     *
     * @param updatedFreeForm          The FreeForm to check for missing values
     * @param referenceFreeForm        The FreeForm containing the fields to check against
     * @param checkOnlyMandatoryFields Whether to check only mandatory fields
     * @return true if at least one field from reference form is missing a value in updated form, false otherwise
     */
    public static boolean hasMissingValuesFromReference(FreeForm updatedFreeForm, FreeForm referenceFreeForm, boolean checkOnlyMandatoryFields) {
        // Extract typed values from both forms
        Map<String, FreeForm.TypedValue> updatedTypedValues = FreeFormHelper.extractTypedValues(updatedFreeForm);
        Map<String, FreeForm.TypedValue> referenceTypedValues = FreeFormHelper.extractTypedValues(referenceFreeForm);

        // Check each field in the reference form
        for (Map.Entry<String, FreeForm.TypedValue> referenceEntry : referenceTypedValues.entrySet()) {
            String fieldName = referenceEntry.getKey();
            FreeForm.TypedValue referenceTypedValue = referenceEntry.getValue();

            // Get the field definition from reference TypedValue
            Field referenceField = referenceTypedValue.getField();
            if (referenceField != null) {
                // Check if field is mandatory
                boolean isMandatory = FieldOptionsExtractor.extractIsMandatory(referenceField);

                // If checkOnlyMandatoryFields is true, only consider mandatory fields
                if (checkOnlyMandatoryFields && !isMandatory) {
                    continue; // Skip non-mandatory fields
                }

                // Check if this field exists in updated form and has a value
                FreeForm.TypedValue updatedTypedValue = updatedTypedValues.get(fieldName);
                if (updatedTypedValue == null) {
                    // Field doesn't exist in updated form
                    return true;
                }

                Object updatedFieldValue = updatedTypedValue.getValue();
                Object referenceFieldValue = referenceTypedValue.getValue();

                if (updatedFieldValue == null || FreeFormHelper.isValueEmpty(updatedFieldValue)) {
                    // Field exists but has no value or empty value
                    return true;
                }

                // For nested structures, recursively check nested fields
                if (FreeFormHelper.isNestedStructure(referenceField)) {
                    if (hasMissingNestedValues(updatedFieldValue, referenceFieldValue, referenceField, checkOnlyMandatoryFields)) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    /**
     * Checks if the updated FreeForm has values ONLY for fields that are present in the reference FreeForm.
     * This is used to determine if the updated form contains only values for fields from the reference form
     * and no additional fields with values. Supports nested field comparison for MultiField and List structures.
     *
     * @param updatedFreeForm          The FreeForm to check for extra values
     * @param referenceFreeForm        The FreeForm containing the allowed fields
     * @param checkOnlyMandatoryFields Whether to check only mandatory fields
     * @return true if updated form has values only for reference fields, false if it has values for additional fields
     */
    public static boolean hasOnlyReferenceFieldValues(FreeForm updatedFreeForm, FreeForm referenceFreeForm, boolean checkOnlyMandatoryFields) {
        // Extract typed values from both forms
        Map<String, FreeForm.TypedValue> updatedTypedValues = FreeFormHelper.extractTypedValues(updatedFreeForm);
        Map<String, FreeForm.TypedValue> referenceTypedValues = FreeFormHelper.extractTypedValues(referenceFreeForm);

        // Check each field in the updated form
        for (Map.Entry<String, FreeForm.TypedValue> updatedEntry : updatedTypedValues.entrySet()) {
            String fieldName = updatedEntry.getKey();
            FreeForm.TypedValue updatedTypedValue = updatedEntry.getValue();

            // Get the field definition from updated TypedValue
            Field updatedField = updatedTypedValue.getField();
            Object updatedFieldValue = updatedTypedValue.getValue();

            if (updatedField != null && updatedFieldValue != null && !FreeFormHelper.isValueEmpty(updatedFieldValue)) {
                // Check if field is mandatory
                boolean isMandatory = FieldOptionsExtractor.extractIsMandatory(updatedField);

                // If checkOnlyMandatoryFields is true, only consider mandatory fields
                if (checkOnlyMandatoryFields && !isMandatory) {
                    continue; // Skip non-mandatory fields
                }

                // Check if this field exists in reference form
                FreeForm.TypedValue referenceTypedValue = referenceTypedValues.get(fieldName);
                if (referenceTypedValue == null) {
                    // Updated form has a value for a field not in reference form
                    return false;
                }

                // For nested structures, recursively check if updated form has extra nested fields
                if (FreeFormHelper.isNestedStructure(updatedField)) {
                    Object referenceFieldValue = referenceTypedValue.getValue();
                    if (!hasOnlyReferenceNestedValues(updatedFieldValue, referenceFieldValue, updatedField, checkOnlyMandatoryFields)) {
                        return false;
                    }
                }
            }
        }

        return true;
    }

    // Helper methods

    /**
     * Checks if two fields are compatible for value copying
     */
    private static boolean areFieldsCompatible(Field targetField, Field sourceField) {
        if (targetField == null || sourceField == null) {
            return false;
        }

        // Fields are compatible if they have the same field type
        String targetFieldType = targetField.getFieldType();
        String sourceFieldType = sourceField.getFieldType();

        return targetFieldType != null && targetFieldType.equals(sourceFieldType);
    }

    /**
     * Merges values from source Map into target Map for nested structures (MultiField)
     * Inspired by the existing mergeMultiFieldValues method but with shouldOverwrite logic
     */
    private static Map<String, Object> mergeFreeFormValues(
            Map<String, Object> targetMap,
            Map<String, Object> sourceMap,
            Field parentField,
            boolean shouldOverwrite) {

        // Create a new map with all target values (preserve all original fields)
        Map<String, Object> result = new HashMap<>(targetMap);

        // Iterate through source map entries to update matching fields
        for (Map.Entry<String, Object> entry : sourceMap.entrySet()) {
            String fieldName = entry.getKey();
            Object sourceValue = entry.getValue();
            Object targetValue = result.get(fieldName);

            // Get the nested field definition to ensure field compatibility
            Field nestedField = FreeFormHelper.getNestedField(parentField, fieldName);

            if (nestedField != null && sourceValue != null) {
                // Decide whether to update based on shouldOverwrite and existing value
                boolean shouldUpdate = shouldOverwrite || FreeFormHelper.isValueEmpty(targetValue);

                if (shouldUpdate) {
                    // If both values are Maps and it's a nested structure, recursively merge
                    if (FreeFormHelper.isNestedStructure(nestedField) && targetValue instanceof Map && sourceValue instanceof Map) {
                        Map<String, Object> mergedNestedValue = mergeFreeFormValues(
                                (Map<String, Object>) targetValue,
                                (Map<String, Object>) sourceValue,
                                nestedField,
                                shouldOverwrite);
                        result.put(fieldName, mergedNestedValue);
                    } else if (FreeFormHelper.isNestedStructure(nestedField) && "com.krista.fields.List".equals(nestedField.getFieldType())) {
                        // Handle List field merging
                        Object mergedListValue = mergeListValues(targetValue, sourceValue, shouldOverwrite);
                        result.put(fieldName, mergedListValue);
                    } else {
                        // For simple fields, directly copy the source value
                        result.put(fieldName, sourceValue);
                    }
                } else if (FreeFormHelper.isNestedStructure(nestedField) && targetValue instanceof Map && sourceValue instanceof Map) {
                    // Even when not updating the field itself, we should still merge nested structures
                    Map<String, Object> mergedNestedValue = mergeFreeFormValues(
                            (Map<String, Object>) targetValue,
                            (Map<String, Object>) sourceValue,
                            nestedField,
                            shouldOverwrite);
                    result.put(fieldName, mergedNestedValue);
                }
                // If shouldUpdate is false, we keep the original targetValue (already in result)
            }
            // If nestedField is null or sourceValue is null, we ignore this source field
        }

        return result;
    }

    /**
     * Merges List values from source into target
     */
    private static Object mergeListValues(Object targetValue, Object sourceValue, boolean shouldOverwrite) {
        // If target is empty or shouldOverwrite is true, use source value
        if (FreeFormHelper.isValueEmpty(targetValue) || shouldOverwrite) {
            return sourceValue;
        }

        // If we reach here, target is not empty and shouldOverwrite is false
        // So we keep the target value
        return targetValue;
    }

    /**
     * Recursively checks if nested values are missing in updated structure compared to reference structure.
     * Used by hasMissingValuesFromReference for MultiField and List comparisons.
     */
    private static boolean hasMissingNestedValues(Object updatedValue, Object referenceValue, Field field, boolean checkOnlyMandatoryFields) {
        if (referenceValue == null || FreeFormHelper.isValueEmpty(referenceValue)) {
            return false; // Nothing to compare against
        }

        if (updatedValue == null || FreeFormHelper.isValueEmpty(updatedValue)) {
            return true; // Updated value is missing but reference has value
        }

        String fieldType = field.getFieldType();

        if ("com.krista.fields.MultiField".equals(fieldType)) {
            return hasMissingMultiFieldValues(updatedValue, referenceValue, field, checkOnlyMandatoryFields);
        } else if ("com.krista.fields.List".equals(fieldType)) {
            return hasMissingListValues(updatedValue, referenceValue, field, checkOnlyMandatoryFields);
        }

        return false; // For non-nested fields, top-level comparison is sufficient
    }

    /**
     * Checks if MultiField nested values are missing in updated structure.
     */
    private static boolean hasMissingMultiFieldValues(Object updatedValue, Object referenceValue, Field field, boolean checkOnlyMandatoryFields) {
        if (!(updatedValue instanceof Map) || !(referenceValue instanceof Map)) {
            return false; // Can't compare non-Map values
        }

        Map<String, Object> updatedMap = (Map<String, Object>) updatedValue;
        Map<String, Object> referenceMap = (Map<String, Object>) referenceValue;

        // Check each nested field in reference
        for (Map.Entry<String, Object> referenceEntry : referenceMap.entrySet()) {
            String nestedFieldName = referenceEntry.getKey();
            Object referenceNestedValue = referenceEntry.getValue();

            // Get nested field definition
            Field nestedField = FreeFormHelper.getNestedField(field, nestedFieldName);

            // If we have nested field definition, check if it's mandatory
            if (nestedField != null) {
                boolean isMandatory = FieldOptionsExtractor.extractIsMandatory(nestedField);

                if (checkOnlyMandatoryFields && !isMandatory) {
                    continue; // Skip non-mandatory nested fields
                }
            } else if (checkOnlyMandatoryFields) {
                // If no nested field definition and we're only checking mandatory fields,
                // assume the field is optional and skip it
                continue;
            }

            // Check if the nested value is missing in updated map
            Object updatedNestedValue = updatedMap.get(nestedFieldName);
            if (updatedNestedValue == null || FreeFormHelper.isValueEmpty(updatedNestedValue)) {
                if (referenceNestedValue != null && !FreeFormHelper.isValueEmpty(referenceNestedValue)) {
                    return true; // Missing nested value
                }
            }

            // Recursively check nested structures if we have field definition
            if (nestedField != null && FreeFormHelper.isNestedStructure(nestedField)) {
                if (hasMissingNestedValues(updatedNestedValue, referenceNestedValue, nestedField, checkOnlyMandatoryFields)) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Checks if List nested values are missing in updated structure.
     * For Lists, we consider it missing if the updated list is empty but reference list has values.
     */
    private static boolean hasMissingListValues(Object updatedValue, Object referenceValue, Field field, boolean checkOnlyMandatoryFields) {
        if (!(updatedValue instanceof List) || !(referenceValue instanceof List)) {
            return false; // Can't compare non-List values
        }

        List<?> updatedList = (List<?>) updatedValue;
        List<?> referenceList = (List<?>) referenceValue;

        // If reference has values but updated is empty, consider it missing
        if (!referenceList.isEmpty() && updatedList.isEmpty()) {
            return true;
        }

        // For more complex List element comparison, we could implement element-by-element checking
        // but for now, we consider non-empty lists as having values
        return false;
    }

    /**
     * Recursively checks if updated structure has only reference nested values (no extra nested fields).
     * Used by hasOnlyReferenceFieldValues for MultiField and List comparisons.
     */
    private static boolean hasOnlyReferenceNestedValues(Object updatedValue, Object referenceValue, Field field, boolean checkOnlyMandatoryFields) {
        if (updatedValue == null || FreeFormHelper.isValueEmpty(updatedValue)) {
            return true; // Empty updated value has no extra fields
        }

        if (referenceValue == null || FreeFormHelper.isValueEmpty(referenceValue)) {
            return false; // Updated has values but reference is empty
        }

        String fieldType = field.getFieldType();

        if ("com.krista.fields.MultiField".equals(fieldType)) {
            return hasOnlyReferenceMultiFieldValues(updatedValue, referenceValue, field, checkOnlyMandatoryFields);
        } else if ("com.krista.fields.List".equals(fieldType)) {
            return hasOnlyReferenceListValues(updatedValue, referenceValue, field, checkOnlyMandatoryFields);
        }

        return true; // For non-nested fields, top-level comparison is sufficient
    }

    /**
     * Checks if MultiField has only reference nested values (no extra nested fields).
     */
    private static boolean hasOnlyReferenceMultiFieldValues(Object updatedValue, Object referenceValue, Field field, boolean checkOnlyMandatoryFields) {
        if (!(updatedValue instanceof Map) || !(referenceValue instanceof Map)) {
            return true; // Can't have extra fields if not Maps
        }

        Map<String, Object> updatedMap = (Map<String, Object>) updatedValue;
        Map<String, Object> referenceMap = (Map<String, Object>) referenceValue;

        // Check each nested field in updated map
        for (Map.Entry<String, Object> updatedEntry : updatedMap.entrySet()) {
            String nestedFieldName = updatedEntry.getKey();
            Object updatedNestedValue = updatedEntry.getValue();

            if (updatedNestedValue != null && !FreeFormHelper.isValueEmpty(updatedNestedValue)) {
                // Get nested field definition
                Field nestedField = FreeFormHelper.getNestedField(field, nestedFieldName);

                // If we have nested field definition, check if it's mandatory
                if (nestedField != null) {
                    boolean isMandatory = FieldOptionsExtractor.extractIsMandatory(nestedField);

                    if (checkOnlyMandatoryFields && !isMandatory) {
                        continue; // Skip non-mandatory nested fields
                    }
                } else if (checkOnlyMandatoryFields) {
                    // If no nested field definition and we're only checking mandatory fields,
                    // assume the field is optional and skip it
                    continue;
                }

                // Check if this nested field exists in reference
                if (!referenceMap.containsKey(nestedFieldName)) {
                    return false; // Extra nested field found
                }

                // Recursively check nested structures if we have field definition
                if (nestedField != null && FreeFormHelper.isNestedStructure(nestedField)) {
                    Object referenceNestedValue = referenceMap.get(nestedFieldName);
                    if (!hasOnlyReferenceNestedValues(updatedNestedValue, referenceNestedValue, nestedField, checkOnlyMandatoryFields)) {
                        return false;
                    }
                }
            }
        }

        return true;
    }

    /**
     * Checks if List has only reference values (no extra elements beyond what's expected).
     * For Lists, we generally allow different content as long as it's the same field.
     */
    private static boolean hasOnlyReferenceListValues(Object updatedValue, Object referenceValue, Field field, boolean checkOnlyMandatoryFields) {
        // For Lists, we generally consider them valid as long as they're the same field
        // More complex element-by-element comparison could be implemented if needed
        return true;
    }

}
