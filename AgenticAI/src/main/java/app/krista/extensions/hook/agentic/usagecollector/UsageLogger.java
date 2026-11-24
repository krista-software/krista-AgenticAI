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

import app.krista.extensions.hook.agentic.telemetry.AgenticSessionTelemetryEmitter;
import app.krista.extensions.hook.util.JsonMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Logs usage reports to JSONL files.
 * Creates daily log files in logs/usage/ directory.
 */
public class UsageLogger {

    private static final Logger LOGGER = LoggerFactory.getLogger(UsageLogger.class);

    private static final String LOG_DIR = "logs/usage";

    private static final Map<String, FileWriter> dailyWriters = new ConcurrentHashMap<>();

    /**
     * Logs session usage summary.
     */
    public static void logSessionUsage(SessionUsageCollector collector) {
        try {
            SessionUsageCollector.SessionSummary summary = collector.getSessionSummary();

            LOGGER.info("USAGE_LOG_ATTEMPT: Logging usage for session={} status={} calls={}",
                summary.getSessionId(), summary.getStatus(), summary.getTotalLlmCalls());

            // Emit session-level telemetry to SigNoz alongside the file write. Fire-and-forget:
            // the emitter swallows all Throwables so a telemetry failure cannot break file logging.
            AgenticSessionTelemetryEmitter.emit(summary, collector.getRequestContext());

            UsageReport report = new UsageReport(
                summary.getSessionId(),
                summary.getAgentName(),
                summary.getTotalLlmCalls(),
                summary.getTotalPromptTokens(),
                summary.getTotalCompletionTokens(),
                summary.getTotalTokens(),
                summary.getTotalCost(),
                summary.getDurationMs(),
                summary.getStatus().toString(),
                LocalDateTime.now(),
                summary.getChildSessionsCount(),
                summary.getChildTotalCost(),
                truncateErrorMessage(summary.getErrorMessage(), 500),
                summary.getErrorCode()
            );

            String logLine = JsonMapper.getInstance().writeValueAsString(report);
            FileWriter writer = getWriterForToday();
            writer.write(logLine + "\n");
            writer.flush();

            // Force sync to disk
            try {
                FileDescriptor fd = ((FileOutputStream) writer.getClass().getDeclaredField("out").get(writer)).getFD();
                fd.sync();
            } catch (Exception e) {
                // Sync not supported or failed, but write already flushed
            }

            LOGGER.info("USAGE_LOG_SUCCESS: Successfully logged usage for session={}", summary.getSessionId());

        } catch (IOException e) {
            String sessionId = (collector != null && collector.getSessionSummary() != null)
                ? collector.getSessionSummary().getSessionId()
                : "unknown";
            LOGGER.error("USAGE_LOG_ERROR: Failed to log usage for session={}", sessionId, e);
        }
    }

    private static FileWriter getWriterForToday() throws IOException {
        String today = LocalDate.now().format(DateTimeFormatter.ISO_DATE);
        String fileName = LOG_DIR + "/usage-" + today + ".jsonl";
        File logFile = new File(fileName);

        // Check if we need to rotate writers (date changed)
        rotateWritersIfNeeded(today);

        // Check if existing writer is stale (file hasn't been modified in last 5 minutes)
        FileWriter existingWriter = dailyWriters.get(today);
        if (existingWriter != null && logFile.exists()) {
            long lastModified = logFile.lastModified();
            long now = System.currentTimeMillis();
            long fiveMinutes = 5 * 60 * 1000;

            if (now - lastModified > fiveMinutes) {
                LOGGER.info("USAGE_LOG_ROTATE: File not modified in 5+ minutes, recreating writer for: {}", today);
                try {
                    existingWriter.close();
                } catch (IOException e) {
                    LOGGER.error("USAGE_LOG_ERROR: Failed to close stale writer: {}", e.getMessage());
                }
                dailyWriters.remove(today);
                existingWriter = null;
            }
        }

        if (existingWriter != null) {
            return existingWriter;
        }

        // Create new writer
        try {
            // Ensure parent directories exist
            Path dirPath = Paths.get(LOG_DIR);
            Path parentPath = dirPath.getParent();

            // Create parent directories if needed
            if (parentPath != null && !Files.exists(parentPath)) {
                Files.createDirectories(parentPath);
            }

            // Create the logs/usage directory
            if (!Files.exists(dirPath)) {
                Files.createDirectory(dirPath);
            }

            // Open file in append mode
            FileWriter writer = new FileWriter(logFile, true);
            dailyWriters.put(today, writer);
            LOGGER.info("USAGE_LOG_CREATED: New usage log file for date: {} path={}", today, logFile.getAbsolutePath());
            return writer;
        } catch (IOException e) {
            LOGGER.error("USAGE_LOG_ERROR: Cannot create usage log file: {}", fileName, e);
            throw new RuntimeException("Cannot create usage log file: " + fileName, e);
        }
    }

    /**
     * Rotates writers when date changes by closing old writers and removing them from cache.
     */
    private static void rotateWritersIfNeeded(String today) {
        // Close and remove writers for dates other than today
        dailyWriters.entrySet().removeIf(entry -> {
            if (!entry.getKey().equals(today)) {
                try {
                    entry.getValue().close();
                    LOGGER.info("Closed usage log file for date: {}", entry.getKey());
                } catch (IOException e) {
                    LOGGER.error("Error closing usage log file for date {}: {}", entry.getKey(), e.getMessage());
                }
                return true; // Remove from map
            }
            return false; // Keep today's writer
        });
    }

    private static String truncateErrorMessage(String message, int maxLength) {

        if (message == null) return null;
        if (message.length() <= maxLength) return message;
        return message.substring(0, maxLength) + "...[truncated]";
    }

    private static record UsageReport(
        String sessionId,
        String agentName,
        int totalLlmCalls,
        int totalPromptTokens,
        int totalCompletionTokens,
        int totalTokens,
        double totalCost,
        long durationMs,
        String status,
        LocalDateTime timestamp,
        int childSessions,
        double childTotalCost,
        String errorMessage,
        String errorCode
    ) {}

    /**
     * Resets the logger state. Useful for testing.
     */
    public static void reset() {
        for (FileWriter writer : dailyWriters.values()) {
            try {
                writer.close();
            } catch (IOException e) {
                // Ignore
            }
        }
        dailyWriters.clear();
    }
}
