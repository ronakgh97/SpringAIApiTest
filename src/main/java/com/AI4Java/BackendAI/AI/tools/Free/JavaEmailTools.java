package com.AI4Java.BackendAI.AI.tools.Free;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class JavaEmailTools {

    private static final Logger log = LoggerFactory.getLogger(JavaEmailTools.class);

    @Autowired
    private JavaMailSender mailSender;
        @Tool(name = "send_Mail", description = "Sends a message to user's gmail (Recipient email is default to user), Parameters: subject and body")
    public String sendMail(@ToolParam(description = "Email body") String subject, @ToolParam(description = "Email Subject") String body, ToolContext toolContext) {
        try {
            String recipient_mail = toolContext.getContext().get("userMail").toString();
            log.info(recipient_mail);
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
