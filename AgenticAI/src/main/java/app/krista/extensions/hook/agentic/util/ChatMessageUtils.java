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

package app.krista.extensions.hook.agentic.util;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;

/**
 * Utility class for working with ChatMessage objects.
 * Provides helper methods to extract text from different message types.
 */
public class ChatMessageUtils {

    /**
     * Extracts text content from a ChatMessage.
     * This method handles the deprecated text() method by using type-specific methods instead.
     *
     * @param message the ChatMessage to extract text from
     * @return the text content of the message, or empty string if message is null or has no text
     */
    public static String getText(ChatMessage message) {

        if (message == null) {

            return "";
        }

        // Use type-specific methods based on message type
        if (message instanceof UserMessage userMessage) {

            return userMessage.singleText();
        } else if (message instanceof AiMessage aiMessage) {

            return aiMessage.text();
        } else if (message instanceof SystemMessage systemMessage) {

            return systemMessage.text();
        } else if (message instanceof ToolExecutionResultMessage toolMessage) {

            return toolMessage.text();
        }

        // Fallback for unknown message types
        return "";
    }
}

