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
 * Verifies the literal content of the {@code @SystemMessage} annotation on {@link ToolPlanner}.
 *
 * These tests are the regression guard for KR-20396: the prompt was changed to teach the LLM
 * that {@code ask_a_person} always returns {@code {"userResponse": "<user's reply>"}} rather than
 * named output keys. The tests confirm:
 *
 *   1. The phrase "userResponse" appears in the prompt — present in STEP 3, STEP 4, the example
 *      workflow, and CRITICAL RULE 10.
 *   2. The old phrase "userConfirmed" does NOT appear anywhere in the prompt — it was the
 *      incorrect output key that caused silent data loss when the LLM supplied multiple
 *      outputVariableNames but only the first was consumed.
 *   3. CRITICAL RULE 10 explicitly states the contract so the LLM cannot infer separate keys.
 *   4. The example workflow demonstrates extraction from userResponse, not individual variable
 *      assignment.
 *
 * These tests are intentionally asserting on raw prompt text. That makes them fragile to
 * cosmetic rewording, but that fragility is desirable here: any prompt edit that touches
 * the userResponse/userConfirmed semantics must force a deliberate update to these tests,
 * preventing accidental regression.
 *
 * No LLM calls are made — only the annotation metadata is inspected.
 */
@DisplayName("ToolPlanner @SystemMessage content")
class ToolPlannerSystemPromptTest {

    private static final String SYSTEM_MESSAGE_TEXT = extractSystemMessageText();

    private static String extractSystemMessageText() {

        try {

            Method planNextStep = ToolPlanner.class.getMethod(
                "planNextStep",
                java.util.List.class,
                java.util.List.class,
                String.class,
                String.class,
                java.util.Map.class,
                String.class,
                String.class
            );
            dev.langchain4j.service.SystemMessage annotation =
                planNextStep.getAnnotation(dev.langchain4j.service.SystemMessage.class);

            assertNotNull(annotation,
                "planNextStep must carry a @SystemMessage annotation");

            String[] lines = annotation.value();
            assertFalse(lines == null || lines.length == 0,
                "@SystemMessage value array must not be empty");

            return String.join("\n", lines);

        } catch (NoSuchMethodException e) {

            throw new AssertionError(
                "planNextStep method signature has changed — update this test to match the new signature",
                e
            );
        }
    }

    // -------------------------------------------------------------------------
    // TC-SP-1: userResponse is present
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("TC-SP-1: Prompt contains 'userResponse' (the correct ask_a_person return key)")
    void prompt_containsUserResponse() {

        assertTrue(
            SYSTEM_MESSAGE_TEXT.contains("userResponse"),
            "The system prompt must contain 'userResponse' — this is the key that ask_a_person "
            + "always returns, and the LLM must be taught to read it"
        );
    }

    // -------------------------------------------------------------------------
    // TC-SP-2: The removed phrase userConfirmed must not appear
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("TC-SP-2: Prompt does NOT contain 'userConfirmed' (the removed incorrect key)")
    void prompt_doesNotContainUserConfirmed() {

        assertFalse(
            SYSTEM_MESSAGE_TEXT.contains("userConfirmed"),
            "The system prompt must NOT contain 'userConfirmed' — this was the old, incorrect "
            + "expected output key for ask_a_person that caused silent data loss when the LLM "
            + "provided multiple outputVariableNames"
        );
    }

    // -------------------------------------------------------------------------
    // TC-SP-3: STEP 3 teaches the LLM how to interpret the confirmation response
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("TC-SP-3: STEP 3 instructs the LLM to read userResponse to determine confirmation")
    void step3_instructsReadingUserResponse() {

        // The exact wording from the KR-20396 change is:
        // "The tool returns {\"userResponse\": \"<user's reply>\"}. Read userResponse to determine if the user confirmed."
        assertTrue(
            SYSTEM_MESSAGE_TEXT.contains("Read userResponse to determine if the user confirmed"),
            "STEP 3 must instruct the LLM to read userResponse to determine confirmation. "
            + "This replaces the old 'Expected output: userConfirmed (boolean)' wording"
        );
    }

