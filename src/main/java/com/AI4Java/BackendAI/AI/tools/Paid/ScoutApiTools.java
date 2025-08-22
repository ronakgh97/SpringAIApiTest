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

import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class ScoutApiTools {

    /* ───────── Static configuration ───────── */
    private static final Logger logger = LoggerFactory.getLogger(ScoutApiTools.class);

    private static final String API_HOST = "scout-amazon-data.p.rapidapi.com";
    private static final String ENDPOINT_SEARCH = "https://" + API_HOST + "/Amazon-Search-Data";

    private static final int MAX_LIMIT = 50;     // API hard maximum
    private static final int DEF_LIMIT = 10;
    private static final int DISPLAY_LIMIT = 15;     // chat safety
    private static final int DESC_LEN = 160;

    private static final Duration TIMEOUT = Duration.ofSeconds(15);
    private static final int MAX_RETRIES = 2;
    private static final Duration RETRY_DELAY = Duration.ofSeconds(1);
    private static final int MAX_MEM = 4 * 1024 * 1024; // 4 MB

    private static final Charset UTF_8 = StandardCharsets.UTF_8;

    /* ───────── Injected values ───────── */
    @Value("${scout.api.key}")          // RapidAPI key
    private String apiKey;

    private WebClient web;
    private final ObjectMapper mapper = new ObjectMapper();
    private final AtomicLong requestCnt = new AtomicLong(0);

    @PostConstruct
    public void init() {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("scout.api.key must be configured");
        }
        web = WebClient.builder()
                .codecs(cfg -> cfg.defaultCodecs().maxInMemorySize(MAX_MEM))
                .defaultHeader("User-Agent", "SpringAI-ScoutTool/1.0")
                .defaultHeader("X-RapidAPI-Key", apiKey)
                .defaultHeader("X-RapidAPI-Host", API_HOST)
                .build();
        logger.info("ScoutApiTools initialised");
    }

    @PreDestroy
    public void shutdown() {
        logger.info("ScoutApiTools shutting down – total requests: {}", requestCnt.get());
    }

    /* ─────────── Amazon product search ─────────── */
    @Tool(
            name = "scout_amazon_search",
            description = "Real-time Amazon product search via Scout (RapidAPI). "
                    + "Returns title, price, rating, review count, availability and link."
    )
    public String scout_amazon_search(
            @ToolParam(description = "Search keywords") String query,
            @ToolParam(description = "Amazon region code (US, UK …)", required = false) String region,
            @ToolParam(description = "Sort order (RELEVANCE, PRICE_LOW_HIGH, PRICE_HIGH_LOW, RATING)",
                    required = false) String sort,
            @ToolParam(description = "Max results (1-50, default 10)", required = false) Integer limit) {

        long id = requestCnt.incrementAndGet();
        logger.debug("Scout search #{} – q='{}' region='{}' sort='{}' limit={}",
                id, query, region, sort, limit);

        /* ── Validate input ── */
        Validation v = validateInput(query, region, sort, limit);
        if (!v.valid) return "❌ " + v.message;

        /* ── Build URL ── */
        String url = ENDPOINT_SEARCH
                + "?query=" + URLEncoder.encode(v.q, UTF_8)
                + "&region=" + v.region
                + "&sort_by=" + v.sort
                + "&page=1";

        try {
            String json = web.get()
                    .uri(url)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(TIMEOUT)
                    .retryWhen(Retry.fixedDelay(MAX_RETRIES, RETRY_DELAY)
                            .filter(t -> t instanceof WebClientRequestException))
                    .block();

            List<Product> list = parseProducts(json, v.max);
            return formatResult(v.q, list);

        } catch (WebClientResponseException e) {
            if (e.getStatusCode().value() == 429) {
                return "❌ Scout API rate-limit exceeded.";
            }
            logger.error("Scout API error #{} – HTTP {}", id, e.getStatusCode().value());
            return "❌ Scout API error: HTTP " + e.getStatusCode().value();
        } catch (Exception e) {
            logger.error("Scout network/error #{} – {}", id, e.getMessage());
            return "❌ Scout search failed for \"" + query + "\".";
        }
    }

    /* ─────────── Parsing ─────────── */
    private List<Product> parseProducts(String json, int max) throws Exception {
        JsonNode root = mapper.readTree(json).path("products");
        List<Product> out = new ArrayList<>();
        for (JsonNode p : root) {
            if (out.size() >= max || out.size() >= DISPLAY_LIMIT) break;
            String title = p.path("title").asText("");
            String url = p.path("url").asText("");
            if (title.isEmpty() || url.isEmpty()) continue;

            String price = p.path("price").asText("N/A");
            String rating = p.path("rating").asText("");
            String reviews = p.path("reviews").asText("");
            String avail = p.path("availability").asText("");

            out.add(new Product(title, url, price, rating, reviews, avail));
        }
        return out;
    }

    /* ─────────── Formatting ─────────── */
    private String formatResult(String query, List<Product> list) {
        if (list.isEmpty()) return "🛒 No Amazon results for \"" + query + "\".";

        StringBuilder sb = new StringBuilder("🛒 **Amazon results for \"")
                .append(query).append("\"**\n\n");

        int n = 1;
        for (Product p : list) {
            sb.append("**").append(n++).append(". ").append(p.title).append("**\n")
                    .append("💲 ").append(p.price);

            if (!p.rating.isEmpty()) sb.append(" | ⭐ ").append(p.rating);
            if (!p.reviews.isEmpty()) sb.append(" (").append(p.reviews).append(" reviews)");
            sb.append('\n');

            if (!p.availability.isEmpty()) sb.append("📦 ").append(p.availability).append('\n');
            sb.append("🔗 ").append(p.url).append("\n\n");
        }
        return sb.toString();
    }

    /* ─────────── Validation ─────────── */
    private Validation validateInput(String q, String region, String sort, Integer limit) {
        if (q == null || q.isBlank()) {
            return Validation.err("Search keywords cannot be empty.");
        }
        String reg = (region == null || region.isBlank()) ? "US"
                : region.trim().toUpperCase(Locale.ROOT);
        if (reg.length() != 2 || !reg.chars().allMatch(Character::isLetter)) {
            return Validation.err("Region must be a 2-letter country code.");
        }

        String srt = (sort == null || sort.isBlank()) ? "RELEVANCE"
                : sort.trim().toUpperCase(Locale.ROOT);
        switch (srt) {
            case "RELEVANCE", "PRICE_LOW_HIGH", "PRICE_HIGH_LOW", "RATING" -> {
            }
            default -> {
                return Validation.err("Unsupported sort: " + srt);
            }
        }

        int lim = (limit == null) ? DEF_LIMIT
                : Math.min(Math.max(1, limit), MAX_LIMIT);

        return new Validation(true, null, q.trim(), reg, srt, lim);
    }

    /* ─────────── Helper types ─────────── */
    private record Product(String title, String url, String price,
                           String rating, String reviews, String availability) {
    }

    private record Validation(boolean valid, String message,
                              String q, String region, String sort, int max) {
        static Validation err(String m) {
            return new Validation(false, m, null, null, null, 0);
        }
    }
}


