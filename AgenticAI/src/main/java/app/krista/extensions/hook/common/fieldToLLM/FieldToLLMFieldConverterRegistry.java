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

package app.krista.extensions.hook.common.fieldToLLM;

import app.krista.extensions.hook.common.fieldToLLM.impl.DefaultFieldToLLMFieldConverter;
import app.krista.extensions.hook.common.fieldToLLM.impl.ListFieldToLLMFieldConverter;
import app.krista.extensions.hook.common.fieldToLLM.impl.MultiFieldToLLMFieldConverter;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class FieldToLLMFieldConverterRegistry {
    private final Map<String, FieldToLLMFieldConverter> converterMap = new ConcurrentHashMap<>();
    private final FieldToLLMFieldConverter defaultConverter;

    // Supported field types from FieldValueToLLMValueConverterRegistry
    private static final Set<String> SUPPORTED_FIELD_TYPES = Set.of(
            "com.krista.fields.Time",
            "com.krista.fields.Date",
            "com.krista.fields.DateRange",
            "com.krista.fields.DateTimeRange",
            "com.krista.fields.MultiField",
            "com.krista.fields.List",
            "com.krista.fields.Checkboxes",
            "com.krista.fields.Switch",
            "com.krista.fields.Phone",
            "com.krista.fields.Rating",
            "com.krista.fields.PickOne",
            "com.krista.fields.Email",
            "com.krista.fields.Identifier",
            "com.krista.fields.Text",
            "com.krista.fields.Street",
            "com.krista.fields.State",
            "com.krista.fields.Person",
            "com.krista.fields.Country",
            "com.krista.fields.Paragraph",
            "com.krista.fields.Street2",
            "com.krista.fields.ProperNoun",
            "com.krista.fields.City",
            "com.krista.fields.PostalCode",
            "com.krista.fields.Percentage",
            "com.krista.fields.Unit",
            "com.krista.fields.Number",
            "com.krista.fields.Currency"
    );

    public static FieldToLLMFieldConverterRegistry create() {
        FieldToLLMFieldConverterRegistry registry = new FieldToLLMFieldConverterRegistry();

        // Register specialized converters for complex field types
        registry.register("com.krista.fields.MultiField", new MultiFieldToLLMFieldConverter(registry));
        registry.register("com.krista.fields.List", new ListFieldToLLMFieldConverter(registry));

        return registry;
    }

    public FieldToLLMFieldConverterRegistry() {
        this.defaultConverter = new DefaultFieldToLLMFieldConverter();
    }

    public void register(String fieldType, FieldToLLMFieldConverter converter) {
        Objects.requireNonNull(fieldType);
        Objects.requireNonNull(converter);
        if (this.converterMap.containsKey(fieldType)) {
            throw new IllegalStateException("FieldToLLMFieldConverter already registered for field type: " + fieldType);
        } else {
            this.converterMap.put(fieldType, converter);
        }
    }

    public FieldToLLMFieldConverter get(String fieldType) throws Exception {
        // DEBUG: Log field type being requested
        System.out.println("DEBUG: Requesting converter for fieldType: " + fieldType);

        // First validate that the field type is supported
        if (!SUPPORTED_FIELD_TYPES.contains(fieldType)) {
            System.out.println("DEBUG: FieldType " + fieldType + " is NOT in SUPPORTED_FIELD_TYPES");
            throw new UnsupportedFieldTypeException("No converter registered for field type: " + fieldType +
                ". Supported types are: " + SUPPORTED_FIELD_TYPES);
        }

        System.out.println("DEBUG: FieldType " + fieldType + " is supported, proceeding...");
        // Return specialized converter if available, otherwise default converter
        FieldToLLMFieldConverter converter = this.converterMap.get(fieldType);
        if (converter != null) {
            return converter;
        }

        // Return default converter for supported field types without specialized converters
        return defaultConverter;
    }
}
