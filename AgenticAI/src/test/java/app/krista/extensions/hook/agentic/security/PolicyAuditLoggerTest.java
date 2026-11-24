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

package app.krista.extensions.hook.agentic.security;

import app.krista.extensions.hook.agentic.dto.PolicyCheckResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for PolicyAuditLogger.
 * Follows the same patterns as SecurityLoggerTest.
 */
public class PolicyAuditLoggerTest {

    private static final String LOG_DIR = "logs/security";

    @BeforeEach
    void setUp() {

        PolicyAuditLogger.reset();
    }

    @AfterEach
    void tearDown() throws IOException {

        PolicyAuditLogger.reset();

        String today = LocalDate.now().format(DateTimeFormatter.ISO_DATE);
        String fileName = LOG_DIR + "/policy-enforcement-" + today + ".jsonl";
        Path logFile = Paths.get(fileName);
        if (Files.exists(logFile)) {

            Files.delete(logFile);
        }

        Path logDir = Paths.get(LOG_DIR);
        if (Files.exists(logDir) && Files.list(logDir).count() == 0) {

            Files.delete(logDir);
        }
    }

    private Path getLogFilePath() {

        String today = LocalDate.now().format(DateTimeFormatter.ISO_DATE);
        return Paths.get(LOG_DIR + "/policy-enforcement-" + today + ".jsonl");
    }

    private boolean waitForFile(Path path) throws InterruptedException {

        for (int i = 0; i < 20; i++) {

            if (Files.exists(path)) {

                return true;
            }
            Thread.sleep(100);
        }
        return false;
    }

    @Test
    @DisplayName("log() should write ALLOWED event to JSONL file")
    void testLogAllowedEvent() throws IOException, InterruptedException {

        // Given
        String sessionId = "sess_policy_allowed_001";
        PolicyCheckResult allowed = PolicyCheckResult.allowed("Action complies with all policies");

        // When
        PolicyAuditLogger.log(sessionId, allowed, "create_order_tool");

        // Then
        Path logFile = getLogFilePath();
        assertTrue(waitForFile(logFile), "Log file should be created");

        List<String> lines = Files.readAllLines(logFile);
        assertEquals(1, lines.size(), "Should have exactly one log entry");

        String entry = lines.get(0);
        assertTrue(entry.contains("ALLOWED"), "Entry should contain ALLOWED event type");
        assertTrue(entry.contains(sessionId), "Entry should contain session ID");
        assertTrue(entry.contains("create_order_tool"), "Entry should contain tool name");

        // Verify valid JSON
        ObjectMapper mapper = new ObjectMapper();
        assertDoesNotThrow(() -> mapper.readTree(entry), "Log entry should be valid JSON");
    }

    @Test
    @DisplayName("log() should write BLOCKED event to JSONL file")
    void testLogBlockedEvent() throws IOException, InterruptedException {

        // Given
        String sessionId = "sess_policy_blocked_001";
        PolicyCheckResult blocked = PolicyCheckResult.blocked(
                "No external API calls",
                "Tool calls external API",
                "HIGH"
        );

        // When
        PolicyAuditLogger.log(sessionId, blocked, "external_api_tool");

        // Then
        Path logFile = getLogFilePath();
        assertTrue(waitForFile(logFile), "Log file should be created");

        List<String> lines = Files.readAllLines(logFile);
        assertEquals(1, lines.size(), "Should have exactly one log entry");

        String entry = lines.get(0);
        assertTrue(entry.contains("BLOCKED"), "Entry should contain BLOCKED event type");
        assertTrue(entry.contains("SELF_CORRECTION_TRIGGERED"), "Entry should contain action taken");
        assertTrue(entry.contains("No external API calls"), "Entry should contain violated policy");
        assertTrue(entry.contains("HIGH"), "Entry should contain severity");
    }

    @Test
    @DisplayName("logHardBlock() should write HARD_BLOCKED event")
    void testLogHardBlockEvent() throws IOException, InterruptedException {

        // Given
        String sessionId = "sess_hard_block_001";
        PolicyCheckResult blocked = PolicyCheckResult.blocked(
                "No external API calls",
                "Attempted call to external payment gateway",
                "HIGH"
        );

        // When
        PolicyAuditLogger.logHardBlock(sessionId, blocked);

        // Then
        Path logFile = getLogFilePath();
        assertTrue(waitForFile(logFile), "Log file should be created");

        List<String> lines = Files.readAllLines(logFile);
        String entry = lines.get(0);
        assertTrue(entry.contains("HARD_BLOCKED"), "Entry should contain HARD_BLOCKED event type");
        assertTrue(entry.contains("BLOCKED_NO_OVERRIDE"), "Entry should contain BLOCKED_NO_OVERRIDE action");
        assertTrue(entry.contains("No external API calls"), "Entry should contain violated policy");
        assertTrue(entry.contains("Attempted call to external payment gateway"), "Entry should contain reason");
    }

