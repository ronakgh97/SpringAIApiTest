package com.AI4Java.BackendAI.dto.session;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class SessionCreateDto {

    @NotBlank(message = "Session name is required")
    @Size(min = 1, max = 100, message = "Session name must be between 1 and 100 characters")
    private String nameSession;

    @NotBlank(message = "Model is required")
    private String model;

    public SessionCreateDto() {}

    public SessionCreateDto(String nameSession, String model) {
        this.nameSession = nameSession;
        this.model = model;
    }

    // Getters and setters
    public String getNameSession() {
        return nameSession;
    }

    public void setNameSession(String nameSession) {
        this.nameSession = nameSession;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }
}
