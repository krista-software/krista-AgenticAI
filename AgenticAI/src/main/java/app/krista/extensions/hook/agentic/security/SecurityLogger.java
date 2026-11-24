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

import app.krista.extensions.hook.util.JsonMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
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
 * Logs security events including guardrail violations.
 * Creates daily log files in logs/security/ directory.
 */
public class SecurityLogger {

    private static final Logger LOGGER = LoggerFactory.getLogger(SecurityLogger.class);

    private static final String LOG_DIR = "logs/security";

    private static final Map<String, FileWriter> dailyWriters = new ConcurrentHashMap<>();

    /**
     * Logs a guardrail violation.
     *
     * @param sessionId The session where violation occurred
     * @param violationType Type of violation (PROMPT_INJECTION, JAILBREAK, PII_LEAK, etc.)
     *                    // TODO: Implement specific violation type detection
     * @param severity HIGH, MEDIUM, or LOW
     * @param actionTaken BLOCKED or WARNED
     * @param input The violating input (full text for forensics)
     * @param agentName Name of the agent that detected the violation
     * @param reason The specific reason why the violation was detected
     */
    public static void logGuardrailViolation(
            String sessionId,
            String violationType,
            String severity,
            String actionTaken,
            String input,
            String agentName,
            String reason) {

        try {
            LOGGER.info("SECURITY_LOG: session={} type={} severity={} action={} agent={} reason=\"{}\" input=\"{}\"",
                sessionId, violationType, severity, actionTaken, agentName,
                reason != null ? reason : "unknown",
                input != null ? input : "null");

            SecurityEvent event = new SecurityEvent(
                LocalDateTime.now(),
                sessionId,
                violationType,
                severity,
                actionTaken,
                input,
                agentName,
                reason
            );

            String logLine = JsonMapper.getInstance().writeValueAsString(event);

            FileWriter writer = getWriterForToday();
            writer.write(logLine + "\n");
            writer.flush();

        } catch (IOException e) {
            LOGGER.error("SECURITY_LOG_ERROR: Failed to write security log for session={}", sessionId, e);
            // Fallback: log the full details at ERROR level so nothing is silently lost
            LOGGER.error("SECURITY_LOG_FALLBACK: session={} type={} severity={} action={} reason=\"{}\"",
                sessionId, violationType, severity, actionTaken,
                reason != null ? reason : "unknown");
        } catch (Exception e) {
            LOGGER.error("SECURITY_LOG_ERROR: Unexpected error for session={}", sessionId, e);
        }
    }

    /**
     * Gets or creates a FileWriter for today's log file.
     * Creates new file daily and rotates writers when date changes.
     */
    private static FileWriter getWriterForToday() throws IOException {
        String today = LocalDate.now().format(DateTimeFormatter.ISO_DATE);
        String fileName = LOG_DIR + "/guardrail-violations-" + today + ".jsonl";

        // Check if we need to rotate writers (date changed)
        rotateWritersIfNeeded(today);

        return dailyWriters.computeIfAbsent(today, date -> {
            try {
                // Ensure parent directories exist
                Path dirPath = Paths.get(LOG_DIR);
                Path parentPath = dirPath.getParent();

                // Create parent directories if needed
                if (parentPath != null && !Files.exists(parentPath)) {
                    Files.createDirectories(parentPath);
                }

                // Create the logs/security directory
                if (!Files.exists(dirPath)) {
                    Files.createDirectory(dirPath);
                }

                // Open file in append mode
                File logFile = new File(fileName);
                FileWriter writer = new FileWriter(logFile, true);
                LOGGER.info("Created new security log file for date: {}", today);
                return writer;
            } catch (IOException e) {
                throw new RuntimeException("Cannot create security log file: " + fileName, e);
            }
        });
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
                    LOGGER.info("Closed security log file for date: {}", entry.getKey());
                } catch (IOException e) {
                    LOGGER.error("Error closing security log file for date {}: {}", entry.getKey(), e.getMessage());
                }
                return true; // Remove from map
            }
            return false; // Keep today's writer
        });
    }

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

    /**
     * Security event record.
     */
    private static record SecurityEvent(
        LocalDateTime timestamp,
        String sessionId,
        String violationType,
        String severity,
        String actionTaken,
        String input,
        String agentName,
        String reason
    ) {}
}
