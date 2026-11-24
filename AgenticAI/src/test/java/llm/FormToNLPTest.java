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

package llm;

import app.krista.extensions.hook.common.PromptBuilder;
import app.krista.extensions.hook.common.models.StepMetadata;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import common.TestHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class FormToNLPTest {

    public static void main(String[] args) throws Exception {
        String prompt = TestHelper.readFileAsStringFromResource("prompts/InformUser.txt");
        List<String> jsonInputs = TestHelper.readJsonStringsFromResource("FormToNLP/jsonInput.json");
        List<String> sampleOutputs = TestHelper.readLinesFromResource("FormToNLP/sampleOutput.txt");

        if(sampleOutputs.size() != jsonInputs.size()) {
            throw new Exception("Json input and sample output lengths should be same");
        }

        List<String> inputs = new ArrayList<>();
        ObjectMapper objectMapper = new ObjectMapper();

        for (String jsonInput : jsonInputs) {
            // Parse JSON input to Map for PromptBuilder
            Map<String, Object> fieldValueMap = objectMapper.readValue(jsonInput, new TypeReference<>() {});

            // Create sample metadata for testing
            StepMetadata metaData = new StepMetadata(
                "Test Conversation", "test-execution-id", "Test Workspace",
                "Test Topic", "Test Step", "Test Type", "Test Description"
            );

            // Use PromptBuilder to create properly formatted prompts
            String formattedInput = PromptBuilder.createPromptForInformingUser(fieldValueMap, metaData, "English");
            inputs.add(formattedInput);
        }

        TestHelper.processTestCase(prompt, inputs, sampleOutputs, args[0], args[1], "FormToNLP.csv");
    }

}
