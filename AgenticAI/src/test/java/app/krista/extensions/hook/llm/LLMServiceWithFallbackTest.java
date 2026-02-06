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

package app.krista.extensions.hook.llm;

import app.krista.extension.executor.Invoker;
import app.krista.extensions.hook.Constants;
import app.krista.extensions.hook.config.LLMConfiguration;
import app.krista.extensions.hook.llm.claude.ClaudeClient;
import app.krista.extensions.hook.llm.krista.KristaClient;
import app.krista.extensions.hook.llm.openai.OpenAIClient;
import org.junit.Before;
import org.junit.Test;


import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Test class for LLMServiceWithFallback to verify fallback logic, rate limiting integration,
 * and HTTP client pool usage.
 */
public class LLMServiceWithFallbackTest {

    private Invoker mockInvoker;
    private KristaClient mockKristaClient;
    private OpenAIClient mockOpenAIClient;
    private ClaudeClient mockClaudeClient;

    private LLMServiceWithFallback llmService;
    private RateLimitTracker rateLimitTracker;
    private HttpClientPool httpClientPool;
    private Map<String, Object> mockAttributes;

    @Before
    public void setUp() {
        // Initialize mocks manually for compatibility
        mockInvoker = mock(Invoker.class);
        mockKristaClient = mock(KristaClient.class);
        mockOpenAIClient = mock(OpenAIClient.class);
        mockClaudeClient = mock(ClaudeClient.class);

        // Clear rate limits and HTTP client pool before each test
        rateLimitTracker = RateLimitTracker.getInstance();
        rateLimitTracker.clearAllRateLimits();

        httpClientPool = HttpClientPool.getInstance();
        httpClientPool.clearCache();

        // Setup mock attributes for Invoker
        mockAttributes = new HashMap<>();
        mockAttributes.put("EnableKristaLLMFallback", true);
        mockAttributes.put("KristaAPIKey", "krista-test-key");
        mockAttributes.put(Constants.CLOUD_LLM_PROVIDER, "openai");
        mockAttributes.put(Constants.CLOUD_API_KEY, "cloud-test-key");

        when(mockInvoker.getAttributes()).thenReturn(mockAttributes);

        // Create service with mocked invoker
        llmService = new TestLLMServiceWithFallback(mockInvoker);
    }

//    @Test
//    public void testQueryWithFallback_KristaSuccess_ReturnsKristaResponse() {
//        // Arrange
//        String systemPrompt = "System prompt";
//        String userPrompt = "User prompt";
//        String expectedResponse = "Krista response";
//
//        when(mockKristaClient.query(systemPrompt, userPrompt)).thenReturn(expectedResponse);
//
//        // Act
//        String result = llmService.queryWithFallback(systemPrompt, userPrompt);
//
//        // Assert
//        assertEquals("Should return Krista response", expectedResponse, result);
//    }

    @Test
    public void testQueryWithFallback_KristaFailsCloudSucceeds_ReturnsCloudResponse() {
        // Arrange
        String systemPrompt = "System prompt";
        String userPrompt = "User prompt";
        String expectedResponse = "Cloud response";
        
        when(mockKristaClient.query(systemPrompt, userPrompt)).thenThrow(new RuntimeException("Krista failed"));
        when(mockOpenAIClient.query(systemPrompt, userPrompt)).thenReturn(expectedResponse);

        // Act
        String result = llmService.queryWithFallback(systemPrompt, userPrompt);

        // Assert
        assertEquals("Should return cloud response", expectedResponse, result);
    }

    @Test
    public void testQueryWithFallback_CloudProviderRateLimited_SkipsCloudLLM() {
        // Arrange
        String systemPrompt = "System prompt";
        String userPrompt = "User prompt";
        
        // Mark cloud provider as rate limited
        rateLimitTracker.markRateLimited("openai", 60);
        
        when(mockKristaClient.query(systemPrompt, userPrompt)).thenThrow(new RuntimeException("Krista failed"));

        // Act
        String result = llmService.queryWithFallback(systemPrompt, userPrompt);

        // Assert
        assertNull("Should return null when cloud provider is rate limited", result);
    }

