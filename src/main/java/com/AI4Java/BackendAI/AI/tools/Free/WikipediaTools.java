package com.AI4Java.BackendAI.AI.tools.Free;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
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

@Service
public class WikipediaTools {

    private static final Logger logger = LoggerFactory.getLogger(WikipediaTools.class);

    // API Configuration
    private static final String WIKIPEDIA_API_BASE = "https://en.wikipedia.org/api/rest_v1";
    private static final String WIKIPEDIA_SEARCH_API = "https://en.wikipedia.org/w/api.php";
    private static final String WIKIPEDIA_BASE_URL = "https://en.wikipedia.org/wiki/";

    // HTTP Configuration
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration ARTICLE_TIMEOUT = Duration.ofSeconds(15);
    private static final int MAX_MEMORY_SIZE = 8192 * 8192;
    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final Duration RETRY_DELAY = Duration.ofSeconds(1);

    // Content Configuration
    private static final int MAX_SEARCH_RESULTS = 5;
    private static final int MAX_RELATED_ARTICLES = 8;
    private static final int MAX_EXTRACT_LENGTH = 2000;
    private static final int MAX_SUMMARY_LENGTH = 2000;

    // Input Validation
    private static final int MIN_QUERY_LENGTH = 1;
    private static final int MAX_QUERY_LENGTH = 200;
    private static final int MAX_TITLE_LENGTH = 255;

    // API Parameters
    private static final String SEARCH_PARAMS = "?action=opensearch&search=%s&limit=%d&namespace=0&format=json";
    private static final String ARTICLE_PARAMS = "?action=query&format=json&titles=%s&prop=extracts&exintro=&explaintext=&exsectionformat=plain";
    private static final String RANDOM_PARAMS = "?action=query&format=json&list=random&rnnamespace=0&rnlimit=1";
    private static final String LINKS_PARAMS = "?action=query&format=json&titles=%s&prop=links&pllimit=%d&plnamespace=0";

    // HTTP Headers
    private static final String USER_AGENT = "SpringAI-WikipediaTool/1.0";

    // Instance variables
    private WebClient webClient;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AtomicLong requestCount = new AtomicLong(0);

