package com.AI4Java.BackendAI.dto.session;

import com.AI4Java.BackendAI.dto.message.MessageResponseDto;
import java.time.LocalDateTime;
import java.util.List;

public class SessionResponseDto {

    private String sessionId;
    private String nameSession;
    private String model;
    private LocalDateTime dateTime;
    private List<MessageResponseDto> messages;
    private int messageCount;

    public SessionResponseDto() {}

    public SessionResponseDto(String sessionId, String nameSession, String model, 
                            LocalDateTime dateTime, List<MessageResponseDto> messages, int messageCount) {
        this.sessionId = sessionId;
        this.nameSession = nameSession;
        this.model = model;
        this.dateTime = dateTime;
        this.messages = messages;
        this.messageCount = messageCount;
    }

    // Getters and setters
    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

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

    public LocalDateTime getDateTime() {
        return dateTime;
    }

    public void setDateTime(LocalDateTime dateTime) {
        this.dateTime = dateTime;
    }

    public List<MessageResponseDto> getMessages() {
        return messages;
    }

    public void setMessages(List<MessageResponseDto> messages) {
        this.messages = messages;
    }

    public int getMessageCount() {
        return messageCount;
    }

    public void setMessageCount(int messageCount) {
        this.messageCount = messageCount;
    }
}
