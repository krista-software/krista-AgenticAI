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

/**
 * Utility class to sanitize malformed JSON strings, particularly those containing
 * unescaped quotes within string values (common with LLM-generated content).
 *
 * This follows industry best practices for handling untrusted/external JSON input.
 */
public class JsonSanitizer {

    /**
     * Extracts a JSON object from text by finding the first '{' and last '}'.
     * Strips leading/trailing non-JSON text that LLMs sometimes add around
     * valid JSON objects (e.g., conversational preamble, markdown fences, commentary).
     *
     * @param text The raw LLM response text
     * @return The extracted substring from first '{' to last '}' (inclusive),
     *         or the original text if no valid brace pair is found
     */
    public static String extractJsonObject(String text) {

        if (text == null || text.isEmpty()) {
            return text;
        }

        int firstBrace = text.indexOf('{');
        int lastBrace = text.lastIndexOf('}');

        if (firstBrace < 0 || lastBrace < 0 || firstBrace >= lastBrace) {
            return text;
        }

        return text.substring(firstBrace, lastBrace + 1);
    }

    /**
     * Sanitizes a JSON string by escaping unescaped double quotes within string values.
     * This handles cases where LLMs generate JSON with quotes inside string values without
     * proper escaping.
     * 
     * Example:
     * Input:  {"query": "What did "John" say?"}
     * Output: {"query": "What did \"John\" say?"}
     * 
     * @param json The potentially malformed JSON string
     * @return Sanitized JSON string with properly escaped quotes
     */
    public static String sanitize(String json) {
        if (json == null || json.isEmpty()) {
            return json;
        }
        
        StringBuilder result = new StringBuilder();
        boolean inString = false;
        boolean escaped = false;
        
        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);
            
            if (escaped) {
                // Previous character was a backslash, so this character is escaped
                result.append(c);
                escaped = false;
                continue;
            }
            
            if (c == '\\') {
                // This is an escape character
                result.append(c);
                escaped = true;
                continue;
            }
            
            if (c == '"') {
                if (!inString) {
                    // Starting a string
                    inString = true;
                    result.append(c);
                } else {
                    // Check if this quote is followed by a JSON structural character
                    // If yes, it's likely a string terminator
                    // If no, it's likely an unescaped quote within the string
                    int nextNonWhitespace = findNextNonWhitespace(json, i + 1);
                    if (nextNonWhitespace < json.length()) {
                        char nextChar = json.charAt(nextNonWhitespace);
                        if (isJsonStructuralChar(nextChar)) {
                            // This is a string terminator
                            inString = false;
                            result.append(c);
                        } else {
                            // This is an unescaped quote within the string - escape it
                            result.append("\\\"");
                        }
                    } else {
                        // End of string, must be a terminator
                        inString = false;
                        result.append(c);
                    }
                }
            } else {
                result.append(c);
            }
        }
        
        return result.toString();
    }
    
    /**
     * Checks if sanitization was applied by comparing original and sanitized strings.
     * 
     * @param original The original JSON string
     * @param sanitized The sanitized JSON string
     * @return true if sanitization changed the string
     */
    public static boolean wasSanitized(String original, String sanitized) {
        return original != null && !original.equals(sanitized);
    }
    
    /**
     * Finds the index of the next non-whitespace character starting from the given position.
     * 
     * @param str The string to search
     * @param start The starting position
     * @return Index of next non-whitespace character, or string length if none found
     */
    private static int findNextNonWhitespace(String str, int start) {
        for (int i = start; i < str.length(); i++) {
            if (!Character.isWhitespace(str.charAt(i))) {
                return i;
            }
        }
        return str.length();
    }
    
    /**
     * Checks if a character is a JSON structural character (indicates end of string value).
     * Structural chars: , } ] : (comma, closing brace, closing bracket, colon)
     * 
     * @param c The character to check
     * @return true if the character is a JSON structural character
     */
    private static boolean isJsonStructuralChar(char c) {
        return c == ',' || c == '}' || c == ']' || c == ':';
    }
}
