package com.AI4Java.BackendAI.BasicTests;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
public class CreateUserWithOneSessionMessage {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void createUser() throws Exception {
        // 1. Register a new user
        String userRegistrationDto = "{\"userName\":\"ronak777\", \"password\":\"123456\", \"gmail\":\"ronakgh97@gmail.com\"}";
        mockMvc.perform(post("/api/v1/users/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(userRegistrationDto))
                .andExpect(status().isCreated());

        // 2. Login with the newly created user
        String userLoginDto = "{\"userName\":\"ronak777\", \"password\":\"123456\"}";
        MvcResult loginResult = mockMvc.perform(post("/api/v1/users/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(userLoginDto))
                .andExpect(status().isOk())
                .andReturn();

        String loginResponse = loginResult.getResponse().getContentAsString();
        String token = objectMapper.readTree(loginResponse).get("data").get("token").asText();

        createSession(token);

    }

    void createSession(String token) throws Exception {
        // 3. Create a new chat session
        String sessionCreateDto = "{\"nameSession\":\"Session testing\", \"model\":\"google/gemma-3-4b\"}";
        MvcResult sessionResult = mockMvc.perform(post("/api/v1/sessions/create")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(sessionCreateDto))
                .andExpect(status().isCreated())
                .andReturn();

        String sessionResponse = sessionResult.getResponse().getContentAsString();
        String sessionId = objectMapper.readTree(sessionResponse).get("data").get("sessionId").asText();

        sendMessage(token, sessionId);

    }

    void sendMessage(String token, String sessionId) throws Exception {
        String chatRequest = "{\"prompt\":\"Hello Hashimoto!!, Can you send me a mail of recent news on trump and putin visit alaska summit?\"}";
        mockMvc.perform(post("/api/v1/chat/" + sessionId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(chatRequest))
                .andExpect(status().isOk())
                .andReturn();

        Thread.sleep(100000); //For to save the sendMessage

    }

}
