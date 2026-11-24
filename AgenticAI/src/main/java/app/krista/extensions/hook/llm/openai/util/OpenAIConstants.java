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

package app.krista.extensions.hook.llm.openai.util;

@SuppressWarnings("unused")
public class OpenAIConstants {

    // Model names
    public static final String GPT_4_1 = "gpt-4.1";
    public static final String GPT_5_2 = "gpt-5.2";
    public static final String GPT_4_1_mini = "gpt-4.1-mini";
    public static final String GPT_4_1_nano = "gpt-4.1-nano";

    // API Configuration
    public static final String DEFAULT_API_URL = "https://api.openai.com/v1/chat/completions";
    public static final int DEFAULT_TIMEOUT_SECONDS = 60;
    public static final int DEFAULT_CONNECT_TIMEOUT_SECONDS = 10;
    public static final int DEFAULT_MAX_TOKENS = 1024;

    // Model Parameters
    public static final double PRECISE_TEMPERATURE = 0.7;
    public static final double CREATIVE_TEMPERATURE = 0.8;
    public static final double PRECISE_TOP_P = 0.95;
    public static final double CREATIVE_TOP_P = 0.8;
    public static final int TOKEN_ESTIMATION_DIVISOR = 3;

    // HTTP Configuration
    public static final String CONTENT_TYPE = "application/json; charset=utf-8";
    public static final String ACCEPT_TYPE = "application/json; charset=utf-8";
    public static final String AUTHORIZATION_HEADER = "Authorization";
    public static final String API_KEY_HEADER = "api-key";
    public static final String CONTENT_TYPE_HEADER = "Content-Type";
    public static final String ACCEPT_HEADER = "Accept";
    public static final String BEARER_PREFIX = "Bearer ";

    // HTTP Status Codes
    public static final int HTTP_OK = 200;
    public static final int HTTP_RATE_LIMIT = 429;

    // Error Messages
    public static final String API_KEY_EMPTY_ERROR = "API Key not found!";
    public static final String MODEL_NAME_EMPTY_ERROR = "Model name not found!";
    public static final String NULL_RESPONSE_ERROR = "Received null response from OpenAI API";
    public static final String API_CALL_FAILED_ERROR = "OpenAI API call failed: ";
    public static final String RATE_LIMIT_ERROR = "Rate limit exceeded";
    public static final String HTTP_ERROR_PREFIX = "HTTP error ";
    public static final String NO_RESPONSE_BODY_ERROR = ": No response body";
    public static final String INVALID_FORMAT_ERROR = "Input prompt was not in the correct format!";
    public static final String TIMEOUT_ERROR = "Timeout connecting to OpenAI API. Server may be overloaded or slow to respond.";

    // Rate Limiting Headers (OpenAI specific)
    public static final String RETRY_AFTER_HEADER = "retry-after";
    public static final String X_RATELIMIT_LIMIT_REQUESTS = "x-ratelimit-limit-requests";
    public static final String X_RATELIMIT_REMAINING_REQUESTS = "x-ratelimit-remaining-requests";
    public static final String X_RATELIMIT_RESET_REQUESTS = "x-ratelimit-reset-requests";
    public static final String X_RATELIMIT_LIMIT_TOKENS = "x-ratelimit-limit-tokens";
    public static final String X_RATELIMIT_REMAINING_TOKENS = "x-ratelimit-remaining-tokens";
    public static final String X_RATELIMIT_RESET_TOKENS = "x-ratelimit-reset-tokens";
    public static final long DEFAULT_RETRY_AFTER_SECONDS = 60;
}