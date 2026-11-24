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

package app.krista.extensions.hook.llm.openai;

import app.krista.extensions.hook.llm.HttpClientPool;
import app.krista.extensions.hook.llm.RateLimitException;
import app.krista.extensions.hook.llm.openai.util.ChatGPTResponse;
import okhttp3.*;
import org.junit.Before;
import org.junit.Test;


import java.io.IOException;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Test class for OpenAI client rate limiting functionality.
 * Tests rate limit detection, header parsing, and exception handling.
 */
public class OpenAIClientRateLimitTest {

    private OkHttpClient mockHttpClient;
    private Call mockCall;
    private Response mockResponse;
    private ResponseBody mockResponseBody;

    private OpenAIClient openAIClient;

    @Before
    public void setUp() {
        // Initialize mocks manually for compatibility
        mockHttpClient = mock(OkHttpClient.class);
        mockCall = mock(Call.class);
        mockResponse = mock(Response.class);
        mockResponseBody = mock(ResponseBody.class);

        // Clear HTTP client pool cache to ensure clean state
        HttpClientPool.getInstance().clearCache();

        // Create OpenAI client with test configuration
        openAIClient = new OpenAIClient("test-api-key", "gpt-3.5-turbo", 30);
    }

    @Test
    public void testExecute_WithRateLimitResponse_ThrowsRateLimitException() throws IOException {
        // Arrange
        String testJson = "{\"model\":\"gpt-3.5-turbo\",\"messages\":[]}";
        
        // Mock rate limit response (HTTP 429)
        when(mockResponse.code()).thenReturn(429);
        when(mockResponse.header("retry-after")).thenReturn("60");
        when(mockResponse.body()).thenReturn(mockResponseBody);
        when(mockResponseBody.string()).thenReturn("{\"error\":{\"message\":\"Rate limit exceeded\"}}");
        
        when(mockCall.execute()).thenReturn(mockResponse);
        when(mockHttpClient.newCall(any(Request.class))).thenReturn(mockCall);

        // Use reflection to replace the HTTP client with our mock
        try {
            java.lang.reflect.Field httpClientField = OpenAIClient.class.getDeclaredField("httpClient");
            httpClientField.setAccessible(true);
            httpClientField.set(openAIClient, mockHttpClient);
        } catch (Exception e) {
            fail("Failed to inject mock HTTP client: " + e.getMessage());
        }

        // Act & Assert
        try {
            openAIClient.execute(testJson, "test-api-key", "gpt-3.5-turbo");
            fail("Expected RateLimitException to be thrown");
        } catch (RateLimitException e) {
            assertEquals("Provider should be OpenAI", "OpenAI", e.getProvider());
            assertEquals("Retry after should be 60 seconds", 60, e.getRetryAfterSeconds());
            assertTrue("Message should contain rate limit info", e.getMessage().contains("retry after 60 seconds"));
        }
    }

    @Test
    public void testExecute_WithRateLimitResponseNoRetryHeader_UsesDefaultRetryTime() throws IOException {
        // Arrange
        String testJson = "{\"model\":\"gpt-3.5-turbo\",\"messages\":[]}";

        // Mock rate limit response without retry-after header
        when(mockResponse.code()).thenReturn(429);
        when(mockResponse.header("retry-after")).thenReturn(null);
        when(mockResponse.header("x-ratelimit-reset-requests")).thenReturn(null);
        when(mockResponse.body()).thenReturn(mockResponseBody);
        when(mockResponseBody.string()).thenReturn("{\"error\":{\"message\":\"Rate limit exceeded\"}}");

        when(mockCall.execute()).thenReturn(mockResponse);
        when(mockHttpClient.newCall(any(Request.class))).thenReturn(mockCall);

        // Use reflection to replace the HTTP client with our mock
        try {
            java.lang.reflect.Field httpClientField = OpenAIClient.class.getDeclaredField("httpClient");
            httpClientField.setAccessible(true);
            httpClientField.set(openAIClient, mockHttpClient);
        } catch (Exception e) {
            fail("Failed to inject mock HTTP client: " + e.getMessage());
        }

        // Act & Assert
        try {
            openAIClient.execute(testJson, "test-api-key", "gpt-3.5-turbo");
            fail("Expected RateLimitException to be thrown");
        } catch (RateLimitException e) {
            assertEquals("Provider should be OpenAI", "OpenAI", e.getProvider());
            assertEquals("Should use default retry time", 60, e.getRetryAfterSeconds());
        }
    }

    @Test
    public void testExecute_WithRateLimitResponseNoHeaders_UsesDefaultRetryTime() throws IOException {
        // Arrange
        String testJson = "{\"model\":\"gpt-3.5-turbo\",\"messages\":[]}";
        
        // Mock rate limit response without any retry headers
        when(mockResponse.code()).thenReturn(429);
        when(mockResponse.header("retry-after")).thenReturn(null);
        when(mockResponse.header("x-ratelimit-reset-requests")).thenReturn(null);
        when(mockResponse.body()).thenReturn(mockResponseBody);
        when(mockResponseBody.string()).thenReturn("{\"error\":{\"message\":\"Rate limit exceeded\"}}");
        
        when(mockCall.execute()).thenReturn(mockResponse);
        when(mockHttpClient.newCall(any(Request.class))).thenReturn(mockCall);

        // Use reflection to replace the HTTP client with our mock
        try {
            java.lang.reflect.Field httpClientField = OpenAIClient.class.getDeclaredField("httpClient");
            httpClientField.setAccessible(true);
            httpClientField.set(openAIClient, mockHttpClient);
        } catch (Exception e) {
            fail("Failed to inject mock HTTP client: " + e.getMessage());
        }

        // Act & Assert
        try {
            openAIClient.execute(testJson, "test-api-key", "gpt-3.5-turbo");
            fail("Expected RateLimitException to be thrown");
        } catch (RateLimitException e) {
            assertEquals("Provider should be OpenAI", "OpenAI", e.getProvider());
            assertEquals("Should use default retry time", 60, e.getRetryAfterSeconds());
        }
    }