    // -------------------------------------------------------------------------
    // TC-SP-4: STEP 4 teaches parameter extraction from userResponse
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("TC-SP-4: STEP 4 states ask_a_person always returns userResponse and values must be extracted from it")
    void step4_instructsParameterExtractionFromUserResponse() {

        // From the KR-20396 change in STEP 4:
        // "ask_a_person always returns {"userResponse": "<user's text>"}. Read the userResponse key on the next turn
        //  and extract parameter values from it."
        assertTrue(
            SYSTEM_MESSAGE_TEXT.contains("extract parameter values from it"),
            "STEP 4 must instruct the LLM to extract parameter values from userResponse. "
            + "This is the core fix: instead of expecting separate named keys, the LLM must "
            + "parse userResponse itself"
        );
    }

    // -------------------------------------------------------------------------
    // TC-SP-5: CRITICAL RULE 10 closes the loop — no separate named keys expected
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("TC-SP-5: CRITICAL RULE 10 explicitly forbids expecting separate named keys from ask_a_person")
    void criticalRule10_forbidsSeparateNamedKeys() {

        // From the KR-20396 change in CRITICAL RULES:
        // "Do not expect separate named keys for each piece of information you asked for."
        assertTrue(
            SYSTEM_MESSAGE_TEXT.contains("Do not expect separate named keys"),
            "CRITICAL RULE 10 must explicitly tell the LLM not to expect separate named keys "
            + "for each piece of information. This prevents the silent data loss that occurred "
            + "when the LLM supplied multiple outputVariableNames"
        );
    }

    // -------------------------------------------------------------------------
    // TC-SP-6: Example workflow shows userResponse extraction, not variable assignment
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("TC-SP-6: Example workflow step shows tool result as {\"userResponse\": \"Widget A\"}")
    void exampleWorkflow_showsUserResponsePayload() {

        // From the KR-20396 change to the example workflow, step 5:
        // "Tool result: {\"userResponse\": \"Widget A\"}"
        assertTrue(
            SYSTEM_MESSAGE_TEXT.contains("\"userResponse\": \"Widget A\"")
                || SYSTEM_MESSAGE_TEXT.contains("userResponse") && SYSTEM_MESSAGE_TEXT.contains("Widget A"),
            "The example workflow must demonstrate the tool result as {\"userResponse\": \"Widget A\"} "
            + "so the LLM has a concrete example of the return format"
        );
    }

    // -------------------------------------------------------------------------
    // TC-SP-7: Example workflow shows extraction from userResponse
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("TC-SP-7: Example workflow step shows extraction from userResponse, not direct variable binding")
    void exampleWorkflow_showsExtractionFromUserResponse() {

        // From the KR-20396 change to the example workflow, step 6:
        // "Extract from userResponse: product = \"Widget A\""
        assertTrue(
            SYSTEM_MESSAGE_TEXT.contains("Extract from userResponse"),
            "The example workflow must show the LLM extracting values from userResponse (step 6). "
            + "The old wording 'Tool result provides: product: \"Widget A\"' implied direct variable "
            + "binding which no longer applies"
        );
    }

    // -------------------------------------------------------------------------
    // TC-SP-8: The old example workflow wording is gone
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("TC-SP-8: Prompt does NOT contain the old example step 'User responds:' phrasing")
    void prompt_doesNotContainOldExamplePhrasing() {

        assertFalse(
            SYSTEM_MESSAGE_TEXT.contains("User responds: \"Widget A\""),
            "The old example step 'User responds: \"Widget A\"' must be gone — it was replaced by "
            + "'Tool result: {\"userResponse\": \"Widget A\"}' to make the return structure explicit"
        );
    }

