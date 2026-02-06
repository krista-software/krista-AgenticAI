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
import app.krista.extensions.hook.stepHandlers.AskAPersonHandlerExtractVariables;
import app.krista.extensions.hook.stepHandlers.AskAPersonHandlerSummarize;
import app.krista.extensions.hook.stepHandlers.InformAPersonHandlerSummarize;
import app.krista.extensions.hook.stepHandlers.StepExecutionHandler;

/**
 * Factory for creating step execution handlers based on agent action
 */
public class StepHandlerFactory {

    /**
     * Creates the appropriate step handler based on the agent action
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
        };
    }
}
