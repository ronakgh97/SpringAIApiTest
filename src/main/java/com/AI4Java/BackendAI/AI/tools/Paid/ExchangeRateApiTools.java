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

import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class ExchangeRateApiTools {

    private static final Logger logger = LoggerFactory.getLogger(ExchangeRateApiTools.class);

    /* ─────────── Configuration ─────────── */
    private static final String API_BASE = "https://v6.exchangerate-api.com/v6";
    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static final int MAX_RETRIES = 2;
    private static final Duration RETRY_DELAY = Duration.ofSeconds(1);
    private static final int MAX_MEM = 2 * 1024 * 1024;   // 2 MB
    private static final int ISO_LEN = 3;                 // exactly 3 letters

    /* ─────────── DI / runtime values ─────────── */
    @Value("${exchange.api.key}")
    private String apiKey;

    private WebClient web;
    private final ObjectMapper mapper = new ObjectMapper();
    private final AtomicLong callCounter = new AtomicLong(0);

    @PostConstruct
    public void init() {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("exchange.api.key must be configured");
        }
        web = WebClient.builder()
                .codecs(cfg -> cfg.defaultCodecs().maxInMemorySize(MAX_MEM))
                .defaultHeader("User-Agent", "SpringAI-ExchangeTool/1.0")
                .build();
        logger.info("ExchangeRateApiTools initialised");
    }

    @PreDestroy
    public void shutdown() {
        logger.info("ExchangeRateApiTools shutting down – total requests handled: {}", callCounter.get());
    }

    /* ─────────── Public Tool ─────────── */
    @Tool(
            name = "get_exchange_rate",
            description = "Fetch the latest FX rate or convert an amount. "
                    + "Parameters: from (base ISO-4217 code), to (target ISO code), amount (optional).")
    public String get_exchange_rate(
            @ToolParam(description = "Base currency ISO code, e.g. USD") String from,
            @ToolParam(description = "Target currency ISO code, e.g. EUR") String to,
            @ToolParam(description = "Amount to convert (optional)", required = false) Double amount) {

        long id = callCounter.incrementAndGet();
        logger.debug("FX request #{} – {} → {}  amount={}", id, from, to, amount);

        /* ── Validate input ── */
        ValidationResult val = validateIsoCodes(from, to);
        if (!val.valid) {
            logger.warn("Invalid FX request #{} – {}", id, val.message);
            return "❌ " + val.message;
        }

        /* ── Build URL ── */
        String url = String.format("%s/%s/pair/%s/%s",
                API_BASE,
                URLEncoder.encode(apiKey, StandardCharsets.UTF_8),
                val.from,
                val.to);

        /* ── Call API ── */
        try {
            String json = web.get()
                    .uri(url)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(TIMEOUT)
                    .retryWhen(Retry.fixedDelay(MAX_RETRIES, RETRY_DELAY)
                            .filter(t -> t instanceof WebClientRequestException))
                    .block();

            if (json == null || json.isBlank()) {
                throw new FxApiException("Empty response from FX API");
            }

            /* ── Parse ── */
            JsonNode root = mapper.readTree(json);
            if (!"success".equalsIgnoreCase(root.path("result").asText())) {
                String err = root.path("error-type").asText("unknown");
                throw new FxApiException("API error: " + err);
            }

            BigDecimal rate = root.path("conversion_rate").decimalValue();
            BigDecimal converted = (amount != null)
                    ? BigDecimal.valueOf(amount).multiply(rate)
                    : null;

            /* ── Format ── */
            if (converted != null) {
                return String.format("💱 %.2f %s = %.2f %s  (rate %.6f)",
                        amount, val.from, converted, val.to, rate);
            }
            return String.format("💱 1 %s = %.6f %s", val.from, rate, val.to);

        } catch (FxApiException e) {
            logger.error("FX request #{} – {}", id, e.getMessage());
            return "❌ " + e.getMessage();
        } catch (WebClientResponseException e) {
            logger.error("FX request #{} – HTTP {}", id, e.getStatusCode());
            return "❌ FX service responded with HTTP " + e.getRawStatusCode();
        } catch (Exception e) {
            logger.error("FX request #{} – unexpected error", id, e);
            return "❌ Failed to fetch exchange rate – please try again later.";
        }
    }

    /* ─────────── Helpers ─────────── */

    private ValidationResult validateIsoCodes(String from, String to) {
        if (from == null || to == null) return ValidationResult.err("Currency codes cannot be null.");
        String f = from.trim().toUpperCase(Locale.ROOT);
        String t = to.trim().toUpperCase(Locale.ROOT);
        if (f.length() != ISO_LEN || t.length() != ISO_LEN)
            return ValidationResult.err("Currency codes must be 3-letter ISO-4217 codes.");
        if (!f.chars().allMatch(Character::isLetter) || !t.chars().allMatch(Character::isLetter))
            return ValidationResult.err("Currency codes must contain letters only.");
        return new ValidationResult(true, null, f, t);
    }

    private record ValidationResult(boolean valid, String message, String from, String to) {
        static ValidationResult err(String msg) {
            return new ValidationResult(false, msg, null, null);
        }
    }

    /* ─────────── Custom Exceptions ─────────── */
    private static class FxApiException extends Exception {
        FxApiException(String msg) {
            super(msg);
        }
    }
}


