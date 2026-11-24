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
 * Test class for HttpClientPool to verify client caching, thread safety,
 * and connection pool optimization functionality.
 */
public class HttpClientPoolTest {

    private HttpClientPool httpClientPool;

    @Before
    public void setUp() {
        httpClientPool = HttpClientPool.getInstance();
        // Clear cache before each test to ensure clean state
        httpClientPool.clearCache();
    }

    @Test
    public void testGetInstance_ReturnsSameInstance() {
        // Act
        HttpClientPool instance1 = HttpClientPool.getInstance();
        HttpClientPool instance2 = HttpClientPool.getInstance();

        // Assert
        assertSame("getInstance should return the same singleton instance", instance1, instance2);
    }

    @Test
    public void testGetClient_WithSameConfiguration_ReturnsSameClient() {
        // Act
        OkHttpClient client1 = httpClientPool.getClient("openai", 30);
        OkHttpClient client2 = httpClientPool.getClient("openai", 30);

        // Assert
        assertSame("Same configuration should return cached client", client1, client2);
        assertEquals("Cache size should be 1", 1, httpClientPool.getCacheSize());
    }

    @Test
    public void testGetClient_WithDifferentProvider_ReturnsDifferentClients() {
        // Act
        OkHttpClient openaiClient = httpClientPool.getClient("openai", 30);
        OkHttpClient claudeClient = httpClientPool.getClient("claude", 30);

        // Assert
        assertNotSame("Different providers should return different clients", openaiClient, claudeClient);
        assertEquals("Cache size should be 2", 2, httpClientPool.getCacheSize());
    }

    @Test
    public void testGetClient_WithDifferentTimeout_ReturnsDifferentClients() {
        // Act
        OkHttpClient client30s = httpClientPool.getClient("openai", 30);
        OkHttpClient client60s = httpClientPool.getClient("openai", 60);

        // Assert
        assertNotSame("Different timeouts should return different clients", client30s, client60s);
        assertEquals("Cache size should be 2", 2, httpClientPool.getCacheSize());
    }

    @Test
    public void testGetClient_VerifyTimeoutConfiguration() {
        // Act
        OkHttpClient client = httpClientPool.getClient("test", 45);

        // Assert
        assertEquals("Connect timeout should match configuration", 45, client.connectTimeoutMillis() / 1000);
        assertEquals("Read timeout should match configuration", 45, client.readTimeoutMillis() / 1000);
        assertEquals("Write timeout should match configuration", 45, client.writeTimeoutMillis() / 1000);
    }

    @Test
    public void testGetClient_VerifyConnectionPoolOptimization() {
        // Act
        OkHttpClient client = httpClientPool.getClient("test", 30);

        // Assert
        assertNotNull("Client should have connection pool", client.connectionPool());
        assertFalse("Client should not retry on connection failure", client.retryOnConnectionFailure());
    }

    @Test
    public void testClearCache_RemovesAllCachedClients() {
        // Arrange
        httpClientPool.getClient("openai", 30);
        httpClientPool.getClient("claude", 30);
        httpClientPool.getClient("krista", 30);
        assertEquals("Cache should have 3 clients", 3, httpClientPool.getCacheSize());

        // Act
        httpClientPool.clearCache();

        // Assert
        assertEquals("Cache should be empty after clear", 0, httpClientPool.getCacheSize());
    }

    @Test
    public void testClearCache_NewClientsCreatedAfterClear() {
        // Arrange
        OkHttpClient originalClient = httpClientPool.getClient("openai", 30);
        httpClientPool.clearCache();

        // Act
        OkHttpClient newClient = httpClientPool.getClient("openai", 30);

        // Assert
        assertNotSame("New client should be created after cache clear", originalClient, newClient);
        assertEquals("Cache size should be 1", 1, httpClientPool.getCacheSize());
    }

    @Test
    public void testConcurrentAccess_ThreadSafety() throws InterruptedException {
        // Arrange
        int threadCount = 10;
        int requestsPerThread = 5;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);

        // Act - Multiple threads requesting same client configuration
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    for (int j = 0; j < requestsPerThread; j++) {
                        OkHttpClient client = httpClientPool.getClient("concurrent-test", 30);
                        assertNotNull("Client should not be null", client);
                        successCount.incrementAndGet();
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        // Assert
        assertTrue("All threads should complete within timeout", latch.await(10, TimeUnit.SECONDS));
        assertEquals("All requests should succeed", threadCount * requestsPerThread, successCount.get());
        assertEquals("Only one client should be cached for same configuration", 1, httpClientPool.getCacheSize());

        executor.shutdown();
    }

    @Test
    public void testConcurrentAccess_DifferentConfigurations() throws InterruptedException {
        // Arrange
        int threadCount = 6;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);

        // Act - Multiple threads requesting different client configurations
        for (int i = 0; i < threadCount; i++) {
            final int threadIndex = i;
            executor.submit(() -> {
                try {
                    String provider = "provider-" + (threadIndex % 3); // 3 different providers
                    int timeout = 30 + (threadIndex % 2) * 30; // 2 different timeouts (30, 60)
                    
                    OkHttpClient client = httpClientPool.getClient(provider, timeout);
                    assertNotNull("Client should not be null", client);
                    successCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        // Assert
        assertTrue("All threads should complete within timeout", latch.await(10, TimeUnit.SECONDS));
        assertEquals("All requests should succeed", threadCount, successCount.get());
        // Should have 6 different clients (3 providers × 2 timeouts)
        assertEquals("Should have 6 different cached clients", 6, httpClientPool.getCacheSize());

        executor.shutdown();
    }

    @Test
    public void testConcurrentCacheClear_ThreadSafety() throws InterruptedException {
        // Arrange
        int threadCount = 5;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        // Pre-populate cache
        httpClientPool.getClient("test1", 30);
        httpClientPool.getClient("test2", 30);

        // Act - Multiple threads clearing cache and getting clients
        for (int i = 0; i < threadCount; i++) {
            final int threadIndex = i;
            executor.submit(() -> {
                try {
                    if (threadIndex % 2 == 0) {
                        // Even threads clear cache
                        httpClientPool.clearCache();
                    } else {
                        // Odd threads get clients
                        OkHttpClient client = httpClientPool.getClient("concurrent-clear-test", 30);
                        assertNotNull("Client should not be null", client);
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        // Assert
        assertTrue("All threads should complete within timeout", latch.await(10, TimeUnit.SECONDS));
        // Cache size should be consistent (no exceptions should occur)
        assertTrue("Cache size should be non-negative", httpClientPool.getCacheSize() >= 0);

        executor.shutdown();
    }
}
