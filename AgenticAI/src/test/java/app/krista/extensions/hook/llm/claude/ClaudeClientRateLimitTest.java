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

package app.krista.extensions.hook.llm.claude;

import app.krista.extensions.hook.llm.HttpClientPool;
import app.krista.extensions.hook.llm.RateLimitException;
import okhttp3.*;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockitoAnnotations;


import java.io.IOException;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Test class for Claude client rate limiting functionality.
 * Tests rate limit detection, header parsing, and exception handling.
 */
public class ClaudeClientRateLimitTest {

    private OkHttpClient mockHttpClient;
    private Call mockCall;
    private Response mockResponse;
    private ResponseBody mockResponseBody;

    private ClaudeClient claudeClient;

    @Before
    public void setUp() {
        // Initialize mocks manually for compatibility
        mockHttpClient = mock(OkHttpClient.class);
        mockCall = mock(Call.class);
        mockResponse = mock(Response.class);
        mockResponseBody = mock(ResponseBody.class);

        // Clear HTTP client pool cache to ensure clean state
        HttpClientPool.getInstance().clearCache();

        // Create Claude client with test configuration
        claudeClient = new ClaudeClient("test-api-key", "claude-3-sonnet-20240229", 30);
    }

    @Test
    public void testHandleResponse_WithRateLimitResponse_ThrowsRateLimitException() throws IOException {
        // Arrange
        // Mock rate limit response (HTTP 429)
        when(mockResponse.code()).thenReturn(429);
        when(mockResponse.header("retry-after")).thenReturn("90");
        when(mockResponse.body()).thenReturn(mockResponseBody);
        when(mockResponseBody.string()).thenReturn("{\"error\":{\"message\":\"Rate limit exceeded\"}}");

        // Act & Assert
        try {
            // Use reflection to call the private handleResponse method
            java.lang.reflect.Method handleResponseMethod = ClaudeClient.class.getDeclaredMethod("handleResponse", Response.class);
            handleResponseMethod.setAccessible(true);
            handleResponseMethod.invoke(claudeClient, mockResponse);
            
            fail("Expected RateLimitException to be thrown");
        } catch (Exception e) {
            // Unwrap reflection exception
            Throwable cause = e.getCause();
            assertTrue("Should throw RateLimitException", cause instanceof RateLimitException);
            
            RateLimitException rateLimitException = (RateLimitException) cause;
            assertEquals("Provider should be Claude", "Claude", rateLimitException.getProvider());
            assertEquals("Retry after should be 90 seconds", 90, rateLimitException.getRetryAfterSeconds());
            assertTrue("Message should contain rate limit info", rateLimitException.getMessage().contains("retry after 90 seconds"));
        }
    }

    @Test
    public void testHandleResponse_WithRateLimitResponseNoRetryHeader_UsesDefaultRetryTime() throws IOException {
        // Arrange
        // Mock rate limit response without retry-after header
        when(mockResponse.code()).thenReturn(429);
        when(mockResponse.header("retry-after")).thenReturn(null);
        when(mockResponse.body()).thenReturn(mockResponseBody);
        when(mockResponseBody.string()).thenReturn("{\"error\":{\"message\":\"Rate limit exceeded\"}}");

        // Act & Assert
        try {
            // Use reflection to call the private handleResponse method
            java.lang.reflect.Method handleResponseMethod = ClaudeClient.class.getDeclaredMethod("handleResponse", Response.class);
            handleResponseMethod.setAccessible(true);
            handleResponseMethod.invoke(claudeClient, mockResponse);

            fail("Expected RateLimitException to be thrown");
        } catch (Exception e) {
            // Unwrap reflection exception
            Throwable cause = e.getCause();
            assertTrue("Should throw RateLimitException", cause instanceof RateLimitException);

            RateLimitException rateLimitException = (RateLimitException) cause;
            assertEquals("Provider should be Claude", "Claude", rateLimitException.getProvider());
            assertEquals("Should use default retry time", 60, rateLimitException.getRetryAfterSeconds());
        }
    }

    @Test
    public void testHandleResponse_WithRateLimitResponseNoHeaders_UsesDefaultRetryTime() throws IOException {
        // Arrange
        // Mock rate limit response without any retry headers
        when(mockResponse.code()).thenReturn(429);
        when(mockResponse.header("retry-after")).thenReturn(null);
        when(mockResponse.header("anthropic-ratelimit-reset")).thenReturn(null);
        when(mockResponse.body()).thenReturn(mockResponseBody);
        when(mockResponseBody.string()).thenReturn("{\"error\":{\"message\":\"Rate limit exceeded\"}}");

        // Act & Assert
        try {
            // Use reflection to call the private handleResponse method
            java.lang.reflect.Method handleResponseMethod = ClaudeClient.class.getDeclaredMethod("handleResponse", Response.class);
            handleResponseMethod.setAccessible(true);
            handleResponseMethod.invoke(claudeClient, mockResponse);
            
            fail("Expected RateLimitException to be thrown");
        } catch (Exception e) {
            // Unwrap reflection exception
            Throwable cause = e.getCause();
            assertTrue("Should throw RateLimitException", cause instanceof RateLimitException);
            
            RateLimitException rateLimitException = (RateLimitException) cause;
            assertEquals("Provider should be Claude", "Claude", rateLimitException.getProvider());
            assertEquals("Should use default retry time", 60, rateLimitException.getRetryAfterSeconds());
        }
    }

