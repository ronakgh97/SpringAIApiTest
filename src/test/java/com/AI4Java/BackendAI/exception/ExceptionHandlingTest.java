package com.AI4Java.BackendAI.exception;

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
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import reactor.core.publisher.Flux;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Optional;
import java.util.concurrent.TimeoutException;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ChatAIController.class)
class ExceptionHandlingTest {

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
        
        testSession = new SessionEntries();
        testSession.setSessionId(testSessionId);
        testSession.setNameSession("Test Session");
        testSession.setModel("gpt-3.5-turbo");
        testSession.setDateTime(LocalDateTime.now());

        testUser = new UserEntries();
        testUser.setUserName("testuser");
        testUser.setSessionEntries(Arrays.asList(testSession));

        validChatRequest = new ChatRequest();
        validChatRequest.setPrompt("Hello, how are you?");
    }

    // ========== Database Exception Tests ==========

    @Test
    @WithMockUser(username = "testuser", roles = {"USER"})
    void handleChat_DatabaseConnectionFailure_ShouldReturnInternalServerError() throws Exception {
        // Given
        when(sessionServices.getById(testSessionId))
                .thenThrow(new DataAccessException("Database connection failed") {});

        // When & Then
        mockMvc.perform(post("/api/v1/chat/{sessionId}", testSessionId.toString())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validChatRequest)))
                .andExpect(status().isInternalServerError());

        verify(sessionServices).getById(testSessionId);
        verify(userServices, never()).findByUserName(any());
        verify(chatAIService, never()).getAiResponse(any(), any());
    }

    @Test
    @WithMockUser(username = "testuser", roles = {"USER"})
    void handleChat_DatabaseTimeout_ShouldReturnInternalServerError() throws Exception {
        // Given
        when(sessionServices.getById(testSessionId))
                .thenThrow(new QueryTimeoutException("Database query timeout"));

        // When & Then
        mockMvc.perform(post("/api/v1/chat/{sessionId}", testSessionId.toString())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validChatRequest)))
                .andExpect(status().isInternalServerError());

        verify(sessionServices).getById(testSessionId);
    }

    @Test
    @WithMockUser(username = "testuser", roles = {"USER"})
    void handleChat_DataIntegrityViolation_ShouldReturnInternalServerError() throws Exception {
        // Given
        when(sessionServices.getById(testSessionId)).thenReturn(Optional.of(testSession));
        when(userServices.findByUserName("testuser"))
                .thenThrow(new DataIntegrityViolationException("Data integrity violation"));

        // When & Then
        mockMvc.perform(post("/api/v1/chat/{sessionId}", testSessionId.toString())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validChatRequest)))
                .andExpect(status().isInternalServerError());

        verify(sessionServices).getById(testSessionId);
        verify(userServices).findByUserName("testuser");
    }

    // ========== AI Service Exception Tests ==========

    @Test
    @WithMockUser(username = "testuser", roles = {"USER"})
    void handleChat_AIServiceTimeout_ShouldReturnInternalServerError() throws Exception {
        // Given
        when(sessionServices.getById(testSessionId)).thenReturn(Optional.of(testSession));
        when(userServices.findByUserName("testuser")).thenReturn(testUser);
        when(chatAIService.getAiResponse(testSessionId, validChatRequest.getPrompt()))
                .thenReturn(Flux.error(new TimeoutException("AI service timeout")));

        // When & Then
        mockMvc.perform(post("/api/v1/chat/{sessionId}", testSessionId.toString())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validChatRequest)))
                .andExpect(status().isInternalServerError());

        verify(chatAIService).getAiResponse(testSessionId, validChatRequest.getPrompt());
    }

    @Test
    @WithMockUser(username = "testuser", roles = {"USER"})
    void handleChat_AIServiceConnectionFailure_ShouldReturnInternalServerError() throws Exception {
        // Given
        when(sessionServices.getById(testSessionId)).thenReturn(Optional.of(testSession));
        when(userServices.findByUserName("testuser")).thenReturn(testUser);
        when(chatAIService.getAiResponse(testSessionId, validChatRequest.getPrompt()))
                .thenReturn(Flux.error(new RuntimeException("Connection to AI service failed")));

        // When & Then
        mockMvc.perform(post("/api/v1/chat/{sessionId}", testSessionId.toString())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validChatRequest)))
                .andExpect(status().isInternalServerError());
    }

    @Test
    @WithMockUser(username = "testuser", roles = {"USER"})
    void handleChat_AIServiceRateLimitExceeded_ShouldReturnInternalServerError() throws Exception {
        // Given
        when(sessionServices.getById(testSessionId)).thenReturn(Optional.of(testSession));
        when(userServices.findByUserName("testuser")).thenReturn(testUser);
        when(chatAIService.getAiResponse(testSessionId, validChatRequest.getPrompt()))
                .thenReturn(Flux.error(new RuntimeException("Rate limit exceeded")));

        // When & Then
        mockMvc.perform(post("/api/v1/chat/{sessionId}", testSessionId.toString())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validChatRequest)))
                .andExpect(status().isInternalServerError());
    }

    @Test
    @WithMockUser(username = "testuser", roles = {"USER"})
    void handleChat_AIServiceQuotaExceeded_ShouldReturnInternalServerError() throws Exception {
        // Given
        when(sessionServices.getById(testSessionId)).thenReturn(Optional.of(testSession));
        when(userServices.findByUserName("testuser")).thenReturn(testUser);
        when(chatAIService.getAiResponse(testSessionId, validChatRequest.getPrompt()))
                .thenReturn(Flux.error(new RuntimeException("Quota exceeded")));

        // When & Then
        mockMvc.perform(post("/api/v1/chat/{sessionId}", testSessionId.toString())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validChatRequest)))
                .andExpect(status().isInternalServerError());
    }

    // ========== Network Exception Tests ==========

    @Test
    @WithMockUser(username = "testuser", roles = {"USER"})
    void handleChat_NetworkConnectivityIssue_ShouldReturnInternalServerError() throws Exception {
        // Given
        when(sessionServices.getById(testSessionId)).thenReturn(Optional.of(testSession));
        when(userServices.findByUserName("testuser")).thenReturn(testUser);
        when(chatAIService.getAiResponse(testSessionId, validChatRequest.getPrompt()))
                .thenReturn(Flux.error(new RuntimeException("Network unreachable")));

        // When & Then
        mockMvc.perform(post("/api/v1/chat/{sessionId}", testSessionId.toString())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validChatRequest)))
                .andExpect(status().isInternalServerError());
    }

    @Test
    @WithMockUser(username = "testuser", roles = {"USER"})
    void handleChat_DNSResolutionFailure_ShouldReturnInternalServerError() throws Exception {
        // Given
        when(sessionServices.getById(testSessionId)).thenReturn(Optional.of(testSession));
        when(userServices.findByUserName("testuser")).thenReturn(testUser);
        when(chatAIService.getAiResponse(testSessionId, validChatRequest.getPrompt()))
                .thenReturn(Flux.error(new RuntimeException("DNS resolution failed")));

        // When & Then
        mockMvc.perform(post("/api/v1/chat/{sessionId}", testSessionId.toString())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validChatRequest)))
                .andExpect(status().isInternalServerError());
    }

    // ========== JSON Processing Exception Tests ==========

    @Test
    @WithMockUser(username = "testuser", roles = {"USER"})
    void handleChat_MalformedJSON_ShouldReturnBadRequest() throws Exception {
        // When & Then
        mockMvc.perform(post("/api/v1/chat/{sessionId}", testSessionId.toString())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ malformed json "))
                .andExpect(status().isBadRequest());

        verify(sessionServices, never()).getById(any());
    }

    @Test
    @WithMockUser(username = "testuser", roles = {"USER"})
    void handleChat_IncompleteJSON_ShouldReturnBadRequest() throws Exception {
        // When & Then
        mockMvc.perform(post("/api/v1/chat/{sessionId}", testSessionId.toString())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"prompt\":"))
                .andExpect(status().isBadRequest());

        verify(sessionServices, never()).getById(any());
    }

    @Test
    @WithMockUser(username = "testuser", roles = {"USER"})
    void handleChat_UnexpectedJSONStructure_ShouldReturnBadRequest() throws Exception {
        // When & Then
        mockMvc.perform(post("/api/v1/chat/{sessionId}", testSessionId.toString())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"unexpected_field\": \"value\"}"))
                .andExpect(status().isBadRequest());

        verify(sessionServices, never()).getById(any());
    }

    // ========== Validation Exception Tests ==========

    @Test
    @WithMockUser(username = "testuser", roles = {"USER"})
    void handleChat_ValidationFailure_EmptyPrompt_ShouldReturnBadRequest() throws Exception {
        // Given
        ChatRequest emptyPromptRequest = new ChatRequest();
        emptyPromptRequest.setPrompt("");

        // When & Then
        mockMvc.perform(post("/api/v1/chat/{sessionId}", testSessionId.toString())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(emptyPromptRequest)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "testuser", roles = {"USER"})
    void handleChat_ValidationFailure_TooLongPrompt_ShouldReturnBadRequest() throws Exception {
        // Given
        ChatRequest longPromptRequest = new ChatRequest();
        longPromptRequest.setPrompt("A".repeat(5001)); // Over the 5000 limit

        // When & Then
        mockMvc.perform(post("/api/v1/chat/{sessionId}", testSessionId.toString())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(longPromptRequest)))
                .andExpect(status().isBadRequest());
    }

    // ========== Memory Exception Tests ==========

    @Test
    @WithMockUser(username = "testuser", roles = {"USER"})
    void handleChat_OutOfMemoryError_ShouldReturnInternalServerError() throws Exception {
        // Given
        when(sessionServices.getById(testSessionId)).thenReturn(Optional.of(testSession));
        when(userServices.findByUserName("testuser")).thenReturn(testUser);
        when(chatAIService.getAiResponse(testSessionId, validChatRequest.getPrompt()))
                .thenThrow(new OutOfMemoryError("Java heap space"));

        // When & Then
        mockMvc.perform(post("/api/v1/chat/{sessionId}", testSessionId.toString())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validChatRequest)))
                .andExpect(status().isInternalServerError());
    }

    // ========== Threading Exception Tests ==========

    @Test
    @WithMockUser(username = "testuser", roles = {"USER"})
    void handleChat_ThreadInterruption_ShouldReturnInternalServerError() throws Exception {
        // Given
        when(sessionServices.getById(testSessionId)).thenReturn(Optional.of(testSession));
        when(userServices.findByUserName("testuser")).thenReturn(testUser);
        when(chatAIService.getAiResponse(testSessionId, validChatRequest.getPrompt()))
                .thenReturn(Flux.error(new InterruptedException("Thread interrupted")));

        // When & Then
        mockMvc.perform(post("/api/v1/chat/{sessionId}", testSessionId.toString())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validChatRequest)))
                .andExpect(status().isInternalServerError());
    }

    // ========== Security Exception Tests ==========

    @Test
    @WithMockUser(username = "testuser", roles = {"USER"})
    void handleChat_SecurityException_ShouldReturnInternalServerError() throws Exception {
        // Given
        when(sessionServices.getById(testSessionId)).thenReturn(Optional.of(testSession));
        when(userServices.findByUserName("testuser"))
                .thenThrow(new SecurityException("Access denied"));

        // When & Then
        mockMvc.perform(post("/api/v1/chat/{sessionId}", testSessionId.toString())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validChatRequest)))
                .andExpect(status().isInternalServerError());
    }

    // ========== Null Pointer Exception Tests ==========

    @Test
    @WithMockUser(username = "testuser", roles = {"USER"})
    void handleChat_NullPointerException_ShouldReturnInternalServerError() throws Exception {
        // Given
        when(sessionServices.getById(testSessionId)).thenReturn(Optional.of(testSession));
        when(userServices.findByUserName("testuser"))
                .thenThrow(new NullPointerException("Null pointer access"));

        // When & Then
        mockMvc.perform(post("/api/v1/chat/{sessionId}", testSessionId.toString())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validChatRequest)))
                .andExpect(status().isInternalServerError());
    }

    // ========== Illegal Argument Exception Tests ==========

    @Test
    @WithMockUser(username = "testuser", roles = {"USER"})
    void handleChat_IllegalArgumentException_ShouldReturnInternalServerError() throws Exception {
        // Given
        when(sessionServices.getById(testSessionId)).thenReturn(Optional.of(testSession));
        when(userServices.findByUserName("testuser")).thenReturn(testUser);
        when(chatAIService.getAiResponse(testSessionId, validChatRequest.getPrompt()))
                .thenThrow(new IllegalArgumentException("Invalid argument provided"));

        // When & Then
        mockMvc.perform(post("/api/v1/chat/{sessionId}", testSessionId.toString())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validChatRequest)))
                .andExpect(status().isInternalServerError());
    }

    // ========== Concurrent Access Exception Tests ==========

    @Test
    @WithMockUser(username = "testuser", roles = {"USER"})
    void handleChat_ConcurrentModificationException_ShouldReturnInternalServerError() throws Exception {
        // Given
        when(sessionServices.getById(testSessionId)).thenReturn(Optional.of(testSession));
        when(userServices.findByUserName("testuser")).thenReturn(testUser);
        when(chatAIService.getAiResponse(testSessionId, validChatRequest.getPrompt()))
                .thenThrow(new java.util.ConcurrentModificationException("Concurrent modification"));

        // When & Then
        mockMvc.perform(post("/api/v1/chat/{sessionId}", testSessionId.toString())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validChatRequest)))
                .andExpect(status().isInternalServerError());
    }

    // ========== Serialization Exception Tests ==========

    @Test
    @WithMockUser(username = "testuser", roles = {"USER"})
    void handleChat_SerializationException_ShouldReturnInternalServerError() throws Exception {
        // Given
        when(sessionServices.getById(testSessionId)).thenReturn(Optional.of(testSession));
        when(userServices.findByUserName("testuser")).thenReturn(testUser);
        when(chatAIService.getAiResponse(testSessionId, validChatRequest.getPrompt()))
                .thenReturn(Flux.error(new RuntimeException("Serialization failed")));

        // When & Then
        mockMvc.perform(post("/api/v1/chat/{sessionId}", testSessionId.toString())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validChatRequest)))
                .andExpect(status().isInternalServerError());
    }

    // ========== HTTP Client Exception Tests ==========

    @Test
    @WithMockUser(username = "testuser", roles = {"USER"})
    void handleChat_HttpClientException_ShouldReturnInternalServerError() throws Exception {
        // Given
        when(sessionServices.getById(testSessionId)).thenReturn(Optional.of(testSession));
        when(userServices.findByUserName("testuser")).thenReturn(testUser);
        when(chatAIService.getAiResponse(testSessionId, validChatRequest.getPrompt()))
                .thenReturn(Flux.error(new RuntimeException("HTTP 500 Internal Server Error")));

        // When & Then
        mockMvc.perform(post("/api/v1/chat/{sessionId}", testSessionId.toString())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validChatRequest)))
                .andExpect(status().isInternalServerError());
    }

    // ========== Multiple Exception Scenarios ==========

    @Test
    @WithMockUser(username = "testuser", roles = {"USER"})
    void handleChat_ChainedException_ShouldReturnInternalServerError() throws Exception {
        // Given
        RuntimeException rootCause = new RuntimeException("Root cause");
        RuntimeException chainedException = new RuntimeException("Chained exception", rootCause);
        
        when(sessionServices.getById(testSessionId)).thenReturn(Optional.of(testSession));
        when(userServices.findByUserName("testuser")).thenReturn(testUser);
        when(chatAIService.getAiResponse(testSessionId, validChatRequest.getPrompt()))
                .thenThrow(chainedException);

        // When & Then
        mockMvc.perform(post("/api/v1/chat/{sessionId}", testSessionId.toString())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validChatRequest)))
                .andExpect(status().isInternalServerError());
    }

    // ========== Exception Recovery Tests ==========

    @Test
    @WithMockUser(username = "testuser", roles = {"USER"})
    void handleChat_ExceptionOnFirstCallSuccessOnSecond_ShouldEventuallySucceed() throws Exception {
        // Given
        when(sessionServices.getById(testSessionId)).thenReturn(Optional.of(testSession));
        when(userServices.findByUserName("testuser")).thenReturn(testUser);
        
        // First call throws exception, second call succeeds
        when(chatAIService.getAiResponse(testSessionId, validChatRequest.getPrompt()))
                .thenThrow(new RuntimeException("Temporary failure"))
                .thenReturn(Flux.just("Success"));

        // When & Then - First call fails
        mockMvc.perform(post("/api/v1/chat/{sessionId}", testSessionId.toString())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validChatRequest)))
                .andExpect(status().isInternalServerError());

        // Second call succeeds
        mockMvc.perform(post("/api/v1/chat/{sessionId}", testSessionId.toString())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validChatRequest)))
                .andExpect(status().isOk());

        verify(chatAIService, times(2)).getAiResponse(testSessionId, validChatRequest.getPrompt());
    }

    // ========== Exception Message Validation Tests ==========

    @Test
    @WithMockUser(username = "testuser", roles = {"USER"})
    void handleChat_ExceptionWithCustomMessage_ShouldReturnGenericErrorMessage() throws Exception {
        // Given
        when(sessionServices.getById(testSessionId)).thenReturn(Optional.of(testSession));
        when(userServices.findByUserName("testuser")).thenReturn(testUser);
        when(chatAIService.getAiResponse(testSessionId, validChatRequest.getPrompt()))
                .thenThrow(new RuntimeException("Detailed internal error message"));

        // When & Then - Should not expose internal error details
        mockMvc.perform(post("/api/v1/chat/{sessionId}", testSessionId.toString())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validChatRequest)))
                .andExpect(status().isInternalServerError())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Internal server error occurred")));
    }
}
