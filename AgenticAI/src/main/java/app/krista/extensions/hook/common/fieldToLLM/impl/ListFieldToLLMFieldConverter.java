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

import app.krista.extensions.hook.common.fieldToLLM.FieldAttributeFilter;
import app.krista.extensions.hook.common.fieldToLLM.FieldMetadataExtractor;
import app.krista.extensions.hook.common.fieldToLLM.FieldToLLMFieldConverter;
import app.krista.extensions.hook.common.fieldToLLM.FieldToLLMFieldConverterRegistry;
import app.krista.extensions.hook.common.models.LLMField;
import app.krista.model.field.Field;

import java.util.HashMap;
import java.util.Map;

public class ListFieldToLLMFieldConverter implements FieldToLLMFieldConverter {

    private final FieldToLLMFieldConverterRegistry registry;



    public ListFieldToLLMFieldConverter(FieldToLLMFieldConverterRegistry registry) {
        this.registry = registry;
    }

    @Override
    public LLMField extract(Field field) throws Exception {
        // Extract isMandatory using global utility
        boolean isMandatory = FieldMetadataExtractor.extractIsMandatory(field);

        // Filter attributes using global filter and process element field with default context
        Map<String, Object> filteredAttributes = processAttributesWithElementField(field.getAttributeValues(), FieldAttributeFilter.PromptContext.DEFAULT);

        return new LLMField(isMandatory, field.getFieldType(), filteredAttributes);
    }

    @Override
    public LLMField extract(Field field, FieldAttributeFilter.PromptContext context) throws Exception {
        // Extract isMandatory using global utility
        boolean isMandatory = FieldMetadataExtractor.extractIsMandatory(field);

        // Filter attributes using context-aware filter and process element field
        Map<String, Object> filteredAttributes = processAttributesWithElementField(field.getAttributeValues(), context);

        return new LLMField(isMandatory, field.getFieldType(), filteredAttributes);
    }



    /**
     * Apply global attribute filtering and process element field for List
     */
    private Map<String, Object> processAttributesWithElementField(Map<String, Object> attributes) throws Exception {
        return processAttributesWithElementField(attributes, FieldAttributeFilter.PromptContext.DEFAULT);
    }

    /**
     * Apply context-aware attribute filtering and process element field for List
     */
    private Map<String, Object> processAttributesWithElementField(Map<String, Object> attributes, FieldAttributeFilter.PromptContext context) throws Exception {
        // First apply context-aware filtering
        Map<String, Object> filtered = FieldAttributeFilter.filterAttributes(attributes, context);

        // Then process the "field" attribute if present
        if (filtered.containsKey("field")) {
            filtered.put("field", processElementField(filtered.get("field"), context));
        }

        return filtered;
    }

    /**
     * Process element field for List (field attribute contains a single Field)
     */
    private Object processElementField(Object fieldObj) throws Exception {
        return processElementField(fieldObj, FieldAttributeFilter.PromptContext.DEFAULT);
    }

    /**
     * Process element field for List with context-aware filtering (field attribute contains a single Field)
     */
    private Object processElementField(Object fieldObj, FieldAttributeFilter.PromptContext context) throws Exception {
        if (!(fieldObj instanceof Field elementField)) {
            return fieldObj; // Return as-is if not a Field
        }

        // Check if element field is secured - secured fields cannot be rendered as text
        if (FieldMetadataExtractor.extractIsSecured(elementField)) {
            throw new Exception("Secured field in list cannot be rendered as text. " +
                "No converter registered for field type: " + elementField.getFieldType());
        }

        // Get appropriate converter for the element field type
        FieldToLLMFieldConverter converter = registry.get(elementField.getFieldType());

        // Recursively convert the element field to LLMField with context
        LLMField elementLLMField = converter.extract(elementField, context);

        // Create a map representation
        Map<String, Object> fieldMap = new HashMap<>();
        fieldMap.put("fieldType", elementLLMField.fieldType());
        fieldMap.put("isMandatoryField", elementLLMField.isMandatory());
        fieldMap.put("attributes", elementLLMField.attributeValues());

        return fieldMap;
    }
}
