package com.AI4Java.BackendAI.AI.tools;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

@Service
public class WebSearchTools {

    private static final Logger log = LoggerFactory.getLogger(WebSearchTools.class);

    @Tool(name = "web_search", description = "Searches the web using DuckDuckGo for current information. " +
            "Use this when you need recent information, news, or facts not in your training data. " +
            "Parameter: query - the search terms to look for.")
    public String web_search(String query) {
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
                    .timeout(Duration.ofSeconds(15))
                    .block();

            return parseHtmlResults(htmlResponse, query);

        } catch (Exception e) {
            log.error("Failed to perform web search for query '{}': {}", query, e.getMessage());
            return "Failed to perform web search. Please try again or rephrase your query.";
        }
    }

    private String parseHtmlResults(String html, String originalQuery) {
        StringBuilder results = new StringBuilder();
        results.append("Search results for: ").append(originalQuery).append("\n\n");

        try {
            // Simple regex parsing for search results (you might want to use Jsoup for better parsing)
            java.util.regex.Pattern titlePattern = java.util.regex.Pattern.compile(
                    "<a[^>]*class=\"result__a\"[^>]*>([^<]+)</a>");
            java.util.regex.Pattern snippetPattern = java.util.regex.Pattern.compile(
                    "<a[^>]*class=\"result__snippet\"[^>]*>([^<]+)</a>");

            java.util.regex.Matcher titleMatcher = titlePattern.matcher(html);
            java.util.regex.Matcher snippetMatcher = snippetPattern.matcher(html);

            int count = 0;
            while (titleMatcher.find() && snippetMatcher.find() && count < 5) {
                String title = titleMatcher.group(1).trim();
                String snippet = snippetMatcher.group(1).trim();

                results.append(count + 1).append(". ").append(title).append("\n");
                results.append("   ").append(snippet).append("\n\n");
                count++;
            }

            if (count == 0) {
                return "No search results found for '" + originalQuery + "'. Try different keywords.";
            }

            return results.toString();

        } catch (Exception e) {
            log.error("Failed to parse HTML results: {}", e.getMessage());
            return "Search completed but results couldn't be parsed properly.";
        }
    }
}

