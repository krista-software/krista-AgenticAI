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

package app.krista.extensions.hook.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;
import java.time.Instant;

/**
 * Centralized Gson utility providing shared, properly-configured Gson instances.
 *
 * <p>This class eliminates ad-hoc Gson creation and ensures consistent JSON serialization
 * across the application. All instances are pre-configured with an {@link InstantTypeAdapter}
 * for ISO-8601 {@link Instant} serialization and {@code serializeNulls()} for explicit null
 * handling.</p>
 *
 * <p>Gson instances are immutable and thread-safe, making singleton usage safe
 * for concurrent access.</p>
 *
 * <h3>Usage Examples:</h3>
 * <pre>
 * // Standard JSON serialization
 * String json = GsonProvider.getInstance().toJson(myObject);
 * MyClass obj = GsonProvider.getInstance().fromJson(json, MyClass.class);
 *
 * // Pretty-printed JSON for logging
 * String prettyJson = GsonProvider.getPrettyPrinter().toJson(myObject);
 * </pre>
 *
 * @see Gson
 * @see JsonMapper
 */
public final class GsonProvider {

    /**
     * TypeAdapter for {@link Instant} that serializes to/from ISO-8601 format strings.
     * Handles null values gracefully.
     */
    private static final TypeAdapter<Instant> INSTANT_TYPE_ADAPTER = new TypeAdapter<Instant>() {

        @Override
        public void write(JsonWriter out, Instant value) throws IOException {

            if (value == null) {

                out.nullValue();
            } else {

                out.value(value.toString()); // ISO-8601 format
            }
        }

        @Override
        public Instant read(JsonReader in) throws IOException {

            if (in.peek() == JsonToken.NULL) {

                in.nextNull();
                return null;
            }
            return Instant.parse(in.nextString());
        }
    };

    /**
     * Shared Gson instance configured for standard (compact) JSON operations.
     * Includes InstantTypeAdapter and serializeNulls.
     */
    private static final Gson INSTANCE = new GsonBuilder()
        .serializeNulls()
        .registerTypeAdapter(Instant.class, INSTANT_TYPE_ADAPTER)
        .create();

    /**
     * Shared Gson instance configured for pretty-printed JSON output.
     * Includes InstantTypeAdapter, serializeNulls, and indented output.
     */
    private static final Gson PRETTY_PRINTER = new GsonBuilder()
        .serializeNulls()
        .setPrettyPrinting()
        .registerTypeAdapter(Instant.class, INSTANT_TYPE_ADAPTER)
        .create();

    /**
     * Private constructor to prevent instantiation.
     * This is a utility class providing only static methods.
     */
    private GsonProvider() {

        // Private constructor to prevent instantiation
    }

    /**
     * Returns the shared Gson instance for standard JSON operations.
     *
     * <p>This instance is configured with:</p>
     * <ul>
     *   <li>InstantTypeAdapter for ISO-8601 Instant serialization</li>
     *   <li>serializeNulls for explicit null handling</li>
     *   <li>Compact output (no indentation)</li>
     * </ul>
     *
     * <p>Thread-safe for concurrent use.</p>
     *
     * @return the shared Gson instance
     */
    public static Gson getInstance() {

        return INSTANCE;
    }

    /**
     * Returns the shared Gson instance configured for pretty-printed output.
     *
     * <p>This instance is configured with:</p>
     * <ul>
     *   <li>InstantTypeAdapter for ISO-8601 Instant serialization</li>
     *   <li>serializeNulls for explicit null handling</li>
     *   <li>Indented output for human readability</li>
     * </ul>
     *
     * <p>Ideal for logging and debugging. Thread-safe for concurrent use.</p>
     *
     * @return the shared Gson instance with pretty-printing enabled
     */
    public static Gson getPrettyPrinter() {

        return PRETTY_PRINTER;
    }
}
