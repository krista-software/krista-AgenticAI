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

package app.krista.extensions.hook.llm.krista;

import okhttp3.*;
import java.io.IOException;
import java.net.ConnectException;
import java.util.concurrent.TimeUnit;

/**
 * Standalone diagnostic tool for Krista LLM connection issues.
 * Run this as a main method to diagnose connection problems.
 */
public class KristaConnectionDiagnostic {

    public static void main(String[] args) {
        System.out.println("=== Krista LLM Connection Diagnostic Tool ===\n");
        
        KristaConnectionDiagnostic diagnostic = new KristaConnectionDiagnostic();
        
        // Test different possible URLs
        String[] urlsToTest = {
            "http://10.0.0.102:8000/v1/chat/completions",
            "http://localhost:8000/v1/chat/completions",
            "http://127.0.0.1:8000/v1/chat/completions"
        };
        
        for (String url : urlsToTest) {
            diagnostic.diagnoseConnection(url);
            System.out.println();
        }
        
        System.out.println("=== Diagnostic Complete ===");
        System.out.println("\nIf all tests failed with 'Connection refused':");
        System.out.println("1. Check if Krista LLM server is running");
        System.out.println("2. Verify the correct IP address and port");
        System.out.println("3. Check firewall settings");
        System.out.println("4. Try running: curl -I http://10.0.0.102:8000/health (if health endpoint exists)");
    }

    public void diagnoseConnection(String url) {
        System.out.println("Testing: " + url);
        System.out.println("----------------------------------------");
        
        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .writeTimeout(10, TimeUnit.SECONDS)
                .build();

        // Test 1: Basic connectivity
        testBasicConnectivity(client, url);
        
        // Test 2: API endpoint test
        testApiEndpoint(client, url);
    }

    private void testBasicConnectivity(OkHttpClient client, String url) {
        System.out.print("Basic connectivity test... ");
        
        try {
            Request request = new Request.Builder()
                    .url(url.replace("/v1/chat/completions", "/")) // Test root endpoint
                    .head()
                    .build();

            try (Response response = client.newCall(request).execute()) {
                System.out.println("✅ SUCCESS (Status: " + response.code() + ")");
            }
        } catch (ConnectException e) {
            System.out.println("❌ CONNECTION REFUSED");
            System.out.println("   → Server is not running or not accessible");
        } catch (IOException e) {
            System.out.println("❌ ERROR: " + e.getMessage());
        }
    }

    private void testApiEndpoint(OkHttpClient client, String url) {
        System.out.print("API endpoint test... ");
        
        try {
            String testPayload = createMinimalTestPayload();
            
            Request request = new Request.Builder()
                    .url(url)
                    .addHeader("Content-Type", "application/json")
                    .post(RequestBody.create(testPayload, MediaType.parse("application/json")))
                    .build();

            try (Response response = client.newCall(request).execute()) {
                if (response.isSuccessful()) {
                    System.out.println("✅ SUCCESS (Status: " + response.code() + ")");
                    
                    if (response.body() != null) {
                        String responseBody = response.body().string();
                        if (responseBody.contains("choices") || responseBody.contains("content")) {
                            System.out.println("   → Valid API response received");
                        } else {
                            System.out.println("   → Unexpected response format");
                            System.out.println("   → Response: " + responseBody.substring(0, Math.min(100, responseBody.length())));
                        }
                    }
                } else {
                    System.out.println("❌ HTTP ERROR: " + response.code());
                    if (response.body() != null) {
                        String errorBody = response.body().string();
                        System.out.println("   → Error: " + errorBody.substring(0, Math.min(200, errorBody.length())));
                    }
                }
            }
        } catch (ConnectException e) {
            System.out.println("❌ CONNECTION REFUSED");
        } catch (IOException e) {
            System.out.println("❌ ERROR: " + e.getMessage());
        }
    }

    private String createMinimalTestPayload() {
        return "{\n" +
                "  \"model\": \"Qwen/Qwen3-32B-AWQ\",\n" +
                "  \"messages\": [\n" +
                "    {\n" +
                "      \"role\": \"user\",\n" +
                "      \"content\": \"Hi\"\n" +
                "    }\n" +
                "  ],\n" +
                "  \"max_tokens\": 5\n" +
                "}";
    }
}
