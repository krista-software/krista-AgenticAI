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

package app.krista.extensions.hook.llm.krista.util;

@SuppressWarnings("unused")
public class KristaConstants {

    // Model names
    public static final String QWEN_32B_AWQ = "Qwen/Qwen3-32B-AWQ";
    
    // API Configuration
    public static final String DEFAULT_API_URL = "http://10.0.0.102:8000/v1/chat/completions";
    public static final int DEFAULT_MAX_TOKENS = 8192;
    public static final int DEFAULT_TIMEOUT_SECONDS = 60;
    public static final double DEFAULT_TEMPERATURE = 0.0;
    public static final double DEFAULT_TOP_P = 0.95;
    public static final int DEFAULT_TOP_K = 20;
    public static final double DEFAULT_MIN_P = 0.0;
    public static final double DEFAULT_PRESENCE_PENALTY = 0.0;
    public static final boolean DEFAULT_STREAM = false;
    public static final boolean DEFAULT_ENABLE_THINKING = false;
    
    // HTTP Status Codes
    public static final int HTTP_OK = 200;
    public static final int HTTP_RATE_LIMIT = 429;

    // HTTP Headers
    public static final String AUTHORIZATION_HEADER = "Authorization";
    public static final String CONTENT_TYPE_HEADER = "Content-Type";
    public static final String CONTENT_TYPE = "application/json";
    public static final String BEARER_PREFIX = "Bearer ";

    // JSON Field Names
    public static final String MODEL_FIELD = "model";
    public static final String MESSAGES_FIELD = "messages";
    public static final String MAX_TOKENS_FIELD = "max_tokens";
    public static final String TEMPERATURE_FIELD = "temperature";
    public static final String TOP_P_FIELD = "top_p";
    public static final String TOP_K_FIELD = "top_k";
    public static final String MIN_P_FIELD = "min_p";
    public static final String PRESENCE_PENALTY_FIELD = "presence_penalty";
    public static final String STREAM_FIELD = "stream";
    public static final String CHAT_TEMPLATE_KWARGS_FIELD = "chat_template_kwargs";
    public static final String ENABLE_THINKING_FIELD = "enable_thinking";
    public static final String ROLE_FIELD = "role";
    public static final String CONTENT_FIELD = "content";
    public static final String SYSTEM_ROLE = "system";
    public static final String USER_ROLE = "user";

    // Error Messages
    public static final String API_KEY_EMPTY_ERROR = "API Key is missing!";
    public static final String MODEL_EMPTY_ERROR = "Model name is missing!";
    public static final String NULL_RESPONSE_ERROR = "Received null response from Krista API";
    public static final String API_CALL_FAILED_ERROR = "Krista API call failed: ";
    public static final String RATE_LIMIT_ERROR = "Rate limit exceeded - server is busy";
    public static final String API_ERROR_FORMAT = "Krista API error - Status: %d, URL: %s, Response: %s";
    public static final String NO_RESPONSE_BODY = "No response body";
    public static final String EMPTY_RESPONSE_ERROR = "Empty response from Krista API at ";
    public static final String NO_CHOICES_ERROR = "No choices in response from Krista API. Response: ";
    public static final String PARSE_RESPONSE_ERROR = "Failed to parse Krista API response: ";
    public static final String CONNECTION_REFUSED_ERROR = "Connection refused to Krista LLM server at ";
    public static final String CONNECTION_REFUSED_ADVICE = ". Please check if the server is running and accessible.";
    public static final String TIMEOUT_ERROR = "Timeout connecting to Krista LLM server at ";
    public static final String TIMEOUT_ADVICE = ". Server may be overloaded or slow to respond.";
}
