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

import app.krista.model.field.NamedField;
import app.krista.model.field.NamedValuedField;
import org.junit.Test;
import org.junit.Before;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;

public class NamedFieldConverterTest {

    private NamedField testNamedField;

    @Before
    public void setUp() {
        // Create a test NamedField
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("minLength", 5);
        attributes.put("maxLength", 100);

        Map<String, NamedValuedField> options = new HashMap<>();
        NamedValuedField requiredOption = new NamedValuedField("required", "Boolean", true, Map.of(), Map.of());
        options.put("required", requiredOption);

        testNamedField = new NamedField("testField", "com.krista.fields.Text", attributes, options);
    }

    @Test
    public void testConvertToAppNamedField_WithAppNamedField_ReturnsOriginal() {
        // Test with app.krista.model.field.NamedField (should return as-is)
        NamedField result = NamedFieldConverter.convertToAppNamedField(testNamedField);
        
        assertNotNull(result);
        assertSame(testNamedField, result);
        assertEquals("testField", result.getName());
        assertEquals("com.krista.fields.Text", result.getFieldType());
    }

    @Test
    public void testConvertToAppNamedField_WithNull_ReturnsNull() {
        NamedField result = NamedFieldConverter.convertToAppNamedField(null);
        assertNull(result);
    }

    @Test
    public void testExtractName_WithAppNamedField_ReturnsName() {
        String name = NamedFieldConverter.extractName(testNamedField);
        assertEquals("testField", name);
    }

    @Test
    public void testExtractName_WithNull_ReturnsNull() {
        String name = NamedFieldConverter.extractName(null);
        assertNull(name);
    }

    @Test
    public void testExtractFieldType_WithAppNamedField_ReturnsFieldType() {
        String fieldType = NamedFieldConverter.extractFieldType(testNamedField);
        assertEquals("com.krista.fields.Text", fieldType);
    }

    @Test
    public void testExtractFieldType_WithNull_ReturnsNull() {
        String fieldType = NamedFieldConverter.extractFieldType(null);
        assertNull(fieldType);
    }
}
