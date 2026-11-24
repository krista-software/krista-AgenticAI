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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link PolicyBriefGeneratorFactory} — singleton factory and memory management.
 *
 * <p>These tests cover the parts of the factory that can be exercised without a real
 * LLM model: the reset mechanism, null argument validation, and memory cleanup safety.
 * The factory's {@code getInstance(Invoker)} requires a real {@link app.krista.extension.executor.Invoker}
 * with valid API keys to create the {@link dev.langchain4j.model.chat.ChatLanguageModel},
 * so that path is tested indirectly via the service lifecycle tests.</p>
 */
public class PolicyBriefGeneratorFactoryTest {

    @AfterEach
    void tearDown() {

        // Always reset the singleton between tests so they stay independent
        PolicyBriefGeneratorFactory.reset();
    }

    // =========================================================================
    // Null argument handling
    // =========================================================================

    @Test
    @DisplayName("Should throw IllegalArgumentException when invoker is null")
    void testGetInstanceWithNullInvokerThrows() {

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> PolicyBriefGeneratorFactory.getInstance(null),
                "getInstance(null) should throw IllegalArgumentException"
        );
        assertTrue(exception.getMessage().contains("null"),
                "Exception message should mention null invoker");
    }

    // =========================================================================
    // Reset behavior
    // =========================================================================

    @Test
    @DisplayName("Should not throw when reset is called before any instance is created")
    void testResetBeforeCreationIsNoop() {

        assertDoesNotThrow(PolicyBriefGeneratorFactory::reset,
                "reset() before any getInstance() should be a safe no-op");
    }

    @Test
    @DisplayName("Should allow multiple resets without error")
    void testMultipleResetsAreIdempotent() {

        PolicyBriefGeneratorFactory.reset();
        PolicyBriefGeneratorFactory.reset();
        PolicyBriefGeneratorFactory.reset();

        // No exception means pass
    }

    // =========================================================================
    // Memory cleanup safety
    // =========================================================================

    @Test
    @DisplayName("Should not throw when cleanupMemory is called before any instance exists")
    void testCleanupMemoryBeforeInstanceIsNoop() {

        // chatMemoryMap is null at this point since no instance was created
        assertDoesNotThrow(() -> PolicyBriefGeneratorFactory.cleanupMemory("some-job-id"),
                "cleanupMemory should be safe when no instance has been created");
    }

    @Test
    @DisplayName("Should not throw when cleanupMemory is called with null jobId")
    void testCleanupMemoryWithNullJobId() {

        assertDoesNotThrow(() -> PolicyBriefGeneratorFactory.cleanupMemory(null),
                "cleanupMemory(null) should be a safe no-op");
    }

    @Test
    @DisplayName("Should not throw when cleanupMemory is called after reset")
    void testCleanupMemoryAfterReset() {

        PolicyBriefGeneratorFactory.reset();

        assertDoesNotThrow(() -> PolicyBriefGeneratorFactory.cleanupMemory("any-id"),
                "cleanupMemory after reset should be a safe no-op");
    }
}
