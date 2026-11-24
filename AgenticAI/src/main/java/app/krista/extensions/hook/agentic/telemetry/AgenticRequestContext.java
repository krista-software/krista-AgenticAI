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

import app.krista.extension.executor.Invoker;
import app.krista.ksdk.workspace.Workspace;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Immutable snapshot of workspace and invoker identity strings captured at the entry point
 * of an Agentic AI request.
 *
 * <p>Extracting these values once — on the request thread where {@link Workspace} and
 * {@link Invoker} are accessible — allows downstream service methods and telemetry emitters
 * to remain simple, taking only serialisable strings instead of platform objects.
 *
 * <p>A request-scoped {@link ThreadLocal} ({@link #setCurrent}, {@link #getCurrent},
 * {@link #clearCurrent}) lets the entry point propagate the snapshot to session-creation
 * code without plumbing it through every method signature. Call {@code clearCurrent} in a
 * {@code finally} block so the value never leaks to a pooled thread's next request.
 *
 * @param workspaceId   workspace identifier, or {@code null} if unavailable
 * @param workspaceName workspace display name, or {@code null} if unavailable
 * @param invokerId     invoker identifier, or {@code null} if unavailable
 * @param invokerName   invoker display name, or {@code null} if unavailable
 */
public record AgenticRequestContext(
        String workspaceId,
        String workspaceName,
        String invokerId,
        String invokerName
) {

    private static final Logger LOGGER = LoggerFactory.getLogger(AgenticRequestContext.class);

    private static final ThreadLocal<AgenticRequestContext> CURRENT = new ThreadLocal<>();

    /**
     * Extracts identity strings from platform objects, tolerating any exception per field.
     *
     * <p>Preference order for {@code workspaceId} is:
     * <ol>
     *   <li>{@link Invoker#getWorkspaceId()} — reliable in invoker-driven flows where the
     *       {@link Workspace} accessors would otherwise throw {@code MustAuthenticateException}.</li>
     *   <li>{@link Workspace#getWorkspaceId()} — fallback for contexts where {@code Invoker}
     *       is absent or did not return an ID.</li>
     * </ol>
     *
     * <p>{@code workspaceName} is only obtainable from {@link Workspace}; if that accessor
     * throws (e.g. {@code MustAuthenticateException}), the name is simply left {@code null}
     * — telemetry will omit the attribute rather than failing the request.
     *
     * @param invoker   the extension invoker; may be {@code null}
     * @param workspace the workspace; may be {@code null}
     * @return a context snapshot; individual fields may be {@code null} if unavailable
     */
    public static AgenticRequestContext from(Invoker invoker, Workspace workspace) {

        String invId = null;
        String invName = null;
        String workspaceIdFromInvoker = null;
        if (invoker != null) {

            invId = safeCall(invoker::getInvokerId, "invokerId");
            invName = safeCall(invoker::getInvokerName, "invokerName");
            workspaceIdFromInvoker = safeCall(invoker::getWorkspaceId, "invokerWorkspaceId");
        }

        String workspaceIdFromWorkspace = null;
        String wsName = null;
        if (workspace != null) {

            workspaceIdFromWorkspace = safeCall(workspace::getWorkspaceId, "workspaceId");
            wsName = safeCall(workspace::getWorkspaceName, "workspaceName");
        }

        // Prefer Invoker-sourced workspaceId; fall back to Workspace-sourced one.
        String wsId = workspaceIdFromInvoker != null ? workspaceIdFromInvoker : workspaceIdFromWorkspace;

        return new AgenticRequestContext(wsId, wsName, invId, invName);
    }

    /**
     * Sets the request-scoped context on the current thread. Call at the public entry point.
     * Must be paired with {@link #clearCurrent()} in a {@code finally} block.
     */
    public static void setCurrent(AgenticRequestContext context) {

        CURRENT.set(context);
    }

    /**
     * Returns the current thread's request context, or {@code null} if none is set.
     */
    public static AgenticRequestContext getCurrent() {

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

    @FunctionalInterface
    private interface StringSupplier {

        String get() throws Exception;
    }

    /**
     * Executes a platform accessor and returns its result, or {@code null} if it throws.
     * Logs at DEBUG so expected failures (e.g. {@code MustAuthenticateException} from
     * {@link Workspace} accessors in invoker-driven flows) don't spam production logs.
     */
    private static String safeCall(StringSupplier supplier, String fieldName) {

        try {

            return supplier.get();
        } catch (Throwable t) {

            LOGGER.debug("[TELEMETRY] Could not extract {} for telemetry: {}", fieldName, t.getMessage());
            return null;
        }
    }
}
