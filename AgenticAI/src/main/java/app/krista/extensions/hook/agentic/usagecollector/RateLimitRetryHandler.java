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

package app.krista.extensions.hook.agentic.usagecollector;

import app.krista.extensions.hook.agentic.guardrails.ContextLengthExceededException;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Handles retry logic with exponential backoff for rate-limited LLM API calls.
 *
 * This class implements an exponential backoff strategy when rate limits are encountered,
 * automatically retrying failed requests with increasing delays between attempts.
 */
public class RateLimitRetryHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(RateLimitRetryHandler.class);

    // Rate limiting configuration
    private static final int MAX_RETRIES = 3;
    private static final long INITIAL_BACKOFF_MS = 1000; // 1 second
    private static final double BACKOFF_MULTIPLIER = 2.0; // Exponential backoff

    /**
     * Execute an LLM call with retry logic and exponential backoff.
     *
     * @param delegate The ChatLanguageModel to call
     * @param messages The messages to send to the LLM
     * @param agentName The name of the agent (for logging)
     * @return The LLM response
     * @throws RuntimeException if all retries are exhausted or a non-rate-limit error occurs
     */
    public static Response<dev.langchain4j.data.message.AiMessage> executeWithRetry(
            ChatLanguageModel delegate,
            List<ChatMessage> messages,
            String agentName) {

        int attempt = 0;
        long backoffMs = INITIAL_BACKOFF_MS;

        while (attempt <= MAX_RETRIES) {
            try {
                if (attempt > 0) {
                    LOGGER.info("LLM call attempt {}/{} for {}", attempt + 1, MAX_RETRIES + 1, agentName);
                }

                // Call the actual LLM
                Response<dev.langchain4j.data.message.AiMessage> response = delegate.generate(messages);

                if (attempt > 0) {
                    LOGGER.info("LLM call succeeded after {} attempts for {}", attempt + 1, agentName);
                }

                return response;

            } catch (RuntimeException e) {
                // Check if this is a rate limit exception
                boolean isRateLimit = isRateLimitException(e);

                if (isRateLimit && attempt < MAX_RETRIES) {
                    // Extract retry-after time from exception if available
                    long retryAfterMs = extractRetryAfterTime(e, backoffMs);

                    LOGGER.warn("Rate limit hit on attempt {} for {} - retrying after {}ms. Error: {}",
                        attempt + 1, agentName, retryAfterMs, e.getMessage());

                    // Sleep for the backoff period
                    try {
                        Thread.sleep(retryAfterMs);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        LOGGER.error("Retry interrupted for {}", agentName);
                        throw new RuntimeException("LLM call interrupted during retry", ie);
                    }

                    // Exponential backoff for next attempt
                    backoffMs = (long) (backoffMs * BACKOFF_MULTIPLIER);
                    attempt++;

                } else {
                    // Not a rate limit error, or max retries exceeded
                    if (isRateLimit) {
                        LOGGER.error("Rate limit exceeded after {} attempts for {}", MAX_RETRIES + 1, agentName);
                        throw new RuntimeException("Rate limit exceeded after " + (MAX_RETRIES + 1) +
                            " attempts: " + e.getMessage(), e);
                    } else if (isContextLengthException(e)) {
                        // Context-length errors are non-retryable — the same prompt will always fail
                        LOGGER.warn("Context length exceeded for {}: {}", agentName, e.getMessage());
                        throw new ContextLengthExceededException(
                            "The input data exceeds the AI model's processing capacity. " +
                            "Try reducing the number of records or input variables.",
                            e,
                            agentName);
                    } else {
                        // Different error, re-throw immediately
                        throw e;
                    }
                }
            }
        }

        // Should never reach here, but just in case
        throw new RuntimeException("LLM call failed after " + (MAX_RETRIES + 1) + " attempts");
    }

    /**
     * Check if the exception is a rate limit exception.
     * Looks for rate limit indicators in the exception message and cause chain.
     */
    private static boolean isRateLimitException(RuntimeException e) {
        // Check exception message for rate limit indicators
        String message = e.getMessage();
        if (message != null) {
            String lowerMessage = message.toLowerCase();
            if (lowerMessage.contains("rate limit") ||
                lowerMessage.contains("429") ||
                lowerMessage.contains("too many requests") ||
                lowerMessage.contains("quota exceeded")) {
                return true;
            }
        }

        // Check cause chain for RateLimitException
        Throwable cause = e.getCause();
        while (cause != null) {
            if (cause.getClass().getSimpleName().equals("RateLimitException")) {
                return true;
            }
            String causeMessage = cause.getMessage();
            if (causeMessage != null) {
                String lowerCauseMessage = causeMessage.toLowerCase();
                if (lowerCauseMessage.contains("rate limit") ||
                    lowerCauseMessage.contains("429") ||
                    lowerCauseMessage.contains("too many requests")) {
                    return true;
                }
            }
            cause = cause.getCause();
        }

        return false;
    }

    /**
     * Check if the exception is a context-length / token-limit exception.
     *
     * <p>Checks the exception message and the full cause chain for patterns known to indicate
     * that the prompt exceeded the model's context window. The check is case-insensitive and
     * covers all major LLM providers:
     * <ul>
     *   <li>OpenAI / Azure OpenAI: {@code "context_length_exceeded"}, {@code "maximum context length"}</li>
     *   <li>Anthropic: {@code "prompt is too long"}, {@code "too many tokens"}</li>
     *   <li>Google Gemini: {@code "exceeds the maximum number of tokens"}, {@code "token limit"}</li>
     *   <li>Generic / fallback: {@code "request too large"}, {@code "content_length_limit"}</li>
     * </ul>
     *
     * <p>To add support for a new provider, add its error indicator string to the check below.
     *
     * @param e the exception thrown by the LLM delegate
     * @return {@code true} if the exception signals a context-length / token-limit error
     */
    private static boolean isContextLengthException(RuntimeException e) {

        if (containsContextLengthPattern(e.getMessage())) {
            return true;
        }

        Throwable cause = e.getCause();
        while (cause != null) {
            if (containsContextLengthPattern(cause.getMessage())) {
                return true;
            }
            cause = cause.getCause();
        }

        return false;
    }

    /**
     * Returns {@code true} if the message contains any of the known context-length
     * error indicators (case-insensitive match).
     *
     * @param message the message text to inspect, may be {@code null}
     * @return {@code true} if a pattern is found, {@code false} otherwise
     */
    private static boolean containsContextLengthPattern(String message) {

        if (message == null) {
            return false;
        }

        String lower = message.toLowerCase();
        return lower.contains("context_length_exceeded")
            || lower.contains("maximum context length")
            || lower.contains("prompt is too long")
            || lower.contains("too many tokens")
            || lower.contains("token limit")
            || lower.contains("exceeds the maximum number of tokens")
            || lower.contains("request too large")
            || lower.contains("content_length_limit");
    }

    /**
     * Extract retry-after time from the exception if available.
     * Falls back to the provided default backoff time.
     */
    private static long extractRetryAfterTime(RuntimeException e, long defaultBackoffMs) {
        // Try to extract retry-after from exception message
        String message = e.getMessage();
        if (message != null && message.contains("retry after")) {
            try {
                // Look for patterns like "retry after 60 seconds"
                String[] parts = message.split("retry after");
                if (parts.length > 1) {
                    String timePart = parts[1].trim().split(" ")[0];
                    long seconds = Long.parseLong(timePart);
                    LOGGER.debug("Using retry-after from response: {} seconds", seconds);
                    return seconds * 1000; // Convert to milliseconds
                }
            } catch (Exception parseError) {
                // Ignore parsing errors, use default
            }
        }

        // Check if cause is RateLimitException and extract retry time
        Throwable cause = e.getCause();
        while (cause != null) {
            if (cause.getClass().getSimpleName().equals("RateLimitException")) {
                try {
                    // Use reflection to get retryAfterSeconds
                    java.lang.reflect.Method method = cause.getClass().getMethod("getRetryAfterSeconds");
                    long seconds = (Long) method.invoke(cause);
                    LOGGER.debug("Using retry-after from RateLimitException: {} seconds", seconds);
                    return seconds * 1000; // Convert to milliseconds
                } catch (Exception reflectionError) {
                    // Ignore reflection errors, use default
                }
            }
            cause = cause.getCause();
        }

        // Fall back to exponential backoff
        LOGGER.debug("Using exponential backoff: {}ms", defaultBackoffMs);
        return defaultBackoffMs;
    }
}
