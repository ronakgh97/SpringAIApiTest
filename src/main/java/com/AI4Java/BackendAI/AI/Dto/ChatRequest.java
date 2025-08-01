package com.AI4Java.BackendAI.AI.Dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class ChatRequest {
    
    @NotBlank(message = "Prompt is required")
    @Size(min = 1, max = 5000, message = "Prompt must be between 1 and 5000 characters")
    private String prompt;
    
    private String sessionId; // Optional for existing sessions

    public ChatRequest() {
    }

    public ChatRequest(String prompt) {
        this.prompt = prompt;
    }
    
    public ChatRequest(String prompt, String sessionId) {
        this.prompt = prompt;
        this.sessionId = sessionId;
    }

    public String getPrompt() {
        return prompt;
    }

    public void setPrompt(String prompt) {
        this.prompt = prompt;
    }

    public String getSessionId() {
        return sessionId;
    }
    
    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }
}
