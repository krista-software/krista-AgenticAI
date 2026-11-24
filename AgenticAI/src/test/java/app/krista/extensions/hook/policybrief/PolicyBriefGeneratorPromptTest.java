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

package app.krista.extensions.hook.policybrief;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies the literal content of the {@code @SystemMessage} and {@code @UserMessage} annotations
 * on {@link PolicyBriefGenerator}.
 *
 * These tests are the regression guard for prompt injection defences (KR-20399): the prompt was
 * updated to wrap untrusted data in XML delimiters ({@code <krista_user_input>} and
 * {@code <krista_ext_data>}) and to include an explicit UNTRUSTED DATA security instruction.
 * The tests confirm:
 *
 *   1. The security instruction "UNTRUSTED DATA" is present in the system message.
 *   2. The intent is wrapped in {@code <krista_user_input>} tags (user-supplied).
 *   3. The RAG results are wrapped in {@code <krista_ext_data>} tags (externally-sourced).
 *   4. Tool definitions are NOT wrapped — they are system-controlled content.
 *   5. The user input in the user message is wrapped in {@code <krista_user_input>} tags.
 *   6. The prompt uses operations-only wording and instructs first-draft creation via replace_brief.
 *   7. The prompt has substantial content (not accidentally emptied).
 *
 * These tests are intentionally asserting on raw prompt text. That makes them fragile to
 * cosmetic rewording, but that fragility is desirable here: any prompt edit that touches
 * the security delimiters or injection defences must force a deliberate update to these tests,
 * preventing accidental regression.
 *
 * No LLM calls are made — only the annotation metadata is inspected.
 */
@DisplayName("PolicyBriefGenerator @SystemMessage / @UserMessage content")
class PolicyBriefGeneratorPromptTest {

    private static final String SYSTEM_MESSAGE_TEXT = extractAnnotationText(true);
    private static final String USER_MESSAGE_TEXT = extractAnnotationText(false);

    private static String extractAnnotationText(boolean system) {

        try {

            Method generateOrRefine = PolicyBriefGenerator.class.getMethod(
                "generateOrRefine",
                String.class,
                String.class,
                String.class,
                String.class,
                String.class,
                String.class
            );

            if (system) {

                dev.langchain4j.service.SystemMessage annotation =
                    generateOrRefine.getAnnotation(dev.langchain4j.service.SystemMessage.class);

                assertNotNull(annotation,
                    "generateOrRefine must carry a @SystemMessage annotation");

                String[] lines = annotation.value();
                assertFalse(lines == null || lines.length == 0,
                    "@SystemMessage value array must not be empty");

                return String.join("\n", lines);

            } else {

                dev.langchain4j.service.UserMessage annotation =
                    generateOrRefine.getAnnotation(dev.langchain4j.service.UserMessage.class);

                assertNotNull(annotation,
                    "generateOrRefine must carry a @UserMessage annotation");

                String[] lines = annotation.value();
                assertFalse(lines == null || lines.length == 0,
                    "@UserMessage value array must not be empty");

                return String.join("\n", lines);
            }

        } catch (NoSuchMethodException e) {

            throw new AssertionError(
                "generateOrRefine method signature has changed — update this test to match the new signature",
                e
            );
        }
    }

    // -------------------------------------------------------------------------
    // TC-PBG-1: Security instruction is present in system message
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("TC-PBG-1: System prompt contains 'UNTRUSTED DATA' (security instruction present)")
    void systemPrompt_containsSecurityInstruction() {

        assertTrue(
            SYSTEM_MESSAGE_TEXT.contains("UNTRUSTED DATA"),
            "The system prompt must contain 'UNTRUSTED DATA' — this security instruction "
            + "teaches the LLM to treat delimited content as opaque data, not as commands"
        );
    }

    // -------------------------------------------------------------------------
    // TC-PBG-2: intent is wrapped in <krista_user_input> tags in system message
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("TC-PBG-2: System prompt wraps {{intent}} in <krista_user_input> tags")
    void systemPrompt_wrapsIntentInUserInputTags() {

        assertTrue(
            SYSTEM_MESSAGE_TEXT.contains("<krista_user_input>{{intent}}</krista_user_input>"),
            "The system prompt must wrap {{intent}} in <krista_user_input> tags — "
            + "intent is user-supplied data and must be delimited for injection defence"
        );
    }

