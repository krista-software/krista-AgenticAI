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

package app.krista.extensions.hook.common.fieldToLLM.impl;

import app.krista.extensions.hook.common.NamedFieldConverter;
import app.krista.extensions.hook.common.fieldToLLM.FieldAttributeFilter;
import app.krista.extensions.hook.common.fieldToLLM.FieldOptionsExtractor;
import app.krista.extensions.hook.common.fieldToLLM.FieldToLLMFieldConverter;
import app.krista.extensions.hook.common.fieldToLLM.FieldToLLMFieldConverterRegistry;
import app.krista.extensions.hook.common.models.LLMField;
import app.krista.model.field.Field;
import app.krista.model.field.NamedField;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MultiFieldToLLMFieldConverter implements FieldToLLMFieldConverter {

    private final FieldToLLMFieldConverterRegistry registry;



    public MultiFieldToLLMFieldConverter(FieldToLLMFieldConverterRegistry registry) {
        this.registry = registry;
    }

    @Override
    public LLMField extract(Field field) throws Exception {
        // Extract isMandatory using global utility
        boolean isMandatory = FieldOptionsExtractor.extractIsMandatory(field);

        // Filter attributes using global filter and process nested fields with default context
        Map<String, Object> filteredAttributes = processAttributesWithNestedFields(field.getAttributeValues(), FieldAttributeFilter.PromptContext.DEFAULT);

        return new LLMField(isMandatory, field.getFieldType(), filteredAttributes);
    }

    @Override
    public LLMField extract(Field field, FieldAttributeFilter.PromptContext context) throws Exception {
        // Extract isMandatory using global utility
        boolean isMandatory = FieldOptionsExtractor.extractIsMandatory(field);

        // Filter attributes using context-aware filter and process nested fields
        Map<String, Object> filteredAttributes = processAttributesWithNestedFields(field.getAttributeValues(), context);

        return new LLMField(isMandatory, field.getFieldType(), filteredAttributes);
    }



    /**
     * Apply global attribute filtering and process nested fields for MultiField
     */
    private Map<String, Object> processAttributesWithNestedFields(Map<String, Object> attributes, FieldAttributeFilter.PromptContext context) {
        // First apply context-aware filtering
        Map<String, Object> filtered = FieldAttributeFilter.filterAttributes(attributes, context);

        // Then process the "fields" attribute if present
        if (filtered.containsKey("fields")) {
            filtered.put("fields", processNestedFields(filtered.get("fields"), context));
        }

        return filtered;
    }

    /**
     * Process nested fields for MultiField with context-aware filtering (fields attribute contains List<NamedField>)
     */
    private Object processNestedFields(Object fieldsObj, FieldAttributeFilter.PromptContext context) {
        if (!(fieldsObj instanceof List<?> fieldsList)) {
            return fieldsObj; // Return as-is if not a list
        }

        List<Object> processedFields = new ArrayList<>();

        for (Object fieldObj : fieldsList) {
            // Convert to app.krista.model.field.NamedField
            NamedField namedField = NamedFieldConverter.convertToAppNamedField(fieldObj);

            if (namedField != null) {
                try {
                    // Get appropriate converter for the nested field type
                    FieldToLLMFieldConverter converter = registry.get(namedField.getFieldType());

                    // Recursively convert each nested field to LLMField with context
                    LLMField nestedLLMField = converter.extract(namedField, context);

                    // Create a map representation that includes the field name
                    Map<String, Object> fieldMap = new HashMap<>();
                    fieldMap.put("name", namedField.getName());
                    fieldMap.put("fieldType", nestedLLMField.fieldType());
                    fieldMap.put("isMandatoryField", nestedLLMField.isMandatory());
                    fieldMap.put("attributes", nestedLLMField.attributeValues());

                    processedFields.add(fieldMap);
                } catch (Exception e) {
                    System.out.println("Failed to process nested field: " + e.getMessage());
                    // Skip this field if we can't process it
                }
            } else {
                System.out.println("Failed to convert field to NamedField: " + fieldObj.getClass().getName());
            }
        }

        return processedFields;
    }
}
