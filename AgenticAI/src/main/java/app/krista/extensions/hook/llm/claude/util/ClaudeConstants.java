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

package app.krista.extensions.hook.llm.claude.util;

@SuppressWarnings("unused")
public class ClaudeConstants {

    // Model names
    public static final String CLAUDE_3_7_SONNET = "claude-3-7-sonnet-20250219";
    public static final String CLAUDE_SONNET_4 = "claude-sonnet-4-20250514";
    public static final String CLAUDE_3_5_HAIKU = "claude-3-5-haiku-20241022";
    public static final String CLAUDE_3_OPUS = "claude-3-opus-20240229";

    // API Configuration
    public static final String DEFAULT_API_URL = "https://api.anthropic.com/v1/messages";
    public static final int DEFAULT_MAX_TOKENS = 1024;
    public static final int DEFAULT_TIMEOUT_SECONDS = 60;

    // HTTP Headers
    public static final String API_KEY_HEADER = "x-api-key";
    public static final String CONTENT_TYPE_HEADER = "Content-Type";
    public static final String CONTENT_TYPE = "application/json";
    public static final String ANTHROPIC_VERSION_HEADER = "anthropic-version";
    public static final String ANTHROPIC_BETA_HEADER = "anthropic-beta";
    public static final String ANTHROPIC_VERSION = "2023-06-01";
    public static final String ANTHROPIC_BETA = "pdfs-2024-09-25";

    // JSON Field Names
    public static final String MODEL_FIELD = "model";
    public static final String MAX_TOKENS_FIELD = "max_tokens";
    public static final String ROLE_FIELD = "role";
    public static final String CONTENT_FIELD = "content";
    public static final String MESSAGES_FIELD = "messages";
    public static final String SYSTEM_FIELD = "system";
    public static final String USER_ROLE = "user";
    public static final String TEXT_FIELD = "text";
    public static final int CONTENT_ARRAY_INDEX = 0;

    // Error Messages
    public static final String API_KEY_EMPTY_ERROR = "API Key is missing!";
    public static final String API_CALL_FAILED_ERROR = "Claude API call failed: ";
    public static final String HTTP_ERROR_PREFIX = "HTTP error ";
    public static final String RESPONSE_PARSE_ERROR = "Failed to parse Claude response: ";
    public static final String REQUEST_BODY_ERROR = "Failed to create simple request body";
    public static final String TIMEOUT_ERROR = "Timeout connecting to Claude API. Server may be overloaded or slow to respond.";

    // Rate Limiting Headers (Claude/Anthropic specific)
    public static final String RETRY_AFTER_HEADER = "retry-after";
    public static final long DEFAULT_RETRY_AFTER_SECONDS = 60;

}