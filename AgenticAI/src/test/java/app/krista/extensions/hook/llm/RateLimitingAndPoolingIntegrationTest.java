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

import app.krista.extensions.hook.llm.claude.ClaudeClient;
import app.krista.extensions.hook.llm.krista.KristaClient;
import app.krista.extensions.hook.llm.openai.OpenAIClient;
import okhttp3.OkHttpClient;
import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

/**
 * Integration test class to verify that rate limiting and HTTP client pooling
 * work correctly together under concurrent load conditions.
 */
public class RateLimitingAndPoolingIntegrationTest {

    private HttpClientPool httpClientPool;
    private RateLimitTracker rateLimitTracker;

    @Before
    public void setUp() {
        httpClientPool = HttpClientPool.getInstance();
        rateLimitTracker = RateLimitTracker.getInstance();
        
        // Clear state before each test
        httpClientPool.clearCache();
        rateLimitTracker.clearAllRateLimits();
    }

    @Test
    public void testHttpClientPooling_UnderConcurrentLoad_MaintainsCorrectCacheSize() throws InterruptedException {
        // Arrange
        int threadCount = 20;
        int requestsPerThread = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger clientCreationCount = new AtomicInteger(0);

        // Act - Multiple threads creating clients with same configurations
        for (int i = 0; i < threadCount; i++) {
            final int threadIndex = i;
            executor.submit(() -> {
                try {
                    for (int j = 0; j < requestsPerThread; j++) {
                        // Create clients with limited set of configurations
                        String provider = "provider-" + (threadIndex % 3); // 3 different providers
                        int timeout = 30 + ((threadIndex + j) % 2) * 30; // 2 different timeouts (30, 60)
                        
                        OkHttpClient client = httpClientPool.getClient(provider, timeout);
                        assertNotNull("Client should not be null", client);
                        clientCreationCount.incrementAndGet();
                        
                        // Verify timeout configuration
                        assertEquals("Timeout should match configuration", timeout, client.connectTimeoutMillis() / 1000);
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        // Assert
        assertTrue("All threads should complete within timeout", latch.await(15, TimeUnit.SECONDS));
        assertEquals("All client requests should succeed", threadCount * requestsPerThread, clientCreationCount.get());
        
        // Should have at most 6 cached clients (3 providers × 2 timeouts)
        int cacheSize = httpClientPool.getCacheSize();
        assertTrue("Cache size should be reasonable", cacheSize <= 6);
        assertTrue("Cache size should be positive", cacheSize > 0);

        executor.shutdown();
    }

    @Test
    public void testRateLimitTracker_UnderConcurrentLoad_MaintainsConsistentState() throws InterruptedException {
        // Arrange
        int threadCount = 15;
        int operationsPerThread = 30;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger operationCount = new AtomicInteger(0);

        // Act - Multiple threads performing rate limit operations
        for (int i = 0; i < threadCount; i++) {
            final int threadIndex = i;
            executor.submit(() -> {
                try {
                    for (int j = 0; j < operationsPerThread; j++) {
                        String provider = "provider-" + (threadIndex % 5); // 5 different providers
                        
                        // Perform various operations
                        if (j % 3 == 0) {
                            // Mark as rate limited
                            rateLimitTracker.markRateLimited(provider, 60 + j);
                        } else if (j % 3 == 1) {
                            // Check if rate limited
                            boolean isLimited = rateLimitTracker.isRateLimited(provider);
                            // Result doesn't matter, just ensure no exceptions
                        } else {
                            // Get remaining time
                            long remaining = rateLimitTracker.getRemainingRateLimitSeconds(provider);
                            assertTrue("Remaining time should be non-negative", remaining >= 0);
                        }
                        
                        operationCount.incrementAndGet();
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        // Assert
        assertTrue("All threads should complete within timeout", latch.await(15, TimeUnit.SECONDS));
        assertEquals("All operations should succeed", threadCount * operationsPerThread, operationCount.get());

        executor.shutdown();
    }

    @Test
    public void testLLMClientCreation_WithPoolingAndRateLimit_WorksCorrectly() throws InterruptedException {
        // Arrange
        int threadCount = 12;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);

        // Mark some providers as rate limited
        rateLimitTracker.markRateLimited("openai", 30);

        // Act - Multiple threads creating different LLM clients
        for (int i = 0; i < threadCount; i++) {
            final int threadIndex = i;
            executor.submit(() -> {
                try {
                    // Create different types of clients
                    if (threadIndex % 3 == 0) {
                        // Create Krista clients
                        KristaClient kristaClient = new KristaClient("test-key", "test-model", 30);
                        assertNotNull("Krista client should be created", kristaClient);
                    } else if (threadIndex % 3 == 1) {
                        // Create OpenAI clients (some rate limited)
                        OpenAIClient openAIClient = new OpenAIClient("test-key", "gpt-3.5-turbo", 30);
                        assertNotNull("OpenAI client should be created", openAIClient);
                        
                        // Check if provider is rate limited
                        boolean isRateLimited = rateLimitTracker.isRateLimited("openai");
                        assertTrue("OpenAI should be rate limited", isRateLimited);
                    } else {
                        // Create Claude clients
                        ClaudeClient claudeClient = new ClaudeClient("test-key", "claude-3-sonnet", 30);
                        assertNotNull("Claude client should be created", claudeClient);
                    }
                    
                    successCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        // Assert
        assertTrue("All threads should complete within timeout", latch.await(10, TimeUnit.SECONDS));
        assertEquals("All client creations should succeed", threadCount, successCount.get());
        
        // Verify HTTP client pool has cached clients
        assertTrue("HTTP client pool should have cached clients", httpClientPool.getCacheSize() > 0);

        executor.shutdown();
    }

    @Test
    public void testRateLimitExpiration_WithConcurrentAccess_WorksCorrectly() throws InterruptedException {
        // Arrange
        String provider = "test-provider";
        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger rateLimitedCount = new AtomicInteger(0);
        AtomicInteger notRateLimitedCount = new AtomicInteger(0);

        // Mark provider as rate limited for short duration
        rateLimitTracker.markRateLimited(provider, 2);

        // Act - Multiple threads checking rate limit status over time
        for (int i = 0; i < threadCount; i++) {
            final int threadIndex = i;
            executor.submit(() -> {
                try {
                    // Stagger the checks over time
                    Thread.sleep(threadIndex * 300); // 0-2.7 seconds
                    
                    boolean isRateLimited = rateLimitTracker.isRateLimited(provider);
                    if (isRateLimited) {
                        rateLimitedCount.incrementAndGet();
                    } else {
                        notRateLimitedCount.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    latch.countDown();
                }
            });
        }

        // Assert
        assertTrue("All threads should complete within timeout", latch.await(5, TimeUnit.SECONDS));
        
        // Some threads should see rate limited, others should not (due to expiration)
        assertTrue("Some checks should see rate limited state", rateLimitedCount.get() > 0);
        assertTrue("Some checks should see non-rate limited state", notRateLimitedCount.get() > 0);
        assertEquals("All checks should complete", threadCount, rateLimitedCount.get() + notRateLimitedCount.get());

        executor.shutdown();
    }

    @Test
    public void testHttpClientPoolClearance_WithConcurrentAccess_ThreadSafe() throws InterruptedException {
        // Arrange
        int threadCount = 8;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger operationCount = new AtomicInteger(0);

        // Pre-populate cache
        httpClientPool.getClient("test1", 30);
        httpClientPool.getClient("test2", 60);

        // Act - Some threads clear cache, others get clients
        for (int i = 0; i < threadCount; i++) {
            final int threadIndex = i;
            executor.submit(() -> {
                try {
                    for (int j = 0; j < 10; j++) {
                        if ((threadIndex + j) % 4 == 0) {
                            // Clear cache
                            httpClientPool.clearCache();
                        } else {
                            // Get client
                            OkHttpClient client = httpClientPool.getClient("concurrent-test-" + j, 30);
                            assertNotNull("Client should not be null", client);
                        }
                        operationCount.incrementAndGet();
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        // Assert
        assertTrue("All threads should complete within timeout", latch.await(10, TimeUnit.SECONDS));
        assertEquals("All operations should succeed", threadCount * 10, operationCount.get());
        
        // Cache should be in consistent state (no exceptions should occur)
        assertTrue("Cache size should be non-negative", httpClientPool.getCacheSize() >= 0);

        executor.shutdown();
    }

    @Test
    public void testCombinedStressTest_RateLimitingAndPooling_UnderHighLoad() throws InterruptedException {
        // Arrange
        int threadCount = 25;
        int operationsPerThread = 20;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger totalOperations = new AtomicInteger(0);

        // Act - High load test combining both rate limiting and HTTP client pooling
        for (int i = 0; i < threadCount; i++) {
            final int threadIndex = i;
            executor.submit(() -> {
                try {
                    for (int j = 0; j < operationsPerThread; j++) {
                        String provider = "provider-" + (threadIndex % 4);
                        int timeout = 30 + ((threadIndex + j) % 3) * 15; // 30, 45, 60 seconds
                        
                        // Perform mixed operations
                        if (j % 5 == 0) {
                            // Mark rate limited
                            rateLimitTracker.markRateLimited(provider, 30 + j);
                        } else if (j % 5 == 1) {
                            // Check rate limit
                            boolean isLimited = rateLimitTracker.isRateLimited(provider);
                            // Result doesn't matter for this test
                        } else if (j % 5 == 2) {
                            // Clear all rate limits occasionally
                            if (threadIndex % 10 == 0) {
                                rateLimitTracker.clearAllRateLimits();
                            }
                        } else if (j % 5 == 3) {
                            // Get HTTP client
                            OkHttpClient client = httpClientPool.getClient(provider, timeout);
                            assertNotNull("Client should not be null", client);
                        } else {
                            // Clear HTTP client cache occasionally
                            if (threadIndex % 15 == 0) {
                                httpClientPool.clearCache();
                            }
                        }
                        
                        totalOperations.incrementAndGet();
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        // Assert
        assertTrue("All threads should complete within timeout", latch.await(20, TimeUnit.SECONDS));
        assertEquals("All operations should complete", threadCount * operationsPerThread, totalOperations.get());
        
        // Both systems should be in consistent state
        assertTrue("HTTP client cache size should be non-negative", httpClientPool.getCacheSize() >= 0);
        
        // Rate limit tracker should be functional
        String status = rateLimitTracker.getStatus();
        assertNotNull("Rate limit status should not be null", status);

        executor.shutdown();
    }
}
