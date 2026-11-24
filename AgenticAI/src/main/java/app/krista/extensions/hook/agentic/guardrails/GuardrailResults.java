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
 * Result of a guardrail check.
 * Indicates whether an action is allowed or blocked, and why.
 */
public class GuardrailResults {
    private final boolean allowed;
    private final String reason;

    private GuardrailResults(boolean allowed, String reason) {
        this.allowed = allowed;
        this.reason = reason;
    }

    public static GuardrailResults allowed() {
        return new GuardrailResults(true, null);
    }

    public static GuardrailResults blocked(String reason) {
        return new GuardrailResults(false, reason);
    }

    public boolean isAllowed() {
        return allowed;
    }

    public boolean isBlocked() {
        return !allowed;
    }

    public String getReason() {
        return reason;
    }

    @Override
    public String toString() {
        return allowed ? "ALLOWED" : "BLOCKED: " + reason;
    }
}
