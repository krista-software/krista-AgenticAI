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
 * Record containing details about an execution limit violation.
 * Used by ExecutionLimitExceededException to provide context about which limit was exceeded.
 * 
 * This is a Java 21 record for immutable data transfer.
 */
public record LimitDetails(
    String limitType,      // Type of limit: "cost", "time", or "iteration"
    double currentValue,   // Current value (e.g., 6.23 for cost in dollars)
    double maxValue,       // Maximum allowed value (e.g., 5.0 for cost limit)
    String sessionId       // Session ID for debugging and logging
) {

    /**
     * Validate that limit details are properly formed.
     * 
     * @throws IllegalArgumentException if any field is invalid
     */
    public LimitDetails {

        if (limitType == null || limitType.trim().isEmpty()) {

            throw new IllegalArgumentException("limitType cannot be null or empty");
        }
        
        if (currentValue < 0) {

            throw new IllegalArgumentException("currentValue cannot be negative");
        }
        
        if (maxValue < 0) {

            throw new IllegalArgumentException("maxValue cannot be negative");
        }
        
        if (sessionId == null || sessionId.trim().isEmpty()) {

            throw new IllegalArgumentException("sessionId cannot be null or empty");
        }
    }
}

