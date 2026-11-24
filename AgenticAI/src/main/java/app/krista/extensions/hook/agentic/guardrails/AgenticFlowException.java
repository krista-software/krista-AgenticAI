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
 * Base exception for all agentic flow errors. Carries a structured
 * {@link AgenticErrorCode} so that the {@code OrchestrationHandler}
 * can catch a single type and route the error code to the converter
 * for user-facing status message resolution.
 *
 * <p>Subclasses hardcode their error code (e.g., {@link GuardrailViolationException}
 * always uses {@link AgenticErrorCode#GUARDRAIL_BLOCKED}). Direct instances are
 * used for hard-stop errors (session not found, persistence failed) and retry
 * exhaustion errors (JSON parse, malformed response, tool/argument validation).
 */
public class AgenticFlowException extends RuntimeException {

    private final AgenticErrorCode errorCode;

    /**
     * Creates an {@code AgenticFlowException} with the given error code and message.
     *
     * @param errorCode the structured error code for programmatic routing
     * @param message   a description of the error (typically includes diagnostic context)
     */
    public AgenticFlowException(AgenticErrorCode errorCode, String message) {

        super(message);
        this.errorCode = errorCode;
    }

    /**
     * Creates an {@code AgenticFlowException} with the given error code, message, and cause.
     *
     * @param errorCode the structured error code for programmatic routing
     * @param message   a description of the error
     * @param cause     the underlying exception
     */
    public AgenticFlowException(AgenticErrorCode errorCode, String message, Throwable cause) {

        super(message, cause);
        this.errorCode = errorCode;
    }

    /**
     * Returns the structured error code for this exception.
     *
     * @return the error code, never {@code null}
     */
    public AgenticErrorCode getErrorCode() {

        return errorCode;
    }
}
