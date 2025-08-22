package com.AI4Java.BackendAI.AI.tools.Paid;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
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
import java.util.Locale;
import java.util.concurrent.atomic.AtomicLong;

/* ────────────────────────────────────────────────────────────────────────── */
@Service
public class SerpApiTools {
    /* — static config — */
    private static final Logger logger = LoggerFactory.getLogger(SerpApiTools.class);
    private static final String API_BASE = "https://serpapi.com/search?";
    private static final Duration TIMEOUT = Duration.ofSeconds(15);
    private static final int MAX_RETRY = 2;
    private static final Duration RETRY_DELAY = Duration.ofSeconds(1);
    private static final int MAX_MEM = 4 * 1024 * 1024;
    private static final int SHOW_LIMIT = 5;         // chat display
    private static final Charset UTF_8 = StandardCharsets.UTF_8;

    /* — DI — */
    @Value("${serpapi.api.key}")
    private String apiKey;

    private WebClient web;
    private final Gson gson = new Gson();
    private final AtomicLong callCnt = new AtomicLong(0);

    @PostConstruct
    public void init() {
        if (apiKey == null || apiKey.isBlank())
            throw new IllegalStateException("serpapi.api.key must be configured");

        web = WebClient.builder()
                .codecs(cfg -> cfg.defaultCodecs().maxInMemorySize(MAX_MEM))
                .defaultHeader("User-Agent", "SpringAI-SerpApiTool/1.0")
                .build();
        logger.info("SerpApiTools initialised");
    }

    @PreDestroy
    public void shutdown() {
        logger.info("SerpApiTools shutting down – total requests: {}", callCnt.get());
    }

    /* ─────────────────────── GOOGLE SEARCH ─────────────────────── */
    @Tool(
            name = "search_google",
            description = "Google Web search via SerpApi. Parameters: query (required), "
                    + "location (optional), num_results 1-20 (optional)"
    )
    public String search_google(
            @ToolParam(description = "Search query") String query,
            @ToolParam(description = "Location hint", required = false) String location,
            @ToolParam(description = "Results 1-20", required = false) String numResults) {
        return runSearch(query, location, numResults, "google", "organic_results",
                "🔍 Google Search Results", this::fmtOrganic);
    }

    /* ─────────────────────── GOOGLE NEWS ─────────────────────── */
    @Tool(
            name = "search_news_withSerpAPI",
            description = "Latest Google News via SerpApi. Parameters: query (required), location (optional)"
    )
    public String search_news(
            @ToolParam(description = "News query") String query,
            @ToolParam(description = "Location hint", required = false) String location) {
        return runSearch(query, location, null, "google_news", "news_results",
                "📰 Latest News", this::fmtNews);
    }

    /* ─────────────────────── GOOGLE IMAGES ─────────────────────── */
    @Tool(
            name = "search_images",
            description = "Google Images via SerpApi. Parameters: query (required), safe_search active/off"
    )
    public String search_images(
            @ToolParam(description = "Image query") String query,
            @ToolParam(description = "Safe search flag", required = false) String safe) {

        String safeParam = (safe != null && safe.equalsIgnoreCase("off")) ? "off" : "active";
        return runSearch(query, null, null, "google_images", "images_results",
                "🖼️ Image Results", (arr, i) -> fmtImage(arr, i, safeParam),
                "&safe=" + safeParam);
    }

    /* ─────────────────────── GOOGLE SHOPPING ─────────────────────── */
    @Tool(
            name = "search_shopping",
            description = "Google Shopping via SerpApi. Parameters: query (required), location (optional)"
    )
    public String search_shopping(
            @ToolParam(description = "Product query") String query,
            @ToolParam(description = "Location hint", required = false) String location) {
        return runSearch(query, location, null, "google_shopping", "shopping_results",
                "🛒 Shopping Results", this::fmtShopping);
    }

    /* ═══════════════  Core execution helper  ═══════════════ */
    private String runSearch(String q,
                             String location,
                             String numResults,
                             String engine,
                             String arrKey,
                             String header,
                             ResultFormatter formatter) {
        return runSearch(q, location, numResults, engine, arrKey, header, formatter, "");
    }

