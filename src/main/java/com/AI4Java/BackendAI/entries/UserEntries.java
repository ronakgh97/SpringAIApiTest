package com.AI4Java.BackendAI.entries;


import org.bson.types.ObjectId;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.DBRef;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Document(collection = "userEntries")
public class UserEntries {

    @Id
    private ObjectId userId;

    @Indexed(unique = true)
    private String userName;

    private String password;

    @Indexed(unique = true)
    private String gmail;

    @DBRef
    private List<SessionEntries> sessionEntries = new ArrayList<>();

    private List<String> roles;

    private boolean isVerified = false;

    private String verificationCode = null;

    private LocalDateTime verificationCodeExpires;

    public UserEntries() {
    }

    public UserEntries(ObjectId userId, String userName, String password, String gmail, List<SessionEntries> sessionEntries, List<String> roles, boolean isVerify, String verificationCode, LocalDateTime verificationCodeExpires) {
        this.userId = userId;
        this.userName = userName;
        this.password = password;
        this.gmail = gmail;
        this.sessionEntries=sessionEntries;
        this.roles=roles;
        this.isVerified =isVerify;
        this.verificationCode = verificationCode;
        this.verificationCodeExpires = verificationCodeExpires;
    }

    public ObjectId getUserId() {
        return userId;
    }

    public void setUserId(ObjectId userId) {
        this.userId = userId;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getGmail() {
        return gmail;
    }

    public void setGmail(String gmail) {
        this.gmail = gmail;
    }

    public List<SessionEntries> getSessionEntries() {
        return sessionEntries;
    }

    public void setSessionEntries(List<SessionEntries> sessionEntries) {
        this.sessionEntries = sessionEntries;
    }

    public List<String> getRoles() {
        return roles;
    }

    public void setRoles(List<String> roles) {
        this.roles = roles;
    }

    public boolean isVerified() {
        return isVerified;
    }

    public void setVerified(boolean verified) {
        isVerified = verified;
    }

    public String getVerificationCode() {
        return verificationCode;
    }

    public void setVerificationCode(String verificationCode) {
        this.verificationCode = verificationCode;
    }

    public LocalDateTime getVerificationCodeExpires() {
        return verificationCodeExpires;
    }

    public void setVerificationCodeExpires(LocalDateTime verificationCodeExpires) {
        this.verificationCodeExpires = verificationCodeExpires;
    }
}
