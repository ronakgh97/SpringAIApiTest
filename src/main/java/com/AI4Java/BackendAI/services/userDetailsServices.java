package com.AI4Java.BackendAI.services;

import com.AI4Java.BackendAI.MyController.AdminController;
import com.AI4Java.BackendAI.entries.UserEntries;
import com.AI4Java.BackendAI.repository.UserRepo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

@Service
public class userDetailsServices implements UserDetailsService {

    private static final Logger logger = LoggerFactory.getLogger(userDetailsServices.class);

    @Autowired
    private UserRepo userRepo;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        logger.debug("Looking for user with username: {}", username);

        UserEntries user;
        try {
            user = userRepo.findByUserName(username);
        } catch (Exception e) {
            logger.error("Exception while fetching user: {} ", e.getMessage());
            e.printStackTrace();
            throw new UsernameNotFoundException("Exception while fetching user: " + username, e);
        }

        if (user != null) {
            logger.debug("User found: {}", user.getUserName());
            logger.debug("User roles: {}", user.getRoles());
            logger.debug("Session entries count: {}",
                    (user.getSessionEntries() != null ? user.getSessionEntries().size() : 0));

            return User.builder()
                    .username(user.getUserName())
                    .password(user.getPassword())
                    .roles(user.getRoles().toArray(new String[0]))
                    .build();
        }
        logger.warn("No user found for username: {}", username);
        throw new UsernameNotFoundException("User not found: " + username);
    }
}
