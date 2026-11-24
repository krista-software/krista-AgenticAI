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

/**
 * Represents an expected output with its metadata and generation instructions.
 * Used to guide the AI agent on what outputs to generate.
 */
public class ExpectedOutput {

    private AIContextVariable aiContextVariable;
    private String instruction;
    private String operationType;

    public ExpectedOutput() {
    }

    public ExpectedOutput(AIContextVariable aiContextVariable, String instruction) {
        this.aiContextVariable = aiContextVariable;
        this.instruction = instruction;
    }

    public ExpectedOutput(AIContextVariable aiContextVariable, String instruction, String operationType) {
        this.aiContextVariable = aiContextVariable;
        this.instruction = instruction;
        this.operationType = operationType;
    }

    public AIContextVariable getAiContextVariable() {
        return aiContextVariable;
    }

    public void setAiContextVariable(AIContextVariable aiContextVariable) {
        this.aiContextVariable = aiContextVariable;
    }

    public String getInstruction() {
        return instruction;
    }

    public void setInstruction(String instruction) {
        this.instruction = instruction;
    }

    public String getOperationType() {
        return operationType;
    }

    public void setOperationType(String operationType) {
        this.operationType = operationType;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("{");
        boolean hasPrevious = false;

        if (aiContextVariable != null && !aiContextVariable.toString().isEmpty()) {
            sb.append("variable:'").append(aiContextVariable.toString()).append('\'');
            hasPrevious = true;
        }

        if (instruction != null && !instruction.isEmpty()) {
            if (hasPrevious) sb.append(", ");
            sb.append("instruction:'").append(instruction).append('\'');
            hasPrevious = true;
        }

        if (operationType != null) {
            if (hasPrevious) sb.append(", ");
            sb.append("\"operationType\":\"").append(operationType).append("\"");
        }

        sb.append('}');
        return sb.toString();
    }
}
