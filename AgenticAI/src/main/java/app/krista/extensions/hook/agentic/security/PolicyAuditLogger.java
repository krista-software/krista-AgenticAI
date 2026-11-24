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
 * Logs policy enforcement events for audit trail.
 * Creates daily JSONL log files in logs/security/ directory.
 * Modeled after SecurityLogger with the same patterns: SLF4J + daily JSONL files,
 * ConcurrentHashMap for daily writers, FileWriter in append mode,
 * I/O exceptions swallowed with SLF4J fallback.
 */
public class PolicyAuditLogger {

    private static final Logger LOGGER = LoggerFactory.getLogger(PolicyAuditLogger.class);

    private static final String LOG_DIR = "logs/security";

    private static final Map<String, FileWriter> dailyWriters = new ConcurrentHashMap<>();

    /**
     * Logs an ALLOWED or BLOCKED policy check result.
     *
     * @param sessionId The session where the policy check occurred
     * @param result    The policy check result
     * @param toolName  The tool that was evaluated
     */
    public static void log(String sessionId, PolicyCheckResult result, String toolName) {

        String eventType = result.getDecision() != null ? result.getDecision().name() : "UNKNOWN";
        String actionTaken = result.getDecision() == PolicyCheckResult.Decision.ALLOWED
                ? "ALLOWED"
                : "SELF_CORRECTION_TRIGGERED";

        PolicyAuditEvent event = new PolicyAuditEvent(
                LocalDateTime.now(),
                sessionId,
                eventType,
                toolName,
                result.getViolatedPolicy(),
                result.getSeverity(),
                actionTaken,
                result.getReason()
        );

        writeEvent(sessionId, event);
    }

    /**
     * Logs a HARD_BLOCKED event when policy enforcement exhausts retries and blocks with no override.
     *
     * @param sessionId The session where the hard block occurred
     * @param result    The policy check result that triggered the hard block
     */
    public static void logHardBlock(String sessionId, PolicyCheckResult result) {

        PolicyAuditEvent event = new PolicyAuditEvent(
                LocalDateTime.now(),
                sessionId,
                "HARD_BLOCKED",
                null,
                result.getViolatedPolicy(),
                result.getSeverity(),
                "BLOCKED_NO_OVERRIDE",
                result.getReason()
        );

        writeEvent(sessionId, event);
    }

    /**
     * Logs a CHECKER_ERROR event when the PolicyChecker itself fails.
     *
     * @param sessionId    The session where the error occurred
     * @param toolName     The tool that was being evaluated
     * @param errorMessage The error message from the PolicyChecker failure
     */
    public static void logCheckerError(String sessionId, String toolName, String errorMessage) {

        PolicyAuditEvent event = new PolicyAuditEvent(
                LocalDateTime.now(),
                sessionId,
                "CHECKER_ERROR",
                toolName,
                null,
                "HIGH",
                "BLOCKED",
                errorMessage
        );

        writeEvent(sessionId, event);
    }

    /**
     * Resets the logger state. Useful for testing.
     */
    public static void reset() {

        for (FileWriter writer : dailyWriters.values()) {

            try {

                writer.close();
            } catch (IOException e) {

                // Ignore during reset
            }
        }
        dailyWriters.clear();
    }

    private static void writeEvent(String sessionId, PolicyAuditEvent event) {

        try {

            LOGGER.info("POLICY_AUDIT: session={} event={} tool={} severity={} action={} reason=\"{}\"",
                    sessionId, event.eventType(), event.attemptedTool(),
                    event.severity(), event.actionTaken(),
                    event.reason() != null ? event.reason() : "unknown");

            String logLine = JsonMapper.getInstance().writeValueAsString(event);

            FileWriter writer = getWriterForToday();
            writer.write(logLine + "\n");
            writer.flush();

        } catch (IOException e) {

            LOGGER.error("POLICY_AUDIT_ERROR: Failed to write policy audit log for session={}", sessionId, e);
            LOGGER.error("POLICY_AUDIT_FALLBACK: session={} event={} tool={} severity={} action={} reason=\"{}\"",
                    sessionId, event.eventType(), event.attemptedTool(),
                    event.severity(), event.actionTaken(),
                    event.reason() != null ? event.reason() : "unknown");
        } catch (Exception e) {

            LOGGER.error("POLICY_AUDIT_ERROR: Unexpected error for session={}", sessionId, e);
        }
    }

    /**
     * Gets or creates a FileWriter for today's log file.
     * Creates new file daily and rotates writers when date changes.
     */
    private static FileWriter getWriterForToday() throws IOException {

        String today = LocalDate.now().format(DateTimeFormatter.ISO_DATE);
        String fileName = LOG_DIR + "/policy-enforcement-" + today + ".jsonl";

        rotateWritersIfNeeded(today);

        return dailyWriters.computeIfAbsent(today, date -> {

            try {

                Path dirPath = Paths.get(LOG_DIR);
                Path parentPath = dirPath.getParent();

                if (parentPath != null && !Files.exists(parentPath)) {

                    Files.createDirectories(parentPath);
                }

                if (!Files.exists(dirPath)) {

                    Files.createDirectory(dirPath);
                }

                File logFile = new File(fileName);
                FileWriter writer = new FileWriter(logFile, true);
                LOGGER.info("Created new policy audit log file for date: {}", today);
                return writer;
            } catch (IOException e) {

                throw new RuntimeException("Cannot create policy audit log file: " + fileName, e);
            }
        });
    }

    /**
     * Rotates writers when date changes by closing old writers and removing them from cache.
     */
    private static void rotateWritersIfNeeded(String today) {

        dailyWriters.entrySet().removeIf(entry -> {

            if (!entry.getKey().equals(today)) {

                try {

                    entry.getValue().close();
                    LOGGER.info("Closed policy audit log file for date: {}", entry.getKey());
                } catch (IOException e) {

                    LOGGER.error("Error closing policy audit log file for date {}: {}", entry.getKey(), e.getMessage());
                }
                return true;
            }
            return false;
        });
    }

    /**
     * Policy audit event record.
     */
    record PolicyAuditEvent(
            LocalDateTime timestamp,
            String sessionId,
            String eventType,
            String attemptedTool,
            String violatedPolicy,
            String severity,
            String actionTaken,
            String reason
    ) {

    }
}
