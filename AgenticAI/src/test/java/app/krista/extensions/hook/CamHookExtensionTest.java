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

package app.krista.extensions.hook;

import app.krista.extension.executor.Invoker;
import org.junit.Before;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static app.krista.extensions.hook.Constants.CLOUD_API_KEY;
import static app.krista.extensions.hook.Constants.CLOUD_LLM_PROVIDER;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * Test class for CamHookExtension to verify validation, connection testing, and setup validation functionality.
 */
public class CamHookExtensionTest {

    private Map<String, Object> mockAttributes;
    private CamHookExtension camHookExtension;

    @Before
    public void setUp() {
        // Initialize mocks manually for compatibility
        Invoker mockInvoker = mock(Invoker.class);
        mockAttributes = new HashMap<>();
        
        when(mockInvoker.getAttributes()).thenReturn(mockAttributes);
        camHookExtension = new CamHookExtension(mockInvoker);
    }

    @Test
    public void testCustomTabs_ReturnsDocumentationTab() {
        // Act
        Map<String, String> tabs = camHookExtension.customTabs();

        // Assert
        assertNotNull("Custom tabs should not be null", tabs);
        assertEquals("Should have one tab", 1, tabs.size());
        assertTrue("Should contain Documentation tab", tabs.containsKey("Documentation"));
        assertEquals("Documentation path should be correct", "static/docs", tabs.get("Documentation"));
    }

    @Test
    public void testValidateAttributes_ValidOpenAIConfiguration_FailsWithInvalidKey() {
        // Arrange
        Map<String, String> attributes = new HashMap<>();
        attributes.put(CLOUD_LLM_PROVIDER, "OpenAI");
        attributes.put(CLOUD_API_KEY, "sk-test-invalid-api-key-12345");

        // Act & Assert - Should fail due to invalid API key (real API call)
        try {
            camHookExtension.validateAttributes(attributes);
            fail("Expected RuntimeException due to invalid API key");
        } catch (RuntimeException e) {
            // Expected - connection test will fail with invalid API key
            assertTrue("Should contain connection failure message",
                e.getMessage().contains("Failed to connect to LLM provider") ||
                e.getMessage().contains("Configuration validation failed"));
        }
    }

    @Test
    public void testValidateAttributes_ValidClaudeConfiguration_FailsWithInvalidKey() {
        // Arrange
        Map<String, String> attributes = new HashMap<>();
        attributes.put(CLOUD_LLM_PROVIDER, "Claude");
        attributes.put(CLOUD_API_KEY, "sk-ant-test-invalid-api-key-12345");

        // Act & Assert - Should fail due to invalid API key (real API call)
        try {
            camHookExtension.validateAttributes(attributes);
            fail("Expected RuntimeException due to invalid API key");
        } catch (RuntimeException e) {
            // Expected - connection test will fail with invalid API key
            assertTrue("Should contain connection failure message",
                e.getMessage().contains("Failed to connect to LLM provider") ||
                e.getMessage().contains("Configuration validation failed"));
        }
    }

    @Test
    public void testValidateAttributes_MissingProvider_ThrowsException() {
        // Arrange
        Map<String, String> attributes = new HashMap<>();
        attributes.put(CLOUD_API_KEY, "sk-test-api-key-12345");

        // Act & Assert
        try {
            camHookExtension.validateAttributes(attributes);
            fail("Expected RuntimeException for missing provider");
        } catch (RuntimeException e) {
            assertTrue("Should contain provider required message", 
                e.getMessage().contains("CloudLLMProvider is required"));
        }
    }

    @Test
    public void testValidateAttributes_EmptyProvider_ThrowsException() {
        // Arrange
        Map<String, String> attributes = new HashMap<>();
        attributes.put(CLOUD_LLM_PROVIDER, "");
        attributes.put(CLOUD_API_KEY, "sk-test-api-key-12345");

        // Act & Assert
        try {
            camHookExtension.validateAttributes(attributes);
            fail("Expected RuntimeException for empty provider");
        } catch (RuntimeException e) {
            assertTrue("Should contain provider required message", 
                e.getMessage().contains("CloudLLMProvider is required"));
        }
    }

