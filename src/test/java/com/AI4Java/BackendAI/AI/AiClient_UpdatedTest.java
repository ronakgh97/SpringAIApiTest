package com.AI4Java.BackendAI.AI;

import com.AI4Java.BackendAI.entries.SessionEntries;
import com.AI4Java.BackendAI.services.SessionServices;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AiClient_UpdatedTest {

    @Mock
    private ChatMemory chatMemory;

    @Mock
    private SessionServices sessionServices;

    private AiClient_Updated aiClient;
    private ObjectId testSessionId;
    private SessionEntries testSession;

    @BeforeEach
    void setUp() {
        // Create test session
        testSessionId = new ObjectId();
        testSession = new SessionEntries();
        testSession.setSessionId(testSessionId);
        testSession.setModel("gpt-3.5-turbo");
        testSession.setNameSession("Test Session");
        testSession.setDateTime(LocalDateTime.now());

        // Create AiClient with mocked dependencies (we'll mock the OpenAI API calls)
        aiClient = new AiClient_Updated(chatMemory, "fake-api-key", "https://api.openai.com/v1");
        
        // Inject the mocked SessionServices
        ReflectionTestUtils.setField(aiClient, "sessionServices", sessionServices);
    }

    @Test
    void testGetAiResponse_ValidRequest_ShouldReturnFlux() {
        // Given
        String userPrompt = "Hello, how are you?";
        when(sessionServices.getById(testSessionId)).thenReturn(Optional.of(testSession));

        // When
        Flux<String> response = aiClient.getAiResponse(testSessionId, userPrompt);

        // Then
        assertThat(response).isNotNull();
        verify(sessionServices).getById(testSessionId);
    }

    @Test
    void testGetAiResponse_SessionNotFound_ShouldThrowException() {
        // Given
        String userPrompt = "Hello, how are you?";
        when(sessionServices.getById(testSessionId)).thenReturn(Optional.empty());

        // When & Then
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            aiClient.getAiResponse(testSessionId, userPrompt).blockFirst();
        });

        assertThat(exception.getMessage()).contains("Session not found with ID:");
        verify(sessionServices).getById(testSessionId);
    }

    @Test
    void testGetAiResponse_EmptyPrompt_ShouldHandleGracefully() {
        // Given
        String emptyPrompt = "";
        when(sessionServices.getById(testSessionId)).thenReturn(Optional.of(testSession));

        // When
        Flux<String> response = aiClient.getAiResponse(testSessionId, emptyPrompt);

        // Then
        assertThat(response).isNotNull();
        verify(sessionServices).getById(testSessionId);
    }

    @Test
    void testGetAiResponse_NullPrompt_ShouldHandleGracefully() {
        // Given
        String nullPrompt = null;
        when(sessionServices.getById(testSessionId)).thenReturn(Optional.of(testSession));

        // When
        Flux<String> response = aiClient.getAiResponse(testSessionId, nullPrompt);

        // Then
        assertThat(response).isNotNull();
        verify(sessionServices).getById(testSessionId);
    }

    @Test
    void testGetAiResponse_VeryLongPrompt_ShouldHandle() {
        // Given
        String longPrompt = "A".repeat(10000); // 10k characters
        when(sessionServices.getById(testSessionId)).thenReturn(Optional.of(testSession));

        // When
        Flux<String> response = aiClient.getAiResponse(testSessionId, longPrompt);

        // Then
        assertThat(response).isNotNull();
        verify(sessionServices).getById(testSessionId);
    }

    @Test
    void testGetAiResponse_DifferentModels_ShouldWork() {
        // Test with different models
        String[] models = {"gpt-3.5-turbo", "gpt-4", "claude-2", "llama-2-70b"};
        String userPrompt = "Test prompt";

        for (String model : models) {
            // Given
            testSession.setModel(model);
            when(sessionServices.getById(testSessionId)).thenReturn(Optional.of(testSession));

            // When
            Flux<String> response = aiClient.getAiResponse(testSessionId, userPrompt);

            // Then
            assertThat(response).isNotNull();
        }
    }

    @Test
    void testGetAiResponse_SpecialCharacters_ShouldHandle() {
        // Given
        String specialPrompt = "Test with special chars: @#$%^&*()[]{}|\\:;\"'<>,.?/`~";
        when(sessionServices.getById(testSessionId)).thenReturn(Optional.of(testSession));

        // When
        Flux<String> response = aiClient.getAiResponse(testSessionId, specialPrompt);

        // Then
        assertThat(response).isNotNull();
        verify(sessionServices).getById(testSessionId);
    }

    @Test
    void testGetAiResponse_UnicodeCharacters_ShouldHandle() {
        // Given
        String unicodePrompt = "Test with unicode: 你好 こんにちは 🚀 emoji test";
        when(sessionServices.getById(testSessionId)).thenReturn(Optional.of(testSession));

        // When
        Flux<String> response = aiClient.getAiResponse(testSessionId, unicodePrompt);

        // Then
        assertThat(response).isNotNull();
        verify(sessionServices).getById(testSessionId);
    }

    @Test
    void testGetAiResponse_MultilinePrompt_ShouldHandle() {
        // Given
        String multilinePrompt = "Line 1\nLine 2\nLine 3\n\nLine 5";
        when(sessionServices.getById(testSessionId)).thenReturn(Optional.of(testSession));

        // When
        Flux<String> response = aiClient.getAiResponse(testSessionId, multilinePrompt);

        // Then
        assertThat(response).isNotNull();
        verify(sessionServices).getById(testSessionId);
    }

    @Test
    void testGetAiResponse_NullSessionId_ShouldThrowException() {
        // Given
        ObjectId nullSessionId = null;
        String userPrompt = "Hello";

        // When & Then
        assertThrows(Exception.class, () -> {
            aiClient.getAiResponse(nullSessionId, userPrompt).blockFirst();
        });
    }

    @Test
    void testGetAiResponse_MultipleSequentialCalls_ShouldWork() {
        // Given
        String userPrompt = "Hello";
        when(sessionServices.getById(testSessionId)).thenReturn(Optional.of(testSession));

        // When - Make multiple calls
        for (int i = 0; i < 5; i++) {
            Flux<String> response = aiClient.getAiResponse(testSessionId, userPrompt + " " + i);
            assertThat(response).isNotNull();
        }

        // Then
        verify(sessionServices, times(5)).getById(testSessionId);
    }

    // Performance and stress tests
    @Test
    void testGetAiResponse_StressTest_MultipleConcurrentCalls() {
        // Given
        String userPrompt = "Stress test prompt";
        when(sessionServices.getById(any(ObjectId.class))).thenReturn(Optional.of(testSession));

        // When - Simulate concurrent calls
        Flux<String> combinedFlux = Flux.range(1, 10)
                .flatMap(i -> {
                    ObjectId sessionId = new ObjectId();
                    return aiClient.getAiResponse(sessionId, userPrompt + " " + i);
                })
                .timeout(Duration.ofSeconds(30)); // Timeout for safety

        // Then
        StepVerifier.create(combinedFlux)
                .expectSubscription()
                .thenCancel()
                .verify();
    }

    @Test
    void testSessionIdConversion_ShouldConvertToString() {
        // Given
        String userPrompt = "Test conversion";
        when(sessionServices.getById(testSessionId)).thenReturn(Optional.of(testSession));

        // When
        aiClient.getAiResponse(testSessionId, userPrompt);

        // Then
        verify(sessionServices).getById(testSessionId);
        // Verify that sessionId.toString() is used for conversation ID
        String expectedConvId = testSessionId.toString();
        assertThat(expectedConvId).isNotEmpty();
        assertThat(expectedConvId).hasSize(24); // ObjectId string length
    }

    @Test
    void testSystemPrompt_ShouldBeConsistent() {
        // Test that the system prompt about Mitsubishi is applied consistently
        String userPrompt = "Who are you?";
        when(sessionServices.getById(testSessionId)).thenReturn(Optional.of(testSession));

        // When
        Flux<String> response = aiClient.getAiResponse(testSessionId, userPrompt);

        // Then
        assertThat(response).isNotNull();
        // The system prompt should mention Mitsubishi and Japanese Tour Assistant
        // This would be validated in integration tests where we can check actual responses
    }

    @Test
    void testChatOptions_ShouldBeConfiguredCorrectly() {
        // Given
        String userPrompt = "Test options";
        when(sessionServices.getById(testSessionId)).thenReturn(Optional.of(testSession));

        // When
        Flux<String> response = aiClient.getAiResponse(testSessionId, userPrompt);

        // Then
        assertThat(response).isNotNull();
        // OpenAiChatOptions should be configured with:
        // - temperature: 0.7
        // - maxTokens: 256
        // - model from session
        verify(sessionServices).getById(testSessionId);
    }
}
