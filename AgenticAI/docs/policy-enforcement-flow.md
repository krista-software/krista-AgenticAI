# Policy Brief Enforcement — Runtime Flow

## Full Decision Flow (callLlmWithValidation)

```
                    callLlmWithValidation(attemptNumber=1, policyRetryCount=0)
                                        |
                                        v
                              ToolPlanner.planNextStep()
                                        |
                                        v
                              +-------------------+
                              | Response Type?    |
                              +-------------------+
                              |         |         |
                         COMPLETE    ERROR    EXECUTE_TOOL
                           |          |          |
                        return     return        v
                                        +------------------+
                                        | Tool name valid? |
                                        +------------------+
                                          |            |
                                         NO           YES
                                          |            |
                                          v            v
                                   retry (bump    +------------------------+
                                   attemptNumber) | policyBrief present    |
                                                  | AND policyChecker set? |
                                                  +------------------------+
                                                     |              |
                                                    NO             YES
                                                     |              |
                                                  return            v
                                                  response   PolicyChecker.evaluate()
                                                                    |
                                                     +--------------+--------------+
                                                     |              |              |
                                                  ALLOWED       BLOCKED       EXCEPTION
                                                     |              |              |
                                                  return            v              v
                                                  response   policyRetryCount  +-------------+
                                                             < MAX (1)?        | Fail Policy |
                                                               |       |       +-------------+
                                                              YES      NO       |           |
                                                               |       |     FAIL_CLOSED  FAIL_OPEN
                                                               v       v        |           |
                                                            RETRY   ESCALATE  escalate    return
                                                               |       |                  response
                                                               v       v
                                                  handlePolicy  synthesize
                                                  ViolationRetry EscalationResponse
                                                       |              |
                                                       v              v
                                            callLlmWithValidation  +------------------+
                                            (policyRetryCount+1)   | ask_a_person     |
                                                                   | tool available?  |
                                                                   +------------------+
                                                                      |            |
                                                                     YES           NO
                                                                      |            |
                                                                      v            v
                                                                  EXECUTE_TOOL   ERROR
                                                                  (ask_a_person) "escalation
                                                                                  failed"
```

## testFirstViolationRetrySuccess — Sequence

```
 Test Setup                ToolPlanner              PolicyChecker          FlowService
     |                         |                         |                     |
     |--- startFlow() -------->|                         |                     |
     |                         |                         |                     |
     |                    [1] planNextStep()              |                     |
     |                         |                         |                     |
     |                         |--- EXECUTE_TOOL ------->|                     |
     |                         |    (VALID_TOOL)         |                     |
     |                         |                    [2] evaluate()             |
     |                         |                         |                     |
     |                         |                    BLOCKED                    |
     |                         |                    "Tool calls               |
     |                         |                     external API"            |
     |                         |                         |                     |
     |                         |                         |--- policyRetryCount=0 < MAX(1)
     |                         |                         |    --> RETRY         |
     |                         |                         |                     |
     |                    [3] planNextStep()              |                     |
     |                         |    (with violation       |                     |
     |                         |     feedback as          |                     |
     |                         |     previousResult)      |                     |
     |                         |                         |                     |
     |                         |--- EXECUTE_TOOL ------->|                     |
     |                         |    (VALID_TOOL)         |                     |
     |                         |                    [4] evaluate()             |
     |                         |                         |                     |
     |                         |                    ALLOWED                    |
     |                         |                    "Revised action            |
     |                         |                     complies"                |
     |                         |                         |                     |
     |<--- EXECUTE_TOOL -------|-------------------------|---------------------|
     |     (VALID_TOOL)        |                         |                     |
     |                         |                         |                     |
     | ASSERT: responseType = EXECUTE_TOOL                                     |
     | ASSERT: policyChecker.callCount = 2                                     |
     | ASSERT: toolPlanner called 2 times                                      |
```

## Self-Correction Feedback (what the ToolPlanner sees on retry)

```json
{
  "policy_violation": true,
  "error_message": "Your planned action was blocked: Tool calls external API. Violated policy: 'No external calls'. Choose a different approach that complies with all policy rules.",
  "violated_policy": "No external calls",
  "original_tool": "{\"toolType\":\"catalog_request\",...}"
}
```

This is passed as `previousToolResult` to the ToolPlanner, same pattern as
malformed JSON retries and invalid tool name retries.

## Counter Independence

```
                    attemptNumber    policyRetryCount
                    ─────────────    ────────────────
Initial call             1                0
Policy retry             1                1          ← attemptNumber NOT bumped
Tool validation retry    2                0          ← policyRetryCount reset
```

These are independent counters. A policy retry does NOT consume a tool-validation
retry budget, and vice versa.