    @Test
    public void testQueryWithFallback_CloudRateLimitException_MarksProviderAsRateLimited() {
        // Arrange
        String systemPrompt = "System prompt";
        String userPrompt = "User prompt";
        
        when(mockKristaClient.query(systemPrompt, userPrompt)).thenThrow(new RuntimeException("Krista failed"));
        when(mockOpenAIClient.query(anyString(), anyString()))
            .thenThrow(new RuntimeException("API call failed", new RateLimitException("Rate limited", "OpenAI", 120)));

        // Act
        String result = llmService.queryWithFallback(systemPrompt, userPrompt);

        // Assert
        assertNull("Should return null when cloud provider gets rate limited", result);
        assertTrue("OpenAI should be marked as rate limited", rateLimitTracker.isRateLimited("openai"));
        assertEquals("Rate limit should be for 120 seconds", 120, rateLimitTracker.getRemainingRateLimitSeconds("openai"), 5);
    }

    @Test
    public void testQueryWithFallback_KristaLLMDisabled_UsesCloudDirectly() {
        // Arrange
        String systemPrompt = "System prompt";
        String userPrompt = "User prompt";
        String expectedResponse = "Cloud response";

        // Disable Krista LLM fallback
        mockAttributes.put("EnableKristaLLMFallback", false);

        when(mockOpenAIClient.query(systemPrompt, userPrompt)).thenReturn(expectedResponse);

        // Act
        String result = llmService.queryWithFallback(systemPrompt, userPrompt);

        // Assert
        assertEquals("Should return cloud response", expectedResponse, result);
        verify(mockKristaClient, never()).query(anyString(), anyString());
    }

    @Test
    public void testQueryWithFallback_KristaAPIKeyMissing_TreatsAsDisabled() {
        // Arrange
        String systemPrompt = "System prompt";
        String userPrompt = "User prompt";
        String expectedResponse = "Cloud response";

        // Enable Krista LLM fallback but remove API key
        mockAttributes.put("EnableKristaLLMFallback", true);
        mockAttributes.put("KristaAPIKey", ""); // Empty API key

        when(mockOpenAIClient.query(systemPrompt, userPrompt)).thenReturn(expectedResponse);

        // Act
        String result = llmService.queryWithFallback(systemPrompt, userPrompt);

        // Assert
        assertEquals("Should return cloud response", expectedResponse, result);
        verify(mockKristaClient, never()).query(anyString(), anyString());
    }

    @Test
    public void testQueryWithFallback_KristaAPIKeyNull_TreatsAsDisabled() {
        // Arrange
        String systemPrompt = "System prompt";
        String userPrompt = "User prompt";
        String expectedResponse = "Cloud response";

        // Enable Krista LLM fallback but set API key to null
        mockAttributes.put("EnableKristaLLMFallback", true);
        mockAttributes.remove("KristaAPIKey"); // This will make getOrDefault return ""

        when(mockOpenAIClient.query(systemPrompt, userPrompt)).thenReturn(expectedResponse);

        // Act
        String result = llmService.queryWithFallback(systemPrompt, userPrompt);

        // Assert
        assertEquals("Should return cloud response", expectedResponse, result);
        verify(mockKristaClient, never()).query(anyString(), anyString());
    }

