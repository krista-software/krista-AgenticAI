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

package app.krista.extensions.hook.agentic.dto;

/**
 * Categorizes system-generated errors so that the converter can map each
 * failure to an appropriate user-facing status message without relying on
 * fragile string matching.
 */
public enum AgenticErrorCode {

    JSON_PARSE_FAILED,
    TOOL_VALIDATION_FAILED,
    ARGUMENT_VALIDATION_FAILED,
    OUTPUT_VALIDATION_FAILED,
    MALFORMED_RESPONSE,
    SESSION_NOT_FOUND,
    PARENT_SESSION_NOT_FOUND,
    PARENT_SESSION_TERMINATED,
    PERSISTENCE_FAILED,
    GUARDRAIL_BLOCKED,
    EXECUTION_LIMIT_ITERATION,
    CONTEXT_LENGTH_EXCEEDED,
    UNEXPECTED_ERROR
}
