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

package app.krista.extensions.hook.common.models;

import app.krista.extensions.hook.Constants;

import java.util.HashMap;
import java.util.Map;

/**
 * Represents metadata information about a step execution context.
 * This class provides structured access to metadata and can convert to Map format for prompts.
 */
public class StepMetadata {
    private final String conversationTitle;
    private final String executionId;
    private final String workspaceName;
    private final String topicName;
    private final String stepTitle;
    private final String stepType;
    private final String conversationDescription;

    public StepMetadata(String conversationTitle, String executionId, String workspaceName, 
                       String topicName, String stepTitle, String stepType, String conversationDescription) {
        this.conversationTitle = conversationTitle;
        this.executionId = executionId;
        this.workspaceName = workspaceName;
        this.topicName = topicName;
        this.stepTitle = stepTitle;
        this.stepType = stepType;
        this.conversationDescription = conversationDescription;
    }

    /**
     * Creates StepMetadata from a Map (typically from StepInput metadata).
     */
    public static StepMetadata fromMap(Map<String, Object> metaDataMap) {
        return new StepMetadata(
            (String) metaDataMap.get(Constants.CONVERSATION_TITLE),
            (String) metaDataMap.get(Constants.EXECUTION_ID),
            (String) metaDataMap.get(Constants.WORKSPACE_NAME),
            (String) metaDataMap.get(Constants.TOPIC_NAME),
            (String) metaDataMap.get(Constants.STEP_TITLE),
            (String) metaDataMap.get(Constants.STEP_TYPE),
            (String) metaDataMap.get(Constants.CONVERSATION_DESCRIPTION)
        );
    }

    /**
     * Converts to Map format suitable for prompts.
     * Excludes ExecutionId and StepType as they are not needed in prompts.
     */
    public Map<String, Object> toPromptMap() {
        Map<String, Object> promptMap = new HashMap<>();
        promptMap.put(Constants.CONVERSATION_TITLE, conversationTitle);
        promptMap.put(Constants.WORKSPACE_NAME, workspaceName);
        promptMap.put(Constants.TOPIC_NAME, topicName);
        promptMap.put(Constants.STEP_TITLE, stepTitle);
        promptMap.put(Constants.CONVERSATION_DESCRIPTION, conversationDescription);
        return promptMap;
    }

    // Getter methods
    public String getConversationTitle() {
        return conversationTitle;
    }

    public String getStepTitle() {
        return stepTitle;
    }

    public String getStepType() {
        return stepType;
    }

    public String getExecutionId() {
        return executionId;
    }

    @Override
    public String toString() {
        return "StepMetadata{" +
                "conversationTitle='" + conversationTitle + '\'' +
                ", executionId='" + executionId + '\'' +
                ", workspaceName='" + workspaceName + '\'' +
                ", topicName='" + topicName + '\'' +
                ", stepTitle='" + stepTitle + '\'' +
                ", stepType='" + stepType + '\'' +
                ", conversationDescription='" + conversationDescription + '\'' +
                '}';
    }
}
