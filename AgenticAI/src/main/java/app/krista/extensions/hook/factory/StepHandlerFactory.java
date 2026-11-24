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

package app.krista.extensions.hook.factory;

import app.krista.extension.executor.Invoker;
import app.krista.extension.executor.impl.AgentAction;
import app.krista.extensions.hook.stepHandlers.*;
import app.krista.extensions.util.KeyValueStore;

/**
 * Factory for creating step execution handlers based on agent action.
 * Uses constructor injection to receive KeyValueStore for persistence support.
 */
public class StepHandlerFactory {

    private final KeyValueStore keyValueStore;

    /**
     * Creates a new StepHandlerFactory with KeyValueStore for session persistence.
     * 
     * @param keyValueStore the KeyValueStore for persisting session data (required, cannot be null)
     * @throws IllegalArgumentException if keyValueStore is null
     */
    public StepHandlerFactory(KeyValueStore keyValueStore) {
        if (keyValueStore == null) {
            throw new IllegalArgumentException("KeyValueStore is required for session persistence. " +
                "This is mandatory for production deployments to ensure session survival across extension restarts.");
        }
        this.keyValueStore = keyValueStore;
    }

    /**
     * Creates the appropriate step handler based on the agent action.
     * 
     * @param agentAction the action type to handle
     * @param invoker the invoker for LLM calls
     * @return the appropriate step execution handler
     * @throws Exception if handler creation fails
     */
    public StepExecutionHandler createStepHandler(AgentAction agentAction, Invoker invoker) throws Exception {
        System.out.println("Creating step handler for action: " + agentAction);
        
        return switch (agentAction) {
            case SUMMARIZE_INFORM -> {
                System.out.println("Creating InformAPersonHandlerSummarize with automatic LLM fallback");
                yield new InformAPersonHandlerSummarize(invoker);
            }
            case SUMMARIZE_ASK -> {
                System.out.println("Creating AskAPersonHandlerSummarize with automatic LLM fallback");
                yield new AskAPersonHandlerSummarize(invoker);
            }
            case EXTRACT_VARIABLES -> {
                System.out.println("Creating AskAPersonHandlerExtractVariables with automatic LLM fallback");
                yield new AskAPersonHandlerExtractVariables(invoker);
            }
            case ORCHESTRATE -> {
                System.out.println("Creating OrchestrationHandler with automatic LLM fallback and session persistence");
                yield new OrchestrationHandler(invoker, keyValueStore);
            }
        };
    }
}
