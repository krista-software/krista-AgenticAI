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

import okhttp3.ConnectionPool;
import okhttp3.OkHttpClient;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Thread-safe HTTP client pool for managing cached OkHttpClient instances.
 * Clients are cached based on their configuration to avoid creating multiple
 * clients with the same settings, improving performance and resource usage.
 */
public class HttpClientPool {
    
    // Singleton instance
    private static volatile HttpClientPool instance;
    private static final Object INSTANCE_LOCK = new Object();
    
    // Client cache with read-write lock for thread safety
    private final ConcurrentHashMap<String, OkHttpClient> clientCache;
    private final ReentrantReadWriteLock cacheLock;
    
    // Connection pool settings for optimal concurrent usage
    private static final int MAX_IDLE_CONNECTIONS = 20;
    private static final long KEEP_ALIVE_DURATION_MINUTES = 5;
    
    private HttpClientPool() {
        this.clientCache = new ConcurrentHashMap<>();
        this.cacheLock = new ReentrantReadWriteLock();
    }
    
    /**
     * Get the singleton instance of HttpClientPool
     * 
     * @return HttpClientPool instance
     */
    public static HttpClientPool getInstance() {
        if (instance == null) {
            synchronized (INSTANCE_LOCK) {
                if (instance == null) {
                    instance = new HttpClientPool();
                }
            }
        }
        return instance;
    }
    
    /**
     * Get or create an HTTP client with the specified configuration.
     * Clients are cached based on their configuration key.
     * 
     * @param provider Provider name (e.g., "krista", "openai", "claude")
     * @param timeoutSeconds Timeout configuration in seconds
     * @return Cached or new OkHttpClient instance
     */
    public OkHttpClient getClient(String provider, int timeoutSeconds) {
        String cacheKey = createCacheKey(provider, timeoutSeconds);
        
        // Try to get from cache first (read lock)
        cacheLock.readLock().lock();
        try {
            OkHttpClient cachedClient = clientCache.get(cacheKey);
            if (cachedClient != null) {
                return cachedClient;
            }
        } finally {
            cacheLock.readLock().unlock();
        }
        
        // Create new client if not in cache (write lock)
        cacheLock.writeLock().lock();
        try {
            // Double-check pattern - another thread might have created it
            OkHttpClient cachedClient = clientCache.get(cacheKey);
            if (cachedClient != null) {
                return cachedClient;
            }
            
            // Create new client with optimized connection pool
            OkHttpClient newClient = createOptimizedClient(timeoutSeconds);
            clientCache.put(cacheKey, newClient);
            
            System.out.println("HttpClientPool: Created new HTTP client for " + provider + 
                " with " + timeoutSeconds + "s timeout. Cache size: " + clientCache.size());
            
            return newClient;
        } finally {
            cacheLock.writeLock().unlock();
        }
    }
    
    /**
     * Clear the client cache. Useful when configuration changes require new clients.
     */
    public void clearCache() {
        cacheLock.writeLock().lock();
        try {
            System.out.println("HttpClientPool: Clearing client cache. Previous size: " + clientCache.size());
            clientCache.clear();
        } finally {
            cacheLock.writeLock().unlock();
        }
    }
    
    /**
     * Get current cache size for monitoring
     * 
     * @return Number of cached clients
     */
    public int getCacheSize() {
        cacheLock.readLock().lock();
        try {
            return clientCache.size();
        } finally {
            cacheLock.readLock().unlock();
        }
    }
    
    /**
     * Create cache key based on client configuration
     */
    private String createCacheKey(String provider, int timeoutSeconds) {
        return provider + "_timeout_" + timeoutSeconds;
    }
    
    /**
     * Create an optimized OkHttpClient for concurrent usage
     */
    private OkHttpClient createOptimizedClient(int timeoutSeconds) {
        // Create optimized connection pool for concurrent requests
        ConnectionPool connectionPool = new ConnectionPool(
            MAX_IDLE_CONNECTIONS, 
            KEEP_ALIVE_DURATION_MINUTES, 
            TimeUnit.MINUTES
        );
        
        return new OkHttpClient.Builder()
            .connectTimeout(timeoutSeconds, TimeUnit.SECONDS)
            .readTimeout(timeoutSeconds, TimeUnit.SECONDS)
            .writeTimeout(timeoutSeconds, TimeUnit.SECONDS)
            .connectionPool(connectionPool)
            .retryOnConnectionFailure(false) // We handle retries at service level
            .build();
    }
}
