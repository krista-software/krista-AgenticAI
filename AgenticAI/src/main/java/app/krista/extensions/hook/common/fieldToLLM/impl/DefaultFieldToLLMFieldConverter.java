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
import app.krista.extensions.hook.common.fieldToLLM.FieldOptionsExtractor;
import app.krista.extensions.hook.common.fieldToLLM.FieldToLLMFieldConverter;
import app.krista.extensions.hook.common.models.LLMField;
import app.krista.model.field.Field;

import java.util.Map;

public class DefaultFieldToLLMFieldConverter implements FieldToLLMFieldConverter {



    @Override
    public LLMField extract(Field field) {
        // Extract isMandatory using global utility
        boolean isMandatory = FieldOptionsExtractor.extractIsMandatory(field);

        // Filter attributes using global filter with default context
        Map<String, Object> filteredAttributes = FieldAttributeFilter.filterAttributes(field.getAttributeValues(), FieldAttributeFilter.PromptContext.DEFAULT);

        return new LLMField(isMandatory, field.getFieldType(), filteredAttributes);
    }

    @Override
    public LLMField extract(Field field, FieldAttributeFilter.PromptContext context) {
        // Extract isMandatory using global utility
        boolean isMandatory = FieldOptionsExtractor.extractIsMandatory(field);

        // Filter attributes using context-aware filter
        Map<String, Object> filteredAttributes = FieldAttributeFilter.filterAttributes(field.getAttributeValues(), context);

        return new LLMField(isMandatory, field.getFieldType(), filteredAttributes);
    }




}
