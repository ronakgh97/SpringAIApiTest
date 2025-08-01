package com.AI4Java.BackendAI.exceptions;

public class SessionException extends RuntimeException {
    
    public SessionException(String message) {
        super(message);
    }
    
    public SessionException(String message, Throwable cause) {
        super(message, cause);
    }
    
    public static class SessionNotFoundException extends SessionException {
        public SessionNotFoundException(String sessionId) {
            super("Session not found: " + sessionId);
        }
    }
    
    public static class SessionAccessDeniedException extends SessionException {
        public SessionAccessDeniedException(String sessionId) {
            super("Access denied to session: " + sessionId);
        }
    }
}
