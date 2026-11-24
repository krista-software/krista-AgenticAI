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

/**
 * Determines behavior when the PolicyChecker itself fails (e.g., LLM timeout, parse error).
 * FAIL_OPEN: Allow tool execution if PolicyChecker errors — availability over strictness.
 * FAIL_CLOSED: Block tool execution if PolicyChecker errors — strictness over availability.
 */
public enum PolicyCheckerFailurePolicy {

    FAIL_OPEN,
    FAIL_CLOSED
}
