package com.AI4Java.BackendAI.entries;

import java.time.LocalDateTime;

public class MessageEntries {

    private String role;

    private String content;

    private LocalDateTime timestamp;

    public MessageEntries() {
    }

    public MessageEntries(String role, String content, LocalDateTime timestamp) {
        this.role = role;
        this.content = content;
        this.timestamp = timestamp;
    }

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

