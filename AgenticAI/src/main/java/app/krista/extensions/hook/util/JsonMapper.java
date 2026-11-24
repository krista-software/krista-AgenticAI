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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/**
 * Centralized JSON mapping utility providing shared, properly-configured ObjectMapper instances.
 * 
 * <p>This class eliminates ObjectMapper proliferation and ensures consistent JSON serialization
 * across the application. All instances are pre-configured with JavaTimeModule for Java 8 date/time
 * support and ISO-8601 date formatting.</p>
 * 
 * <p>ObjectMapper instances are thread-safe after configuration, making singleton usage safe
 * for concurrent access.</p>
 * 
 * <h3>Usage Examples:</h3>
 * <pre>
 * // Standard JSON serialization
 * String json = JsonMapper.getInstance().writeValueAsString(myObject);
 * MyClass obj = JsonMapper.getInstance().readValue(json, MyClass.class);
 * 
 * // Pretty-printed JSON for logging
 * String prettyJson = JsonMapper.getPrettyPrinter().writeValueAsString(myObject);
 * </pre>
 * 
 * @see ObjectMapper
 * @see JavaTimeModule
 */
public class JsonMapper {
    
    /**
     * Shared ObjectMapper instance configured for standard JSON operations.
     * Includes JavaTimeModule and ISO-8601 date formatting.
     */
    private static final ObjectMapper INSTANCE;
    
    /**
     * Shared ObjectMapper instance configured for pretty-printed JSON output.
     * Includes JavaTimeModule, ISO-8601 date formatting, and indented output.
     */
    private static final ObjectMapper PRETTY_PRINTER;
    
    static {
        
        INSTANCE = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
            
        PRETTY_PRINTER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .enable(SerializationFeature.INDENT_OUTPUT);
    }
    
    /**
     * Private constructor to prevent instantiation.
     * This is a utility class providing only static methods.
     */
    private JsonMapper() {
        
        // Private constructor to prevent instantiation
    }
    
    /**
     * Returns the shared ObjectMapper instance for standard JSON operations.
     * 
     * <p>This instance is configured with:</p>
     * <ul>
     *   <li>JavaTimeModule for Java 8 date/time types (LocalDateTime, Instant, etc.)</li>
     *   <li>ISO-8601 date formatting (e.g., "2026-02-19T03:38:52.478192Z")</li>
     *   <li>Compact output (no indentation)</li>
     * </ul>
     * 
     * <p>Thread-safe for concurrent use.</p>
     * 
     * @return the shared ObjectMapper instance
     */
    public static ObjectMapper getInstance() {
        
        return INSTANCE;
    }
    
    /**
     * Returns the shared ObjectMapper instance configured for pretty-printed output.
     * 
     * <p>This instance is configured with:</p>
     * <ul>
     *   <li>JavaTimeModule for Java 8 date/time types (LocalDateTime, Instant, etc.)</li>
     *   <li>ISO-8601 date formatting (e.g., "2026-02-19T03:38:52.478192Z")</li>
     *   <li>Indented output for human readability</li>
     * </ul>
     * 
     * <p>Ideal for logging and debugging. Thread-safe for concurrent use.</p>
     * 
     * @return the shared ObjectMapper instance with pretty-printing enabled
     */
    public static ObjectMapper getPrettyPrinter() {
        
        return PRETTY_PRINTER;
    }
}

