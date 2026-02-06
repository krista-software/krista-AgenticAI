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

package app.krista.extensions.hook.helper;

import app.krista.extension.executor.impl.AgentAction;

/**
 * Utility class for parsing and normalizing action strings
 */
public class ActionParser {

    /**
     * Parses action string to AgentAction enum
     */
    public static AgentAction parseAction(String action) {
        if (action == null || action.isEmpty()) {
            throw new IllegalArgumentException("Action cannot be null or empty");
        }

        // Force uppercase for the action string
        String upperAction = action.toUpperCase();
        System.out.println("Converted action to uppercase: " + upperAction);

        // Try to parse the enum directly
        try {
            return AgentAction.valueOf(upperAction);
        } catch (IllegalArgumentException e) {
            // If direct parsing fails, try normalization
            String normalizedAction = normalizeActionString(action);
            System.out.println("Normalized action: " + normalizedAction);
            return AgentAction.valueOf(normalizedAction);
        }
    }

    /**
     * Normalizes action string to match enum format.
     * Handles various formats like "summarize_inform", "summarizeInform", "SUMMARIZE_INFORM", etc.
     */
    private static String normalizeActionString(String action) {
        if (action == null || action.isEmpty()) {
            return "";
        }

        // First try direct uppercase conversion
        String upperAction = action.toUpperCase();

        // Check if it's already a valid enum value
        try {
            AgentAction.valueOf(upperAction);
            return upperAction;
        } catch (IllegalArgumentException e) {
            // Not a direct match, try other formats
        }

        // Handle camelCase (e.g., "summarizeInform" -> "SUMMARIZE_INFORM")
        if (!action.contains("_")) {
            StringBuilder result = new StringBuilder();
            for (char c : action.toCharArray()) {
                if (Character.isUpperCase(c) && !result.isEmpty()) {
                    result.append('_');
                }
                result.append(Character.toUpperCase(c));
            }
            return result.toString();
        }

        return upperAction;
    }

    /**
     * Gets all enum values as strings for error messages
     */
    public static String[] getEnumValuesAsString() {
        AgentAction[] values = AgentAction.values();
        String[] result = new String[values.length];
        for (int i = 0; i < values.length; i++) {
            result[i] = values[i].name();
        }
        return result;
    }
}
