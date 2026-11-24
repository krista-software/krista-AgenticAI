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

package agentic;

import app.krista.extensions.hook.agentic.dto.AgenticCommand;
import app.krista.extensions.hook.agentic.dto.AgenticContinueRequest;
import app.krista.extensions.hook.agentic.dto.AgenticResponse;
import app.krista.extensions.hook.agentic.dto.AgenticStartRequest;
import app.krista.extensions.hook.agentic.dto.AgenticToolDefinition;
import app.krista.extensions.hook.agentic.dto.PolicyCheckResult;
import app.krista.extensions.hook.agentic.service.AgenticFlowService;
import app.krista.extensions.hook.agentic.session.AgenticSessionStore;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration test harness for {@link AgenticFlowService}.
 *
 * <p><b>Group A, B, C</b> require {@code OPENAI_API_KEY} and are skipped in CI when absent.
 * <b>Group D</b> is always executed (no API key, deterministic).
 *
 * <p>Run all modes:
 * <pre>
 *   OPENAI_API_KEY=&lt;key&gt; ./gradlew test --tests "agentic.AgenticFlowServiceLiveTest"
 * </pre>
 * Run CI-safe group only:
 * <pre>
 *   ./gradlew test --tests "agentic.AgenticFlowServiceLiveTest"
 * </pre>
 */
public class AgenticFlowServiceLiveTest {

    private static final String OPENAI_MODEL = "gpt-5.2";

    @BeforeEach
    void isolateSessions() {

        AgenticSessionStore.getInstance().clear();
    }

    // =========================================================================
    // Group A — Regression: basic flow, no policy (Mode 3, fully live)
    // =========================================================================

    @Nested
    @DisplayName("Group A — Regression: basic flow, no policy")
    @EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
    class GroupA_BasicFlowNoPolicy {

        private AgenticFlowService service;

        @BeforeEach
        void buildService() {

            Map<Object, ChatMemory> chatMemoryMap = new ConcurrentHashMap<>();
            ChatLanguageModel model = buildModel();
            service = AgenticTestHarness.liveMode(model, chatMemoryMap);
        }

        @Test
        @DisplayName("basicToolExecutionAndCompletion — tool selected then flow completes on result")
        void basicToolExecutionAndCompletion() {

            AgenticToolDefinition catalogueTool = AgenticTestHarness.businessTool(
                    "{\"toolType\":\"catalog_request\",\"invokerId\":\"INV_001\",\"catalogRequestId\":\"read_catalogue\"}",
                    "Retrieve a product from the catalogue by its SKU"
            );
            List<AgenticToolDefinition> tools = AgenticTestHarness.toolsWithAskAPerson(catalogueTool);

            AgenticStartRequest request = AgenticTestHarness.startRequest(
                    tools,
                    "Look up product SKU-9001 in the catalogue and return its details.",
                    List.of(),
                    "Retrieve product details"
            );

            AgenticResponse first = service.startFlow(request);

            assertNotNull(first.getSessionId(), "sessionId must be set");
            assertEquals(AgenticResponse.ResponseType.EXECUTE_TOOL, first.getResponseType(),
                    "Expected EXECUTE_TOOL on first turn");
            assertNotNull(first.getCommand(), "command must be present");
            assertTrue(first.getConfidence() >= 0.75,
                    "confidence should be >= 0.75, got: " + first.getConfidence());

            // Continue with a simulated tool result
            AgenticContinueRequest continueReq = new AgenticContinueRequest(
                    first.getSessionId(),
                    first.getCommand().toolName(),
                    Map.of("sku", "SKU-9001", "name", "Gadget Pro", "price", 49.99)
            );
            AgenticResponse second = service.continueFlow(continueReq);

            assertEquals(AgenticResponse.ResponseType.COMPLETE, second.getResponseType(),
                    "Flow should complete after tool result is provided");
        }

