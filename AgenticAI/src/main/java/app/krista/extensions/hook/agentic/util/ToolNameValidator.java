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

package app.krista.extensions.hook.agentic.util;

import app.krista.extensions.hook.agentic.dto.AgenticToolDefinition;

import java.util.List;

/**
 * Utility class for validating tool names returned by LLM.
 * Provides exact match validation and builds retry context messages.
 */
public class ToolNameValidator {
    
    /**
     * Validates that the returned tool name matches one of the available tools exactly.
     * 
     * @param toolName The tool name returned by LLM
     * @param availableTools List of available tools to match against
     * @return true if exact match found, false otherwise
     */
    public static boolean isValidToolName(String toolName, 
                                         List<AgenticToolDefinition> availableTools) {
        if (toolName == null || toolName.isEmpty()) {
            return false;
        }
        
        // Exact match check
        for (AgenticToolDefinition tool : availableTools) {
            if (tool.name().equals(toolName)) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Builds a context message for LLM retry when validation fails.
     * Shows the invalid tool name and examples of valid tools.
     * 
     * @param invalidToolName The invalid tool name returned by LLM
     * @param availableTools List of available tools
     * @return Error context message for retry
     */
    public static String buildRetryContext(String invalidToolName,
                                          List<AgenticToolDefinition> availableTools) {
        StringBuilder context = new StringBuilder();
        context.append("ERROR: The tool name you selected does not exist in the available tools list.\n\n");
        context.append("You selected: ").append(invalidToolName).append("\n\n");
        context.append("This tool name is INVALID because it doesn't match any tool exactly.\n\n");
        context.append("Examples of valid tool names (copy one EXACTLY):\n");
        
        // Show first 5 tools as examples
        int count = 0;
        for (AgenticToolDefinition tool : availableTools) {
            if (count >= 5) {
                context.append("... and ").append(availableTools.size() - 5).append(" more tools\n");
                break;
            }
            context.append("- ").append(tool.name()).append("\n");
            count++;
        }
        
        context.append("\nCRITICAL: Copy the ENTIRE 'name' field JSON string exactly, including all braces { }, quotes \", colons :, and commas ,");
        
        return context.toString();
    }
}
