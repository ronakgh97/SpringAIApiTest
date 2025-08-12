package com.AI4Java.BackendAI.BasicTests;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class DummyTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void contextLoads() {
        // This test will verify that the application context loads successfully
        assertTrue(true);
    }

    @Test
    void basicHealthCheck() throws Exception {
        // Basic functionality test
        mockMvc.perform(get("/api/v1/health")
           .contentType(MediaType.APPLICATION_JSON))
           .andExpect(status().isOk());

    }
}
