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

import app.krista.extensions.hook.llm.LLMServiceWithFallback;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

public class MessageTranslationServiceTest {

    @Mock
    private LLMServiceWithFallback mockLLMService;

    private MessageTranslationService translationService;

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        translationService = new MessageTranslationService(mockLLMService);
    }

    @Test
    public void testTranslateMessage_EnglishLanguage_ReturnsOriginalMessage() {
        // Arrange
        String englishMessage = "Hello, world!";
        String language = "en";

        // Act
        String result = translationService.translateMessage(englishMessage, language);

        // Assert
        assertEquals(englishMessage, result);
        verify(mockLLMService, never()).generateResponse(anyString(), anyString());
    }

    @Test
    public void testTranslateMessage_NullLanguage_ReturnsOriginalMessage() {
        // Arrange
        String message = "Hello, world!";
        String language = null;

        // Act
        String result = translationService.translateMessage(message, language);

        // Assert
        assertEquals(message, result);
        verify(mockLLMService, never()).generateResponse(anyString(), anyString());
    }

    @Test
    public void testTranslateMessage_EmptyLanguage_ReturnsOriginalMessage() {
        // Arrange
        String message = "Hello, world!";
        String language = "";

        // Act
        String result = translationService.translateMessage(message, language);

        // Assert
        assertEquals(message, result);
        verify(mockLLMService, never()).generateResponse(anyString(), anyString());
    }

    @Test
    public void testTranslateMessage_NullMessage_ReturnsNull() {
        // Arrange
        String message = null;
        String language = "es";

        // Act
        String result = translationService.translateMessage(message, language);

        // Assert
        assertNull(result);
        verify(mockLLMService, never()).generateResponse(anyString(), anyString());
    }

    @Test
    public void testTranslateMessage_EmptyMessage_ReturnsEmptyString() {
        // Arrange
        String message = "";
        String language = "es";

        // Act
        String result = translationService.translateMessage(message, language);

        // Assert
        assertEquals("", result);
        verify(mockLLMService, never()).generateResponse(anyString(), anyString());
    }

    @Test
    public void testTranslateMessage_SpanishTranslation_Success() {
        // Arrange
        String englishMessage = "Please type Yes to continue.";
        String language = "es";
        String expectedTranslation = "Por favor, escriba Sí para continuar.";

        // Mock LLM response - now returns the translation directly
        when(mockLLMService.generateResponse(anyString(), anyString())).thenReturn(expectedTranslation);

        // Act
        String result = translationService.translateMessage(englishMessage, language);

        // Assert
        assertEquals(expectedTranslation, result);
        verify(mockLLMService, times(1)).generateResponse(anyString(), anyString());
    }

    @Test
    public void testTranslateMessage_FrenchTranslation_Success() {
        // Arrange
        String englishMessage = "Field not supported, please switch to form based mode";
        String language = "fr";
        String expectedTranslation = "Champ non pris en charge, veuillez passer au mode basé sur les formulaires";

        // Mock LLM response - now returns the translation directly
        when(mockLLMService.generateResponse(anyString(), anyString())).thenReturn(expectedTranslation);

        // Act
        String result = translationService.translateMessage(englishMessage, language);

        // Assert
        assertEquals(expectedTranslation, result);
        verify(mockLLMService, times(1)).generateResponse(anyString(), anyString());
    }

    @Test
    public void testTranslateMessage_LLMFailure_ReturnsOriginalMessage() {
        // Arrange
        String englishMessage = "Hello, world!";
        String language = "es";

        // Mock LLM to throw exception
        when(mockLLMService.generateResponse(anyString(), anyString())).thenThrow(new RuntimeException("API is down"));

        // Act
        String result = translationService.translateMessage(englishMessage, language);

        // Assert
        assertEquals(englishMessage, result);
        verify(mockLLMService, times(1)).generateResponse(anyString(), anyString());
    }

    @Test
    public void testTranslateMessage_WithTranslatableConstants() {
        // Arrange
        String language = "es";
        String expectedTranslation = "No hay información disponible.";

        // Mock LLM response
        when(mockLLMService.generateResponse(anyString(), anyString())).thenReturn(expectedTranslation);

        // Act
        String result = TranslatableConstants.translate(TranslatableConstants.NO_INFORMATION_AVAILABLE, translationService, language);

        // Assert
        assertEquals(expectedTranslation, result);
        verify(mockLLMService, times(1)).generateResponse(anyString(), anyString());
    }

    @Test
    public void testTranslateMessage_HTMLContent_PreservesFormatting() {
        // Arrange
        String htmlMessage = "<p>Please <strong>confirm</strong> your details.</p>";
        String language = "fr";
        String expectedTranslation = "<p>Veuillez <strong>confirmer</strong> vos détails.</p>";

        // Mock LLM response
        when(mockLLMService.generateResponse(anyString(), anyString())).thenReturn(expectedTranslation);

        // Act
        String result = translationService.translateMessage(htmlMessage, language);

        // Assert
        assertEquals(expectedTranslation, result);
        assertTrue(result.contains("<p>"));
        assertTrue(result.contains("<strong>"));
        assertTrue(result.contains("</strong>"));
        assertTrue(result.contains("</p>"));
        verify(mockLLMService, times(1)).generateResponse(anyString(), anyString());
    }
}
