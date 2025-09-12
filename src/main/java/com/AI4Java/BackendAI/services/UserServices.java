package com.AI4Java.BackendAI.services;

import com.AI4Java.BackendAI.entries.UserEntries;
import com.AI4Java.BackendAI.repository.UserRepo;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class UserServices {

    private static final Logger log = LoggerFactory.getLogger(UserServices.class);

    @Autowired
    private UserRepo userRepo;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Transactional
    public UserEntries saveNewEntry(UserEntries entry) {
        log.info("Creating new user: {}", entry.getUserName());
        entry.setPassword(passwordEncoder.encode(entry.getPassword()));
        entry.setRoles(List.of("USER"));
        userRepo.save(entry);
        log.info("User created successfully: {}", entry.getUserName());
        return entry;
    }

    @Transactional
    public UserEntries updateRolesToAdmin(String username) {
        log.info("Attempting to grant ADMIN role to user: {}", username);
        UserEntries user = userRepo.findByUserName(username);
        if (user == null) {
            log.error("User not found: {}", username);
            throw new UsernameNotFoundException("User not found: " + username);
        }

        log.info("Granting ADMIN role to user: {}", user.getUserName());
        user.setRoles(List.of("USER", "ADMIN"));
        UserEntries savedUser = userRepo.save(user);
        log.info("Admin role granted successfully to user: {}", savedUser.getUserName());

        return savedUser;
    }

    @Transactional
    public void updateUser(UserEntries existingUser, UserEntries updatedData) {
        log.info("Updating user: {}", existingUser.getUserName());
        if (updatedData.getUserName() != null && !updatedData.getUserName().isEmpty()) {
            existingUser.setUserName(updatedData.getUserName());
        }
        if (updatedData.getPassword() != null && !updatedData.getPassword().isEmpty()) {
            existingUser.setPassword(passwordEncoder.encode(updatedData.getPassword()));
        }
        // Roles should be managed separately and not in a general update method.
        userRepo.save(existingUser);
        log.info("User updated successfully: {}", existingUser.getUserName());
    }

    @Transactional
    public void saveUser(UserEntries entry) {
        log.info("Saving user: {}", entry.getUserName());
        userRepo.save(entry);
        log.info("User saved successfully: {}", entry.getUserName());
    }

    public List<UserEntries> getAllEntry() {
        log.info("Retrieving all users");
        return userRepo.findAll();
    }

    public UserEntries findByUserName(String UserName) {
        log.info("Finding user by username: {}", UserName);
        return userRepo.findByUserName(UserName);
    }

    public UserEntries findByMail(String gmail) {
        log.info("Finding user by gmail: {}", gmail);
        return userRepo.findByGmail(gmail);
    }

    public Optional<UserEntries> findById(ObjectId id) {
        log.info("Finding user by id: {}", id);
        return userRepo.findById(id);
    }

    public void deleteById(ObjectId id) {
        log.info("Deleting user by id: {}", id);
        userRepo.deleteById(id);
        log.info("User deleted successfully with id: {}", id);
    }

}
