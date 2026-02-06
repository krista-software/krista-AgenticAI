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

package app.krista.extensions.hook.llm.claude;
import app.krista.extensions.hook.llm.HttpClientPool;
import app.krista.extensions.hook.llm.LLMClient;
import app.krista.extensions.hook.llm.RateLimitException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import okhttp3.*;


import java.io.IOException;

import static app.krista.extensions.hook.llm.claude.util.ClaudeConstants.*;

public class ClaudeClient implements LLMClient {
    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final String model;
    private final int timeoutSeconds;

    public ClaudeClient(String apiKey, String model) {
        this(apiKey, model, DEFAULT_TIMEOUT_SECONDS);
    }

    public ClaudeClient(String apiKey, String model, int timeoutSeconds) {
        if (apiKey == null || apiKey.isEmpty()) {
            throw new IllegalArgumentException(API_KEY_EMPTY_ERROR);
        }

        // Use pooled HTTP client for better resource management and concurrency
        this.httpClient = HttpClientPool.getInstance().getClient("claude", timeoutSeconds);

        this.objectMapper = new ObjectMapper();
        this.apiKey = apiKey;
        this.model = model;
        this.timeoutSeconds = timeoutSeconds;
    }

    /**
     * Send a simple text query
     *
     * @param systemPrompt system instructions
     * @param userPrompt User's query
     * @return Response from Claude API
     */
    @Override
    public String query(String systemPrompt, String userPrompt) {
        try {
            String requestBody = createSimpleRequestBody(systemPrompt, userPrompt);
            Request request = buildRequest(requestBody);

            try (Response response = httpClient.newCall(request).execute()) {
                return handleResponse(response);
            } catch (java.net.SocketTimeoutException e) {
                System.out.println("ClaudeClient: TIMEOUT occurred while connecting to Claude API at " + DEFAULT_API_URL +
                    " (configured timeout: " + timeoutSeconds + "s)");
                throw new IOException(TIMEOUT_ERROR, e);
            }
        } catch (RateLimitException e) {
            // Re-throw rate limit exceptions directly to be handled by service layer
            throw new RuntimeException("Rate limit exceeded: " + e.getMessage() + " (Provider: " + e.getProvider() + ", Retry after: " + e.getRetryAfterSeconds() + "s)", e);
        } catch (IOException e) {
            throw new RuntimeException(API_CALL_FAILED_ERROR + e.getMessage(), e);
        }
    }

    private Request buildRequest(String requestBody) {
        return new Request.Builder()
                .url(DEFAULT_API_URL)
                .addHeader(API_KEY_HEADER, apiKey)
                .addHeader(CONTENT_TYPE_HEADER, CONTENT_TYPE)
                .addHeader(ANTHROPIC_VERSION_HEADER, ANTHROPIC_VERSION)
//                .addHeader(ANTHROPIC_BETA_HEADER, ANTHROPIC_BETA)
                .post(RequestBody.create(
                        requestBody,
                        MediaType.parse(CONTENT_TYPE)
                ))
                .build();
    }

    private String handleResponse(Response response) throws IOException, RateLimitException {
        if (response.code() == 429) {
            // Handle rate limiting with retry-after information
            long retryAfterSeconds = extractRetryAfterSeconds(response);
            String rateLimitMessage = "Claude API rate limit exceeded (retry after " + retryAfterSeconds + " seconds)";
            throw new RateLimitException(rateLimitMessage, "Claude", retryAfterSeconds);
        }

        if (response.isSuccessful() && response.body() != null) {
            String responseBody = response.body().string();
            return extractResponseText(responseBody);
        } else {
            String errorBody = response.body() != null ? response.body().string() : "null";
            throw new IOException(HTTP_ERROR_PREFIX + response.code() + ": " + errorBody);
        }
    }

    private String extractResponseText(String responseBody) {
        try {
            ObjectNode responseJson = (ObjectNode) objectMapper.readTree(responseBody);
            if (responseJson.has(CONTENT_FIELD)) {
                return responseJson.get(CONTENT_FIELD)
                        .get(CONTENT_ARRAY_INDEX)
                        .get(TEXT_FIELD)
                        .asText();
            }
            return "";
        } catch (JsonProcessingException e) {
            throw new RuntimeException(RESPONSE_PARSE_ERROR + e.getMessage(), e);
        }
    }

    private String createSimpleRequestBody(String systemPrompt, String userPrompt) {
        ObjectNode rootNode = objectMapper.createObjectNode();
        rootNode.put(MODEL_FIELD, model);
        rootNode.put(MAX_TOKENS_FIELD, DEFAULT_MAX_TOKENS);

        ObjectNode userMessage = objectMapper.createObjectNode();
        userMessage.put(ROLE_FIELD, USER_ROLE);
        userMessage.put(CONTENT_FIELD, userPrompt);

        ArrayNode messagesArray = rootNode.putArray(MESSAGES_FIELD);
        rootNode.put(SYSTEM_FIELD, systemPrompt);
        messagesArray.add(userMessage);

        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(rootNode);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(REQUEST_BODY_ERROR, e);
        }
    }

    /**
     * Extract retry-after seconds from Claude API response headers.
     * Claude/Anthropic uses 'retry-after' header as documented in their API docs.
     *
     * @param response HTTP response with potential retry-after header
     * @return Number of seconds to wait before retrying (default 60 if header missing)
     */
    private long extractRetryAfterSeconds(Response response) {
        String retryAfterHeader = response.header(RETRY_AFTER_HEADER);
        if (retryAfterHeader == null || retryAfterHeader.trim().isEmpty()) {
            System.out.println("ClaudeClient: No retry-after header found, defaulting to " + DEFAULT_RETRY_AFTER_SECONDS + " seconds");
            return DEFAULT_RETRY_AFTER_SECONDS;
        }

        try {
            // Claude typically returns seconds as documented
            long retryAfter = Long.parseLong(retryAfterHeader.trim());
            System.out.println("ClaudeClient: Rate limited, retry after " + retryAfter + " seconds (from retry-after header)");
            return retryAfter;
        } catch (NumberFormatException e) {
            // If not a number, might be HTTP date format - default to 60 seconds
            System.out.println("ClaudeClient: Could not parse retry-after header '" +
                retryAfterHeader + "', defaulting to " + DEFAULT_RETRY_AFTER_SECONDS + " seconds");
            return DEFAULT_RETRY_AFTER_SECONDS;
        }
    }
}