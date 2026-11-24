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
 * Exception thrown when input validation fails due to guardrail violations.
 * This exception is thrown when user input contains potentially malicious content,
 * prompt injection attempts, or violates other security policies.
 *
 * <p>Always carries {@link AgenticErrorCode#GUARDRAIL_BLOCKED}.
 */
public class GuardrailViolationException extends AgenticFlowException {

    public GuardrailViolationException(String message) {

        super(AgenticErrorCode.GUARDRAIL_BLOCKED, message);
    }

    public GuardrailViolationException(String message, Throwable cause) {

        super(AgenticErrorCode.GUARDRAIL_BLOCKED, message, cause);
    }
}
