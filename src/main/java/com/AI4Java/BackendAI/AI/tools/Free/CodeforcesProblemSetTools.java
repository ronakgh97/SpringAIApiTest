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

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@Service
public class CodeforcesProblemSetTools {

    private static final Logger logger = LoggerFactory.getLogger(CodeforcesProblemSetTools.class);

    // API Constants
    private static final String CODEFORCES_API_BASE = "https://codeforces.com/api";
    private static final String PROBLEMSET_ENDPOINT = "/problemset.problems";
    private static final String PROBLEM_BASE_URL = "https://codeforces.com/contest";

    // Rate limiting and timeouts
    private static final Duration CODEFORCES_RATE_LIMIT = Duration.ofSeconds(3);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);
    private static final int MAX_RETRY_ATTEMPTS = 3;

    // Result limits
    private static final int MAX_SEARCH_RESULTS = 15;
    private static final int MAX_MEMORY_SIZE = 8192 * 8192;

    // Cache settings
    private static final Duration CACHE_DURATION = Duration.ofMinutes(10);

    /**
     * Official Codeforces problem tags (case-insensitive matching)
     */
    private static final Set<String> VALID_TAGS = Set.of(
            "2-sat", "binary search", "bitmasks", "brute force", "chinese remainder theorem",
            "combinatorics", "constructive algorithms", "data structures", "dfs and similar",
            "divide and conquer", "dp", "dsu", "expression parsing", "fft", "flows", "games",
            "geometry", "graph matchings", "graphs", "greedy", "hashing", "implementation",
            "interactive", "math", "matrices", "meet-in-the-middle", "number theory",
            "probabilities", "schedules", "shortest paths", "sortings",
            "string suffix structures", "strings", "ternary search", "trees", "two pointers"
    );

    private WebClient webClient;
    private ObjectMapper objectMapper;
    private Map<String, CachedApiResponse> responseCache;
    private volatile long lastRequestTime = 0;

    @PostConstruct
    public void initialize() {
        logger.info("Initializing Codeforces Tools service");
        this.webClient = createWebClient();
        this.objectMapper = new ObjectMapper();
        this.responseCache = new ConcurrentHashMap<>();
        logger.info("Codeforces Tools service initialized successfully");
    }

    @PreDestroy
    public void cleanup() {
        logger.info("Shutting down Codeforces Tools service");
    }


    private WebClient createWebClient() {
        return WebClient.builder()
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(MAX_MEMORY_SIZE))
                .defaultHeader("User-Agent", "AI4Java-CodeforcesTool/1.0")
                .defaultHeader("Accept", "application/json")
                .build();
    }

    @Tool(name = "cf_problem_search",
            description = "Search Codeforces problems by tags and/or difficulty rating. " +
                    "Returns up to 20 matching problems with links and metadata.")
    public String cf_problem_search(
            @ToolParam(description = "Comma or semicolon separated tags (e.g., 'dp,graphs' or 'math;greedy')",
                    required = false) String tagsCsv,
            @ToolParam(description = "Maximum difficulty rating (e.g., 1600)",
                    required = false) Integer maxRating) {

        logger.debug("Codeforces problem search - tags: '{}', maxRating: {}", tagsCsv, maxRating);

        try {
            // Validate and normalize input
            SearchCriteria criteria = validateAndCreateSearchCriteria(tagsCsv, maxRating);
            if (!criteria.isValid()) {
                logger.warn("Invalid search criteria: {}", criteria.getErrorMessage());
                return "❌ " + criteria.getErrorMessage();
            }

            // Fetch and process results
            List<CodeforceProblem> problems = searchProblems(criteria);
            String formattedResults = formatSearchResults(problems);

            logger.info("Codeforces search completed - {} results returned for tags: [{}], maxRating: {}",
                    problems.size(), String.join(", ", criteria.getTags()), maxRating);

            return formattedResults;

        } catch (CodeforceApiException e) {
            logger.error("Codeforces API error during search: {}", e.getMessage());
            return "❌ " + e.getMessage();
        } catch (Exception e) {
            logger.error("Unexpected error during Codeforces problem search", e);
            return "❌ An unexpected error occurred while searching problems.";
        }
    }

    @Tool(name = "cf_problem_info",
            description = "Get detailed information about a specific Codeforces problem.")
    public String cf_problem_info(
            @ToolParam(description = "Contest ID (e.g., '1700')") String contestId,
            @ToolParam(description = "Problem index (e.g., 'A', 'B1')") String index) {

        logger.debug("Fetching Codeforces problem info - contest: {}, index: {}", contestId, index);

        // Validate input
        ProblemIdentifier problemId = validateProblemIdentifier(contestId, index);
        if (!problemId.isValid()) {
            logger.warn("Invalid problem identifier - contest: {}, index: {}", contestId, index);
            return "❌ " + problemId.getErrorMessage();
        }

        try {
            Optional<CodeforceProblem> problem = findSpecificProblem(problemId);

            if (problem.isPresent()) {
                logger.debug("Successfully found problem {}{}", contestId, index);
                return formatProblemInfo(problem.get());
            } else {
                logger.warn("Problem not found - contest: {}, index: {}", contestId, index);
                return String.format("❌ Problem %s%s not found.", contestId, index.toUpperCase());
            }

        } catch (CodeforceApiException e) {
            logger.error("Codeforces API error while fetching problem info: {}", e.getMessage());
            return "❌ " + e.getMessage();
        } catch (Exception e) {
            logger.error("Unexpected error while fetching problem info for {}{}", contestId, index, e);
            return "❌ An unexpected error occurred while retrieving problem information.";
        }
    }

    @Tool(name = "cf_list_tags",
            description = "List all valid Codeforces problem tags that can be used for searching.")
    public String cf_list_tags() {
        logger.debug("Listing Codeforces problem tags");

        List<String> sortedTags = VALID_TAGS.stream()
                .sorted()
                .collect(Collectors.toList());

        StringBuilder result = new StringBuilder("📋 **Valid Codeforces Tags** (").append(VALID_TAGS.size()).append(" total)\n\n");

        for (String tag : sortedTags) {
            result.append("• ").append(tag).append('\n');
        }

        result.append("\n💡 **Usage Tips:**\n")
                .append("• Tags are case-insensitive\n")
                .append("• Separate multiple tags with commas or semicolons\n")
                .append("• Example: `dp,graphs` or `math;greedy`");

        return result.toString();
    }

    // Validation and helper methods
    private SearchCriteria validateAndCreateSearchCriteria(String tagsCsv, Integer maxRating) {
        List<String> tags = normalizeTags(tagsCsv);

        // Validate tags
        for (String tag : tags) {
            if (!VALID_TAGS.contains(tag.toLowerCase())) {
                return SearchCriteria.invalid(String.format(
                        "Invalid tag '%s'. Use cf_list_tags to see all valid tags.", tag));
            }
        }

        // Validate rating
        if (maxRating != null && (maxRating < 800 || maxRating > 3500)) {
            return SearchCriteria.invalid("Rating must be between 800 and 3500.");
        }

        return SearchCriteria.valid(tags, maxRating);
    }

    private ProblemIdentifier validateProblemIdentifier(String contestId, String index) {
        if (contestId == null || contestId.trim().isEmpty()) {
            return ProblemIdentifier.invalid("Contest ID cannot be empty.");
        }

        if (index == null || index.trim().isEmpty()) {
            return ProblemIdentifier.invalid("Problem index cannot be empty.");
        }

        try {
            Integer.parseInt(contestId.trim());
        } catch (NumberFormatException e) {
            return ProblemIdentifier.invalid("Contest ID must be a valid number.");
        }

        return ProblemIdentifier.valid(contestId.trim(), index.trim().toUpperCase());
    }

    private List<CodeforceProblem> searchProblems(SearchCriteria criteria) throws CodeforceApiException, InterruptedException, JsonProcessingException {
        String apiUrl = buildApiUrl(criteria.getTags());
        JsonNode apiResponse = fetchApiResponse(apiUrl);

        JsonNode problems = apiResponse.path("result").path("problems");
        if (!problems.isArray()) {
            throw new CodeforceApiException("Invalid API response format.");
        }

        return StreamSupport.stream(problems.spliterator(), false)
                .map(this::parseCodeforceProblem)
                .filter(Objects::nonNull)
                .filter(problem -> criteria.getMaxRating() == null ||
                        problem.getRating() <= 0 ||
                        problem.getRating() <= criteria.getMaxRating())
                .limit(MAX_SEARCH_RESULTS)
                .collect(Collectors.toList());
    }

    private Optional<CodeforceProblem> findSpecificProblem(ProblemIdentifier problemId) throws CodeforceApiException, InterruptedException, JsonProcessingException {
        String apiUrl = CODEFORCES_API_BASE + PROBLEMSET_ENDPOINT;
        JsonNode apiResponse = fetchApiResponse(apiUrl);

        JsonNode problems = apiResponse.path("result").path("problems");
        if (!problems.isArray()) {
            throw new CodeforceApiException("Invalid API response format.");
        }

        return StreamSupport.stream(problems.spliterator(), false)
                .filter(p -> problemId.getContestId().equals(p.path("contestId").asText()) &&
                        problemId.getIndex().equalsIgnoreCase(p.path("index").asText()))
                .findFirst()
                .map(this::parseCodeforceProblem);
    }

    private JsonNode fetchApiResponse(String url) throws CodeforceApiException, InterruptedException, JsonProcessingException {
        try {
            // Simple rate limiting
            enforceRateLimit();

            // Check cache first
            CachedApiResponse cached = responseCache.get(url);
            if (cached != null && !cached.isExpired()) {
                logger.debug("Using cached response for URL: {}", url);
                return cached.getResponse();
            }

            logger.debug("Fetching from Codeforces API: {}", url);

            String responseBody = webClient.get()
                    .uri(url)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(REQUEST_TIMEOUT)
                    .retryWhen(Retry.fixedDelay(MAX_RETRY_ATTEMPTS, Duration.ofSeconds(1))
                            .filter(throwable -> throwable instanceof WebClientRequestException))
                    .block();

            if (responseBody == null || responseBody.trim().isEmpty()) {
                throw new CodeforceApiException("Empty response from Codeforces API.");
            }

            JsonNode jsonResponse = objectMapper.readTree(responseBody);

            if (!"OK".equals(jsonResponse.path("status").asText())) {
                String errorMessage = jsonResponse.path("comment").asText("Unknown API error");
                throw new CodeforceApiException("Codeforces API error: " + errorMessage);
            }

            // Cache the response
            responseCache.put(url, new CachedApiResponse(jsonResponse));

            return jsonResponse;

        } catch (WebClientResponseException e) {
            throw new CodeforceApiException("HTTP error " + e.getStatusCode() + ": " + e.getResponseBodyAsString());
        } catch (Exception e) {
            if (e instanceof CodeforceApiException) {
                throw e;
            }
            throw new CodeforceApiException("Failed to fetch data from Codeforces API: " + e.getMessage());
        }
    }

    private synchronized void enforceRateLimit() throws InterruptedException {
        long currentTime = System.currentTimeMillis();
        long timeSinceLastRequest = currentTime - lastRequestTime;

        if (timeSinceLastRequest < CODEFORCES_RATE_LIMIT.toMillis()) {
            long sleepTime = CODEFORCES_RATE_LIMIT.toMillis() - timeSinceLastRequest;
            logger.debug("Rate limiting: sleeping for {} ms", sleepTime);
            Thread.sleep(sleepTime);
        }

        lastRequestTime = System.currentTimeMillis();
    }

    private String buildApiUrl(List<String> tags) {
        String baseUrl = CODEFORCES_API_BASE + PROBLEMSET_ENDPOINT;

        if (tags.isEmpty()) {
            return baseUrl;
        }

        String tagString = String.join(";", tags);

        return baseUrl + "?tags=" + tagString;
    }

    private CodeforceProblem parseCodeforceProblem(JsonNode problemNode) {
        try {
            String contestId = problemNode.path("contestId").asText();
            String index = problemNode.path("index").asText();
            String name = problemNode.path("name").asText();
            int rating = problemNode.path("rating").asInt(-1);

            List<String> tags = StreamSupport.stream(problemNode.path("tags").spliterator(), false)
                    .map(JsonNode::asText)
                    .collect(Collectors.toList());

            return new CodeforceProblem(contestId, index, name, rating, tags);
        } catch (Exception e) {
            logger.warn("Failed to parse problem from JSON node", e);
            return null;
        }
    }

    private List<String> normalizeTags(String tagsCsv) {
        if (tagsCsv == null || tagsCsv.trim().isEmpty()) {
            return Collections.emptyList();
        }

        return Arrays.stream(tagsCsv.split("[,;]"))
                .map(String::trim)
                .map(String::toLowerCase)
                .filter(tag -> !tag.isEmpty())
                .distinct()
                .collect(Collectors.toList());
    }

    private String formatSearchResults(List<CodeforceProblem> problems) {
        if (problems.isEmpty()) {
            return "📚 No problems found matching your criteria.";
        }

        StringBuilder result = new StringBuilder("📚 **Codeforces Problem Search Results**\n\n");

        for (int i = 0; i < problems.size(); i++) {
            CodeforceProblem problem = problems.get(i);
            result.append(i + 1).append(". **[").append(problem.getContestId())
                    .append(problem.getIndex()).append("] ").append(problem.getName()).append("**");

            if (problem.getRating() > 0) {
                result.append(" • Rating: ").append(problem.getRating());
            }

            result.append("\n   📝 Tags: ").append(String.join(", ", problem.getTags()))
                    .append("\n   🔗 ").append(problem.getUrl()).append("\n\n");
        }

        return result.toString();
    }

    private String formatProblemInfo(CodeforceProblem problem) {
        StringBuilder info = new StringBuilder();
        info.append("📄 **[").append(problem.getContestId()).append(problem.getIndex())
                .append("] ").append(problem.getName()).append("**\n");

        if (problem.getRating() > 0) {
            info.append("🎯 **Rating:** ").append(problem.getRating()).append("\n");
        }

        info.append("📝 **Tags:** ").append(String.join(", ", problem.getTags())).append("\n");
        info.append("🔗 **Link:** ").append(problem.getUrl());

        return info.toString();
    }

    // Helper classes
    private static class SearchCriteria {
        private final boolean valid;
        private final String errorMessage;
        private final List<String> tags;
        private final Integer maxRating;

        private SearchCriteria(boolean valid, String errorMessage, List<String> tags, Integer maxRating) {
            this.valid = valid;
            this.errorMessage = errorMessage;
            this.tags = tags != null ? tags : Collections.emptyList();
            this.maxRating = maxRating;
        }

        static SearchCriteria valid(List<String> tags, Integer maxRating) {
            return new SearchCriteria(true, null, tags, maxRating);
        }

        static SearchCriteria invalid(String errorMessage) {
            return new SearchCriteria(false, errorMessage, null, null);
        }

        boolean isValid() {
            return valid;
        }

        String getErrorMessage() {
            return errorMessage;
        }

        List<String> getTags() {
            return tags;
        }

        Integer getMaxRating() {
            return maxRating;
        }
    }

    private static class ProblemIdentifier {
        private final boolean valid;
        private final String errorMessage;
        private final String contestId;
        private final String index;

        private ProblemIdentifier(boolean valid, String errorMessage, String contestId, String index) {
            this.valid = valid;
            this.errorMessage = errorMessage;
            this.contestId = contestId;
            this.index = index;
        }

        static ProblemIdentifier valid(String contestId, String index) {
            return new ProblemIdentifier(true, null, contestId, index);
        }

        static ProblemIdentifier invalid(String errorMessage) {
            return new ProblemIdentifier(false, errorMessage, null, null);
        }

        boolean isValid() {
            return valid;
        }

        String getErrorMessage() {
            return errorMessage;
        }

        String getContestId() {
            return contestId;
        }

        String getIndex() {
            return index;
        }
    }

    private static class CodeforceProblem {
        private final String contestId;
        private final String index;
        private final String name;
        private final int rating;
        private final List<String> tags;

        CodeforceProblem(String contestId, String index, String name, int rating, List<String> tags) {
            this.contestId = contestId;
            this.index = index;
            this.name = name;
            this.rating = rating;
            this.tags = tags != null ? tags : Collections.emptyList();
        }

        String getContestId() {
            return contestId;
        }

        String getIndex() {
            return index;
        }

        String getName() {
            return name;
        }

        int getRating() {
            return rating;
        }

        List<String> getTags() {
            return tags;
        }

        String getUrl() {
            return PROBLEM_BASE_URL + "/" + contestId + "/problem/" + index;
        }
    }

    private static class CachedApiResponse {
        private final JsonNode response;
        private final long timestamp;

        CachedApiResponse(JsonNode response) {
            this.response = response;
            this.timestamp = System.currentTimeMillis();
        }

        JsonNode getResponse() {
            return response;
        }

        boolean isExpired() {
            return System.currentTimeMillis() - timestamp > CACHE_DURATION.toMillis();
        }
    }

    private static class CodeforceApiException extends Exception {
        CodeforceApiException(String message) {
            super(message);
        }
    }
}