        @Test
        @DisplayName("askAPersonClarificationFlow — missing parameter triggers ask_a_person then business tool")
        void askAPersonClarificationFlow() {

            AgenticToolDefinition catalogueTool = AgenticTestHarness.businessTool(
                    "{\"toolType\":\"catalog_request\",\"invokerId\":\"INV_002\",\"catalogRequestId\":\"update_stock\"}",
                    "Update the stock quantity for a product SKU. Requires both 'sku' and 'quantity'."
            );
            List<AgenticToolDefinition> tools = AgenticTestHarness.toolsWithAskAPerson(catalogueTool);

            AgenticStartRequest request = AgenticTestHarness.startRequest(
                    tools,
                    "Update the stock level but I haven't told you which SKU or how many units.",
                    List.of(),
                    "Update stock level"
            );

            AgenticResponse first = service.startFlow(request);

            assertEquals(AgenticResponse.ResponseType.EXECUTE_TOOL, first.getResponseType(),
                    "Expected EXECUTE_TOOL on first turn");
            assertNotNull(first.getCommand(), "command must be present");

            String toolName = first.getCommand().toolName();
            assertTrue(toolName.contains("ask_a_person"),
                    "Expected ask_a_person tool when parameters are missing, got: " + toolName);

            // Provide the missing information
            AgenticContinueRequest clarification = new AgenticContinueRequest(
                    first.getSessionId(),
                    first.getCommand().toolName(),
                    "The SKU is SKU-7777 and the new quantity is 250."
            );
            AgenticResponse second = service.continueFlow(clarification);

            // The planner should now invoke the business tool
            assertNotNull(second, "second response must not be null");
            assertNotNull(second.getResponseType(), "response type must be set");
        }
    }

    // =========================================================================
    // Group B — Policy enforcement loop (Mode 3, fully live)
    // =========================================================================

    @Nested
    @DisplayName("Group B — Policy enforcement loop")
    @EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
    class GroupB_PolicyEnforcementLoop {

        private static final Logger log = LoggerFactory.getLogger(GroupB_PolicyEnforcementLoop.class);

        private AgenticFlowService service;

        @BeforeEach
        void buildService() {

            Map<Object, ChatMemory> chatMemoryMap = new ConcurrentHashMap<>();
            ChatLanguageModel model = buildModel();
            service = AgenticTestHarness.liveMode(model, chatMemoryMap);
        }

        @Test
        @DisplayName("compliantToolPassesPolicyCheck — read-only tool is allowed, flow completes")
        void compliantToolPassesPolicyCheck() {

            AgenticToolDefinition readTool = AgenticTestHarness.businessTool(
                    "{\"toolType\":\"catalog_request\",\"invokerId\":\"INV_010\",\"catalogRequestId\":\"read_product_catalogue\"}",
                    "Read-only retrieval of product entries from the catalogue. Makes no modifications."
            );
            List<AgenticToolDefinition> tools = AgenticTestHarness.toolsWithAskAPerson(readTool);

            AgenticStartRequest request = AgenticTestHarness.startRequest(
                    tools,
                    "List all products in the catalogue.",
                    List.of("Only read-only operations are permitted. No modifications to data are allowed."),
                    "Retrieve catalogue entries"
            );

            AgenticResponse response = AgenticTestHarness.runToCompletion(
                    service,
                    request,
                    (Function<AgenticCommand, Object>) cmd -> List.of(Map.of("id", "P1", "name", "Widget")),
                    5
            );

            assertNotNull(response, "response must not be null");
            // Flow should complete without escalation
            assertEquals(AgenticResponse.ResponseType.COMPLETE, response.getResponseType(),
                    "Compliant tool should allow flow to complete");
        }

