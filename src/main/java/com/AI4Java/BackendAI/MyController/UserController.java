package com.AI4Java.BackendAI.MyController;

import com.AI4Java.BackendAI.dto.ApiResponse;
import com.AI4Java.BackendAI.dto.user.*;
import com.AI4Java.BackendAI.entries.UserEntries;
import com.AI4Java.BackendAI.exceptions.UserException;
import com.AI4Java.BackendAI.mapper.UserMapper;
import com.AI4Java.BackendAI.services.UserServices;
import com.AI4Java.BackendAI.utils.JwtUtil;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private static final Logger log = LoggerFactory.getLogger(UserController.class);

    @Autowired
    private UserServices userServices;

    @Autowired
    private AuthenticationManager authenticationManager;

    @Autowired
    private UserDetailsService userDetailsService;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private UserMapper userMapper;

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<UserResponseDto>> registerUser(@Valid @RequestBody UserRegistrationDto registrationDto) {
        log.info("Registering user: {}", registrationDto.getUserName());
        // Check if user already exists
        if (userServices.findByUserName(registrationDto.getUserName()) != null) {
            log.error("User already exists: {}", registrationDto.getUserName());
            throw new UserException.UserAlreadyExistsException(registrationDto.getUserName());
        }

        //Check if gmail already exists
        if (userServices.findByMail(registrationDto.getGmail()) != null) {
            log.error("User already exists: {}", registrationDto.getUserName());
            throw new UserException.UserAlreadyExistsException(registrationDto.getGmail());
        }

        // Convert DTO to entity and save
        UserEntries userEntity = userMapper.toEntity(registrationDto);
        UserEntries savedUser = userServices.saveNewEntry(userEntity);
        log.info("User registered successfully: {}", savedUser.getUserName());

        // Convert back to response DTO
        UserResponseDto responseDto = userMapper.toResponseDto(savedUser);

        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.success("User registered successfully", responseDto));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponseDto>> loginUser(@Valid @RequestBody UserLoginDto loginDto) {
        log.info("Logging in user: {}", loginDto.getUserName());
        try {
            // Authenticate user
            authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(loginDto.getUserName(), loginDto.getPassword()));

            // Get user details
            UserDetails userDetails = userDetailsService.loadUserByUsername(loginDto.getUserName());
            String jwt = jwtUtil.generateToken(userDetails.getUsername());

            // Get user entity for response
            UserEntries userEntity = userServices.findByUserName(loginDto.getUserName());
            if (userEntity == null) {
                log.error("User not found: {}", loginDto.getUserName());
                throw new UserException.UserNotFoundException(loginDto.getUserName());
            }

            // Create response
            UserResponseDto userResponseDto = userMapper.toResponseDto(userEntity);
            LoginResponseDto loginResponse = new LoginResponseDto(jwt, userResponseDto);
            log.info("User logged in successfully: {}", loginDto.getUserName());
            return ResponseEntity.ok(ApiResponse.success("Login successful", loginResponse));

        } catch (BadCredentialsException e) {
            log.error("Invalid credentials for user: {}", loginDto.getUserName());
            throw new UserException.InvalidCredentialsException();
        }
    }

    @GetMapping("/profile")
    public ResponseEntity<ApiResponse<UserResponseDto>> getUserProfile() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String username = authentication.getName();
        log.info("Retrieving profile for user: {}", username);

        UserEntries userEntity = userServices.findByUserName(username);
        if (userEntity == null) {
            log.error("User not found: {}", username);
            throw new UserException.UserNotFoundException(username);
        }

        UserResponseDto responseDto = userMapper.toResponseDto(userEntity);
        log.info("User profile retrieved successfully for user: {}", username);
        return ResponseEntity.ok(ApiResponse.success("User profile retrieved successfully", responseDto));
    }
}
