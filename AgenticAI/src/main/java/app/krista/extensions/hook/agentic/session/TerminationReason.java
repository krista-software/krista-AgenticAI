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

package app.krista.extensions.hook.agentic.session;

/**
 * Reason an agentic session reached a terminal state. Carried in the structured
 * session_summary so automation authors can distinguish a clean COMPLETE from
 * limit-breaches, guardrail violations, persistence failures, and generic errors.
 */
public enum TerminationReason {

    COMPLETE,
    ERROR,
    ITERATION_LIMIT,
    COST_LIMIT,
    TIME_LIMIT,
    GUARDRAIL_VIOLATION,
    PERSISTENCE_ERROR,
    UNKNOWN;

    /**
     * Maps a {@code LimitDetails.limitType()} string ("iteration" / "cost" / "time")
     * to the corresponding enum value. Falls back to {@link #ERROR} for unknown inputs.
     */
    public static TerminationReason fromLimitType(String limitType) {

        if (limitType == null) {
            return ERROR;
        }
        return switch (limitType.toLowerCase()) {
            case "iteration" -> ITERATION_LIMIT;
            case "cost" -> COST_LIMIT;
            case "time" -> TIME_LIMIT;
            default -> ERROR;
        };
    }
}
