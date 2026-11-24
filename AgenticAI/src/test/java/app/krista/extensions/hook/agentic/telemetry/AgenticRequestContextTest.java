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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Unit tests for {@link AgenticRequestContext#from(app.krista.extension.executor.Invoker,
 * app.krista.ksdk.workspace.Workspace)}. The factory is called on every request entry and
 * must tolerate missing platform objects without throwing — telemetry should degrade to
 * absent attributes rather than failing the request.
 */
class AgenticRequestContextTest {

    @Test
    void fromNullInputsYieldsContextWithAllNullFields() {
        AgenticRequestContext ctx = AgenticRequestContext.from(null, null);

        assertNotNull(ctx, "from() must never return null even when both inputs are null");
        assertNull(ctx.workspaceId());
        assertNull(ctx.workspaceName());
        assertNull(ctx.invokerId());
        assertNull(ctx.invokerName());
    }
}
