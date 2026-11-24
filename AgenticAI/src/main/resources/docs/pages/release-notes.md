# Release Notes

## Version 1.0.6

This release introduces runtime policy governance for agent actions, richer session and conversation summaries for review and debugging, and stronger error handling and resilience.

## New Changes
* Policy enforcement — workspace authors can now define policy rules that the AI agent enforces at runtime; the agent checks each action for compliance, self-corrects, and blocks non-compliant actions with a clear explanation
* Session summaries — automation authors now get a clear, structured view of what happened inside an agentic AI step, including the conversation, the tools it used, and how the session ended, available on both successful and failed runs
* Improved error handling and visibility
    * LLM context/token-limit errors are surfaced to the user with a clear message
    * Error messages now persist and are shown in the conversation, and error codes and messages are recorded in usage logs with detailed diagnostic reports
* Server-side validation of AI-produced tool arguments after type conversion, with automatic retry on invalid values; output variables are validated with warnings
* Automatic retry and fallback handling for LLM rate-limit (HTTP 429) responses
* Fixed a malformed json issue when parsing large OpenAI responses

## Limitations
* Server-side argument validation (Phase 1) does not yet cover Phone, Multi-field, and List field types
* All limitations from [Version 1.0.5](#version-105) continue to apply.

## Known Issues
* [Agentic AI step fails with Chatbot](https://antbrains.atlassian.net/browse/KR-20697)

---

## Version 1.0.5

This release adds real-time auditability so users can see what the AI agent is doing, fixes input/output variable mapping for richer field type support, and resolves several stability and UX issues.

## New Changes
* Structured audit events added — users can now see what the AI agent is doing and why, in real time
* Improved tool parameter and output variable handling for dates, entities, lists, multi-fields, and other complex field types
* Chat input bar is now hidden during active agentic step interactions
* Fixed session stability issues that caused timeouts during long-running agent sessions
* Fixed malformed output when parsing large OpenAI responses in child agent flows

## Limitations
* All limitations from [Version 1.0.4](#version-104) continue to apply.

## Known Issues
* [Agentic AI step fails with Angular Chatbot](https://antbrains.atlassian.net/browse/KR-20697)

---

## Version 1.0.4

## New Changes
* Complex fields and secured attributes supported via chat form action

## Limitations
* When timezone is not specified for date-time related fields, we can not identify browser's timezone in chat mode and LLM can extract incorrect date-time values.
* Workspace and Agent level instructions are not supported.
* Bookmarks is not supported in chat mode

## Known Issues
* [No loading conversation dots present in chat mode](https://antbrains.atlassian.net/browse/KR-19034)
* [Image and link in PMR not visible in chat mode](https://antbrains.atlassian.net/browse/KR-19041)
* [Time mismatch in chat mode from form mode](https://antbrains.atlassian.net/browse/KR-19133)
* [Pre-filled values from Form Mode not retained in Chat Mode](https://antbrains.atlassian.net/browse/KR-19152)
* [Sidebar getting skip in chat mode](https://antbrains.atlassian.net/browse/KR-19056)
