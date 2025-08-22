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
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static java.nio.charset.StandardCharsets.UTF_8;

@Service
public class BraveSearchApiTools {

    private static final Logger logger = LoggerFactory.getLogger(BraveSearchApiTools.class);

    // API Configuration
    private static final String BRAVE_WEB_SEARCH_URL = "https://api.search.brave.com/res/v1/web/search";
    private static final String BRAVE_NEWS_SEARCH_URL = "https://api.search.brave.com/res/v1/news/search";
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(15);
    private static final int MAX_MEMORY_SIZE = 8192 * 8192;
    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final Duration RETRY_DELAY = Duration.ofSeconds(1);

    // Search Configuration
    private static final int MIN_WEB_RESULTS = 1;
    private static final int MAX_WEB_RESULTS = 50;
    private static final int DEFAULT_WEB_RESULTS = 10;
    private static final int MIN_NEWS_RESULTS = 1;
    private static final int MAX_NEWS_RESULTS = 20;
    private static final int DEFAULT_NEWS_RESULTS = 5;
    private static final int MAX_NEWS_DISPLAY = 8;
    private static final int MAX_WEB_DISPLAY = 10;

    // Content Configuration
    private static final int DESCRIPTION_MAX_LENGTH = 150;
    private static final String NEWS_FRESHNESS = "pd"; // Past day

    // Input Validation
    private static final int MIN_QUERY_LENGTH = 1;
    private static final int MAX_QUERY_LENGTH = 400;
    private static final int MAX_COUNTRY_CODE_LENGTH = 5;
    private static final int MAX_LANGUAGE_CODE_LENGTH = 5;

    // HTTP Headers
    private static final String SUBSCRIPTION_HEADER = "X-Subscription-Token";
    private static final String ACCEPT_HEADER = "application/json";
    private static final String ACCEPT_ENCODING_HEADER = "gzip";
    private static final String USER_AGENT = "SpringAI-BraveSearchTool/1.0";

    // Instance variables
    @Value("${brave.api.key}")
    private String braveApiKey;

    private WebClient webClient;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AtomicLong searchCount = new AtomicLong(0);

    @PostConstruct
    public void initialize() {
        logger.info("Initializing Brave Search API Tools service");

        if (braveApiKey == null || braveApiKey.trim().isEmpty()) {
            logger.error("Brave API key is not configured");
            throw new BraveSearchInitializationException("Brave API key is required but not configured");
        }

        webClient = WebClient.builder()
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(MAX_MEMORY_SIZE))
                .defaultHeader("User-Agent", USER_AGENT)
                .defaultHeader("Accept", ACCEPT_HEADER)
                .defaultHeader("Accept-Encoding", ACCEPT_ENCODING_HEADER)
                .build();

