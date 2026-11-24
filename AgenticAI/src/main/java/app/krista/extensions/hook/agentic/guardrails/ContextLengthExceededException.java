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

package app.krista.extensions.hook.agentic.guardrails;

import app.krista.extensions.hook.agentic.dto.AgenticErrorCode;

/**
 * Exception thrown when an LLM provider rejects a request because the input exceeds
 * the model's context window (token limit).
 *
 * <p>This exception is thrown by {@link app.krista.extensions.hook.agentic.usagecollector.RateLimitRetryHandler}
 * when it detects a context-length error in the LLM API response. It is <strong>non-retryable</strong>:
 * submitting the same prompt again will always fail for the same reason. The caller must reduce
 * the amount of input data (e.g., fewer input variables, smaller entity lists) before retrying.
 *
 * <p>The optional {@code modelName} field carries the agent/model identifier available at the
 * detection layer, useful for diagnostic logging without adding noise to the user-facing message.
 *
 * <p>Always carries {@link AgenticErrorCode#CONTEXT_LENGTH_EXCEEDED}.
 */
public class ContextLengthExceededException extends AgenticFlowException {

    private final String modelName;

    /**
     * Constructs a {@code ContextLengthExceededException} with the given message.
     * The model name will be {@code null}.
     *
     * @param message a description of the context-length failure
     */
    public ContextLengthExceededException(String message) {

        super(AgenticErrorCode.CONTEXT_LENGTH_EXCEEDED, message);
        this.modelName = null;
    }

    /**
     * Constructs a {@code ContextLengthExceededException} with the given message and cause.
     * The model name will be {@code null}.
     *
     * @param message a description of the context-length failure
     * @param cause   the underlying exception from the LLM provider
     */
    public ContextLengthExceededException(String message, Throwable cause) {

        super(AgenticErrorCode.CONTEXT_LENGTH_EXCEEDED, message, cause);
        this.modelName = null;
    }

    /**
     * Constructs a {@code ContextLengthExceededException} with the given message, cause,
     * and model/agent name.
     *
     * @param message   a description of the context-length failure
     * @param cause     the underlying exception from the LLM provider
     * @param modelName the name of the agent or model that reported the error (for diagnostics)
     */
    public ContextLengthExceededException(String message, Throwable cause, String modelName) {

        super(AgenticErrorCode.CONTEXT_LENGTH_EXCEEDED, message, cause);
        this.modelName = modelName;
    }

    /**
     * Returns the name of the agent or model that reported the context-length error,
     * or {@code null} if not available.
     *
     * @return the model/agent name, possibly {@code null}
     */
    public String getModelName() {

        return modelName;
    }
}
