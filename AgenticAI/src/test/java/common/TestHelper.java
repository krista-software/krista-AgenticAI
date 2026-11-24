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

package common;

import app.krista.extensions.hook.llm.claude.ClaudeClient;
import app.krista.extensions.hook.llm.claude.util.ClaudeConstants;
import app.krista.extensions.hook.llm.openai.OpenAIClient;
import app.krista.extensions.hook.llm.openai.util.OpenAIConstants;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class TestHelper {

    private static final String claudeModelName = ClaudeConstants.CLAUDE_3_5_HAIKU;;
    private static final String openAIModelName = OpenAIConstants.GPT_4_1_mini;

    public static void processTestCase(String systemPrompt, List<String> inputs, List<String> sampleOutputs,
                                       String claudeKey, String openAIKey, String outputPath) {

        List<String> openAIResults = new ArrayList<>();
        List<String> claudeResults = new ArrayList<>();

        List<String> openAIProcessingTime = new ArrayList<>();
        List<String> claudeProcessingTime = new ArrayList<>();

        // Create LLM client instances directly for testing
        ClaudeClient claudeClient = new ClaudeClient(claudeKey, claudeModelName);
        OpenAIClient openAIClient = new OpenAIClient(openAIKey, openAIModelName);

        for(String input: inputs) {
            long startTime = System.currentTimeMillis();
            openAIResults.add(openAIClient.query(systemPrompt, input));
            openAIProcessingTime.add(String.valueOf(System.currentTimeMillis()-startTime));
        }

        for(String input: inputs) {
            long startTime = System.currentTimeMillis();
            claudeResults.add(claudeClient.query(systemPrompt, input));
            claudeProcessingTime.add(String.valueOf(System.currentTimeMillis()-startTime));
        }

        writeListsToCSV(inputs, sampleOutputs, openAIResults, claudeResults, openAIProcessingTime, claudeProcessingTime, outputPath);

    }

    public static void writeListsToCSV(List<String> col1, List<String> col2, List<String> col3,
                                       List<String> col4, List<String> col5, List<String> col6, String outputPath) {
        int rowCount = col1.size();

        CSVFormat format = CSVFormat.DEFAULT.builder()
                .setHeader("Input", "Sample output", openAIModelName, claudeModelName, "OpenAITime(ms)", "ClaudeTime(ms)")
                .build();

        try (BufferedWriter writer = Files.newBufferedWriter(Paths.get(outputPath), StandardCharsets.UTF_8);
             CSVPrinter printer = new CSVPrinter(writer, format)) {

            for (int i = 0; i < rowCount; i++) {
                printer.printRecord(
                        col1.get(i),
                        col2.get(i),
                        col3.get(i),
                        col4.get(i),
                        col5.get(i),
                        col6.get(i)
                );
            }

            System.out.println("CSV written to: " + outputPath);
        } catch (IOException e) {
            System.err.println("Error writing CSV: " + e.getMessage());
        }
    }

    public static List<String> readLinesFromResource(String fileName) throws Exception {
        ClassLoader classLoader = TestHelper.class.getClassLoader();

        try (InputStream is = classLoader.getResourceAsStream(fileName)) {
            if (is == null) {
                throw new IllegalArgumentException("File not found: " + fileName);
            }

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                return reader.lines().collect(Collectors.toList());
            }
        }
    }

    public static String readFileAsStringFromResource(String fileName) throws Exception {
        ClassLoader classLoader = TestHelper.class.getClassLoader();

        try (InputStream is = classLoader.getResourceAsStream(fileName)) {
            if (is == null) {
                throw new IllegalArgumentException("File not found: " + fileName);
            }

            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }


    public static List<String> readJsonStringsFromResource(String fileName) throws Exception {
        ClassLoader classLoader = TestHelper.class.getClassLoader();

        try (InputStream is = classLoader.getResourceAsStream(fileName)) {
            if (is == null) {
                throw new IllegalArgumentException("File not found: " + fileName);
            }

            // Read entire file content as String
            String content = new String(is.readAllBytes(), StandardCharsets.UTF_8).trim();

            ObjectMapper mapper = new ObjectMapper();
            mapper.enable(SerializationFeature.INDENT_OUTPUT);

            // Parse as array of JsonNode (to preserve formatting)
            JsonNode rootArray = mapper.readTree(content);
            List<String> result = new ArrayList<>();

            for (JsonNode node : rootArray) {
                result.add(mapper.writeValueAsString(node)); // convert each object back to compact JSON string
            }

            return result;
        }
    }
}
