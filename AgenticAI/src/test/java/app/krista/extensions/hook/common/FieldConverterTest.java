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

import app.krista.extensions.hook.common.FieldConverter;
import app.krista.model.field.Field;
import app.krista.model.field.NamedValuedField;
import org.junit.Test;
import org.junit.Before;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;

public class FieldConverterTest {

    private Field testField;

    @Before
    public void setUp() {
        // Create a test Field
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("minLength", 5);
        attributes.put("maxLength", 100);

        Map<String, NamedValuedField> options = new HashMap<>();
        NamedValuedField requiredOption = new NamedValuedField("required", "Boolean", true, Map.of(), Map.of());
        options.put("required", requiredOption);

        testField = new Field("com.krista.fields.Text", attributes, options);
    }

    @Test
    public void testConvertToAppField_WithAppField_ReturnsOriginal() {
        // Act
        Field result = FieldConverter.convertToAppField(testField);

        // Assert
        assertSame(testField, result);
    }

    @Test
    public void testConvertToAppField_WithNull_ReturnsNull() {
        // Act
        Field result = FieldConverter.convertToAppField(null);

        // Assert
        assertNull(result);
    }

    @Test
    public void testConvertToAppField_WithInvalidObject_ReturnsNull() {
        // Act
        Field result = FieldConverter.convertToAppField("not a field");

        // Assert
        assertNull(result);
    }
}
