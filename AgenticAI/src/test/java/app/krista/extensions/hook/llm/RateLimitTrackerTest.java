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

import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

/**
 * Test class for RateLimitTracker to verify rate limit tracking, expiration,
 * and thread safety functionality.
 */
public class RateLimitTrackerTest {

    private RateLimitTracker rateLimitTracker;

    @Before
    public void setUp() {
        rateLimitTracker = RateLimitTracker.getInstance();
        // Clear all rate limits before each test
        rateLimitTracker.clearAllRateLimits();
    }

    @Test
    public void testGetInstance_ReturnsSameInstance() {
        // Act
        RateLimitTracker instance1 = RateLimitTracker.getInstance();
        RateLimitTracker instance2 = RateLimitTracker.getInstance();

        // Assert
        assertSame("getInstance should return the same singleton instance", instance1, instance2);
    }

    @Test
    public void testIsRateLimited_WithNoRateLimit_ReturnsFalse() {
        // Act & Assert
        assertFalse("Provider with no rate limit should return false", rateLimitTracker.isRateLimited("openai"));
        assertFalse("Provider with no rate limit should return false", rateLimitTracker.isRateLimited("claude"));
    }

    @Test
    public void testMarkRateLimited_AndIsRateLimited_ReturnsTrue() {
        // Arrange
        String provider = "openai";
        long retryAfterSeconds = 60;

        // Act
        rateLimitTracker.markRateLimited(provider, retryAfterSeconds);

        // Assert
        assertTrue("Provider should be rate limited", rateLimitTracker.isRateLimited(provider));
    }

    @Test
    public void testMarkRateLimited_CaseInsensitive() {
        // Arrange
        String provider = "OpenAI";
        long retryAfterSeconds = 30;

        // Act
        rateLimitTracker.markRateLimited(provider, retryAfterSeconds);

        // Assert
        assertTrue("Provider should be rate limited (case insensitive)", rateLimitTracker.isRateLimited("openai"));
        assertTrue("Provider should be rate limited (case insensitive)", rateLimitTracker.isRateLimited("OPENAI"));
        assertTrue("Provider should be rate limited (case insensitive)", rateLimitTracker.isRateLimited("OpenAI"));
    }

    @Test
    public void testGetRemainingRateLimitSeconds_WithActiveRateLimit() {
        // Arrange
        String provider = "claude";
        long retryAfterSeconds = 120;

        // Act
        rateLimitTracker.markRateLimited(provider, retryAfterSeconds);
        long remainingSeconds = rateLimitTracker.getRemainingRateLimitSeconds(provider);

        // Assert
        assertTrue("Remaining seconds should be positive", remainingSeconds > 0);
        assertTrue("Remaining seconds should be close to original", remainingSeconds <= retryAfterSeconds);
        assertTrue("Remaining seconds should be reasonable", remainingSeconds >= retryAfterSeconds - 5); // Allow for test execution time
    }

    @Test
    public void testGetRemainingRateLimitSeconds_WithNoRateLimit_ReturnsZero() {
        // Act & Assert
        assertEquals("No rate limit should return 0 seconds", 0, rateLimitTracker.getRemainingRateLimitSeconds("openai"));
    }

    @Test
    public void testRateLimitExpiration_AutomaticallyRemoved() throws InterruptedException {
        // Arrange
        String provider = "test-provider";
        long retryAfterSeconds = 1; // Short duration for test

        // Act
        rateLimitTracker.markRateLimited(provider, retryAfterSeconds);
        assertTrue("Provider should be initially rate limited", rateLimitTracker.isRateLimited(provider));

        // Wait for rate limit to expire
        Thread.sleep(1100); // Wait slightly longer than rate limit duration

        // Assert
        assertFalse("Provider should no longer be rate limited", rateLimitTracker.isRateLimited(provider));
        assertEquals("Remaining seconds should be 0", 0, rateLimitTracker.getRemainingRateLimitSeconds(provider));
    }

    @Test
    public void testMultipleProviders_IndependentRateLimits() {
        // Arrange
        String provider1 = "openai";
        String provider2 = "claude";
        String provider3 = "krista";

        // Act
        rateLimitTracker.markRateLimited(provider1, 60);
        rateLimitTracker.markRateLimited(provider2, 120);
        // provider3 is not rate limited

        // Assert
        assertTrue("Provider1 should be rate limited", rateLimitTracker.isRateLimited(provider1));
        assertTrue("Provider2 should be rate limited", rateLimitTracker.isRateLimited(provider2));
        assertFalse("Provider3 should not be rate limited", rateLimitTracker.isRateLimited(provider3));

        // Check remaining times are different
        long remaining1 = rateLimitTracker.getRemainingRateLimitSeconds(provider1);
        long remaining2 = rateLimitTracker.getRemainingRateLimitSeconds(provider2);
        long remaining3 = rateLimitTracker.getRemainingRateLimitSeconds(provider3);

        assertTrue("Provider1 remaining time should be positive", remaining1 > 0);
        assertTrue("Provider2 remaining time should be positive", remaining2 > 0);
        assertEquals("Provider3 remaining time should be 0", 0, remaining3);
        assertTrue("Provider2 should have more remaining time", remaining2 > remaining1);
    }

