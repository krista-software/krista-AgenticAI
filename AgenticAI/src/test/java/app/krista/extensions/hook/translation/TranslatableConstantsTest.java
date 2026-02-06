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

package app.krista.extensions.hook.translation;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

public class TranslatableConstantsTest {

    @Mock
    private MessageTranslationService mockTranslationService;

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    public void testTranslate_WithValidTranslationService_CallsTranslateMessage() {
        // Arrange
        String constantMessage = TranslatableConstants.NO_INFORMATION_AVAILABLE;
        String language = "es";
        String expectedTranslation = "No hay información disponible.";

        when(mockTranslationService.translateMessage(constantMessage, language)).thenReturn(expectedTranslation);

        // Act
        String result = TranslatableConstants.translate(constantMessage, mockTranslationService, language);

        // Assert
        assertEquals(expectedTranslation, result);
        verify(mockTranslationService, times(1)).translateMessage(constantMessage, language);
    }

    @Test
    public void testTranslate_WithNullTranslationService_ReturnsOriginalMessage() {
        // Arrange
        String constantMessage = TranslatableConstants.FIELD_NOT_SUPPORTED;
        String language = "fr";

        // Act
        String result = TranslatableConstants.translate(constantMessage, null, language);

        // Assert
        assertEquals(constantMessage, result);
    }





    @Test
    public void testConstants_AreNotNull() {
        // Assert all constants are properly defined
        assertNotNull(TranslatableConstants.NO_INFORMATION_AVAILABLE);
        assertNotNull(TranslatableConstants.PLEASE_TYPE_YES_TO_CONTINUE);
        assertNotNull(TranslatableConstants.FIELD_NOT_SUPPORTED);
        assertNotNull(TranslatableConstants.UNABLE_TO_CONVERT_USER_INPUT);
        assertNotNull(TranslatableConstants.ERROR_PROCESSING_REQUEST_PREFIX);
    }

    @Test
    public void testTranslateMethod_WithDifferentLanguages() {
        // Arrange
        String testMessage = "Test message";
        String[] languages = {"es", "fr", "de", "it", "pt", "ja", "ko", "zh"};
        String expectedTranslation = "Translated message";

        when(mockTranslationService.translateMessage(anyString(), anyString())).thenReturn(expectedTranslation);

        // Act & Assert
        for (String language : languages) {
            String result = TranslatableConstants.translate(testMessage, mockTranslationService, language);
            assertEquals(expectedTranslation, result);
        }

        // Verify method was called for each language
        verify(mockTranslationService, times(languages.length)).translateMessage(anyString(), anyString());
    }
}
