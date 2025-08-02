package com.AI4Java.BackendAI.performance;

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
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ChatAIController.class)
class PerformanceEdgeCaseTest {

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
    }

    // ========== Performance Tests ==========

    @Test
    @WithMockUser(username = "testuser", roles = {"USER"})
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void handleChat_ResponseTime_ShouldBeUnder5Seconds() throws Exception {
        // Given
        when(sessionServices.getById(testSessionId)).thenReturn(Optional.of(testSession));
        when(userServices.findByUserName("testuser")).thenReturn(testUser);
        when(chatAIService.getAiResponse(eq(testSessionId), any(String.class)))
                .thenReturn(Flux.just("Quick", " response"));

        ChatRequest chatRequest = new ChatRequest();
        chatRequest.setPrompt("Test prompt");

        // When & Then
        long startTime = System.currentTimeMillis();
        
        mockMvc.perform(post("/api/v1/chat/{sessionId}", testSessionId.toString())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(chatRequest)))
                .andExpect(status().isOk());

        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;
        
        assertThat(duration).isLessThan(5000); // Should complete within 5 seconds
    }

    @Test
    @WithMockUser(username = "testuser", roles = {"USER"})
    void handleChat_ConcurrentRequests_ShouldHandleMultipleUsers() throws Exception {
        // Given
        int numberOfConcurrentUsers = 10;
        CountDownLatch latch = new CountDownLatch(numberOfConcurrentUsers);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        when(sessionServices.getById(any(ObjectId.class))).thenReturn(Optional.of(testSession));
        when(userServices.findByUserName(any(String.class))).thenReturn(testUser);
        when(chatAIService.getAiResponse(any(ObjectId.class), any(String.class)))
                .thenReturn(Flux.just("Concurrent", " response"));

        ExecutorService executorService = Executors.newFixedThreadPool(numberOfConcurrentUsers);

        // When - Submit concurrent requests
        for (int i = 0; i < numberOfConcurrentUsers; i++) {
            final int userId = i;
            executorService.submit(() -> {
                try {
                    ChatRequest chatRequest = new ChatRequest();
                    chatRequest.setPrompt("Concurrent request from user " + userId);

                    MvcResult result = mockMvc.perform(post("/api/v1/chat/{sessionId}", testSessionId.toString())
                                    .with(csrf())
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(chatRequest)))
                            .andReturn();

                    if (result.getResponse().getStatus() == 200) {
                        successCount.incrementAndGet();
                    } else {
                        failureCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    failureCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        // Then - Wait for all requests to complete
        boolean completed = latch.await(30, TimeUnit.SECONDS);
        executorService.shutdown();

        assertTrue(completed, "All concurrent requests should complete within 30 seconds");
        assertThat(successCount.get()).isGreaterThan(0);
        assertThat(successCount.get() + failureCount.get()).isEqualTo(numberOfConcurrentUsers);
    }

    @Test
    @WithMockUser(username = "testuser", roles = {"USER"})
    void handleChat_LargeVolumeSequentialRequests_ShouldHandleGracefully() throws Exception {
        // Given
        int numberOfRequests = 100;
        when(sessionServices.getById(testSessionId)).thenReturn(Optional.of(testSession));
        when(userServices.findByUserName("testuser")).thenReturn(testUser);
        when(chatAIService.getAiResponse(eq(testSessionId), any(String.class)))
                .thenReturn(Flux.just("Bulk", " response"));

        ChatRequest chatRequest = new ChatRequest();
        chatRequest.setPrompt("Bulk test request");

        long startTime = System.currentTimeMillis();
        int successCount = 0;

        // When - Make many sequential requests
        for (int i = 0; i < numberOfRequests; i++) {
            MvcResult result = mockMvc.perform(post("/api/v1/chat/{sessionId}", testSessionId.toString())
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(chatRequest)))
                    .andReturn();

            if (result.getResponse().getStatus() == 200) {
                successCount++;
            }
        }

        long endTime = System.currentTimeMillis();
        long totalTime = endTime - startTime;

        // Then
        assertThat(successCount).isEqualTo(numberOfRequests);
        assertThat(totalTime).isLessThan(60000); // Should complete within 60 seconds
        
        double averageTimePerRequest = (double) totalTime / numberOfRequests;
        assertThat(averageTimePerRequest).isLessThan(500); // Average under 500ms per request
    }

    // ========== Memory and Resource Tests ==========

    @Test
    @WithMockUser(username = "testuser", roles = {"USER"})
    void handleChat_MemoryIntensivePrompts_ShouldNotCauseMemoryLeak() throws Exception {
        // Given - Large prompts to test memory handling
        when(sessionServices.getById(testSessionId)).thenReturn(Optional.of(testSession));
        when(userServices.findByUserName("testuser")).thenReturn(testUser);
        when(chatAIService.getAiResponse(eq(testSessionId), any(String.class)))
                .thenReturn(Flux.just("Memory", " test", " response"));

        Runtime runtime = Runtime.getRuntime();
        long initialMemory = runtime.totalMemory() - runtime.freeMemory();

        // When - Send multiple large prompts
        for (int i = 0; i < 10; i++) {
            ChatRequest largePrompt = new ChatRequest();
            largePrompt.setPrompt("Large prompt: " + "A".repeat(4000)); // 4KB prompt

            mockMvc.perform(post("/api/v1/chat/{sessionId}", testSessionId.toString())
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(largePrompt)))
                    .andExpect(status().isOk());

            // Force garbage collection between requests
            if (i % 3 == 0) {
                System.gc();
                Thread.sleep(10);
            }
        }

        // Then - Check memory usage hasn't grown excessively
        System.gc();
        Thread.sleep(100);
        long finalMemory = runtime.totalMemory() - runtime.freeMemory();
        long memoryIncrease = finalMemory - initialMemory;
        
        // Memory increase should be reasonable (less than 50MB)
        assertThat(memoryIncrease).isLessThan(50 * 1024 * 1024);
    }

    // ========== Edge Case Tests ==========

    @Test
    @WithMockUser(username = "testuser", roles = {"USER"})
    void handleChat_ExtremelyLongPrompt_ShouldHandleGracefully() throws Exception {
        // Given - Prompt at the maximum allowed length
        when(sessionServices.getById(testSessionId)).thenReturn(Optional.of(testSession));
        when(userServices.findByUserName("testuser")).thenReturn(testUser);
        when(chatAIService.getAiResponse(eq(testSessionId), any(String.class)))
                .thenReturn(Flux.just("Long", " prompt", " handled"));

        ChatRequest extremelyLongPrompt = new ChatRequest();
        extremelyLongPrompt.setPrompt("X".repeat(4999)); // Just under the 5000 limit

        // When & Then
        mockMvc.perform(post("/api/v1/chat/{sessionId}", testSessionId.toString())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(extremelyLongPrompt)))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "testuser", roles = {"USER"})
    void handleChat_OneByteBelowLimit_ShouldSucceed() throws Exception {
        // Given
        when(sessionServices.getById(testSessionId)).thenReturn(Optional.of(testSession));
        when(userServices.findByUserName("testuser")).thenReturn(testUser);
        when(chatAIService.getAiResponse(eq(testSessionId), any(String.class)))
                .thenReturn(Flux.just("Boundary", " test"));

        ChatRequest boundaryPrompt = new ChatRequest();
        boundaryPrompt.setPrompt("A".repeat(4999)); // Exactly 4999 characters

        // When & Then
        mockMvc.perform(post("/api/v1/chat/{sessionId}", testSessionId.toString())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(boundaryPrompt)))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "testuser", roles = {"USER"})
    void handleChat_OneByteOverLimit_ShouldFail() throws Exception {
        // Given
        ChatRequest overLimitPrompt = new ChatRequest();
        overLimitPrompt.setPrompt("A".repeat(5001)); // Exactly 5001 characters

        // When & Then
        mockMvc.perform(post("/api/v1/chat/{sessionId}", testSessionId.toString())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(overLimitPrompt)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "testuser", roles = {"USER"})
    void handleChat_OnlyWhitespacePrompt_ShouldReturnBadRequest() throws Exception {
        // Given
        ChatRequest whitespacePrompt = new ChatRequest();
        whitespacePrompt.setPrompt("   \t\n\r   "); // Only whitespace

        // When & Then
        mockMvc.perform(post("/api/v1/chat/{sessionId}", testSessionId.toString())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(whitespacePrompt)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "testuser", roles = {"USER"})
    void handleChat_SingleCharacterPrompt_ShouldSucceed() throws Exception {
        // Given
        when(sessionServices.getById(testSessionId)).thenReturn(Optional.of(testSession));
        when(userServices.findByUserName("testuser")).thenReturn(testUser);
        when(chatAIService.getAiResponse(eq(testSessionId), any(String.class)))
                .thenReturn(Flux.just("Single", " char", " response"));

        ChatRequest singleCharPrompt = new ChatRequest();
        singleCharPrompt.setPrompt("A"); // Single character

        // When & Then
        mockMvc.perform(post("/api/v1/chat/{sessionId}", testSessionId.toString())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(singleCharPrompt)))
                .andExpect(status().isOk());
    }

    // ========== Streaming Response Tests ==========

    @Test
    @WithMockUser(username = "testuser", roles = {"USER"})
    void handleChat_LongStreamingResponse_ShouldHandleCorrectly() throws Exception {
        // Given
        when(sessionServices.getById(testSessionId)).thenReturn(Optional.of(testSession));
        when(userServices.findByUserName("testuser")).thenReturn(testUser);
        
        // Create a long streaming response
        Flux<String> longStream = Flux.range(1, 1000)
                .map(i -> "chunk" + i + " ")
                .delayElements(Duration.ofMilliseconds(1));
        
        when(chatAIService.getAiResponse(eq(testSessionId), any(String.class)))
                .thenReturn(longStream);

        ChatRequest chatRequest = new ChatRequest();
        chatRequest.setPrompt("Request for long streaming response");

        // When & Then
        mockMvc.perform(post("/api/v1/chat/{sessionId}", testSessionId.toString())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(chatRequest)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.TEXT_EVENT_STREAM_VALUE));
    }

    @Test
    @WithMockUser(username = "testuser", roles = {"USER"})
    void handleChat_EmptyStreamingResponse_ShouldHandleCorrectly() throws Exception {
        // Given
        when(sessionServices.getById(testSessionId)).thenReturn(Optional.of(testSession));
        when(userServices.findByUserName("testuser")).thenReturn(testUser);
        when(chatAIService.getAiResponse(eq(testSessionId), any(String.class)))
                .thenReturn(Flux.empty());

        ChatRequest chatRequest = new ChatRequest();
        chatRequest.setPrompt("Request that returns empty stream");

        // When & Then
        mockMvc.perform(post("/api/v1/chat/{sessionId}", testSessionId.toString())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(chatRequest)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.TEXT_EVENT_STREAM_VALUE));
    }

    // ========== Resource Cleanup Tests ==========

    @Test
    @WithMockUser(username = "testuser", roles = {"USER"})
    void handleChat_MultipleSessionsCleanup_ShouldNotLeakResources() throws Exception {
        // Given - Multiple sessions to test resource cleanup
        when(userServices.findByUserName("testuser")).thenReturn(testUser);
        when(chatAIService.getAiResponse(any(ObjectId.class), any(String.class)))
                .thenReturn(Flux.just("Cleanup", " test"));

        ChatRequest chatRequest = new ChatRequest();
        chatRequest.setPrompt("Resource cleanup test");

        // When - Test with multiple different sessions
        for (int i = 0; i < 20; i++) {
            ObjectId sessionId = new ObjectId();
            SessionEntries session = new SessionEntries();
            session.setSessionId(sessionId);
            session.setModel("gpt-3.5-turbo");
            session.setDateTime(LocalDateTime.now());

            when(sessionServices.getById(sessionId)).thenReturn(Optional.of(session));

            mockMvc.perform(post("/api/v1/chat/{sessionId}", sessionId.toString())
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(chatRequest)))
                    .andExpect(status().isOk());
        }

        // Then - Verification happens through successful completion without resource errors
        verify(chatAIService, times(20)).getAiResponse(any(ObjectId.class), eq(chatRequest.getPrompt()));
    }

    // ========== Timeout and Resilience Tests ==========

    @Test
    @WithMockUser(username = "testuser", roles = {"USER"})
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void handleChat_SlowAIResponse_ShouldNotBlockIndefinitely() throws Exception {
        // Given
        when(sessionServices.getById(testSessionId)).thenReturn(Optional.of(testSession));
        when(userServices.findByUserName("testuser")).thenReturn(testUser);
        
        // Simulate slow AI response
        Flux<String> slowResponse = Flux.just("Slow", " response")
                .delayElements(Duration.ofSeconds(2));
        
        when(chatAIService.getAiResponse(eq(testSessionId), any(String.class)))
                .thenReturn(slowResponse);

        ChatRequest chatRequest = new ChatRequest();
        chatRequest.setPrompt("Slow response test");

        // When & Then - Should complete within timeout
        mockMvc.perform(post("/api/v1/chat/{sessionId}", testSessionId.toString())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(chatRequest)))
                .andExpect(status().isOk());
    }
}
