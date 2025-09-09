package com.AI4Java.BackendAI.services;

import com.AI4Java.BackendAI.entries.SessionEntries;
import com.AI4Java.BackendAI.entries.UserEntries;
import com.AI4Java.BackendAI.repository.SessionRepo;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Component
public class SessionServices {

    private static final Logger log = LoggerFactory.getLogger(SessionServices.class);

    @Autowired
    private SessionRepo sessionRepo;

    @Autowired
    private UserServices userServices;

    @Transactional
    public void saveEntry(SessionEntries entry, String username){
        log.info("Saving session for user: {}", username);
        UserEntries user = userServices.findByUserName(username);
        entry.setDateTime(LocalDateTime.now());
        SessionEntries savedSession = sessionRepo.save(entry);
        user.getSessionEntries().add(savedSession);
        userServices.saveUser(user);
        log.info("Session saved successfully for user: {}", username);
    }

    public List<SessionEntries> getAllEntry( ){
        log.info("Retrieving all sessions");
        return sessionRepo.findAll();
    }

    @Transactional
    public boolean deleteById(ObjectId id, String username){
        log.info("Deleting session with id: {} for user: {}", id, username);
        boolean removed=false;
        UserEntries user = userServices.findByUserName(username);
        removed = user.getSessionEntries().removeIf(x->x.getSessionId().equals(id));
        if(removed){
            userServices.saveUser(user);
            sessionRepo.deleteById(id);
            log.info("Session deleted successfully with id: {}", id);
        }
        return removed;
    }

    public Optional<SessionEntries> getById(ObjectId id){
        log.info("Retrieving session by id: {}", id);
        return sessionRepo.findById(id);
    }

    @Transactional
    public void simpleSave(SessionEntries sessionEntries){
        log.info("Saving session");
        sessionRepo.save(sessionEntries);
        log.info("Session saved successfully");
    }

    public SessionEntries checkIfExists(ObjectId sessionId){
        log.info("Checking if session exists with id: {}", sessionId);
        return sessionRepo.findById(sessionId)
                .orElseThrow(() -> new RuntimeException("Session not found with ID: "+ sessionId));
    }

    @Transactional
    public void updateSession(SessionEntries entry, String username){
        log.info("Updating session for user: {}", username);
        UserEntries user = userServices.findByUserName(username);
        entry.setDateTime(LocalDateTime.now());
        //SessionEntries savedSession = sessionRepo.save(entry);
        userServices.saveUser(user);
        log.info("Session updated successfully for user: {}", username);
    }

}