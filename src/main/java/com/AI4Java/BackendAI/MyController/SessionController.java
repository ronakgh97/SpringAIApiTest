package com.AI4Java.BackendAI.MyController;

import com.AI4Java.BackendAI.dto.ApiResponse;
import com.AI4Java.BackendAI.dto.session.SessionCreateDto;
import com.AI4Java.BackendAI.dto.session.SessionResponseDto;
import com.AI4Java.BackendAI.dto.session.SessionUpdateDto;
import com.AI4Java.BackendAI.entries.SessionEntries;
import com.AI4Java.BackendAI.entries.UserEntries;
import com.AI4Java.BackendAI.exceptions.SessionException;
import com.AI4Java.BackendAI.exceptions.UserException;
import com.AI4Java.BackendAI.mapper.SessionMapper;
import com.AI4Java.BackendAI.services.SessionServices;
import com.AI4Java.BackendAI.services.UserServices;
import jakarta.validation.Valid;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/sessions")
public class SessionController {

    private static final Logger log = LoggerFactory.getLogger(SessionController.class);

    @Autowired
    private SessionServices sessionServices;

    @Autowired
    private UserServices userServices;

    @Autowired
    private SessionMapper sessionMapper;

    @PostMapping("/create")
    public ResponseEntity<ApiResponse<SessionResponseDto>> createSession(@Valid @RequestBody SessionCreateDto sessionCreateDto) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String username = authentication.getName();
        log.info("Creating session for user: {}", username);

        // Convert DTO to entity
        SessionEntries sessionEntity = sessionMapper.toEntity(sessionCreateDto);

        // Save session
        sessionServices.saveEntry(sessionEntity, username);
        log.info("Session created successfully for user: {}", username);

        // Convert back to response DTO
        SessionResponseDto responseDto = sessionMapper.toResponseDto(sessionEntity);

        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.success("Session created successfully", responseDto));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteSession(@PathVariable String id) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String username = authentication.getName();
        log.info("Deleting session with id: {} for user: {}", id, username);

        ObjectId objectId;
        try {
            objectId = new ObjectId(id);
        } catch (IllegalArgumentException e) {
            log.error("Invalid session id: {}", id);
            throw new SessionException.SessionNotFoundException(id);
        }

        boolean removed = sessionServices.deleteById(objectId, username);
        if (!removed) {
            log.error("Session not found with id: {}", id);
            throw new SessionException.SessionNotFoundException(id);
        }
        log.info("Session deleted successfully with id: {}", id);
        return ResponseEntity.ok(ApiResponse.success("Session deleted successfully", null));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<SessionResponseDto>> updateSession(@PathVariable String id,
                                                                        @Valid @RequestBody SessionUpdateDto updateDto) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String username = authentication.getName();
        log.info("Updating session with id: {} for user: {}", id, username);

        ObjectId objectId;
        try {
            objectId = new ObjectId(id);
        } catch (IllegalArgumentException e) {
            log.error("Invalid session id: {}", id);
            throw new SessionException.SessionNotFoundException(id);
        }

        UserEntries currUser = userServices.findByUserName(username);
        if (currUser == null) {
            log.error("User not found: {}", username);
            throw new UserException.UserNotFoundException(username);
        }

        // Check if user has access to this session
        boolean hasAccess = currUser.getSessionEntries()
                .stream().anyMatch(session -> session.getSessionId().equals(objectId));

        if (!hasAccess) {
            log.error("User: {} does not have access to session: {}", username, id);
            throw new SessionException.SessionAccessDeniedException(id);
        }

        Optional<SessionEntries> sessionEntry = sessionServices.getById(objectId);
        if (sessionEntry.isEmpty()) {
            log.error("Session not found with id: {}", id);
            throw new SessionException.SessionNotFoundException(id);
        }

        // Update session
        SessionEntries session = sessionEntry.get();
        if (updateDto.getNameSession() != null && !updateDto.getNameSession().trim().isEmpty()) {
            session.setNameSession(updateDto.getNameSession().trim());
        }

        // Save updated session (you might need to implement this in your service)
        sessionServices.updateSession(session, username);
        log.info("Session updated successfully with id: {}", id);

        SessionResponseDto responseDto = sessionMapper.toResponseDto(session);
        return ResponseEntity.ok(ApiResponse.success("Session updated successfully", responseDto));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<SessionResponseDto>> getSession(@PathVariable String id) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String username = authentication.getName();
        log.info("Retrieving session with id: {} for user: {}", id, username);

        ObjectId objectId;
        try {
            objectId = new ObjectId(id);
        } catch (IllegalArgumentException e) {
            log.error("Invalid session id: {}", id);
            throw new SessionException.SessionNotFoundException(id);
        }

        UserEntries currUser = userServices.findByUserName(username);
        if (currUser == null) {
            log.error("User not found: {}", username);
            throw new UserException.UserNotFoundException(username);
        }

        // Check if user has access to this session
        boolean hasAccess = currUser.getSessionEntries()
                .stream().anyMatch(session -> session.getSessionId().equals(objectId));

        if (!hasAccess) {
            log.error("User: {} does not have access to session: {}", username, id);
            throw new SessionException.SessionAccessDeniedException(id);
        }

        Optional<SessionEntries> sessionEntry = sessionServices.getById(objectId);
        if (sessionEntry.isEmpty()) {
            log.error("Session not found with id: {}", id);
            throw new SessionException.SessionNotFoundException(id);
        }

        SessionResponseDto responseDto = sessionMapper.toResponseDto(sessionEntry.get());
        log.info("Session retrieved successfully with id: {}", id);
        return ResponseEntity.ok(ApiResponse.success("Session retrieved successfully", responseDto));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<SessionResponseDto>>> getAllSessions() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String username = authentication.getName();
        log.info("Retrieving all sessions for user: {}", username);

        UserEntries user = userServices.findByUserName(username);
        if (user == null) {
            log.error("User not found: {}", username);
            throw new UserException.UserNotFoundException(username);
        }

        List<SessionEntries> sessionEntries = user.getSessionEntries();

        // Convert to DTOs (without messages for list view)
        List<SessionResponseDto> sessionDtos = sessionEntries.stream()
                .map(sessionMapper::toResponseDtoWithoutMessages)
                .collect(Collectors.toList());

        String message = sessionDtos.isEmpty() ?
            "No sessions found" :
            String.format("Found %d sessions", sessionDtos.size());
        log.info("Found {} sessions for user: {}", sessionDtos.size(), username);
        return ResponseEntity.ok(ApiResponse.success(message, sessionDtos));
    }
}