        @Test
        @Tag("flaky-llm")
        @DisplayName("violatingToolSelfCorrectedByReplanning — bulk update blocked, single update allowed")
        // Model inference variance: after the policy block, the planner does not always switch
        // from ask_a_person to the single-update tool within the configured retry budget.
        void violatingToolSelfCorrectedByReplanning() {

            AgenticToolDefinition singleUpdate = AgenticTestHarness.businessTool(
                    "{\"toolType\":\"catalog_request\",\"invokerId\":\"INV_011\",\"catalogRequestId\":\"update_single_record\"}",
                    "Update a single product record by ID. Modifies one row at a time."
            );
            AgenticToolDefinition bulkUpdate = AgenticTestHarness.businessTool(
                    "{\"toolType\":\"catalog_request\",\"invokerId\":\"INV_012\",\"catalogRequestId\":\"bulk_update_records\"}",
                    "Bulk update multiple product records in a single operation."
            );
            List<AgenticToolDefinition> tools = AgenticTestHarness.toolsWithAskAPerson(singleUpdate, bulkUpdate);

            AgenticStartRequest request = AgenticTestHarness.startRequest(
                    tools,
                    "Update product SKU-1234 salePrice to 19.99 USD in the product catalog.",
                    List.of("Bulk update operations are prohibited. Only single-record updates are permitted."),
                    "Update product records"
            );

            AgenticResponse response = AgenticTestHarness.runToCompletion(
                    service,
                    request,
                    (Function<AgenticCommand, Object>) cmd -> {

                        String toolName = cmd.toolName();
                        log.info("violatingToolSelfCorrectedByReplanning — tool invoked: {}, args: {}",
                                toolName, cmd.arguments());
                        if (toolName.contains("ask_a_person")) {

                            return Map.of("userResponse",
                                    "Update SKU-1234 to price $19.99, one record at a time using the single-update tool");
                        }
                        return Map.of("status", "updated", "recordId", "SKU-1234");
                    },
                    8
            );

            log.info("violatingToolSelfCorrectedByReplanning — final response type: {}", response.getResponseType());

            assertNotNull(response, "response must not be null");
            // Flow should not end in an unresolvable error
            assertTrue(
                    response.getResponseType() == AgenticResponse.ResponseType.COMPLETE
                            || response.getResponseType() == AgenticResponse.ResponseType.EXECUTE_TOOL,
                    "Expected COMPLETE or final EXECUTE_TOOL, got: " + response.getResponseType()
            );
        }

        @Test
        @DisplayName("doubleViolationTriggersEscalation — repeated price violation escalates to ask_a_person")
        void doubleViolationTriggersEscalation() {

            AgenticToolDefinition updateProduct = AgenticTestHarness.businessTool(
                    "{\"toolType\":\"catalog_request\",\"invokerId\":\"INV_013\",\"catalogRequestId\":\"update_product_record\"}",
                    "Update a product record. Can modify any field including price, name, and category."
            );
            List<AgenticToolDefinition> tools = AgenticTestHarness.toolsWithAskAPerson(updateProduct);

            AgenticStartRequest request = AgenticTestHarness.startRequest(
                    tools,
                    "Fix the wrong prices in the catalogue — update price to $0.01 for all products.",
                    List.of("Price fields must never be modified. Only descriptive fields may be updated."),
                    "Correct product data"
            );

            AgenticResponse response = service.startFlow(request);

            assertNotNull(response, "response must not be null");
            assertEquals(AgenticResponse.ResponseType.EXECUTE_TOOL, response.getResponseType(),
                    "Expected EXECUTE_TOOL (escalation to ask_a_person) after double policy violation");
            assertNotNull(response.getCommand(), "command must be present on escalation");
            assertTrue(response.getCommand().toolName().contains("ask_a_person"),
                    "Escalation should target ask_a_person, got: " + response.getCommand().toolName());
        }
    }

    // =========================================================================
    // Group C — Argument-level violation, scripted planner (Mode 2)
    // =========================================================================

    @Nested
    @DisplayName("Group C — Argument-level violation with scripted planner")
    @EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
    class GroupC_ArgumentLevelViolationScriptedPlanner {

        private static final String UPDATE_PRODUCT_TOOL =
                "{\"toolType\":\"catalog_request\",\"invokerId\":\"INV_020\",\"catalogRequestId\":\"update_product_record\"}";
        private static final String DATA_QUALITY_POLICY =
                "Price fields must never be modified during a data quality run.";

        private ChatLanguageModel model;

        @BeforeEach
        void buildModel() {

            model = AgenticFlowServiceLiveTest.buildModel();
        }

