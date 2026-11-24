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

package app.krista.extensions.hook.policybrief;

import java.util.regex.Pattern;

/**
 * Normalizes leaked internal rule-ID prefixes from author-facing policy brief text.
 */
final class PolicyBriefRuleTextNormalizer {

    private static final Pattern LEAKED_RULE_ID_PREFIX =
            Pattern.compile("^R\\d+\\s*(?:\\.|:|\\)|-)\\s+");

    private PolicyBriefRuleTextNormalizer() {
    }

    static String normalizeVisibleRuleText(String text) {

        if (text == null) {

            return null;
        }

        return LEAKED_RULE_ID_PREFIX.matcher(text).replaceFirst("");
    }
}
