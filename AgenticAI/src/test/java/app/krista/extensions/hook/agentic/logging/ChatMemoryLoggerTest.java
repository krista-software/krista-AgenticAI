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

package app.krista.extensions.hook.agentic.logging;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ChatMemoryLogger per-session file creation.
 */
public class ChatMemoryLoggerTest {

    private static final String LOG_DIR = "logs/chat-memory";

    @BeforeEach
    void setUp() {
        // Clean up any existing test files
        cleanupTestFiles();
    }

    @AfterEach
    void tearDown() {
        // Clean up test files after each test
        cleanupTestFiles();
    }

    private void cleanupTestFiles() {
        try {
            Path logDir = Paths.get(LOG_DIR);
            if (Files.exists(logDir)) {
                Files.list(logDir)
                    .filter(path -> path.getFileName().toString().startsWith("chat-memory-sess_test"))
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException e) {
                            // Ignore
                        }
                    });
            }
        } catch (IOException e) {
            // Ignore
        }
    }

    @Test
    void testChatMemoryLogger_CreatesFileForParentSession() throws IOException, InterruptedException {
        // Given
        String sessionId = "sess_test_parent_001";
        ChatMemory memory = MessageWindowChatMemory.withMaxMessages(10);
        memory.add(new SystemMessage("You are a helpful assistant"));
        memory.add(new UserMessage("Hello"));
        memory.add(new AiMessage("Hi there!"));

        // When
        ChatMemoryLogger.logChatMemory(sessionId, memory, "IN_PROGRESS");
        Thread.sleep(100);

        // Then
        String expectedFileName = LOG_DIR + "/chat-memory-" + sessionId + ".jsonl";
        Path logFile = Paths.get(expectedFileName);

        assertTrue(Files.exists(logFile), 
            "Chat memory log file should be created for parent session: " + expectedFileName);

        // Verify file content
        List<String> lines = Files.readAllLines(logFile);
        assertEquals(1, lines.size(), "File should contain exactly one snapshot");

        String jsonLine = lines.get(0);
        assertTrue(jsonLine.contains("\"sessionId\":\"" + sessionId + "\""), 
            "JSON should contain sessionId");
        assertTrue(jsonLine.contains("\"status\":\"IN_PROGRESS\""), 
            "JSON should contain status");
        assertTrue(jsonLine.contains("\"messageCount\":3"), 
            "JSON should contain messageCount=3");
        assertTrue(jsonLine.contains("SYSTEM"), 
            "JSON should contain SYSTEM message type");
        assertTrue(jsonLine.contains("USER"), 
            "JSON should contain USER message type");
        assertTrue(jsonLine.contains("AI"), 
            "JSON should contain AI message type");
    }

    @Test
    void testChatMemoryLogger_CreatesFileForChildSession() throws IOException, InterruptedException {
        // Given
        String parentId = "sess_test_parent_002";
        String childId = "child_abc123";
        String sessionId = parentId + "." + childId;
        
        ChatMemory memory = MessageWindowChatMemory.withMaxMessages(10);
        memory.add(new SystemMessage("Child agent prompt"));
        memory.add(new UserMessage("Child query"));

        // When
        ChatMemoryLogger.logChatMemory(sessionId, memory, "IN_PROGRESS");
        Thread.sleep(100);

        // Then
        String expectedFileName = LOG_DIR + "/chat-memory-" + sessionId + ".jsonl";
        Path logFile = Paths.get(expectedFileName);

        assertTrue(Files.exists(logFile), 
            "Chat memory log file should be created for child session: " + expectedFileName);

        // Verify filename includes both parent and child IDs
        String fileName = logFile.getFileName().toString();
        assertTrue(fileName.contains(parentId), 
            "Filename should contain parent session ID");
        assertTrue(fileName.contains(childId), 
            "Filename should contain child session ID");
    }

    @Test
    void testChatMemoryLogger_AppendsMultipleSnapshots() throws IOException, InterruptedException {
        // Given
        String sessionId = "sess_test_append_001";
        ChatMemory memory = MessageWindowChatMemory.withMaxMessages(10);

        // First snapshot
        memory.add(new SystemMessage("System prompt"));
        memory.add(new UserMessage("First query"));
        memory.add(new AiMessage("First response"));

        // When - Log first snapshot
        ChatMemoryLogger.logChatMemory(sessionId, memory, "IN_PROGRESS");
        Thread.sleep(50);

        // Add more messages
        memory.add(new UserMessage("Second query"));
        memory.add(new AiMessage("Second response"));

        // Log second snapshot
        ChatMemoryLogger.logChatMemory(sessionId, memory, "IN_PROGRESS");
        Thread.sleep(50);

        // Then
        String expectedFileName = LOG_DIR + "/chat-memory-" + sessionId + ".jsonl";
        Path logFile = Paths.get(expectedFileName);

        assertTrue(Files.exists(logFile), "Log file should exist");

        List<String> lines = Files.readAllLines(logFile);
        assertEquals(2, lines.size(), "File should contain 2 snapshots");

        // Verify first snapshot has 3 messages
        assertTrue(lines.get(0).contains("\"messageCount\":3"), 
            "First snapshot should have 3 messages");

        // Verify second snapshot has 5 messages
        assertTrue(lines.get(1).contains("\"messageCount\":5"), 
            "Second snapshot should have 5 messages");
    }

    @Test
    void testChatMemoryLogger_StatusTracking() throws IOException, InterruptedException {
        // Given
        String sessionId = "sess_test_status_001";
        ChatMemory memory = MessageWindowChatMemory.withMaxMessages(10);
        memory.add(new SystemMessage("Test"));

        // When - Log with different statuses
        ChatMemoryLogger.logChatMemory(sessionId, memory, "IN_PROGRESS");
        Thread.sleep(50);
        ChatMemoryLogger.logChatMemory(sessionId, memory, "COMPLETED");
        Thread.sleep(50);

        // Then
        String expectedFileName = LOG_DIR + "/chat-memory-" + sessionId + ".jsonl";
        Path logFile = Paths.get(expectedFileName);

        List<String> lines = Files.readAllLines(logFile);
        assertEquals(2, lines.size(), "File should contain 2 snapshots");

        assertTrue(lines.get(0).contains("\"status\":\"IN_PROGRESS\""), 
            "First snapshot should have IN_PROGRESS status");
        assertTrue(lines.get(1).contains("\"status\":\"COMPLETED\""), 
            "Second snapshot should have COMPLETED status");
    }

    @Test
    void testChatMemoryLogger_CloseSession() throws IOException, InterruptedException {
        // Given
        String sessionId = "sess_test_close_001";
        ChatMemory memory = MessageWindowChatMemory.withMaxMessages(10);
        memory.add(new SystemMessage("Test"));

        // Create file by logging
        ChatMemoryLogger.logChatMemory(sessionId, memory, "IN_PROGRESS");
        Thread.sleep(100);

        String expectedFileName = LOG_DIR + "/chat-memory-" + sessionId + ".jsonl";
        Path logFile = Paths.get(expectedFileName);

        assertTrue(Files.exists(logFile), "File should exist before closing");

        // When - Close session
        ChatMemoryLogger.closeSession(sessionId);

        // Then - File should still exist (just writer is closed)
        assertTrue(Files.exists(logFile), "File should still exist after closing");

        // Verify we can still read it
        List<String> lines = Files.readAllLines(logFile);
        assertEquals(1, lines.size(), "File should still contain the logged data");
    }

    @Test
    void testChatMemoryLogger_NullMemory() {
        // Given
        String sessionId = "sess_test_null_001";

        // When & Then - Should not throw exception
        assertDoesNotThrow(() -> {
            ChatMemoryLogger.logChatMemory(sessionId, null, "IN_PROGRESS");
        }, "Should handle null memory gracefully");
    }

    @Test
    void testChatMemoryLogger_EmptyMemory() throws IOException, InterruptedException {
        // Given
        String sessionId = "sess_test_empty_001";
        ChatMemory memory = MessageWindowChatMemory.withMaxMessages(10);
        // Don't add any messages

        // When
        ChatMemoryLogger.logChatMemory(sessionId, memory, "IN_PROGRESS");
        Thread.sleep(100);

        // Then
        String expectedFileName = LOG_DIR + "/chat-memory-" + sessionId + ".jsonl";
        Path logFile = Paths.get(expectedFileName);

        assertTrue(Files.exists(logFile), "File should be created even with empty memory");

        List<String> lines = Files.readAllLines(logFile);
        assertEquals(1, lines.size(), "File should contain one snapshot");
        assertTrue(lines.get(0).contains("\"messageCount\":0"), 
            "JSON should show 0 messages for empty memory");
    }
}
