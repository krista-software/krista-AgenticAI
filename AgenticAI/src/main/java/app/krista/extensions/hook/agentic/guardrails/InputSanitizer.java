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
 * Sanitizes untrusted input before it reaches the LLM.
 *
 * Strips instruction hierarchy markers (<<<USER_DATA>>>, <<<END_USER_DATA>>>)
 * that could allow a user to break out of the untrusted data boundary in the
 * ToolPlanner system prompt.
 *
 * This is the code-level complement to the LLM-side instruction hierarchy defense.
 * Pattern-based injection detection was removed in KR-20580 because it caused
 * false positives on legitimate business text and was trivially bypassed.
 *
 * @see app.krista.extensions.hook.agentic.planner.ToolPlanner
 */
public final class InputSanitizer {

    private InputSanitizer() {

    }

    /**
     * Strips instruction hierarchy markers from untrusted input.
     *
     * Uses exact ASCII match intentionally — the ToolPlanner system prompt uses
     * exact ASCII markers ({@literal <<<USER_DATA>>>}, {@literal <<<END_USER_DATA>>>}),
     * so only exact ASCII strings can break the boundary. Unicode look-alikes
     * (e.g., fullwidth angle brackets) would not be interpreted as markers by the LLM.
     *
     * @param input the raw untrusted input (may be null)
     * @return sanitized input with markers removed, or empty string if null
     */
    public static String sanitize(String input) {

        if (input == null) {

            return "";
        }
        return input.replace("<<<USER_DATA>>>", "")
                    .replace("<<<END_USER_DATA>>>", "");
    }
}
