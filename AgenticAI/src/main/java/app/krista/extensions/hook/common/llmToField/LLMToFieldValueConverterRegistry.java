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

package app.krista.extensions.hook.common.llmToField;

import app.krista.extensions.hook.common.llmToField.impl.*;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public class LLMToFieldValueConverterRegistry {
    private final Map<String, LLMToFieldValueConverter<?>> extensionObjectProcessorMap = new ConcurrentHashMap<>();

    public static LLMToFieldValueConverterRegistry create() {
        LLMToFieldValueConverterRegistry registry = new LLMToFieldValueConverterRegistry();
        registry.register("com.krista.fields.Time", new TimeLLMToFieldValueConverter());
        registry.register("com.krista.fields.Date", new DateLLMToFieldValueConverter());
        registry.register("com.krista.fields.DateRange", new DateRangeLLMToFieldValueConverter());
        registry.register("com.krista.fields.DateTimeRange", new DateRangeLLMToFieldValueConverter());

        registry.register("com.krista.fields.MultiField", new MultiFieldLLMToFieldValueConverter(registry));
        registry.register("com.krista.fields.List", new ListLLMToFieldValueConverter(registry));
        registry.register("com.krista.fields.Checkboxes", new CheckboxLLMToFieldValueConverter());
        registry.register("com.krista.fields.Switch", new SwitchLLMToFieldValueConverter());
        registry.register("com.krista.fields.Phone", new PhoneNumberLLMToFieldValueConverter());
        registry.register("com.krista.fields.Rating", new RatingLLMToFieldValueConverter());

        registry.register("com.krista.fields.PickOne", new TextLLMToFieldValueConverter());
        registry.register("com.krista.fields.Email", new TextLLMToFieldValueConverter());
        registry.register("com.krista.fields.Identifier", new TextLLMToFieldValueConverter());
        registry.register("com.krista.fields.Text", new TextLLMToFieldValueConverter());
        registry.register("com.krista.fields.Street", new TextLLMToFieldValueConverter());
        registry.register("com.krista.fields.State", new TextLLMToFieldValueConverter());
        registry.register("com.krista.fields.Person", new TextLLMToFieldValueConverter());
        registry.register("com.krista.fields.Country", new TextLLMToFieldValueConverter());
        registry.register("com.krista.fields.Paragraph", new TextLLMToFieldValueConverter());
        registry.register("com.krista.fields.Street2", new TextLLMToFieldValueConverter());
        registry.register("com.krista.fields.ProperNoun", new TextLLMToFieldValueConverter());
        registry.register("com.krista.fields.City", new TextLLMToFieldValueConverter());
        registry.register("com.krista.fields.PostalCode", new TextLLMToFieldValueConverter());

        registry.register("com.krista.fields.Percentage", new NumberLLMToFieldValueConverter());
        registry.register("com.krista.fields.Unit", new NumberLLMToFieldValueConverter());
        registry.register("com.krista.fields.Number", new NumberLLMToFieldValueConverter());
        registry.register("com.krista.fields.Currency", new NumberLLMToFieldValueConverter());

        return registry;
    }

    public void register(String fieldType, LLMToFieldValueConverter<?> extractor) {
        Objects.requireNonNull(fieldType);
        Objects.requireNonNull(extractor);
        if (this.extensionObjectProcessorMap.containsKey(fieldType)) {
            throw new IllegalStateException("LLMToFieldValueConverter already registered for field type: " + fieldType);
        } else {
            this.extensionObjectProcessorMap.put(fieldType, extractor);
        }
    }

    public LLMToFieldValueConverter<?> get(String fieldType) {
        if(!this.extensionObjectProcessorMap.containsKey(fieldType)) {
            throw new IllegalStateException("No extractor registered for field type: " + fieldType);
        }
        return this.extensionObjectProcessorMap.get(fieldType);
    }
}