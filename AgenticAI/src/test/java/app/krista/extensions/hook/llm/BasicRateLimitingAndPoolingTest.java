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
 * Basic integration test for rate limiting and HTTP client pooling functionality.
 * Tests only public APIs and supported features without reflection or private method access.
 */
public class BasicRateLimitingAndPoolingTest {

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
    public void testHttpClientPool_BasicFunctionality() {
        // Test singleton pattern
        HttpClientPool instance1 = HttpClientPool.getInstance();
        HttpClientPool instance2 = HttpClientPool.getInstance();
        assertSame("Should return same singleton instance", instance1, instance2);

        // Test client caching
        OkHttpClient client1 = httpClientPool.getClient("test-provider", 30);
        OkHttpClient client2 = httpClientPool.getClient("test-provider", 30);
        assertSame("Same configuration should return cached client", client1, client2);

        // Test different configurations create different clients
        OkHttpClient client3 = httpClientPool.getClient("test-provider", 60);
        assertNotSame("Different timeout should create different client", client1, client3);

        // Test cache size
        assertTrue("Cache should have clients", httpClientPool.getCacheSize() > 0);

        // Test cache clearing
        httpClientPool.clearCache();
        assertEquals("Cache should be empty after clear", 0, httpClientPool.getCacheSize());
    }

    @Test
    public void testRateLimitTracker_BasicFunctionality() {
        // Test singleton pattern
        RateLimitTracker instance1 = RateLimitTracker.getInstance();
        RateLimitTracker instance2 = RateLimitTracker.getInstance();
        assertSame("Should return same singleton instance", instance1, instance2);

        // Test rate limiting
        String provider = "test-provider";
        assertFalse("Provider should not be rate limited initially", rateLimitTracker.isRateLimited(provider));
        assertEquals("Remaining time should be 0", 0, rateLimitTracker.getRemainingRateLimitSeconds(provider));

        // Mark as rate limited
        rateLimitTracker.markRateLimited(provider, 60);
        assertTrue("Provider should be rate limited", rateLimitTracker.isRateLimited(provider));
        assertTrue("Remaining time should be positive", rateLimitTracker.getRemainingRateLimitSeconds(provider) > 0);

        // Test case insensitivity
        assertTrue("Should be case insensitive", rateLimitTracker.isRateLimited("TEST-PROVIDER"));

        // Test clearing
        rateLimitTracker.clearAllRateLimits();
        assertFalse("Provider should not be rate limited after clear", rateLimitTracker.isRateLimited(provider));
    }

    @Test
    public void testLLMClientCreation_UsesHttpClientPool() {
        // Test that creating LLM clients uses the HTTP client pool
        int initialCacheSize = httpClientPool.getCacheSize();

        // Create different types of clients
        try {
            new KristaClient("test-key", "test-model", 30);
        } catch (Exception e) {
            // Expected to fail due to invalid key/connection, but should still create HTTP client
        }

        try {
            new OpenAIClient("test-key", "gpt-3.5-turbo", 30);
        } catch (Exception e) {
            // Expected to fail due to invalid key/connection, but should still create HTTP client
        }

        try {
            new ClaudeClient("test-key", "claude-3-sonnet", 30);
        } catch (Exception e) {
            // Expected to fail due to invalid key/connection, but should still create HTTP client
        }

        int finalCacheSize = httpClientPool.getCacheSize();
        assertTrue("HTTP client pool should have cached clients", finalCacheSize > initialCacheSize);
    }

    @Test
    public void testConcurrentHttpClientPoolAccess() throws InterruptedException {
        int threadCount = 10;
        int requestsPerThread = 5;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);

