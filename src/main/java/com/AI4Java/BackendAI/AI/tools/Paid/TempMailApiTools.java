package com.AI4Java.BackendAI.AI.tools.Paid;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.util.retry.Retry;

import java.security.MessageDigest;
import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class TempMailApiTools {

    /* ────── Static configuration ────── */
    private static final Logger logger  = LoggerFactory.getLogger(TempMailApiTools.class);
    private static final String HOST    = "privatix-temp-mail-v1.p.rapidapi.com";
    private static final String BASEURL = "https://" + HOST;
    private static final int    MAX_MEM = 2 * 1024 * 1024;   // 2 MB
    private static final Duration TIMEOUT     = Duration.ofSeconds(10);
    private static final int    MAX_RETRY     = 2;
    private static final Duration RETRY_DELAY = Duration.ofSeconds(1);

    @Value("${tempmail.api.key}")
    private String apiKey;

    private WebClient          web;
    private final ObjectMapper mapper = new ObjectMapper();
    private final AtomicLong   counter= new AtomicLong(0);

    @PostConstruct
    public void init() {
        if (apiKey == null || apiKey.isBlank())
            throw new IllegalStateException("tempmail.api.key must be configured");
        web = WebClient.builder()
                .codecs(cfg -> cfg.defaultCodecs().maxInMemorySize(MAX_MEM))
                .defaultHeader("User-Agent", "SpringAI-TempMailTool/1.0")
                .defaultHeader("X-RapidAPI-Key", apiKey)
                .defaultHeader("X-RapidAPI-Host", HOST)
                .build();
        logger.info("TempMailApiTools initialised");
    }

    @PreDestroy
    public void shutdown() {
        logger.info("TempMailApiTools shut down – total requests: {}", counter.get());
    }

    @Tool(
            name = "generate_temp_email",
            description = "Get a fresh, disposable email address (valid ~1 hour)."
    )
    public String generate_temp_email() {
        long id = counter.incrementAndGet();
        logger.debug("TempMail generate #{}", id);

        try {
            String domainsJson = callApi("/request/domains/format/json", id);
            JsonNode domains   = mapper.readTree(domainsJson);
            if (!domains.isArray() || domains.isEmpty())
                return "❌ Temp-Mail: no domains available.";

            String domain = domains.get(0).asText();
            String prefix = "temp" + System.nanoTime()%1_000_000;
            String email  = prefix + "@" + domain;
            return "📧 " + email;

        } catch (Exception e) {
            logger.error("TempMail domain fetch failed #{}", id, e);
            return "❌ Could not generate temp email.";
        }
    }

    @Tool(
            name = "get_temp_inbox",
            description = "Check received messages for a disposable Temp-Mail email."
    )
    public String get_temp_inbox(
            @ToolParam(description = "Disposable email address") String email) {
        long id = counter.incrementAndGet();
        logger.debug("TempMail inbox fetch #{} for {}", id, email);

        try {
            if (email == null || email.isBlank() || !email.contains("@"))
                return "❌ Invalid email address.";

            String md5 = md5Hex(email.trim().toLowerCase(Locale.ROOT));
            String json = callApi("/request/mail/id/" + md5 + "/format/json", id);

            JsonNode arr = mapper.readTree(json);
            if (!arr.isArray() || arr.isEmpty())
                return "📭 Inbox empty (yet).";

            StringBuilder out = new StringBuilder("📬 **Temp-Mail inbox for ")
                    .append(email).append("**\n\n");
            int i = 1;
            for (JsonNode m : arr) {
                out.append(i++).append(". **")
                        .append(m.path("mail_subject").asText("(no subject)")).append("**\n")
                        .append("From: ").append(m.path("mail_from").asText("unknown")).append('\n')
                        .append("Date: ").append(m.path("mail_date").asText("")).append('\n')
                        .append("Preview: ").append(truncate(m.path("mail_text_only").asText(""), 120)).append("\n\n");
            }
            return out.toString();

        } catch (Exception e) {
            logger.error("TempMail inbox fetch failed #{}", id, e);
            return "❌ Failed to read inbox.";
        }
    }

    /* ────── Internal API request with retry+timeout ────── */
    private String callApi(String path, long id) throws Exception {
        try {
            return web.get()
                    .uri(BASEURL + path)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(TIMEOUT)
                    .retryWhen(Retry.fixedDelay(MAX_RETRY, RETRY_DELAY)
                            .filter(t -> t instanceof WebClientRequestException))
                    .block();
        } catch (WebClientResponseException e) {
            if (e.getStatusCode().value() == 429)
                return "❌ TempMail rate-limit exceeded.";
            throw e;
        }
    }

    private static String md5Hex(String s) throws Exception {
        byte[] hash = MessageDigest.getInstance("MD5").digest(s.getBytes());
        StringBuilder sb = new StringBuilder();
        for (byte b : hash) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    private static String truncate(String t, int max) {
        return (t == null || t.length() <= max) ? t : t.substring(0, max) + "…";
    }
}


