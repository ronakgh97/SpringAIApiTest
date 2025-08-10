package com.AI4Java.BackendAI.AI.tools;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Component
public class EmailTools {

    private static final Logger log = LoggerFactory.getLogger(EmailTools.class);

    @Autowired
    private JavaMailSender mailSender;
//TODO: set recipient_mail to current user's gmail
        @Tool(name = "send_Mail", description = "Sends a message to gmail, Parameters: subject, body and recipient_mail.")
    public String send_Mail(String subject, String body, String recipient_mail) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(recipient_mail);
            message.setSubject(subject);
            message.setText(body);
            mailSender.send(message);
            return "Email sent successfully to " + recipient_mail;
        } catch (Exception e) {
            log.error("Failed to send email for user: {}", e.getMessage());
            return "Failed to send email: " + e.getMessage();
        }
    }
}