    @PostConstruct
    public void initialize() {
        logger.info("Initializing Wikipedia Tools service");
        webClient = WebClient.builder()
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(MAX_MEMORY_SIZE))
                .defaultHeader("User-Agent", USER_AGENT)
                .build();
        logger.info("Wikipedia Tools service initialized successfully");
    }

    @PreDestroy
    public void cleanup() {
        logger.info("Shutting down Wikipedia Tools service");
        logger.info("Wikipedia Tools cleaned up successfully. Total requests: {}", requestCount.get());
    }

    @Tool(name = "wikipedia_search",
            description = "Search Wikipedia articles by topic or keyword. " +
                    "Returns a list of relevant Wikipedia articles with summaries and links.")
    public String wikipedia_search(@ToolParam(description = "Search phrase or term") String query) {
        long requestId = requestCount.incrementAndGet();
        logger.debug("Starting Wikipedia search #{} for query: '{}'", requestId, query);

        // Validate input
        WikipediaRequest request = validateSearchRequest(query);
        if (!request.isValid()) {
            logger.warn("Invalid search request #{}: {}", requestId, request.getErrorMessage());
            return "❌ " + request.getErrorMessage();
        }

        try {
            String response = performSearchRequest(request.getQuery(), requestId);
            SearchResultData results = parseSearchResults(response, request.getQuery());
            String formattedResult = formatSearchResults(results);

            logger.info("Wikipedia search #{} completed successfully - {} results found",
                    requestId, results.getResultCount());
            return formattedResult;

        } catch (WikipediaApiException e) {
            logger.error("Wikipedia search #{} failed: {}", requestId, e.getMessage());
            return "❌ " + e.getMessage();
        } catch (Exception e) {
            logger.error("Unexpected error during Wikipedia search #{}", requestId, e);
            return String.format("❌ Failed to search Wikipedia for '%s'. Please try again.", query);
        }
    }

    @Tool(name = "wikipedia_summary",
            description = "Get a detailed summary of a specific Wikipedia article. " +
                    "Provides description, extract, and links to the full article.")
    public String wikipedia_summary(@ToolParam(description = "Exact article title") String title) {
        long requestId = requestCount.incrementAndGet();
        logger.debug("Starting Wikipedia summary #{} for title: '{}'", requestId, title);

        // Validate input
        WikipediaRequest request = validateTitleRequest(title);
        if (!request.isValid()) {
            logger.warn("Invalid summary request #{}: {}", requestId, request.getErrorMessage());
            return "❌ " + request.getErrorMessage();
        }

        try {
            String response = performSummaryRequest(request.getQuery(), requestId);
            SummaryData summary = parseSummaryResponse(response, request.getQuery());
            String formattedResult = formatSummary(summary);

            logger.info("Wikipedia summary #{} completed successfully for: {}", requestId, summary.getTitle());
            return formattedResult;

        } catch (WikipediaApiException e) {
            logger.error("Wikipedia summary #{} failed: {}", requestId, e.getMessage());
            return "❌ " + e.getMessage();
        } catch (Exception e) {
            logger.error("Unexpected error during Wikipedia summary #{}", requestId, e);
            return String.format("❌ Could not find Wikipedia article for '%s'. Try searching first.", title);
        }
    }

    @Tool(name = "wikipedia_article",
            description = "Get the full content of a Wikipedia article. " +
                    "Returns the complete article text with proper formatting.")
    public String wikipedia_article(@ToolParam(description = "Exact article title") String title) {
        long requestId = requestCount.incrementAndGet();
        logger.debug("Starting Wikipedia article #{} for title: '{}'", requestId, title);

        // Validate input
        WikipediaRequest request = validateTitleRequest(title);
        if (!request.isValid()) {
            logger.warn("Invalid article request #{}: {}", requestId, request.getErrorMessage());
            return "❌ " + request.getErrorMessage();
        }

        try {
            String response = performArticleRequest(request.getQuery(), requestId);
            ArticleData article = parseArticleResponse(response, request.getQuery());
            String formattedResult = formatArticle(article);

            logger.info("Wikipedia article #{} completed successfully for: {}", requestId, article.getTitle());
            return formattedResult;

        } catch (WikipediaApiException e) {
            logger.error("Wikipedia article #{} failed: {}", requestId, e.getMessage());
            return "❌ " + e.getMessage();
        } catch (Exception e) {
            logger.error("Unexpected error during Wikipedia article #{}", requestId, e);
            return String.format("❌ Could not retrieve full article for '%s'.", title);
        }
    }

    @Tool(name = "wikipedia_random",
            description = "Get a random Wikipedia article. Great for discovering new topics and learning about diverse subjects!")
    public String wikipedia_random() {
        long requestId = requestCount.incrementAndGet();
        logger.debug("Starting random Wikipedia article #{}", requestId);

        try {
            String response = performRandomRequest(requestId);
            String randomTitle = parseRandomResponse(response);

            logger.debug("Got random article title for request #{}: {}", requestId, randomTitle);

            // Get summary of the random article
            String result = wikipedia_summary(randomTitle);

            logger.info("Random Wikipedia article #{} completed successfully: {}", requestId, randomTitle);
            return result;

        } catch (WikipediaApiException e) {
            logger.error("Random Wikipedia article #{} failed: {}", requestId, e.getMessage());
            return "❌ " + e.getMessage();
        } catch (Exception e) {
            logger.error("Unexpected error during random Wikipedia article #{}", requestId, e);
            return "❌ Failed to get random Wikipedia article.";
        }
    }

    @Tool(name = "wikipedia_related",
            description = "Find articles related to a given Wikipedia topic. " +
                    "Returns links to related Wikipedia articles based on the source article's internal links.")
    public String wikipedia_related(@ToolParam(description = "Exact article title") String title) {
        long requestId = requestCount.incrementAndGet();
        logger.debug("Starting Wikipedia related articles #{} for title: '{}'", requestId, title);

        // Validate input
        WikipediaRequest request = validateTitleRequest(title);
        if (!request.isValid()) {
            logger.warn("Invalid related articles request #{}: {}", requestId, request.getErrorMessage());
            return "❌ " + request.getErrorMessage();
        }

        try {
            String response = performRelatedRequest(request.getQuery(), requestId);
            RelatedArticlesData related = parseRelatedResponse(response, request.getQuery());
            String formattedResult = formatRelatedArticles(related);

            logger.info("Wikipedia related articles #{} completed successfully - {} articles found",
                    requestId, related.getArticleCount());
            return formattedResult;

        } catch (WikipediaApiException e) {
            logger.error("Wikipedia related articles #{} failed: {}", requestId, e.getMessage());
            return "❌ " + e.getMessage();
        } catch (Exception e) {
            logger.error("Unexpected error during Wikipedia related articles #{}", requestId, e);
            return String.format("❌ Could not find related articles for '%s'.", title);
        }
    }

    // API Request Methods
    private String performSearchRequest(String query, long requestId) throws WikipediaApiException {
        return performApiRequest(
                WIKIPEDIA_SEARCH_API + String.format(SEARCH_PARAMS,
                        URLEncoder.encode(query, StandardCharsets.UTF_8), MAX_SEARCH_RESULTS),
                REQUEST_TIMEOUT,
                requestId,
                "search"
        );
    }

    private String performSummaryRequest(String title, long requestId) throws WikipediaApiException {
        String encodedTitle = URLEncoder.encode(title.replace(" ", "_"), StandardCharsets.UTF_8);
        return performApiRequest(
                WIKIPEDIA_API_BASE + "/page/summary/" + encodedTitle,
                REQUEST_TIMEOUT,
                requestId,
                "summary"
        );
    }

    private String performArticleRequest(String title, long requestId) throws WikipediaApiException {
        return performApiRequest(
                WIKIPEDIA_SEARCH_API + String.format(ARTICLE_PARAMS,
                        URLEncoder.encode(title, StandardCharsets.UTF_8)),
                ARTICLE_TIMEOUT,
                requestId,
                "article"
        );
    }

    private String performRandomRequest(long requestId) throws WikipediaApiException {
        return performApiRequest(
                WIKIPEDIA_SEARCH_API + RANDOM_PARAMS,
                REQUEST_TIMEOUT,
                requestId,
                "random"
        );
    }

    private String performRelatedRequest(String title, long requestId) throws WikipediaApiException {
        return performApiRequest(
                WIKIPEDIA_SEARCH_API + String.format(LINKS_PARAMS,
                        URLEncoder.encode(title, StandardCharsets.UTF_8), MAX_RELATED_ARTICLES),
                REQUEST_TIMEOUT,
                requestId,
                "related"
        );
    }

    private String performApiRequest(String url, Duration timeout, long requestId, String requestType)
            throws WikipediaApiException {
        try {
            logger.debug("Making {} API request #{}: {}", requestType, requestId,
                    url.substring(0, Math.min(url.length(), 100)) + "...");

            String response = webClient.get()
                    .uri(url)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(timeout)
                    .retryWhen(Retry.fixedDelay(MAX_RETRY_ATTEMPTS, RETRY_DELAY)
                            .filter(throwable -> throwable instanceof WebClientRequestException))
                    .block();

            if (response == null || response.trim().isEmpty()) {
                throw new WikipediaApiException("Received empty response from Wikipedia API.");
            }

            logger.debug("Successfully received {} response for request #{}", requestType, requestId);
            return response;

        } catch (WebClientRequestException e) {
            throw new WikipediaApiException("Network error while accessing Wikipedia: " + e.getMessage());
        } catch (WebClientResponseException e) {
            if (e.getStatusCode().value() == 404) {
                throw new WikipediaApiException("Wikipedia article not found.");
            }
            throw new WikipediaApiException("Wikipedia API error: HTTP " + e.getStatusCode());
        } catch (Exception e) {
            if (e instanceof WikipediaApiException) {
                throw e;
            }
            throw new WikipediaApiException("Failed to access Wikipedia API: " + e.getMessage());
        }
    }

    // Validation Methods
    private WikipediaRequest validateSearchRequest(String query) {
        if (query == null || query.trim().isEmpty()) {
            return WikipediaRequest.invalid("Search query cannot be empty.");
        }

        String trimmedQuery = query.trim();
        if (trimmedQuery.length() < MIN_QUERY_LENGTH) {
            return WikipediaRequest.invalid("Search query is too short.");
        }
        if (trimmedQuery.length() > MAX_QUERY_LENGTH) {
            return WikipediaRequest.invalid("Search query is too long (maximum " + MAX_QUERY_LENGTH + " characters).");
        }

        return WikipediaRequest.valid(trimmedQuery);
    }

    private WikipediaRequest validateTitleRequest(String title) {
        if (title == null || title.trim().isEmpty()) {
            return WikipediaRequest.invalid("Article title cannot be empty.");
        }

        String trimmedTitle = title.trim();
        if (trimmedTitle.length() > MAX_TITLE_LENGTH) {
            return WikipediaRequest.invalid("Article title is too long (maximum " + MAX_TITLE_LENGTH + " characters).");
        }

        return WikipediaRequest.valid(trimmedTitle);
    }

    // Parsing Methods
    private SearchResultData parseSearchResults(String response, String query) throws WikipediaApiException {
        try {
            JsonNode root = objectMapper.readTree(response);

            if (!root.isArray() || root.size() < 2) {
                return new SearchResultData(query, new ArrayList<>());
            }

            JsonNode titles = root.get(1);
            JsonNode descriptions = root.get(2);
            JsonNode urls = root.get(3);

            List<SearchResultItem> items = new ArrayList<>();
            for (int i = 0; i < titles.size() && i < MAX_SEARCH_RESULTS; i++) {
                String title = titles.get(i).asText();
                String description = i < descriptions.size() ? descriptions.get(i).asText() : "";
                String url = i < urls.size() ? urls.get(i).asText() : "";

                items.add(new SearchResultItem(title, description, url));
            }

            return new SearchResultData(query, items);

        } catch (Exception e) {
            throw new WikipediaApiException("Failed to parse search results: " + e.getMessage());
        }
    }

    private SummaryData parseSummaryResponse(String response, String title) throws WikipediaApiException {
        try {
            JsonNode root = objectMapper.readTree(response);

            String actualTitle = root.path("title").asText();
            String description = root.path("description").asText();
            String extract = root.path("extract").asText();
            String pageUrl = root.path("content_urls").path("desktop").path("page").asText();
            String thumbnailUrl = root.path("thumbnail").path("source").asText();

            return new SummaryData(actualTitle, description, extract, pageUrl, thumbnailUrl);

        } catch (Exception e) {
            throw new WikipediaApiException("Failed to parse article summary: " + e.getMessage());
        }
    }

    private ArticleData parseArticleResponse(String response, String title) throws WikipediaApiException, JsonProcessingException {
        try {
            JsonNode root = objectMapper.readTree(response);
            JsonNode pages = root.path("query").path("pages");

            if (pages.isMissingNode() || !pages.elements().hasNext()) {
                throw new WikipediaApiException("Article not found: " + title);
            }

            JsonNode page = pages.elements().next();
            String actualTitle = page.path("title").asText();
            String extract = page.path("extract").asText();

            if (extract.isEmpty()) {
                throw new WikipediaApiException("No content found for article: " + title);
            }

            return new ArticleData(actualTitle, extract);

        } catch (Exception e) {
            if (e instanceof WikipediaApiException) {
                throw e;
            }
            throw new WikipediaApiException("Failed to parse article content: " + e.getMessage());
        }
    }

    private String parseRandomResponse(String response) throws WikipediaApiException {
        try {
            JsonNode root = objectMapper.readTree(response);
            JsonNode randomPage = root.path("query").path("random").get(0);
            return randomPage.path("title").asText();

        } catch (Exception e) {
            throw new WikipediaApiException("Failed to parse random article response: " + e.getMessage());
        }
    }

    private RelatedArticlesData parseRelatedResponse(String response, String title) throws WikipediaApiException {
        try {
            JsonNode root = objectMapper.readTree(response);
            JsonNode pages = root.path("query").path("pages");

            if (pages.isMissingNode() || !pages.elements().hasNext()) {
                return new RelatedArticlesData(title, new ArrayList<>());
            }

            JsonNode page = pages.elements().next();
            JsonNode links = page.path("links");

            List<String> relatedTitles = new ArrayList<>();
            for (JsonNode link : links) {
                String linkTitle = link.path("title").asText();
                if (!linkTitle.isEmpty() && relatedTitles.size() < MAX_RELATED_ARTICLES) {
                    relatedTitles.add(linkTitle);
                }
            }

            return new RelatedArticlesData(title, relatedTitles);

        } catch (Exception e) {
            throw new WikipediaApiException("Failed to parse related articles: " + e.getMessage());
        }
    }

    // Formatting Methods
    private String formatSearchResults(SearchResultData results) {
        if (results.getItems().isEmpty()) {
            return String.format("❌ No Wikipedia articles found for '%s'.", results.getQuery());
        }

        StringBuilder output = new StringBuilder();
        output.append(String.format("🔍 **Wikipedia Search Results for: \"%s\"**\n\n", results.getQuery()));

        for (int i = 0; i < results.getItems().size(); i++) {
            SearchResultItem item = results.getItems().get(i);
            output.append(String.format("**%d. %s**\n", i + 1, item.getTitle()));

            if (!item.getDescription().isEmpty()) {
                output.append(String.format("   %s\n", item.getDescription()));
            }

            output.append(String.format("   🔗 %s\n\n", item.getUrl()));
        }

        output.append("💡 **Tip:** Use 'wikipedia_summary [title]' to get detailed information about any article.");
        return output.toString();
    }

    private String formatSummary(SummaryData summary) {
        StringBuilder output = new StringBuilder();
        output.append(String.format("📖 **%s**\n\n", summary.getTitle()));

        if (!summary.getDescription().isEmpty()) {
            output.append(String.format("*%s*\n\n", summary.getDescription()));
        }

        if (!summary.getExtract().isEmpty()) {
            String shortExtract = summary.getExtract().length() > MAX_SUMMARY_LENGTH ?
                    summary.getExtract().substring(0, MAX_SUMMARY_LENGTH) + "..." : summary.getExtract();
            output.append(String.format("%s\n\n", shortExtract));
        }

        if (!summary.getThumbnailUrl().isEmpty()) {
            output.append(String.format("🖼️ **Image:** %s\n", summary.getThumbnailUrl()));
        }

        output.append(String.format("🔗 **Full Article:** %s\n\n", summary.getPageUrl()));
        output.append("💡 **Commands:** Use 'wikipedia_article' for full content or 'wikipedia_related' for related topics.");

        return output.toString();
    }

    private String formatArticle(ArticleData article) {
        StringBuilder output = new StringBuilder();
        output.append(String.format("📄 **Wikipedia Article: %s**\n\n", article.getTitle()));

        String content = article.getExtract();
        if (content.length() > MAX_EXTRACT_LENGTH) {
            output.append(content.substring(0, MAX_EXTRACT_LENGTH));
            output.append("...\n\n📄 [Article truncated - full content available on Wikipedia]\n\n");
        } else {
            output.append(content).append("\n\n");
        }

        output.append(String.format("🔗 **Full Article:** %s%s",
                WIKIPEDIA_BASE_URL, URLEncoder.encode(article.getTitle().replace(" ", "_"), StandardCharsets.UTF_8)));

        return output.toString();
    }

    private String formatRelatedArticles(RelatedArticlesData related) {
        if (related.getRelatedTitles().isEmpty()) {
            return String.format("❌ No related articles found for '%s'.", related.getOriginalTitle());
        }

        StringBuilder output = new StringBuilder();
        output.append(String.format("🔗 **Related Wikipedia Articles for: \"%s\"**\n\n", related.getOriginalTitle()));

        for (int i = 0; i < related.getRelatedTitles().size(); i++) {
            String relatedTitle = related.getRelatedTitles().get(i);
            output.append(String.format("**%d. %s**\n", i + 1, relatedTitle));
            output.append(String.format("   🔗 %s%s\n\n",
                    WIKIPEDIA_BASE_URL, URLEncoder.encode(relatedTitle.replace(" ", "_"), StandardCharsets.UTF_8)));
        }

        output.append("💡 **Tip:** Use 'wikipedia_summary [title]' to learn more about any related topic.");
        return output.toString();
    }

    // Helper classes
    private static class WikipediaRequest {
        private final boolean valid;
        private final String errorMessage;
        private final String query;

        private WikipediaRequest(boolean valid, String errorMessage, String query) {
            this.valid = valid;
            this.errorMessage = errorMessage;
            this.query = query;
        }

        static WikipediaRequest valid(String query) {
            return new WikipediaRequest(true, null, query);
        }

        static WikipediaRequest invalid(String errorMessage) {
            return new WikipediaRequest(false, errorMessage, null);
        }

        boolean isValid() { return valid; }
        String getErrorMessage() { return errorMessage; }
        String getQuery() { return query; }
    }

    private static class SearchResultItem {
        private final String title;
        private final String description;
        private final String url;

        SearchResultItem(String title, String description, String url) {
            this.title = title != null ? title : "";
            this.description = description != null ? description : "";
            this.url = url != null ? url : "";
        }

        String getTitle() { return title; }
        String getDescription() { return description; }
        String getUrl() { return url; }
    }

    private static class SearchResultData {
        private final String query;
        private final List<SearchResultItem> items;

        SearchResultData(String query, List<SearchResultItem> items) {
            this.query = query;
            this.items = items;
        }

        String getQuery() { return query; }
        List<SearchResultItem> getItems() { return items; }
        int getResultCount() { return items.size(); }
    }

    private static class SummaryData {
        private final String title;
        private final String description;
        private final String extract;
        private final String pageUrl;
        private final String thumbnailUrl;

        SummaryData(String title, String description, String extract, String pageUrl, String thumbnailUrl) {
            this.title = title != null ? title : "";
            this.description = description != null ? description : "";
            this.extract = extract != null ? extract : "";
            this.pageUrl = pageUrl != null ? pageUrl : "";
            this.thumbnailUrl = thumbnailUrl != null ? thumbnailUrl : "";
        }

        String getTitle() { return title; }
        String getDescription() { return description; }
        String getExtract() { return extract; }
        String getPageUrl() { return pageUrl; }
        String getThumbnailUrl() { return thumbnailUrl; }
    }

    private static class ArticleData {
        private final String title;
        private final String extract;

        ArticleData(String title, String extract) {
            this.title = title != null ? title : "";
            this.extract = extract != null ? extract : "";
        }

        String getTitle() { return title; }
        String getExtract() { return extract; }
    }

    private static class RelatedArticlesData {
        private final String originalTitle;
        private final List<String> relatedTitles;

        RelatedArticlesData(String originalTitle, List<String> relatedTitles) {
            this.originalTitle = originalTitle;
            this.relatedTitles = relatedTitles;
        }

        String getOriginalTitle() { return originalTitle; }
        List<String> getRelatedTitles() { return relatedTitles; }
        int getArticleCount() { return relatedTitles.size(); }
    }

    // Custom exception
    private static class WikipediaApiException extends Exception {
        WikipediaApiException(String message) {
            super(message);
        }
    }
}