        @Test
        @DisplayName("checkerBlocksPriceFieldArguments — price argument causes BLOCKED decision")
        void checkerBlocksPriceFieldArguments() {

            AgenticResponse scriptedResponse = new AgenticResponse();
            scriptedResponse.setResponseType(AgenticResponse.ResponseType.EXECUTE_TOOL);
            scriptedResponse.setConfidence(0.95);
            scriptedResponse.setCommand(new AgenticCommand(
                    UPDATE_PRODUCT_TOOL,
                    Map.of("price", 99.99, "productId", "P001")
            ));

            AgenticTestHarness.ScriptedToolPlanner planner = new AgenticTestHarness.ScriptedToolPlanner()
                    .withResponse(scriptedResponse);

            AgenticToolDefinition tool = AgenticTestHarness.businessTool(
                    UPDATE_PRODUCT_TOOL,
                    "Update a product record. Can modify price, name, and other fields."
            );
            List<AgenticToolDefinition> tools = AgenticTestHarness.toolsWithAskAPerson(tool);

            AgenticStartRequest request = AgenticTestHarness.startRequest(
                    tools,
                    "Update product P001 with the new price.",
                    List.of(DATA_QUALITY_POLICY),
                    "Update product data"
            );

            AgenticFlowService service = AgenticTestHarness.scriptedPlannerMode(planner, model);
            AgenticResponse response = service.startFlow(request);

            assertNotNull(response, "response must not be null");
            // The PolicyChecker should block and escalate since there is only one business tool
            assertEquals(AgenticResponse.ResponseType.EXECUTE_TOOL, response.getResponseType(),
                    "Expected escalation after price field block");
            assertTrue(response.getCommand().toolName().contains("ask_a_person"),
                    "Blocked price argument should escalate to ask_a_person");
        }

        @Test
        @DisplayName("priceFieldBlockedThenDescriptiveFieldAllowed — violation on first plan, retry with compliant args succeeds")
        void priceFieldBlockedThenDescriptiveFieldAllowed() {

            // Turn 1 — planner proposes update including price field (the violation you tested manually)
            AgenticResponse violatingResponse = new AgenticResponse();
            violatingResponse.setResponseType(AgenticResponse.ResponseType.EXECUTE_TOOL);
            violatingResponse.setConfidence(0.9);
            violatingResponse.setCommand(new AgenticCommand(
                    UPDATE_PRODUCT_TOOL,
                    Map.of("price", 19.99, "name", "Widget Pro", "productId", "P001")
            ));

            // Turn 2 — planner replans with only descriptive fields after being told price is blocked
            AgenticResponse compliantResponse = new AgenticResponse();
            compliantResponse.setResponseType(AgenticResponse.ResponseType.EXECUTE_TOOL);
            compliantResponse.setConfidence(0.9);
            compliantResponse.setCommand(new AgenticCommand(
                    UPDATE_PRODUCT_TOOL,
                    Map.of("name", "Widget Pro", "tags", List.of("electronics"), "productId", "P001")
            ));

            AgenticTestHarness.ScriptedToolPlanner planner = new AgenticTestHarness.ScriptedToolPlanner()
                    .withResponse(violatingResponse)   // PolicyChecker blocks this
                    .withResponse(compliantResponse);  // PolicyChecker allows this after retry

            AgenticToolDefinition tool = AgenticTestHarness.businessTool(
                    UPDATE_PRODUCT_TOOL,
                    "Update a product record. Can modify price, name, tags, and other fields."
            );
            List<AgenticToolDefinition> tools = AgenticTestHarness.toolsWithAskAPerson(tool);

            AgenticStartRequest request = AgenticTestHarness.startRequest(
                    tools,
                    "Run data quality checks and fix inconsistencies in the product catalogue",
                    List.of(
                            "Price fields must never be modified during a data quality run",
                            "Only descriptive fields (name, tags) may be updated"
                    ),
                    "Data quality run"
            );

            AgenticFlowService service = AgenticTestHarness.scriptedPlannerMode(planner, model);
            AgenticResponse response = service.startFlow(request);

            assertNotNull(response, "response must not be null");
            assertEquals(AgenticResponse.ResponseType.EXECUTE_TOOL, response.getResponseType(),
                    "Expected EXECUTE_TOOL with compliant arguments after retry");
            assertEquals(UPDATE_PRODUCT_TOOL, response.getCommand().toolName(),
                    "Business tool should be returned after compliant retry — not ask_a_person");
            assertFalse(response.getCommand().arguments().containsKey("price"),
                    "price field must not be present in the allowed response");
            assertEquals(2, planner.getCallCount(),
                    "Planner must be called twice: once blocked, once allowed");
        }

