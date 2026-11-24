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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Represents metadata about a context variable used in AI operations.
 * Contains information about the variable's name, type, description, and requirements.
 * Supports complex types (List, Table, MultiField, Entity) with sub-context variables.
 */
public class AIContextVariable {

    private String name;
    private String type;
    private String description;
    private boolean required;
    private List<AIContextVariable> subContextVariables;
    private Map<String, Object> metaData;

    public AIContextVariable() {
        this.subContextVariables = new ArrayList<>();
        this.metaData = new HashMap<>();
    }

    public AIContextVariable(String name, String type, String description, boolean required) {
        this();
        this.name = name;
        this.type = type;
        this.description = description;
        this.required = required;
    }
    
    public AIContextVariable(String name, String type) {
        this(name, type, null, false);
    }
    
    public AIContextVariable(String name, String type, List<AIContextVariable> subContextVariables) {
        this(name, type, null, false);
        this.subContextVariables = subContextVariables != null ? subContextVariables : new ArrayList<>();
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public boolean isRequired() {
        return required;
    }

    public void setRequired(boolean required) {
        this.required = required;
    }

    public List<AIContextVariable> getSubContextVariables() {
        return subContextVariables;
    }

    public void setSubContextVariables(List<AIContextVariable> subContextVariables) {
        this.subContextVariables = subContextVariables != null ? subContextVariables : new ArrayList<>();
    }

    public Map<String, Object> getMetaData() {
        return metaData;
    }

    public void setMetaData(Map<String, Object> metaData) {
        this.metaData = metaData != null ? metaData : new HashMap<>();
    }

    @Override
    public String toString() {
        return "AIContextVariable{" +
                "name='" + name + '\'' +
                ", type='" + type + '\'' +
                ", description='" + description + '\'' +
                ", required=" + required +
                '}';
    }
}