    @Test
    @DisplayName("logCheckerError() should write CHECKER_ERROR event")
    void testLogCheckerErrorEvent() throws IOException, InterruptedException {

        // Given
        String sessionId = "sess_checker_error_001";

        // When
        PolicyAuditLogger.logCheckerError(sessionId, "delete_tool", "LLM timeout after 30s");

        // Then
        Path logFile = getLogFilePath();
        assertTrue(waitForFile(logFile), "Log file should be created");

        List<String> lines = Files.readAllLines(logFile);
        String entry = lines.get(0);
        assertTrue(entry.contains("CHECKER_ERROR"), "Entry should contain CHECKER_ERROR event type");
        assertTrue(entry.contains("delete_tool"), "Entry should contain tool name");
        assertTrue(entry.contains("LLM timeout after 30s"), "Entry should contain error message");
        assertTrue(entry.contains("HIGH"), "Entry should contain HIGH severity");
    }

    @Test
    @DisplayName("Daily rotation should create file with correct date-based name")
    void testDailyRotationFileName() throws IOException, InterruptedException {

        // Given
        String sessionId = "sess_rotation_001";
        PolicyCheckResult allowed = PolicyCheckResult.allowed("OK");

        // When
        PolicyAuditLogger.log(sessionId, allowed, "some_tool");

        // Then
        String today = LocalDate.now().format(DateTimeFormatter.ISO_DATE);
        Path expectedFile = Paths.get(LOG_DIR + "/policy-enforcement-" + today + ".jsonl");
        assertTrue(waitForFile(expectedFile),
                "Log file should be named with today's date: " + expectedFile);
    }

    @Test
    @DisplayName("I/O failure should be swallowed and not propagate exceptions")
    void testIoFailureSwallowed() {

        // Given - a result that should be loggable regardless of I/O state
        PolicyCheckResult result = PolicyCheckResult.allowed("OK");

        // When/Then - should not throw even if something goes wrong internally
        assertDoesNotThrow(() -> {

            PolicyAuditLogger.log("sess_io_test", result, "tool");
        }, "I/O failure should be swallowed");
    }

    @Test
    @DisplayName("Concurrent writes should not lose entries")
    void testConcurrentWriteSafety() throws InterruptedException, IOException {

        // Given
        int threadCount = 10;
        CountDownLatch latch = new CountDownLatch(threadCount);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        // When - write from multiple threads simultaneously
        for (int i = 0; i < threadCount; i++) {

            final int idx = i;
            executor.submit(() -> {

                try {

                    PolicyCheckResult result = PolicyCheckResult.allowed("Concurrent test " + idx);
                    PolicyAuditLogger.log("sess_concurrent_" + idx, result, "tool_" + idx);
                } finally {

                    latch.countDown();
                }
            });
        }

        latch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        // Then - all entries should be written
        Path logFile = getLogFilePath();
        assertTrue(waitForFile(logFile), "Log file should be created");

        List<String> lines = Files.readAllLines(logFile);
        assertEquals(threadCount, lines.size(),
                "All " + threadCount + " concurrent writes should be present");

        // Verify each line is valid JSON
        ObjectMapper mapper = new ObjectMapper();
        for (String line : lines) {

            assertDoesNotThrow(() -> mapper.readTree(line),
                    "Each concurrent entry should be valid JSON");
        }
    }

    @Test
    @DisplayName("reset() should clear writers allowing fresh log files")
    void testResetClearsWriters() throws IOException, InterruptedException {

        // Given - write an entry
        PolicyAuditLogger.log("sess_reset_001", PolicyCheckResult.allowed("before reset"), "tool1");

        Path logFile = getLogFilePath();
        assertTrue(waitForFile(logFile), "Log file should be created before reset");

        // When
        PolicyAuditLogger.reset();

        // Then - writing again should still work (writers recreated)
        assertDoesNotThrow(() -> {

            PolicyAuditLogger.log("sess_reset_002", PolicyCheckResult.allowed("after reset"), "tool2");
        }, "Should be able to log after reset");
    }
}
