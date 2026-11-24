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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for PolicyCheckResult DTO.
 */
public class PolicyCheckResultTest {

    @Test
    @DisplayName("Should construct with all fields via constructor")
    void testConstructionWithAllFields() {

        PolicyCheckResult result = new PolicyCheckResult(
                PolicyCheckResult.Decision.BLOCKED,
                "No external API calls",
                "Tool calls external API",
                "HIGH"
        );

        assertEquals(PolicyCheckResult.Decision.BLOCKED, result.getDecision());
        assertEquals("No external API calls", result.getViolatedPolicy());
        assertEquals("Tool calls external API", result.getReason());
        assertEquals("HIGH", result.getSeverity());
    }

    @Test
    @DisplayName("Should create ALLOWED result via factory method")
    void testAllowedFactory() {

        PolicyCheckResult result = PolicyCheckResult.allowed("Action complies with all policies");

        assertEquals(PolicyCheckResult.Decision.ALLOWED, result.getDecision());
        assertNull(result.getViolatedPolicy(), "ALLOWED result should have null violatedPolicy");
        assertEquals("Action complies with all policies", result.getReason());
        assertNull(result.getSeverity(), "ALLOWED result should have null severity");
    }

    @Test
    @DisplayName("Should create BLOCKED result via factory method")
    void testBlockedFactory() {

        PolicyCheckResult result = PolicyCheckResult.blocked(
                "Must not delete records",
                "Attempted to delete customer record",
                "HIGH"
        );

        assertEquals(PolicyCheckResult.Decision.BLOCKED, result.getDecision());
        assertEquals("Must not delete records", result.getViolatedPolicy());
        assertEquals("Attempted to delete customer record", result.getReason());
        assertEquals("HIGH", result.getSeverity());
    }

    @Test
    @DisplayName("Should create checkerError result via factory method")
    void testCheckerErrorFactory() {

        PolicyCheckResult result = PolicyCheckResult.checkerError("LLM timeout after 30s");

        assertEquals(PolicyCheckResult.Decision.BLOCKED, result.getDecision());
        assertNull(result.getViolatedPolicy(), "checkerError should have null violatedPolicy");
        assertEquals("PolicyChecker error: LLM timeout after 30s", result.getReason());
        assertEquals("HIGH", result.getSeverity());
    }

    @Test
    @DisplayName("Should support getters and setters")
    void testGettersAndSetters() {

        PolicyCheckResult result = new PolicyCheckResult();

        result.setDecision(PolicyCheckResult.Decision.ALLOWED);
        result.setViolatedPolicy("some policy");
        result.setReason("some reason");
        result.setSeverity("MEDIUM");

        assertEquals(PolicyCheckResult.Decision.ALLOWED, result.getDecision());
        assertEquals("some policy", result.getViolatedPolicy());
        assertEquals("some reason", result.getReason());
        assertEquals("MEDIUM", result.getSeverity());
    }

    @Test
    @DisplayName("Should include all fields in toString output")
    void testToStringIncludesAllFields() {

        PolicyCheckResult result = new PolicyCheckResult(
                PolicyCheckResult.Decision.BLOCKED,
                "No PII exposure",
                "Contains SSN",
                "HIGH"
        );

        String str = result.toString();
        assertTrue(str.contains("BLOCKED"), "toString should contain decision");
        assertTrue(str.contains("No PII exposure"), "toString should contain violatedPolicy");
        assertTrue(str.contains("Contains SSN"), "toString should contain reason");
        assertTrue(str.contains("HIGH"), "toString should contain severity");
    }

    @Test
    @DisplayName("Should handle no-arg constructor with null defaults")
    void testNoArgConstructor() {

        PolicyCheckResult result = new PolicyCheckResult();

        assertNull(result.getDecision());
        assertNull(result.getViolatedPolicy());
        assertNull(result.getReason());
        assertNull(result.getSeverity());
    }
}
