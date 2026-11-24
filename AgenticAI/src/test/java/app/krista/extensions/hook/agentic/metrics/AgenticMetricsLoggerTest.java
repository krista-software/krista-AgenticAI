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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for AgenticMetricsLogger.
 */
public class AgenticMetricsLoggerTest {

    @TempDir
    Path tempDir;
    
    private AgenticMetricsLogger logger;
    private Path logFile;

    @BeforeEach
    void setUp() throws IOException {
        // Reset singleton for clean tests
        AgenticMetricsLogger.resetInstance();
        
        logFile = tempDir.resolve("test-metrics.jsonl");
        logger = AgenticMetricsLogger.getInstance(logFile.toString());
    }

    @AfterEach
    void tearDown() {
        // Reset singleton to clean up resources
        AgenticMetricsLogger.resetInstance();
    }
    
    /**
     * Waits for a file to be written by checking for content
     * @param path Path to the file
     * @param timeoutMs Maximum time to wait in milliseconds
     * @throws InterruptedException if interrupted while waiting
     */
    private void waitForFileToBeWritten(String path, long timeoutMs) throws InterruptedException {
        long startTime = System.currentTimeMillis();
        while (System.currentTimeMillis() - startTime < timeoutMs) {
            try {
                if (Files.exists(Paths.get(path))) {
                    List<String> lines = Files.readAllLines(Paths.get(path));
                    if (!lines.isEmpty()) {
                        return; // File has content
                    }
                }
                Thread.sleep(100);
            } catch (IOException e) {
                // File not ready yet
            }
        }
        // Timeout - still try to read
    }
    

    @Test
    @DisplayName("logValidationFailed - should increment counter")
    void testLogValidationFailed_IncrementsCounter() {
        long initialCount = logger.getCounter("validation_failed");
        
        logger.logValidationFailed("sess_123", "{\"invalid\":\"tool\"}", 1);
        
        assertEquals(initialCount + 1, logger.getCounter("validation_failed"));
    }

    @Test
    @DisplayName("logValidationFailed - should write to file")
    void testLogValidationFailed_WritesToFile() throws Exception {
        logger.logValidationFailed("sess_123", "{\"invalid\":\"tool\"}", 1);

        // Wait for file to be written
        waitForFileToBeWritten(logFile.toString(), 1000);

        List<String> lines = Files.readAllLines(logFile);
        assertFalse(lines.isEmpty(), "File should not be empty");

        String json = lines.get(0);
        assertTrue(json.contains("validation_failed"), "Missing event type");
        assertTrue(json.contains("sess_123"), "Missing session ID");
        assertTrue(json.contains("\\\"invalid\\\":\\\"tool\\\""), "Missing tool data");
        assertTrue(json.contains("\"attempt\":1"), "Missing attempt count");
    }

    @Test
    @DisplayName("logRetrySuccess - should increment counter")
    void testLogRetrySuccess_IncrementsCounter() {
        long initialCount = logger.getCounter("retry_success");
        
        logger.logRetrySuccess("sess_123", "{\"valid\":\"tool\"}", 2);
        
        assertEquals(initialCount + 1, logger.getCounter("retry_success"));
    }

    @Test
    @DisplayName("logRetrySuccess - should write to file")
    void testLogRetrySuccess_WritesToFile() throws Exception {
        logger.logRetrySuccess("sess_123", "{\"valid\":\"tool\"}", 2);

        // Wait for file to be written
        waitForFileToBeWritten(logFile.toString(), 1000);

        List<String> lines = Files.readAllLines(logFile);
        assertFalse(lines.isEmpty(), "File should not be empty");

        String json = lines.get(0);
        assertTrue(json.contains("retry_success"), "Missing event type");
        assertTrue(json.contains("sess_123"), "Missing session ID");
        assertTrue(json.contains("\\\"valid\\\":\\\"tool\\\""), "Missing tool data");
        assertTrue(json.contains("\"attempt\":2"), "Missing attempt count");
    }

    @Test
    @DisplayName("logMaxRetriesExceeded - should increment counter")
    void testLogMaxRetriesExceeded_IncrementsCounter() {
        long initialCount = logger.getCounter("max_retries_exceeded");
        
        logger.logMaxRetriesExceeded("sess_123", "{\"last\":\"invalid\"}");
        
        assertEquals(initialCount + 1, logger.getCounter("max_retries_exceeded"));
    }

