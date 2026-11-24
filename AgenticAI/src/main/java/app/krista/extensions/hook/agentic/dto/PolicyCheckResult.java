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

package app.krista.extensions.hook.agentic.dto;

public class PolicyCheckResult {

    public enum Decision {

        ALLOWED,
        BLOCKED
    }

    private Decision decision;
    private String violatedPolicy;
    private String reason;
    private String severity;

    public PolicyCheckResult() {

    }

    public PolicyCheckResult(Decision decision, String violatedPolicy, String reason, String severity) {

        this.decision = decision;
        this.violatedPolicy = violatedPolicy;
        this.reason = reason;
        this.severity = severity;
    }

    public static PolicyCheckResult allowed(String reason) {

        return new PolicyCheckResult(Decision.ALLOWED, null, reason, null);
    }

    public static PolicyCheckResult blocked(String violatedPolicy, String reason, String severity) {

        return new PolicyCheckResult(Decision.BLOCKED, violatedPolicy, reason, severity);
    }

    public static PolicyCheckResult checkerError(String errorMessage) {

        return new PolicyCheckResult(Decision.BLOCKED, null, "PolicyChecker error: " + errorMessage, "HIGH");
    }

    public Decision getDecision() {

        return decision;
    }

    public void setDecision(Decision decision) {

        this.decision = decision;
    }

    public String getViolatedPolicy() {

        return violatedPolicy;
    }

    public void setViolatedPolicy(String violatedPolicy) {

        this.violatedPolicy = violatedPolicy;
    }

    public String getReason() {

        return reason;
    }

    public void setReason(String reason) {

        this.reason = reason;
    }

    public String getSeverity() {

        return severity;
    }

    public void setSeverity(String severity) {

        this.severity = severity;
    }

    @Override
    public String toString() {

        return "PolicyCheckResult{" +
                "decision=" + decision +
                ", violatedPolicy='" + violatedPolicy + '\'' +
                ", reason='" + reason + '\'' +
                ", severity='" + severity + '\'' +
                '}';
    }
}
