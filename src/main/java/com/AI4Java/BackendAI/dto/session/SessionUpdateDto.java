package com.AI4Java.BackendAI.dto.session;

import jakarta.validation.constraints.Size;

public class SessionUpdateDto {

    @Size(min = 1, max = 100, message = "Session name must be between 1 and 100 characters")
    private String nameSession;

    public SessionUpdateDto() {}

    public SessionUpdateDto(String nameSession) {
        this.nameSession = nameSession;
    }

    // Getters and setters
    public String getNameSession() {
        return nameSession;
    }

    public void setNameSession(String nameSession) {
        this.nameSession = nameSession;
    }
}