    @Test
    @DisplayName("logMaxRetriesExceeded - should write to file")
    void testLogMaxRetriesExceeded_WritesToFile() throws Exception {
        logger.logMaxRetriesExceeded("sess_123", "{\"last\":\"invalid\"}");

        // Wait for file to be written
        waitForFileToBeWritten(logFile.toString(), 1000);

        List<String> lines = Files.readAllLines(logFile);
        assertFalse(lines.isEmpty(), "File should not be empty");

        String json = lines.get(0);
        assertTrue(json.contains("max_retries_exceeded"), "Missing event type");
        assertTrue(json.contains("sess_123"), "Missing session ID");
        assertTrue(json.contains("\\\"last\\\":\\\"invalid\\\""), "Missing tool data");
    }

    @Test
    @DisplayName("Multiple events - should write all to file")
    void testMultipleEvents() throws Exception {
        logger.logValidationFailed("sess_1", "invalid1", 1);
        logger.logValidationFailed("sess_1", "invalid2", 2);
        logger.logRetrySuccess("sess_1", "valid", 3);

        // Wait for file to be written
        waitForFileToBeWritten(logFile.toString(), 1000);

        List<String> lines = Files.readAllLines(logFile);
        assertEquals(3, lines.size(), "Should have exactly 3 events");

        assertTrue(lines.get(0).contains("validation_failed"), "First event should be validation_failed");
        assertTrue(lines.get(1).contains("validation_failed"), "Second event should be validation_failed");
        assertTrue(lines.get(2).contains("retry_success"), "Third event should be retry_success");
    }

    @Test
    @DisplayName("getAllCounters - should return all counter values")
    void testGetAllCounters() {
        logger.logValidationFailed("sess_1", "invalid", 1);
        logger.logValidationFailed("sess_2", "invalid", 1);
        logger.logRetrySuccess("sess_1", "valid", 2);
        logger.logMaxRetriesExceeded("sess_3", "invalid");
        
        Map<String, Long> counters = logger.getAllCounters();
        
        assertEquals(2, counters.get("validation_failed"));
        assertEquals(1, counters.get("retry_success"));
        assertEquals(1, counters.get("max_retries_exceeded"));
    }

    @Test
    @DisplayName("getCounter - should return 0 for unknown metric")
    void testGetCounter_UnknownMetric() {
        assertEquals(0, logger.getCounter("unknown_metric"));
    }

    @Test
    @DisplayName("Log file path - should return correct path")
    void testGetLogFilePath() {
        assertEquals(logFile.toString(), logger.getLogFilePath());
    }

    @Test
    @DisplayName("Timestamp format - should be ISO-8601")
    void testTimestampFormat() throws Exception {
        logger.logValidationFailed("sess_123", "invalid", 1);

        // Wait for file to be written
        waitForFileToBeWritten(logFile.toString(), 1000);

        List<String> lines = Files.readAllLines(logFile);
        assertFalse(lines.isEmpty(), "File should not be empty");

        String json = lines.get(0);
        // Should contain ISO-8601 timestamp (e.g., 2026-01-31T10:30:00Z) with optional fractional seconds
        assertTrue(json.matches(".*\"timestamp\":\"\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d+)?Z\".*"), "Timestamp should be in ISO-8601 format");
    }

    @Test
    @DisplayName("Null tool names - should handle gracefully")
    void testNullToolNames() throws Exception {
        logger.logValidationFailed("sess_123", null, 1);

        // Wait for file to be written
        waitForFileToBeWritten(logFile.toString(), 1000);

        List<String> lines = Files.readAllLines(logFile);
        assertEquals(1, lines.size(), "Should have exactly 1 event");
        assertTrue(lines.get(0).contains("null"), "Should contain null value");
    }

    @Test
    @DisplayName("Concurrent logging - should handle multiple threads")
    void testConcurrentLogging() throws Exception {
        int threadCount = 10;
        int eventsPerThread = 10;
        int expectedTotal = threadCount * eventsPerThread;

        Thread[] threads = new Thread[threadCount];
        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            threads[i] = new Thread(() -> {
                for (int j = 0; j < eventsPerThread; j++) {
                    logger.logValidationFailed("sess_" + threadId, "invalid", j + 1);
                }
            });
            threads[i].start();
        }

        // Wait for all threads to complete
        for (Thread thread : threads) {
            thread.join();
        }

        // Wait for all lines to be written
        waitForFileToBeWritten(logFile.toString(), 5000);

        // Verify all events were written
        List<String> lines = Files.readAllLines(logFile);
        assertEquals(expectedTotal, lines.size(), "Should have exactly " + expectedTotal + " events from concurrent threads");
    }
}