        // Multiple threads accessing HTTP client pool concurrently
        for (int i = 0; i < threadCount; i++) {
            final int threadIndex = i;
            executor.submit(() -> {
                try {
                    for (int j = 0; j < requestsPerThread; j++) {
                        String provider = "provider-" + (threadIndex % 3);
                        int timeout = 30 + (j % 2) * 30;
                        
                        OkHttpClient client = httpClientPool.getClient(provider, timeout);
                        assertNotNull("Client should not be null", client);
                        successCount.incrementAndGet();
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        assertTrue("All threads should complete", latch.await(10, TimeUnit.SECONDS));
        assertEquals("All requests should succeed", threadCount * requestsPerThread, successCount.get());
        assertTrue("Cache should have reasonable size", httpClientPool.getCacheSize() <= 6); // 3 providers × 2 timeouts

        executor.shutdown();
    }

    @Test
    public void testConcurrentRateLimitTrackerAccess() throws InterruptedException {
        int threadCount = 8;
        int operationsPerThread = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);

        // Multiple threads accessing rate limit tracker concurrently
        for (int i = 0; i < threadCount; i++) {
            final int threadIndex = i;
            executor.submit(() -> {
                try {
                    for (int j = 0; j < operationsPerThread; j++) {
                        String provider = "provider-" + (threadIndex % 4);
                        
                        // Perform various operations
                        if (j % 3 == 0) {
                            rateLimitTracker.markRateLimited(provider, 30 + j);
                        } else if (j % 3 == 1) {
                            boolean isLimited = rateLimitTracker.isRateLimited(provider);
                            // Just verify no exceptions occur
                        } else {
                            long remaining = rateLimitTracker.getRemainingRateLimitSeconds(provider);
                            assertTrue("Remaining time should be non-negative", remaining >= 0);
                        }
                        
                        successCount.incrementAndGet();
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        assertTrue("All threads should complete", latch.await(10, TimeUnit.SECONDS));
        assertEquals("All operations should succeed", threadCount * operationsPerThread, successCount.get());

        executor.shutdown();
    }

    @Test
    public void testRateLimitExpiration() throws InterruptedException {
        String provider = "expiration-test";
        
        // Mark as rate limited for short duration
        rateLimitTracker.markRateLimited(provider, 1);
        assertTrue("Should be rate limited initially", rateLimitTracker.isRateLimited(provider));
        
        // Wait for expiration
        Thread.sleep(1100);
        
        // Should no longer be rate limited
        assertFalse("Should not be rate limited after expiration", rateLimitTracker.isRateLimited(provider));
        assertEquals("Remaining time should be 0", 0, rateLimitTracker.getRemainingRateLimitSeconds(provider));
    }

    @Test
    public void testMultipleProviderRateLimiting() {
        String provider1 = "provider1";
        String provider2 = "provider2";
        String provider3 = "provider3";

        // Mark different providers with different rate limits
        rateLimitTracker.markRateLimited(provider1, 30);
        rateLimitTracker.markRateLimited(provider2, 60);
        // provider3 is not rate limited

        // Verify independent rate limiting
        assertTrue("Provider1 should be rate limited", rateLimitTracker.isRateLimited(provider1));
        assertTrue("Provider2 should be rate limited", rateLimitTracker.isRateLimited(provider2));
        assertFalse("Provider3 should not be rate limited", rateLimitTracker.isRateLimited(provider3));

        // Verify different remaining times
        long remaining1 = rateLimitTracker.getRemainingRateLimitSeconds(provider1);
        long remaining2 = rateLimitTracker.getRemainingRateLimitSeconds(provider2);
        long remaining3 = rateLimitTracker.getRemainingRateLimitSeconds(provider3);

        assertTrue("Provider1 should have remaining time", remaining1 > 0);
        assertTrue("Provider2 should have remaining time", remaining2 > 0);
        assertEquals("Provider3 should have no remaining time", 0, remaining3);
        assertTrue("Provider2 should have more remaining time", remaining2 > remaining1);
    }

    @Test
    public void testHttpClientConfiguration() {
        // Test that HTTP clients are configured correctly
        OkHttpClient client30 = httpClientPool.getClient("config-test", 30);
        OkHttpClient client60 = httpClientPool.getClient("config-test", 60);

        // Verify timeout configuration
        assertEquals("30s client should have correct timeout", 30, client30.connectTimeoutMillis() / 1000);
        assertEquals("60s client should have correct timeout", 60, client60.connectTimeoutMillis() / 1000);

        // Verify they are different instances
        assertNotSame("Different timeout configs should create different clients", client30, client60);

        // Verify connection pool is configured
        assertNotNull("Client should have connection pool", client30.connectionPool());
        assertNotNull("Client should have connection pool", client60.connectionPool());

        // Verify retry configuration
        assertFalse("Client should not retry on connection failure", client30.retryOnConnectionFailure());
        assertFalse("Client should not retry on connection failure", client60.retryOnConnectionFailure());
    }

    @Test
    public void testRateLimitStatus() {
        // Test status reporting
        String status = rateLimitTracker.getStatus();
        assertTrue("Status should indicate no active rate limits", status.contains("No active rate limits"));

        // Add some rate limits
        rateLimitTracker.markRateLimited("provider1", 30);
        rateLimitTracker.markRateLimited("provider2", 60);

        status = rateLimitTracker.getStatus();
        assertTrue("Status should contain provider1", status.contains("provider1"));
        assertTrue("Status should contain provider2", status.contains("provider2"));
        assertTrue("Status should contain remaining time info", status.contains("seconds remaining"));
    }

    @Test
    public void testStressTest_CombinedOperations() throws InterruptedException {
        int threadCount = 15;
        int operationsPerThread = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger totalOperations = new AtomicInteger(0);

        // Stress test combining both HTTP client pool and rate limiting
        for (int i = 0; i < threadCount; i++) {
            final int threadIndex = i;
            executor.submit(() -> {
                try {
                    for (int j = 0; j < operationsPerThread; j++) {
                        String provider = "stress-provider-" + (threadIndex % 3);
                        int timeout = 30 + ((threadIndex + j) % 2) * 15;
                        
                        // Mix of operations
                        if (j % 4 == 0) {
                            rateLimitTracker.markRateLimited(provider, 20 + j);
                        } else if (j % 4 == 1) {
                            boolean isLimited = rateLimitTracker.isRateLimited(provider);
                        } else if (j % 4 == 2) {
                            OkHttpClient client = httpClientPool.getClient(provider, timeout);
                            assertNotNull("Client should not be null", client);
                        } else {
                            long remaining = rateLimitTracker.getRemainingRateLimitSeconds(provider);
                            assertTrue("Remaining time should be non-negative", remaining >= 0);
                        }
                        
                        totalOperations.incrementAndGet();
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        assertTrue("All threads should complete", latch.await(15, TimeUnit.SECONDS));
        assertEquals("All operations should complete", threadCount * operationsPerThread, totalOperations.get());

        // Verify both systems are still functional
        assertTrue("HTTP client cache should be functional", httpClientPool.getCacheSize() >= 0);
        assertNotNull("Rate limit status should be available", rateLimitTracker.getStatus());

        executor.shutdown();
    }
}
