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

package app.krista.extensions.hook.common.fieldToLLM.valueConverter;

import app.krista.extensions.hook.common.fieldToLLM.valueConverter.impl.*;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public class FieldValueToLLMValueConverterRegistry {
    private final Map<String, FieldValueToLLMValueConverter<?>> converterMap = new ConcurrentHashMap<>();

    public static FieldValueToLLMValueConverterRegistry create() {
        FieldValueToLLMValueConverterRegistry registry = new FieldValueToLLMValueConverterRegistry();
        
        // Register converters for various field types
        registry.register("com.krista.fields.Time", new TimeFieldValueToLLMValueConverter());
        registry.register("com.krista.fields.Date", new DateFieldValueToLLMValueConverter());
        registry.register("com.krista.fields.DateRange", new DateRangeFieldValueToLLMValueConverter());
        registry.register("com.krista.fields.DateTimeRange", new DateRangeFieldValueToLLMValueConverter());

        registry.register("com.krista.fields.MultiField", new MultiFieldValueToLLMValueConverter(registry));
        registry.register("com.krista.fields.Checkboxes", new CheckboxFieldValueToLLMValueConverter());
        registry.register("com.krista.fields.Switch", new SwitchFieldValueToLLMValueConverter());
        registry.register("com.krista.fields.Phone", new PhoneNumberFieldValueToLLMValueConverter());
        registry.register("com.krista.fields.Rating", new RatingFieldValueToLLMValueConverter());

        registry.register("com.krista.fields.PickOne", new TextFieldValueToLLMValueConverter());
        registry.register("com.krista.fields.Email", new TextFieldValueToLLMValueConverter());
        registry.register("com.krista.fields.Identifier", new TextFieldValueToLLMValueConverter());
        registry.register("com.krista.fields.Text", new TextFieldValueToLLMValueConverter());
        registry.register("com.krista.fields.Street", new TextFieldValueToLLMValueConverter());
        registry.register("com.krista.fields.State", new TextFieldValueToLLMValueConverter());
        registry.register("com.krista.fields.Person", new TextFieldValueToLLMValueConverter());
        registry.register("com.krista.fields.Country", new TextFieldValueToLLMValueConverter());
        registry.register("com.krista.fields.Paragraph", new TextFieldValueToLLMValueConverter());
        registry.register("com.krista.fields.Street2", new TextFieldValueToLLMValueConverter());
        registry.register("com.krista.fields.ProperNoun", new TextFieldValueToLLMValueConverter());
        registry.register("com.krista.fields.City", new TextFieldValueToLLMValueConverter());
        registry.register("com.krista.fields.PostalCode", new TextFieldValueToLLMValueConverter());

        registry.register("com.krista.fields.Percentage", new NumberFieldValueToLLMValueConverter());
        registry.register("com.krista.fields.Unit", new NumberFieldValueToLLMValueConverter());
        registry.register("com.krista.fields.Number", new NumberFieldValueToLLMValueConverter());
        registry.register("com.krista.fields.Currency", new NumberFieldValueToLLMValueConverter());

        return registry;
    }

    public void register(String fieldType, FieldValueToLLMValueConverter<?> converter) {
        Objects.requireNonNull(fieldType);
        Objects.requireNonNull(converter);
        if (this.converterMap.containsKey(fieldType)) {
            throw new IllegalStateException("FieldValueToLLMValueConverter already registered for field type: " + fieldType);
        } else {
            this.converterMap.put(fieldType, converter);
        }
    }

    public FieldValueToLLMValueConverter<?> get(String fieldType) {
        if(!this.converterMap.containsKey(fieldType)) {
            throw new IllegalStateException("No converter registered for field type: " + fieldType);
        }
        return this.converterMap.get(fieldType);
    }
}