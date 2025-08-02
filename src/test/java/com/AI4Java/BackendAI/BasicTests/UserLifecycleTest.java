package com.AI4Java.BackendAI.BasicTests;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import com.AI4Java.BackendAI.repository.SessionRepo;
import com.AI4Java.BackendAI.repository.UserRepo;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.springframework.test.web.servlet.MvcResult;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
public class UserLifecycleTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepo userRepo;

    @Autowired
    private SessionRepo sessionRepo;

    @AfterEach
    void cleanup() {
        userRepo.deleteAll();
        sessionRepo.deleteAll();
    }

    @Test
    void testUserLifecycle() throws Exception {
        // 1. Register a new user
        String userRegistrationDto = "{\"userName\":\"testuser\", \"password\":\"password\", \"gmail\":\"testuser@example.com\"}";
        mockMvc.perform(post("/api/v1/users/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(userRegistrationDto))
                .andExpect(status().isCreated());

        // 2. Login with the newly created user
        String userLoginDto = "{\"userName\":\"testuser\", \"password\":\"password\"}";
        MvcResult loginResult = mockMvc.perform(post("/api/v1/users/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(userLoginDto))
                .andExpect(status().isOk())
                .andReturn();

        String loginResponse = loginResult.getResponse().getContentAsString();
        String token = objectMapper.readTree(loginResponse).get("data").get("token").asText();

        // 3. Create a new chat session
        String sessionCreateDto = "{\"nameSession\":\"Test Session\", \"model\":\"google/gemma-3-4b\"}";
        MvcResult sessionResult = mockMvc.perform(post("/api/v1/sessions/create")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(sessionCreateDto))
                .andExpect(status().isCreated())
                .andReturn();

        String sessionResponse = sessionResult.getResponse().getContentAsString();
        String sessionId = objectMapper.readTree(sessionResponse).get("data").get("sessionId").asText();

        // 4. Chat with AI in that session
        String chatRequest = "{\"prompt\":\"Hello, AI!\"}";
        mockMvc.perform(post("/api/v1/chat/" + sessionId)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(chatRequest))
                .andExpect(status().isOk())
                .andReturn();

        // 5. Delete the session
        mockMvc.perform(delete("/api/v1/sessions/" + sessionId)
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

    }
}