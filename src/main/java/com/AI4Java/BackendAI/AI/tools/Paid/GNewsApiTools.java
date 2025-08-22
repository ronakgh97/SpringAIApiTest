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
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class GNewsApiTools {

    /* ─────────────── Static configuration ─────────────── */
    private static final Logger logger = LoggerFactory.getLogger(GNewsApiTools.class);

    private static final String API_BASE_SEARCH = "https://gnews.io/api/v4/search";
    private static final String API_BASE_HEADLINE = "https://gnews.io/api/v4/top-headlines";

    private static final int MAX_RESULTS = 10;  // API free-tier hard cap
    private static final int MAX_DISPLAY = 8;   // trim output for chat
    private static final int DESC_LEN = 150;

    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static final int MAX_RETRIES = 2;
    private static final Duration RETRY_DELAY = Duration.ofSeconds(1);
    private static final int MAX_MEM = 4 * 1024 * 1024; // 4 MB

    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withLocale(Locale.US);

    /* ─────────────── Injected values ─────────────── */
    @Value("${gnews.api.key}")
    private String gnewsKey;

    private WebClient web;
    private final ObjectMapper mapper = new ObjectMapper();
    private final AtomicLong requestCnt = new AtomicLong(0);

    @PostConstruct
    public void init() {
        if (gnewsKey == null || gnewsKey.isBlank()) {
            throw new IllegalStateException("gnews.api.key must be configured");
        }
        web = WebClient.builder()
                .codecs(cfg -> cfg.defaultCodecs().maxInMemorySize(MAX_MEM))
                .defaultHeader("User-Agent", "SpringAI-GNewsTool/1.0")
                .defaultHeader("X-Api-Key", gnewsKey)
                .build();
        logger.info("GNewsApiTools initialised");
    }

    @PreDestroy
    public void shutdown() {
        logger.info("GNewsApiTools shutting down – total requests handled: {}", requestCnt.get());
    }

    /* ─────────────────────  SEARCH  ───────────────────── */
    @Tool(
            name = "gnews_search",
            description = "Search premium, verified news sources via GNews.io. "
                    + "Parameters: query (keywords) and optional lang (ISO-639-1).")
    public String gnews_search(
            @ToolParam(description = "Search keywords") String query,
            @ToolParam(description = "Language code (en, es …)", required = false) String lang) {

        long id = requestCnt.incrementAndGet();
        logger.debug("GNews search #{} – query='{}' lang='{}'", id, query, lang);

        ValidationResult v = validateQuery(query, lang);
        if (!v.valid) return "❌ " + v.message;

        try {
            String url = API_BASE_SEARCH + "?q=" + URLEncoder.encode(v.query, UTF_8)
                    + "&lang=" + v.lang + "&max=" + MAX_RESULTS;

            String json = executeRequest(url, id, "search");
            List<Article> articles = parseArticles(json);

            return formatArticles("GNews.io Results", v.query, articles);

        } catch (GNewsException e) {
            logger.error("GNews search #{} – {}", id, e.getMessage());
            return "❌ " + e.getMessage();
        }
    }

    /* ────────────────────  HEADLINES  ─────────────────── */
    @Tool(
            name = "gnews_headlines",
            description = "Get latest top headlines by category (general, world, business, tech, sports …). "
                    + "Optional lang parameter.")
    public String gnews_headlines(
            @ToolParam(description = "Category", required = false) String topic,
            @ToolParam(description = "Language code", required = false) String lang) {

        long id = requestCnt.incrementAndGet();
        logger.debug("GNews headlines #{} – topic='{}' lang='{}'", id, topic, lang);

        ValidationResult v = validateQuery(topic == null ? "general" : topic, lang);
        if (!v.valid) return "❌ " + v.message;

        try {
            String url = API_BASE_HEADLINE + "?category=" + v.query.toLowerCase(Locale.ROOT)
                    + "&lang=" + v.lang + "&max=" + MAX_RESULTS;

            String json = executeRequest(url, id, "headlines");
            List<Article> articles = parseArticles(json);

            return formatArticles("GNews.io – Top Headlines", v.query + " category", articles);

        } catch (GNewsException e) {
            logger.error("GNews headlines #{} – {}", id, e.getMessage());
            return "❌ " + e.getMessage();
        }
    }

    /* ─────────────── Request / Parsing helpers ─────────────── */

    private String executeRequest(String url, long id, String tag) throws GNewsException {
        try {
            return web.get()
                    .uri(url)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(TIMEOUT)
                    .retryWhen(Retry.fixedDelay(MAX_RETRIES, RETRY_DELAY)
                            .filter(t -> t instanceof WebClientRequestException))
                    .block();
        } catch (WebClientResponseException e) {
            if (e.getStatusCode().value() == 401) {
                throw new GNewsException("Invalid GNews API key.");
            }
            if (e.getStatusCode().value() == 429) {
                throw new GNewsException("GNews rate-limit exceeded.");
            }
            throw new GNewsException("GNews service error: HTTP " + e.getStatusCode().value());
        } catch (Exception e) {
            throw new GNewsException("Network error while contacting GNews (" + tag + ")");
        }
    }

    private List<Article> parseArticles(String json) throws GNewsException {
        try {
            JsonNode root = mapper.readTree(json);
            JsonNode arr = root.path("articles");

            List<Article> list = new ArrayList<>();
            for (JsonNode a : arr) {
                if (list.size() >= MAX_DISPLAY) break;
                String t = a.path("title").asText("");
                String u = a.path("url").asText("");
                String d = a.path("description").asText("");
                String s = a.path("source").path("name").asText("");
                String ts = a.path("publishedAt").asText("");
                if (!t.isEmpty() && !u.isEmpty()) list.add(new Article(t, u, d, s, ts));
            }
            return list;

        } catch (Exception e) {
            throw new GNewsException("Failed to parse GNews response");
        }
    }

    private String formatArticles(String header, String query, List<Article> list) {
        if (list.isEmpty()) {
            return "📰 **GNews.io** – No articles found for '" + query + "'.";
        }

        StringBuilder sb = new StringBuilder("📰 **")
                .append(header).append(": \"").append(query).append("\"**\n\n");

        int idx = 1;
        for (Article a : list) {
            sb.append("**").append(idx++).append(". ").append(a.title).append("**\n");
            sb.append("📰 *").append(a.source).append("*");
            if (!a.published.isEmpty()) {
                sb.append(" | 📅 ").append(a.published.replace('T', ' ').replace('Z', ' '));
            }
            sb.append("\n");
            if (!a.desc.isEmpty() && !"null".equalsIgnoreCase(a.desc)) {
                sb.append("📝 ").append(truncate(a.desc, DESC_LEN)).append("\n");
            }
            sb.append("🔗 ").append(a.url).append("\n\n");
        }
        return sb.toString();
    }

    /* ─────────────── Validation helpers ─────────────── */

    private ValidationResult validateQuery(String query, String lang) {
        if (query == null || query.isBlank()) {
            return ValidationResult.err("Query/topic cannot be empty.");
        }
        String q = query.trim();
        String l = (lang == null || lang.isBlank()) ? "en" : lang.trim().toLowerCase(Locale.ROOT);
        if (l.length() != 2 || !l.chars().allMatch(Character::isLetter)) {
            return ValidationResult.err("Language code must be a two-letter ISO-639-1 code.");
        }
        return new ValidationResult(true, null, q, l);
    }

    private static String truncate(String text, int len) {
        if (text.length() <= len) return text;
        return text.substring(0, len) + "…";
    }

    /* ─────────────── Records / DTOs ─────────────── */

    private record Article(String title, String url, String desc, String source, String published) {
    }

    private record ValidationResult(boolean valid, String message, String query, String lang) {
        static ValidationResult err(String m) {
            return new ValidationResult(false, m, null, null);
        }
    }

    /* ─────────────── Custom exception ─────────────── */
    private static class GNewsException extends Exception {
        GNewsException(String msg) {
            super(msg);
        }
    }

    /* ─────────────── Charset shortcut ─────────────── */
    private static final Charset UTF_8 = StandardCharsets.UTF_8;
}