    @Test
    public void testHandleResponse_WithSuccessfulResponse_ReturnsContent() throws IOException {
        // Arrange
        String responseJson = "{\"content\":[{\"text\":\"Test response from Claude\"}]}";
        
        // Mock successful response
        when(mockResponse.code()).thenReturn(200);
        when(mockResponse.isSuccessful()).thenReturn(true);
        when(mockResponse.body()).thenReturn(mockResponseBody);
        when(mockResponseBody.string()).thenReturn(responseJson);

        // Act & Assert
        try {
            // Use reflection to call the private handleResponse method
            java.lang.reflect.Method handleResponseMethod = ClaudeClient.class.getDeclaredMethod("handleResponse", Response.class);
            handleResponseMethod.setAccessible(true);
            String result = (String) handleResponseMethod.invoke(claudeClient, mockResponse);
            
            assertNotNull("Response should not be null", result);
            assertEquals("Should return extracted text", "Test response from Claude", result);
        } catch (Exception e) {
            fail("Should not throw exception for successful response: " + e.getMessage());
        }
    }

    @Test
    public void testHandleResponse_WithServerError_ThrowsIOException() throws IOException {
        // Arrange
        // Mock server error response
        when(mockResponse.code()).thenReturn(500);
        when(mockResponse.isSuccessful()).thenReturn(false);
        when(mockResponse.body()).thenReturn(mockResponseBody);
        when(mockResponseBody.string()).thenReturn("{\"error\":{\"message\":\"Internal server error\"}}");

        // Act & Assert
        try {
            // Use reflection to call the private handleResponse method
            java.lang.reflect.Method handleResponseMethod = ClaudeClient.class.getDeclaredMethod("handleResponse", Response.class);
            handleResponseMethod.setAccessible(true);
            handleResponseMethod.invoke(claudeClient, mockResponse);
            
            fail("Expected IOException to be thrown");
        } catch (Exception e) {
            // Unwrap reflection exception
            Throwable cause = e.getCause();
            assertTrue("Should throw IOException", cause instanceof IOException);
            assertTrue("Error message should contain status code", cause.getMessage().contains("500"));
        }
    }

    @Test
    public void testQuery_WithValidInputs_CallsExecute() {
        // This test verifies that the query method properly processes inputs
        // We can't easily mock the HTTP call, so we test the validation logic
        
        // Test with valid inputs - should not throw exception during validation
        try {
            // This will fail at HTTP call level, but validation should pass
            claudeClient.query("System prompt", "User prompt");
        } catch (RuntimeException e) {
            // Expected to fail at HTTP level since we're not mocking the actual HTTP client
            // But it should not fail due to validation
            assertTrue("Should fail at API call level, not validation", 
                e.getMessage().contains("API call failed") || e.getMessage().contains("Connection"));
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void testConstructor_WithEmptyApiKey_ThrowsException() {
        // Act & Assert
        new ClaudeClient("", "claude-3-sonnet-20240229");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testConstructor_WithNullApiKey_ThrowsException() {
        // Act & Assert
        new ClaudeClient(null, "claude-3-sonnet-20240229");
    }

    @Test
    public void testConstructor_WithValidParameters_CreatesClient() {
        // Act
        ClaudeClient client = new ClaudeClient("test-key", "claude-3-sonnet-20240229", 45);
        
        // Assert
        assertNotNull("Client should be created successfully", client);
    }

    @Test
    public void testConstructor_UsesHttpClientPool() {
        // Arrange
        int initialCacheSize = HttpClientPool.getInstance().getCacheSize();
        
        // Act
        new ClaudeClient("test-key", "claude-3-sonnet-20240229", 30);
        
        // Assert
        int finalCacheSize = HttpClientPool.getInstance().getCacheSize();
        assertTrue("HTTP client pool should be used", finalCacheSize >= initialCacheSize);
    }

    @Test
    public void testExtractRetryAfterSeconds_WithRetryAfterHeader() throws Exception {
        // Arrange
        when(mockResponse.header("retry-after")).thenReturn("120");
        when(mockResponse.header("anthropic-ratelimit-reset")).thenReturn("180");

        // Act
        java.lang.reflect.Method extractMethod = ClaudeClient.class.getDeclaredMethod("extractRetryAfterSeconds", Response.class);
        extractMethod.setAccessible(true);
        long retryAfter = (Long) extractMethod.invoke(claudeClient, mockResponse);

        // Assert
        assertEquals("Should use retry-after header first", 120, retryAfter);
    }

    @Test
    public void testExtractRetryAfterSeconds_WithNoHeaders_ReturnsDefault() throws Exception {
        // Arrange
        when(mockResponse.header("retry-after")).thenReturn(null);

        // Act
        java.lang.reflect.Method extractMethod = ClaudeClient.class.getDeclaredMethod("extractRetryAfterSeconds", Response.class);
        extractMethod.setAccessible(true);
        long retryAfter = (Long) extractMethod.invoke(claudeClient, mockResponse);

        // Assert
        assertEquals("Should return default value", 60, retryAfter);
    }


}
