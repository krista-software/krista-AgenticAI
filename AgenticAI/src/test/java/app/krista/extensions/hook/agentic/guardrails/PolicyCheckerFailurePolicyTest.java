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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for PolicyCheckerFailurePolicy enum.
 */
public class PolicyCheckerFailurePolicyTest {

    @Test
    @DisplayName("Should have FAIL_OPEN value")
    void testFailOpenExists() {

        PolicyCheckerFailurePolicy policy = PolicyCheckerFailurePolicy.FAIL_OPEN;
        assertNotNull(policy, "FAIL_OPEN should exist");
        assertEquals("FAIL_OPEN", policy.name());
    }

    @Test
    @DisplayName("Should have FAIL_CLOSED value")
    void testFailClosedExists() {

        PolicyCheckerFailurePolicy policy = PolicyCheckerFailurePolicy.FAIL_CLOSED;
        assertNotNull(policy, "FAIL_CLOSED should exist");
        assertEquals("FAIL_CLOSED", policy.name());
    }

    @Test
    @DisplayName("Should have exactly two values")
    void testExactlyTwoValues() {

        PolicyCheckerFailurePolicy[] values = PolicyCheckerFailurePolicy.values();
        assertEquals(2, values.length, "Should have exactly FAIL_OPEN and FAIL_CLOSED");
    }

    @Test
    @DisplayName("Should support valueOf lookup")
    void testValueOfLookup() {

        assertEquals(PolicyCheckerFailurePolicy.FAIL_OPEN, PolicyCheckerFailurePolicy.valueOf("FAIL_OPEN"));
        assertEquals(PolicyCheckerFailurePolicy.FAIL_CLOSED, PolicyCheckerFailurePolicy.valueOf("FAIL_CLOSED"));
    }
}
