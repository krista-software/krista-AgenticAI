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

package app.krista.extensions.hook.llm.openai;

import java.io.IOException;

import app.krista.extensions.hook.llm.HttpClientPool;
import app.krista.extensions.hook.llm.LLMClient;
import app.krista.extensions.hook.llm.RateLimitException;
import app.krista.extensions.hook.llm.openai.util.ChatGPTResponse;
import app.krista.extensions.hook.llm.openai.util.StringI18N;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;

import static app.krista.extensions.hook.llm.openai.util.OpenAIConstants.*;

@SuppressWarnings("unused")
public class OpenAIClient implements LLMClient {

    private final OkHttpClient httpClient;
    private final String apiKey;
    private final String modelName;
    private final int timeoutSeconds;

    public OpenAIClient(String apiKey, String modelName) {
        this(apiKey, modelName, DEFAULT_TIMEOUT_SECONDS);
    }

    public OpenAIClient(String apiKey, String modelName, int timeoutSeconds) {
        this.apiKey = apiKey;
        this.modelName = modelName;
        this.timeoutSeconds = timeoutSeconds;

        // Use pooled HTTP client for better resource management and thread safety
        this.httpClient = HttpClientPool.getInstance().getClient("openai", timeoutSeconds);
    }

    @Override
    public String query(String systemPrompt, String userPrompt) {
        try {
            if(apiKey == null || apiKey.isBlank()){
                throw new IllegalArgumentException(API_KEY_EMPTY_ERROR);
            }
            if(modelName == null || modelName.isBlank()){
                throw new IllegalArgumentException(MODEL_NAME_EMPTY_ERROR);
            }

            String requestBody = createSimpleRequestBody(systemPrompt, userPrompt);
            ChatGPTResponse response = execute(requestBody, apiKey, modelName);

            if (response == null) {
                throw new RuntimeException(NULL_RESPONSE_ERROR);
            }

            return response.getAnswer();
        } catch (RateLimitException e) {
            // Re-throw rate limit exceptions directly to be handled by service layer
            throw new RuntimeException("Rate limit exceeded: " + e.getMessage() + " (Provider: " + e.getProvider() + ", Retry after: " + e.getRetryAfterSeconds() + "s)", e);
        } catch (IOException e) {
            throw new RuntimeException(API_CALL_FAILED_ERROR + e.getMessage(), e);
        }
    }

    private String createSimpleRequestBody(String systemPrompt, String userPrompt) {
        JsonObject systemPromptJson = new JsonObject();
        systemPromptJson.addProperty("role", "system");
        systemPromptJson.addProperty("content", systemPrompt);

        JsonObject userPromptJson = new JsonObject();
        userPromptJson.addProperty("role", "user");
        userPromptJson.addProperty("content", userPrompt);

        JsonArray messages = new JsonArray();
        messages.add(systemPromptJson);
        messages.add(userPromptJson);
        JsonObject finalJson = new JsonObject();
        finalJson.add("messages", messages);
        return finalJson.toString();
    }

    public ChatGPTResponse execute(String rawDocumentJson, String apiKey, String modelName) throws IOException, RateLimitException {
        rawDocumentJson = addModelParameters(rawDocumentJson, modelName);

        RequestBody body = RequestBody.create(rawDocumentJson, MediaType.parse(CONTENT_TYPE));
        okhttp3.Request request = new okhttp3.Request.Builder()
                .url(DEFAULT_API_URL)
                .addHeader(AUTHORIZATION_HEADER, BEARER_PREFIX + apiKey)
                .addHeader(API_KEY_HEADER, apiKey)
                .addHeader(CONTENT_TYPE_HEADER, CONTENT_TYPE)
                .addHeader(ACCEPT_HEADER, ACCEPT_TYPE)
                .post(body)
                .build();

        try (okhttp3.Response response = httpClient.newCall(request).execute()) {
            ResponseBody responseBody = response.body();
            String responseJson = responseBody == null ? null : responseBody.string();

            if (response.code() == HTTP_RATE_LIMIT) {
                // Extract retry-after information from headers
                long retryAfterSeconds = extractRetryAfterSeconds(response);
                String rateLimitMessage = RATE_LIMIT_ERROR + " (retry after " + retryAfterSeconds + " seconds)";
                throw new RateLimitException(rateLimitMessage, "OpenAI", retryAfterSeconds);
            }

            if (response.code() != HTTP_OK) {
                if (StringI18N.isEmpty(responseJson)) {
                    throw new IOException(HTTP_ERROR_PREFIX + response.code() + NO_RESPONSE_BODY_ERROR);
                } else {
                    JsonObject chat = new Gson().fromJson(responseJson, JsonObject.class);
                    String errorMessage = chat.get("error").getAsJsonObject().get("message").toString();
                    throw new IOException(HTTP_ERROR_PREFIX + response.code() + ": " + errorMessage);
                }
            }

            if (StringI18N.isEmpty(responseJson)) {
                return null;
            }

            ChatGPTResponse chat = new Gson().fromJson(responseJson, ChatGPTResponse.class);
            if (chat.getChoices() == null || chat.getChoices().isEmpty()) {
                return null;
            }

            return chat;
        } catch (java.net.SocketTimeoutException e) {
            System.out.println("OpenAIClient: TIMEOUT occurred while connecting to OpenAI API at " + DEFAULT_API_URL +
                " (configured timeout: " + timeoutSeconds + "s)");
            throw new IOException(TIMEOUT_ERROR, e);
        }
    }

