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

import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe tracker for rate limiting status of different LLM providers.
 * Prevents API calls when we know a provider is rate limited.
 */
public class RateLimitTracker {
    
    // Singleton instance
    private static volatile RateLimitTracker instance;
    private static final Object INSTANCE_LOCK = new Object();
    
    // Track rate limit status per provider
    private final ConcurrentHashMap<String, Long> rateLimitedUntil;
    
    private RateLimitTracker() {
        this.rateLimitedUntil = new ConcurrentHashMap<>();
    }
    
    /**
     * Get the singleton instance of RateLimitTracker
     * 
     * @return RateLimitTracker instance
     */
    public static RateLimitTracker getInstance() {
        if (instance == null) {
            synchronized (INSTANCE_LOCK) {
                if (instance == null) {
                    instance = new RateLimitTracker();
                }
            }
        }
        return instance;
    }
    
    /**
     * Check if a provider is currently rate limited
     * 
     * @param provider Provider name (e.g., "openai", "claude")
     * @return true if provider is rate limited, false otherwise
     */
    public boolean isRateLimited(String provider) {
        Long rateLimitedUntilTime = rateLimitedUntil.get(provider.toLowerCase());
        if (rateLimitedUntilTime == null) {
            return false;
        }
        
        long currentTime = System.currentTimeMillis();
        if (currentTime >= rateLimitedUntilTime) {
            // Rate limit has expired, remove it
            rateLimitedUntil.remove(provider.toLowerCase());
            return false;
        }
        
        return true;
    }
    
    /**
     * Mark a provider as rate limited until a specific time
     * 
     * @param provider Provider name
     * @param retryAfterSeconds Number of seconds to wait before retrying
     */
    public void markRateLimited(String provider, long retryAfterSeconds) {
        long rateLimitedUntilTime = System.currentTimeMillis() + (retryAfterSeconds * 1000);
        rateLimitedUntil.put(provider.toLowerCase(), rateLimitedUntilTime);
        
        System.out.println("RateLimitTracker: Marked " + provider + " as rate limited until " + 
            new java.util.Date(rateLimitedUntilTime) + " (" + retryAfterSeconds + " seconds)");
    }
    
    /**
     * Get remaining time in seconds until rate limit expires
     * 
     * @param provider Provider name
     * @return Remaining seconds, or 0 if not rate limited
     */
    public long getRemainingRateLimitSeconds(String provider) {
        Long rateLimitedUntilTime = rateLimitedUntil.get(provider.toLowerCase());
        if (rateLimitedUntilTime == null) {
            return 0;
        }
        
        long currentTime = System.currentTimeMillis();
        if (currentTime >= rateLimitedUntilTime) {
            rateLimitedUntil.remove(provider.toLowerCase());
            return 0;
        }
        
        return (rateLimitedUntilTime - currentTime) / 1000;
    }

    /**
     * Clear all rate limit statuses
     */
    public void clearAllRateLimits() {
        rateLimitedUntil.clear();
        System.out.println("RateLimitTracker: Cleared all rate limits");
    }
    
    /**
     * Get current rate limit status for debugging
     * 
     * @return String representation of current rate limits
     */
    public String getStatus() {
        if (rateLimitedUntil.isEmpty()) {
            return "RateLimitTracker: No active rate limits";
        }
        
        StringBuilder status = new StringBuilder("RateLimitTracker: Active rate limits:\n");
        long currentTime = System.currentTimeMillis();
        
        rateLimitedUntil.forEach((provider, untilTime) -> {
            long remainingSeconds = Math.max(0, (untilTime - currentTime) / 1000);
            status.append("  - ").append(provider).append(": ").append(remainingSeconds).append(" seconds remaining\n");
        });
        
        return status.toString();
    }
}
