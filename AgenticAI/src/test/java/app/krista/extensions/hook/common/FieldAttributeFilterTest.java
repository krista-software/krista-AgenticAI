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
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;

public class FieldAttributeFilterTest {

    @Test
    public void testDefaultContextFiltering() {
        // Arrange
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("minLength", 5);
        attributes.put("maxLength", 100);
        attributes.put("formatPattern", "\\d+");
        attributes.put("options", "option1,option2");
        attributes.put("invalidAttribute", "should be filtered out");
        attributes.put("field", "nested field");
        attributes.put("fields", "nested fields");

        // Act
        Map<String, Object> filtered = FieldAttributeFilter.filterAttributes(attributes, FieldAttributeFilter.PromptContext.DEFAULT);

        // Assert
        assertEquals(6, filtered.size());
        assertTrue(filtered.containsKey("minLength"));
        assertTrue(filtered.containsKey("maxLength"));
        assertTrue(filtered.containsKey("formatPattern"));
        assertTrue(filtered.containsKey("options"));
        assertTrue(filtered.containsKey("field"));
        assertTrue(filtered.containsKey("fields"));
        assertFalse(filtered.containsKey("invalidAttribute"));
    }

    @Test
    public void testEntityExtractionContextFiltering() {
        // Arrange
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("minLength", 5);
        attributes.put("maxLength", 100);
        attributes.put("formatPattern", "\\d+");
        attributes.put("options", "option1,option2");
        attributes.put("invalidAttribute", "should be filtered out");
        attributes.put("field", "nested field");
        attributes.put("fields", "nested fields");

        // Act
        Map<String, Object> filtered = FieldAttributeFilter.filterAttributes(attributes, FieldAttributeFilter.PromptContext.ENTITY_EXTRACTION);

        // Assert - Only options, formatPattern, field, and fields should remain
        assertEquals(4, filtered.size());
        assertFalse(filtered.containsKey("minLength"));
        assertFalse(filtered.containsKey("maxLength"));
        assertTrue(filtered.containsKey("formatPattern"));
        assertTrue(filtered.containsKey("options"));
        assertTrue(filtered.containsKey("field"));
        assertTrue(filtered.containsKey("fields"));
        assertFalse(filtered.containsKey("invalidAttribute"));
    }

    @Test
    public void testDefaultMethodUsesDefaultContext() {
        // Arrange
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("minLength", 5);
        attributes.put("maxLength", 100);
        attributes.put("formatPattern", "\\d+");
        attributes.put("options", "option1,option2");

        // Act
        Map<String, Object> filtered = FieldAttributeFilter.filterAttributes(attributes);

        // Assert - Should include all valid attributes (same as DEFAULT context)
        assertEquals(4, filtered.size());
        assertTrue(filtered.containsKey("minLength"));
        assertTrue(filtered.containsKey("maxLength"));
        assertTrue(filtered.containsKey("formatPattern"));
        assertTrue(filtered.containsKey("options"));
    }

    @Test
    public void testNullAttributesHandling() {
        // Act
        Map<String, Object> filtered = FieldAttributeFilter.filterAttributes(null, FieldAttributeFilter.PromptContext.DEFAULT);

        // Assert
        assertNotNull(filtered);
        assertTrue(filtered.isEmpty());
    }

    @Test
    public void testEmptyAttributesHandling() {
        // Arrange
        Map<String, Object> attributes = new HashMap<>();

        // Act
        Map<String, Object> filtered = FieldAttributeFilter.filterAttributes(attributes, FieldAttributeFilter.PromptContext.ENTITY_EXTRACTION);

        // Assert
        assertNotNull(filtered);
        assertTrue(filtered.isEmpty());
    }
}
