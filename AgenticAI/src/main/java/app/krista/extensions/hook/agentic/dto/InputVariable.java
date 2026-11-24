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
 * Represents an input variable with its metadata and value.
 * Used to pass input data to the AI agent with context about the variable.
 */
public class InputVariable {

    private AIContextVariable aiContextVariable;
    private Object value;

    public InputVariable() {
    }

    public InputVariable(AIContextVariable aiContextVariable, Object value) {
        this.aiContextVariable = aiContextVariable;
        this.value = value;
    }

    public AIContextVariable getAiContextVariable() {
        return aiContextVariable;
    }

    public void setAiContextVariable(AIContextVariable aiContextVariable) {
        this.aiContextVariable = aiContextVariable;
    }

    public Object getValue() {
        return value;
    }

    public void setValue(Object value) {
        this.value = value;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("{");
        boolean hasPrevious = false;

        if (aiContextVariable != null && !aiContextVariable.toString().isEmpty()) {
            sb.append("variable:'").append(aiContextVariable.toString()).append('\'');
            hasPrevious = true;
        }

        if (value != null) {
            if (hasPrevious) sb.append(", ");
            sb.append("value:'").append(value).append('\'');
        }

        sb.append('}');
        return sb.toString();
    }
}