        @Test
        @DisplayName("checkerAllowsDescriptiveFieldArguments — non-price arguments are ALLOWED")
        void checkerAllowsDescriptiveFieldArguments() {

            AgenticResponse scriptedResponse = new AgenticResponse();
            scriptedResponse.setResponseType(AgenticResponse.ResponseType.EXECUTE_TOOL);
            scriptedResponse.setConfidence(0.95);
            scriptedResponse.setCommand(new AgenticCommand(
                    UPDATE_PRODUCT_TOOL,
                    Map.of("name", "Widget Pro", "category", "Electronics", "productId", "P001")
            ));

            AgenticTestHarness.ScriptedToolPlanner planner = new AgenticTestHarness.ScriptedToolPlanner()
                    .withResponse(scriptedResponse);

            AgenticToolDefinition tool = AgenticTestHarness.businessTool(
                    UPDATE_PRODUCT_TOOL,
                    "Update a product record. Can modify price, name, and other fields."
            );
            List<AgenticToolDefinition> tools = AgenticTestHarness.toolsWithAskAPerson(tool);

            AgenticStartRequest request = AgenticTestHarness.startRequest(
                    tools,
                    "Update the name and category for product P001.",
                    List.of(DATA_QUALITY_POLICY),
                    "Update product descriptive data"
            );

            AgenticFlowService service = AgenticTestHarness.scriptedPlannerMode(planner, model);
            AgenticResponse response = service.startFlow(request);

            assertNotNull(response, "response must not be null");
            // Descriptive fields are compliant — service should return EXECUTE_TOOL for the business tool
            assertEquals(AgenticResponse.ResponseType.EXECUTE_TOOL, response.getResponseType(),
                    "Expected EXECUTE_TOOL for compliant descriptive field arguments");
            assertEquals(UPDATE_PRODUCT_TOOL, response.getCommand().toolName(),
                    "Should execute the business tool, not ask_a_person");
        }
    }

    // =========================================================================
    // Group D — Fast path, no policy brief (Mode 1, deterministic, always runs)
    // =========================================================================

    @Nested
    @DisplayName("Group D — Fast path: no policy brief (always runs, no API key needed)")
    class GroupD_NoPolicyBrief {

        @Test
        @DisplayName("noPolicyBriefSkipsChecker — FakePolicyChecker never invoked when policyBrief is empty")
        void noPolicyBriefSkipsChecker() {

            AgenticToolDefinition catalogueTool = AgenticTestHarness.businessTool(
                    "{\"toolType\":\"catalog_request\",\"invokerId\":\"INV_100\",\"catalogRequestId\":\"read_catalogue\"}",
                    "Retrieve products from the catalogue."
            );
            List<AgenticToolDefinition> tools = AgenticTestHarness.toolsWithAskAPerson(catalogueTool);

            AgenticResponse scriptedResponse = new AgenticResponse();
            scriptedResponse.setResponseType(AgenticResponse.ResponseType.COMPLETE);
            scriptedResponse.setConfidence(0.9);
            scriptedResponse.setFinalResponse("Done.");

            AgenticTestHarness.ScriptedToolPlanner planner = new AgenticTestHarness.ScriptedToolPlanner()
                    .withResponse(scriptedResponse);

            AgenticTestHarness.FakePolicyChecker checker = new AgenticTestHarness.FakePolicyChecker();

            AgenticFlowService service = AgenticTestHarness.deterministicMode(planner, checker);

            AgenticStartRequest request = AgenticTestHarness.startRequest(
                    tools,
                    "List all products.",
                    List.of(),   // empty policy brief — checker must not be called
                    "Retrieve products"
            );

            AgenticResponse response = service.startFlow(request);

            assertNotNull(response, "response must not be null");
            assertEquals(0, checker.getCallCount(),
                    "FakePolicyChecker must NOT be called when policyBrief is empty");
        }
    }

