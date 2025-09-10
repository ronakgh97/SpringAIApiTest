package com.AI4Java.BackendAI.AI.tools.WebSearch;

import jakarta.annotation.PreDestroy;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.stereotype.Service;

import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class SeleniumBrowserSearchTools implements ApplicationListener<ContextRefreshedEvent> {

    private static final Logger logger = LoggerFactory.getLogger(SeleniumBrowserSearchTools.class);

    // Browser Configuration
    private static final int VIEWPORT_WIDTH = 1920;
    private static final int VIEWPORT_HEIGHT = 1080;
    private static final boolean HEADLESS_MODE = true;

    // Timeout Configuration
    private static final Duration DEFAULT_WAIT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration PAGE_LOAD_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration IMPLICIT_WAIT_TIMEOUT = Duration.ofSeconds(5);

    // Search Configuration
    private static final int MAX_DUCKDUCKGO_RESULTS = 6;
    private static final int MAX_BING_RESULTS = 5;
    private static final int DUCKDUCKGO_SNIPPET_LENGTH = 1500;
    private static final int BING_SNIPPET_LENGTH = 1225;
    private static final int URL_MAX_DISPLAY_LENGTH = 100;
    private static final int URL_SUFFIX_LENGTH = 100;
    private static final int TEXT_TRUNCATE_THRESHOLD = 1200;

    // Wait Time Configuration
    private static final int BASE_HUMAN_DELAY_MS = 1000;
    private static final int MAX_RANDOM_DELAY_MS = 2000;
    private static final int BING_BASE_DELAY_MS = 1500;
    private static final int BING_MAX_RANDOM_DELAY_MS = 1000;

    // Search Engine Configuration Map
    private static final Map<String, SearchEngineConfig> SEARCH_ENGINES = Map.of(
            "duckduckgo", new SearchEngineConfig(
                    "DuckDuckGo",
                    "https://duckduckgo.com/?q=%s&t=h_&ia=web",
                    "[data-testid='result']",
                    "[data-testid='result-title-a']",
                    "[data-testid='result-snippet']",
                    MAX_DUCKDUCKGO_RESULTS,
                    DUCKDUCKGO_SNIPPET_LENGTH,
                    BASE_HUMAN_DELAY_MS,
                    MAX_RANDOM_DELAY_MS,
                    "🦆"
            ),
            "bing", new SearchEngineConfig(
                    "Bing",
                    "https://www.bing.com/search?q=%s",
                    ".b_algo",
                    "h2 a",
                    ".b_caption p, .b_dList",
                    MAX_BING_RESULTS,
                    BING_SNIPPET_LENGTH,
                    BING_BASE_DELAY_MS,
                    BING_MAX_RANDOM_DELAY_MS,
                    "🔍"
            )
    );

    // Default search engine order
    private static final List<String> DEFAULT_SEARCH_ORDER = List.of("duckduckgo", "bing");

    // User Agents Pool
    private static final List<String> USER_AGENTS = List.of(
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:109.0) Gecko/20100101 Firefox/115.0",
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10.15; rv:109.0) Gecko/20100101 Firefox/115.0"
    );

    // Chrome Options
    private static final List<String> CHROME_ARGS = List.of(
            "--no-sandbox",
            "--disable-dev-shm-usage",
            "--disable-gpu",
            "--window-size=" + VIEWPORT_WIDTH + "," + VIEWPORT_HEIGHT,
            "--disable-blink-features=AutomationControlled",
            "--disable-extensions",
            "--no-first-run",
            "--disable-default-apps",
            "--disable-infobars",
            "--disable-notifications",
            "--disable-popup-blocking",
            "--disable-translate",
            "--disable-background-timer-throttling",
            "--disable-renderer-backgrounding",
            "--disable-backgrounding-occluded-windows",
            "--disable-default-apps",
            "--disable-dev-shm-usage",
            "--disable-extensions-file-access-check",
            "--disable-extensions-http-throttling",
            "--disable-extensions-http-throttling",
            "--memory-pressure-off",
            "--max_old_space_size=4096"
    );

    private static final List<String> EXCLUDE_SWITCHES = List.of("enable-automation");

    // JavaScript for stealth
    private static final String STEALTH_SCRIPT =
            "Object.defineProperty(navigator, 'webdriver', {get: () => undefined}); " +
                    "Object.defineProperty(navigator, 'plugins', {get: () => Array.from({length: 5}, () => ({}))}); " +
                    "Object.defineProperty(navigator, 'languages', {get: () => ['en-US', 'en']}); " +
                    "window.chrome = {runtime: {}, loadTimes: () => ({}), csi: () => ({})};";

    // Instance variables
    private volatile WebDriver driver;
    private final SecureRandom random = new SecureRandom();
    private final AtomicLong searchCount = new AtomicLong(0);

    @Override
    public void onApplicationEvent(ContextRefreshedEvent event) {
        CompletableFuture.runAsync(this::initializeDriverAsync);
    }

    public void initializeDriverAsync() {
        logger.info("Initializing Selenium browser search tool asynchronously...");
        try {
            setupChromeDriver();
            logger.info("Selenium browser search tool initialized successfully. Available engines: {}",
                    String.join(", ", SEARCH_ENGINES.keySet()));
        } catch (Exception e) {
            logger.error("Asynchronous Selenium browser initialization failed.", e);
        }
    }

    @PreDestroy
    public void cleanupDriver() {
        logger.info("Shutting down Selenium browser search tool");
        try {
            if (driver != null) {
                driver.quit();
                logger.info("Selenium driver cleaned up successfully. Total searches: {}", searchCount.get());
            }
        } catch (Exception e) {
            logger.error("Error during Selenium driver cleanup", e);
        }
    }

    @Tool(name = "browser_search_selenium",
            description = "Performs comprehensive web search using browser automation with anti-detection measures. " +
                    "Supports multiple engines (DuckDuckGo and Bing) with engine preference and automatic fallback.")
    public String browserSearch(
            @ToolParam(description = "Search query") String query,
            @ToolParam(description = "Preferred search engine: 'duckduckgo' or 'bing' (optional)", required = false) String engine) {

        if (driver == null) {
            logger.warn("Selenium driver is not yet initialized. Please try again in a moment.");
            return "⏳ The browser is warming up. Please try again in a few moments.";
        }

        long searchId = searchCount.incrementAndGet();
        logger.debug("Starting browser search #{} for query: '{}' with engine preference: '{}'",
                searchId, query, engine);

        // Validate input
        SearchRequest request = validateSearchRequest(query, engine);
        if (!request.isValid()) {
            logger.warn("Invalid search request #{}: {}", searchId, request.getErrorMessage());
            return "❌ " + request.getErrorMessage();
        }

        try {
            String result = searchWithMultipleEngines(request, searchId);

            logger.info("Browser search #{} completed successfully", searchId);
            return result;

        } catch (SearchException e) {
            logger.error("Browser search #{} failed: {}", searchId, e.getMessage());
            return "❌ " + e.getMessage();
        } catch (Exception e) {
            logger.error("Unexpected error during browser search #{}", searchId, e);
            return "❌ An unexpected error occurred during browser search. Please try again.";
        }
    }

    private SearchRequest validateSearchRequest(String query, String engine) {
        if (query == null || query.trim().isEmpty()) {
            return SearchRequest.invalid("Search query cannot be empty.");
        }

        String trimmedQuery = query.trim();
        if (trimmedQuery.length() > 200) {
            return SearchRequest.invalid("Search query is too long (maximum 200 characters).");
        }

        String preferredEngine = null;
        if (engine != null && !engine.trim().isEmpty()) {
            String normalizedEngine = engine.trim().toLowerCase();
            if (!SEARCH_ENGINES.containsKey(normalizedEngine)) {
                return SearchRequest.invalid("Invalid search engine. Available options: " +
                        String.join(", ", SEARCH_ENGINES.keySet()));
            }
            preferredEngine = normalizedEngine;
        }

        return SearchRequest.valid(trimmedQuery, preferredEngine);
    }

    private String searchWithMultipleEngines(SearchRequest request, long searchId) throws SearchException {
        List<String> engineOrder = determineSearchOrder(request.getPreferredEngine());
        StringBuilder allResults = new StringBuilder();

        for (String engineKey : engineOrder) {
            SearchEngineConfig config = SEARCH_ENGINES.get(engineKey);
            if (config == null) continue;

            try {
                logger.debug("Attempting search #{} with {} engine", searchId, config.name);
                SearchEngineResult result = performSearchWithEngine(request.getQuery(), config, searchId);

                if (result.hasResults()) {
                    allResults.append(result.getFormattedResults()).append(" ");
                    logger.info("Search #{} successful with {} engine - {} results",
                            searchId, config.name, result.getResultCount());

                    // If we have results from preferred engine or DuckDuckGo, we can stop
                    if (request.getPreferredEngine() != null || "duckduckgo".equals(engineKey)) {
                        break;
                    }
                } else {
                    logger.debug("Search #{} with {} engine returned no results", searchId, config.name);
                }

            } catch (Exception e) {
                logger.warn("Search #{} error with {} engine: {}", searchId, config.name, e.getMessage());
                continue;
            }
        }

        return allResults.length() > 0 ? allResults.toString() :
                "❌ No results found from any search engine for: " + request.getQuery();
    }

    private List<String> determineSearchOrder(String preferredEngine) {
        List<String> order = new ArrayList<>();

        // Add preferred engine first if specified
        if (preferredEngine != null) {
            order.add(preferredEngine);
        }

        // Add remaining engines from default order
        for (String engine : DEFAULT_SEARCH_ORDER) {
            if (!order.contains(engine)) {
                order.add(engine);
            }
        }

        return order;
    }

    private SearchEngineResult performSearchWithEngine(String query, SearchEngineConfig config, long searchId)
            throws SearchException {
        try {
            logger.debug("Starting {} search #{}", config.name, searchId);

            String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);
            String searchUrl = String.format(config.urlTemplate, encodedQuery);

            driver.get(searchUrl);

            // Wait for results to load
            WebDriverWait wait = new WebDriverWait(driver, DEFAULT_WAIT_TIMEOUT);
            wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector(config.resultSelector)));

            // Human-like delay
            humanDelay(config.baseDelayMs, config.maxRandomDelayMs);

            List<WebElement> results = driver.findElements(By.cssSelector(config.resultSelector));
            List<SearchResultItem> items = extractSearchResults(results, config);

            return new SearchEngineResult(config.name, query, items, config);

        } catch (TimeoutException e) {
            throw new SearchException(config.name + " search timed out - page may be blocked");
        } catch (Exception e) {
            logger.warn("{} search #{} failed: {}", config.name, searchId, e.getMessage());
            return SearchEngineResult.empty(config.name, query);
        }
    }

    private List<SearchResultItem> extractSearchResults(List<WebElement> results, SearchEngineConfig config) {
        List<SearchResultItem> items = new ArrayList<>();

        for (int i = 0; i < Math.min(results.size(), config.maxResults); i++) {
            try {
                WebElement result = results.get(i);

                WebElement titleElement = result.findElement(By.cssSelector(config.titleSelector));
                WebElement snippetElement = result.findElement(By.cssSelector(config.snippetSelector));

                String title = titleElement.getText().trim();
                String link = titleElement.getAttribute("href");
                String snippet = snippetElement.getText().trim();

                if (!title.isEmpty() && !link.isEmpty()) {
                    items.add(new SearchResultItem(title, link, snippet));
                }

            } catch (Exception e) {
                logger.debug("Failed to extract {} result {}: {}", config.name, i + 1, e.getMessage());
                continue;
            }
        }

        return items;
    }

    private void setupChromeDriver() throws SeleniumInitializationException {
        try {
            ChromeOptions options = createChromeOptions();
            driver = new ChromeDriver(options);

            // Set timeouts
            driver.manage().timeouts().pageLoadTimeout(PAGE_LOAD_TIMEOUT);
            driver.manage().timeouts().implicitlyWait(IMPLICIT_WAIT_TIMEOUT);

            // Execute stealth script
            ((JavascriptExecutor) driver).executeScript(STEALTH_SCRIPT);

        } catch (Exception e) {
            throw new SeleniumInitializationException("Failed to setup Chrome driver: " + e.getMessage(), e);
        }
    }

    private ChromeOptions createChromeOptions() {
        ChromeOptions options = new ChromeOptions();

        // Add arguments
        for (String arg : CHROME_ARGS) {
            options.addArguments(arg);
        }

        // Add headless mode if enabled
        if (HEADLESS_MODE) {
            options.addArguments("--headless=new");
        }

        // Add random user agent
        String userAgent = getRandomUserAgent();
        options.addArguments("--user-agent=" + userAgent);

        // Experimental options
        options.setExperimentalOption("excludeSwitches", EXCLUDE_SWITCHES);
        options.setExperimentalOption("useAutomationExtension", false);

        // Preferences
        Map<String, Object> prefs = new HashMap<>();
        prefs.put("profile.default_content_setting_values.notifications", 2);
        prefs.put("profile.managed_default_content_settings.images", 2); // Disable images for speed
        prefs.put("profile.default_content_settings.popups", 0);
        options.setExperimentalOption("prefs", prefs);

        return options;
    }

    private String getRandomUserAgent() {
        String selectedAgent = USER_AGENTS.get(random.nextInt(USER_AGENTS.size()));
        logger.debug("Selected user agent: {}...", selectedAgent.substring(0, Math.min(50, selectedAgent.length())));
        return selectedAgent;
    }

    private void humanDelay(int baseDelayMs, int maxRandomDelayMs) throws InterruptedException {
        int delay = baseDelayMs + random.nextInt(maxRandomDelayMs);
        Thread.sleep(delay);
    }

    private static String truncateText(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) {
            return text;
        }

        int lastSpace = text.lastIndexOf(' ', maxLength);
        if (lastSpace > maxLength - TEXT_TRUNCATE_THRESHOLD) {
            return text.substring(0, lastSpace) + "...";
        }
        return text.substring(0, maxLength) + "...";
    }

    private static String shortenUrl(String url) {
        if (url == null || url.length() <= URL_MAX_DISPLAY_LENGTH) {
            return url;
        }

        try {
            URL parsedUrl = new URL(url);
            return parsedUrl.getHost() + "..." + url.substring(url.length() - URL_SUFFIX_LENGTH);
        } catch (MalformedURLException e) {
            return url.substring(0, URL_MAX_DISPLAY_LENGTH) + "...";
        }
    }

    // Helper classes
    private static class SearchEngineConfig {
        final String name;
        final String urlTemplate;
        final String resultSelector;
        final String titleSelector;
        final String snippetSelector;
        final int maxResults;
        final int snippetLength;
        final int baseDelayMs;
        final int maxRandomDelayMs;
        final String emoji;

        SearchEngineConfig(String name, String urlTemplate, String resultSelector,
                           String titleSelector, String snippetSelector, int maxResults,
                           int snippetLength, int baseDelayMs, int maxRandomDelayMs, String emoji) {
            this.name = name;
            this.urlTemplate = urlTemplate;
            this.resultSelector = resultSelector;
            this.titleSelector = titleSelector;
            this.snippetSelector = snippetSelector;
            this.maxResults = maxResults;
            this.snippetLength = snippetLength;
            this.baseDelayMs = baseDelayMs;
            this.maxRandomDelayMs = maxRandomDelayMs;
            this.emoji = emoji;
        }
    }

    private static class SearchRequest {
        private final boolean valid;
        private final String errorMessage;
        private final String query;
        private final String preferredEngine;

        private SearchRequest(boolean valid, String errorMessage, String query, String preferredEngine) {
            this.valid = valid;
            this.errorMessage = errorMessage;
            this.query = query;
            this.preferredEngine = preferredEngine;
        }

        static SearchRequest valid(String query, String preferredEngine) {
            return new SearchRequest(true, null, query, preferredEngine);
        }

        static SearchRequest invalid(String errorMessage) {
            return new SearchRequest(false, errorMessage, null, null);
        }

        boolean isValid() { return valid; }
        String getErrorMessage() { return errorMessage; }
        String getQuery() { return query; }
        String getPreferredEngine() { return preferredEngine; }
    }

    private static class SearchResultItem {
        final String title;
        final String link;
        final String snippet;

        SearchResultItem(String title, String link, String snippet) {
            this.title = title;
            this.link = link;
            this.snippet = snippet;
        }
    }

    private static class SearchEngineResult {
        private final String engineName;
        private final String query;
        private final List<SearchResultItem> items;
        private final SearchEngineConfig config;

        SearchEngineResult(String engineName, String query, List<SearchResultItem> items, SearchEngineConfig config) {
            this.engineName = engineName;
            this.query = query;
            this.items = items != null ? items : Collections.emptyList();
            this.config = config;
        }

        static SearchEngineResult empty(String engineName, String query) {
            return new SearchEngineResult(engineName, query, Collections.emptyList(), null);
        }

        boolean hasResults() {
            return !items.isEmpty();
        }

        int getResultCount() {
            return items.size();
        }

        String getFormattedResults() {
            if (items.isEmpty()) {
                return "";
            }

            StringBuilder result = new StringBuilder();
            result.append(config.emoji).append(" **").append(engineName).append(" Results for: ")
                    .append(query).append("** ");

            for (int i = 0; i < items.size(); i++) {
                SearchResultItem item = items.get(i);
                result.append("**").append(i + 1).append(". ").append(item.title).append("** ");

                if (!item.snippet.isEmpty()) {
                    result.append("📝 ").append(truncateText(item.snippet, config.snippetLength)).append(" ");
                }

                result.append("🔗 ").append(shortenUrl(item.link)).append(" ");
            }

            return result.toString();
        }
    }

    // Custom exceptions
    private static class SeleniumInitializationException extends RuntimeException {
        SeleniumInitializationException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    private static class SearchException extends Exception {
        SearchException(String message) {
            super(message);
        }
    }
}




