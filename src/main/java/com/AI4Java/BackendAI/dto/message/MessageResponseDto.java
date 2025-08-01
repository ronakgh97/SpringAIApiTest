package com.AI4Java.BackendAI.dto.message;

import java.time.LocalDateTime;

public class MessageResponseDto {

    private String role;
    private String content;
    private LocalDateTime timestamp;

    public MessageResponseDto() {}

    public MessageResponseDto(String role, String content, LocalDateTime timestamp) {
        this.role = role;
        this.content = content;
        this.timestamp = timestamp;
    }

    // Getters and setters
    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }
}
