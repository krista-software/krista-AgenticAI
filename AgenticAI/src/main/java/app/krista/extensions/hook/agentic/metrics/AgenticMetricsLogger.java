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

package app.krista.extensions.hook.agentic.metrics;

import app.krista.extensions.hook.util.JsonMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * File-based metrics logger for agentic flow validation events.
 * Writes structured JSON events to a file for later analysis.
 */
public class AgenticMetricsLogger {

    private static final Logger LOGGER = LoggerFactory.getLogger(AgenticMetricsLogger.class);

    private static final String DEFAULT_LOG_PATH = "./logs/agentic-metrics.jsonl";
    private static final DateTimeFormatter DATE_FORMATTER =
        DateTimeFormatter.ISO_INSTANT;
    
    private final Path logFilePath;
    private final BufferedWriter writer;
    private final Map<String, AtomicLong> counters = new ConcurrentHashMap<>();
    
    // Singleton instance
    private static volatile AgenticMetricsLogger instance;
    private static final Object lock = new Object();
    
    /**
     * Gets the singleton metrics logger instance.
     */
    public static AgenticMetricsLogger getInstance() {
        if (instance == null) {
            synchronized (lock) {
                if (instance == null) {
                    try {
                        instance = new AgenticMetricsLogger(DEFAULT_LOG_PATH);
                    } catch (IOException e) {
                        LOGGER.error("Failed to initialize metrics logger: {}", e.getMessage());
                        // Return a no-op logger that doesn't throw
                        instance = createNoOpLogger();
                    }
                }
            }
        }
        return instance;
    }
    
    /**
     * Creates a metrics logger with custom path.
     */
    public static AgenticMetricsLogger getInstance(String logPath) {
        if (instance == null) {
            synchronized (lock) {
                if (instance == null) {
                    try {
                        instance = new AgenticMetricsLogger(logPath);
                    } catch (IOException e) {
                        LOGGER.error("Failed to initialize metrics logger: {}", e.getMessage());
                        instance = createNoOpLogger();
                    }
                }
            }
        }
        return instance;
    }
    
    private static AgenticMetricsLogger createNoOpLogger() {
        return new AgenticMetricsLogger() {
            @Override
            public void logValidationFailed(String sessionId, String invalidToolName, int attempt) {
                // No-op
            }
            @Override
            public void logRetrySuccess(String sessionId, String validToolName, int attempt) {
                // No-op
            }
            @Override
            public void logMaxRetriesExceeded(String sessionId, String lastInvalidTool) {
                // No-op
            }
            @Override
            public long getCounter(String metric) {
                return 0;
            }
        };
    }
    
    // Private constructor for singleton
    private AgenticMetricsLogger(String logPath) throws IOException {
        this.logFilePath = Paths.get(logPath);
        
        // Create directories if they don't exist
        Files.createDirectories(logFilePath.getParent());
        
        // Open file for appending (create if doesn't exist)
        this.writer = Files.newBufferedWriter(
            logFilePath,
            StandardOpenOption.CREATE,
            StandardOpenOption.APPEND,
            StandardOpenOption.WRITE
        );
        
        // Initialize counters
        counters.put("validation_failed", new AtomicLong(0));
        counters.put("retry_success", new AtomicLong(0));
        counters.put("max_retries_exceeded", new AtomicLong(0));
        
        LOGGER.info("AgenticMetricsLogger initialized: {}", logFilePath.toAbsolutePath());
    }
    
    // Protected constructor for no-op subclass
    protected AgenticMetricsLogger() {
        this.logFilePath = null;
        this.writer = null;
    }
    
    /**
     * Logs a validation failure event.
     */
    public void logValidationFailed(String sessionId, String invalidToolName, int attempt) {
        counters.get("validation_failed").incrementAndGet();
        
        MetricsEvent event = new MetricsEvent(
            DATE_FORMATTER.format(Instant.now()),
            "validation_failed",
            sessionId,
            attempt,
            invalidToolName,
            null
        );
        
        writeEvent(event);
    }
    
    /**
     * Logs a successful retry event.
     */
    public void logRetrySuccess(String sessionId, String validToolName, int attempt) {
        counters.get("retry_success").incrementAndGet();
        
        MetricsEvent event = new MetricsEvent(
            DATE_FORMATTER.format(Instant.now()),
            "retry_success",
            sessionId,
            attempt,
            null,
            validToolName
        );
        
        writeEvent(event);
    }
    
    /**
     * Logs max retries exceeded event.
     */
    public void logMaxRetriesExceeded(String sessionId, String lastInvalidTool) {
        counters.get("max_retries_exceeded").incrementAndGet();
        
        MetricsEvent event = new MetricsEvent(
            DATE_FORMATTER.format(Instant.now()),
            "max_retries_exceeded",
            sessionId,
            null,
            lastInvalidTool,
            null
        );
        
        writeEvent(event);
    }
    
    /**
     * Gets current counter value for a metric.
     */
    public long getCounter(String metric) {
        AtomicLong counter = counters.get(metric);
        return counter != null ? counter.get() : 0;
    }
    
    /**
     * Gets all current counters.
     */
    public Map<String, Long> getAllCounters() {
        Map<String, Long> result = new ConcurrentHashMap<>();
        counters.forEach((key, value) -> result.put(key, value.get()));
        return result;
    }
    
    private void writeEvent(MetricsEvent event) {
        if (writer == null) {
            return; // No-op logger
        }
        
        try {
            String json = JsonMapper.getInstance().writeValueAsString(event);
            synchronized (writer) {
                writer.write(json);
                writer.newLine();
                writer.flush();  // Ensure immediate write for test reliability
            }
        } catch (IOException e) {
            LOGGER.error("Failed to write metrics event: {}", e.getMessage());
        }
    }
    
    /**
     * Closes the logger and releases resources.
     */
    public void close() {
        if (writer != null) {
            try {
                writer.close();
            } catch (IOException e) {
                LOGGER.error("Error closing metrics logger: {}", e.getMessage());
            }
        }
    }
    
    /**
     * Gets the log file path.
     */
    public String getLogFilePath() {
        return logFilePath != null ? logFilePath.toString() : "no-op";
    }
    
    /**
     * Resets the singleton instance. Package-private for testing only.
     */
    /**
     * Resets the singleton instance. Package-private for testing only.
     */
    static void resetInstance() {
        synchronized (lock) {
            if (instance != null) {
                try {
                    instance.close();
                } catch (Exception e) {
                    // Ignore
                }
                instance = null;
            }
        }
    }
    
    /**
     * Metrics event data structure.
     */
    public static class MetricsEvent {
        private final String timestamp;
        private final String eventType;
        private final String sessionId;
        private final Integer attempt;
        private final String invalidToolName;
        private final String validToolName;
        
        public MetricsEvent(String timestamp, String eventType, String sessionId,
                           Integer attempt, String invalidToolName, String validToolName) {
            this.timestamp = timestamp;
            this.eventType = eventType;
            this.sessionId = sessionId;
            this.attempt = attempt;
            this.invalidToolName = invalidToolName;
            this.validToolName = validToolName;
        }
        
        // Getters
        public String getTimestamp() { return timestamp; }
        public String getEventType() { return eventType; }
        public String getSessionId() { return sessionId; }
        public Integer getAttempt() { return attempt; }
        public String getInvalidToolName() { return invalidToolName; }
        public String getValidToolName() { return validToolName; }
    }
}
