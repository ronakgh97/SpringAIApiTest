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
public class NewsDataApiTools {

    /* ──────────────── Static configuration ──────────────── */
    private static final Logger logger = LoggerFactory.getLogger(NewsDataApiTools.class);

    private static final String API_BASE = "https://newsdata.io/api/1/news";
    private static final int MAX_RESULTS = 10;    // free-tier hard cap
    private static final int DISPLAY_LIMIT = 8;     // trim chat output
    private static final int DESC_LEN = 150;

    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static final int MAX_RETRIES = 2;
    private static final Duration RETRY_DELAY = Duration.ofSeconds(1);
    private static final int MAX_MEM_BYTES = 4 * 1024 * 1024; // 4 MB

    private static final Charset UTF_8 = StandardCharsets.UTF_8;

    /* ──────────────── Injected values ──────────────── */
    @Value("${newsData.api.key}")
    private String newsDataKey;

    private WebClient web;
    private final ObjectMapper mapper = new ObjectMapper();
    private final AtomicLong requestCnt = new AtomicLong(0);

    @PostConstruct
    public void init() {
        if (newsDataKey == null || newsDataKey.isBlank()) {
            throw new IllegalStateException("newsData.api.key must be configured");
        }
        web = WebClient.builder()
                .codecs(cfg -> cfg.defaultCodecs().maxInMemorySize(MAX_MEM_BYTES))
                .defaultHeader("User-Agent", "SpringAI-NewsDataTool/1.0")
                .defaultHeader("X-ACCESS-KEY", newsDataKey)
                .build();
        logger.info("NewsDataApiTools initialised");
    }

    @PreDestroy
    public void shutdown() {
        logger.info("NewsDataApiTools shutting down – total requests handled: {}", requestCnt.get());
    }

    /* ─────────────────────  SEARCH  ───────────────────── */
    @Tool(
            name = "newsData_search",
            description = "Search global news on Newsdata.io. "
                    + "Parameters: query (keywords) with optional country (ISO-3166-1 alpha-2) "
                    + "and language (ISO-639-1)."
    )
    public String newsData_search(
            @ToolParam(description = "Search keywords") String query,
            @ToolParam(description = "Country code (us, in, uk …)", required = false) String country,
            @ToolParam(description = "Language code (en, es …)", required = false) String language) {

        long id = requestCnt.incrementAndGet();
        logger.debug("NewsData search #{} – q='{}' country='{}' lang='{}'", id, query, country, language);

        Validation v = validateInput(query, country, language);
        if (!v.valid) return "❌ " + v.message;

        try {
            /* ── Build URL ── */
            StringBuilder url = new StringBuilder(API_BASE)
                    .append("?q=").append(URLEncoder.encode(v.q, UTF_8))
                    .append("&page=1")              // first page only (free tier)
                    .append("&page_size=").append(MAX_RESULTS);

            if (v.lang != null) url.append("&language=").append(v.lang);
            if (v.country != null) url.append("&country=").append(v.country);

            String json = executeRequest(url.toString(), id);
            List<Article> articles = parseArticles(json);

            return formatArticles(v.q, articles);

        } catch (NewsDataException e) {
            logger.error("NewsData search #{} – {}", id, e.getMessage());
            return "❌ " + e.getMessage();
        }
    }

    /* ──────────────── Internal helpers ──────────────── */

    private String executeRequest(String url, long id) throws NewsDataException {
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
                throw new NewsDataException("Invalid Newsdata API key.");
            }
            if (e.getStatusCode().value() == 429) {
                throw new NewsDataException("Newsdata rate-limit exceeded.");
            }
            throw new NewsDataException("Newsdata service error: HTTP " + e.getStatusCode().value());
        } catch (Exception e) {
            throw new NewsDataException("Network error while contacting Newsdata");
        }
    }

    private List<Article> parseArticles(String json) throws NewsDataException {
        try {
            JsonNode root = mapper.readTree(json);

            if (!"success".equalsIgnoreCase(root.path("status").asText())) {
                String msg = root.path("message").asText("Unknown API error");
                throw new NewsDataException("Newsdata API error: " + msg);
            }

            List<Article> list = new ArrayList<>();
            for (JsonNode a : root.path("results")) {
                if (list.size() >= DISPLAY_LIMIT) break;

                String title = a.path("title").asText("");
                String url = a.path("link").asText("");
                if (title.isEmpty() || url.isEmpty()) continue;

                String desc = a.path("description").asText("");
                String src = a.path("source_id").asText("");
                String date = a.path("pubDate").asText("").replace('T', ' ').replace('Z', ' ');

                list.add(new Article(title, url, desc, src, date));
            }
            return list;

        } catch (NewsDataException e) {
            throw e;
        } catch (Exception e) {
            throw new NewsDataException("Failed to parse Newsdata response");
        }
    }

    private String formatArticles(String query, List<Article> list) {
        if (list.isEmpty()) {
            return "📰 **Newsdata.io** – No articles found for '" + query + "'.";
        }

        StringBuilder sb = new StringBuilder("📰 **Newsdata.io Results: \"")
                .append(query).append("\"**\n\n");

        int idx = 1;
        for (Article a : list) {
            sb.append("**").append(idx++).append(". ").append(a.title).append("**\n");
            sb.append("📰 *").append(a.source).append("*");
            if (!a.date.isEmpty()) sb.append(" | 📅 ").append(a.date);
            sb.append("\n");
            if (!a.desc.isEmpty() && !"null".equalsIgnoreCase(a.desc)) {
                sb.append("📝 ").append(truncate(a.desc, DESC_LEN)).append("\n");
            }
            sb.append("🔗 ").append(a.url).append("\n\n");
        }
        return sb.toString();
    }

    /* ──────────────── Validation ──────────────── */

    private Validation validateInput(String query, String country, String lang) {
        if (query == null || query.isBlank()) {
            return Validation.err("Search keywords cannot be empty.");
        }
        String q = query.trim();

        String l = null;
        if (lang != null && !lang.isBlank()) {
            l = lang.trim().toLowerCase(Locale.ROOT);
            if (l.length() != 2 || !l.chars().allMatch(Character::isLetter)) {
                return Validation.err("Language must be a 2-letter ISO-639-1 code.");
            }
        }

        String c = null;
        if (country != null && !country.isBlank()) {
            c = country.trim().toLowerCase(Locale.ROOT);
            if (c.length() != 2 || !c.chars().allMatch(Character::isLetter)) {
                return Validation.err("Country must be a 2-letter ISO-3166-1 code.");
            }
        }
        return new Validation(true, null, q, c, l);
    }

    private static String truncate(String txt, int len) {
        return txt.length() <= len ? txt : txt.substring(0, len) + "…";
    }

    /* ──────────────── DTO / record types ──────────────── */

    private record Article(String title, String url, String desc, String source, String date) {
    }

    private record Validation(boolean valid, String message, String q, String country, String lang) {
        static Validation err(String m) {
            return new Validation(false, m, null, null, null);
        }
    }

    /* ──────────────── Custom exception ──────────────── */
    private static class NewsDataException extends Exception {
        NewsDataException(String msg) {
            super(msg);
        }
    }
}


