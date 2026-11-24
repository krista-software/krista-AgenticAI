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

package app.krista.extensions.hook.agentic;

import app.krista.extensions.hook.util.JsonMapper;
import com.fasterxml.jackson.core.JsonProcessingException;

import java.util.Map;

/**
 * Constants for the agentic orchestration flow.
 */
public final class AgenticConstants {

    private AgenticConstants() {
    }

    // Tool type identifiers
    public static final String TOOL_TYPE_CONVERSATION = "conversation";
    public static final String TOOL_TYPE_INVOKER = "invoker";
    public static final String TOOL_TYPE_ASK_A_PERSON = "ask_a_person";
    public static final String TOOL_TYPE_INFORM_A_PERSON = "inform_a_person";
    public static final String TOOL_TYPE_ENTITY = "entity";
    public static final String TOOL_TYPE_CATALOG_REQUEST = "catalog_request";

    // Message history roles
    public static final String ROLE_USER = "user";
    public static final String ROLE_TOOL = "tool";

    // Message history field names
    public static final String FIELD_ROLE = "role";
    public static final String FIELD_TOOL_NAME = "toolName";
    public static final String FIELD_TOOL_TYPE = "toolType";
    public static final String FIELD_RESULT = "result";
    public static final String FIELD_TIMESTAMP = "timestamp";
    public static final String FIELD_CONTENT = "content";
    public static final String FIELD_STATUS = "status";
    public static final String FIELD_ERROR = "error";
    public static final String FIELD_MESSAGE = "message";
    public static final String FIELD_CHANNEL_NAME = "channelName";
    public static final String FIELD_CONVERSATION_NAME = "conversationName";
    public static final String FIELD_CATALOG_REQUEST_ID = "catalogRequestId";
    public static final String FIELD_INVOKER_ID = "invokerId";
    public static final String FIELD_ENTITY_ID = "entityId";
    public static final String FIELD_OPERATION_TYPE = "operationType";

    // Status values
    public static final String STATUS_ERROR = "error";
    public static final String STATUS_UNKNOWN = "unknown";

    // Fallback for null/missing tool names - structured JSON to not break downstream parsing
    public static final String UNRESOLVED_TOOL_NAME = buildUnresolvedToolName();

    // System-reserved variable names
    public static final String SYSTEM_VAR_CONVERSATION_HISTORY = "conversation_history";
    public static final String SYSTEM_VAR_TOOL_SUMMARY = "tool_summary";
    public static final String SYSTEM_VAR_SESSION_SUMMARY = "session_summary";

    // Session summary structured-field keys (under SYSTEM_VAR_SESSION_SUMMARY)
    public static final String SUMMARY_FIELD_SESSION_ID = "sessionId";
    public static final String SUMMARY_FIELD_PARENT_SESSION_ID = "parentSessionId";
    public static final String SUMMARY_FIELD_TERMINATION_REASON = "terminationReason";
    public static final String SUMMARY_FIELD_FINAL_RESPONSE = "finalResponse";
    public static final String SUMMARY_FIELD_ERROR_MESSAGE = "errorMessage";
    public static final String SUMMARY_FIELD_STARTED_AT = "startedAt";
    public static final String SUMMARY_FIELD_ENDED_AT = "endedAt";
    public static final String SUMMARY_FIELD_DURATION_MS = "durationMs";
    public static final String SUMMARY_FIELD_ITERATIONS = "iterations";
    public static final String SUMMARY_FIELD_CONVERSATION_HISTORY = "conversationHistory";
    public static final String SUMMARY_FIELD_TOOL_SUMMARY = "toolSummary";
    public static final String SUMMARY_FIELD_ACTIVITY_FEED = "activityFeed";
    public static final String SUMMARY_FIELD_CHILD_SESSIONS = "childSessions";
    public static final String SUMMARY_FIELD_USAGE = "usage";

    // Child session identifier
    public static final String SESSION_CHILD_PREFIX = ".child";
    public static final String SESSION_CHILD_SEPARATOR = ".child_";

    // Policy enforcement — two retries for self-correction, then hard block.
    // With the policy brief removed from the ToolPlanner prompt, the LLM relies solely on
    // the violation feedback message to self-correct; two retries provide enough turns.
    public static final int MAX_POLICY_RETRIES = 2;

    private static String buildUnresolvedToolName() {

        try {

            return JsonMapper.getInstance().writeValueAsString(
                Map.of("name", "unresolved", FIELD_TOOL_TYPE, STATUS_UNKNOWN)
            );
        } catch (JsonProcessingException e) {

            // Should never happen with a simple map of strings
            return "{\"name\":\"unresolved\",\"toolType\":\"unknown\"}";
        }
    }
}