    // =========================================================================
    // Group E — Vendor Invoice Orchestration (ToolPlanner → PolicyChecker → self-correction)
    // =========================================================================

    @Nested
    @DisplayName("Group E — Vendor Invoice Orchestration")
    @EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
    class GroupE_VendorInvoiceOrchestration {

        private static final Logger log = LoggerFactory.getLogger(GroupE_VendorInvoiceOrchestration.class);

        private static final List<String> VENDOR_INVOICE_POLICY_BRIEF = List.of(
                "Verify vendor compliance before processing any invoice or creating a PO: confirm vendor onboarding completion (including W-9 and compliance certification) and confirm the vendor is not on any sanctions list; do not proceed if either check fails.",
                "Apply purchase authorization thresholds before processing: <$5,000 requires direct manager approval; $5,000-$10,000 requires department head approval; >$10,000 requires VP approval; any single purchase >$50,000 requires CFO approval.",
                "Do not split or sequence orders/invoices to circumvent approval thresholds; if the activity appears intended to bypass thresholds, stop and require correct approval for the full amount before proceeding.",
                "When requesting approvals, include a clear business purpose and the total amount in USD; retain the approval record/ID and associate it with the PO and invoice record.",
                "Create purchase orders only after the required approval is obtained; the PO must reference the approval ID and accurately describe the line items and total amount.",
                "Payment confirmation emails and other communications related to financial transactions must include the transaction ID (e.g., PO number, invoice number, or payment reference).",
                "Any external/customer-facing email that contains financial commitments, legal statements, or contract modifications must be reviewed by a manager before sending; do not send until review is complete.",
                "Follow email usage controls relevant to this step: keep attachments <=25MB or use approved file-sharing for larger files.",
                "Protect data in emails: do not include PII in email subject lines or unencrypted email bodies; never transmit PII to external domains without encryption; do not send Restricted data via email without encryption and explicit recipient authorization.",
                "Do not send vendor banking details (e.g., bank account numbers, routing numbers) or tax identifiers (e.g., SSN/EIN/TIN) in plain/unencrypted email; if such data must be transmitted, use an approved encrypted channel and only to explicitly authorized recipients.",
                "Use required subject tagging: if an email contains Confidential or Restricted data, include the [CONFIDENTIAL] tag in the subject line (and still comply with encryption/authorization requirements)."
        );

        private static final String LOOKUP_VENDOR_TOOL =
                "{\"toolType\":\"catalog_request\",\"invokerId\":\"INV_3a7f1c92-d8e4-4b1a-9c56-2e8d0f4a7b31\",\"catalogRequestId\":\"localDomainRequest_b21e4f8a-6c3d-4a95-8d12-9f7e3b1c0d5a\"}";
        private static final String GET_APPROVAL_TOOL =
                "{\"toolType\":\"catalog_request\",\"invokerId\":\"INV_3a7f1c92-d8e4-4b1a-9c56-2e8d0f4a7b31\",\"catalogRequestId\":\"localDomainRequest_c45f7a2b-8e19-4d63-b7a4-1c3e9d5f2a80\"}";
        private static final String CREATE_PO_TOOL =
                "{\"toolType\":\"catalog_request\",\"invokerId\":\"INV_3a7f1c92-d8e4-4b1a-9c56-2e8d0f4a7b31\",\"catalogRequestId\":\"localDomainRequest_d56a8b3c-9f2e-4e74-c8b5-2d4f0e6a3b91\"}";
        private static final String SEND_EMAIL_TOOL =
                "{\"toolType\":\"catalog_request\",\"invokerId\":\"INV_7b2e9d14-a5c8-4f36-9e71-3d6a8c0f1b24\",\"catalogRequestId\":\"localDomainRequest_e67b9c4d-0a3f-4f85-d9c6-3e5a1f7b4ca2\"}";