    // -------------------------------------------------------------------------
    // TC-SP-9: Sanity — the prompt is non-trivially long (not accidentally emptied)
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("TC-SP-9: Prompt has substantial content (not accidentally truncated)")
    void prompt_hasSubstantialContent() {

        assertTrue(
            SYSTEM_MESSAGE_TEXT.length() > 500,
            "The system prompt must have substantial content (>500 chars). "
            + "If this fails the annotation value may have been accidentally cleared"
        );
    }

    // -------------------------------------------------------------------------
    // TC-SP-10: Core decision framework keywords are present
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("TC-SP-10: Core decision framework keywords are present (STEP 3, STEP 4, CRITICAL RULES)")
    void prompt_containsCoreFrameworkKeywords() {

        assertAll("Core framework keywords must all be present",
            () -> assertTrue(SYSTEM_MESSAGE_TEXT.contains("STEP 3"),
                "STEP 3 must be present"),
            () -> assertTrue(SYSTEM_MESSAGE_TEXT.contains("STEP 4"),
                "STEP 4 must be present"),
            () -> assertTrue(SYSTEM_MESSAGE_TEXT.contains("CRITICAL RULES"),
                "CRITICAL RULES section must be present"),
            () -> assertTrue(SYSTEM_MESSAGE_TEXT.contains("EXECUTE_TOOL"),
                "EXECUTE_TOOL response format must be present"),
            () -> assertTrue(SYSTEM_MESSAGE_TEXT.contains("COMPLETE"),
                "COMPLETE response format must be present")
        );
    }

    // -------------------------------------------------------------------------
    // TC-SP-11: Security instruction is present
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("TC-SP-11: Prompt contains 'UNTRUSTED DATA' (security instruction is present)")
    void prompt_containsSecurityInstruction() {

        assertTrue(
            SYSTEM_MESSAGE_TEXT.contains("UNTRUSTED DATA"),
            "The system prompt must contain 'UNTRUSTED DATA' — this security instruction "
            + "teaches the LLM to treat delimited content as opaque data, not as commands"
        );
    }

    // -------------------------------------------------------------------------
    // TC-SP-12: User data delimiter is present
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("TC-SP-12: Prompt contains '<<<USER_DATA>>>' (user data delimiter present)")
    void prompt_containsUserDataDelimiter() {

        assertTrue(
            SYSTEM_MESSAGE_TEXT.contains("<<<USER_DATA>>>"),
            "The system prompt must contain '<<<USER_DATA>>>' — this delimiter marks "
            + "user-supplied data so the LLM can distinguish it from system instructions"
        );
    }

    // -------------------------------------------------------------------------
    // TC-SP-13: External data delimiter is present
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("TC-SP-13: Prompt contains '<<<END_USER_DATA>>>' and user-data framing for external context")
    void prompt_containsExternalDataDelimiter() {

        assertAll("External context should be framed with current user-data delimiters",
            () -> assertTrue(
                SYSTEM_MESSAGE_TEXT.contains("<<<END_USER_DATA>>>"),
                "The system prompt must contain '<<<END_USER_DATA>>>' to close untrusted data sections"
            ),
            () -> assertTrue(
                SYSTEM_MESSAGE_TEXT.contains("Input Variables:"),
                "The system prompt must explicitly frame input variables as untrusted external context"
            )
        );
    }

    // -------------------------------------------------------------------------
    // TC-SP-14: Scope enforcement rule is present
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("TC-SP-14: Prompt contains 'Only perform actions relevant' (scope enforcement rule present)")
    void prompt_containsScopeEnforcementRule() {

        assertTrue(
            SYSTEM_MESSAGE_TEXT.contains("Only perform actions relevant"),
            "The system prompt must contain the scope enforcement rule 'Only perform actions relevant' "
            + "— this prevents the LLM from following injected instructions embedded in data fields"
        );
    }
}
