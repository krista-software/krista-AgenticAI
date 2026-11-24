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

package app.krista.extensions.hook.agentic.telemetry;

import app.krista.extensions.hook.common.models.StepMetadata;

/**
 * Immutable snapshot of conversation and step identity captured at the entry point of an
 * Agentic AI request. Parallels {@link AgenticRequestContext} but holds different fields
 * sourced from the request's {@code MetaData} payload (see {@link StepMetadata}).
 *
 * <p>Used as a low-cardinality dimension on telemetry spans so SigNoz queries can filter
 * by conversation, step, or step type — e.g. "show me all spans for conversation X" or
 * "tool.call latency for step Y in conversation Z".
 *
 * <p>A request-scoped {@link ThreadLocal} ({@link #setCurrent}, {@link #getCurrent},
 * {@link #clearCurrent}) lets the entry point propagate the snapshot to span emitters
 * without plumbing it through every method signature. Always pair {@code setCurrent} with
 * {@code clearCurrent} in a {@code finally} block so it never leaks to a pooled thread's
 * next request.
 *
 * @param conversationId   {@code executionId} (e.g. {@code CEC_xxx}) — unique per conversation execution
 * @param conversationName {@code conversationTitle} — display name of the conversation
 * @param stepType         step type code (e.g. {@code CST_1140}) — identifies the kind of agentic step
 * @param stepName         {@code stepTitle} — display name of the step (e.g. {@code "Agentic AI"})
 */
public record AgenticStepContext(
        String conversationId,
        String conversationName,
        String stepType,
        String stepName
) {

    private static final ThreadLocal<AgenticStepContext> CURRENT = new ThreadLocal<>();

    /**
     * Builds a context snapshot from a {@link StepMetadata}. Tolerates a null input —
     * returns {@code null} in that case so the caller can simply skip telemetry stamping.
     */
    public static AgenticStepContext from(StepMetadata metadata) {

        if (metadata == null) {

            return null;
        }
        return new AgenticStepContext(
                metadata.getExecutionId(),
                metadata.getConversationTitle(),
                metadata.getStepType(),
                metadata.getStepTitle()
        );
    }

    /**
     * Sets the request-scoped context on the current thread. Call at the public entry point.
     * Must be paired with {@link #clearCurrent()} in a {@code finally} block.
     */
    public static void setCurrent(AgenticStepContext context) {

        CURRENT.set(context);
    }

    /**
     * Returns the current thread's step context, or {@code null} if none is set.
     */
    public static AgenticStepContext getCurrent() {

        return CURRENT.get();
    }

    /**
     * Clears the request-scoped context on the current thread. Always call in a
     * {@code finally} block at the public entry point to prevent leakage to the next
     * request served by a pooled thread.
     */
    public static void clearCurrent() {

        CURRENT.remove();
    }
}