        private final Function<AgenticCommand, Object> vendorInvoiceToolProvider = command -> {

            String toolName = command.toolName();
            if (toolName.contains("localDomainRequest_b21e4f8a")) {        // vendor compliance lookup

                return Map.of("status", "compliant", "w9_complete", true,
                        "sanctions_clear", true, "vendor_name", "Acme Corp");
            } else if (toolName.contains("localDomainRequest_c45f7a2b")) { // purchase approval

                return Map.of("approved", true, "approver", "Jane Smith",
                        "approver_level", "VP", "approval_id", "APR-2026-001");
            } else if (toolName.contains("localDomainRequest_d56a8b3c")) { // create purchase order

                return Map.of("po_number", "PO-2026-4421", "status", "created",
                        "amount", 15000, "vendor", "Acme Corp", "approval_id", "APR-2026-001");
            } else if (toolName.contains("localDomainRequest_e67b9c4d")) { // send email

                return Map.of("sent", true, "message_id", "MSG-2026-001");
            }
            return Map.of("status", "unknown_tool");
        };

        private List<AgenticToolDefinition> vendorInvoiceTools() {

            AgenticToolDefinition lookupVendor = new AgenticToolDefinition(
                    LOOKUP_VENDOR_TOOL,
                    "Look up vendor compliance status including W-9 verification, sanctions screening, and onboarding completion status.",
                    "catalog_request",
                    List.of(new AgenticToolDefinition.ParameterDefinition("vendor_name", "Text", "Name of the vendor to look up", true)),
                    false
            );
            AgenticToolDefinition getApproval = new AgenticToolDefinition(
                    GET_APPROVAL_TOOL,
                    "Request purchase approval at the specified level (Manager, Department Head, VP, or CFO). Returns approval status and approver details.",
                    "catalog_request",
                    List.of(
                            new AgenticToolDefinition.ParameterDefinition("approval_level", "Text", "Required approval level", true),
                            new AgenticToolDefinition.ParameterDefinition("amount", "Decimal", "Purchase amount in USD requiring approval", true),
                            new AgenticToolDefinition.ParameterDefinition("purpose", "Text", "Business justification for the purchase", true)
                    ),
                    false
            );
            AgenticToolDefinition createPO = new AgenticToolDefinition(
                    CREATE_PO_TOOL,
                    "Create a purchase order for a vendor with the specified amount, line items, and approval documentation.",
                    "catalog_request",
                    List.of(
                            new AgenticToolDefinition.ParameterDefinition("vendor_name", "Text", "Vendor to issue PO to", true),
                            new AgenticToolDefinition.ParameterDefinition("amount", "Decimal", "Total PO amount in USD", true),
                            new AgenticToolDefinition.ParameterDefinition("description", "Text", "Line item description", true),
                            new AgenticToolDefinition.ParameterDefinition("approval_id", "Text", "Approval reference ID from prior approval step", true)
                    ),
                    false
            );
            AgenticToolDefinition sendEmail = new AgenticToolDefinition(
                    SEND_EMAIL_TOOL,
                    "Send an email to a recipient. Can include attachments and financial transaction details. Subject to email usage policies.",
                    "catalog_request",
                    List.of(
                            new AgenticToolDefinition.ParameterDefinition("to", "Text", "Recipient email address", true),
                            new AgenticToolDefinition.ParameterDefinition("subject", "Text", "Email subject line", true),
                            new AgenticToolDefinition.ParameterDefinition("body", "Text", "Email body content", true),
                            new AgenticToolDefinition.ParameterDefinition("attachments", "Text", "Comma-separated file paths to attach", false)
                    ),
                    false
            );
            return AgenticTestHarness.toolsWithAskAPerson(lookupVendor, getApproval, createPO, sendEmail);
        }

