package com.AI4Java.BackendAI.entries;

import org.bson.types.ObjectId;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Document(collection = "sessionsEntries")
public class SessionEntries {

    @Id
    private ObjectId sessionId;

    private String nameSession;

    private String model;

    private LocalDateTime dateTime;

    List<MessageEntries> messages = new ArrayList<>();

    public SessionEntries() {
    }

    public SessionEntries(ObjectId sessionId, String nameSession, String model, LocalDateTime dateTime) {
        this.sessionId = sessionId;
        this.nameSession = nameSession;
        this.model = model;
        this.dateTime=dateTime;
    }

    public ObjectId getSessionId() {
        return sessionId;
    }

    public void setSessionId(ObjectId sessionId) {
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

    public List<MessageEntries> getMessages() { return messages; }

    public void setMessages(List<MessageEntries> messages) { this.messages = messages; }

    public LocalDateTime getDateTime() {
        return dateTime;
    }

    public void setDateTime(LocalDateTime dateTime) {
        this.dateTime = dateTime;
    }
}