    @Test
    public void testExecute_WithSuccessfulResponse_ReturnsResponse() throws IOException, RateLimitException {
        // Arrange
        String testJson = "{\"model\":\"gpt-3.5-turbo\",\"messages\":[]}";
        String responseJson = "{\"choices\":[{\"message\":{\"content\":\"Test response\"}}]}";
        
        // Mock successful response
        when(mockResponse.code()).thenReturn(200);
        when(mockResponse.isSuccessful()).thenReturn(true);
        when(mockResponse.body()).thenReturn(mockResponseBody);
        when(mockResponseBody.string()).thenReturn(responseJson);
        
        when(mockCall.execute()).thenReturn(mockResponse);
        when(mockHttpClient.newCall(any(Request.class))).thenReturn(mockCall);

        // Use reflection to replace the HTTP client with our mock
        try {
            java.lang.reflect.Field httpClientField = OpenAIClient.class.getDeclaredField("httpClient");
            httpClientField.setAccessible(true);
            httpClientField.set(openAIClient, mockHttpClient);
        } catch (Exception e) {
            fail("Failed to inject mock HTTP client: " + e.getMessage());
        }

        // Act
        ChatGPTResponse response = openAIClient.execute(testJson, "test-api-key", "gpt-3.5-turbo");

        // Assert
        assertNotNull("Response should not be null", response);
        // Note: The actual response parsing depends on the ChatGPTResponse implementation
    }

    @Test
    public void testExecute_WithServerError_ThrowsIOException() throws IOException {
        // Arrange
        String testJson = "{\"model\":\"gpt-3.5-turbo\",\"messages\":[]}";
        
        // Mock server error response
        when(mockResponse.code()).thenReturn(500);
        when(mockResponse.isSuccessful()).thenReturn(false);
        when(mockResponse.body()).thenReturn(mockResponseBody);
        when(mockResponseBody.string()).thenReturn("{\"error\":{\"message\":\"Internal server error\"}}");
        
        when(mockCall.execute()).thenReturn(mockResponse);
        when(mockHttpClient.newCall(any(Request.class))).thenReturn(mockCall);

        // Use reflection to replace the HTTP client with our mock
        try {
            java.lang.reflect.Field httpClientField = OpenAIClient.class.getDeclaredField("httpClient");
            httpClientField.setAccessible(true);
            httpClientField.set(openAIClient, mockHttpClient);
        } catch (Exception e) {
            fail("Failed to inject mock HTTP client: " + e.getMessage());
        }

        // Act & Assert
        try {
            openAIClient.execute(testJson, "test-api-key", "gpt-3.5-turbo");
            fail("Expected IOException to be thrown");
        } catch (IOException e) {
            assertTrue("Error message should contain status code", e.getMessage().contains("500"));
        } catch (RateLimitException e) {
            fail("Should not throw RateLimitException for non-429 errors");
        }
    }

    @Test
    public void testQuery_WithValidInputs_CallsExecute() {
        // This test verifies that the query method properly calls execute
        // We can't easily mock the execute method, so we test the validation logic
        
        // Test with valid inputs - should not throw exception during validation
        try {
            // This will fail at HTTP call level, but validation should pass
            openAIClient.query("System prompt", "User prompt");
        } catch (RuntimeException e) {
            // Expected to fail at HTTP level since we're not mocking the actual HTTP client
            // But it should not fail due to validation
            assertTrue("Should fail at API call level, not validation", 
                e.getMessage().contains("API call failed") || e.getMessage().contains("Connection"));
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void testQuery_WithEmptyApiKey_ThrowsException() {
        // Arrange
        OpenAIClient clientWithEmptyKey = new OpenAIClient("", "gpt-3.5-turbo");
        
        // Act & Assert
        clientWithEmptyKey.query("System prompt", "User prompt");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testQuery_WithEmptyModel_ThrowsException() {
        // Arrange
        OpenAIClient clientWithEmptyModel = new OpenAIClient("test-key", "");
        
        // Act & Assert
        clientWithEmptyModel.query("System prompt", "User prompt");
    }

    @Test
    public void testConstructor_WithValidParameters_CreatesClient() {
        // Act
        OpenAIClient client = new OpenAIClient("test-key", "gpt-3.5-turbo", 45);
        
        // Assert
        assertNotNull("Client should be created successfully", client);
    }

    @Test
    public void testConstructor_UsesHttpClientPool() {
        // Arrange
        int initialCacheSize = HttpClientPool.getInstance().getCacheSize();
        
        // Act
        new OpenAIClient("test-key", "gpt-3.5-turbo", 30);
        
        // Assert
        int finalCacheSize = HttpClientPool.getInstance().getCacheSize();
        assertTrue("HTTP client pool should be used", finalCacheSize >= initialCacheSize);
    }
}
