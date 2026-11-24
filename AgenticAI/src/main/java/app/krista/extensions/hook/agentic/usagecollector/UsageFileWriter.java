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

import app.krista.extensions.hook.util.JsonMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Writes session usage data to JSON files.
 * Files are created in the "usage-reports" directory in the user's home directory.
 */
public class UsageFileWriter {

    private static final Logger LOGGER = LoggerFactory.getLogger(UsageFileWriter.class);

    private static final Path REPORTS_DIR;
    private static final DateTimeFormatter FILE_TIMESTAMP_FORMAT =
        DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneId.systemDefault());

    static {

        // Initialize reports directory in user's home directory
        String userHome = System.getProperty("user.home");
        REPORTS_DIR = Paths.get(userHome, "usage-reports");

        // Create reports directory if it doesn't exist
        try {

            Files.createDirectories(REPORTS_DIR);
            LOGGER.info("Usage reports directory initialized: {}", REPORTS_DIR.toAbsolutePath());
        } catch (IOException e) {

            LOGGER.error("Failed to create usage reports directory", e);
        }
    }

    // Private constructor to prevent instantiation
    private UsageFileWriter() {
    }
    
    /**
     * Write session usage data to a JSON file.
     *
     * @param collector The session usage collector
     * @return The path to the created file, or null if failed
     */
    public static String writeUsageFile(SessionUsageCollector collector) {
        try {
            SessionUsageCollector.SessionSummary summary = collector.getSessionSummary();
            List<TokenUsageRecord> records = collector.getUsageRecords();

            // Build the JSON structure
            Map<String, Object> data = new HashMap<>();

            // Session metadata
            data.put("sessionId", summary.getSessionId());

            // Extract and add step metadata from session ID
            Map<String, String> stepMetadata = extractStepMetadataFromSessionId(summary.getSessionId());
            if (!stepMetadata.isEmpty()) {
                data.put("stepMetadata", stepMetadata);
            }

            data.put("agentName", summary.getAgentName());
            data.put("status", summary.getStatus().toString());
            data.put("startTime", summary.getStartTime());
            data.put("endTime", summary.getEndTime());
            data.put("durationMs", summary.getDurationMs());
            data.put("durationSeconds", summary.getDurationMs() / 1000.0);

            if (summary.getErrorMessage() != null) {
                data.put("errorMessage", summary.getErrorMessage());
            }

            // Summary statistics
            Map<String, Object> summaryData = new HashMap<>();
            summaryData.put("totalLlmCalls", summary.getTotalLlmCalls());
            summaryData.put("totalPromptTokens", summary.getTotalPromptTokens());
            summaryData.put("totalCompletionTokens", summary.getTotalCompletionTokens());
            summaryData.put("totalTokens", summary.getTotalTokens());
            summaryData.put("totalCostUSD", summary.getTotalCost());
            summaryData.put("averageTokensPerCall",
                summary.getTotalLlmCalls() > 0 ? summary.getTotalTokens() / (double) summary.getTotalLlmCalls() : 0);
            summaryData.put("averageCostPerCall",
                summary.getTotalLlmCalls() > 0 ? summary.getTotalCost() / summary.getTotalLlmCalls() : 0);
            data.put("summary", summaryData);

            // Individual LLM calls
            List<Map<String, Object>> llmCalls = records.stream().map(record -> {
                Map<String, Object> call = new HashMap<>();
                call.put("timestamp", record.getTimestamp());
                call.put("modelName", record.getModelName());
                call.put("apiKeyMasked", record.getApiKeyMasked());
                call.put("agentName", record.getAgentName());
                call.put("promptSummary", record.getPromptSummary());
                call.put("promptLength", record.getPromptLength());
                call.put("completionLength", record.getCompletionLength());
                call.put("promptTokens", record.getPromptTokens());
                call.put("completionTokens", record.getCompletionTokens());
                call.put("totalTokens", record.getTotalTokens());
                call.put("costUSD", record.getCost());
                call.put("requestDurationMs", record.getRequestDurationMs());

                // Include full prompt and completion for detailed analysis
                call.put("fullPrompt", record.getFullPrompt());
                call.put("completionText", record.getCompletionText());

                return call;
            }).collect(Collectors.toList());

            data.put("llmCalls", llmCalls);

            // Generate filename
            String timestamp = FILE_TIMESTAMP_FORMAT.format(summary.getStartTime());
            String filename = String.format("usage-%s-%s.json",
                summary.getSessionId().replaceAll("[^a-zA-Z0-9-]", "_"),
                timestamp);

            Path filePath = REPORTS_DIR.resolve(filename);

            // Write to file
            JsonMapper.getPrettyPrinter().writeValue(filePath.toFile(), data);

            LOGGER.info("Usage report written to: {} - calls: {}, tokens: {}, cost: ${}",
                filePath.toAbsolutePath(),
                summary.getTotalLlmCalls(),
                summary.getTotalTokens(),
                String.format("%.6f", summary.getTotalCost()));

            return filePath.toAbsolutePath().toString();

        } catch (Exception e) {
            LOGGER.error("Failed to write usage file", e);
            return null;
        }
    }
    
    /**
     * Extract step metadata from session ID.
     * Expected format: sess_workspacename_topicname_conversationtitle_executionid_steptitle_uuid
     *
     * @param sessionId The session ID to parse
     * @return Map containing extracted metadata fields, or empty map if parsing fails
     */
    private static Map<String, String> extractStepMetadataFromSessionId(String sessionId) {
        Map<String, String> metadata = new HashMap<>();

        if (sessionId == null || !sessionId.startsWith("sess_")) {
            return metadata;
        }

        try {
            // Remove "sess_" prefix
            String withoutPrefix = sessionId.substring(5);

            // Split by underscore
            String[] parts = withoutPrefix.split("_");

            // We expect at least 6 parts: workspace, topic, conversation, execution, step, uuid
            // But UUID itself contains hyphens (not underscores), so we need to be careful
            // The last part should be the UUID (which has a specific format)
            // Everything before the last UUID-like part should be our metadata

            if (parts.length >= 6) {
                // Find where the UUID starts (last part that looks like a UUID)
                int uuidIndex = parts.length - 1;

                // Reconstruct the parts (some fields might have had underscores that were sanitized)
                // Format: workspace_topic_conversation_execution_step_uuid
                // We'll take the first 5 segments as metadata
                int metadataEndIndex = Math.min(5, parts.length - 1);

                if (parts.length > 0) metadata.put("workspaceName", parts[0]);
                if (parts.length > 1) metadata.put("topicName", parts[1]);
                if (parts.length > 2) metadata.put("conversationTitle", parts[2]);
                if (parts.length > 3) metadata.put("executionId", parts[3]);
                if (parts.length > 4) metadata.put("stepTitle", parts[4]);
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to parse step metadata from session ID: {}", e.getMessage());
        }

        return metadata;
    }

    /**
     * Get the reports directory path.
     */
    public static String getReportsDirectory() {
        return REPORTS_DIR.toAbsolutePath().toString();
    }
}

