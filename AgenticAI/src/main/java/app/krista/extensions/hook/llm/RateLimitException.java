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

/**
 * Exception thrown when a rate limit is encountered (HTTP 429).
 * Contains information about when to retry based on server response headers.
 */
public class RateLimitException extends Exception {
    
    private final long retryAfterSeconds;
    private final String provider;
    
    /**
     * Create a rate limit exception with retry timing information
     * 
     * @param message Error message
     * @param provider LLM provider name (e.g., "OpenAI", "Claude", "Krista")
     * @param retryAfterSeconds Number of seconds to wait before retrying (from Retry-After header)
     */
    public RateLimitException(String message, String provider, long retryAfterSeconds) {
        super(message);
        this.provider = provider;
        this.retryAfterSeconds = retryAfterSeconds;
    }

    /**
     * Get the number of seconds to wait before retrying
     * 
     * @return Retry delay in seconds
     */
    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
    
    /**
     * Get the LLM provider that returned the rate limit
     * 
     * @return Provider name
     */
    public String getProvider() {
        return provider;
    }

}