        logger.info("Brave Search API Tools service initialized successfully");
    }

    @PreDestroy
    public void cleanup() {
        logger.info("Shutting down Brave Search API Tools service");
        logger.info("Brave Search API Tools cleaned up successfully. Total searches: {}", searchCount.get());
    }

    @Tool(name = "brave_web_search",
            description = "Search the web using Brave's independent search index with over 30 billion pages. " +
                    "Provides high-quality, real-time search results perfect for current information, news, facts, and research.")
    public String brave_web_search(
            @ToolParam(description = "Search query") String query,
            @ToolParam(description = "Number of results (1-50, default: 10)", required = false) Integer count,
            @ToolParam(description = "Country code (us, uk, in, etc.)", required = false) String country,
            @ToolParam(description = "Search language (en, es, fr, etc.)", required = false) String searchLang) {

        long searchId = searchCount.incrementAndGet();
        logger.debug("Starting Brave web search #{} for query: '{}'", searchId, query);

        // Validate input
        WebSearchRequest request = validateWebSearchRequest(query, count, country, searchLang);
        if (!request.isValid()) {
            logger.warn("Invalid web search request #{}: {}", searchId, request.getErrorMessage());
            return "❌ " + request.getErrorMessage();
        }

        try {
            String response = performWebSearch(request, searchId);
            WebSearchResult searchResult = parseWebSearchResponse(response, request.getQuery());
            String formattedResult = formatWebSearchResult(searchResult);

            logger.info("Brave web search #{} completed successfully - {} results found",
                    searchId, searchResult.getResultCount());
            return formattedResult;

        } catch (BraveSearchException e) {
            logger.error("Brave web search #{} failed: {}", searchId, e.getMessage());
            return "❌ " + e.getMessage();
        } catch (Exception e) {
            logger.error("Unexpected error during Brave web search #{}", searchId, e);
            return "❌ Failed to perform Brave web search: " + e.getMessage();
        }
    }

    @Tool(name = "brave_news_search",
            description = "Search for recent news articles using Brave's news index. " +
                    "Returns current news articles with publication dates, sources, and descriptions.")
    public String brave_news_search(
            @ToolParam(description = "News search query") String query,
            @ToolParam(description = "Number of results (1-20, default: 5)", required = false) Integer count,
            @ToolParam(description = "Country code (us, uk, in, etc.)", required = false) String country) {

        long searchId = searchCount.incrementAndGet();
        logger.debug("Starting Brave news search #{} for query: '{}'", searchId, query);

        // Validate input
        NewsSearchRequest request = validateNewsSearchRequest(query, count, country);
        if (!request.isValid()) {
            logger.warn("Invalid news search request #{}: {}", searchId, request.getErrorMessage());
            return "❌ " + request.getErrorMessage();
        }

        try {
            String response = performNewsSearch(request, searchId);
            NewsSearchResult searchResult = parseNewsSearchResponse(response, request.getQuery());
            String formattedResult = formatNewsSearchResult(searchResult);

            logger.info("Brave news search #{} completed successfully - {} articles found",
                    searchId, searchResult.getResultCount());
            return formattedResult;

        } catch (BraveSearchException e) {
            logger.error("Brave news search #{} failed: {}", searchId, e.getMessage());
            return "❌ " + e.getMessage();
        } catch (Exception e) {
            logger.error("Unexpected error during Brave news search #{}", searchId, e);
            return "❌ Failed to search Brave news: " + e.getMessage();
        }
    }

    /* ─────────────────────────  IMAGES  ───────────────────────── */
    @Tool(name = "brave_image_search",
            description = "Search for images with Brave’s image index. "
                    + "Returns thumbnails, titles, sources and links.")
    public String brave_image_search(
            @ToolParam(description = "Image search query") String query,
            @ToolParam(description = "Number of results (1-50, default 10)", required = false) Integer count,
            @ToolParam(description = "Country code (us, uk, in, …)", required = false) String country,
            @ToolParam(description = "Search language (en, es, …)", required = false) String searchLang) {

        long id = searchCount.incrementAndGet();
        logger.debug("Starting Brave image search #{} for '{}'", id, query);

        // Re-use existing validation helpers
        WebSearchRequest req = validateWebSearchRequest(query, count, country, searchLang);
        if (!req.isValid()) return "❌ " + req.getErrorMessage();

        try {
            /* Build endpoint */
            StringBuilder url = new StringBuilder("https://api.search.brave.com/res/v1/images/search");
            url.append("?q=").append(URLEncoder.encode(req.getQuery(), UTF_8));
            url.append("&count=").append(req.getCount());
            if (req.getCountry() != null) url.append("&country=").append(req.getCountry());
            if (req.getSearchLang() != null) url.append("&search_lang=").append(req.getSearchLang());

            String json = performApiRequest(url.toString(), id, "image search");
            ImageSearchResult img = parseImageSearchResponse(json, req.getQuery());
            return formatImageSearch(img);

        } catch (BraveSearchException ex) {
            logger.error("Image search #{} failed: {}", id, ex.getMessage());
            return "❌ " + ex.getMessage();
        }
    }

    /* ─────────────────────────  VIDEOS  ───────────────────────── */
    @Tool(name = "brave_video_search",
            description = "Search for videos with Brave’s video index. "
                    + "Returns title, duration, source and video link.")
    public String brave_video_search(
            @ToolParam(description = "Video search query") String query,
            @ToolParam(description = "Number of results (1-50, default 10)", required = false) Integer count,
            @ToolParam(description = "Country code (us, uk, in, …)", required = false) String country,
            @ToolParam(description = "Search language (en, es, …)", required = false) String searchLang) {

        long id = searchCount.incrementAndGet();
        logger.debug("Starting Brave video search #{} for '{}'", id, query);

        WebSearchRequest req = validateWebSearchRequest(query, count, country, searchLang);
        if (!req.isValid()) return "❌ " + req.getErrorMessage();

        try {
            StringBuilder url = new StringBuilder("https://api.search.brave.com/res/v1/videos/search");
            url.append("?q=").append(URLEncoder.encode(req.getQuery(), UTF_8));
            url.append("&count=").append(req.getCount());
            if (req.getCountry() != null) url.append("&country=").append(req.getCountry());
            if (req.getSearchLang() != null) url.append("&search_lang=").append(req.getSearchLang());

            String json = performApiRequest(url.toString(), id, "video search");
            VideoSearchResult vid = parseVideoSearchResponse(json, req.getQuery());
            return formatVideoSearch(vid);

        } catch (BraveSearchException ex) {
            logger.error("Video search #{} failed: {}", id, ex.getMessage());
            return "❌ " + ex.getMessage();
        }
    }

    /* ─────────────────────────  PARSERS  ───────────────────────── */
    private ImageSearchResult parseImageSearchResponse(String json, String q) throws BraveSearchException {
        try {
            JsonNode root = objectMapper.readTree(json);
            List<ImageItem> items = new ArrayList<>();

            for (JsonNode n : root.path("results")) {
                if (items.size() >= MAX_WEB_DISPLAY) break;
                String title = n.path("title").asText("");
                String page = n.path("url").asText("");
                String thumb = n.path("thumbnail").path("src").asText("");
                String src = n.path("source").asText("");
                if (!page.isEmpty()) items.add(new ImageItem(title, page, thumb, src));
            }
            return new ImageSearchResult(q, items);

        } catch (Exception e) {
            throw new BraveSearchException("Failed to parse image results: " + e.getMessage());
        }
    }

    private VideoSearchResult parseVideoSearchResponse(String json, String q) throws BraveSearchException {
        try {
            JsonNode root = objectMapper.readTree(json);
            List<VideoItem> items = new ArrayList<>();

            for (JsonNode n : root.path("results")) {
                if (items.size() >= MAX_NEWS_DISPLAY) break;
                String title = n.path("title").asText("");
                String url = n.path("url").asText("");
                String desc = n.path("description").asText("");
                String dur = n.path("duration").asText("");
                String thumb = n.path("thumbnail").path("src").asText("");
                String src = n.path("meta_url").path("hostname").asText("");
                if (!url.isEmpty()) items.add(new VideoItem(title, url, desc, dur, thumb, src));
            }
            return new VideoSearchResult(q, items);

        } catch (Exception e) {
            throw new BraveSearchException("Failed to parse video results: " + e.getMessage());
        }
    }

    /* ─────────────────────────  FORMATTERS  ───────────────────────── */
    private String formatImageSearch(ImageSearchResult res) {
        if (res.items.isEmpty()) return "❌ No image results for '" + res.q + "'.";
        StringBuilder sb = new StringBuilder("🖼️ **Brave Image Search: \"").append(res.q).append("\"**\n\n");
        int i = 1;
        for (ImageItem it : res.items) {
            sb.append("**").append(i++).append(". ").append(it.title).append("**\n");
            sb.append("🔗 ").append(it.pageUrl).append("\n");
            if (!it.thumb.isEmpty()) sb.append("🌄 ").append(it.thumb).append("\n");
            if (!it.source.isEmpty()) sb.append("📌 ").append(it.source).append("\n");
            sb.append("\n");
        }
        return sb.toString();
    }

    private String formatVideoSearch(VideoSearchResult res) {
        if (res.items.isEmpty()) return "❌ No video results for '" + res.q + "'.";
        StringBuilder sb = new StringBuilder("🎥 **Brave Video Search: \"").append(res.q).append("\"**\n\n");
        int i = 1;
        for (VideoItem v : res.items) {
            sb.append("**").append(i++).append(". ").append(v.title).append("**\n");
            sb.append("🕒 ").append(v.duration).append(" | ");
            if (!v.source.isEmpty()) sb.append("📺 ").append(v.source);
            sb.append("\n");
            if (!v.description.isEmpty())
                sb.append("📝 ").append(truncateSnippet(v.description, DESCRIPTION_MAX_LENGTH)).append("\n");
            sb.append("🔗 ").append(v.url).append("\n\n");
        }
        return sb.toString();
    }

    /* ─────────────────────────  DATA CLASSES  ───────────────────────── */
    private record ImageItem(String title, String pageUrl, String thumb, String source) {
    }

    private record VideoItem(String title, String url, String description,
                             String duration, String thumb, String source) {
    }

    private record ImageSearchResult(String q, List<ImageItem> items) {
        int getResultCount() {
            return items.size();
        }
    }

    private record VideoSearchResult(String q, List<VideoItem> items) {
        int getResultCount() {
            return items.size();
        }
    }


    // API Request Methods
    private String performWebSearch(WebSearchRequest request, long searchId) throws BraveSearchException {
        StringBuilder url = new StringBuilder(BRAVE_WEB_SEARCH_URL);
        url.append("?q=").append(URLEncoder.encode(request.getQuery(), UTF_8));
        url.append("&count=").append(request.getCount());

        if (request.getCountry() != null) {
            url.append("&country=").append(request.getCountry().toLowerCase());
        }
        if (request.getSearchLang() != null) {
            url.append("&search_lang=").append(request.getSearchLang().toLowerCase());
        }

        return performApiRequest(url.toString(), searchId, "web search");
    }

    private String performNewsSearch(NewsSearchRequest request, long searchId) throws BraveSearchException {
        StringBuilder url = new StringBuilder(BRAVE_NEWS_SEARCH_URL);
        url.append("?q=").append(URLEncoder.encode(request.getQuery(), UTF_8));
        url.append("&count=").append(request.getCount());
        url.append("&freshness=").append(NEWS_FRESHNESS);

        if (request.getCountry() != null) {
            url.append("&country=").append(request.getCountry().toLowerCase());
        }

        return performApiRequest(url.toString(), searchId, "news search");
    }

    private String performApiRequest(String url, long searchId, String requestType) throws BraveSearchException {
        try {
            logger.debug("Making Brave {} API request #{}", requestType, searchId);

            String response = webClient.get()
                    .uri(url)
                    .header(SUBSCRIPTION_HEADER, braveApiKey)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(REQUEST_TIMEOUT)
                    .retryWhen(Retry.fixedDelay(MAX_RETRY_ATTEMPTS, RETRY_DELAY)
                            .filter(throwable -> throwable instanceof WebClientRequestException))
                    .block();

            if (response == null || response.trim().isEmpty()) {
                throw new BraveSearchException("Received empty response from Brave Search API.");
            }

            logger.debug("Successfully received {} response for request #{}", requestType, searchId);
            return response;

        } catch (WebClientRequestException e) {
            throw new BraveSearchException("Network error while accessing Brave Search: " + e.getMessage());
        } catch (WebClientResponseException e) {
            if (e.getStatusCode().value() == 401) {
                throw new BraveSearchException("Invalid Brave Search API key.");
            } else if (e.getStatusCode().value() == 429) {
                throw new BraveSearchException("Brave Search API rate limit exceeded.");
            }
            throw new BraveSearchException("Brave Search API error: HTTP " + e.getStatusCode());
        } catch (Exception e) {
            if (e instanceof BraveSearchException) {
                throw e;
            }
            throw new BraveSearchException("Failed to access Brave Search API: " + e.getMessage());
        }
    }

    // Validation Methods
    private WebSearchRequest validateWebSearchRequest(String query, Integer count, String country, String searchLang) {
        SearchRequestValidation baseValidation = validateBaseSearchRequest(query);
        if (!baseValidation.isValid()) {
            return WebSearchRequest.invalid(baseValidation.getErrorMessage());
        }

        int validatedCount = DEFAULT_WEB_RESULTS;
        if (count != null) {
            if (count < MIN_WEB_RESULTS || count > MAX_WEB_RESULTS) {
                return WebSearchRequest.invalid(String.format("Count must be between %d and %d.",
                        MIN_WEB_RESULTS, MAX_WEB_RESULTS));
            }
            validatedCount = count;
        }

        String validatedCountry = validateCountryCode(country);
        String validatedLang = validateLanguageCode(searchLang);

        return WebSearchRequest.valid(baseValidation.getQuery(), validatedCount, validatedCountry, validatedLang);
    }

    private NewsSearchRequest validateNewsSearchRequest(String query, Integer count, String country) {
        SearchRequestValidation baseValidation = validateBaseSearchRequest(query);
        if (!baseValidation.isValid()) {
            return NewsSearchRequest.invalid(baseValidation.getErrorMessage());
        }

        int validatedCount = DEFAULT_NEWS_RESULTS;
        if (count != null) {
            if (count < MIN_NEWS_RESULTS || count > MAX_NEWS_RESULTS) {
                return NewsSearchRequest.invalid(String.format("Count must be between %d and %d.",
                        MIN_NEWS_RESULTS, MAX_NEWS_RESULTS));
            }
            validatedCount = count;
        }

        String validatedCountry = validateCountryCode(country);

        return NewsSearchRequest.valid(baseValidation.getQuery(), validatedCount, validatedCountry);
    }

    private SearchRequestValidation validateBaseSearchRequest(String query) {
        if (query == null || query.trim().isEmpty()) {
            return SearchRequestValidation.invalid("Search query cannot be empty.");
        }

        String trimmedQuery = query.trim();
        if (trimmedQuery.length() < MIN_QUERY_LENGTH) {
            return SearchRequestValidation.invalid("Search query is too short.");
        }
        if (trimmedQuery.length() > MAX_QUERY_LENGTH) {
            return SearchRequestValidation.invalid("Search query is too long (maximum " + MAX_QUERY_LENGTH + " characters).");
        }

        return SearchRequestValidation.valid(trimmedQuery);
    }

    private String validateCountryCode(String country) {
        if (country == null || country.trim().isEmpty()) {
            return null;
        }

        String trimmed = country.trim();
        if (trimmed.length() > MAX_COUNTRY_CODE_LENGTH) {
            return null; // Invalid country code, ignore
        }

        return trimmed;
    }

    private String validateLanguageCode(String language) {
        if (language == null || language.trim().isEmpty()) {
            return null;
        }

        String trimmed = language.trim();
        if (trimmed.length() > MAX_LANGUAGE_CODE_LENGTH) {
            return null; // Invalid language code, ignore
        }

        return trimmed;
    }

    // Parsing Methods
    private WebSearchResult parseWebSearchResponse(String response, String query) throws BraveSearchException {
        try {
            JsonNode root = objectMapper.readTree(response);

            if (!root.has("web") || !root.get("web").has("results")) {
                return new WebSearchResult(query, new ArrayList<>());
            }

            JsonNode results = root.get("web").get("results");
            List<WebSearchResultItem> items = new ArrayList<>();

            for (JsonNode result : results) {
                if (items.size() >= MAX_WEB_DISPLAY) break;

                String title = result.path("title").asText("");
                String url = result.path("url").asText("");
                String description = result.path("description").asText("");

                if (!title.isEmpty() && !url.isEmpty()) {
                    items.add(new WebSearchResultItem(title, url, description));
                }
            }

            return new WebSearchResult(query, items);

        } catch (Exception e) {
            throw new BraveSearchException("Failed to parse web search results: " + e.getMessage());
        }
    }

    private NewsSearchResult parseNewsSearchResponse(String response, String query) throws BraveSearchException {
        try {
            JsonNode root = objectMapper.readTree(response);

            if (!root.has("results")) {
                return new NewsSearchResult(query, new ArrayList<>());
            }

            JsonNode results = root.get("results");
            List<NewsResultItem> items = new ArrayList<>();

            for (JsonNode article : results) {
                if (items.size() >= MAX_NEWS_DISPLAY) break;

                String title = article.path("title").asText("");
                String url = article.path("url").asText("");
                String description = article.path("description").asText("");
                String source = article.path("meta_url").path("hostname").asText("");
                String publishedAt = article.path("age").asText("");

                if (!title.isEmpty() && !url.isEmpty()) {
                    items.add(new NewsResultItem(title, url, description, source, publishedAt));
                }
            }

            return new NewsSearchResult(query, items);

        } catch (Exception e) {
            throw new BraveSearchException("Failed to parse news search results: " + e.getMessage());
        }
    }

    private static String truncateSnippet(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) {
            return text;
        }

        int lastSpace = text.lastIndexOf(' ', maxLength);
        if (lastSpace > maxLength - 20) {
            return text.substring(0, lastSpace) + "...";
        }
        return text.substring(0, maxLength) + "...";
    }

    // Formatting Methods
    private String formatWebSearchResult(WebSearchResult result) {
        if (result.getItems().isEmpty()) {
            return "❌ No web results found for '" + result.getQuery() + "'.";
        }

        StringBuilder output = new StringBuilder();
        output.append("🔍 **Brave Web Search: \"").append(result.getQuery()).append("\"**\n\n");

        for (int i = 0; i < result.getItems().size(); i++) {
            WebSearchResultItem item = result.getItems().get(i);
            output.append("**").append(i + 1).append(". ").append(item.getTitle()).append("**\n");

            if (!item.getDescription().isEmpty()) {
                String shortDesc = item.getDescription().length() > DESCRIPTION_MAX_LENGTH ?
                        item.getDescription().substring(0, DESCRIPTION_MAX_LENGTH) + "…" : item.getDescription();
                output.append("📝 ").append(shortDesc).append("\n");
            }

            output.append("🔗 ").append(item.getUrl()).append("\n\n");
        }

        return output.toString();
    }

    private String formatNewsSearchResult(NewsSearchResult result) {
        if (result.getItems().isEmpty()) {
            return "❌ No news results found for '" + result.getQuery() + "'.";
        }

        StringBuilder output = new StringBuilder();
        output.append("📰 **Brave News Search: \"").append(result.getQuery()).append("\"**\n\n");

        for (int i = 0; i < result.getItems().size(); i++) {
            NewsResultItem item = result.getItems().get(i);
            output.append("**").append(i + 1).append(". ").append(item.getTitle()).append("**\n");

            output.append("📰 *").append(item.getSource()).append("*");
            if (!item.getPublishedAt().isEmpty()) {
                output.append(" | 📅 ").append(item.getPublishedAt());
            }
            output.append("\n");

            if (!item.getDescription().isEmpty()) {
                String shortDesc = item.getDescription().length() > DESCRIPTION_MAX_LENGTH ?
                        item.getDescription().substring(0, DESCRIPTION_MAX_LENGTH) + "…" : item.getDescription();
                output.append("📝 ").append(shortDesc).append("\n");
            }

            output.append("🔗 ").append(item.getUrl()).append("\n\n");
        }

        return output.toString();
    }

    // Helper classes
    private static class SearchRequestValidation {
        private final boolean valid;
        private final String errorMessage;
        private final String query;

        private SearchRequestValidation(boolean valid, String errorMessage, String query) {
            this.valid = valid;
            this.errorMessage = errorMessage;
            this.query = query;
        }

        static SearchRequestValidation valid(String query) {
            return new SearchRequestValidation(true, null, query);
        }

        static SearchRequestValidation invalid(String errorMessage) {
            return new SearchRequestValidation(false, errorMessage, null);
        }

        boolean isValid() {
            return valid;
        }

        String getErrorMessage() {
            return errorMessage;
        }

        String getQuery() {
            return query;
        }
    }

    private static class WebSearchRequest {
        private final boolean valid;
        private final String errorMessage;
        private final String query;
        private final int count;
        private final String country;
        private final String searchLang;

        private WebSearchRequest(boolean valid, String errorMessage, String query, int count, String country, String searchLang) {
            this.valid = valid;
            this.errorMessage = errorMessage;
            this.query = query;
            this.count = count;
            this.country = country;
            this.searchLang = searchLang;
        }

        static WebSearchRequest valid(String query, int count, String country, String searchLang) {
            return new WebSearchRequest(true, null, query, count, country, searchLang);
        }

        static WebSearchRequest invalid(String errorMessage) {
            return new WebSearchRequest(false, errorMessage, null, 0, null, null);
        }

        boolean isValid() {
            return valid;
        }

        String getErrorMessage() {
            return errorMessage;
        }

        String getQuery() {
            return query;
        }

        int getCount() {
            return count;
        }

        String getCountry() {
            return country;
        }

        String getSearchLang() {
            return searchLang;
        }
    }

    private static class NewsSearchRequest {
        private final boolean valid;
        private final String errorMessage;
        private final String query;
        private final int count;
        private final String country;

        private NewsSearchRequest(boolean valid, String errorMessage, String query, int count, String country) {
            this.valid = valid;
            this.errorMessage = errorMessage;
            this.query = query;
            this.count = count;
            this.country = country;
        }

        static NewsSearchRequest valid(String query, int count, String country) {
            return new NewsSearchRequest(true, null, query, count, country);
        }

        static NewsSearchRequest invalid(String errorMessage) {
            return new NewsSearchRequest(false, errorMessage, null, 0, null);
        }

        boolean isValid() {
            return valid;
        }

        String getErrorMessage() {
            return errorMessage;
        }

        String getQuery() {
            return query;
        }

        int getCount() {
            return count;
        }

        String getCountry() {
            return country;
        }
    }

    private static class WebSearchResultItem {
        private final String title;
        private final String url;
        private final String description;

        WebSearchResultItem(String title, String url, String description) {
            this.title = title != null ? title : "";
            this.url = url != null ? url : "";
            this.description = description != null ? description : "";
        }

        String getTitle() {
            return title;
        }

        String getUrl() {
            return url;
        }

        String getDescription() {
            return description;
        }
    }

    private static class NewsResultItem {
        private final String title;
        private final String url;
        private final String description;
        private final String source;
        private final String publishedAt;

        NewsResultItem(String title, String url, String description, String source, String publishedAt) {
            this.title = title != null ? title : "";
            this.url = url != null ? url : "";
            this.description = description != null ? description : "";
            this.source = source != null ? source : "";
            this.publishedAt = publishedAt != null ? publishedAt : "";
        }

        String getTitle() {
            return title;
        }

        String getUrl() {
            return url;
        }

        String getDescription() {
            return description;
        }

        String getSource() {
            return source;
        }

        String getPublishedAt() {
            return publishedAt;
        }
    }

    private static class WebSearchResult {
        private final String query;
        private final List<WebSearchResultItem> items;

        WebSearchResult(String query, List<WebSearchResultItem> items) {
            this.query = query;
            this.items = items;
        }

        String getQuery() {
            return query;
        }

        List<WebSearchResultItem> getItems() {
            return items;
        }

        int getResultCount() {
            return items.size();
        }
    }

    private static class NewsSearchResult {
        private final String query;
        private final List<NewsResultItem> items;

        NewsSearchResult(String query, List<NewsResultItem> items) {
            this.query = query;
            this.items = items;
        }

        String getQuery() {
            return query;
        }

        List<NewsResultItem> getItems() {
            return items;
        }

        int getResultCount() {
            return items.size();
        }
    }

    // Custom exceptions
    private static class BraveSearchInitializationException extends RuntimeException {
        BraveSearchInitializationException(String message) {
            super(message);
        }
    }

    private static class BraveSearchException extends Exception {
        BraveSearchException(String message) {
            super(message);
        }
    }
}


