package com.AI4Java.BackendAI.exception;

import com.AI4Java.BackendAI.AI.Dto.ChatRequest;
import com.AI4Java.BackendAI.utils.JwtUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.security.SignatureException;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureWebMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureWebMvc
@ActiveProfiles("test")
class SecurityExceptionHandlingTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private JwtUtil jwtUtil;

    private MockMvc mockMvc;
    private ObjectId testSessionId;
    private ChatRequest validChatRequest;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(context)
                .apply(springSecurity())  
                .build();

        testSessionId = new ObjectId();
        validChatRequest = new ChatRequest();
        validChatRequest.setPrompt("Hello, how are you?");
    }

    // ========== JWT Token Exception Tests ==========

    @Test
    void handleChat_MissingAuthorizationHeader_ShouldReturnUnauthorized() throws Exception {
        // When & Then - No Authorization header
        mockMvc.perform(post("/api/v1/chat/{sessionId}", testSessionId.toString())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validChatRequest)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void handleChat_InvalidAuthorizationHeaderFormat_ShouldReturnUnauthorized() throws Exception {
        // When & Then - Invalid header format (missing Bearer)
        mockMvc.perform(post("/api/v1/chat/{sessionId}", testSessionId.toString())
                        .header("Authorization", "InvalidFormat token123")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validChatRequest)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void handleChat_EmptyBearerToken_ShouldReturnUnauthorized() throws Exception {
        // When & Then - Empty token
        mockMvc.perform(post("/api/v1/chat/{sessionId}", testSessionId.toString())
                        .header("Authorization", "Bearer ")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validChatRequest)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void handleChat_MalformedJwtToken_ShouldReturnUnauthorized() throws Exception {
        // Given
        String malformedToken = "malformed.jwt.token";
        when(jwtUtil.extractUsername(any())).thenThrow(new MalformedJwtException("JWT strings must contain exactly 2 period characters"));

        // When & Then
        mockMvc.perform(post("/api/v1/chat/{sessionId}", testSessionId.toString())
                        .header("Authorization", "Bearer " + malformedToken)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validChatRequest)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void handleChat_ExpiredJwtToken_ShouldReturnUnauthorized() throws Exception {
        // Given
        String expiredToken = "expired.jwt.token";
        when(jwtUtil.extractUsername(any())).thenThrow(new ExpiredJwtException(null, null, "JWT expired"));

        // When & Then
        mockMvc.perform(post("/api/v1/chat/{sessionId}", testSessionId.toString())
                        .header("Authorization", "Bearer " + expiredToken)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validChatRequest)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void handleChat_InvalidJwtSignature_ShouldReturnUnauthorized() throws Exception {
        // Given
        String invalidSignatureToken = "header.payload.invalidsignature";
        when(jwtUtil.extractUsername(any())).thenThrow(new SignatureException("JWT signature does not match locally computed signature"));

        // When & Then
        mockMvc.perform(post("/api/v1/chat/{sessionId}", testSessionId.toString())
                        .header("Authorization", "Bearer " + invalidSignatureToken)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validChatRequest)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void handleChat_JwtWithInvalidClaims_ShouldReturnUnauthorized() throws Exception {
        // Given
        String invalidClaimsToken = "header.invalidclaims.signature";
        when(jwtUtil.extractUsername(any())).thenThrow(new RuntimeException("Invalid JWT claims"));

        // When & Then
        mockMvc.perform(post("/api/v1/chat/{sessionId}", testSessionId.toString())  
                        .header("Authorization", "Bearer " + invalidClaimsToken)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validChatRequest)))
                .andExpect(status().isUnauthorized());
    }

    // ========== Authentication Exception Tests ==========

    @Test
    @WithAnonymousUser
    void handleChat_AnonymousUser_ShouldReturnUnauthorized() throws Exception {
        // When & Then - Anonymous user (no authentication)
        mockMvc.perform(post("/api/v1/chat/{sessionId}", testSessionId.toString())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validChatRequest)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void handleChat_BadCredentials_ShouldReturnUnauthorized() throws Exception {
        // Given
        String invalidToken = "invalid.credentials.token";
        when(jwtUtil.extractUsername(any())).thenThrow(new BadCredentialsException("Bad credentials"));

        // When & Then
        mockMvc.perform(post("/api/v1/chat/{sessionId}", testSessionId.toString())
                        .header("Authorization", "Bearer " + invalidToken)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validChatRequest)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void handleChat_AuthenticationException_ShouldReturnUnauthorized() throws Exception {
        // Given
        String problematicToken = "problematic.auth.token";
        when(jwtUtil.extractUsername(any())).thenThrow(new AuthenticationException("Authentication failed") {});

        // When & Then
        mockMvc.perform(post("/api/v1/chat/{sessionId}", testSessionId.toString())
                        .header("Authorization", "Bearer " + problematicToken)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validChatRequest)))
                .andExpect(status().isUnauthorized());
    }

    // ========== Authorization Exception Tests ==========

    @Test
    @WithMockUser(username = "testuser", roles = {"INVALID_ROLE"})
    void handleChat_InvalidRole_ShouldReturnForbidden() throws Exception {
        // When & Then - User with invalid role
        mockMvc.perform(post("/api/v1/chat/{sessionId}", testSessionId.toString())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validChatRequest)))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "testuser", roles = {})
    void handleChat_NoRoles_ShouldReturnForbidden() throws Exception {
        // When & Then - User with no roles
        mockMvc.perform(post("/api/v1/chat/{sessionId}", testSessionId.toString())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validChatRequest)))
                .andExpect(status().isForbidden());
    }

    // ========== CSRF Exception Tests ==========

    @Test
    @WithMockUser(username = "testuser", roles = {"USER"})
    void handleChat_MissingCSRFToken_ShouldReturnForbidden() throws Exception {
        // When & Then - Missing CSRF token
        mockMvc.perform(post("/api/v1/chat/{sessionId}", testSessionId.toString())
                        // Note: No .with(csrf()) here
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validChatRequest)))
                .andExpect(status().isForbidden());
    }

    // ========== Method Not Allowed Exception Tests ==========

    @Test
    @WithMockUser(username = "testuser", roles = {"USER"})
    void handleChat_GetMethodNotAllowed_ShouldReturnMethodNotAllowed() throws Exception {
        // When & Then - Using GET instead of POST
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/v1/chat/{sessionId}", testSessionId.toString())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validChatRequest)))
                .andExpect(status().isMethodNotAllowed());
    }

    // ========== Content Type Exception Tests ==========

    @Test
    @WithMockUser(username = "testuser", roles = {"USER"})
    void handleChat_UnsupportedMediaType_ShouldReturnUnsupportedMediaType() throws Exception {
        // When & Then - Wrong content type
        mockMvc.perform(post("/api/v1/chat/{sessionId}", testSessionId.toString())
                        .with(csrf())
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("plain text content"))
                .andExpect(status().isUnsupportedMediaType());
    }

    @Test
    @WithMockUser(username = "testuser", roles = {"USER"})
    void handleChat_MissingContentType_ShouldReturnUnsupportedMediaType() throws Exception {
        // When & Then - No content type specified
        mockMvc.perform(post("/api/v1/chat/{sessionId}", testSessionId.toString())
                        .with(csrf())
                        .content(objectMapper.writeValueAsString(validChatRequest)))
                .andExpect(status().isUnsupportedMediaType());
    }

    // ========== Path Variable Exception Tests ==========

    @Test
    @WithMockUser(username = "testuser", roles = {"USER"})
    void handleChat_InvalidSessionIdFormat_ShouldReturnBadRequest() throws Exception {
        // When & Then - Invalid ObjectId format
        mockMvc.perform(post("/api/v1/chat/{sessionId}", "invalid-session-id-format")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validChatRequest)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "testuser", roles = {"USER"})
    void handleChat_EmptySessionId_ShouldReturnNotFound() throws Exception {
        // When & Then - Empty session ID
        mockMvc.perform(post("/api/v1/chat/")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validChatRequest)))
                .andExpect(status().isNotFound());
    }

    // ========== Multiple Security Issues Tests ==========

    @Test
    void handleChat_MultipleSecurityIssues_ShouldReturnUnauthorized() throws Exception {
        // When & Then - Multiple issues: no auth header, wrong content type, invalid session ID
        mockMvc.perform(post("/api/v1/chat/{sessionId}", "invalid-id")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("invalid content"))
                .andExpect(status().isUnauthorized()); // Should fail at auth first
    }

    // ========== Token Tampering Tests ==========

    @Test
    void handleChat_TamperedJwtToken_ShouldReturnUnauthorized() throws Exception {
        // Given - A JWT token that has been tampered with
        String tamperedToken = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJoYWNrZXIiLCJleHAiOjk5OTk5OTk5OTl9.invalid_signature";
        when(jwtUtil.extractUsername(any())).thenThrow(new SignatureException("JWT signature validation failed"));

        // When & Then
        mockMvc.perform(post("/api/v1/chat/{sessionId}", testSessionId.toString())
                        .header("Authorization", "Bearer " + tamperedToken)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validChatRequest)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void handleChat_JwtWithModifiedPayload_ShouldReturnUnauthorized() throws Exception {
        // Given - JWT with modified payload
        String modifiedPayloadToken = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJhZG1pbiIsInJvbGUiOiJBRE1JTiIsImV4cCI6OTk5OTk5OTk5OX0.signature";
        when(jwtUtil.extractUsername(any())).thenThrow(new SignatureException("JWT signature mismatch"));

        // When & Then
        mockMvc.perform(post("/api/v1/chat/{sessionId}", testSessionId.toString())
                        .header("Authorization", "Bearer " + modifiedPayloadToken)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validChatRequest)))
                .andExpect(status().isUnauthorized());
    }

    // ========== Rate Limiting Exception Tests (if implemented) ==========

    @Test
    @WithMockUser(username = "testuser", roles = {"USER"})
    void handleChat_TooManyRequests_ShouldHandleGracefully() throws Exception {
        // This test assumes you might implement rate limiting in the future
        // For now, it just verifies the endpoint can handle rapid requests
        
        for (int i = 0; i < 10; i++) {
            mockMvc.perform(post("/api/v1/chat/{sessionId}", testSessionId.toString())
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validChatRequest)))
                    .andExpect(status().isNotFound()); // Will fail due to session not found, but that's expected
        }
    }

    // ========== Cross-Site Request Forgery Tests ==========

    @Test
    @WithMockUser(username = "testuser", roles = {"USER"})
    void handleChat_CSRFAttack_ShouldBeBlocked() throws Exception {
        // When & Then - Attempt CSRF attack (no CSRF token)
        mockMvc.perform(post("/api/v1/chat/{sessionId}", testSessionId.toString())
                        .header("Origin", "http://malicious-site.com")
                        .header("Referer", "http://malicious-site.com/attack")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validChatRequest)))
                .andExpect(status().isForbidden());
    }

    // ========== Security Header Tests ==========

    @Test
    @WithMockUser(username = "testuser", roles = {"USER"})
    void handleChat_WithSecurityHeaders_ShouldIncludeSecurityHeaders() throws Exception {
        // When & Then - Check that security headers are present in response
        mockMvc.perform(post("/api/v1/chat/{sessionId}", testSessionId.toString())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)  
                        .content(objectMapper.writeValueAsString(validChatRequest)))
                .andExpect(status().isNotFound()) // Expected due to session not found
                .andExpect(header().exists("X-Content-Type-Options"))
                .andExpect(header().exists("X-Frame-Options"))
                .andExpect(header().exists("X-XSS-Protection"));
    }
}
