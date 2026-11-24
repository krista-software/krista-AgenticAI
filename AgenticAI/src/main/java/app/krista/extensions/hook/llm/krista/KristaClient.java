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

package app.krista.extensions.hook.llm.krista;

import app.krista.extensions.hook.llm.HttpClientPool;
import app.krista.extensions.hook.llm.LLMClient;
import app.krista.extensions.hook.llm.krista.util.KristaResponse;
import app.krista.extensions.hook.util.GsonProvider;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import okhttp3.*;

import java.io.IOException;

import static app.krista.extensions.hook.llm.krista.util.KristaConstants.*;

@SuppressWarnings("unused")
public class KristaClient implements LLMClient {

    private final OkHttpClient httpClient;
    private final Gson gson;
    private final String apiKey;
    private final String model;
    private final String apiUrl;
    private final int timeoutSeconds;

    public KristaClient(String apiKey, String model) {
        this(apiKey, model, DEFAULT_TIMEOUT_SECONDS);
    }

    public KristaClient(String apiKey, String model, int timeoutSeconds) {
        if (apiKey == null || apiKey.isEmpty()) {
            throw new IllegalArgumentException(API_KEY_EMPTY_ERROR);
        }
        if (model == null || model.isEmpty()) {
            throw new IllegalArgumentException(MODEL_EMPTY_ERROR);
        }

        // Use pooled HTTP client for better resource management and concurrency
        this.httpClient = HttpClientPool.getInstance().getClient("krista", timeoutSeconds);

        this.gson = GsonProvider.getInstance();
        this.apiKey = apiKey;
        this.model = model;
        this.apiUrl = DEFAULT_API_URL;
        this.timeoutSeconds = timeoutSeconds;
    }

    @Override
    public String query(String systemPrompt, String userPrompt) {
        try {
            String requestBody = createRequestBody(systemPrompt, userPrompt);
            Request request = buildRequest(requestBody);
            KristaResponse response = execute(request);

            return response.getAnswer();
        } catch (IOException e) {
            throw new RuntimeException(API_CALL_FAILED_ERROR + e.getMessage(), e);
        }
    }

    private String createRequestBody(String systemPrompt, String userPrompt) {
        JsonObject systemMessage = new JsonObject();
        systemMessage.addProperty(ROLE_FIELD, SYSTEM_ROLE);
        systemMessage.addProperty(CONTENT_FIELD, systemPrompt);

        JsonObject userMessage = new JsonObject();
        userMessage.addProperty(ROLE_FIELD, USER_ROLE);
        userMessage.addProperty(CONTENT_FIELD, userPrompt);

        JsonArray messages = new JsonArray();
        messages.add(systemMessage);
        messages.add(userMessage);

        JsonObject requestJson = new JsonObject();
        requestJson.addProperty(MODEL_FIELD, model);
        requestJson.add(MESSAGES_FIELD, messages);
        requestJson.addProperty(MAX_TOKENS_FIELD, DEFAULT_MAX_TOKENS);
        requestJson.addProperty(TEMPERATURE_FIELD, DEFAULT_TEMPERATURE);
        requestJson.addProperty(TOP_P_FIELD, DEFAULT_TOP_P);
        requestJson.addProperty(TOP_K_FIELD, DEFAULT_TOP_K);
        requestJson.addProperty(MIN_P_FIELD, DEFAULT_MIN_P);
        requestJson.addProperty(PRESENCE_PENALTY_FIELD, DEFAULT_PRESENCE_PENALTY);
        requestJson.addProperty(STREAM_FIELD, DEFAULT_STREAM);

        // Add chat_template_kwargs
        JsonObject chatTemplateKwargs = new JsonObject();
        chatTemplateKwargs.addProperty(ENABLE_THINKING_FIELD, DEFAULT_ENABLE_THINKING);
        requestJson.add(CHAT_TEMPLATE_KWARGS_FIELD, chatTemplateKwargs);

        return requestJson.toString();
    }

    private Request buildRequest(String requestBody) {
        Request.Builder builder = new Request.Builder()
                .url(apiUrl)
                .addHeader(CONTENT_TYPE_HEADER, CONTENT_TYPE)
                .post(RequestBody.create(
                        requestBody,
                        MediaType.parse(CONTENT_TYPE)
                ));

        // Add API key header (required for Krista LLM authentication)
        builder.addHeader(AUTHORIZATION_HEADER, BEARER_PREFIX + apiKey);

        return builder.build();
    }

    private KristaResponse execute(Request request) throws IOException {
        try (Response response = httpClient.newCall(request).execute()) {
            ResponseBody responseBody = response.body();
            String responseJson = responseBody == null ? null : responseBody.string();

            // Krista LLM doesn't implement rate limiting, so no 429 handling needed
            if (!response.isSuccessful()) {
                String errorMessage = String.format(
                    API_ERROR_FORMAT,
                    response.code(),
                    apiUrl,
                    responseJson != null ? responseJson : NO_RESPONSE_BODY
                );
                throw new IOException(errorMessage);
            }

            if (responseJson == null || responseJson.isEmpty()) {
                throw new IOException(EMPTY_RESPONSE_ERROR + apiUrl);
            }

            try {
                KristaResponse kristaResponse = gson.fromJson(responseJson, KristaResponse.class);
                if (kristaResponse.getChoices() == null || kristaResponse.getChoices().isEmpty()) {
                    throw new IOException(NO_CHOICES_ERROR + responseJson);
                }
                return kristaResponse;
            } catch (Exception e) {
                throw new IOException(PARSE_RESPONSE_ERROR + responseJson, e);
            }
        } catch (java.net.ConnectException e) {
            System.out.println("KristaClient: Connection refused to Krista LLM server at " + apiUrl);
            throw new IOException(CONNECTION_REFUSED_ERROR + apiUrl + CONNECTION_REFUSED_ADVICE, e);
        } catch (java.net.SocketTimeoutException e) {
            System.out.println("KristaClient: TIMEOUT occurred while connecting to Krista LLM server at " + apiUrl +
                " (configured timeout: " + timeoutSeconds + "s)");
            throw new IOException(TIMEOUT_ERROR + apiUrl + TIMEOUT_ADVICE, e);
        }
    }

    /**
     * Get the configured timeout in seconds for this client
     */
    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }
}