    @Test
    public void testValidateAttributes_InvalidProvider_ThrowsException() {
        // Arrange
        Map<String, String> attributes = new HashMap<>();
        attributes.put(CLOUD_LLM_PROVIDER, "InvalidProvider");
        attributes.put(CLOUD_API_KEY, "sk-test-api-key-12345");

        // Act & Assert
        try {
            camHookExtension.validateAttributes(attributes);
            fail("Expected RuntimeException for invalid provider");
        } catch (RuntimeException e) {
            assertTrue("Should contain provider validation message", 
                e.getMessage().contains("CloudLLMProvider must be either 'OpenAI' or 'Claude'"));
        }
    }

    @Test
    public void testValidateAttributes_MissingAPIKey_ThrowsException() {
        // Arrange
        Map<String, String> attributes = new HashMap<>();
        attributes.put(CLOUD_LLM_PROVIDER, "OpenAI");

        // Act & Assert
        try {
            camHookExtension.validateAttributes(attributes);
            fail("Expected RuntimeException for missing API key");
        } catch (RuntimeException e) {
            assertTrue("Should contain API key required message", 
                e.getMessage().contains("CloudAPIKey is required"));
        }
    }

    @Test
    public void testValidateAttributes_EmptyAPIKey_ThrowsException() {
        // Arrange
        Map<String, String> attributes = new HashMap<>();
        attributes.put(CLOUD_LLM_PROVIDER, "OpenAI");
        attributes.put(CLOUD_API_KEY, "");

        // Act & Assert
        try {
            camHookExtension.validateAttributes(attributes);
            fail("Expected RuntimeException for empty API key");
        } catch (RuntimeException e) {
            assertTrue("Should contain API key required message", 
                e.getMessage().contains("CloudAPIKey is required"));
        }
    }

    @Test
    public void testTestConnection_NoConfiguration_ThrowsException() {
        // Arrange - empty attributes
        mockAttributes.clear();

        // Act & Assert
        try {
            camHookExtension.testConnection();
            fail("Expected RuntimeException for no configuration");
        } catch (RuntimeException e) {
            assertTrue("Should contain no configuration message", 
                e.getMessage().contains("No configuration found"));
        }
    }

    @Test
    public void testTestConnection_WithInvalidConfiguration_FailsConnection() {
        // Arrange
        mockAttributes.put(CLOUD_LLM_PROVIDER, "OpenAI");
        mockAttributes.put(CLOUD_API_KEY, "sk-test-invalid-api-key-12345");

        // Act & Assert
        try {
            camHookExtension.testConnection();
            fail("Expected RuntimeException due to invalid API key");
        } catch (RuntimeException e) {
            // Expected - connection test will fail with invalid API key
            assertTrue("Should contain connection failure message",
                e.getMessage().contains("Connection test failed") ||
                e.getMessage().contains("Configuration validation failed"));
        }
    }

    @Test
    public void testValidateAttributes_CaseInsensitiveProvider_FailsWithInvalidKey() {
        // Arrange
        Map<String, String> attributes = new HashMap<>();
        attributes.put(CLOUD_LLM_PROVIDER, "openai"); // lowercase
        attributes.put(CLOUD_API_KEY, "sk-test-invalid-api-key-12345");

        // Act & Assert - Should fail due to invalid API key (real API call)
        try {
            camHookExtension.validateAttributes(attributes);
            fail("Expected RuntimeException due to invalid API key");
        } catch (RuntimeException e) {
            // Expected - connection test will fail with invalid API key
            assertTrue("Should contain connection failure message",
                e.getMessage().contains("Failed to connect to LLM provider") ||
                e.getMessage().contains("Configuration validation failed"));
        }
    }
}
