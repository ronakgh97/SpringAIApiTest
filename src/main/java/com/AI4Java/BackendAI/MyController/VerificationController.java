package com.AI4Java.BackendAI.MyController;

import com.AI4Java.BackendAI.dto.ApiResponse;
import com.AI4Java.BackendAI.entries.UserEntries;
import com.AI4Java.BackendAI.exceptions.UserException;
import com.AI4Java.BackendAI.services.UserServices;
import com.AI4Java.BackendAI.services.VerificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/verify")
public class VerificationController {

    private static final Logger log = LoggerFactory.getLogger(VerificationController.class);

    @Autowired
    private VerificationService verificationService;

    @Autowired
    private UserServices userServices;

    @GetMapping("")
    public ResponseEntity<ApiResponse<String>> sendVerificationCode() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        log.info("Authentication object in sendVerificationCode: {}", authentication);
        String username = authentication.getName();
        UserEntries userEntries = userServices.findByUserName(username);
        try {
            log.info("Request to send verification code to: {}", userEntries.getGmail());
            verificationService.sendVerificationCode(userEntries, userEntries.getGmail());
            return ResponseEntity.ok(ApiResponse.success("Verification code sent to " + userEntries.getGmail(), null));
        } catch (UserException.UserNotFoundException e) {
            log.warn("Failed to send verification code. User not found with gmail: {}", userEntries.getGmail());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(e.getMessage(), null));
        } catch (Exception e) {
            log.error("An unexpected error occurred while sending verification code to: {}", userEntries.getGmail(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiResponse.error("An unexpected error occurred.", null));
        }
    }

    @PostMapping("/{code}")
    public ResponseEntity<ApiResponse<String>> verifyCode(@PathVariable String code) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        log.info("Authentication object in verifyCode: {}", authentication);
        String username = authentication.getName();
        UserEntries userEntries = userServices.findByUserName(username);
        try {
            log.info("Request to verify code for: {}", userEntries.getGmail());
            boolean isVerified = verificationService.verifyCode(userEntries, userEntries.getGmail(), code);
            if (isVerified) {
                return ResponseEntity.ok(ApiResponse.success("Verification successful", null));
            }
            return ResponseEntity.badRequest().body(ApiResponse.error("Invalid or expired verification code", null));
        } catch (Exception e) {
            log.error("An unexpected error occurred during verification for: {}", userEntries.getGmail(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiResponse.error("An unexpected error occurred.", null));
        }
    }
}
