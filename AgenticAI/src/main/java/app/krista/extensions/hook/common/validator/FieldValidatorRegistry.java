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

package app.krista.extensions.hook.common.validator;

import app.krista.extensions.hook.common.validator.impl.*;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public class FieldValidatorRegistry {
    private final FieldValidator defaultFieldValidator;
    private final Map<String, FieldValidator> extensionObjectProcessorMap = new ConcurrentHashMap<>();

    public static FieldValidatorRegistry create() {
        DefaultFieldValidator defaultFieldValidator = new DefaultFieldValidator();
        FieldValidatorRegistry registry = new FieldValidatorRegistry(defaultFieldValidator);
        registry.register("com.krista.fields.Date", new DateValidator());
        registry.register("com.krista.fields.DateRange", new DateRangeValidator());
        registry.register("com.krista.fields.DateTimeRange", new DateTimeRangeValidator());

        registry.register("com.krista.fields.MultiField", new MultiFieldValidator(registry));
        registry.register("com.krista.fields.List", new ListValidator(registry));
        registry.register("com.krista.fields.PickOne", new PickOneValidator());
        registry.register("com.krista.fields.Checkboxes", new CheckboxesValidator());
        registry.register("com.krista.fields.Email", new EmailValidator());
        registry.register("com.krista.fields.Identifier", new IdentifierValidator());

        registry.register("com.krista.fields.Text", new TextFieldValidator());
        registry.register("com.krista.fields.Street", new TextFieldValidator());
        registry.register("com.krista.fields.State", new TextFieldValidator());
        registry.register("com.krista.fields.Person", new TextFieldValidator());
        registry.register("com.krista.fields.Country", new TextFieldValidator());
        registry.register("com.krista.fields.Paragraph", new TextFieldValidator());
        registry.register("com.krista.fields.Street2", new TextFieldValidator());
        registry.register("com.krista.fields.ProperNoun", new TextFieldValidator());
        registry.register("com.krista.fields.City", new TextFieldValidator());

        registry.register("com.krista.fields.Percentage", new NumberFieldValidator());
        registry.register("com.krista.fields.Unit", new NumberFieldValidator());
        registry.register("com.krista.fields.Number", new NumberFieldValidator());
        registry.register("com.krista.fields.Currency", new NumberFieldValidator());

        registry.register("com.krista.fields.Switch", new SwitchValidator());
        registry.register("com.krista.fields.Rating", new RatingValidator());
        registry.register("com.krista.fields.Time", new TimeValidator());
        registry.register("com.krista.fields.Phone", new PhoneValidator());

        return registry;
    }

    public FieldValidatorRegistry(FieldValidator defaultFieldValidator) {
        this.defaultFieldValidator = defaultFieldValidator;
    }

    public void register(String fieldType, FieldValidator validator) {
        Objects.requireNonNull(fieldType);
        Objects.requireNonNull(validator);
        if (this.extensionObjectProcessorMap.containsKey(fieldType)) {
            throw new IllegalStateException("Validator already registered for field type: " + fieldType);
        } else {
            this.extensionObjectProcessorMap.put(fieldType, validator);
        }
    }

    public FieldValidator get(String fieldType) {
        return this.extensionObjectProcessorMap.getOrDefault(fieldType, this.defaultFieldValidator);
    }
}