    private String runSearch(String q,
                             String location,
                             String numResults,
                             String engine,
                             String arrKey,
                             String header,
                             ResultFormatter formatter,
                             String extraParams) {

        long id = callCnt.incrementAndGet();
        logger.debug("SerpApi #{} – engine={} q='{}'", id, engine, q);

        /* validation */
        if (q == null || q.isBlank()) return "❌ Query cannot be empty.";
        int num = parseNum(numResults);

        /* url */
        StringBuilder url = new StringBuilder(API_BASE)
                .append("engine=").append(engine)
                .append("&q=").append(URLEncoder.encode(q.trim(), UTF_8))
                .append("&api_key=").append(apiKey)
                .append("&num=").append(num)
                .append(extraParams);

        if (location != null && !location.isBlank())
            url.append("&location=").append(URLEncoder.encode(location, UTF_8));

        try {
            String json = web.get()
                    .uri(url.toString())
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(TIMEOUT)
                    .retryWhen(Retry.fixedDelay(MAX_RETRY, RETRY_DELAY)
                            .filter(t -> t instanceof WebClientRequestException))
                    .block();

            JsonObject root = gson.fromJson(json, JsonObject.class);
            if (!root.has(arrKey)) {
                return header + " – No results.";
            }

            JsonArray arr = root.getAsJsonArray(arrKey);
            StringBuilder out = new StringBuilder(header)
                    .append(" for \"").append(q).append("\"**\n\n");

            for (int i = 0; i < Math.min(SHOW_LIMIT, arr.size()); i++) {
                out.append(formatter.format(arr, i));
            }
            return out.toString();

        } catch (WebClientResponseException e) {
            if (e.getStatusCode().value() == 429)
                return "❌ SerpApi rate-limit exceeded.";
            return "❌ SerpApi HTTP error " + e.getRawStatusCode();
        } catch (Exception e) {
            logger.error("SerpApi failure #{} – {}", id, e.getMessage());
            return "❌ Failed to retrieve results.";
        }
    }

    /* ═══════════════  Formatters  ═══════════════ */
    private String fmtOrganic(JsonArray arr, int i) {
        JsonObject o = arr.get(i).getAsJsonObject();
        return one(i, o, "title", "snippet", "link");
    }

    private String fmtNews(JsonArray arr, int i) {
        JsonObject o = arr.get(i).getAsJsonObject();
        StringBuilder sb = new StringBuilder("**").append(i + 1).append(". ")
                .append(get(o, "title")).append("**\n")
                .append("   📅 ").append(get(o, "date"))
                .append(" | 📰 ").append(get(o, "source")).append('\n');
        if (!get(o, "snippet").isEmpty()) sb.append("   ").append(get(o, "snippet")).append('\n');
        sb.append("   🔗 ").append(get(o, "link")).append("\n\n");
        return sb.toString();
    }

    private String fmtImage(JsonArray arr, int i, String safe) {
        JsonObject o = arr.get(i).getAsJsonObject();
        StringBuilder sb = new StringBuilder("**").append(i + 1).append(". ")
                .append(get(o, "title")).append("**\n")
                .append("   📷 Source: ").append(get(o, "source")).append('\n')
                .append("   🔗 ").append(get(o, "original")).append('\n');
        if ("off".equalsIgnoreCase(safe)) sb.append("   (Safe-search off)\n");
        sb.append('\n');
        return sb.toString();
    }

    private String fmtShopping(JsonArray arr, int i) {
        JsonObject o = arr.get(i).getAsJsonObject();
        StringBuilder sb = new StringBuilder("**").append(i + 1).append(". ")
                .append(get(o, "title")).append("**\n")
                .append("   💰 ").append(get(o, "price"))
                .append(" | 🏪 ").append(get(o, "source"));
        if (!get(o, "rating").isEmpty())
            sb.append(" | ⭐ ").append(get(o, "rating"));
        sb.append('\n')
                .append("   🔗 ").append(get(o, "link")).append("\n\n");
        return sb.toString();
    }

    private String one(int idx, JsonObject o, String titleK, String snipK, String linkK) {
        StringBuilder sb = new StringBuilder("**").append(idx + 1).append(". ")
                .append(get(o, titleK)).append("**\n");
        if (!get(o, snipK).isEmpty()) sb.append("   ").append(get(o, snipK)).append('\n');
        sb.append("   🔗 ").append(get(o, linkK)).append("\n\n");
        return sb.toString();
    }

    /* ═══════════════  Utility  ═══════════════ */
    private interface ResultFormatter {
        String format(JsonArray arr, int index);
    }

    private String get(JsonObject o, String k) {
        return (o.has(k) && !o.get(k).isJsonNull()) ? o.get(k).getAsString() : "";
    }

    private int parseNum(String s) {
        try {
            int n = Integer.parseInt(s);
            return Math.min(20, Math.max(1, n));
        } catch (Exception e) {
            return 10;
        }
    }
}


