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

package app.krista.extensions.hook.agentic.planner;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies the literal content of the {@code @SystemMessage} and {@code @UserMessage} annotations
 * on {@link PolicyChecker}.
 *
 * These tests are the regression guard for prompt injection defences (KR-20399): the prompt was
 * updated to wrap untrusted data in XML delimiters ({@code <krista_user_input>} and
 * {@code <krista_ext_data>}) and to include an explicit UNTRUSTED DATA security instruction.
 * The tests confirm:
 *
 *   1. The security instruction "UNTRUSTED DATA" is present in the system message.
 *   2. The user goal is wrapped in {@code <krista_user_input>} tags.
 *   3. The policy brief is wrapped in {@code <krista_ext_data>} tags.
 *   4. The tool history in the user message is wrapped in {@code <krista_ext_data>} tags.
 *   5. The planned tool in the user message is wrapped in {@code <krista_ext_data>} tags.
 *   6. The prompt has substantial content (not accidentally emptied).
 *
 * These tests are intentionally asserting on raw prompt text. That makes them fragile to
 * cosmetic rewording, but that fragility is desirable here: any prompt edit that touches
 * the security delimiters or injection defences must force a deliberate update to these tests,
 * preventing accidental regression.
 *
 * No LLM calls are made — only the annotation metadata is inspected.
 */
@DisplayName("PolicyChecker @SystemMessage / @UserMessage content")
class PolicyCheckerPromptTest {

    private static final String SYSTEM_MESSAGE_TEXT = extractAnnotationText(true);
    private static final String USER_MESSAGE_TEXT = extractAnnotationText(false);

    private static String extractAnnotationText(boolean system) {

        try {

            Method evaluate = PolicyChecker.class.getMethod(
                "evaluate",
                String.class,
                String.class,
                String.class,
                String.class,
                String.class
            );

            if (system) {

                dev.langchain4j.service.SystemMessage annotation =
                    evaluate.getAnnotation(dev.langchain4j.service.SystemMessage.class);

                assertNotNull(annotation,
                    "evaluate must carry a @SystemMessage annotation");

                String[] lines = annotation.value();
                assertFalse(lines == null || lines.length == 0,
                    "@SystemMessage value array must not be empty");

                return String.join("\n", lines);

            } else {

                dev.langchain4j.service.UserMessage annotation =
                    evaluate.getAnnotation(dev.langchain4j.service.UserMessage.class);

                assertNotNull(annotation,
                    "evaluate must carry a @UserMessage annotation");

                String[] lines = annotation.value();
                assertFalse(lines == null || lines.length == 0,
                    "@UserMessage value array must not be empty");

                return String.join("\n", lines);
            }

        } catch (NoSuchMethodException e) {

            throw new AssertionError(
                "evaluate method signature has changed — update this test to match the new signature",
                e
            );
        }
    }

    // -------------------------------------------------------------------------
    // TC-PC-1: Security instruction is present in system message
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("TC-PC-1: System prompt contains 'UNTRUSTED DATA' (security instruction present)")
    void systemPrompt_containsSecurityInstruction() {

        assertTrue(
            SYSTEM_MESSAGE_TEXT.contains("UNTRUSTED DATA"),
            "The system prompt must contain 'UNTRUSTED DATA' — this security instruction "
            + "teaches the LLM to treat delimited content as opaque data, not as commands"
        );
    }

    // -------------------------------------------------------------------------
    // TC-PC-2: userGoal is wrapped in <krista_user_input> tags in system message
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("TC-PC-2: System prompt wraps {{userGoal}} in <krista_user_input> tags")
    void systemPrompt_wrapsUserGoalInUserInputTags() {

        assertTrue(
            SYSTEM_MESSAGE_TEXT.contains("<krista_user_input>{{userGoal}}</krista_user_input>"),
            "The system prompt must wrap {{userGoal}} in <krista_user_input> tags — "
            + "user goals are untrusted input and must be delimited for injection defence"
        );
    }

    // -------------------------------------------------------------------------
    // TC-PC-3: policyBrief is wrapped in <krista_ext_data> tags in system message
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("TC-PC-3: System prompt wraps {{policyBrief}} in <krista_ext_data> tags")
    void systemPrompt_wrapsPolicyBriefInExtDataTags() {

        assertTrue(
            SYSTEM_MESSAGE_TEXT.contains("<krista_ext_data>{{policyBrief}}</krista_ext_data>"),
            "The system prompt must wrap {{policyBrief}} in <krista_ext_data> tags — "
            + "policy briefs are externally-sourced data and must be delimited for injection defence"
        );
    }

    // -------------------------------------------------------------------------
    // TC-PC-4: toolHistory is wrapped in <krista_ext_data> tags in user message
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("TC-PC-4: User message wraps {{toolHistory}} in <krista_ext_data> tags")
    void userMessage_wrapsToolHistoryInExtDataTags() {

        assertTrue(
            USER_MESSAGE_TEXT.contains("<krista_ext_data>{{toolHistory}}</krista_ext_data>"),
            "The user message must wrap {{toolHistory}} in <krista_ext_data> tags — "
            + "tool history is externally-sourced data and must be delimited for injection defence"
        );
    }

    // -------------------------------------------------------------------------
    // TC-PC-5: plannedTool is wrapped in <krista_ext_data> tags in user message
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("TC-PC-5: User message wraps {{plannedTool}} in <krista_ext_data> tags")
    void userMessage_wrapsPlannedToolInExtDataTags() {

        assertTrue(
            USER_MESSAGE_TEXT.contains("<krista_ext_data>{{plannedTool}}"),
            "The user message must wrap {{plannedTool}} in <krista_ext_data> tags — "
            + "planned tool details are externally-sourced data and must be delimited for injection defence"
        );
    }

    // -------------------------------------------------------------------------
    // TC-PC-6: Prompt has substantial content (not accidentally truncated)
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("TC-PC-6: System prompt has substantial content (>200 chars)")
    void systemPrompt_hasSubstantialContent() {

        assertTrue(
            SYSTEM_MESSAGE_TEXT.length() > 200,
            "The system prompt must have substantial content (>200 chars). "
            + "If this fails the annotation value may have been accidentally cleared"
        );
    }
}
