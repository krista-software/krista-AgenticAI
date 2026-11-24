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

package app.krista.extensions.hook.agentic.session;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Typed activity-feed event recorded against an {@link AgenticSession}. Each variant carries
 * exactly the fields its event needs, eliminating string-literal keys at call sites. The
 * {@link #details()} method returns the payload merged into {@code session_summary.activityFeed[*].details}.
 */
public sealed interface ActivityEvent
    permits ActivityEvent.SessionStarted,
            ActivityEvent.ChildSessionStarted,
            ActivityEvent.ChildSessionDelegated,
            ActivityEvent.ToolDispatched,
            ActivityEvent.ToolResult,
            ActivityEvent.SessionCompleted,
            ActivityEvent.SessionErrored,
            ActivityEvent.GuardrailViolation,
            ActivityEvent.ExecutionLimitExceeded,
            ActivityEvent.PersistenceFailure,
            ActivityEvent.UnexpectedException {

    /** Event-type discriminator written as the {@code type} field in the activity feed entry. */
    String type();

    /** Per-event payload. Uses {@link LinkedHashMap} so JSON key order matches the field declaration. */
    Map<String, Object> details();

    record SessionStarted(int toolsCount, List<String> inputVarNames) implements ActivityEvent {
        public String type() { return "session_started"; }
        public Map<String, Object> details() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("toolsCount", toolsCount);
            m.put("inputVarNames", inputVarNames);
            return m;
        }
    }

    record ChildSessionStarted(String parentSessionId, int toolsCount, List<String> inputVarNames) implements ActivityEvent {
        public String type() { return "child_session_started"; }
        public Map<String, Object> details() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("parentSessionId", parentSessionId);
            m.put("toolsCount", toolsCount);
            m.put("inputVarNames", inputVarNames);
            return m;
        }
    }

    record ChildSessionDelegated(String childSessionId) implements ActivityEvent {
        public String type() { return "child_session_delegated"; }
        public Map<String, Object> details() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("childSessionId", childSessionId);
            return m;
        }
    }

    record ToolDispatched(String toolName) implements ActivityEvent {
        public String type() { return "tool_dispatched"; }
        public Map<String, Object> details() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("toolName", toolName);
            return m;
        }
    }

    record ToolResult(String toolName, String status) implements ActivityEvent {
        public String type() { return "tool_result"; }
        public Map<String, Object> details() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("toolName", toolName);
            m.put("status", status);
            return m;
        }
    }

    record SessionCompleted(boolean hasFinalResponse) implements ActivityEvent {
        public String type() { return "session_completed"; }
        public Map<String, Object> details() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("hasFinalResponse", hasFinalResponse);
            return m;
        }
    }

    /** {@code errorMessage} may be null; in that case the details map is empty. */
    record SessionErrored(String errorMessage) implements ActivityEvent {
        public String type() { return "session_errored"; }
        public Map<String, Object> details() {
            Map<String, Object> m = new LinkedHashMap<>();
            if (errorMessage != null) {
                m.put("errorMessage", errorMessage);
            }
            return m;
        }
    }

    record GuardrailViolation(String reason) implements ActivityEvent {
        public String type() { return "guardrail_violation"; }
        public Map<String, Object> details() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("reason", reason);
            return m;
        }
    }

    /** {@code currentValue}/{@code maxValue} are doubles so iteration, cost, and time limits share one shape. */
    record ExecutionLimitExceeded(String limitType, double currentValue, double maxValue) implements ActivityEvent {
        public String type() { return "execution_limit_exceeded"; }
        public Map<String, Object> details() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("limitType", limitType);
            m.put("currentValue", currentValue);
            m.put("maxValue", maxValue);
            return m;
        }
    }

    record PersistenceFailure(String phase, String errorCode) implements ActivityEvent {
        public String type() { return "persistence_failure"; }
        public Map<String, Object> details() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("phase", phase);
            m.put("errorCode", errorCode);
            return m;
        }
    }

    record UnexpectedException(String exceptionClass, String message) implements ActivityEvent {
        public String type() { return "unexpected_exception"; }
        public Map<String, Object> details() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("exception", exceptionClass);
            m.put("message", message != null ? message : "");
            return m;
        }
    }
}
