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

package app.krista.extensions.hook.agentic.usagecollector;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for UsageLogger file creation and daily rotation functionality.
 */
public class UsageLoggerTest {

    @TempDir
    Path tempDir;

    private static final String TEST_LOG_DIR = "logs/usage";

    @BeforeEach
    void setUp() {
        // Reset the logger to clear any static state between tests
        UsageLogger.reset();
    }

    @AfterEach
    void tearDown() throws IOException {
        // Clean up any log files created during tests
        String today = LocalDate.now().format(DateTimeFormatter.ISO_DATE);
        String yesterday = LocalDate.now().minusDays(1).format(DateTimeFormatter.ISO_DATE);
        
        // Clean up today's file
        String todayFileName = TEST_LOG_DIR + "/usage-" + today + ".jsonl";
        Path todayLogFile = Paths.get(todayFileName);
        if (Files.exists(todayLogFile)) {
            Files.delete(todayLogFile);
        }
        
        // Clean up yesterday's file
        String yesterdayFileName = TEST_LOG_DIR + "/usage-" + yesterday + ".jsonl";
        Path yesterdayLogFile = Paths.get(yesterdayFileName);
        if (Files.exists(yesterdayLogFile)) {
            Files.delete(yesterdayLogFile);
        }
        
        // Clean up directory if empty
        Path logDir = Paths.get(TEST_LOG_DIR);
        if (Files.exists(logDir) && Files.list(logDir).count() == 0) {
            Files.delete(logDir);
        }
    }

    @Test
    void testUsageLogFileCreation_CreatesFileWithCorrectName() throws IOException, InterruptedException {
        // Given
        String today = LocalDate.now().format(DateTimeFormatter.ISO_DATE);
        String sessionId = "sess_test_001";
        String agentName = "ToolPlanner";
        
        SessionUsageCollector collector = new SessionUsageCollector(sessionId, agentName);
        
        // Add a usage record using Builder
        TokenUsageRecord record = TokenUsageRecord.builder()
            .modelName("gpt-4o-mini")
            .apiKeyMasked("test-api-key")
            .agentName(agentName)
            .promptSummary("Test prompt")
            .fullPrompt("Test prompt content")
            .completionText("Test completion content")
            .promptTokens(100)
            .completionTokens(50)
            .totalTokens(150)
            .cost(0.0001)
            .requestDurationMs(1000L)
            .build();
        collector.addUsageRecord(record);
        collector.markCompleted();

        // When
        UsageLogger.logSessionUsage(collector);
        
        // Wait for file operations
        Thread.sleep(100);

        // Then
        String expectedFileName = TEST_LOG_DIR + "/usage-" + today + ".jsonl";
        Path logFile = Paths.get(expectedFileName);
        
        assertTrue(Files.exists(logFile), 
            "Usage log file should be created with today's date: " + expectedFileName);
        
        // Verify filename format
        String fileName = logFile.getFileName().toString();
        assertTrue(fileName.startsWith("usage-"), 
            "Filename should start with 'usage-'");
        assertTrue(fileName.contains(today), 
            "Filename should contain today's date: " + today);
        assertTrue(fileName.endsWith(".jsonl"), 
            "Filename should end with '.jsonl'");
    }

    @Test
    void testUsageLogFileCreation_WritesValidJson() throws IOException, InterruptedException {
        // Given
        String today = LocalDate.now().format(DateTimeFormatter.ISO_DATE);
        String sessionId = "sess_test_002";
        String agentName = "ToolPlanner";
        
        SessionUsageCollector collector = new SessionUsageCollector(sessionId, agentName);
        
        // Add usage records using Builder
        TokenUsageRecord record1 = TokenUsageRecord.builder()
            .modelName("gpt-4o-mini")
            .apiKeyMasked("test-api-key")
            .agentName(agentName)
            .promptSummary("First prompt")
            .fullPrompt("First prompt content")
            .completionText("First completion")
            .promptTokens(100)
            .completionTokens(50)
            .totalTokens(150)
            .cost(0.0001)
            .requestDurationMs(1000L)
            .build();
        collector.addUsageRecord(record1);
        
        TokenUsageRecord record2 = TokenUsageRecord.builder()
            .modelName("gpt-4o-mini")
            .apiKeyMasked("test-api-key")
            .agentName(agentName)
            .promptSummary("Second prompt")
            .fullPrompt("Second prompt content")
            .completionText("Second completion")
            .promptTokens(200)
            .completionTokens(100)
            .totalTokens(300)
            .cost(0.0002)
            .requestDurationMs(2000L)
            .build();
        collector.addUsageRecord(record2);
        
        collector.markCompleted();

        // When
        UsageLogger.logSessionUsage(collector);
        
        // Wait for file operations
        Thread.sleep(100);

        // Then
        String expectedFileName = TEST_LOG_DIR + "/usage-" + today + ".jsonl";
        Path logFile = Paths.get(expectedFileName);
        
        assertTrue(Files.exists(logFile), "Log file should exist");
        
        List<String> lines = Files.readAllLines(logFile);
        assertEquals(1, lines.size(), "File should contain exactly one JSON line");
        
        String jsonLine = lines.get(0);
        
        // Verify JSON contains expected fields
        assertTrue(jsonLine.contains("\"sessionId\":\"" + sessionId + "\""), 
            "JSON should contain sessionId");
        assertTrue(jsonLine.contains("\"agentName\":\"" + agentName + "\""), 
            "JSON should contain agentName");
        assertTrue(jsonLine.contains("\"totalLlmCalls\":2"), 
            "JSON should contain totalLlmCalls=2");
        assertTrue(jsonLine.contains("\"totalPromptTokens\":300"), 
            "JSON should contain totalPromptTokens=300 (100+200)");
        assertTrue(jsonLine.contains("\"totalCompletionTokens\":150"), 
            "JSON should contain totalCompletionTokens=150 (50+100)");
        assertTrue(jsonLine.contains("\"totalTokens\":450"), 
            "JSON should contain totalTokens=450 (150+300)");
        assertTrue(jsonLine.contains("\"status\":\"COMPLETED\""), 
            "JSON should contain status=COMPLETED");
        assertTrue(jsonLine.contains("\"timestamp\""), 
            "JSON should contain timestamp");
    }

