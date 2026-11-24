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

package app.krista.extensions.hook.policybrief;

import app.krista.extension.executor.Invoker;
import app.krista.extensions.hook.agentic.factory.ModelFactory;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.service.AiServices;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Singleton factory for creating and managing the {@link PolicyBriefGenerator} AI service.
 *
 * <p>Unlike the stateless {@code PolicyCheckerFactory}, this factory provisions a
 * {@link ChatMemoryProvider} so that multi-turn conversations (initial generation
 * followed by author feedback rounds) maintain context within the same job.</p>
 *
 * <p>Thread-safe using double-checked locking. The factory owns the
 * {@link ConcurrentHashMap} backing chat memory, allowing explicit cleanup
 * when a job completes or expires.</p>
 */
public class PolicyBriefGeneratorFactory {

    private static final Logger LOGGER = LoggerFactory.getLogger(PolicyBriefGeneratorFactory.class);

    private static final int MAX_MESSAGES_PER_SESSION = 20;

    private static volatile PolicyBriefGenerator instance;
    private static volatile ConcurrentHashMap<Object, ChatMemory> chatMemoryMap;

    private PolicyBriefGeneratorFactory() {

    }

    /**
     * Gets or creates the singleton {@link PolicyBriefGenerator} instance.
     *
     * @param invoker the invoker containing API key and LLM configuration
     * @return the singleton generator instance
     * @throws IllegalArgumentException if invoker is null
     * @throws IllegalStateException    if model creation fails
     */
    public static PolicyBriefGenerator getInstance(Invoker invoker) {

        if (invoker == null) {

            throw new IllegalArgumentException("Invoker cannot be null");
        }

        if (instance == null) {

            synchronized (PolicyBriefGeneratorFactory.class) {

                if (instance == null) {

                    LOGGER.info("Creating singleton PolicyBriefGenerator instance");
                    chatMemoryMap = new ConcurrentHashMap<>();
                    instance = createGenerator(invoker, chatMemoryMap);
                }
            }
        }
        return instance;
    }

    /**
     * Removes the chat memory associated with the given job ID.
     * Call this when a job completes, fails, or is evicted from the job store
     * to prevent unbounded memory growth.
     *
     * @param jobId the job identifier whose memory should be cleaned up
     */
    public static void cleanupMemory(String jobId) {

        if (chatMemoryMap != null && jobId != null) {

            ChatMemory removed = chatMemoryMap.remove(jobId);
            if (removed != null) {

                LOGGER.debug("Cleaned up chat memory for job: {}", jobId);
            }
        }
    }

    /**
     * Resets the singleton instance. Useful for testing or when configuration changes.
     */
    public static synchronized void reset() {

        LOGGER.info("Resetting PolicyBriefGenerator singleton instance");
        instance = null;
        chatMemoryMap = null;
    }

    private static PolicyBriefGenerator createGenerator(Invoker invoker,
                                                         Map<Object, ChatMemory> memoryMap) {

        ChatLanguageModel chatModel;

        try {

            chatModel = ModelFactory.createFromAttributes(invoker, false, "PolicyBriefGenerator");
            LOGGER.info("PolicyBriefGenerator ChatLanguageModel created successfully");
        } catch (Exception e) {

            LOGGER.error("Failed to create PolicyBriefGenerator model: {}", e.getMessage(), e);
            throw new IllegalStateException("Cannot create PolicyBriefGenerator: " + e.getMessage(), e);
        }

        ChatMemoryProvider memoryProvider = sessionId ->
                memoryMap.computeIfAbsent(sessionId, id ->
                        MessageWindowChatMemory.builder()
                                .id(id)
                                .maxMessages(MAX_MESSAGES_PER_SESSION)
                                .build()
                );

        LOGGER.info("PolicyBriefGenerator configured with max {} messages per session",
                MAX_MESSAGES_PER_SESSION);

        return AiServices.builder(PolicyBriefGenerator.class)
                .chatLanguageModel(chatModel)
                .chatMemoryProvider(memoryProvider)
                .build();
    }
}
