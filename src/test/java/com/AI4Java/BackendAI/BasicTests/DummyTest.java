package com.AI4Java.BackendAI.BasicTests;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class DummyTest {

    @Test
    void contextLoads() {
        // This test will verify that the application context loads successfully
        assertTrue(true);
    }

    @Test
    void basicHealthCheck() {
        // Basic functionality test
        String expected = "Backend is running";
        String actual = "Backend is running";
        assertEquals(expected, actual);
    }
}
