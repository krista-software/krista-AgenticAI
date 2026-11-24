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
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.anthropic.AnthropicChatModel;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.output.Response;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for context-length error detection and exception propagation in
 * {@link RateLimitRetryHandler}.
 *
 * <p>The detection methods ({@code isContextLengthException}, {@code containsContextLengthPattern})
 * are private, so all cases are exercised through the public
 * {@link RateLimitRetryHandler#executeWithRetry} API. A stub {@link ChatLanguageModel}
 * lambda is used in place of a real LLM — this is intentional and appropriate here
 * because we are testing our own classification logic, not the LLM provider itself.
 * Making a real LLM call that exceeds the context window would require constructing
 * a 200K+ token prompt, which is expensive, slow, and provider-dependent.
 */
@DisplayName("RateLimitRetryHandler — context-length error detection")
class RateLimitRetryHandlerContextLengthTest {

    private static final String AGENT_NAME = "test-agent";

    // ---------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------

    /**
     * Returns a stub {@link ChatLanguageModel} that always throws a
     * {@link RuntimeException} carrying the given error message.
     *
     * <p>The stub is a lambda — {@link ChatLanguageModel} is a functional interface
     * so no Mockito overhead is needed for this simple "always fail" contract.
     */
    private static ChatLanguageModel delegateThrowing(String errorMessage) {

        return messages -> {
            throw new RuntimeException(errorMessage);
        };
    }

    /**
     * Invokes {@link RateLimitRetryHandler#executeWithRetry} with a delegate that
     * throws immediately and returns the caught exception, or {@code null} if no
     * exception was thrown.
     */
    private static RuntimeException captureException(ChatLanguageModel delegate) {

        try {
            RateLimitRetryHandler.executeWithRetry(delegate, List.of(), AGENT_NAME);
            return null;
        } catch (RuntimeException ex) {
            return ex;
        }
    }

    // ---------------------------------------------------------------------------
    // Positive cases — known provider messages MUST produce ContextLengthExceededException
    // ---------------------------------------------------------------------------

    @Test
    @DisplayName("Should detect OpenAI context-length error and throw ContextLengthExceededException")
    void openAiContextLengthError_throwsContextLengthExceededException() {

        // Given: the exact error message returned by OpenAI when the prompt is too long
        String openAiError =
            "This model's maximum context length is 128000 tokens. " +
            "However, your messages resulted in 215847 tokens. " +
            "Please reduce the length of the messages.";
        ChatLanguageModel delegate = delegateThrowing(openAiError);

        // When
        RuntimeException thrown = captureException(delegate);

        // Then
        assertNotNull(thrown, "executeWithRetry must throw when the delegate fails");
        assertInstanceOf(ContextLengthExceededException.class, thrown,
            "OpenAI 'maximum context length' error must be classified as ContextLengthExceededException, " +
            "but got: " + thrown.getClass().getSimpleName());
    }

    @Test
    @DisplayName("Should detect Anthropic context-length error and throw ContextLengthExceededException")
    void anthropicContextLengthError_throwsContextLengthExceededException() {

        // Given: the exact error message returned by Anthropic when the prompt is too long
        String anthropicError = "prompt is too long: 215847 tokens > 200000 maximum";
        ChatLanguageModel delegate = delegateThrowing(anthropicError);

        // When
        RuntimeException thrown = captureException(delegate);

        // Then
        assertNotNull(thrown, "executeWithRetry must throw when the delegate fails");
        assertInstanceOf(ContextLengthExceededException.class, thrown,
            "Anthropic 'prompt is too long' error must be classified as ContextLengthExceededException, " +
            "but got: " + thrown.getClass().getSimpleName());
    }

    @Test
    @DisplayName("Should detect Azure OpenAI context-length error and throw ContextLengthExceededException")
    void azureContextLengthError_throwsContextLengthExceededException() {

        // Given: the exact error message returned by Azure OpenAI when the prompt is too long
        String azureError = "The request was too large. context_length_exceeded";
        ChatLanguageModel delegate = delegateThrowing(azureError);

        // When
        RuntimeException thrown = captureException(delegate);

        // Then
        assertNotNull(thrown, "executeWithRetry must throw when the delegate fails");
        assertInstanceOf(ContextLengthExceededException.class, thrown,
            "Azure 'context_length_exceeded' error must be classified as ContextLengthExceededException, " +
            "but got: " + thrown.getClass().getSimpleName());
    }

    @Test
    @DisplayName("Should detect generic token-limit error and throw ContextLengthExceededException")
    void genericTokenLimitError_throwsContextLengthExceededException() {

        // Given: a generic token-limit message (e.g., from an internal proxy or fallback provider)
        String genericError = "token limit exceeded";
        ChatLanguageModel delegate = delegateThrowing(genericError);

        // When
        RuntimeException thrown = captureException(delegate);

        // Then
        assertNotNull(thrown, "executeWithRetry must throw when the delegate fails");
        assertInstanceOf(ContextLengthExceededException.class, thrown,
            "Generic 'token limit exceeded' error must be classified as ContextLengthExceededException, " +
            "but got: " + thrown.getClass().getSimpleName());
    }

    @ParameterizedTest(name = "[{index}] pattern: \"{0}\"")
    @DisplayName("Should detect all documented provider context-length patterns")
    @ValueSource(strings = {
        "context_length_exceeded",
        "maximum context length",
        "prompt is too long",
        "too many tokens",
        "token limit",
        "exceeds the maximum number of tokens",
        "request too large",
        "content_length_limit"
    })
    void allDocumentedPatterns_throwContextLengthExceededException(String pattern) {

        // Given: a RuntimeException whose message contains one of the documented patterns
        ChatLanguageModel delegate = delegateThrowing("LLM API error: " + pattern + " for this request");

        // When
        RuntimeException thrown = captureException(delegate);

        // Then
        assertNotNull(thrown, "executeWithRetry must throw when the delegate fails");
        assertInstanceOf(ContextLengthExceededException.class, thrown,
            "Pattern '" + pattern + "' must trigger ContextLengthExceededException, " +
            "but got: " + thrown.getClass().getSimpleName());
    }

    @Test
    @DisplayName("Should detect context-length pattern in exception cause chain, not just top-level message")
    void contextLengthPatternInCause_throwsContextLengthExceededException() {

        // Given: the context-length indicator is nested inside the cause chain
        // (this mirrors how langchain4j sometimes wraps provider errors)
        RuntimeException root = new RuntimeException("prompt is too long: 210000 tokens > 200000 maximum");
        RuntimeException wrapper = new RuntimeException("LLM call failed", root);

        ChatLanguageModel delegate = messages -> {
            throw wrapper;
        };

        // When
        RuntimeException thrown = captureException(delegate);

        // Then
        assertNotNull(thrown, "executeWithRetry must throw when the delegate fails");
        assertInstanceOf(ContextLengthExceededException.class, thrown,
            "Context-length pattern in the cause chain must still trigger ContextLengthExceededException, " +
            "but got: " + thrown.getClass().getSimpleName());
    }

    @Test
    @DisplayName("Detection is case-insensitive — mixed-case provider messages are matched")
    void caseInsensitiveDetection_mixedCaseMessage_throwsContextLengthExceededException() {

        // Given: a message where the pattern appears in mixed case
        // (some providers capitalise differently across API versions)
        String mixedCaseError = "Maximum Context Length exceeded for this model configuration";
        ChatLanguageModel delegate = delegateThrowing(mixedCaseError);

        // When
        RuntimeException thrown = captureException(delegate);

        // Then
        assertNotNull(thrown, "executeWithRetry must throw when the delegate fails");
        assertInstanceOf(ContextLengthExceededException.class, thrown,
            "Case-insensitive match must classify ContextLengthExceededException, " +
            "but got: " + thrown.getClass().getSimpleName());
    }

    // ---------------------------------------------------------------------------
    // Negative cases — unrelated errors must NOT produce ContextLengthExceededException
    // ---------------------------------------------------------------------------

    @Test
    @DisplayName("Should NOT classify auth error as context-length — rethrows original exception")
    void authError_rethrowsOriginalException() {

        // Given: an authentication failure (no context-length indicator)
        String authError = "Invalid API key provided";
        ChatLanguageModel delegate = delegateThrowing(authError);

        // When
        RuntimeException thrown = captureException(delegate);

        // Then
        assertNotNull(thrown, "executeWithRetry must rethrow when the delegate fails");
        assertFalse(thrown instanceof ContextLengthExceededException,
            "Auth error must NOT be classified as ContextLengthExceededException");
        assertEquals(authError, thrown.getMessage(),
            "Original exception must be rethrown unchanged for auth errors");
    }

    @Test
    @DisplayName("Should NOT classify server error as context-length — rethrows original exception")
    void serverError_rethrowsOriginalException() {

        // Given: a generic server-side failure
        String serverError = "Internal server error";
        ChatLanguageModel delegate = delegateThrowing(serverError);

        // When
        RuntimeException thrown = captureException(delegate);

        // Then
        assertNotNull(thrown, "executeWithRetry must rethrow when the delegate fails");
        assertFalse(thrown instanceof ContextLengthExceededException,
            "Server error must NOT be classified as ContextLengthExceededException");
        assertEquals(serverError, thrown.getMessage(),
            "Original exception must be rethrown unchanged for server errors");
    }

    @Test
    @DisplayName("Should NOT classify bad-request error as context-length — rethrows original exception")
    void badRequestError_rethrowsOriginalException() {

        // Given: a malformed-request error with no token/context indicator
        String badRequestError = "Bad request";
        ChatLanguageModel delegate = delegateThrowing(badRequestError);

        // When
        RuntimeException thrown = captureException(delegate);

        // Then
        assertNotNull(thrown, "executeWithRetry must rethrow when the delegate fails");
        assertFalse(thrown instanceof ContextLengthExceededException,
            "Generic bad-request error must NOT be classified as ContextLengthExceededException");
        assertEquals(badRequestError, thrown.getMessage(),
            "Original exception must be rethrown unchanged for bad-request errors");
    }

    @ParameterizedTest(name = "[{index}] error: \"{0}\"")
    @DisplayName("Should NOT classify common non-context-length errors as context-length")
    @ValueSource(strings = {
        "Invalid API key provided",
        "Internal server error",
        "Bad request",
        "Service unavailable",
        "Connection timeout",
        "Unauthorized"
    })
    void nonContextLengthErrors_doNotThrowContextLengthExceededException(String errorMessage) {

        // Given: a non-context-length provider error
        ChatLanguageModel delegate = delegateThrowing(errorMessage);

        // When
        RuntimeException thrown = captureException(delegate);

        // Then
        assertNotNull(thrown, "executeWithRetry must rethrow when the delegate fails");
        assertFalse(thrown instanceof ContextLengthExceededException,
            "Error '" + errorMessage + "' must NOT produce ContextLengthExceededException");
    }

    // ---------------------------------------------------------------------------
    // Integration — exception carries the right state
    // ---------------------------------------------------------------------------

    @Test
    @DisplayName("ContextLengthExceededException carries the agent name for diagnostics")
    void contextLengthException_carriesAgentName() {

        // Given
        ChatLanguageModel delegate = delegateThrowing("context_length_exceeded");

        // When
        RuntimeException thrown = captureException(delegate);

        // Then
        assertInstanceOf(ContextLengthExceededException.class, thrown);
        ContextLengthExceededException cle = (ContextLengthExceededException) thrown;

        assertEquals(AGENT_NAME, cle.getModelName(),
            "ContextLengthExceededException must carry the agentName passed to executeWithRetry " +
            "so that operators can identify which agent hit the limit in diagnostic logs");
    }

    @Test
    @DisplayName("ContextLengthExceededException wraps the original LLM exception as its cause")
    void contextLengthException_preservesOriginalCause() {

        // Given
        String providerMessage = "prompt is too long: 215847 tokens > 200000 maximum";
        ChatLanguageModel delegate = delegateThrowing(providerMessage);

        // When
        RuntimeException thrown = captureException(delegate);

        // Then
        assertInstanceOf(ContextLengthExceededException.class, thrown);
        assertNotNull(thrown.getCause(),
            "ContextLengthExceededException must preserve the original exception as its cause " +
            "so that stack traces remain complete for debugging");
        assertEquals(providerMessage, thrown.getCause().getMessage(),
            "The wrapped cause must carry the original provider error message");
    }

    @Test
    @DisplayName("ContextLengthExceededException message is actionable and not the raw provider error")
    void contextLengthException_hasActionableMessage() {

        // Given
        ChatLanguageModel delegate = delegateThrowing("context_length_exceeded");

        // When
        RuntimeException thrown = captureException(delegate);

        // Then
        assertInstanceOf(ContextLengthExceededException.class, thrown);
        String message = thrown.getMessage();

        assertNotNull(message, "ContextLengthExceededException must have a message");
        assertFalse(message.isBlank(), "ContextLengthExceededException message must not be blank");

        // The message should guide the user toward a fix, not expose raw provider internals
        String lowerMessage = message.toLowerCase();
        boolean mentionsInputOrData = lowerMessage.contains("input") || lowerMessage.contains("data")
            || lowerMessage.contains("records") || lowerMessage.contains("variables");
        assertTrue(mentionsInputOrData,
            "ContextLengthExceededException message should guide the user to reduce input data, " +
            "but was: '" + message + "'");
    }

    @Test
    @DisplayName("Non-context-length RuntimeException is rethrown without wrapping")
    void nonContextLengthRuntimeException_isRethrownAsIs() {

        // Given: a custom RuntimeException subclass that contains no context-length pattern
        class CustomProviderException extends RuntimeException {

            CustomProviderException(String message) {

                super(message);
            }
        }

        CustomProviderException original = new CustomProviderException("Service temporarily unavailable");
        ChatLanguageModel delegate = messages -> {
            throw original;
        };

        // When
        RuntimeException thrown = captureException(delegate);

        // Then
        assertNotNull(thrown);
        assertSame(original, thrown,
            "A non-context-length, non-rate-limit RuntimeException must be rethrown as-is " +
            "without any wrapping — executeWithRetry must not swallow or alter unrelated errors");
    }

    // ---------------------------------------------------------------------------
    // Real LLM provider tests — verify actual provider error messages are caught
    // ---------------------------------------------------------------------------

    @Nested
    @Tag("llm-integration")
    @DisplayName("Real LLM provider context-length errors")
    class RealProviderTests {

        /**
         * Generates a prompt string large enough to exceed the given token limit.
         * Rough estimate: 1 token ≈ 4 characters in English text.
         */
        private String generateOversizedPrompt(int targetTokens) {

            // "word " is 5 chars ≈ 1.25 tokens; overshoot to be safe
            return "word ".repeat(targetTokens);
        }

        @Test
        @DisplayName("OpenAI: oversized prompt triggers ContextLengthExceededException with real API error")
        void openAi_oversizedPrompt_throwsContextLengthExceededException() {

            String apiKey = System.getenv("OPENAI_API_KEY");
            Assumptions.assumeTrue(apiKey != null && !apiKey.isBlank(),
                "OPENAI_API_KEY not set — skipping real OpenAI context-length test");

            // gpt-4o-mini has 128K context — cheapest way to trigger context-length error
            ChatLanguageModel model = OpenAiChatModel.builder()
                .modelName("gpt-4o-mini")
                .apiKey(apiKey)
                .build();

            String oversizedPrompt = generateOversizedPrompt(200_000);
            List<ChatMessage> messages = List.of(UserMessage.from(oversizedPrompt));

            RuntimeException thrown = null;
            try {

                RateLimitRetryHandler.executeWithRetry(model, messages, "openai-context-test");
            } catch (RuntimeException e) {

                thrown = e;
            }

            assertNotNull(thrown, "Oversized prompt must cause an exception");
            assertInstanceOf(ContextLengthExceededException.class, thrown,
                "Real OpenAI context-length error must be classified as ContextLengthExceededException. " +
                "Actual exception: " + thrown.getClass().getSimpleName() + " — message: " + thrown.getMessage() +
                " — cause: " + (thrown.getCause() != null ? thrown.getCause().getMessage() : "null"));

            // Log the actual error for pattern verification
            System.out.println("[GROUND TRUTH] OpenAI error message: " + thrown.getCause().getMessage());
        }

        @Test
        @DisplayName("Anthropic: oversized prompt triggers ContextLengthExceededException with real API error")
        void anthropic_oversizedPrompt_throwsContextLengthExceededException() {

            String apiKey = System.getenv("ANTHROPIC_API_KEY");
            Assumptions.assumeTrue(apiKey != null && !apiKey.isBlank(),
                "ANTHROPIC_API_KEY not set — skipping real Anthropic context-length test");

            // Use claude-sonnet-4-5 (no timestamp — avoids expiry issues with dated model IDs)
            // 200K context — send ~250K tokens to guarantee exceeding it
            ChatLanguageModel model = AnthropicChatModel.builder()
                .modelName("claude-sonnet-4-5")
                .apiKey(apiKey)
                .maxTokens(1024)
                .build();

            String oversizedPrompt = generateOversizedPrompt(250_000);
            List<ChatMessage> messages = List.of(UserMessage.from(oversizedPrompt));

            RuntimeException thrown = null;
            try {

                RateLimitRetryHandler.executeWithRetry(model, messages, "anthropic-context-test");
            } catch (RuntimeException e) {

                thrown = e;
            }

            assertNotNull(thrown, "Oversized prompt must cause an exception");
            assertInstanceOf(ContextLengthExceededException.class, thrown,
                "Real Anthropic context-length error must be classified as ContextLengthExceededException. " +
                "Actual exception: " + thrown.getClass().getSimpleName() + " — message: " + thrown.getMessage() +
                " — cause: " + (thrown.getCause() != null ? thrown.getCause().getMessage() : "null"));

            // Log the actual error for pattern verification
            System.out.println("[GROUND TRUTH] Anthropic error message: " + thrown.getCause().getMessage());
        }
    }
}
