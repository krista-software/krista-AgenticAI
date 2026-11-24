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

package agentic;

import app.krista.extensions.hook.agentic.dto.AgenticToolDefinition;
import app.krista.extensions.hook.agentic.planner.ToolPlanner;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.service.AiServices;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
public class PlannerAgentTest {
    private static final String OPENAI_MODEL = "gpt-4";
    /**
     * Test to verify that consecutive calls to planNextStep do NOT duplicate the system message
     * when using the same session without clearing memory.
     */
    @Test
    public void testSystemMessageNoDuplication() {
        Map<Object, ChatMemory> chatMemoryMap = new ConcurrentHashMap<>();

        ChatMemoryProvider provider = sessionId ->
                chatMemoryMap.computeIfAbsent(sessionId, id ->
                        MessageWindowChatMemory.builder()
                                .id(id)
                                .maxMessages(50)
                                .build()
                );

        ChatLanguageModel model = OpenAiChatModel.builder()
                .modelName(OPENAI_MODEL)
                .apiKey(System.getenv("OPENAI_API_KEY"))
                .build();

        ToolPlanner planner = AiServices.builder(ToolPlanner.class)
                .chatLanguageModel(model)
                .chatMemoryProvider(provider)
                .build();

        // Split tools into business and helper
        List<AgenticToolDefinition> businessTools = new ArrayList<>();
        businessTools.add(new AgenticToolDefinition("tool1", "description1", "type1", new ArrayList<>(), false));
        
        List<AgenticToolDefinition> helperTools = new ArrayList<>();
        helperTools.add(new AgenticToolDefinition("tool2", "description2", "type2", new ArrayList<>(), true));

        List<Map<String, Object>> expectedOutputs = new ArrayList<>();
        Map<String, Object> output = new HashMap<>();
        output.put("variable", Map.of("name", "result", "type", "String"));
        output.put("instruction", "Fill in the result");
        expectedOutputs.add(output);

        // First query
        planner.planNextStep(businessTools, helperTools, "first query", "session-1", new HashMap<>(), "", "");

        // Second query on the same session WITHOUT clearing memory
        planner.planNextStep(businessTools, helperTools, "second query", "session-1", new HashMap<>(), "", "");

        System.out.println("========CHAT MEMORY AFTER TWO QUERIES (NO CLEAR)======");
        ChatMemory memory = chatMemoryMap.get("session-1");
        memory.messages().forEach(msg -> {
            System.out.println(msg.type() + ": " + msg.text() + "\n----");
        });

        // Expected behavior - system message should appear ONLY ONCE:
        //   system: You are a helpful assistant... tools: [...]
        //   user: first query
        //   assistant: ...
        //   user: second query
        //   assistant: ...
        //
        // If there's a bug, you'll see:
        //   system: You are a helpful assistant... tools: [...]
        //   user: first query
        //   assistant: ...
        //   system: You are a helpful assistant... tools: [...]  ← DUPLICATE!
        //   user: second query
        //   assistant: ...
    }

    /**
     * Test to verify that the system message is correctly sent again
     * when memory is cleared between queries.
     */
    @Test
    public void testSystemMessageAfterMemoryClear() {
        Map<Object, ChatMemory> chatMemoryMap = new ConcurrentHashMap<>();

        ChatMemoryProvider provider = sessionId ->
                chatMemoryMap.computeIfAbsent(sessionId, id ->
                        MessageWindowChatMemory.builder()
                                .id(id)
                                .maxMessages(50)
                                .build()
                );

        ChatLanguageModel model = OpenAiChatModel.builder()
                .modelName(OPENAI_MODEL)
                .apiKey(System.getenv("OPENAI_API_KEY"))
                .build();

        ToolPlanner planner = AiServices.builder(ToolPlanner.class)
                .chatLanguageModel(model)
                .chatMemoryProvider(provider)
                .build();

        // Split tools into business and helper
        List<AgenticToolDefinition> businessTools = new ArrayList<>();
        businessTools.add(new AgenticToolDefinition("tool1", "description1", "type1", new ArrayList<>(), false));
        
        List<AgenticToolDefinition> helperTools = new ArrayList<>();
        helperTools.add(new AgenticToolDefinition("tool2", "description2", "type2", new ArrayList<>(), true));

        List<Map<String, Object>> expectedOutputs = new ArrayList<>();
        Map<String, Object> output = new HashMap<>();
        output.put("variable", Map.of("name", "result", "type", "String"));
        output.put("instruction", "Fill in the result");
        expectedOutputs.add(output);

        // First query - this should add system message + user message + assistant response
        planner.planNextStep(businessTools, helperTools, "first query", "session-1", new HashMap<>(), "", "");

        System.out.println("========CHAT MEMORY AFTER FIRST QUERY======");
        ChatMemory memory = chatMemoryMap.get("session-1");
        memory.messages().forEach(msg -> {
            System.out.println(msg.type() + ": " + msg.text() + "\n----");
        });

        // Clear the memory to simulate a fresh start
        memory.clear();
        System.out.println("\n========MEMORY CLEARED======\n");

        // Second query - this should add system message again + user message + assistant response
        planner.planNextStep(businessTools, helperTools, "second query", "session-1", new HashMap<>(), "", "");

        System.out.println("========CHAT MEMORY AFTER SECOND QUERY (AFTER CLEAR)======");
        memory.messages().forEach(msg -> {
            System.out.println(msg.type() + ": " + msg.text());
        });

        // Expected behavior:
        // After first query:
        //   system: You are a helpful assistant... tools: [...]
        //   user: first query
        //   assistant: ...
        //
        // After clear and second query:
        //   system: You are a helpful assistant... tools: [...]  ← Should be sent again
        //   user: second query
        //   assistant: ...
    }
}
