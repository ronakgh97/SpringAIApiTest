package com.AI4Java.BackendAI.dto.user;

public class LoginResponseDto {

    private String token;
    private String tokenType = "Bearer";
    private UserResponseDto user;

    public LoginResponseDto() {}

    public LoginResponseDto(String token, UserResponseDto user) {
        this.token = token;
        this.user = user;
    }

    // Getters and setters
    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getTokenType() {
        return tokenType;
    }

    public void setTokenType(String tokenType) {
        this.tokenType = tokenType;
    }

    public UserResponseDto getUser() {
        return user;
    }

    public void setUser(UserResponseDto user) {
        this.user = user;
    }
}
