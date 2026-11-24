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

import app.krista.extensions.hook.agentic.util.ChatMessageUtils;
import app.krista.extensions.hook.util.JsonMapper;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.memory.ChatMemory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Logs chat memory snapshots to session-specific files.
 * Creates one file per session (parent or child) for real-time conversation tracking.
 * Files are stored in logs/chat-memory/ directory.
 */
public class ChatMemoryLogger {

    private static final Logger LOGGER = LoggerFactory.getLogger(ChatMemoryLogger.class);

    private static final String LOG_DIR = "logs/chat-memory";

    private static final Map<String, FileWriter> sessionWriters = new ConcurrentHashMap<>();

    /**
     * Logs the current state of chat memory for a session.
     * Creates a new file if it doesn't exist, appends if it does.
     *
     * @param sessionId The session ID (parent or child)
     * @param memory The ChatMemory to log
     * @param status The current session status (e.g., "IN_PROGRESS", "COMPLETED", "ERROR")
     */
    public static void logChatMemory(String sessionId, ChatMemory memory, String status) {
        if (memory == null) {
            LOGGER.warn("Chat memory is null for session {}", sessionId);
            return;
        }

        try {
            FileWriter writer = getWriterForSession(sessionId);

            ChatMemorySnapshot snapshot = new ChatMemorySnapshot(
                LocalDateTime.now(),
                sessionId,
                status,
                memory.messages().size(),
                memory.messages().stream()
                    .map(msg -> new MessageSnapshot(msg.type().toString(), ChatMessageUtils.getText(msg)))
                    .collect(Collectors.toList())
            );

            String logLine = JsonMapper.getInstance().writeValueAsString(snapshot);
            writer.write(logLine + "\n");
            writer.flush();

        } catch (IOException e) {
            LOGGER.error("Failed to log chat memory for session {}: {}", sessionId, e.getMessage(), e);
        }
    }

    /**
     * Closes the FileWriter for a session and removes it from the cache.
     * Should be called when a session completes or errors.
     *
     * @param sessionId The session ID to close
     */
    public static void closeSession(String sessionId) {
        FileWriter writer = sessionWriters.remove(sessionId);
        if (writer != null) {
            try {
                writer.close();
            } catch (IOException e) {
                LOGGER.error("Error closing chat memory log for session {}: {}", sessionId, e.getMessage(), e);
            }
        }
    }

    /**
     * Gets or creates a FileWriter for the given session.
     * Handles both parent and child session naming.
     */
    private static FileWriter getWriterForSession(String sessionId) throws IOException {
        return sessionWriters.computeIfAbsent(sessionId, id -> {
            try {
                String fileName = getFileName(id);

                // Ensure parent directories exist
                Path dirPath = Paths.get(LOG_DIR);
                Path parentPath = dirPath.getParent();

                if (parentPath != null && !Files.exists(parentPath)) {
                    Files.createDirectories(parentPath);
                }

                if (!Files.exists(dirPath)) {
                    Files.createDirectory(dirPath);
                }

                File logFile = new File(fileName);
                return new FileWriter(logFile, true);

            } catch (IOException e) {
                throw new RuntimeException("Cannot create chat memory log file for session: " + id, e);
            }
        });
    }

    /**
     * Generates the file name for a session.
     * Parent: chat-memory-{sessionId}.jsonl
     * Child: chat-memory-{parentId}.{childId}.jsonl
     */
    private static String getFileName(String sessionId) {
        // Check if it's a child session (contains a dot)
        if (sessionId.contains(".")) {
            // Child session: sess_parent.child_childId
            // File: chat-memory-sess_parent.child_childId.jsonl
            return LOG_DIR + "/chat-memory-" + sessionId + ".jsonl";
        } else {
            // Parent session: sess_xxx
            // File: chat-memory-sess_xxx.jsonl
            return LOG_DIR + "/chat-memory-" + sessionId + ".jsonl";
        }
    }

    /**
     * Snapshot of chat memory at a point in time.
     */
    private static record ChatMemorySnapshot(
        LocalDateTime timestamp,
        String sessionId,
        String status,
        int messageCount,
        List<MessageSnapshot> messages
    ) {}

    /**
     * Individual message snapshot.
     */
    private static record MessageSnapshot(
        String type,
        String text
    ) {}
}