    @Test
    public void testClearAllRateLimits_RemovesAllLimits() {
        // Arrange
        rateLimitTracker.markRateLimited("openai", 60);
        rateLimitTracker.markRateLimited("claude", 120);
        rateLimitTracker.markRateLimited("krista", 30);

        // Verify they are rate limited
        assertTrue("OpenAI should be rate limited", rateLimitTracker.isRateLimited("openai"));
        assertTrue("Claude should be rate limited", rateLimitTracker.isRateLimited("claude"));
        assertTrue("Krista should be rate limited", rateLimitTracker.isRateLimited("krista"));

        // Act
        rateLimitTracker.clearAllRateLimits();

        // Assert
        assertFalse("OpenAI should not be rate limited", rateLimitTracker.isRateLimited("openai"));
        assertFalse("Claude should not be rate limited", rateLimitTracker.isRateLimited("claude"));
        assertFalse("Krista should not be rate limited", rateLimitTracker.isRateLimited("krista"));
    }

    @Test
    public void testGetStatus_WithNoRateLimits() {
        // Act
        String status = rateLimitTracker.getStatus();

        // Assert
        assertTrue("Status should indicate no active rate limits", 
            status.contains("No active rate limits"));
    }

    @Test
    public void testGetStatus_WithActiveRateLimits() {
        // Arrange
        rateLimitTracker.markRateLimited("openai", 60);
        rateLimitTracker.markRateLimited("claude", 120);

        // Act
        String status = rateLimitTracker.getStatus();

        // Assert
        assertTrue("Status should contain provider names", status.contains("openai"));
        assertTrue("Status should contain provider names", status.contains("claude"));
        assertTrue("Status should contain remaining time info", status.contains("seconds remaining"));
    }

    @Test
    public void testConcurrentAccess_ThreadSafety() throws InterruptedException {
        // Arrange
        int threadCount = 10;
        int operationsPerThread = 20;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);

        // Act - Multiple threads performing rate limit operations
        for (int i = 0; i < threadCount; i++) {
            final int threadIndex = i;
            executor.submit(() -> {
                try {
                    for (int j = 0; j < operationsPerThread; j++) {
                        String provider = "provider-" + (threadIndex % 3); // 3 different providers
                        
                        // Perform various operations
                        rateLimitTracker.markRateLimited(provider, 30 + j);
                        boolean isLimited = rateLimitTracker.isRateLimited(provider);
                        long remaining = rateLimitTracker.getRemainingRateLimitSeconds(provider);
                        
                        // Verify operations don't throw exceptions
                        assertTrue("Remaining time should be non-negative", remaining >= 0);
                        successCount.incrementAndGet();
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        // Assert
        assertTrue("All threads should complete within timeout", latch.await(10, TimeUnit.SECONDS));
        assertEquals("All operations should succeed", threadCount * operationsPerThread, successCount.get());

        executor.shutdown();
    }

    @Test
    public void testConcurrentClearAndAccess_ThreadSafety() throws InterruptedException {
        // Arrange
        int threadCount = 8;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);

        // Act - Some threads clear, others access rate limits
        for (int i = 0; i < threadCount; i++) {
            final int threadIndex = i;
            executor.submit(() -> {
                try {
                    if (threadIndex % 3 == 0) {
                        // Clear all rate limits
                        rateLimitTracker.clearAllRateLimits();
                    } else if (threadIndex % 3 == 1) {
                        // Mark rate limited
                        rateLimitTracker.markRateLimited("concurrent-test", 60);
                    } else {
                        // Check rate limit status
                        boolean isLimited = rateLimitTracker.isRateLimited("concurrent-test");
                        long remaining = rateLimitTracker.getRemainingRateLimitSeconds("concurrent-test");
                        assertTrue("Remaining time should be non-negative", remaining >= 0);
                    }
                    successCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        // Assert
        assertTrue("All threads should complete within timeout", latch.await(10, TimeUnit.SECONDS));
        assertEquals("All operations should succeed", threadCount, successCount.get());

        executor.shutdown();
    }

    @Test
    public void testUpdateRateLimit_OverwritesPrevious() {
        // Arrange
        String provider = "test-provider";
        
        // Act
        rateLimitTracker.markRateLimited(provider, 60);
        long firstRemaining = rateLimitTracker.getRemainingRateLimitSeconds(provider);
        
        rateLimitTracker.markRateLimited(provider, 120); // Update with longer duration
        long secondRemaining = rateLimitTracker.getRemainingRateLimitSeconds(provider);

        // Assert
        assertTrue("First remaining time should be around 60 seconds", firstRemaining <= 60 && firstRemaining > 55);
        assertTrue("Second remaining time should be around 120 seconds", secondRemaining <= 120 && secondRemaining > 115);
        assertTrue("Second remaining time should be greater", secondRemaining > firstRemaining);
    }
}
