package com.AI4Java.BackendAI.integration;

import com.AI4Java.BackendAI.AI.Dto.ChatRequest;
import com.AI4Java.BackendAI.entries.SessionEntries;
import com.AI4Java.BackendAI.entries.UserEntries;
import com.AI4Java.BackendAI.repository.SessionRepo;
import com.AI4Java.BackendAI.repository.UserRepository;
import com.AI4Java.BackendAI.services.SessionServices;
import com.AI4Java.BackendAI.services.UserServices;
import com.AI4Java.BackendAI.utils.JwtUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureWebMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.LocalDateTime;
import java.util.ArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebMvc
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SpringAiIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserServices userServices;

    @Autowired
    private SessionServices sessionServices;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SessionRepo sessionRepo;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private MockMvc mockMvc;
    private String jwtToken;
    private UserEntries testUser;
    private SessionEntries testSession;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(context)
                .apply(springSecurity())
                .build();

        // Clean up test data before each test
        userRepository.deleteAll();
        sessionRepo.deleteAll();

        // Create test user
        testUser = new UserEntries();
        testUser.setUserName("testuser");
        testUser.setEmail("test@example.com");
        testUser.setPassword(passwordEncoder.encode("password123"));
        testUser.setRoles("USER");
        testUser.setSessionEntries(new ArrayList<>());
        userServices.saveUser(testUser);

        // Create test session
        testSession = new SessionEntries();
        testSession.setSessionId(new ObjectId());
        testSession.setNameSession("Test Session");
        testSession.setModel("gpt-3.5-turbo");
        testSession.setDateTime(LocalDateTime.now());
        sessionServices.saveEntry(testSession, "testuser");

        // Generate JWT token
        jwtToken = jwtUtil.generateToken("testuser");
    }

    @AfterEach
    void tearDown() {
        // Clean up test data after each test
        userRepository.deleteAll();
        sessionRepo.deleteAll();
    }

    @Test
    @Order(1)
    void integration_UserLoginAndChatFlow_ShouldWorkEndToEnd() throws Exception {
        // Step 1: Login user (this would normally be done via /api/v1/users/login)
        assertThat(jwtToken).isNotNull();
        assertThat(jwtToken).isNotEmpty();

        // Step 2: Verify user exists and has session
        UserEntries user = userServices.findByUserName("testuser");
        assertThat(user).isNotNull();
        assertThat(user.getSessionEntries()).isNotEmpty();

        SessionEntries session = user.getSessionEntries().get(0);
        assertThat(session).isNotNull();

        // Step 3: Send chat request
        ChatRequest chatRequest = new ChatRequest();
        chatRequest.setPrompt("Hello, tell me about Japan!");

        // When & Then
        mockMvc.perform(post("/api/v1/chat/{sessionId}", session.getSessionId().toString())
                        .header("Authorization", "Bearer " + jwtToken)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(chatRequest)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.TEXT_EVENT_STREAM_VALUE));
    }

    @Test
    @Order(2)
    void integration_InvalidJwtToken_ShouldReturnUnauthorized() throws Exception {
        // Given
        String invalidToken = "invalid.jwt.token";
        ChatRequest chatRequest = new ChatRequest();
        chatRequest.setPrompt("Hello!");

        // When & Then
        mockMvc.perform(post("/api/v1/chat/{sessionId}", testSession.getSessionId().toString())
                        .header("Authorization", "Bearer " + invalidToken)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(chatRequest)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @Order(3)
    void integration_ExpiredJwtToken_ShouldReturnUnauthorized() throws Exception {
        // Given - Create an expired token (this is simulated since we can't easily create expired tokens)
        // In a real scenario, you'd wait for the token to expire or manipulate the JWT creation
        String expiredToken = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJ0ZXN0dXNlciIsImV4cCI6MTYwMDAwMDAwMH0.invalid";
        ChatRequest chatRequest = new ChatRequest();
        chatRequest.setPrompt("Hello!");

        // When & Then
        mockMvc.perform(post("/api/v1/chat/{sessionId}", testSession.getSessionId().toString())
                        .header("Authorization", "Bearer " + expiredToken)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(chatRequest)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @Order(4)
    void integration_UserAccessingAnotherUsersSession_ShouldReturnForbidden() throws Exception {
        // Given - Create another user
        UserEntries anotherUser = new UserEntries();
        anotherUser.setUserName("anotheruser");
        anotherUser.setEmail("another@example.com");
        anotherUser.setPassword(passwordEncoder.encode("password123"));
        anotherUser.setRoles("USER");
        anotherUser.setSessionEntries(new ArrayList<>());
        userServices.saveUser(anotherUser);

        // Create a session for the other user
        SessionEntries anotherSession = new SessionEntries();
        anotherSession.setSessionId(new ObjectId());
        anotherSession.setNameSession("Another Session");
        anotherSession.setModel("gpt-4");
        anotherSession.setDateTime(LocalDateTime.now());
        sessionServices.saveEntry(anotherSession, "anotheruser");

        ChatRequest chatRequest = new ChatRequest();
        chatRequest.setPrompt("Hello!");

        // When & Then - testuser trying to access anotheruser's session
        mockMvc.perform(post("/api/v1/chat/{sessionId}", anotherSession.getSessionId().toString())
                        .header("Authorization", "Bearer " + jwtToken)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(chatRequest)))
                .andExpect(status().isForbidden());
    }

    @Test
    @Order(5)
    void integration_NonExistentSession_ShouldReturnNotFound() throws Exception {
        // Given
        ObjectId nonExistentSessionId = new ObjectId();
        ChatRequest chatRequest = new ChatRequest();
        chatRequest.setPrompt("Hello!");

        // When & Then
        mockMvc.perform(post("/api/v1/chat/{sessionId}", nonExistentSessionId.toString())
                        .header("Authorization", "Bearer " + jwtToken)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(chatRequest)))
                .andExpect(status().isNotFound());
    }

    @Test
    @Order(6)
    void integration_LargePrompt_ShouldHandleCorrectly() throws Exception {
        // Given
        ChatRequest largePromptRequest = new ChatRequest();
        largePromptRequest.setPrompt("A".repeat(4999)); // Just under the limit

        UserEntries user = userServices.findByUserName("testuser");
        SessionEntries session = user.getSessionEntries().get(0);

        // When & Then
        mockMvc.perform(post("/api/v1/chat/{sessionId}", session.getSessionId().toString())
                        .header("Authorization", "Bearer " + jwtToken)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(largePromptRequest)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.TEXT_EVENT_STREAM_VALUE));
    }

    @Test
    @Order(7)
    void integration_TooLargePrompt_ShouldReturnBadRequest() throws Exception {
        // Given
        ChatRequest tooLargePromptRequest = new ChatRequest();
        tooLargePromptRequest.setPrompt("A".repeat(5001)); // Over the limit

        UserEntries user = userServices.findByUserName("testuser");
        SessionEntries session = user.getSessionEntries().get(0);

        // When & Then
        mockMvc.perform(post("/api/v1/chat/{sessionId}", session.getSessionId().toString())
                        .header("Authorization", "Bearer " + jwtToken)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(tooLargePromptRequest)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Order(8)
    void integration_MultipleSessionsForUser_ShouldWorkCorrectly() throws Exception {
        // Given - Create multiple sessions for the user
        SessionEntries session1 = new SessionEntries();
        session1.setSessionId(new ObjectId());
        session1.setNameSession("Session 1");
        session1.setModel("gpt-3.5-turbo");
        session1.setDateTime(LocalDateTime.now());
        sessionServices.saveEntry(session1, "testuser");

        SessionEntries session2 = new SessionEntries();
        session2.setSessionId(new ObjectId());
        session2.setNameSession("Session 2");
        session2.setModel("gpt-4");
        session2.setDateTime(LocalDateTime.now());
        sessionServices.saveEntry(session2, "testuser");

        ChatRequest chatRequest = new ChatRequest();
        chatRequest.setPrompt("Hello from different sessions!");

        // When & Then - Test both sessions
        mockMvc.perform(post("/api/v1/chat/{sessionId}", session1.getSessionId().toString())
                        .header("Authorization", "Bearer " + jwtToken)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(chatRequest)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/chat/{sessionId}", session2.getSessionId().toString())
                        .header("Authorization", "Bearer " + jwtToken)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(chatRequest)))
                .andExpect(status().isOk());
    }

    @Test
    @Order(9)
    void integration_DifferentModelTypes_ShouldWorkCorrectly() throws Exception {
        // Test with different model types
        String[] models = {"gpt-3.5-turbo", "gpt-4", "claude-2", "llama-2-70b"};
        
        for (String model : models) {
            // Given
            SessionEntries session = new SessionEntries();
            session.setSessionId(new ObjectId());
            session.setNameSession("Session with " + model);
            session.setModel(model);
            session.setDateTime(LocalDateTime.now());
            sessionServices.saveEntry(session, "testuser");

            ChatRequest chatRequest = new ChatRequest();
            chatRequest.setPrompt("Test with model: " + model);

            // When & Then
            mockMvc.perform(post("/api/v1/chat/{sessionId}", session.getSessionId().toString())
                            .header("Authorization", "Bearer " + jwtToken)
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(chatRequest)))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.TEXT_EVENT_STREAM_VALUE));
        }
    }

    @Test
    @Order(10)
    void integration_ConcurrentRequests_ShouldHandleCorrectly() throws Exception {
        // Given
        UserEntries user = userServices.findByUserName("testuser");
        SessionEntries session = user.getSessionEntries().get(0);
        
        // Create multiple threads to simulate concurrent requests
        int threadCount = 5;
        Thread[] threads = new Thread[threadCount];
        Exception[] exceptions = new Exception[threadCount];

        // When
        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            threads[i] = new Thread(() -> {
                try {
                    ChatRequest chatRequest = new ChatRequest();
                    chatRequest.setPrompt("Concurrent request " + index);

                    mockMvc.perform(post("/api/v1/chat/{sessionId}", session.getSessionId().toString())
                                    .header("Authorization", "Bearer " + jwtToken)
                                    .with(csrf())
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(chatRequest)))
                            .andExpect(status().isOk());
                } catch (Exception e) {
                    exceptions[index] = e;
                }
            });
            threads[i].start();
        }

        // Wait for all threads to complete
        for (Thread thread : threads) {
            thread.join();
        }

        // Then - Verify no exceptions occurred
        for (int i = 0; i < threadCount; i++) {
            assertThat(exceptions[i]).isNull();
        }
    }

    @Test
    @Order(11)
    void integration_UnicodeAndSpecialCharacters_ShouldHandleCorrectly() throws Exception {
        // Given
        UserEntries user = userServices.findByUserName("testuser");
        SessionEntries session = user.getSessionEntries().get(0);

        String[] testPrompts = {
            "Hello with unicode: 你好 こんにちは 🚀",
            "Special chars: @#$%^&*()[]{}|\\:;\"'<>,.?/`~",
            "Multiline\nPrompt\nWith\nBreaks",
            "Mixed: 日本語 English Français Español 中文 한국어",
            "Emoji test: 😀 😃 😄 😁 🚀 🌟 ⭐ 🎉 🎊 🥳"
        };

        for (String prompt : testPrompts) {
            // Given
            ChatRequest chatRequest = new ChatRequest();
            chatRequest.setPrompt(prompt);

            // When & Then
            mockMvc.perform(post("/api/v1/chat/{sessionId}", session.getSessionId().toString())
                            .header("Authorization", "Bearer " + jwtToken)
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(chatRequest)))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.TEXT_EVENT_STREAM_VALUE));
        }
    }

    @Test
    @Order(12)
    void integration_DatabasePersistence_ShouldWorkCorrectly() throws Exception {
        // Given - Verify initial state
        UserEntries initialUser = userServices.findByUserName("testuser");
        int initialSessionCount = initialUser.getSessionEntries().size();

        // Create a new session
        SessionEntries newSession = new SessionEntries();
        newSession.setSessionId(new ObjectId());
        newSession.setNameSession("Persistence Test Session");
        newSession.setModel("gpt-3.5-turbo");
        newSession.setDateTime(LocalDateTime.now());
        sessionServices.saveEntry(newSession, "testuser");

        // When - Verify session was persisted
        UserEntries updatedUser = userServices.findByUserName("testuser");
        
        // Then
        assertThat(updatedUser.getSessionEntries()).hasSize(initialSessionCount + 1);
        
        boolean sessionFound = updatedUser.getSessionEntries().stream()
                .anyMatch(s -> s.getNameSession().equals("Persistence Test Session"));
        assertThat(sessionFound).isTrue();

        // Verify we can use the new session for chat
        ChatRequest chatRequest = new ChatRequest();
        chatRequest.setPrompt("Testing persistence");

        mockMvc.perform(post("/api/v1/chat/{sessionId}", newSession.getSessionId().toString())
                        .header("Authorization", "Bearer " + jwtToken)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(chatRequest)))
                .andExpect(status().isOk());
    }
}
