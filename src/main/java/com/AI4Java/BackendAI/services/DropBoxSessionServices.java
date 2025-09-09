package com.AI4Java.BackendAI.services;

import com.AI4Java.BackendAI.entries.DropBoxSessionEntries;
import com.AI4Java.BackendAI.entries.UserEntries;
import com.AI4Java.BackendAI.repository.DropboxSessionRepo;
import com.AI4Java.BackendAI.repository.UserRepo;
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
public class DropBoxSessionServices {

    private static final Logger log = LoggerFactory.getLogger(DropBoxSessionServices.class);

    @Autowired
    private UserServices userServices;

    @Autowired
    private DropboxSessionRepo dropboxSessionRepo;

    @Autowired
    private UserRepo userRepo;

    @Transactional
    public void saveEntry(DropBoxSessionEntries entry, String username){
        log.info("Saving session for user: {}", username);
        UserEntries user = userServices.findByUserName(username);
        entry.setLocalDateTime(LocalDateTime.now());
        DropBoxSessionEntries dropBoxSessionEntries = dropboxSessionRepo.save(entry);
        user.getDropBoxSessionEntries().add(entry);
        userServices.saveUser(user);
        log.info("Session saved successfully for user: {}", username);
    }

    public List<DropBoxSessionEntries> getAll(){
        log.info("Retrieving all sessions");
        return dropboxSessionRepo.findAll();
    }

    public boolean deleteById(ObjectId id, String username){
        log.info("Deleting session with id: {} for user: {}", id, username);
        boolean removed=false;
        UserEntries user = userServices.findByUserName(username);
        removed = user.getDropBoxSessionEntries().removeIf(x->x.getSessionId().equals(id));
        if (removed) {
            userServices.saveUser(user);
            dropboxSessionRepo.deleteById(id);
            log.info("Session deleted successfully with id: {}", id);
        }
        return removed;
    }

    public Optional<DropBoxSessionEntries> getById(ObjectId id){
        log.info("Retrieving session by id: {}", id);
        return dropboxSessionRepo.findById(id);
    }

    @Transactional
    public void simpleSave(DropBoxSessionEntries dropBoxSessionEntries){
        log.info("Saving session");
        dropboxSessionRepo.save(dropBoxSessionEntries);
        log.info("Session saved successfully");
    }

    @Transactional
    public DropBoxSessionEntries checkIfExists(ObjectId sessionId){
        log.info("Checking if session exists with id: {}", sessionId);
        return dropboxSessionRepo.findById(sessionId)
                .orElseThrow(()->new RuntimeException("Session not found with ID: "+ sessionId));
    }

    @Transactional
    public void updateSession(DropBoxSessionEntries entry, String username){
        log.info("Updating session for user: {}", username);
        UserEntries user = userServices.findByUserName(username);
        entry.setLocalDateTime(LocalDateTime.now());
        //DropBoxSessionEntries savedSession = dropboxSessionRepo.save(entry);
        userServices.saveUser(user);
        log.info("Session updated successfully for user: {}", username);
    }
}
