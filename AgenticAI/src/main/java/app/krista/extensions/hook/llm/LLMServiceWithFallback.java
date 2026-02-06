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

package app.krista.extensions.hook.llm;

import app.krista.extension.executor.Invoker;
import app.krista.extensions.hook.config.LLMConfiguration;
import app.krista.extensions.hook.factory.LLMClientFactory;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Service layer that provides automatic fallback from Krista LLM to cloud LLM.
 * Always tries Krista LLM first (configurable attempts), then falls back to cloud LLM (3 attempts with retry).
 * Handles retry logic, error handling, and logging consistently across all LLM providers.
 */
@SuppressWarnings("unchecked")
public class LLMServiceWithFallback {

    private final LLMClientFactory llmClientFactory;
    private final LLMConfiguration llmConfiguration;
    private final ObjectMapper objectMapper;

    // Retry configuration
    private static final int KRISTA_MAX_ATTEMPTS = 1;
    private static final int CLOUD_MAX_RETRY_ATTEMPTS = 3;
    private static final long RETRY_DELAY_MS = 250L;

    // Timeout configuration
    private static final int KRISTA_TIMEOUT_SECONDS = 15;
    private static final int CLOUD_TIMEOUT_SECONDS = 15;

    public LLMServiceWithFallback(Invoker invoker) {
        this.llmClientFactory = new LLMClientFactory(invoker);
        this.llmConfiguration = new LLMConfiguration(invoker);
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Extract variable mapping and move_to_next_step flag from entity extraction prompt.
     * Used with EntityExtraction.txt prompt.
     *
     * @param systemPrompt system instructions
     * @param userPrompt user query
     * @return EntityExtractionResult containing variableMapping and moveToNextStep
     */
    public EntityExtractionResult extractVariableMapping(String systemPrompt, String userPrompt) {
        Map<String, Object> response = queryAsMap(systemPrompt, userPrompt);

        Map<String, Object> variableMapping = (Map<String, Object>) response.get("variableMapping");
        if (variableMapping == null) {
            variableMapping = new HashMap<>();
        }

        Object moveToNextStep = response.get("move_to_next_step");
        boolean shouldMoveToNextStep = moveToNextStep instanceof Boolean && Boolean.TRUE.equals(moveToNextStep);

        return new EntityExtractionResult(variableMapping, shouldMoveToNextStep);
    }

    /**
     * Generate response text from prompts that return a "response" field.
     * Used with AskUser.txt, ConfirmDetails.txt, and InformUser.txt prompts.
     *
     * @param systemPrompt system instructions
     * @param userPrompt user query
     * @return response text
     */
    public String generateResponse(String systemPrompt, String userPrompt) {
        Map<String, Object> response = queryAsMap(systemPrompt, userPrompt);
        return (String) response.get("response");
    }

    /**
     * Query LLM with automatic fallback from Krista to cloud LLM.
     * This is the main method that handles all LLM interactions with fallback logic.
     *
     * @param systemPrompt system instructions
     * @param userPrompt user query
     * @return response string from LLM
     */
    public String queryWithFallback(String systemPrompt, String userPrompt) {
        System.out.println("LLMServiceWithFallback: Starting query with automatic fallback strategy");
        long startTime = System.currentTimeMillis();
        String attemptedCloudProvider;

        // Check if Krista LLM fallback is enabled
        boolean kristaFallbackEnabled = llmConfiguration.isKristaLLMFallbackEnabled();

        if (kristaFallbackEnabled) {
            // Step 1: Try Krista LLM first (configurable attempts)
            String kristaResponse = tryKristaLLM(systemPrompt, userPrompt);
            if (kristaResponse != null) {
                long duration = System.currentTimeMillis() - startTime;
                System.out.println("LLMServiceWithFallback: Query completed successfully with Krista LLM in " + duration + "ms");
                return kristaResponse;
            }

            // Step 2: Fallback to cloud LLM (3 attempts with retry)
            System.out.println("LLMServiceWithFallback: Switching to cloud LLM fallback strategy");
        } else {
            // Krista LLM fallback is disabled, go directly to cloud LLM
            System.out.println("LLMServiceWithFallback: Krista LLM fallback is disabled, using cloud LLM directly");
        }

        // Store which cloud provider we're attempting
        attemptedCloudProvider = llmConfiguration.getCloudProvider();
        String cloudResponse = tryCloudLLM(systemPrompt, userPrompt);
        if (cloudResponse != null) {
            long duration = System.currentTimeMillis() - startTime;
            System.out.println("LLMServiceWithFallback: Query completed successfully with cloud LLM in " + duration + "ms");
            return cloudResponse;
        }

        // All attempts failed - check if it's due to rate limiting (only if we attempted cloud LLM)
        if (attemptedCloudProvider != null) {
            RateLimitTracker rateLimitTracker = RateLimitTracker.getInstance();
            if (rateLimitTracker.isRateLimited(attemptedCloudProvider)) {
                long remainingSeconds = rateLimitTracker.getRemainingRateLimitSeconds(attemptedCloudProvider);
                long duration = System.currentTimeMillis() - startTime;
                System.out.println("LLMServiceWithFallback: Request failed due to rate limiting after " + duration + "ms");
                return "Sorry. Our AI Service is currently rate limited. Please try again in " + remainingSeconds + " seconds.";
            }
        }

        // All attempts failed for other reasons
        long duration = System.currentTimeMillis() - startTime;
        System.out.println("LLMServiceWithFallback: All LLM providers failed after " + duration + "ms");
        return "Sorry. Our AI Service is experiencing issues right now. Please try again soon.";
    }

    /**
     * Try Krista LLM (configurable attempts)
     */
    private String tryKristaLLM(String systemPrompt, String userPrompt) {
        try {
            System.out.println("LLMServiceWithFallback: Attempting Krista LLM (" + KRISTA_MAX_ATTEMPTS + " attempt" + (KRISTA_MAX_ATTEMPTS == 1 ? "" : "s") + ") with " + KRISTA_TIMEOUT_SECONDS + "s timeout");
            LLMClient kristaClient = llmClientFactory.createKristaClient(KRISTA_TIMEOUT_SECONDS);
            System.out.println("LLMServiceWithFallback: Created Krista client: " + kristaClient.getClass().getSimpleName());

            for (int attempt = 1; attempt <= KRISTA_MAX_ATTEMPTS; attempt++) {
                try {
                    if (KRISTA_MAX_ATTEMPTS > 1) {
                        System.out.println("LLMServiceWithFallback: Krista LLM attempt " + attempt + " of " + KRISTA_MAX_ATTEMPTS);
                    }

                    String response = kristaClient.query(systemPrompt, userPrompt);

                    if (response == null || response.trim().isEmpty()) {
                        System.out.println("LLMServiceWithFallback: Krista LLM returned empty response on attempt " + attempt);
                        if (attempt < KRISTA_MAX_ATTEMPTS) {
                            System.out.println("LLMServiceWithFallback: Retrying Krista LLM in " + RETRY_DELAY_MS + "ms...");
                            briefPause();
                            continue;
                        }
                        return null;
                    }

                    System.out.println("LLMServiceWithFallback: Krista LLM response length: " + response.length() + " characters");
                    System.out.println("LLMServiceWithFallback: Krista LLM response content:");
                    System.out.println(beautifyJson(response));
                    return response;

                } catch (Exception e) {
                    String errorMessage = e.getMessage();
                    boolean isTimeout = errorMessage != null && (errorMessage.toLowerCase().contains("timeout") ||
                                       errorMessage.toLowerCase().contains("sockettimeout"));

                    // Krista LLM doesn't implement rate limiting, so no special rate limit handling needed
                    if (isTimeout) {
                        System.out.println("LLMServiceWithFallback: Krista LLM TIMEOUT on attempt " + attempt + " of " + KRISTA_MAX_ATTEMPTS +
                            " (timeout: " + KRISTA_TIMEOUT_SECONDS + "s): " + errorMessage);
                    } else {
                        System.out.println("LLMServiceWithFallback: Krista LLM error on attempt " + attempt + " of " + KRISTA_MAX_ATTEMPTS + ": " + errorMessage);
                    }

                    if (attempt < KRISTA_MAX_ATTEMPTS) {
                        System.out.println("LLMServiceWithFallback: Retrying Krista LLM in " + RETRY_DELAY_MS + "ms...");
                        briefPause();
                    } else {
                        System.out.println("LLMServiceWithFallback: All Krista LLM retry attempts failed");
                    }
                }
            }

        } catch (Exception e) {
            System.out.println("LLMServiceWithFallback: Failed to create Krista LLM client: " + e.getMessage());
        }

        return null;
    }

    /**
     * Try cloud LLM with retry logic (3 attempts)
     */
    private String tryCloudLLM(String systemPrompt, String userPrompt) {
        try {
            String cloudProvider = llmConfiguration.getCloudProvider();

            // Check if provider is currently rate limited
            RateLimitTracker rateLimitTracker = RateLimitTracker.getInstance();
            if (rateLimitTracker.isRateLimited(cloudProvider)) {
                long remainingSeconds = rateLimitTracker.getRemainingRateLimitSeconds(cloudProvider);
                System.out.println("LLMServiceWithFallback: " + cloudProvider + " is rate limited for " + remainingSeconds + " more seconds - skipping attempt");
                return null;
            }

            System.out.println("LLMServiceWithFallback: Attempting cloud LLM with " + CLOUD_TIMEOUT_SECONDS + "s timeout");
            LLMClient cloudClient = llmClientFactory.createCloudClient(cloudProvider, CLOUD_TIMEOUT_SECONDS);
            System.out.println("LLMServiceWithFallback: Created cloud client: " + cloudClient.getClass().getSimpleName());

            for (int attempt = 1; attempt <= CLOUD_MAX_RETRY_ATTEMPTS; attempt++) {
                try {
                    System.out.println("LLMServiceWithFallback: Cloud LLM attempt " + attempt + " of " + CLOUD_MAX_RETRY_ATTEMPTS);

                    String response = cloudClient.query(systemPrompt, userPrompt);

                    if (response == null || response.trim().isEmpty()) {
                        System.out.println("LLMServiceWithFallback: Cloud LLM returned empty response on attempt " + attempt);
                        if (attempt < CLOUD_MAX_RETRY_ATTEMPTS) {
                            System.out.println("LLMServiceWithFallback: Retrying cloud LLM in " + RETRY_DELAY_MS + "ms...");
                            briefPause();
                            continue;
                        }
                        return null;
                    }

                    System.out.println("LLMServiceWithFallback: Cloud LLM response length: " + response.length() + " characters");
                    System.out.println("LLMServiceWithFallback: Cloud LLM response content:");
                    System.out.println(beautifyJson(response));
                    return response;

                } catch (Exception e) {
                    String errorMessage = e.getMessage();
                    boolean isTimeout = errorMessage != null && (errorMessage.toLowerCase().contains("timeout") ||
                                       errorMessage.toLowerCase().contains("sockettimeout"));
                    boolean isRateLimit = e.getCause() instanceof RateLimitException;

                    if (isRateLimit) {
                        RateLimitException rateLimitEx = (RateLimitException) e.getCause();
                        long retryAfterSeconds = rateLimitEx.getRetryAfterSeconds();

                        // Mark provider as rate limited to avoid future API calls (use existing cloudProvider variable)
                        RateLimitTracker.getInstance().markRateLimited(cloudProvider, retryAfterSeconds);

                        System.out.println("LLMServiceWithFallback: Cloud LLM (" + cloudProvider + ") rate limited - returning error immediately (retry after " + retryAfterSeconds + " seconds): " + errorMessage);

                        // Don't retry on rate limiting - return immediately to user
                        return null;
                    } else if (isTimeout) {
                        System.out.println("LLMServiceWithFallback: Cloud LLM TIMEOUT on attempt " + attempt + " of " + CLOUD_MAX_RETRY_ATTEMPTS +
                            " (timeout: " + CLOUD_TIMEOUT_SECONDS + "s): " + errorMessage);

                        if (attempt < CLOUD_MAX_RETRY_ATTEMPTS) {
                            System.out.println("LLMServiceWithFallback: Retrying cloud LLM in " + RETRY_DELAY_MS + "ms...");
                            briefPause();
                        } else {
                            System.out.println("LLMServiceWithFallback: All cloud LLM retry attempts failed");
                            System.out.println("LLMServiceWithFallback: Final cloud LLM error: " + Arrays.toString(e.getStackTrace()));
                        }
                    } else {
                        System.out.println("LLMServiceWithFallback: Cloud LLM error on attempt " + attempt + " of " + CLOUD_MAX_RETRY_ATTEMPTS + ": " + errorMessage);

                        if (attempt < CLOUD_MAX_RETRY_ATTEMPTS) {
                            System.out.println("LLMServiceWithFallback: Retrying cloud LLM in " + RETRY_DELAY_MS + "ms...");
                            briefPause();
                        } else {
                            System.out.println("LLMServiceWithFallback: All cloud LLM retry attempts failed");
                            System.out.println("LLMServiceWithFallback: Final cloud LLM error: " + Arrays.toString(e.getStackTrace()));
                        }
                    }
                }
            }

        } catch (Exception e) {
            System.out.println("LLMServiceWithFallback: Failed to create cloud LLM client: " + e.getMessage());
        }

        return null;
    }

    /**
     * Query LLM and return response as Map.
     *
     * @param systemPrompt system instructions
     * @param userPrompt user query
     * @return response parsed as Map
     */
    private Map<String, Object> queryAsMap(String systemPrompt, String userPrompt) {
        String response = queryWithFallback(systemPrompt, userPrompt);
        return convertStringToMap(response);
    }

    /**
     * Beautify JSON string for logging purposes.
     * If the input is not valid JSON, returns the original string.
     *
     * @param jsonString the JSON string to beautify
     * @return beautified JSON string or original string if not valid JSON
     */
    private String beautifyJson(String jsonString) {
        if (jsonString == null || jsonString.trim().isEmpty()) {
            return jsonString;
        }

        try {
            // Parse and reformat with pretty printing
            ObjectMapper prettyMapper = new ObjectMapper();
            prettyMapper.enable(SerializationFeature.INDENT_OUTPUT);
            Object jsonObject = prettyMapper.readValue(jsonString, Object.class);
            return prettyMapper.writeValueAsString(jsonObject);
        } catch (Exception e) {
            // If it's not valid JSON, return the original string
            return jsonString;
        }
    }

    /**
     * Pause briefly between retry attempts
     */
    private static void briefPause() {
        try {
            Thread.sleep(RETRY_DELAY_MS);
        } catch (InterruptedException ignore) {
            Thread.currentThread().interrupt(); // Restore interrupted status
        }
    }

    private Map<String, Object> convertStringToMap(String jsonString) {
        try {
            // Preprocess the string to handle JSON wrapped in markdown code blocks
            String cleanedJson = preprocessJsonString(jsonString);
            System.out.println("LLMServiceWithFallback: Original JSON string length: " + (jsonString != null ? jsonString.length() : "null"));
            System.out.println("LLMServiceWithFallback: Cleaned JSON string length: " + (cleanedJson != null ? cleanedJson.length() : "null"));
            System.out.println("LLMServiceWithFallback: Cleaned JSON content: " + cleanedJson);
            return objectMapper.readValue(cleanedJson, new TypeReference<>() {
            });
        } catch (IOException e) {
            System.out.println("LLMServiceWithFallback: JSON parsing failed. Original: " + jsonString);
            throw new RuntimeException("Failed to parse LLM response JSON: " + jsonString, e);
        }
    }

    /**
     * Preprocesses JSON string to handle cases where LLM wraps JSON in markdown code blocks.
     * Uses regex pattern matching to reliably extract JSON content from code blocks.
     * Handles formats like:
     * ```json
     * {"key": "value"}
     * ```
     *
     * @param jsonString the raw JSON string that might be wrapped in code blocks
     * @return cleaned JSON string ready for parsing
     */
    private String preprocessJsonString(String jsonString) {
        if (jsonString == null || jsonString.trim().isEmpty()) {
            return jsonString;
        }

        String input = jsonString.trim();

        // Regex pattern to match markdown code blocks with optional language specifier
        // Pattern explanation:
        // ```(?:json|JSON)?\s*\n? - matches ``` optionally followed by json/JSON, optional whitespace, optional newline
        // (.*?) - non-greedy capture group for the content
        // \n?\s*``` - optional newline, optional whitespace, closing ```
        Pattern codeBlockPattern = Pattern.compile(
            "```(?:json|JSON)?\\s*\\r?\\n?(.*?)\\r?\\n?\\s*```",
            Pattern.DOTALL
        );

        Matcher matcher = codeBlockPattern.matcher(input);

        if (matcher.find()) {
            String extractedContent = matcher.group(1).trim();
            System.out.println("LLMServiceWithFallback: Detected JSON wrapped in code blocks, extracted content");
            return extractedContent;
        }

        // No code block markers found, return original string
        return jsonString;
    }

    /**
     * Result class for entity extraction containing variable mapping and next step flag.
     */
    public static class EntityExtractionResult {
        private final Map<String, Object> variableMapping;
        private final boolean moveToNextStep;

        public EntityExtractionResult(Map<String, Object> variableMapping, boolean moveToNextStep) {
            this.variableMapping = variableMapping;
            this.moveToNextStep = moveToNextStep;
        }

        public Map<String, Object> getVariableMapping() {
            return variableMapping;
        }

        public boolean shouldMoveToNextStep() {
            return moveToNextStep;
        }
    }
}
