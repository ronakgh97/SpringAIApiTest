package com.AI4Java.BackendAI.MyController;

import com.AI4Java.BackendAI.entries.UserEntries;
import com.AI4Java.BackendAI.repository.UserRepo;
import com.AI4Java.BackendAI.services.UserServices;
import com.AI4Java.BackendAI.services.userDetailsServices;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admins")
public class AdminController {

    @Autowired
    private UserServices userServices;

    @Autowired
    private userDetailsServices userDetailsServices;

    @Autowired
    private UserRepo userRepo;

    private static final Logger logger = LoggerFactory.getLogger(AdminController.class);

    @PostMapping("/make/{username}")
    public ResponseEntity<?> createAdmin(@PathVariable String username) {
        if (!StringUtils.hasText(username)) {
            logger.warn("Username is null or empty");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Username cannot be null or empty.");
        }

        UserDetails userDetails = userDetailsServices.loadUserByUsername(username);
        if (userDetails == null) {
            logger.info("User '{}' does not exist", username);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("User with this name does not exist");
        }

        boolean isAlreadyAdmin = userDetails.getAuthorities().stream()
                .anyMatch(auth -> auth.getAuthority().equals("ADMIN"));

        if (isAlreadyAdmin) {
            logger.warn("User '{}' is already an ADMIN", username);
            return ResponseEntity.status(HttpStatus.CONFLICT).body("User is already ADMIN");
        }

        try {
            UserEntries updatedUser = userServices.updateRolesToAdmin(username);
            logger.info("Admin role granted to user: {}", username);
            return ResponseEntity.status(HttpStatus.CREATED).body(updatedUser);
        } catch (UsernameNotFoundException e) {
            logger.error("User '{}' not found in repository", username);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("User not found.");
        } catch (Exception e) {
            logger.error("Error while making admin for user '{}': {}", username, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Failed to assign ADMIN role to user.");
        }
    }

    @GetMapping("/users")
    public ResponseEntity<?> getAllUsers() {
        try {
            var users = userServices.getAllEntry();
            
            if (users.isEmpty()) {
                logger.info("No users found in the system");
                return ResponseEntity.noContent().build();
            }
            
            logger.info("Retrieved {} users", users.size());
            return ResponseEntity.ok(users);
            
        } catch (Exception e) {
            logger.error("Error retrieving users: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("Failed to retrieve users.");
        }
    }
}
