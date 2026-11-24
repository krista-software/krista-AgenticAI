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

package app.krista.extensions.hook.agentic.factory;

import app.krista.extension.executor.Invoker;
import app.krista.extensions.hook.agentic.planner.ToolPlanner;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.service.AiServices;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * Singleton factory for creating and managing a single ToolPlanner instance.
 * This ensures that the same ToolPlanner (with its ChatMemoryProvider and ChatLanguageModel)
 * is reused across all orchestration requests, preventing unnecessary recreation.
 *
 * Thread-safe using double-checked locking pattern.
 */
public class ToolPlannerFactory {

    private static final Logger LOGGER = LoggerFactory.getLogger(ToolPlannerFactory.class);

    private static volatile ToolPlanner instance;
    private static volatile Map<Object, ChatMemory> chatMemoryMapInstance;
    
    // Private constructor to prevent instantiation
    private ToolPlannerFactory() {
    }
    
    /**
     * Gets or creates the singleton ToolPlanner instance.
     * The factory owns and manages the chatMemoryMap to ensure all instances share the same map.
     *
     * @param invoker The invoker containing API key and configuration
     * @return The singleton ToolPlanner instance
     * @throws IllegalArgumentException if invoker is null
     * @throws IllegalStateException if model creation fails
     */
    public static ToolPlanner getInstance(Invoker invoker) {
        if (invoker == null) {
            throw new IllegalArgumentException("Invoker cannot be null");
        }

        if (instance == null) {
            synchronized (ToolPlannerFactory.class) {
                if (instance == null) {
                    LOGGER.info("Creating singleton ToolPlanner instance");
                    // Factory creates and owns the chatMemoryMap
                    chatMemoryMapInstance = new java.util.concurrent.ConcurrentHashMap<>();
                    instance = createToolPlanner(invoker, chatMemoryMapInstance);
                }
            }
        }
        return instance;
    }
    
    /**
     * Gets the chat memory map instance used by the ToolPlanner.
     * This is useful for logging and debugging purposes.
     * 
     * @return The chat memory map, or null if ToolPlanner hasn't been created yet
     */
    public static Map<Object, ChatMemory> getChatMemoryMap() {
        return chatMemoryMapInstance;
    }
    
    /**
     * Creates a new ToolPlanner instance with the provided configuration.
     * This method is called only once during the first getInstance() call.
     * Uses ModelFactory to create model with guardrails and usage tracking enabled.
     *
     * @param invoker The invoker containing API key and configuration
     * @param chatMemoryMap The shared chat memory map for all sessions
     * @return A new ToolPlanner instance
     * @throws IllegalStateException if model creation fails
     */
    private static ToolPlanner createToolPlanner(Invoker invoker, Map<Object, ChatMemory> chatMemoryMap) {
        ChatLanguageModel chatModel;

        try {
            // Create the ChatLanguageModel using ModelFactory with guardrails and usage tracking enabled
            // isAgenticModel=true enables AgenticChatLanguageModel wrapper
            chatModel = ModelFactory.createFromAttributes(invoker, true, "ToolPlanner");
            LOGGER.info("Agentic ChatLanguageModel created successfully");
        } catch (IllegalStateException e) {
            // Re-throw with context
            LOGGER.error("Failed to create ChatLanguageModel: {}", e.getMessage(), e);
            throw new IllegalStateException("Cannot create ToolPlanner: " + e.getMessage(), e);
        } catch (Exception e) {
            // Catch any other unexpected exceptions
            LOGGER.error("Unexpected error creating ChatLanguageModel: {}", e.getMessage(), e);
            throw new IllegalStateException("Cannot create ToolPlanner due to unexpected error: " + e.getMessage(), e);
        }

        // Configure memory provider - using MessageWindowChatMemory for all providers
        // This limits by message count rather than tokens, which works for both OpenAI and Claude
        ChatMemoryProvider memoryProvider = sessionId ->
                chatMemoryMap.computeIfAbsent(sessionId, id ->
                        MessageWindowChatMemory.builder()
                                .id(id)
                                .maxMessages(50) // Limit to 50 messages for all providers
                                .build()
                );

        LOGGER.info("ToolPlanner configured with:");
        LOGGER.info("  Guardrails: ENABLED (input/output validation)");
        LOGGER.info("  Usage Tracking: ENABLED (token counting, cost monitoring)");
        LOGGER.info("  Max messages per session: 50");

        // Build and return the ToolPlanner using AiServices
        return AiServices.builder(ToolPlanner.class)
                .chatLanguageModel(chatModel)
                .chatMemoryProvider(memoryProvider)
                .build();
    }
    
    /**
     * Resets the singleton instance. Useful for testing or when configuration changes.
     * WARNING: This will clear the existing ToolPlanner and all its associated state.
     */
    public static synchronized void reset() {
        LOGGER.info("Resetting ToolPlanner singleton instance");
        instance = null;
        chatMemoryMapInstance = null;
    }
}

