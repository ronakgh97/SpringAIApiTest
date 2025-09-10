package com.AI4Java.BackendAI.AI.tools.WebSearch;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
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
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class SeleniumWebScraperTools implements ApplicationListener<ContextRefreshedEvent> {

    private static final Logger logger = LoggerFactory.getLogger(SeleniumWebScraperTools.class);

    // Browser Configuration
    private static final int VIEWPORT_WIDTH = 1920;
    private static final int VIEWPORT_HEIGHT = 1080;
    private static final boolean HEADLESS_MODE = true;

    // Timeout Configuration
    private static final Duration PAGE_LOAD_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration IMPLICIT_WAIT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration EXPLICIT_WAIT_TIMEOUT = Duration.ofSeconds(15);
    private static final int DYNAMIC_CONTENT_WAIT_MS = 2000;

    // Content Configuration
    private static final int MAX_CONTENT_LENGTH = 2000;
    private static final int MAX_STRUCTURED_ELEMENTS = 20;
    private static final int ELEMENT_TEXT_PREVIEW_LENGTH = 200;
    private static final int CONTENT_TRUNCATE_THRESHOLD = 1800;
    private static final int MAX_TABLE_PREVIEW_ROWS = 3;
    private static final int MAX_HEADING_PREVIEW = 5;

    // CSS Selectors
    private static final String[] CONTENT_SELECTORS = {
            "main", "article", ".content", ".post", ".entry",
            ".main-content", "#main", "#content", ".post-content",
            ".entry-content", ".article-content"
    };

    private static final String REMOVE_ELEMENTS_SELECTOR =
            "script, style, nav, header, footer, aside, .advertisement, .ads";

    private static final String META_DESCRIPTION_SELECTOR =
            "meta[name=description], meta[property='og:description']";

    private static final String AUTHOR_SELECTOR =
            "meta[name=author], .author, .byline, meta[property='article:author']";

    private static final String DATE_SELECTOR =
            "time, .date, .published, meta[property='article:published_time']";

    // Structured data selectors
    private static final String TABLE_SELECTOR = "table";
    private static final String LIST_SELECTOR = "ul, ol";
    private static final String LINK_SELECTOR = "a[href]";
    private static final String IMAGE_SELECTOR = "img[src]";
    private static final String HEADING_SELECTOR = "h1, h2, h3, h4, h5, h6";

    // User Agents Pool
    private static final List<String> USER_AGENTS = List.of(
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36",
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.114 Safari/537.36",
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.101 Safari/537.36",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:109.0) Gecko/20100101 Firefox/115.0",
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10.15; rv:109.0) Gecko/20100101 Firefox/115.0"
    );

    // Chrome Options
    private static final List<String> CHROME_ARGS = List.of(
            "--disable-gpu",
            "--window-size=" + VIEWPORT_WIDTH + "," + VIEWPORT_HEIGHT,
            "--blink-settings=imagesEnabled=false",
            "--disable-dev-shm-usage",
            "--no-sandbox",
            "--disable-extensions",
            "--disable-logging",
            "--disable-notifications",
            "--disable-popup-blocking",
            "--disable-translate",
            "--disable-features=VizDisplayCompositor",
            "--disable-blink-features=AutomationControlled",
            "--memory-pressure-off",
            "--disable-background-networking",
            "--disable-client-side-phishing-detection"
    );

    private static final List<String> EXCLUDE_SWITCHES = List.of("enable-automation");

    // Stealth Scripts
    private static final String[] STEALTH_SCRIPTS = {
            "Object.defineProperty(navigator, 'webdriver', {get: () => undefined})",
            "Object.defineProperty(navigator, 'plugins', {get: () => Array.from({length: 5}, () => ({}))})",
            "Object.defineProperty(navigator, 'languages', {get: () => ['en-US', 'en']})",
            "window.chrome = {runtime: {}, loadTimes: () => ({}), csi: () => ({})}"
    };

    // Instance variables
    private final SecureRandom random = new SecureRandom();
    private final AtomicLong scrapeCount = new AtomicLong(0);

    @Override
    public void onApplicationEvent(ContextRefreshedEvent event) {
        CompletableFuture.runAsync(this::initialize);
    }

    public void initialize() {
        logger.info("Initializing Selenium web scraper tool asynchronously...");
        // Initialization logic if needed (e.g., driver pool setup)
        logger.info("Selenium web scraper tool initialized successfully");
    }

    @PreDestroy
    public void cleanup() {
        logger.info("Shutting down Selenium web scraper tool");

        // Cleanup logic if needed
        logger.info("Selenium web scraper cleaned up successfully. Total scrapes: {}", scrapeCount.get());
    }

    @Tool(name = "scrape_webpage_selenium",
            description = "Scrapes and extracts content from any webpage URL using Selenium browser automation. " +
                    "Returns clean text content, title, and key metadata information.")
    public String scrape_webpage(@ToolParam(description = "Full http/https URL to scrape") String url) {
        long scrapeId = scrapeCount.incrementAndGet();
        logger.debug("Starting webpage scrape #{} for URL: {}", scrapeId, url);

        // Validate URL
        UrlValidationResult validation = validateUrl(url);
        if (!validation.isValid()) {
            logger.warn("Invalid URL for scrape #{}: {}", scrapeId, validation.getErrorMessage());
            return "❌ " + validation.getErrorMessage();
        }

        try {
            String html = fetchPageContent(validation.getCleanUrl(), scrapeId);
            String result = parseWebpageContent(html, validation.getCleanUrl());

            logger.info("Webpage scrape #{} completed successfully for domain: {}",
                    scrapeId, extractDomain(validation.getCleanUrl()));
            return result;

        } catch (ScrapingException e) {
            logger.error("Webpage scrape #{} failed: {}", scrapeId, e.getMessage());
            return "❌ " + e.getMessage();
        } catch (Exception e) {
            logger.error("Unexpected error during webpage scrape #{}", scrapeId, e);
            return "❌ An unexpected error occurred while scraping the webpage.";
        }
    }

    @Tool(name = "extract_structured_data_selenium",
            description = "Extracts structured data from a webpage like tables, lists, or specific elements. " +
                    "Optionally accepts a CSS selector for targeting specific elements.")
    public String extract_structured_data(
            @ToolParam(description = "Full http/https URL") String url,
            @ToolParam(description = "CSS selector for specific elements (optional)", required = false) String selector) {

        long scrapeId = scrapeCount.incrementAndGet();
        logger.debug("Starting structured data extraction #{} for URL: {} with selector: '{}'",
                scrapeId, url, selector);

        // Validate URL
        UrlValidationResult validation = validateUrl(url);
        if (!validation.isValid()) {
            logger.warn("Invalid URL for structured data extraction #{}: {}", scrapeId, validation.getErrorMessage());
            return "❌ " + validation.getErrorMessage();
        }

        try {
            String html = fetchPageContent(validation.getCleanUrl(), scrapeId);
            String result = extractStructuredData(html, selector, validation.getCleanUrl());

            logger.info("Structured data extraction #{} completed successfully", scrapeId);
            return result;

        } catch (ScrapingException e) {
            logger.error("Structured data extraction #{} failed: {}", scrapeId, e.getMessage());
            return "❌ " + e.getMessage();
        } catch (Exception e) {
            logger.error("Unexpected error during structured data extraction #{}", scrapeId, e);
            return "❌ An unexpected error occurred while extracting structured data.";
        }
    }

    @Tool(name = "monitor_webpage_changes_selenium",
            description = "Generates a content hash for webpage monitoring. " +
                    "Useful for detecting changes by comparing hashes over time.")
    public String monitor_webpage_changes(@ToolParam(description = "Full http/https URL to monitor") String url) {
        long scrapeId = scrapeCount.incrementAndGet();
        logger.debug("Starting webpage monitoring #{} for URL: {}", scrapeId, url);

        // Validate URL
        UrlValidationResult validation = validateUrl(url);
        if (!validation.isValid()) {
            logger.warn("Invalid URL for webpage monitoring #{}: {}", scrapeId, validation.getErrorMessage());
            return "❌ " + validation.getErrorMessage();
        }

        try {
            String content = scrape_webpage(validation.getCleanUrl());
            String contentHash = generateContentHash(content);
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

            logger.info("Webpage monitoring #{} completed for domain: {}", scrapeId, extractDomain(validation.getCleanUrl()));

            return String.format("""
                📊 **Webpage Monitoring Report**
                
                🔗 **URL:** %s
                🌐 **Domain:** %s
                🔍 **Content Hash:** %s
                📅 **Timestamp:** %s
                
                💡 **Usage:** Store this hash to compare against future checks for change detection.
                📋 **Tip:** Run this tool periodically and compare hashes to detect content changes.
                """, validation.getCleanUrl(), extractDomain(validation.getCleanUrl()),
                    contentHash.substring(0, 16), timestamp);

        } catch (Exception e) {
            logger.error("Webpage monitoring #{} failed: {}", scrapeId, e.getMessage());
            return "❌ Failed to monitor webpage changes: " + e.getMessage();
        }
    }

    private String fetchPageContent(String url, long scrapeId) throws ScrapingException {
        WebDriver driver = null;
        try {
            ChromeOptions options = createChromeOptions();
            driver = new ChromeDriver(options);

            configureTimeouts(driver);

            logger.debug("Navigating to URL for scrape #{}: {}", scrapeId, url);
            driver.get(url);

            // Optional cookie addition (non-critical)
            addSessionCookie(driver, scrapeId);

            // Apply stealth techniques
            applyStealthTechniques(driver, scrapeId);

            // Wait for page to be fully loaded
            waitForPageLoad(driver, scrapeId);

            // Additional wait for dynamic content
            Thread.sleep(DYNAMIC_CONTENT_WAIT_MS);

            String pageSource = driver.getPageSource();
            logger.debug("Successfully fetched page source for scrape #{}, length: {}", scrapeId, pageSource.length());

            return pageSource;

        } catch (TimeoutException e) {
            throw new ScrapingException("Page load timeout: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ScrapingException("Scraping interrupted: " + e.getMessage());
        } catch (Exception e) {
            throw new ScrapingException("Failed to fetch page content: " + e.getMessage());
        } finally {
            if (driver != null) {
                try {
                    driver.quit();
                } catch (Exception e) {
                    logger.warn("Error closing driver for scrape #{}: {}", scrapeId, e.getMessage());
                }
            }
        }
    }

    private ChromeOptions createChromeOptions() {
        ChromeOptions options = new ChromeOptions();

        // Add headless mode
        if (HEADLESS_MODE) {
            options.addArguments("--headless=new");
        }

        // Add all arguments
        for (String arg : CHROME_ARGS) {
            options.addArguments(arg);
        }

        // Add random user agent
        String userAgent = getRandomUserAgent();
        options.addArguments("--user-agent=" + userAgent);

        // Experimental options
        options.setExperimentalOption("useAutomationExtension", false);
        options.setExperimentalOption("excludeSwitches", EXCLUDE_SWITCHES);

        return options;
    }

    private void configureTimeouts(WebDriver driver) {
        driver.manage().timeouts().pageLoadTimeout(PAGE_LOAD_TIMEOUT);
        driver.manage().timeouts().implicitlyWait(IMPLICIT_WAIT_TIMEOUT);
    }

    private void addSessionCookie(WebDriver driver, long scrapeId) {
        try {
            Cookie sessionCookie = new Cookie("session-id", "scraper-session-" + System.currentTimeMillis());
            driver.manage().addCookie(sessionCookie);
            logger.debug("Session cookie added for scrape #{}", scrapeId);
        } catch (Exception e) {
            logger.debug("Could not add session cookie for scrape #{} (normal): {}", scrapeId, e.getMessage());
        }
    }

    private void applyStealthTechniques(WebDriver driver, long scrapeId) {
        try {
            JavascriptExecutor js = (JavascriptExecutor) driver;
            for (String script : STEALTH_SCRIPTS) {
                js.executeScript(script);
            }
            logger.debug("Stealth techniques applied for scrape #{}", scrapeId);
        } catch (Exception e) {
            logger.debug("Could not apply stealth techniques for scrape #{}: {}", scrapeId, e.getMessage());
        }
    }

    private void waitForPageLoad(WebDriver driver, long scrapeId) {
        try {
            WebDriverWait wait = new WebDriverWait(driver, EXPLICIT_WAIT_TIMEOUT);
            wait.until(webDriver -> ((JavascriptExecutor) webDriver)
                    .executeScript("return document.readyState").equals("complete"));
            logger.debug("Page fully loaded for scrape #{}", scrapeId);
        } catch (TimeoutException e) {
            logger.warn("Page load wait timeout for scrape #{}: {}", scrapeId, e.getMessage());
            // Continue anyway - partial content may still be useful
        }
    }

    private UrlValidationResult validateUrl(String url) {
        if (url == null || url.trim().isEmpty()) {
            return UrlValidationResult.invalid("URL cannot be empty.");
        }

        try {
            URL parsedUrl = new URL(url.trim());
            String protocol = parsedUrl.getProtocol().toLowerCase();

            if (!"http".equals(protocol) && !"https".equals(protocol)) {
                return UrlValidationResult.invalid("URL must use HTTP or HTTPS protocol.");
            }

            if (parsedUrl.getHost() == null || parsedUrl.getHost().trim().isEmpty()) {
                return UrlValidationResult.invalid("URL must have a valid host.");
            }

            return UrlValidationResult.valid(url.trim());

        } catch (MalformedURLException e) {
            return UrlValidationResult.invalid("Invalid URL format: " + e.getMessage());
        }
    }

    private String getRandomUserAgent() {
        return USER_AGENTS.get(random.nextInt(USER_AGENTS.size()));
    }

    private String parseWebpageContent(String html, String url) throws ScrapingException {
        try {
            Document doc = Jsoup.parse(html);

            WebpageMetadata metadata = extractMetadata(doc, url);
            String mainContent = extractMainContent(doc);

            return formatWebpageResult(metadata, mainContent);

        } catch (Exception e) {
            throw new ScrapingException("Failed to parse webpage content: " + e.getMessage());
        }
    }

    private WebpageMetadata extractMetadata(Document doc, String url) {
        String title = doc.title();
        String domain = extractDomain(url);
        String description = extractMetaDescription(doc);
        String author = extractAuthor(doc);
        String publishDate = extractPublishDate(doc);

        return new WebpageMetadata(title, domain, description, author, publishDate);
    }

    private String extractMainContent(Document doc) {
        // Remove unwanted elements
        doc.select(REMOVE_ELEMENTS_SELECTOR).remove();

        // Try to find main content area
        for (String selector : CONTENT_SELECTORS) {
            Element mainContent = doc.selectFirst(selector);
            if (mainContent != null && !mainContent.text().trim().isEmpty()) {
                return mainContent.text();
            }
        }

        // Fallback to body content
        Element body = doc.body();
        return body != null ? body.text() : "No content found";
    }

    private String formatWebpageResult(WebpageMetadata metadata, String content) {
        StringBuilder result = new StringBuilder();
        result.append("📄 **Webpage Content Extracted**\n\n");
        result.append("🌐 **Source:** ").append(metadata.domain).append("\n");
        result.append("📝 **Title:** ").append(metadata.title.isEmpty() ? "No title found" : metadata.title).append("\n");

        if (!metadata.description.isEmpty()) {
            result.append("📋 **Description:** ").append(metadata.description).append("\n");
        }
        if (!metadata.author.isEmpty()) {
            result.append("👤 **Author:** ").append(metadata.author).append("\n");
        }
        if (!metadata.publishDate.isEmpty()) {
            result.append("📅 **Published:** ").append(metadata.publishDate).append("\n");
        }

        result.append("\n**Content:**\n");
        result.append(truncateContent(content, MAX_CONTENT_LENGTH));

        return result.toString();
    }

    private String extractStructuredData(String html, String selector, String url) throws ScrapingException {
        try {
            Document doc = Jsoup.parse(html);

            if (selector != null && !selector.trim().isEmpty()) {
                return extractSpecificElements(doc, selector.trim(), url);
            } else {
                return extractCommonStructuredData(doc, url);
            }

        } catch (Exception e) {
            throw new ScrapingException("Failed to extract structured data: " + e.getMessage());
        }
    }

    private String extractSpecificElements(Document doc, String selector, String url) {
        Elements elements = doc.select(selector);

        if (elements.isEmpty()) {
            return String.format("❌ No elements found with selector: '%s'", selector);
        }

        StringBuilder result = new StringBuilder();
        result.append(String.format("📊 **Structured Data from %s**\n\n", extractDomain(url)));
        result.append(String.format("🎯 **Selector:** %s\n", selector));
        result.append(String.format("📈 **Found:** %d elements\n\n", elements.size()));

        int count = 0;
        for (Element element : elements) {
            if (count >= MAX_STRUCTURED_ELEMENTS) {
                result.append("... and ").append(elements.size() - count).append(" more elements\n");
                break;
            }

            String elementText = element.text().trim();
            if (!elementText.isEmpty()) {
                result.append(String.format("**%d.** %s\n", count + 1,
                        elementText.length() > ELEMENT_TEXT_PREVIEW_LENGTH ?
                                elementText.substring(0, ELEMENT_TEXT_PREVIEW_LENGTH) + "..." :
                                elementText));
                count++;
            }
        }

        return result.toString();
    }

    private String extractCommonStructuredData(Document doc, String url) {
        StringBuilder result = new StringBuilder();
        result.append(String.format("📊 **Structured Data from %s**\n\n", extractDomain(url)));

        // Extract and analyze tables
        Elements tables = doc.select(TABLE_SELECTOR);
        if (!tables.isEmpty()) {
            result.append(String.format("📋 **Tables:** %d found\n", tables.size()));
            appendTablePreview(result, tables);
        }

        // Extract lists
        Elements lists = doc.select(LIST_SELECTOR);
        if (!lists.isEmpty()) {
            result.append(String.format("📝 **Lists:** %d found\n", lists.size()));
        }

        // Extract links
        Elements links = doc.select(LINK_SELECTOR);
        if (!links.isEmpty()) {
            result.append(String.format("🔗 **Links:** %d found\n", links.size()));
        }

        // Extract images
        Elements images = doc.select(IMAGE_SELECTOR);
        if (!images.isEmpty()) {
            result.append(String.format("🖼️ **Images:** %d found\n", images.size()));
        }

        // Extract headings with preview
        Elements headings = doc.select(HEADING_SELECTOR);
        if (!headings.isEmpty()) {
            result.append(String.format("📑 **Headings:** %d found\n", headings.size()));
            appendHeadingPreview(result, headings);
        }

        return result.toString();
    }

    private void appendTablePreview(StringBuilder result, Elements tables) {
        if (!tables.isEmpty()) {
            Element firstTable = tables.first();
            Elements rows = firstTable.select("tr");
            if (!rows.isEmpty()) {
                result.append("   First table preview:\n");
                for (int i = 0; i < Math.min(MAX_TABLE_PREVIEW_ROWS, rows.size()); i++) {
                    String rowText = rows.get(i).text();
                    result.append("   ").append(rowText.length() > 100 ? rowText.substring(0, 100) + "..." : rowText).append("\n");
                }
            }
        }
    }

    private void appendHeadingPreview(StringBuilder result, Elements headings) {
        result.append("   Preview:\n");
        for (int i = 0; i < Math.min(MAX_HEADING_PREVIEW, headings.size()); i++) {
            Element heading = headings.get(i);
            result.append("   ").append(heading.tagName().toUpperCase())
                    .append(": ").append(heading.text()).append("\n");
        }
    }

    // Utility methods
    private static String extractDomain(String url) {
        try {
            return new URL(url).getHost();
        } catch (Exception e) {
            return "Unknown";
        }
    }

    private static String extractMetaDescription(Document doc) {
        Element desc = doc.selectFirst(META_DESCRIPTION_SELECTOR);
        return desc != null ? desc.attr("content") : "";
    }

    private static String extractAuthor(Document doc) {
        Element author = doc.selectFirst(AUTHOR_SELECTOR);
        if (author != null) {
            if (author.hasAttr("content")) return author.attr("content");
            if (author.hasText()) return author.text();
        }
        return "";
    }

    private static String extractPublishDate(Document doc) {
        Element date = doc.selectFirst(DATE_SELECTOR);
        if (date != null) {
            if (date.hasAttr("datetime")) return date.attr("datetime");
            if (date.hasAttr("content")) return date.attr("content");
            if (date.hasText()) return date.text();
        }
        return "";
    }

    private static String truncateContent(String content, int maxLength) {
        if (content == null || content.trim().isEmpty()) {
            return "No content found";
        }

        content = content.trim();
        if (content.length() <= maxLength) {
            return content;
        }

        int lastSpace = content.lastIndexOf(' ', maxLength);
        if (lastSpace > maxLength - CONTENT_TRUNCATE_THRESHOLD) {
            return content.substring(0, lastSpace) + "...\n\n📄 [Content truncated - full text extracted]";
        }
        return content.substring(0, maxLength) + "...\n\n📄 [Content truncated - full text extracted]";
    }

    private static String generateContentHash(String content) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(content.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            return "hash-error-" + System.currentTimeMillis();
        }
    }

    // Helper classes
    private static class UrlValidationResult {
        private final boolean valid;
        private final String errorMessage;
        private final String cleanUrl;

        private UrlValidationResult(boolean valid, String errorMessage, String cleanUrl) {
            this.valid = valid;
            this.errorMessage = errorMessage;
            this.cleanUrl = cleanUrl;
        }

        static UrlValidationResult valid(String cleanUrl) {
            return new UrlValidationResult(true, null, cleanUrl);
        }

        static UrlValidationResult invalid(String errorMessage) {
            return new UrlValidationResult(false, errorMessage, null);
        }

        boolean isValid() { return valid; }
        String getErrorMessage() { return errorMessage; }
        String getCleanUrl() { return cleanUrl; }
    }

    private static class WebpageMetadata {
        final String title;
        final String domain;
        final String description;
        final String author;
        final String publishDate;

        WebpageMetadata(String title, String domain, String description, String author, String publishDate) {
            this.title = title != null ? title : "";
            this.domain = domain != null ? domain : "Unknown";
            this.description = description != null ? description : "";
            this.author = author != null ? author : "";
            this.publishDate = publishDate != null ? publishDate : "";
        }
    }

    // Custom exception
    private static class ScrapingException extends Exception {
        ScrapingException(String message) {
            super(message);
        }
    }
}



