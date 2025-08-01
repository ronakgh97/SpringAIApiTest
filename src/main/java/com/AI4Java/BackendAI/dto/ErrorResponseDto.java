package com.AI4Java.BackendAI.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorResponseDto {
    
    private boolean success = false;
    private String message;
    private String errorCode;
    private int status;
    private LocalDateTime timestamp;
    private String path;
    private Map<String, List<String>> fieldErrors; // For validation errors
    
    public ErrorResponseDto() {
        this.timestamp = LocalDateTime.now();
    }
    
    public ErrorResponseDto(String message, String errorCode, int status, String path) {
        this();
        this.message = message;
        this.errorCode = errorCode;
        this.status = status;
        this.path = path;
    }
    
    // Static factory methods
    public static ErrorResponseDto validationError(String message, Map<String, List<String>> fieldErrors, String path) {
        ErrorResponseDto error = new ErrorResponseDto(message, "VALIDATION_ERROR", 400, path);
        error.setFieldErrors(fieldErrors);
        return error;
    }
    
    public static ErrorResponseDto businessError(String message, String errorCode, int status, String path) {
        return new ErrorResponseDto(message, errorCode, status, path);
    }
    
    public static ErrorResponseDto internalError(String path) {
        return new ErrorResponseDto("An internal server error occurred", "INTERNAL_ERROR", 500, path);
    }
    
    // Getters and setters
    public boolean isSuccess() {
        return success;
    }
    
    public void setSuccess(boolean success) {
        this.success = success;
    }
    
    public String getMessage() {
        return message;
    }
    
    public void setMessage(String message) {
        this.message = message;
    }
    
    public String getErrorCode() {
        return errorCode;
    }
    
    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
    }
    
    public int getStatus() {
        return status;
    }
    
    public void setStatus(int status) {
        this.status = status;
    }
    
    public LocalDateTime getTimestamp() {
        return timestamp;
    }
    
    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }
    
    public String getPath() {
        return path;
    }
    
    public void setPath(String path) {
        this.path = path;
    }
    
    public Map<String, List<String>> getFieldErrors() {
        return fieldErrors;
    }
    
    public void setFieldErrors(Map<String, List<String>> fieldErrors) {
        this.fieldErrors = fieldErrors;
    }
}
