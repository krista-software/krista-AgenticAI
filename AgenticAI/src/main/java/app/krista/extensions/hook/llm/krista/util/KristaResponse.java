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

package app.krista.extensions.hook.llm.krista.util;

import java.util.List;
import org.apache.commons.lang3.builder.ReflectionToStringBuilder;

@SuppressWarnings("unused")
public class KristaResponse {

    public String id;
    public String object;
    public long created;
    public String model;
    public List<Choice> choices;
    public Usage usage;

    public static class Choice {
        public int index;
        public Message message;
        public String finish_reason;

        public static class Message {
            public String role;
            public String content;

            public String getRole() {
                return role;
            }

            public String getContent() {
                return content;
            }

            @Override
            public String toString() {
                return ReflectionToStringBuilder.toString(this);
            }
        }

        public int getIndex() {
            return index;
        }

        public Message getMessage() {
            return message;
        }

        public String getFinishReason() {
            return finish_reason;
        }

        @Override
        public String toString() {
            return ReflectionToStringBuilder.toString(this);
        }
    }

    public static class Usage {
        public int prompt_tokens;
        public int completion_tokens;
        public int total_tokens;

        public int getPromptTokens() {
            return prompt_tokens;
        }

        public int getCompletionTokens() {
            return completion_tokens;
        }

        public int getTotalTokens() {
            return total_tokens;
        }

        @Override
        public String toString() {
            return ReflectionToStringBuilder.toString(this);
        }
    }

    public String getId() {
        return id;
    }

    public String getObject() {
        return object;
    }

    public long getCreated() {
        return created;
    }

    public String getModel() {
        return model;
    }

    public List<Choice> getChoices() {
        return choices;
    }

    public Usage getUsage() {
        return usage;
    }

    public String getAnswer() {
        if (choices != null && !choices.isEmpty()) {
            return choices.getFirst().getMessage().getContent();
        }
        return "N/A";
    }

    @Override
    public String toString() {
        return ReflectionToStringBuilder.toString(this);
    }
}
