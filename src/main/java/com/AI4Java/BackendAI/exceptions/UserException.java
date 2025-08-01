package com.AI4Java.BackendAI.exceptions;

public class UserException extends RuntimeException {
    
    public UserException(String message) {
        super(message);
    }
    
    public UserException(String message, Throwable cause) {
        super(message, cause);
    }
    
    public static class UserNotFoundException extends UserException {
        public UserNotFoundException(String username) {
            super("User not found: " + username);
        }
    }
    
    public static class UserAlreadyExistsException extends UserException {
        public UserAlreadyExistsException(String username) {
            super("User already exists: " + username);
        }
    }
    
    public static class InvalidCredentialsException extends UserException {
        public InvalidCredentialsException() {
            super("Invalid username or password");
        }
    }
}