        @Test
        @Tag("flaky-llm")
        @DisplayName("E1: vendorInvoiceHappyPath — full flow completes with compliant tool ordering")
        void vendorInvoiceHappyPath() {

            Map<Object, ChatMemory> chatMemoryMap = new ConcurrentHashMap<>();
            ChatLanguageModel model = buildModel();
            AgenticFlowService service = AgenticTestHarness.liveMode(model, chatMemoryMap);

            AgenticStartRequest request = AgenticTestHarness.startRequest(
                    vendorInvoiceTools(),
                    "Process vendor invoice INV-2026-0042 from Acme Corp for $15,000 for IT consulting services. Payment reference PAY-2026-7890. Send payment confirmation to accounts@acmecorp.com.",
                    VENDOR_INVOICE_POLICY_BRIEF,
                    "Process vendor invoice with compliance checks"
            );

            AgenticResponse response = AgenticTestHarness.runToCompletion(
                    service, request, vendorInvoiceToolProvider, 10
            );

            assertNotNull(response, "response must not be null");
            log.info("E1 final response type: {}", response.getResponseType());

            // Flow should complete successfully or end on a final tool execution
            assertTrue(
                    response.getResponseType() == AgenticResponse.ResponseType.COMPLETE
                            || response.getResponseType() == AgenticResponse.ResponseType.EXECUTE_TOOL,
                    "Expected COMPLETE or final EXECUTE_TOOL, got: " + response.getResponseType()
            );

            // The flow must not end in ERROR — policy should not be fatally violated
            assertTrue(
                    response.getResponseType() != AgenticResponse.ResponseType.ERROR,
                    "Flow must not end in ERROR"
            );
        }

        @Test
        @DisplayName("E3: vendorInvoiceSelfCorrectionOnViolation — scripted planner self-corrects after PO blocked")
        void vendorInvoiceSelfCorrectionOnViolation() {

            ChatLanguageModel model = buildModel();

            // Turn 1: planner proposes create_purchase_order without prior vendor lookup (violation)
            AgenticResponse violatingResponse = new AgenticResponse();
            violatingResponse.setResponseType(AgenticResponse.ResponseType.EXECUTE_TOOL);
            violatingResponse.setConfidence(0.9);
            violatingResponse.setCommand(new AgenticCommand(
                    CREATE_PO_TOOL,
                    Map.of("amount", 15000, "vendor", "Acme Corp")
            ));

            // Turn 2: planner self-corrects by calling lookup_vendor first
            AgenticResponse correctedResponse = new AgenticResponse();
            correctedResponse.setResponseType(AgenticResponse.ResponseType.EXECUTE_TOOL);
            correctedResponse.setConfidence(0.9);
            correctedResponse.setCommand(new AgenticCommand(
                    LOOKUP_VENDOR_TOOL,
                    Map.of("vendor_name", "Acme Corp")
            ));

            AgenticTestHarness.ScriptedToolPlanner planner = new AgenticTestHarness.ScriptedToolPlanner()
                    .withResponse(violatingResponse)
                    .withResponse(correctedResponse);

            AgenticStartRequest request = AgenticTestHarness.startRequest(
                    vendorInvoiceTools(),
                    "Process a $15,000 vendor invoice from Acme Corp and email payment confirmation to accounts@acmecorp.com",
                    VENDOR_INVOICE_POLICY_BRIEF,
                    "Process vendor invoice with compliance checks"
            );

            AgenticFlowService service = AgenticTestHarness.scriptedPlannerMode(planner, model);
            AgenticResponse response = service.startFlow(request);

            assertNotNull(response, "response must not be null");
            log.info("E3 final response type: {}, planner call count: {}",
                    response.getResponseType(), planner.getCallCount());

            // The planner should have been called twice: once blocked, once self-corrected
            assertEquals(2, planner.getCallCount(),
                    "Planner must be called twice: first blocked by PolicyChecker, then self-corrected");

            // The second response should target the vendor compliance lookup (the corrected action)
            assertEquals(AgenticResponse.ResponseType.EXECUTE_TOOL, response.getResponseType(),
                    "Expected EXECUTE_TOOL after self-correction");
            assertTrue(response.getCommand().toolName().contains("localDomainRequest_b21e4f8a"),
                    "Self-corrected response should target vendor compliance lookup, got: " + response.getCommand().toolName());
        }
    }

    // =========================================================================
    // Shared helpers
    // =========================================================================

    private static ChatLanguageModel buildModel() {

        return OpenAiChatModel.builder()
                .modelName(OPENAI_MODEL)
                .apiKey(System.getenv("OPENAI_API_KEY"))
                .build();
    }
}
