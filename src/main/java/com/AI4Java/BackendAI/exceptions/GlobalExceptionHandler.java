package com.AI4Java.BackendAI.exceptions;

import com.AI4Java.BackendAI.dto.ErrorResponseDto;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {
    
    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    
    // Validation errors
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponseDto> handleValidationErrors(
            MethodArgumentNotValidException ex, HttpServletRequest request) {
        
        Map<String, List<String>> fieldErrors = new HashMap<>();
        
        ex.getBindingResult().getAllErrors().forEach(error -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            
            fieldErrors.computeIfAbsent(fieldName, k -> new ArrayList<>()).add(errorMessage);
        });
        
        ErrorResponseDto errorResponse = ErrorResponseDto.validationError(
            "Validation failed", fieldErrors, request.getRequestURI());
        
        logger.warn("Validation error: {} on path: {}", fieldErrors, request.getRequestURI());
        
        return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
    }
    
    // Constraint violation errors
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponseDto> handleConstraintViolation(
            ConstraintViolationException ex, HttpServletRequest request) {
        
        Map<String, List<String>> fieldErrors = new HashMap<>();
        
        for (ConstraintViolation<?> violation : ex.getConstraintViolations()) {
            String fieldName = violation.getPropertyPath().toString();
            String errorMessage = violation.getMessage();
            fieldErrors.computeIfAbsent(fieldName, k -> new ArrayList<>()).add(errorMessage);
        }
        
        ErrorResponseDto errorResponse = ErrorResponseDto.validationError(
            "Constraint violation", fieldErrors, request.getRequestURI());
        
        logger.warn("Constraint violation: {} on path: {}", fieldErrors, request.getRequestURI());
        
        return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
    }
    
    // User-related exceptions
    @ExceptionHandler(UserException.UserNotFoundException.class)
    public ResponseEntity<ErrorResponseDto> handleUserNotFound(
            UserException.UserNotFoundException ex, HttpServletRequest request) {
        
        ErrorResponseDto errorResponse = ErrorResponseDto.businessError(
            ex.getMessage(), "USER_NOT_FOUND", 404, request.getRequestURI());
        
        logger.warn("User not found: {} on path: {}", ex.getMessage(), request.getRequestURI());
        
        return new ResponseEntity<>(errorResponse, HttpStatus.NOT_FOUND);
    }
    
    @ExceptionHandler(UserException.UserAlreadyExistsException.class)
    public ResponseEntity<ErrorResponseDto> handleUserAlreadyExists(
            UserException.UserAlreadyExistsException ex, HttpServletRequest request) {
        
        ErrorResponseDto errorResponse = ErrorResponseDto.businessError(
            ex.getMessage(), "USER_ALREADY_EXISTS", 409, request.getRequestURI());
        
        logger.warn("User already exists: {} on path: {}", ex.getMessage(), request.getRequestURI());
        
        return new ResponseEntity<>(errorResponse, HttpStatus.CONFLICT);
    }
    
    @ExceptionHandler(UserException.InvalidCredentialsException.class)
    public ResponseEntity<ErrorResponseDto> handleInvalidCredentials(
            UserException.InvalidCredentialsException ex, HttpServletRequest request) {
        
        ErrorResponseDto errorResponse = ErrorResponseDto.businessError(
            ex.getMessage(), "INVALID_CREDENTIALS", 401, request.getRequestURI());
        
        logger.warn("Invalid credentials on path: {}", request.getRequestURI());
        
        return new ResponseEntity<>(errorResponse, HttpStatus.UNAUTHORIZED);
    }
    
    // Session-related exceptions
    @ExceptionHandler(SessionException.SessionNotFoundException.class)
    public ResponseEntity<ErrorResponseDto> handleSessionNotFound(
            SessionException.SessionNotFoundException ex, HttpServletRequest request) {
        
        ErrorResponseDto errorResponse = ErrorResponseDto.businessError(
            ex.getMessage(), "SESSION_NOT_FOUND", 404, request.getRequestURI());
        
        logger.warn("Session not found: {} on path: {}", ex.getMessage(), request.getRequestURI());
        
        return new ResponseEntity<>(errorResponse, HttpStatus.NOT_FOUND);
    }
    
    @ExceptionHandler(SessionException.SessionAccessDeniedException.class)
    public ResponseEntity<ErrorResponseDto> handleSessionAccessDenied(
            SessionException.SessionAccessDeniedException ex, HttpServletRequest request) {
        
        ErrorResponseDto errorResponse = ErrorResponseDto.businessError(
            ex.getMessage(), "SESSION_ACCESS_DENIED", 403, request.getRequestURI());
        
        logger.warn("Session access denied: {} on path: {}", ex.getMessage(), request.getRequestURI());
        
        return new ResponseEntity<>(errorResponse, HttpStatus.FORBIDDEN);
    }
    
    // Security exceptions
    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ErrorResponseDto> handleBadCredentials(
            BadCredentialsException ex, HttpServletRequest request) {
        
        ErrorResponseDto errorResponse = ErrorResponseDto.businessError(
            "Invalid username or password", "INVALID_CREDENTIALS", 401, request.getRequestURI());
        
        logger.warn("Bad credentials on path: {}", request.getRequestURI());
        
        return new ResponseEntity<>(errorResponse, HttpStatus.UNAUTHORIZED);
    }
    
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponseDto> handleAuthenticationException(
            AuthenticationException ex, HttpServletRequest request) {
        
        ErrorResponseDto errorResponse = ErrorResponseDto.businessError(
            "Authentication failed", "AUTHENTICATION_FAILED", 401, request.getRequestURI());
        
        logger.warn("Authentication failed: {} on path: {}", ex.getMessage(), request.getRequestURI());
        
        return new ResponseEntity<>(errorResponse, HttpStatus.UNAUTHORIZED);
    }
    
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponseDto> handleAccessDenied(
            AccessDeniedException ex, HttpServletRequest request) {
        
        ErrorResponseDto errorResponse = ErrorResponseDto.businessError(
            "Access denied", "ACCESS_DENIED", 403, request.getRequestURI());
        
        logger.warn("Access denied: {} on path: {}", ex.getMessage(), request.getRequestURI());
        
        return new ResponseEntity<>(errorResponse, HttpStatus.FORBIDDEN);
    }
    
    // Method argument type mismatch
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponseDto> handleTypeMismatch(
            MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        
        String message = String.format("Invalid value '%s' for parameter '%s'. Expected type: %s", 
            ex.getValue(), ex.getName(), ex.getRequiredType().getSimpleName());
        
        ErrorResponseDto errorResponse = ErrorResponseDto.businessError(
            message, "TYPE_MISMATCH", 400, request.getRequestURI());
        
        logger.warn("Type mismatch: {} on path: {}", message, request.getRequestURI());
        
        return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
    }
    
    // Generic exception handler
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponseDto> handleGenericException(
            Exception ex, HttpServletRequest request) {
        
        ErrorResponseDto errorResponse = ErrorResponseDto.internalError(request.getRequestURI());
        
        logger.error("Unexpected error on path: {}", request.getRequestURI(), ex);
        
        return new ResponseEntity<>(errorResponse, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
