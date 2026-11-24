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

package app.krista.extensions.hook.common;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for OrchestrationContext, focusing on policyBrief support.
 */
public class OrchestrationContextTest {

    @Test
    @DisplayName("empty() should create context with null policyBrief")
    void testEmptyContextHasNullPolicyBrief() {

        OrchestrationContext context = OrchestrationContext.empty();

        assertNull(context.getPolicyBrief(), "empty() context should have null policyBrief");
        assertFalse(context.isOrchestration(), "empty() context should not be orchestration");
    }

    @Test
    @DisplayName("of() should preserve policyBrief")
    void testOfPreservesPolicyBrief() {

        List<String> policyBrief = List.of("No deletions allowed", "Must log all actions");

        OrchestrationContext context = OrchestrationContext.of(
                List.of(Map.of("name", "tool1")),
                "sess_001",
                "prevTool",
                Map.of("result", "ok"),
                "Do something",
                List.of(),
                List.of(),
                policyBrief
        );

        assertNotNull(context.getPolicyBrief(), "policyBrief should not be null");
        assertEquals(2, context.getPolicyBrief().size(), "policyBrief should have 2 rules");
        assertEquals("No deletions allowed", context.getPolicyBrief().get(0));
        assertEquals("Must log all actions", context.getPolicyBrief().get(1));
    }

    @Test
    @DisplayName("getPolicyBrief() should return what was passed")
    void testGetPolicyBriefReturnsPassedValue() {

        List<String> policyBrief = List.of("Single policy rule");

        OrchestrationContext context = OrchestrationContext.of(
                List.of(Map.of("name", "tool1")),
                "sess_002",
                null,
                null,
                "prompt",
                null,
                null,
                policyBrief
        );

        assertSame(policyBrief, context.getPolicyBrief(),
                "getPolicyBrief should return the exact list that was passed");
    }

    @Test
    @DisplayName("of() with null policyBrief should return null from getter")
    void testOfWithNullPolicyBrief() {

        OrchestrationContext context = OrchestrationContext.of(
                List.of(Map.of("name", "tool1")),
                "sess_003",
                null,
                null,
                "prompt",
                null,
                null,
                null
        );

        assertNull(context.getPolicyBrief(), "null policyBrief should be preserved");
    }
}
