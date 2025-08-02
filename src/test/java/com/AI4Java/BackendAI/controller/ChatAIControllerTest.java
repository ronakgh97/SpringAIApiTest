package com.AI4Java.BackendAI.controller;

import com.AI4Java.BackendAI.AI.AiClient_Updated;
import com.AI4Java.BackendAI.AI.Dto.ChatRequest;
import com.AI4Java.BackendAI.MyController.ChatAIController;
import com.AI4Java.BackendAI.entries.SessionEntries;
import com.AI4Java.BackendAI.entries.UserEntries;
import com.AI4Java.BackendAI.services.SessionServices;
import com.AI4Java.BackendAI.services.UserServices;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import reactor.core.publisher.Flux;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ChatAIController.class)
class ChatAIControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AiClient_Updated chatAIService;

    @MockBean
    private SessionServices sessionServices;

    @MockBean
    private UserServices userServices;

    private ObjectId testSessionId;
    private SessionEntries testSession;
    private UserEntries testUser;
    private ChatRequest validChatRequest;

    @BeforeEach
    void setUp() {
        testSessionId = new ObjectId();
        
        // Create test session
        testSession = new SessionEntries();
        testSession.setSessionId(testSessionId);
        testSession.setNameSession("Test Session");
        testSession.setModel("gpt-3.5-turbo");
        testSession.setDateTime(LocalDateTime.now());

        // Create test user
        testUser = new UserEntries();
        testUser.setUserName("testuser");
        testUser.setSessionEntries(Arrays.asList(testSession));

        // Create valid chat request
        validChatRequest = new ChatRequest();
        validChatRequest.setPrompt("Hello, how are you?");
    }

    @Test
    @WithMockUser(username = "testuser", roles = {"USER"})
    void handleChat_ValidRequest_ShouldReturnStreamingResponse() throws Exception {
        // Given
        when(sessionServices.getById(testSessionId)).thenReturn(Optional.of(testSession));
        when(userServices.findByUserName("testuser")).thenReturn(testUser);
        when(chatAIService.getAiResponse(testSessionId, validChatRequest.getPrompt()))
                .thenReturn(Flux.just("Hello", " there", "!"));

        // When & Then
        mockMvc.perform(post("/api/v1/chat/{sessionId}", testSessionId.toString())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validChatRequest)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.TEXT_EVENT_STREAM_VALUE));

        verify(sessionServices).getById(testSessionId);
        verify(userServices).findByUserName("testuser");
        verify(chatAIService).getAiResponse(testSessionId, validChatRequest.getPrompt());
    }

    @Test
    @WithMockUser(username = "testuser", roles = {"USER"})
    void handleChat_EmptyPrompt_ShouldReturnBadRequest() throws Exception {
        // Given
        ChatRequest emptyPromptRequest = new ChatRequest();
        emptyPromptRequest.setPrompt("");

        // When & Then
        mockMvc.perform(post("/api/v1/chat/{sessionId}", testSessionId.toString())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(emptyPromptRequest)))
                .andExpect(status().isBadRequest());

        verify(sessionServices, never()).getById(any());
        verify(chatAIService, never()).getAiResponse(any(), any());
    }

    @Test
    @WithMockUser(username = "testuser", roles = {"USER"})
    void handleChat_NullPrompt_ShouldReturnBadRequest() throws Exception {
        // Given
        ChatRequest nullPromptRequest = new ChatRequest();
        nullPromptRequest.setPrompt(null);

        // When & Then
        mockMvc.perform(post("/api/v1/chat/{sessionId}", testSessionId.toString())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(nullPromptRequest)))
                .andExpect(status().isBadRequest());

        verify(sessionServices, never()).getById(any());
        verify(chatAIService, never()).getAiResponse(any(), any());
    }

    @Test
    @WithMockUser(username = "testuser", roles = {"USER"})
    void handleChat_SessionNotFound_ShouldReturnNotFound() throws Exception {
        // Given
        when(sessionServices.getById(testSessionId)).thenReturn(Optional.empty());

        // When & Then
        mockMvc.perform(post("/api/v1/chat/{sessionId}", testSessionId.toString())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validChatRequest)))
                .andExpect(status().isNotFound());

        verify(sessionServices).getById(testSessionId);
        verify(userServices, never()).findByUserName(any());
        verify(chatAIService, never()).getAiResponse(any(), any());
    }

    @Test
    @WithMockUser(username = "testuser", roles = {"USER"})
    void handleChat_UserNotFound_ShouldReturnUnauthorized() throws Exception {
        // Given
        when(sessionServices.getById(testSessionId)).thenReturn(Optional.of(testSession));
        when(userServices.findByUserName("testuser")).thenReturn(null);

        // When & Then
        mockMvc.perform(post("/api/v1/chat/{sessionId}", testSessionId.toString())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validChatRequest)))
                .andExpect(status().isUnauthorized());

        verify(sessionServices).getById(testSessionId);
        verify(userServices).findByUserName("testuser");
        verify(chatAIService, never()).getAiResponse(any(), any());
    }

    @Test
    @WithMockUser(username = "testuser", roles = {"USER"})
    void handleChat_UserDoesNotOwnSession_ShouldReturnForbidden() throws Exception {
        // Given
        UserEntries userWithoutSession = new UserEntries();
        userWithoutSession.setUserName("testuser");
        userWithoutSession.setSessionEntries(Collections.emptyList());

        when(sessionServices.getById(testSessionId)).thenReturn(Optional.of(testSession));
        when(userServices.findByUserName("testuser")).thenReturn(userWithoutSession);

        // When & Then
        mockMvc.perform(post("/api/v1/chat/{sessionId}", testSessionId.toString())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validChatRequest)))
                .andExpect(status().isForbidden());

        verify(sessionServices).getById(testSessionId);
        verify(userServices).findByUserName("testuser");
        verify(chatAIService, never()).getAiResponse(any(), any());
    }

    @Test
    void handleChat_UnauthenticatedUser_ShouldReturnUnauthorized() throws Exception {
        // When & Then
        mockMvc.perform(post("/api/v1/chat/{sessionId}", testSessionId.toString())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validChatRequest)))
                .andExpect(status().isUnauthorized());

        verify(sessionServices, never()).getById(any());
        verify(userServices, never()).findByUserName(any());
        verify(chatAIService, never()).getAiResponse(any(), any());
    }

    @Test
    @WithMockUser(username = "testuser", roles = {"USER"})
    void handleChat_ServiceThrowsException_ShouldReturnInternalServerError() throws Exception {
        // Given
        when(sessionServices.getById(testSessionId)).thenReturn(Optional.of(testSession));
        when(userServices.findByUserName("testuser")).thenReturn(testUser);
        when(chatAIService.getAiResponse(testSessionId, validChatRequest.getPrompt()))
                .thenThrow(new RuntimeException("AI service error"));

        // When & Then
        mockMvc.perform(post("/api/v1/chat/{sessionId}", testSessionId.toString())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validChatRequest)))
                .andExpect(status().isInternalServerError());

        verify(sessionServices).getById(testSessionId);
        verify(userServices).findByUserName("testuser");
        verify(chatAIService).getAiResponse(testSessionId, validChatRequest.getPrompt());
    }

    @Test
    @WithMockUser(username = "testuser", roles = {"USER"})
    void handleChat_InvalidSessionId_ShouldReturnBadRequest() throws Exception {
        // When & Then
        mockMvc.perform(post("/api/v1/chat/{sessionId}", "invalid-session-id")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validChatRequest)))
                .andExpect(status().isBadRequest());

        verify(sessionServices, never()).getById(any());
        verify(userServices, never()).findByUserName(any());
        verify(chatAIService, never()).getAiResponse(any(), any());
    }

    @Test
    @WithMockUser(username = "testuser", roles = {"USER"})
    void handleChat_LongPrompt_ShouldHandle() throws Exception {
        // Given
        ChatRequest longPromptRequest = new ChatRequest();
        longPromptRequest.setPrompt("A".repeat(4999)); // Just under the 5000 limit

        when(sessionServices.getById(testSessionId)).thenReturn(Optional.of(testSession));
        when(userServices.findByUserName("testuser")).thenReturn(testUser);
        when(chatAIService.getAiResponse(testSessionId, longPromptRequest.getPrompt()))
                .thenReturn(Flux.just("Response"));

        // When & Then
        mockMvc.perform(post("/api/v1/chat/{sessionId}", testSessionId.toString())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(longPromptRequest)))
                .andExpect(status().isOk());

        verify(chatAIService).getAiResponse(testSessionId, longPromptRequest.getPrompt());
    }

    @Test
    @WithMockUser(username = "testuser", roles = {"USER"})
    void handleChat_TooLongPrompt_ShouldReturnBadRequest() throws Exception {
        // Given
        ChatRequest tooLongPromptRequest = new ChatRequest();
        tooLongPromptRequest.setPrompt("A".repeat(5001)); // Over the 5000 limit

        // When & Then
        mockMvc.perform(post("/api/v1/chat/{sessionId}", testSessionId.toString())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(tooLongPromptRequest)))
                .andExpect(status().isBadRequest());

        verify(sessionServices, never()).getById(any());
        verify(chatAIService, never()).getAiResponse(any(), any());
    }

    @Test
    @WithMockUser(username = "testuser", roles = {"USER"})
    void handleChat_SpecialCharactersInPrompt_ShouldHandle() throws Exception {
        // Given
        ChatRequest specialCharsRequest = new ChatRequest();
        specialCharsRequest.setPrompt("Test with special chars: @#$%^&*()[]{}|\\:;\"'<>,.?/`~");

        when(sessionServices.getById(testSessionId)).thenReturn(Optional.of(testSession));
        when(userServices.findByUserName("testuser")).thenReturn(testUser);
        when(chatAIService.getAiResponse(testSessionId, specialCharsRequest.getPrompt()))
                .thenReturn(Flux.just("Response"));

        // When & Then
        mockMvc.perform(post("/api/v1/chat/{sessionId}", testSessionId.toString())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(specialCharsRequest)))
                .andExpect(status().isOk());

        verify(chatAIService).getAiResponse(testSessionId, specialCharsRequest.getPrompt());
    }

    @Test
    @WithMockUser(username = "testuser", roles = {"USER"})
    void handleChat_UnicodeCharactersInPrompt_ShouldHandle() throws Exception {
        // Given
        ChatRequest unicodeRequest = new ChatRequest();
        unicodeRequest.setPrompt("Test with unicode: 你好 こんにちは 🚀 emoji test");

        when(sessionServices.getById(testSessionId)).thenReturn(Optional.of(testSession));
        when(userServices.findByUserName("testuser")).thenReturn(testUser);
        when(chatAIService.getAiResponse(testSessionId, unicodeRequest.getPrompt()))
                .thenReturn(Flux.just("Response"));

        // When & Then
        mockMvc.perform(post("/api/v1/chat/{sessionId}", testSessionId.toString())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(unicodeRequest)))
                .andExpect(status().isOk());

        verify(chatAIService).getAiResponse(testSessionId, unicodeRequest.getPrompt());
    }

    @Test
    @WithMockUser(username = "testuser", roles = {"USER"})
    void handleChat_MultilinePrompt_ShouldHandle() throws Exception {
        // Given
        ChatRequest multilineRequest = new ChatRequest();
        multilineRequest.setPrompt("Line 1\nLine 2\nLine 3");

        when(sessionServices.getById(testSessionId)).thenReturn(Optional.of(testSession));
        when(userServices.findByUserName("testuser")).thenReturn(testUser);
        when(chatAIService.getAiResponse(testSessionId, multilineRequest.getPrompt()))
                .thenReturn(Flux.just("Response"));

        // When & Then
        mockMvc.perform(post("/api/v1/chat/{sessionId}", testSessionId.toString())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(multilineRequest)))
                .andExpect(status().isOk());

        verify(chatAIService).getAiResponse(testSessionId, multilineRequest.getPrompt());
    }

    @Test
    @WithMockUser(username = "testuser", roles = {"USER"})
    void handleChat_InvalidJsonPayload_ShouldReturnBadRequest() throws Exception {
        // When & Then
        mockMvc.perform(post("/api/v1/chat/{sessionId}", testSessionId.toString())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{invalid json}"))
                .andExpect(status().isBadRequest());

        verify(sessionServices, never()).getById(any());
        verify(chatAIService, never()).getAiResponse(any(), any());
    }

    @Test
    @WithMockUser(username = "testuser", roles = {"USER"})
    void handleChat_MissingContentType_ShouldReturnUnsupportedMediaType() throws Exception {
        // When & Then
        mockMvc.perform(post("/api/v1/chat/{sessionId}", testSessionId.toString())
                        .with(csrf())
                        .content(objectMapper.writeValueAsString(validChatRequest)))
                .andExpect(status().isUnsupportedMediaType());

        verify(sessionServices, never()).getById(any());
        verify(chatAIService, never()).getAiResponse(any(), any());
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void handleChat_AdminUser_ShouldNotAccessUserSessions() throws Exception {
        // Given
        UserEntries adminUser = new UserEntries();
        adminUser.setUserName("admin");
        adminUser.setSessionEntries(Collections.emptyList());

        when(sessionServices.getById(testSessionId)).thenReturn(Optional.of(testSession));
        when(userServices.findByUserName("admin")).thenReturn(adminUser);

        // When & Then
        mockMvc.perform(post("/api/v1/chat/{sessionId}", testSessionId.toString())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validChatRequest)))
                .andExpect(status().isForbidden());

        verify(sessionServices).getById(testSessionId);
        verify(userServices).findByUserName("admin");
        verify(chatAIService, never()).getAiResponse(any(), any());
    }

    @Test
    @WithMockUser(username = "testuser", roles = {"USER"})
    void handleChat_ConcurrentRequests_ShouldHandleCorrectly() throws Exception {
        // Given
        when(sessionServices.getById(testSessionId)).thenReturn(Optional.of(testSession));
        when(userServices.findByUserName("testuser")).thenReturn(testUser);
        when(chatAIService.getAiResponse(eq(testSessionId), any(String.class)))
                .thenReturn(Flux.just("Response"));

        // When & Then - Simulate multiple concurrent requests
        for (int i = 0; i < 5; i++) {
            ChatRequest request = new ChatRequest();
            request.setPrompt("Concurrent request " + i);

            mockMvc.perform(post("/api/v1/chat/{sessionId}", testSessionId.toString())
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk());
        }

        verify(sessionServices, times(5)).getById(testSessionId);
        verify(userServices, times(5)).findByUserName("testuser");
        verify(chatAIService, times(5)).getAiResponse(eq(testSessionId), any(String.class));
    }
}
