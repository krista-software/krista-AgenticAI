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
 * Exception thrown when an execution limit is exceeded during agentic flow processing.
 * This is a non-retryable exception (similar to GuardrailViolationException).
 *
 * Execution limits include:
 * - Cost limit: Maximum cost per session (e.g., $5.00)
 * - Time limit: Maximum session duration (e.g., 5 minutes)
 * - Iteration limit: Maximum number of LLM calls per session (e.g., 10)
 *
 * This exception is thrown BEFORE making an LLM call to prevent unnecessary API costs.
 *
 * <p>Always carries {@link AgenticErrorCode#EXECUTION_LIMIT_ITERATION}.
 */
public class ExecutionLimitExceededException extends AgenticFlowException {

    private final LimitDetails details;

    /**
     * Create an execution limit exceeded exception with detailed context.
     *
     * @param details Details about which limit was exceeded (type, current value, max value, session ID)
     */
    public ExecutionLimitExceededException(LimitDetails details) {

        super(AgenticErrorCode.EXECUTION_LIMIT_ITERATION, formatMessage(details));
        this.details = details;
    }

    /**
     * Get the limit details for this exception.
     *
     * @return LimitDetails containing information about the exceeded limit
     */
    public LimitDetails getDetails() {

        return details;
    }

    /**
     * Format a user-friendly error message from limit details.
     *
     * @param details The limit details to format
     * @return Formatted error message
     */
    private static String formatMessage(LimitDetails details) {

        return String.format(
            "Execution limit exceeded for session %s: %s limit reached (current: %s, max: %s)",
            details.sessionId(),
            details.limitType(),
            formatValue(details.currentValue(), details.limitType()),
            formatValue(details.maxValue(), details.limitType())
        );
    }

    /**
     * Format a value based on the limit type.
     * - Cost: Format as currency (e.g., "$5.00")
     * - Time: Format as minutes (e.g., "5.0 minutes")
     * - Iteration: Format as integer count (e.g., "10 iterations")
     *
     * @param value The value to format
     * @param limitType The type of limit ("cost", "time", "iteration")
     * @return Formatted value string
     */
    private static String formatValue(double value, String limitType) {

        return switch (limitType) {

            case "cost" -> String.format("$%.2f", value);
            case "time" -> String.format("%.1f minutes", value);
            case "iteration" -> String.format("%d iterations", (int) value);
            default -> String.valueOf(value);
        };
    }
}
