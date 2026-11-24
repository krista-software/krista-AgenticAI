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

package app.krista.extensions.hook.config;

import app.krista.extension.executor.Invoker;
import app.krista.extensions.hook.Constants;
import org.junit.Before;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * Test class for LLMConfiguration to verify configuration logic,
 * especially the new behavior for treating Krista LLM as disabled when API key is missing.
 */
public class LLMConfigurationTest {

    private Invoker mockInvoker;
    private Map<String, Object> mockAttributes;
    private LLMConfiguration llmConfiguration;

    @Before
    public void setUp() {
        // Initialize mocks manually for compatibility
        mockInvoker = mock(Invoker.class);
        mockAttributes = new HashMap<>();
        
        when(mockInvoker.getAttributes()).thenReturn(mockAttributes);
        llmConfiguration = new LLMConfiguration(mockInvoker);
    }

    @Test
    public void testIsKristaLLMFallbackEnabled_ConfigDisabled_ReturnsFalse() {
        // Arrange
        mockAttributes.put("EnableKristaLLMFallback", false);
        mockAttributes.put("KristaAPIKey", "valid-api-key");

        // Act
        boolean result = llmConfiguration.isKristaLLMFallbackEnabled();

        // Assert
        assertFalse("Should return false when config is disabled", result);
    }

//    @Test
//    public void testIsKristaLLMFallbackEnabled_ConfigEnabledWithValidKey_ReturnsTrue() {
//        // Arrange
//        mockAttributes.put("EnableKristaLLMFallback", true);
//        mockAttributes.put("KristaAPIKey", "valid-api-key");
//
//        // Act
//        boolean result = llmConfiguration.isKristaLLMFallbackEnabled();
//
//        // Assert
//        assertTrue("Should return true when config is enabled and API key is provided", result);
//    }

    @Test
    public void testIsKristaLLMFallbackEnabled_ConfigEnabledButEmptyKey_ReturnsFalse() {
        // Arrange
        mockAttributes.put("EnableKristaLLMFallback", true);
        mockAttributes.put("KristaAPIKey", "");

        // Act
        boolean result = llmConfiguration.isKristaLLMFallbackEnabled();

        // Assert
        assertFalse("Should return false when API key is empty", result);
    }

    @Test
    public void testIsKristaLLMFallbackEnabled_ConfigEnabledButMissingKey_ReturnsFalse() {
        // Arrange
        mockAttributes.put("EnableKristaLLMFallback", true);
        // Don't put KristaAPIKey, so getOrDefault will return ""

        // Act
        boolean result = llmConfiguration.isKristaLLMFallbackEnabled();

        // Assert
        assertFalse("Should return false when API key is missing", result);
    }

    @Test
    public void testIsKristaLLMFallbackEnabled_ConfigEnabledButWhitespaceKey_ReturnsFalse() {
        // Arrange
        mockAttributes.put("EnableKristaLLMFallback", true);
        mockAttributes.put("KristaAPIKey", "   ");

        // Act
        boolean result = llmConfiguration.isKristaLLMFallbackEnabled();

        // Assert
        assertFalse("Should return false when API key is only whitespace", result);
    }

    @Test
    public void testIsKristaLLMFallbackEnabled_DefaultConfigValues_ReturnsFalse() {
        // Arrange - use default values (no attributes set)

        // Act
        boolean result = llmConfiguration.isKristaLLMFallbackEnabled();

        // Assert
        assertFalse("Should return false with default configuration", result);
    }

    @Test
    public void testGetCloudProvider_DefaultValue_ReturnsOpenAI() {
        // Arrange - no CloudLLMProvider set

        // Act
        String result = llmConfiguration.getCloudProvider();

        // Assert
        assertEquals("Should return openai as default", "openai", result);
    }

    @Test
    public void testGetCloudProvider_ClaudeConfigured_ReturnsClaude() {
        // Arrange
        mockAttributes.put(Constants.CLOUD_LLM_PROVIDER, "claude");

        // Act
        String result = llmConfiguration.getCloudProvider();

        // Assert
        assertEquals("Should return claude when configured", "claude", result);
    }

    @Test
    public void testGetCloudProvider_UppercaseValue_ReturnsLowercase() {
        // Arrange
        mockAttributes.put(Constants.CLOUD_LLM_PROVIDER, "CLAUDE");

        // Act
        String result = llmConfiguration.getCloudProvider();

        // Assert
        assertEquals("Should return lowercase value", "claude", result);
    }

//    @Test
//    public void testGetKristaAPIKey_ValidKey_ReturnsKey() {
//        // Arrange
//        String expectedKey = "test-krista-api-key";
//        mockAttributes.put("KristaAPIKey", expectedKey);
//
//        // Act
//        String result = llmConfiguration.getKristaAPIKey();
//
//        // Assert
//        assertEquals("Should return the configured API key", expectedKey, result);
//    }

    @Test
    public void testGetKristaAPIKey_MissingKey_ReturnsEmpty() {
        // Arrange - no KristaAPIKey set

        // Act
        String result = llmConfiguration.getKristaAPIKey();

        // Assert
        assertEquals("Should return empty string when key is missing", "", result);
    }

//    @Test
//    public void testGetKristaAPIKey_WhitespaceKey_ReturnsTrimmed() {
//        // Arrange
//        mockAttributes.put("KristaAPIKey", "  test-key  ");
//
//        // Act
//        String result = llmConfiguration.getKristaAPIKey();
//
//        // Assert
//        assertEquals("Should return trimmed key", "test-key", result);
//    }
}
