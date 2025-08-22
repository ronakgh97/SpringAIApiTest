package com.AI4Java.BackendAI.AI.tools.Free;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import jakarta.mail.internet.MimeMessage;
import org.springframework.mail.javamail.MimeMessageHelper;

@Service
public class ReportTools {

    private static final Logger logger = LoggerFactory.getLogger(ReportTools.class);

    @Autowired
    private JavaMailSender mailSender;

    private static final String DEV_TEAM_MAIL = "ronakgh97@gmail.com";

    @Tool(
            name = "report_errors",
            description = "Report errors to the dev team by sending mail (recipient address is defaulted). Parameters: subject (tool names or error context) and body (error message/details)."
    )
    public String report_errors(
            @ToolParam(description = "Email Subject") String subject,
            @ToolParam(description = "Email Body") String body) {
        try {
            logger.info("Sending error report to dev team: {}", DEV_TEAM_MAIL);

            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");

            String htmlBody = getHtmlMailBody(subject, body);

            helper.setTo(DEV_TEAM_MAIL);
            helper.setSubject(subject);
            helper.setText(htmlBody, true);

            mailSender.send(mimeMessage);

            return "✅ Error report sent successfully to " + DEV_TEAM_MAIL;
        } catch (Exception e) {
            logger.error("Failed to send error report: {}", e.getMessage(), e);
            return "❌ Failed to send error report: " + e.getMessage();
        }
    }

    private String getHtmlMailBody(String subject, String body) {
        String html = """
            <!DOCTYPE html>
            <html>
              <head>
                <meta charset="UTF-8">
                <style>
                  body { font-family: Arial,sans-serif; background-color:#f5f5f5; margin:0; padding:20px; }
                  .container { max-width:600px; margin:0 auto; background:white; padding:20px; border-radius:8px; box-shadow:0 2px 10px rgba(0,0,0,0.1);}
                  h1 { color:#333; border-bottom:2px solid #007bff; padding-bottom:10px;}
                  p { color:#555; line-height:1.6; white-space:pre-wrap;}
                  .footer { margin-top:30px; padding-top:20px; border-top:1px solid #eee; font-size:12px; color:#888;text-align:center;}
                </style>
              </head>
              <body>
                <div class="container">
                  <h1>&#128233; %s</h1>
                  <p>%s</p>
                  <div class="footer">
                    <p>This is an automated Mail Service</p>
                  </div>
                </div>
              </body>
            </html>
            """.formatted(escapeHtml(subject), escapeHtml(body));
        return html;
    }

    private String escapeHtml(String input) {
        if (input == null) return "";
        return input.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}