    // -------------------------------------------------------------------------
    // TC-PBG-3: ragResults is wrapped in <krista_ext_data> tags in system message
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("TC-PBG-3: System prompt wraps {{ragResults}} in <krista_ext_data> tags")
    void systemPrompt_wrapsRagResultsInExtDataTags() {

        assertTrue(
            SYSTEM_MESSAGE_TEXT.contains("<krista_ext_data>{{ragResults}}</krista_ext_data>"),
            "The system prompt must wrap {{ragResults}} in <krista_ext_data> tags — "
            + "RAG results are externally-sourced data and must be delimited for injection defence"
        );
    }

    // -------------------------------------------------------------------------
    // TC-PBG-4: toolDefinitions is NOT wrapped (system-controlled content)
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("TC-PBG-4: System prompt does NOT wrap {{toolDefinitions}} in <krista_ext_data> tags")
    void systemPrompt_doesNotWrapToolDefinitionsInExtDataTags() {

        assertFalse(
            SYSTEM_MESSAGE_TEXT.contains("<krista_ext_data>{{toolDefinitions}}"),
            "The system prompt must NOT wrap {{toolDefinitions}} in <krista_ext_data> tags — "
            + "tool definitions are system-controlled content, not externally-sourced data, "
            + "and should be presented as trusted instructions"
        );
    }

    // -------------------------------------------------------------------------
    // TC-PBG-5: userInput is wrapped in <krista_user_input> tags in user message
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("TC-PBG-5: User message wraps {{userInput}} in <krista_user_input> tags")
    void userMessage_wrapsUserInputInUserInputTags() {

        assertTrue(
            USER_MESSAGE_TEXT.contains("<krista_user_input>{{userInput}}</krista_user_input>"),
            "The user message must wrap {{userInput}} in <krista_user_input> tags — "
            + "user input is untrusted data and must be delimited for injection defence"
        );
    }

    // -------------------------------------------------------------------------
    // TC-PBG-6: Prompt has substantial content (not accidentally truncated)
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("TC-PBG-6: System prompt has operations-only draft instructions")
    void systemPrompt_usesOperationsOnlyContract() {

        assertTrue(
            SYSTEM_MESSAGE_TEXT.contains("replace_brief"),
            "The system prompt must mention replace_brief for operations-only draft creation"
        );
        assertTrue(
            SYSTEM_MESSAGE_TEXT.contains("first full draft")
                && SYSTEM_MESSAGE_TEXT.contains("single replace_brief operation"),
            "The system prompt must explicitly instruct the model to create the initial draft via replace_brief"
        );
        assertFalse(
            SYSTEM_MESSAGE_TEXT.contains("\"policyBrief\""),
            "The system prompt must not present policyBrief as a valid response shape"
        );
        assertTrue(
            SYSTEM_MESSAGE_TEXT.contains("Internal rule IDs like R1 and R2 are application-owned references")
                && SYSTEM_MESSAGE_TEXT.contains("must never appear in author-facing rule text"),
            "The system prompt must explicitly forbid leaking internal rule IDs into visible rule text"
        );
        assertTrue(
            SYSTEM_MESSAGE_TEXT.contains("Start with customer verification")
                && SYSTEM_MESSAGE_TEXT.contains("Log all approval decisions"),
            "replace_brief examples must use plain-prose rule text"
        );
        assertFalse(
            SYSTEM_MESSAGE_TEXT.contains("\"replacementRules\": [\"R1.")
                || SYSTEM_MESSAGE_TEXT.contains("\"replacementRules\":[\"R1.")
                || SYSTEM_MESSAGE_TEXT.contains("\"text\": \"R1.")
                || SYSTEM_MESSAGE_TEXT.contains("\"text\":\"R1."),
            "Prompt examples must not teach visible R# prefixes in rule text"
        );
    }

    @Test
    @DisplayName("TC-PBG-7: System prompt has substantial content (>200 chars)")
    void systemPrompt_hasSubstantialContent() {

        assertTrue(
            SYSTEM_MESSAGE_TEXT.length() > 200,
            "The system prompt must have substantial content (>200 chars). "
            + "If this fails the annotation value may have been accidentally cleared"
        );
    }
}
