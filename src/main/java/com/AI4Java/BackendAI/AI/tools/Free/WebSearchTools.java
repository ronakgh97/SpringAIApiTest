package com.AI4Java.BackendAI.AI.tools.Free;

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

import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class WebSearchTools {

    private static final Logger logger = LoggerFactory.getLogger(WebSearchTools.class);

    // API Configuration
    private static final String DUCKDUCKGO_API_URL = "https://api.duckduckgo.com/";
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(15);
    private static final int MAX_MEMORY_SIZE = 8192 * 8192;
    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final Duration RETRY_DELAY = Duration.ofSeconds(1);

    // Search Configuration
    private static final int MAX_SEARCH_RESULTS = 10;
    private static final int MAX_SNIPPET_LENGTH = 2000;
    private static final int MAX_TITLE_LENGTH = 1200;
    private static final int URL_DISPLAY_MAX_LENGTH = 100;
    private static final int URL_PATH_MAX_LENGTH = 100;
    private static final int URL_TRUNCATE_THRESHOLD = 1800;

    // Query Validation
    private static final int MIN_QUERY_LENGTH = 1;
    private static final int MAX_QUERY_LENGTH = 500;

    // HTTP Headers
    private static final String USER_AGENT = "SpringAI-WebSearchTool/1.0";
    private static final String API_IDENTIFIER = "&t=springAI";

    // API Parameters
    private static final String API_PARAMS = "?q=%s&format=json&no_html=1&skip_disambig=1" + API_IDENTIFIER;

    // Instance variables
    private WebClient webClient;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AtomicLong searchCount = new AtomicLong(0);

    @PostConstruct
    public void initialize() {
        logger.info("Initializing Web Search Tools service");
        webClient = WebClient.builder()
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(MAX_MEMORY_SIZE))
                .defaultHeader("User-Agent", USER_AGENT)
                .build();
        logger.info("Web Search Tools service initialized successfully");
    }

    @PreDestroy
    public void cleanup() {
        logger.info("Shutting down Web Search Tools service");
        logger.info("Web Search Tools cleaned up successfully. Total searches: {}", searchCount.get());
    }

    @Tool(name = "web_search",
            description = "Searches the web using DuckDuckGo API for current information and facts. " +
                    "Returns instant answers when available plus related search results with titles and snippets. " +
                    "Use this when you need recent information, news, or facts not in your training data.")
    public String webSearch(@ToolParam(description = "Search terms or query") String query) {
        long searchId = searchCount.incrementAndGet();
        logger.debug("Starting web search #{} for query: '{}'", searchId, query);

        // Validate input
        SearchRequest request = validateSearchRequest(query);
        if (!request.isValid()) {
            logger.warn("Invalid search request #{}: {}", searchId, request.getErrorMessage());
            return "❌ " + request.getErrorMessage();
        }

        try {
            String jsonResponse = performWebSearch(request.getQuery(), searchId);
            SearchResult result = parseSearchResponse(jsonResponse, request.getQuery());
            String formattedResult = formatSearchResult(result);

            logger.info("Web search #{} completed successfully - {} results found",
                    searchId, result.getResultCount());
            return formattedResult;

        } catch (WebSearchException e) {
            logger.error("Web search #{} failed: {}", searchId, e.getMessage());
            return "❌ " + e.getMessage();
        } catch (Exception e) {
            logger.error("Unexpected error during web search #{}", searchId, e);
            return "❌ Failed to perform web search. Please try again with different keywords.";
        }
    }

    private String performWebSearch(String query, long searchId) throws WebSearchException {
        try {
            String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);
            String apiUrl = DUCKDUCKGO_API_URL + String.format(API_PARAMS, encodedQuery);

            logger.debug("Calling DuckDuckGo API for search #{}", searchId);

            String response = webClient.get()
                    .uri(apiUrl)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(REQUEST_TIMEOUT)
                    .retryWhen(Retry.fixedDelay(MAX_RETRY_ATTEMPTS, RETRY_DELAY)
                            .filter(throwable -> throwable instanceof WebClientRequestException))
                    .block();

            if (response == null || response.trim().isEmpty()) {
                throw new WebSearchException("Received empty response from search API.");
            }

            logger.debug("Successfully received response from DuckDuckGo API for search #{}", searchId);
            return response;

        } catch (WebClientRequestException e) {
            throw new WebSearchException("Network error while searching: " + e.getMessage());
        } catch (WebClientResponseException e) {
            throw new WebSearchException("Search API error: HTTP " + e.getStatusCode());
        } catch (Exception e) {
            if (e instanceof WebSearchException) {
                throw e;
            }
            throw new WebSearchException("Failed to perform web search: " + e.getMessage());
        }
    }

    private SearchRequest validateSearchRequest(String query) {
        if (query == null || query.trim().isEmpty()) {
            return SearchRequest.invalid("Search query cannot be empty.");
        }

        String trimmedQuery = query.trim();
        if (trimmedQuery.length() < MIN_QUERY_LENGTH) {
            return SearchRequest.invalid("Search query is too short.");
        }
        if (trimmedQuery.length() > MAX_QUERY_LENGTH) {
            return SearchRequest.invalid("Search query is too long (maximum " + MAX_QUERY_LENGTH + " characters).");
        }

        return SearchRequest.valid(trimmedQuery);
    }

    private SearchResult parseSearchResponse(String jsonResponse, String originalQuery) throws WebSearchException {
        try {
            JsonNode root = objectMapper.readTree(jsonResponse);

            // Extract instant answer
            InstantAnswer instantAnswer = extractInstantAnswer(root);

            // Extract related topics (search results)
            SearchResultList searchResults = extractSearchResults(root);

            return new SearchResult(originalQuery, instantAnswer, searchResults);

        } catch (Exception e) {
            throw new WebSearchException("Failed to parse search results: " + e.getMessage());
        }
    }

    private InstantAnswer extractInstantAnswer(JsonNode root) {
        String abstractText = root.path("AbstractText").asText();
        String abstractUrl = root.path("AbstractURL").asText();
        String abstractSource = root.path("AbstractSource").asText();

        if (!abstractText.isEmpty()) {
            return new InstantAnswer(abstractText, abstractUrl, abstractSource);
        }
        return null;
    }

    private SearchResultList extractSearchResults(JsonNode root) {
        SearchResultList results = new SearchResultList();
        JsonNode relatedTopics = root.path("RelatedTopics");

        if (!relatedTopics.isArray()) {
            return results;
        }

        for (JsonNode topic : relatedTopics) {
            if (results.size() >= MAX_SEARCH_RESULTS) break;

            // Handle nested topics
            JsonNode topicsArray = topic.path("Topics");
            if (topicsArray.isArray() && topicsArray.size() > 0) {
                for (JsonNode nestedTopic : topicsArray) {
                    if (results.size() >= MAX_SEARCH_RESULTS) break;
                    SearchResultItem item = createSearchResultItem(nestedTopic);
                    if (item != null) {
                        results.add(item);
                    }
                }
            } else {
                SearchResultItem item = createSearchResultItem(topic);
                if (item != null) {
                    results.add(item);
                }
            }
        }

        return results;
    }

    private SearchResultItem createSearchResultItem(JsonNode resultNode) {
        String text = resultNode.path("Text").asText();
        String url = resultNode.path("FirstURL").asText();

        if (text.isEmpty() || url.isEmpty()) {
            return null;
        }

        String title = extractTitleFromText(text);
        String description = extractDescriptionFromText(text, title);

        return new SearchResultItem(title, description, url);
    }

    private String formatSearchResult(SearchResult result) {
        StringBuilder output = new StringBuilder();
        output.append("🔍 **Web Search Results for: \"").append(result.getQuery()).append("\"**\n\n");

        // Add instant answer if available
        if (result.hasInstantAnswer()) {
            InstantAnswer answer = result.getInstantAnswer();
            output.append("🎯 **Instant Answer**");
            if (!answer.getSource().isEmpty()) {
                output.append(" (from ").append(answer.getSource()).append(")");
            }
            output.append(":\n");
            output.append(answer.getText()).append("\n");
            if (!answer.getUrl().isEmpty()) {
                output.append("🔗 ").append(shortenUrl(answer.getUrl())).append("\n");
            }
            output.append("\n");
        }

        // Add search results
        SearchResultList searchResults = result.getSearchResults();
        if (!searchResults.isEmpty()) {
            output.append("📚 **Related Results:**\n\n");

            for (int i = 0; i < searchResults.size(); i++) {
                SearchResultItem item = searchResults.get(i);
                output.append("**").append(i + 1).append(". ").append(item.getTitle()).append("**\n");

                if (!item.getDescription().isEmpty()) {
                    output.append("📝 ").append(truncateSnippet(item.getDescription(), MAX_SNIPPET_LENGTH)).append("\n");
                }

                output.append("🔗 ").append(shortenUrl(item.getUrl())).append("\n\n");
            }
        }

        // Add footer
        if (!result.hasInstantAnswer() && searchResults.isEmpty()) {
            output.append("❌ No results found for '").append(result.getQuery()).append("'.\n");
            output.append("💡 Try using different keywords or more specific terms.");
        } else {
            output.append("---\n");
            output.append("💡 Found ").append(searchResults.size()).append(" result(s)");
            if (result.hasInstantAnswer()) {
                output.append(" + instant answer");
            }
            output.append(" • Powered by DuckDuckGo");
        }

        return output.toString();
    }

    private String extractTitleFromText(String text) {
        int separatorIndex = text.indexOf(" - ");
        if (separatorIndex > 0) {
            String title = text.substring(0, separatorIndex);
            return title.length() > MAX_TITLE_LENGTH ? title.substring(0, MAX_TITLE_LENGTH) + "..." : title;
        }
        return text.length() > MAX_TITLE_LENGTH ? text.substring(0, MAX_TITLE_LENGTH) + "..." : text;
    }

    private String extractDescriptionFromText(String text, String title) {
        if (text.startsWith(title) && text.length() > title.length() + 3) {
            return text.substring(title.length() + 3); // Remove " - "
        }
        return text.equals(title) ? "" : text;
    }

    private static String shortenUrl(String url) {
        if (url == null || url.length() <= URL_DISPLAY_MAX_LENGTH) {
            return url;
        }

        try {
            URL parsedUrl = new URL(url);
            String host = parsedUrl.getHost();
            String path = parsedUrl.getPath();

            if (path.length() > URL_PATH_MAX_LENGTH) {
                path = path.substring(0, URL_TRUNCATE_THRESHOLD) + "...";
            }

            String shortened = host + path;
            return shortened.length() > URL_DISPLAY_MAX_LENGTH ?
                    shortened.substring(0, URL_DISPLAY_MAX_LENGTH - 3) + "..." : shortened;

        } catch (MalformedURLException e) {
            return url.length() > URL_DISPLAY_MAX_LENGTH ?
                    url.substring(0, URL_DISPLAY_MAX_LENGTH - 3) + "..." : url;
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

    // Helper classes
    private static class SearchRequest {
        private final boolean valid;
        private final String errorMessage;
        private final String query;

        private SearchRequest(boolean valid, String errorMessage, String query) {
            this.valid = valid;
            this.errorMessage = errorMessage;
            this.query = query;
        }

        static SearchRequest valid(String query) {
            return new SearchRequest(true, null, query);
        }

        static SearchRequest invalid(String errorMessage) {
            return new SearchRequest(false, errorMessage, null);
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

    private static class InstantAnswer {
        private final String text;
        private final String url;
        private final String source;

        InstantAnswer(String text, String url, String source) {
            this.text = text != null ? text : "";
            this.url = url != null ? url : "";
            this.source = source != null ? source : "";
        }

        String getText() {
            return text;
        }

        String getUrl() {
            return url;
        }

        String getSource() {
            return source;
        }
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

        String getTitle() {
            return title;
        }

        String getDescription() {
            return description;
        }

        String getUrl() {
            return url;
        }
    }

    private static class SearchResultList {
        private final java.util.List<SearchResultItem> items = new java.util.ArrayList<>();

        void add(SearchResultItem item) {
            items.add(item);
        }

        SearchResultItem get(int index) {
            return items.get(index);
        }

        int size() {
            return items.size();
        }

        boolean isEmpty() {
            return items.isEmpty();
        }
    }

    private static class SearchResult {
        private final String query;
        private final InstantAnswer instantAnswer;
        private final SearchResultList searchResults;

        SearchResult(String query, InstantAnswer instantAnswer, SearchResultList searchResults) {
            this.query = query;
            this.instantAnswer = instantAnswer;
            this.searchResults = searchResults != null ? searchResults : new SearchResultList();
        }

        String getQuery() {
            return query;
        }

        InstantAnswer getInstantAnswer() {
            return instantAnswer;
        }

        SearchResultList getSearchResults() {
            return searchResults;
        }

        boolean hasInstantAnswer() {
            return instantAnswer != null;
        }

        int getResultCount() {
            return searchResults.size();
        }
    }

    // Custom exception
    private static class WebSearchException extends Exception {
        WebSearchException(String message) {
            super(message);
        }
    }
}


