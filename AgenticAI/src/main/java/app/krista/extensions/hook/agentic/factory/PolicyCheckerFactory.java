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
import app.krista.extensions.hook.agentic.planner.PolicyChecker;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.service.AiServices;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Singleton factory for creating and managing a single PolicyChecker instance.
 * Unlike ToolPlannerFactory, PolicyChecker has NO chat memory (stateless evaluation).
 * Thread-safe using double-checked locking pattern.
 */
public class PolicyCheckerFactory {

    private static final Logger LOGGER = LoggerFactory.getLogger(PolicyCheckerFactory.class);

    private static volatile PolicyChecker instance;

    private PolicyCheckerFactory() {

    }

    /**
     * Gets or creates the singleton PolicyChecker instance.
     *
     * @param invoker The invoker containing API key and configuration
     * @return The singleton PolicyChecker instance
     * @throws IllegalArgumentException if invoker is null
     * @throws IllegalStateException    if model creation fails
     */
    public static PolicyChecker getInstance(Invoker invoker) {

        if (invoker == null) {

            throw new IllegalArgumentException("Invoker cannot be null");
        }

        if (instance == null) {

            synchronized (PolicyCheckerFactory.class) {

                if (instance == null) {

                    LOGGER.info("Creating singleton PolicyChecker instance");
                    instance = createPolicyChecker(invoker);
                }
            }
        }
        return instance;
    }

    private static PolicyChecker createPolicyChecker(Invoker invoker) {

        ChatLanguageModel chatModel;

        try {

            chatModel = ModelFactory.createFromAttributes(invoker, false, "PolicyChecker");
            LOGGER.info("PolicyChecker ChatLanguageModel created successfully");
        } catch (Exception e) {

            LOGGER.error("Failed to create PolicyChecker model: {}", e.getMessage(), e);
            throw new IllegalStateException("Cannot create PolicyChecker: " + e.getMessage(), e);
        }

        return AiServices.builder(PolicyChecker.class)
                .chatLanguageModel(chatModel)
                .build();
    }

    /**
     * Resets the singleton instance. Useful for testing or when configuration changes.
     */
    public static synchronized void reset() {

        LOGGER.info("Resetting PolicyChecker singleton instance");
        instance = null;
    }
}
