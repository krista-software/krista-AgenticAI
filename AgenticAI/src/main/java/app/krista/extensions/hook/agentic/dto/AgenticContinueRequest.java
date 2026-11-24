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

package app.krista.extensions.hook.agentic.dto;

/**
 * Request to continue an agentic conversation after a tool has been executed.
 * Contains the session ID, tool name, and tool result. The LLM derives success
 * context directly from the toolResult payload — no redundant boolean flag needed.
 */
public class AgenticContinueRequest {

    private String sessionId;
    private String toolName;
    private Object toolResult;

    public AgenticContinueRequest() {
    }

    public AgenticContinueRequest(String sessionId, String toolName, Object toolResult) {
        this.sessionId = sessionId;
        this.toolName = toolName;
        this.toolResult = toolResult;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getToolName() {
        return toolName;
    }

    public void setToolName(String toolName) {
        this.toolName = toolName;
    }

    public Object getToolResult() {
        return toolResult;
    }

    public void setToolResult(Object toolResult) {
        this.toolResult = toolResult;
    }

    @Override
    public String toString() {
        return "AgenticContinueRequest{" +
                "sessionId='" + sessionId + '\'' +
                ", toolName='" + toolName + '\'' +
                ", toolResult=" + toolResult +
                '}';
    }
}
