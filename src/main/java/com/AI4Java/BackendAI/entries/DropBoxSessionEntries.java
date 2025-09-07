package com.AI4Java.BackendAI.entries;

import org.bson.types.ObjectId;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.LocalDateTime;

@Document(collection = "dropbox_sessionEntries")
public class DropBoxSessionEntries {

    @Id
    private ObjectId sessionId;

    private String nameSession;

    private String accessToken;

    private LocalDateTime localDateTime;

    public DropBoxSessionEntries(ObjectId sessionId, String nameSession, String accessToken, LocalDateTime localDateTime) {
        this.sessionId = sessionId;
        this.nameSession = nameSession;
        this.accessToken = accessToken;
        this.localDateTime = localDateTime;
    }

    public DropBoxSessionEntries() {
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

    public String getAccessToken() {
        return accessToken;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    public LocalDateTime getLocalDateTime() {
        return localDateTime;
    }

    public void setLocalDateTime(LocalDateTime localDateTime) {
        this.localDateTime = localDateTime;
    }
}
