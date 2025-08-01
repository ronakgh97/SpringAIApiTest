package com.AI4Java.BackendAI.dto.user;

import java.util.List;

public class UserResponseDto {

    private String userId;
    private String userName;
    private String gmail;
    private List<String> roles;
    private int sessionCount;

    public UserResponseDto() {}

    public UserResponseDto(String userId, String userName, String gmail, List<String> roles, int sessionCount) {
        this.userId = userId;
        this.userName = userName;
        this.gmail = gmail;
        this.roles = roles;
        this.sessionCount = sessionCount;
    }

    // Getters and setters
    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public String getGmail() {
        return gmail;
    }

    public void setGmail(String gmail) {
        this.gmail = gmail;
    }

    public List<String> getRoles() {
        return roles;
    }

    public void setRoles(List<String> roles) {
        this.roles = roles;
    }

    public int getSessionCount() {
        return sessionCount;
    }

    public void setSessionCount(int sessionCount) {
        this.sessionCount = sessionCount;
    }
}
