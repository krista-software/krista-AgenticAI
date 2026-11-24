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

package app.krista.extensions.hook.config;

import app.krista.extension.executor.Invoker;
import app.krista.extensions.hook.Constants;

/**
 * Configuration manager for LLM provider settings
 */
public class LLMConfiguration {

    private final Invoker invoker;

    public LLMConfiguration(Invoker invoker) {
        this.invoker = invoker;
    }



    /**
     * Gets the configured cloud provider (when using cloud LLM)
     */
    public String getCloudProvider() {
        String cloudProvider = invoker.getAttributes().getOrDefault(Constants.CLOUD_LLM_PROVIDER, "openai").toString().trim().toLowerCase();
        System.out.println("Using Cloud LLM Provider: " + cloudProvider);
        return cloudProvider;
    }

    /**
     * Checks if Krista LLM fallback is enabled and has a valid API key
     * Currently always returns false as Krista LLM is disabled in setup page
     */
    public boolean isKristaLLMFallbackEnabled() {
        System.out.println("Krista LLM Fallback Enabled: false (disabled - not available in setup page)");
        return false;
    }

    /**
     * Gets the Krista API Key
     * Currently always returns empty string as Krista LLM is disabled in setup page
     */
    public String getKristaAPIKey() {
        return "";
    }


}
