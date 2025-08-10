package com.AI4Java.BackendAI.MyController;

import com.AI4Java.BackendAI.AI.Dto.ChatRequest;
import com.AI4Java.BackendAI.AI.AiClient_Updated;
import com.AI4Java.BackendAI.entries.SessionEntries;
import com.AI4Java.BackendAI.entries.UserEntries;
import com.AI4Java.BackendAI.services.SessionServices;
import com.AI4Java.BackendAI.services.UserServices;
import com.AI4Java.BackendAI.utils.JwtUtil;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.security.SignatureException;
import jakarta.servlet.http.HttpServletRequest;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.List;

@RestController
@RequestMapping("/api/v1/chat")
public class ChatAIController {

    private static final Logger log = LoggerFactory.getLogger(ChatAIController.class);

    @Autowired
    private AiClient_Updated chatAIService;
    @Autowired
    private SessionServices sessionServices;
    @Autowired
    private UserServices userServices;
    @Autowired
    private JwtUtil jwtUtil;
    @Autowired
    private UserDetailsService userDetailsService;


    @PostMapping(value = "/{sessionId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<Flux<String>> handleChat(@PathVariable ObjectId sessionId, @RequestBody ChatRequest request, HttpServletRequest httpRequest) {
        String username = null;
        try {
            String authHeader = httpRequest.getHeader("Authorization");
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                log.warn("Missing or invalid Authorization header for chat request to session: {}", sessionId);
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Flux.just("Error: Unauthorized"));
            }

            String jwt = authHeader.substring(7);
            username = jwtUtil.extractUsername(jwt);
            UserDetails userDetails = userDetailsService.loadUserByUsername(username);

            if (!jwtUtil.validateToken(jwt, userDetails)) {
                log.warn("Invalid JWT token for user: {}", username);
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Flux.just("Error: Unauthorized"));
            }
        } catch (ExpiredJwtException | MalformedJwtException | SignatureException e) {
            log.warn("JWT validation error: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Flux.just("Error: Unauthorized"));
        } catch (Exception e) {
            log.error("Error during manual authentication: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Flux.just("Error: Internal server error"));
        }


        log.info("Chat request for session: {} from user: {}", sessionId, username);

        try {
            // Validate request
            if (request == null || !StringUtils.hasText(request.getPrompt())) {
                log.warn("Invalid chat request: prompt is empty for session: {}", sessionId);
                return ResponseEntity.badRequest()
                    .body(Flux.just("Error: Prompt cannot be empty"));
            }

            // Check if session exists
            SessionEntries session = sessionServices.getById(sessionId).orElse(null);
            if (session == null) {
                log.warn("Session not found with ID: {}", sessionId);
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Flux.just("Session not found with ID: " + sessionId));
            }

            // Get current user
            UserEntries currentUser = userServices.findByUserName(username);
            if (currentUser == null) {
                log.error("User not found: {}", username);
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Flux.just("User not found"));
            }

            // Check if user owns the session
            List<SessionEntries> userSessions = currentUser.getSessionEntries()
                    .stream()
                    .filter(userSession -> userSession.getSessionId().equals(sessionId))
                    .toList();

            if (userSessions.isEmpty()) {
                log.warn("User {} attempted to access unauthorized session: {}", username, sessionId);
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Flux.just("Forbidden: You do not own this session"));
            }

            log.info("Processing chat request for session: {}", sessionId);

            // Get AI response
            Flux<String> aiResponse = chatAIService.getAiResponse(sessionId, request.getPrompt(), username);
            return ResponseEntity.ok(aiResponse);

        } catch (Exception e) {
            log.error("Error processing chat request for session: {} - {}", sessionId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Flux.just("Internal server error occurred while processing your request"));
        }
    }
}