    @Test
    void testUsageLogFileCreation_MultipleSessions() throws IOException, InterruptedException {
        // Given
        String today = LocalDate.now().format(DateTimeFormatter.ISO_DATE);
        
        // First session
        SessionUsageCollector collector1 = new SessionUsageCollector("sess_001", "ToolPlanner");
        TokenUsageRecord record1 = TokenUsageRecord.builder()
            .modelName("gpt-4o-mini")
            .apiKeyMasked("key1")
            .agentName("ToolPlanner")
            .promptSummary("Prompt 1")
            .fullPrompt("Content 1")
            .completionText("Completion 1")
            .promptTokens(100)
            .completionTokens(50)
            .totalTokens(150)
            .cost(0.0001)
            .requestDurationMs(1000L)
            .build();
        collector1.addUsageRecord(record1);
        collector1.markCompleted();
        
        // Second session
        SessionUsageCollector collector2 = new SessionUsageCollector("sess_002", "ToolPlanner");
        TokenUsageRecord record2 = TokenUsageRecord.builder()
            .modelName("gpt-4o-mini")
            .apiKeyMasked("key2")
            .agentName("ToolPlanner")
            .promptSummary("Prompt 2")
            .fullPrompt("Content 2")
            .completionText("Completion 2")
            .promptTokens(200)
            .completionTokens(100)
            .totalTokens(300)
            .cost(0.0002)
            .requestDurationMs(2000L)
            .build();
        collector2.addUsageRecord(record2);
        collector2.markCompleted();

        // When
        UsageLogger.logSessionUsage(collector1);
        UsageLogger.logSessionUsage(collector2);
        
        // Wait for file operations
        Thread.sleep(100);

        // Then
        String expectedFileName = TEST_LOG_DIR + "/usage-" + today + ".jsonl";
        Path logFile = Paths.get(expectedFileName);
        
        assertTrue(Files.exists(logFile), "Log file should exist");
        
        List<String> lines = Files.readAllLines(logFile);
        assertEquals(2, lines.size(), "File should contain 2 JSON lines (one per session)");
        
        // Verify both sessions are logged
        assertTrue(lines.get(0).contains("\"sessionId\":\"sess_001\""), 
            "First line should contain sess_001");
        assertTrue(lines.get(1).contains("\"sessionId\":\"sess_002\""), 
            "Second line should contain sess_002");
    }

    @Test
    void testUsageLogFileCreation_ErrorSession() throws IOException, InterruptedException {
        // Given
        String today = LocalDate.now().format(DateTimeFormatter.ISO_DATE);
        String sessionId = "sess_error_001";
        String agentName = "ToolPlanner";
        String errorMessage = "Test error message";
        
        SessionUsageCollector collector = new SessionUsageCollector(sessionId, agentName);
        
        // Add a usage record before error
        TokenUsageRecord record = TokenUsageRecord.builder()
            .modelName("gpt-4o-mini")
            .apiKeyMasked("test-api-key")
            .agentName(agentName)
            .promptSummary("Test prompt")
            .fullPrompt("Test prompt content")
            .completionText("Test completion content")
            .promptTokens(100)
            .completionTokens(50)
            .totalTokens(150)
            .cost(0.0001)
            .requestDurationMs(1000L)
            .build();
        collector.addUsageRecord(record);
        
        // Mark as errored
        collector.markError(errorMessage);

        // When
        UsageLogger.logSessionUsage(collector);
        
        // Wait for file operations
        Thread.sleep(100);

        // Then
        String expectedFileName = TEST_LOG_DIR + "/usage-" + today + ".jsonl";
        Path logFile = Paths.get(expectedFileName);
        
        assertTrue(Files.exists(logFile), "Log file should exist");
        
        List<String> lines = Files.readAllLines(logFile);
        assertEquals(1, lines.size(), "File should contain exactly one JSON line");
        
        String jsonLine = lines.get(0);
        assertTrue(jsonLine.contains("\"status\":\"ERROR\""), 
            "JSON should contain status=ERROR");
    }

    @Test
    void testUsageLogFileCreation_NoExceptions() {
        // Given
        SessionUsageCollector collector = new SessionUsageCollector("sess_test", "ToolPlanner");
        TokenUsageRecord record = TokenUsageRecord.builder()
            .modelName("gpt-4o-mini")
            .apiKeyMasked("key")
            .agentName("ToolPlanner")
            .promptSummary("Prompt")
            .fullPrompt("Content")
            .completionText("Completion")
            .promptTokens(100)
            .completionTokens(50)
            .totalTokens(150)
            .cost(0.0001)
            .requestDurationMs(1000L)
            .build();
        collector.addUsageRecord(record);
        collector.markCompleted();

        // When & Then - Should not throw any exceptions
        assertDoesNotThrow(() -> {
            UsageLogger.logSessionUsage(collector);
        }, "Logging usage should not throw exceptions");
    }
}
