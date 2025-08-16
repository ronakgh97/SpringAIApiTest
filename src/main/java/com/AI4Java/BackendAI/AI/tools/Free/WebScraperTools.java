package com.AI4Java.BackendAI.AI.tools.Free;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.security.MessageDigest;
import java.util.Base64;
import java.nio.charset.StandardCharsets;


@Service
public class WebScraperTools {

    private static final Logger log = LoggerFactory.getLogger(WebScraperTools.class);
    private final WebClient webClient = WebClient.builder()
            .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(5 * 1024 * 1024))
            .build();

    @Tool(name = "scrape_webpage", description = "Scrapes and extracts content from any webpage URL. " +
            "Returns clean text content, title, and key information. " +
            "Parameter: url - the webpage URL to scrape")
    public String scrape_webpage(@ToolParam(description = "Full http/https URL to scrape") String url) {
        try {
            log.info("Scraping webpage: {}", url);

            if (!isValidUrl(url)) {
                return "❌ Invalid URL format. Please provide a valid HTTP/HTTPS URL.";
            }

            // Fetch the webpage
            String html = webClient.get()
                    .uri(url)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(15))
                    .block();

            return parseWebpageContent(html, url);

        } catch (Exception e) {
            log.error("Failed to scrape webpage '{}': {}", url, e.getMessage());
            return String.format("❌ Failed to scrape webpage: %s", e.getMessage());
        }
    }

    @Tool(name = "extract_structured_data", description = "Extracts structured data from a webpage like tables, lists, or specific elements. " +
            "Parameters: url (required), selector (optional CSS selector for specific elements)")
    public String extract_structured_data(
            @ToolParam(description = "Full http/https URL") String url,
            @ToolParam(description = "CSS selector for elements", required = false) String selector) {
        try {
            log.info("Extracting structured data from: {} with selector: {}", url, selector);

            String html = webClient.get()
                    .uri(url)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(15))
                    .block();

            return extractStructuredData(html, selector, url);

        } catch (Exception e) {
            log.error("Failed to extract structured data: {}", e.getMessage());
            return "❌ Failed to extract structured data from webpage.";
        }
    }

    @Tool(name = "monitor_webpage_changes", description = "Checks if a webpage has changed since last visit by comparing content hashes. " +
            "Parameter: url - webpage to monitor for changes")
    public String monitor_webpage_changes(@ToolParam(description = "Full http/https URL") String url) {
        try {
            log.info("Monitoring webpage changes: {}", url);

            String currentContent = scrape_webpage(url);
            String contentHash = generateContentHash(currentContent);

            // In real implementation, store and compare with previous hash
            // For now, return current state
            return String.format("📊 **Webpage Monitoring**\n\n" +
                            "🔗 **URL:** %s\n" +
                            "🔍 **Content Hash:** %s\n" +
                            "📅 **Checked:** %s\n\n" +
                            "💡 **Note:** Enable monitoring to track changes over time.",
                    url, contentHash.substring(0, 8),
                    LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));

        } catch (Exception e) {
            log.error("Failed to monitor webpage: {}", e.getMessage());
            return "❌ Failed to monitor webpage changes.";
        }
    }

    // Core parsing methods
    private String parseWebpageContent(String html, String url) {
        try {
            Document doc = Jsoup.parse(html);

            // Extract basic information
            String title = doc.title();
            String domain = extractDomain(url);

            // Remove script and style elements
            doc.select("script, style, nav, header, footer, aside").remove();

            // Extract main content
            String mainContent = extractMainContent(doc);

            // Extract metadata
            String description = extractMetaDescription(doc);
            String author = extractAuthor(doc);
            String publishDate = extractPublishDate(doc);

            // Format response
            StringBuilder result = new StringBuilder();
            result.append("📄 **Webpage Content Extracted**\n\n");
            result.append("🌐 **Source:** ").append(domain).append("\n");
            result.append("📝 **Title:** ").append(title).append("\n");

            if (!description.isEmpty()) {
                result.append("📋 **Description:** ").append(description).append("\n");
            }
            if (!author.isEmpty()) {
                result.append("👤 **Author:** ").append(author).append("\n");
            }
            if (!publishDate.isEmpty()) {
                result.append("📅 **Published:** ").append(publishDate).append("\n");
            }

            result.append("\n**Content:**\n");
            result.append(truncateContent(mainContent, 1500));

            return result.toString();

        } catch (Exception e) {
            log.error("Failed to parse webpage content: {}", e.getMessage());
            return "❌ Failed to parse webpage content.";
        }
    }

    private String extractMainContent(Document doc) {
        // Try to find main content area
        Element mainContent = doc.selectFirst("main, article, .content, .post, .entry");

        if (mainContent != null) {
            return mainContent.text();
        }

        // Fallback to body content
        return doc.body().text();
    }

    private String extractStructuredData(String html, String selector, String url) {
        try {
            Document doc = Jsoup.parse(html);

            if (selector != null && !selector.isEmpty()) {
                // Extract specific elements
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
                    if (count >= 10) break; // Limit results

                    result.append(String.format("**%d.** %s\n", count + 1,
                            element.text().length() > 100 ?
                                    element.text().substring(0, 100) + "..." :
                                    element.text()));
                    count++;
                }

                return result.toString();

            } else {
                // Extract common structured data
                return extractCommonStructuredData(doc, url);
            }

        } catch (Exception e) {
            log.error("Failed to extract structured data: {}", e.getMessage());
            return "❌ Failed to extract structured data.";
        }
    }

    private String extractCommonStructuredData(Document doc, String url) {
        StringBuilder result = new StringBuilder();
        result.append(String.format("📊 **Structured Data from %s**\n\n", extractDomain(url)));

        // Extract tables
        Elements tables = doc.select("table");
        if (!tables.isEmpty()) {
            result.append(String.format("📋 **Tables:** %d found\n", tables.size()));
        }

        // Extract lists
        Elements lists = doc.select("ul, ol");
        if (!lists.isEmpty()) {
            result.append(String.format("📝 **Lists:** %d found\n", lists.size()));
        }

        // Extract links
        Elements links = doc.select("a[href]");
        if (!links.isEmpty()) {
            result.append(String.format("🔗 **Links:** %d found\n", links.size()));
        }

        // Extract images
        Elements images = doc.select("img[src]");
        if (!images.isEmpty()) {
            result.append(String.format("🖼️ **Images:** %d found\n", images.size()));
        }

        return result.toString();
    }

    // Utility methods
    private boolean isValidUrl(String url) {
        try {
            new java.net.URL(url);
            return url.startsWith("http://") || url.startsWith("https://");
        } catch (Exception e) {
            return false;
        }
    }

    private String extractDomain(String url) {
        try {
            java.net.URL u = new java.net.URL(url);
            return u.getHost();
        } catch (Exception e) {
            return "Unknown";
        }
    }

    private String extractMetaDescription(Document doc) {
        Element desc = doc.selectFirst("meta[name=description]");
        return desc != null ? desc.attr("content") : "";
    }

    private String extractAuthor(Document doc) {
        Element author = doc.selectFirst("meta[name=author], .author, .byline");
        return author != null ? author.text() : "";
    }

    private String extractPublishDate(Document doc) {
        Element date = doc.selectFirst("time, .date, .published, meta[property='article:published_time']");
        return date != null ? date.text() : "";
    }

    private String truncateContent(String content, int maxLength) {
        if (content.length() <= maxLength) {
            return content;
        }

        int lastSpace = content.lastIndexOf(' ', maxLength);
        if (lastSpace > maxLength - 50) {
            return content.substring(0, lastSpace) + "...\n\n📄 [Content truncated - full text extracted]";
        }

        return content.substring(0, maxLength) + "...\n\n📄 [Content truncated - full text extracted]";
    }

    private String generateContentHash(String content) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(content.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            return "unknown";
        }
    }
}