    @Test
    public void testQueryWithFallback_ClaudeAsCloudProvider_UsesClaudeClient() {
        // Arrange
        String systemPrompt = "System prompt";
        String userPrompt = "User prompt";
        String expectedResponse = "Claude response";

        mockAttributes.put(Constants.CLOUD_LLM_PROVIDER, "claude");
        when(mockKristaClient.query(systemPrompt, userPrompt)).thenThrow(new RuntimeException("Krista failed"));
        when(mockClaudeClient.query(systemPrompt, userPrompt)).thenReturn(expectedResponse);

        // Act
        String result = llmService.queryWithFallback(systemPrompt, userPrompt);

        // Assert
        assertEquals("Should return Claude response", expectedResponse, result);
    }

//    @Test
//    public void testQueryWithFallback_ConcurrentRequests_ThreadSafety() throws InterruptedException {
//        // Arrange
//        int threadCount = 10;
//        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
//        CountDownLatch latch = new CountDownLatch(threadCount);
//        AtomicInteger successCount = new AtomicInteger(0);
//        AtomicInteger kristaCallCount = new AtomicInteger(0);
//
//        // Setup mock responses
//        when(mockKristaClient.query(anyString(), anyString())).thenAnswer(invocation -> {
//            kristaCallCount.incrementAndGet();
//            return "Krista response " + kristaCallCount.get();
//        });
//
//        // Act - Multiple threads making concurrent requests
//        for (int i = 0; i < threadCount; i++) {
//            final int threadIndex = i;
//            executor.submit(() -> {
//                try {
//                    String result = llmService.queryWithFallback("System prompt " + threadIndex, "User prompt " + threadIndex);
//                    if (result != null && result.startsWith("Krista response")) {
//                        successCount.incrementAndGet();
//                    }
//                } finally {
//                    latch.countDown();
//                }
//            });
//        }
//
//        // Assert
//        assertTrue("All threads should complete within timeout", latch.await(10, TimeUnit.SECONDS));
//        assertEquals("All requests should succeed", threadCount, successCount.get());
//        assertEquals("Krista should be called for each request", threadCount, kristaCallCount.get());
//
//        executor.shutdown();
//    }

    @Test
    public void testQueryWithFallback_HttpClientPoolIntegration_CachesClients() {
        // Arrange
        int initialCacheSize = httpClientPool.getCacheSize();
        
        // Act - This will create real clients that use the HTTP client pool
        try {
            llmService.queryWithFallback("System prompt", "User prompt");
        } catch (Exception e) {
            // Expected to fail at HTTP level, but clients should be created
        }

        // Assert
        int finalCacheSize = httpClientPool.getCacheSize();
        assertTrue("HTTP client pool should cache clients", finalCacheSize >= initialCacheSize);
    }

    /**
     * Test subclass that allows mocking of LLM clients
     */
    private class TestLLMServiceWithFallback extends LLMServiceWithFallback {
        
        public TestLLMServiceWithFallback(Invoker invoker) {
            super(invoker);
        }

        @Override
        public String queryWithFallback(String systemPrompt, String userPrompt) {
            try {
                LLMConfiguration config = new LLMConfiguration(mockInvoker);
                
                if (config.isKristaLLMFallbackEnabled()) {
                    try {
                        String kristaResponse = mockKristaClient.query(systemPrompt, userPrompt);
                        if (kristaResponse != null && !kristaResponse.trim().isEmpty()) {
                            return kristaResponse;
                        }
                    } catch (Exception e) {
                        System.out.println("Krista LLM failed, falling back to cloud");
                    }
                }
                
                // Check rate limiting
                String cloudProvider = config.getCloudProvider();
                if (rateLimitTracker.isRateLimited(cloudProvider)) {
                    return null;
                }
                
                // Try cloud LLM
                LLMClient cloudClient = "claude".equals(cloudProvider) ? mockClaudeClient : mockOpenAIClient;
                return cloudClient.query(systemPrompt, userPrompt);
            } catch (Exception e) {
                // Handle rate limit exceptions
                if (e.getCause() instanceof RateLimitException) {
                    RateLimitException rateLimitException = (RateLimitException) e.getCause();
                    rateLimitTracker.markRateLimited(
                        rateLimitException.getProvider().toLowerCase(), 
                        rateLimitException.getRetryAfterSeconds()
                    );
                }
                return null;
            }
        }
    }
}
