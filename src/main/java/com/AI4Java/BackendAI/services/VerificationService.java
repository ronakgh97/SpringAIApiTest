package com.AI4Java.BackendAI.services;

import com.AI4Java.BackendAI.entries.UserEntries;
import com.AI4Java.BackendAI.exceptions.UserException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class VerificationService {

    private static final Logger log = LoggerFactory.getLogger(VerificationService.class);

    @Autowired
    private UserServices userServices;

    @Autowired
    private JavaMailSender mailSender;

    public void sendVerificationCode(UserEntries user, String gmail) {
        log.info("Sending verification code to: {}", gmail);
        user = userServices.findByMail(gmail);
        String verificationCode = String.valueOf((int) (Math.random() * 900000) + 100000);
        user.setVerificationCode(verificationCode);
        user.setVerificationCodeExpires(LocalDateTime.now().plusMinutes(15));
        userServices.saveUser(user);
        sendEmail(gmail, "Verification Code", "Your verification code is: " + verificationCode);
        log.info("Trying to send Verification code to: {}", gmail);
    }

    public boolean verifyCode(UserEntries user, String gmail, String code) {
        log.info("Verifying code for email: {}", gmail);
        user = userServices.findByMail(gmail);
        if (user == null || user.getVerificationCode() == null || user.getVerificationCodeExpires() == null) {
            log.warn("Verification failed for email: {}. User not found or no code generated.", gmail);
            return false;
        }

        if (user.getVerificationCodeExpires().isBefore(LocalDateTime.now())) {
            log.warn("Verification failed for email: {}. Code has expired.", gmail);
            user.setVerificationCode(null);
            user.setVerificationCodeExpires(null);
            userServices.saveUser(user);
            return false;
        }

        if (user.getVerificationCode().equals(code)) {
            log.info("Verification successful for email: {}", gmail);
            user.setVerified(true);
            user.setVerificationCode(null);
            user.setVerificationCodeExpires(null);
            userServices.saveUser(user);
            return true;
        }

        log.warn("Verification failed for email: {}. Invalid code.", gmail);
        return false;
    }

    private void sendEmail(String to, String subject, String body) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(to);
            message.setSubject(subject);
            message.setText(body);
            mailSender.send(message);
            log.info("Verification Code send successfully");
        } catch (Exception e) {
            log.error("Failed to send email to: {}", to, e);
        }
    }
}