    private String addModelParameters(String rawDocumentJson, String modelName) {
        try{
            JsonObject json = new Gson().fromJson(rawDocumentJson, JsonObject.class);
            json.addProperty("model", modelName);
            json.addProperty("temperature", PRECISE_TEMPERATURE);
            json.addProperty("top_p", PRECISE_TOP_P);
            int tokensUsed = rawDocumentJson.length() / TOKEN_ESTIMATION_DIVISOR;
            json.addProperty("max_tokens", Math.min(tokensUsed, DEFAULT_MAX_TOKENS));
            return json.toString();
        }catch (JsonSyntaxException cause){
            throw new IllegalArgumentException(INVALID_FORMAT_ERROR);
        }
    }
    /**
     * Get the configured timeout in seconds for this client
     */
    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    /**
     * Extract retry-after seconds from OpenAI API response headers.
     * OpenAI uses 'retry-after' header and provides additional rate limit info via x-ratelimit-* headers.
     *
     * @param response HTTP response with potential rate limiting headers
     * @return Number of seconds to wait before retrying (default 60 if header missing)
     */
    private long extractRetryAfterSeconds(okhttp3.Response response) {
        // First try the retry-after header (primary source)
        String retryAfterHeader = response.header(RETRY_AFTER_HEADER);
        if (retryAfterHeader != null && !retryAfterHeader.trim().isEmpty()) {
            try {
                long retryAfter = Long.parseLong(retryAfterHeader.trim());
                System.out.println("OpenAIClient: Rate limited, retry after " + retryAfter + " seconds (from retry-after header)");
                return retryAfter;
            } catch (NumberFormatException e) {
                System.out.println("OpenAIClient: Could not parse retry-after header '" + retryAfterHeader + "'");
            }
        }

        // Fallback: try to extract from x-ratelimit-reset-requests header
        String resetRequestsHeader = response.header(X_RATELIMIT_RESET_REQUESTS);
        if (resetRequestsHeader != null && !resetRequestsHeader.trim().isEmpty()) {
            try {
                // This header typically contains a timestamp, calculate seconds from now
                long resetTime = Long.parseLong(resetRequestsHeader.trim());
                long currentTime = System.currentTimeMillis() / 1000;
                long waitTime = Math.max(1, resetTime - currentTime);
                System.out.println("OpenAIClient: Rate limited, retry after " + waitTime + " seconds (calculated from reset time)");
                return waitTime;
            } catch (NumberFormatException e) {
                System.out.println("OpenAIClient: Could not parse x-ratelimit-reset-requests header '" + resetRequestsHeader + "'");
            }
        }

        // Log available rate limit headers for debugging
        String remainingRequests = response.header(X_RATELIMIT_REMAINING_REQUESTS);
        String limitRequests = response.header(X_RATELIMIT_LIMIT_REQUESTS);
        System.out.println("OpenAIClient: Rate limit info - Remaining: " + remainingRequests + ", Limit: " + limitRequests);

        // Default fallback
        System.out.println("OpenAIClient: No valid rate limit headers found, defaulting to " + DEFAULT_RETRY_AFTER_SECONDS + " seconds");
        return DEFAULT_RETRY_AFTER_SECONDS;
    }





}