package com.AI4Java.BackendAI.AI.tools.Free;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

@Service
public class WebSearchTools {

    private static final Logger log = LoggerFactory.getLogger(WebSearchTools.class);

    @Tool(name = "web_Search",
            description = "Searches the web using DuckDuckGo for current information. " +
                    "Use this when you need recent information, news, or facts not in your training data."+
                    "Returns top results with title, link, and snippet. " +
                    "Parameter: query - the search terms to look for."
    )
    public String webSearch(@ToolParam(description = "Search terms") String query) {
        try {
            log.info("Performing web search for query: {}", query);

            String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);
            String searchUrl = "https://html.duckduckgo.com/html/?q=" + encodedQuery;

            WebClient webClient = WebClient.builder()
                    .defaultHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .build();

            String htmlResponse = webClient.get()
                    .uri(searchUrl)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(30))
                    .block();

            log.info(htmlResponse);

            return parseHtmlResults(htmlResponse, query);

        } catch (Exception e) {
            log.error("Failed to perform web search for query '{}': {}", query, e.getMessage());
            return "Failed to perform web search. Please try again.";
        }
    }

    private String parseHtmlResults(String html, String originalQuery) {
        StringBuilder results = new StringBuilder();
        results.append("🔍 Search results for: ").append(originalQuery).append("\n\n");

        try {
            org.jsoup.nodes.Document doc = org.jsoup.Jsoup.parse(html);
            org.jsoup.select.Elements resultBlocks = doc.select("div.result");

            int count = 0;
            for (org.jsoup.nodes.Element block : resultBlocks) {
                if (count >= 8) break; // Reduced to 8 for better readability

                org.jsoup.nodes.Element titleElement = block.selectFirst("a.result__a");
                org.jsoup.nodes.Element snippetElement = block.selectFirst("a.result__snippet, div.result__snippet");

                // Extract site name and additional metadata
                org.jsoup.nodes.Element urlElement = block.selectFirst("a.result__url");
                org.jsoup.nodes.Element dateElement = block.selectFirst(".result__timestamp");

                if (titleElement != null) {
                    String title = titleElement.text();
                    String link = titleElement.absUrl("href");
                    String snippet = (snippetElement != null) ?
                            truncateSnippet(snippetElement.text(), 150) : "No description available.";

                    // Extract site name from URL or dedicated element
                    String siteName = extractSiteName(link, urlElement);
                    String publishDate = (dateElement != null) ? dateElement.text() : "";

                    // Better formatting
                    results.append("**").append(count + 1).append(". ").append(title).append("**\n");
                    results.append("📝 ").append(snippet).append("\n");
                    results.append("🌐 Source: ").append(siteName);

                    if (!publishDate.isEmpty()) {
                        results.append(" • ").append(publishDate);
                    }
                    results.append("\n");
                    results.append("🔗 ").append(shortenUrl(link)).append("\n\n");

                    count++;
                }
            }

            if (count == 0) {
                return "❌ No search results found for '" + originalQuery + "'. Try different keywords or check spelling.";
            }

            results.append("💡 Found ").append(count).append(" results");
            return results.toString();

        } catch (Exception e) {
            log.error("Failed to parse HTML results: {}", e.getMessage());
            return "Search completed but results couldn't be parsed properly.";
        }
    }

    // Helper methods
    private String extractSiteName(String url, org.jsoup.nodes.Element urlElement) {
        try {
            if (urlElement != null && !urlElement.text().isEmpty()) {
                return urlElement.text(); // DuckDuckGo sometimes provides clean site names
            }

            // Extract domain from URL
            java.net.URL parsedUrl = new java.net.URL(url);
            String host = parsedUrl.getHost();
            return host.startsWith("www.") ? host.substring(4) : host;
        } catch (Exception e) {
            return "Unknown source";
        }
    }

    private String shortenUrl(String url) {
        if (url.length() > 60) {
            try {
                java.net.URL parsedUrl = new java.net.URL(url);
                return parsedUrl.getHost() + "..." + url.substring(url.length() - 20);
            } catch (Exception e) {
                return url.substring(0, 60) + "...";
            }
        }
        return url;
    }

    private String truncateSnippet(String text, int maxLength) {
        if (text.length() <= maxLength) return text;

        // Try to break at a word boundary
        int lastSpace = text.lastIndexOf(' ', maxLength);
        if (lastSpace > maxLength - 20) {
            return text.substring(0, lastSpace) + "...";
        }

        return text.substring(0, maxLength) + "...";
    }

}

