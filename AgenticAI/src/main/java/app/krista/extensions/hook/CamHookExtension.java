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

package app.krista.extensions.hook;


import app.krista.extension.executor.Invoker;
import app.krista.extension.impl.anno.*;
import app.krista.extensions.hook.llm.LLMClient;
import app.krista.extensions.hook.llm.openai.OpenAIClient;
import app.krista.extensions.hook.llm.claude.ClaudeClient;

import javax.inject.Inject;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import static app.krista.extensions.hook.Constants.*;
import static app.krista.extensions.hook.Constants.DOCUMENTATION;
import static app.krista.extensions.hook.Constants.DOCS_PATH;


@Java(version = Java.Version.JAVA_21)
@Field.PickOne(name = CLOUD_LLM_PROVIDER,
        values = {"OpenAI", "Claude"},
        attributes = {@Attribute(name = "visualWidth", value = "S")})
@Field.Text(name = CLOUD_API_KEY, isSecured = true,
        attributes = {
            @Attribute(name = "visualWidth", value = "S"),
            @Attribute(name = "description", value = "API Key for cloud LLM providers (OpenAI or Claude)")
        })

@Extension(implementingDomainIds = "catEntryDomain_66277b92-e35f-4838-a41d-f5e4785eb2c9",
        name = "AgenticAI",
        version = "1.0.2",
        jaxrsId = Constants.JAX_RS_ID,
        requireWorkspaceAdminRights = true,
        supportingModes = app.krista.extension.request.ExecutionMode.LIVE)
@Domain(id = "catEntryDomain_66277b92-e35f-4838-a41d-f5e4785eb2c9",
        name = "AgenticAI",
        ecosystemId = "catEntryEcosystem_2141418b-7dd8-4c04-865e-8763ba9aadcf",
        ecosystemName = "AI",
        ecosystemVersion = "4f61efe0-8b50-4ef7-a841-1334a1fe1e03")
@StaticResource(path = "docs", file = "docs")
public class CamHookExtension {

    private final Invoker invoker;

    @Inject
    public CamHookExtension(Invoker invoker) {
        this.invoker = invoker;
    }

    @InvokerRequest(InvokerRequest.Type.CUSTOM_TABS)
    public Map<String, String> customTabs() {
        return Map.of(DOCUMENTATION, DOCS_PATH);
    }

    @InvokerRequest(InvokerRequest.Type.VALIDATE_ATTRIBUTES)
    public void validateAttributes(Map<String, String> attributes) {
        System.out.println("Validating extension attributes");

        try {
            // Validate CloudLLMProvider
            String cloudProvider = attributes.get(CLOUD_LLM_PROVIDER);
            if (cloudProvider == null || cloudProvider.trim().isEmpty()) {
                throw new IllegalArgumentException("CloudLLMProvider is required. Please select either 'OpenAI' or 'Claude'.");
            }

            String normalizedProvider = cloudProvider.trim().toLowerCase();
            if (!normalizedProvider.equals("openai") && !normalizedProvider.equals("claude")) {
                throw new IllegalArgumentException("CloudLLMProvider must be either 'OpenAI' or 'Claude'. Got: " + cloudProvider);
            }

            // Validate CloudAPIKey
            String apiKey = attributes.get(CLOUD_API_KEY);
            if (apiKey == null || apiKey.trim().isEmpty()) {
                throw new IllegalArgumentException("CloudAPIKey is required for " + cloudProvider + " provider. Please provide a valid API key.");
            }

            // Test the connection with provided credentials
            testConnectionWithAttributes(attributes);

            System.out.println("Extension attributes validation completed successfully");

        } catch (Exception e) {
            System.out.println("Extension attributes validation failed: " + e.getMessage());
            throw new RuntimeException("Configuration validation failed: " + e.getMessage(), e);
        }
    }

    @InvokerRequest(InvokerRequest.Type.TEST_CONNECTION)
    public void testConnection() {
        System.out.println("Testing connection to configured LLM providers");

        try {
            if (Objects.isNull(invoker.getAttributes()) || invoker.getAttributes().isEmpty()) {
                throw new IllegalStateException("No configuration found. Please configure the extension first.");
            }

            Map<String, String> attributesMap = invoker
                    .getAttributes()
                    .entrySet()
                    .stream()
                    .collect(Collectors.toMap(Map.Entry::getKey, entry -> (String) entry.getValue()));

            // Reuse validation logic which includes connection testing
            validateAttributes(attributesMap);

            System.out.println("Connection test completed successfully");

        } catch (Exception e) {
            System.out.println("Connection test failed: " + e.getMessage());
            throw new RuntimeException("Connection test failed: " + e.getMessage(), e);
        }
    }

    /**
     * Helper method to test connection with provided attributes
     */
    private void testConnectionWithAttributes(Map<String, String> attributes) {
        System.out.println("Testing connection to LLM provider");

        try {
            String cloudProvider = attributes.get(CLOUD_LLM_PROVIDER);
            String apiKey = attributes.get(CLOUD_API_KEY);

            // Basic validation that we have the required values
            if (apiKey == null || apiKey.trim().isEmpty()) {
                throw new RuntimeException("API Key is required for connection test");
            }

            // Create the appropriate LLM client based on provider
            LLMClient client = createLLMClient(cloudProvider, apiKey);

            // Test with a simple query
            String testSystemPrompt = "You are a helpful assistant.";
            String testUserPrompt = "Say 'Connection test successful' if you can read this.";

            System.out.println("Sending test query to " + cloudProvider + " provider");
            String response = client.query(testSystemPrompt, testUserPrompt);

            if (response == null || response.trim().isEmpty()) {
                throw new RuntimeException(cloudProvider + " provider returned empty response during connection test");
            }

            System.out.println("Connection test successful for " + cloudProvider + ". Response: " +
                response.substring(0, Math.min(100, response.length())) + "...");

        } catch (Exception e) {
            System.out.println("Connection test failed: " + e.getMessage());
            throw new RuntimeException("Failed to connect to LLM provider: " + e.getMessage(), e);
        }
    }

    /**
     * Creates the appropriate LLM client based on provider type
     */
    private LLMClient createLLMClient(String cloudProvider, String apiKey) {
        String normalizedProvider = cloudProvider.trim().toLowerCase();

        return switch (normalizedProvider) {
            case "openai" -> new OpenAIClient(apiKey, "gpt-4o-mini", 10); // 10 second timeout for connection test
            case "claude" ->
                    new ClaudeClient(apiKey, "claude-3-5-sonnet-20241022", 10); // 10 second timeout for connection test
            default -> throw new IllegalArgumentException("Unsupported LLM provider: " + cloudProvider);
        };
    }
}
