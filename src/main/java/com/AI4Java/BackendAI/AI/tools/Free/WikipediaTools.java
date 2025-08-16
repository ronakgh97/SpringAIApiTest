package com.AI4Java.BackendAI.AI.tools.Free;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Service
public class WikipediaTools {

    private static final Logger log = LoggerFactory.getLogger(WikipediaTools.class);

    private final WebClient webClient = WebClient.builder()
            .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(5 * 1024 * 1024))
            .build();

    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String WIKIPEDIA_API_BASE = "https://en.wikipedia.org/api/rest_v1";
    private static final String WIKIPEDIA_SEARCH_API = "https://en.wikipedia.org/w/api.php";

    @Tool(name = "wikipedia_search", description = "Search Wikipedia articles by topic or keyword. " +
            "Returns a list of relevant Wikipedia articles with summaries.")
    public String wikipedia_search(@ToolParam(description = "Search phrase") String query) {
        try {
            log.info("Wikipedia search: {}", query);

            String searchUrl = String.format(
                    "%s?action=opensearch&search=%s&limit=5&namespace=0&format=json",
                    WIKIPEDIA_SEARCH_API,
                    URLEncoder.encode(query, StandardCharsets.UTF_8)
            );

            String response = webClient.get()
                    .uri(searchUrl)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(10))
                    .block();

            return parseSearchResults(response, query);

        } catch (Exception e) {
            log.error("Wikipedia search failed for '{}': {}", query, e.getMessage());
            return String.format("❌ Failed to search Wikipedia for '%s'. Please try again.", query);
        }
    }

    @Tool(name = "wikipedia_summary", description = "Get a summary of a specific Wikipedia article. " +
            "Parameter: title - the title of the Wikipedia article")
    public String wikipedia_summary(@ToolParam(description = "Exact article title") String title) {
        try {
            log.info("Getting Wikipedia summary for: {}", title);

            String summaryUrl = String.format(
                    "%s/page/summary/%s",
                    WIKIPEDIA_API_BASE,
                    URLEncoder.encode(title.replace(" ", "_"), StandardCharsets.UTF_8)
            );

            String response = webClient.get()
                    .uri(summaryUrl)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(10))
                    .block();

            return parseSummaryResponse(response, title);

        } catch (Exception e) {
            log.error("Failed to get Wikipedia summary for '{}': {}", title, e.getMessage());
            return String.format("❌ Could not find Wikipedia article for '%s'. Try searching first.", title);
        }
    }

    @Tool(name = "wikipedia_article", description = "Get the full content of a Wikipedia article. " +
            "Parameter: title - the title of the Wikipedia article")
    public String wikipedia_article(@ToolParam(description = "Exact article title") String title) {
        try {
            log.info("Getting full Wikipedia article: {}", title);

            String articleUrl = String.format(
                    "%s?action=query&format=json&titles=%s&prop=extracts&exintro=&explaintext=&exsectionformat=plain",
                    WIKIPEDIA_SEARCH_API,
                    URLEncoder.encode(title, StandardCharsets.UTF_8)
            );

            String response = webClient.get()
                    .uri(articleUrl)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(15))
                    .block();

            return parseArticleResponse(response, title);

        } catch (Exception e) {
            log.error("Failed to get Wikipedia article for '{}': {}", title, e.getMessage());
            return String.format("❌ Could not retrieve full article for '%s'.", title);
        }
    }

    @Tool(name = "wikipedia_random", description = "Get a random Wikipedia article. Great for discovering new topics!")
    public String wikipedia_random() {
        try {
            log.info("Getting random Wikipedia article");

            String randomUrl = String.format(
                    "%s?action=query&format=json&list=random&rnnamespace=0&rnlimit=1",
                    WIKIPEDIA_SEARCH_API
            );

            String response = webClient.get()
                    .uri(randomUrl)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(10))
                    .block();

            JsonNode root = objectMapper.readTree(response);
            JsonNode randomPage = root.path("query").path("random").get(0);
            String title = randomPage.path("title").asText();

            // Get summary of the random article
            return wikipedia_summary(title);

        } catch (Exception e) {
            log.error("Failed to get random Wikipedia article: {}", e.getMessage());
            return "❌ Failed to get random Wikipedia article.";
        }
    }

    @Tool(name = "wikipedia_related", description = "Find articles related to a given Wikipedia topic. " +
            "Parameter: title - the title of the Wikipedia article to find related topics for")
    public String wikipedia_related(@ToolParam(description = "Exact article title") String title) {
        try {
            log.info("Finding related Wikipedia articles for: {}", title);

            // Get links from the article
            String linksUrl = String.format(
                    "%s?action=query&format=json&titles=%s&prop=links&pllimit=10&plnamespace=0",
                    WIKIPEDIA_SEARCH_API,
                    URLEncoder.encode(title, StandardCharsets.UTF_8)
            );

            String response = webClient.get()
                    .uri(linksUrl)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(10))
                    .block();

            return parseRelatedResponse(response, title);

        } catch (Exception e) {
            log.error("Failed to find related articles for '{}': {}", title, e.getMessage());
            return String.format("❌ Could not find related articles for '%s'.", title);
        }
    }

    // Parsing Methods
    private String parseSearchResults(String jsonResponse, String query) {
        try {
            JsonNode root = objectMapper.readTree(jsonResponse);

            if (!root.isArray() || root.size() < 2) {
                return String.format("❌ No Wikipedia articles found for '%s'.", query);
            }

            JsonNode titles = root.get(1);
            JsonNode descriptions = root.get(2);
            JsonNode urls = root.get(3);

            StringBuilder result = new StringBuilder();
            result.append(String.format("🔍 **Wikipedia Search Results for: \"%s\"**\n\n", query));

            for (int i = 0; i < titles.size() && i < 5; i++) {
                String title = titles.get(i).asText();
                String description = i < descriptions.size() ? descriptions.get(i).asText() : "";
                String url = i < urls.size() ? urls.get(i).asText() : "";

                result.append(String.format("**%d. %s**\n", i + 1, title));
                if (!description.isEmpty()) {
                    result.append(String.format("   %s\n", description));
                }
                result.append(String.format("   🔗 %s\n\n", url));
            }

            result.append("💡 **Tip:** Use 'wikipedia_summary [title]' to get detailed information about any article.");

            return result.toString();

        } catch (Exception e) {
            log.error("Failed to parse Wikipedia search results: {}", e.getMessage());
            return "❌ Failed to parse search results.";
        }
    }

    private String parseSummaryResponse(String jsonResponse, String title) {
        try {
            JsonNode root = objectMapper.readTree(jsonResponse);

            String actualTitle = root.path("title").asText();
            String description = root.path("description").asText();
            String extract = root.path("extract").asText();
            String pageUrl = root.path("content_urls").path("desktop").path("page").asText();

            // Get thumbnail if available
            String thumbnailUrl = "";
            JsonNode thumbnail = root.path("thumbnail");
            if (!thumbnail.isMissingNode()) {
                thumbnailUrl = thumbnail.path("source").asText();
            }

            StringBuilder result = new StringBuilder();
            result.append(String.format("📖 **%s**\n\n", actualTitle));

            if (!description.isEmpty()) {
                result.append(String.format("*%s*\n\n", description));
            }

            if (!extract.isEmpty()) {
                // Limit extract length for readability
                String shortExtract = extract.length() > 800 ?
                        extract.substring(0, 800) + "..." : extract;
                result.append(String.format("%s\n\n", shortExtract));
            }

            if (!thumbnailUrl.isEmpty()) {
                result.append(String.format("🖼️ **Image:** %s\n", thumbnailUrl));
            }

            result.append(String.format("🔗 **Full Article:** %s\n\n", pageUrl));
            result.append("💡 **Commands:** Use 'wikipedia_article' for full content or 'wikipedia_related' for related topics.");

            return result.toString();

        } catch (Exception e) {
            log.error("Failed to parse Wikipedia summary: {}", e.getMessage());
            return String.format("❌ Found article '%s' but couldn't parse the summary.", title);
        }
    }

    private String parseArticleResponse(String jsonResponse, String title) {
        try {
            JsonNode root = objectMapper.readTree(jsonResponse);
            JsonNode pages = root.path("query").path("pages");

            JsonNode page = pages.elements().next();
            String actualTitle = page.path("title").asText();
            String extract = page.path("extract").asText();

            if (extract.isEmpty()) {
                return String.format("❌ No content found for Wikipedia article '%s'.", title);
            }

            StringBuilder result = new StringBuilder();
            result.append(String.format("📄 **Wikipedia Article: %s**\n\n", actualTitle));

            // Limit content length to avoid overwhelming responses
            if (extract.length() > 2000) {
                result.append(extract.substring(0, 2000));
                result.append("...\n\n📄 [Article truncated - full content available on Wikipedia]\n\n");
            } else {
                result.append(extract).append("\n\n");
            }

            result.append(String.format("🔗 **Full Article:** https://en.wikipedia.org/wiki/%s",
                    URLEncoder.encode(actualTitle.replace(" ", "_"), StandardCharsets.UTF_8)));

            return result.toString();

        } catch (Exception e) {
            log.error("Failed to parse Wikipedia article: {}", e.getMessage());
            return String.format("❌ Could not parse article content for '%s'.", title);
        }
    }

    private String parseRelatedResponse(String jsonResponse, String title) {
        try {
            JsonNode root = objectMapper.readTree(jsonResponse);
            JsonNode pages = root.path("query").path("pages");

            if (pages.isMissingNode()) {
                return String.format("❌ Could not find related articles for '%s'.", title);
            }

            JsonNode page = pages.elements().next();
            JsonNode links = page.path("links");

            if (links.isMissingNode() || links.size() == 0) {
                return String.format("❌ No related articles found for '%s'.", title);
            }

            StringBuilder result = new StringBuilder();
            result.append(String.format("🔗 **Related Wikipedia Articles for: \"%s\"**\n\n", title));

            List<String> relatedTitles = new ArrayList<>();
            for (JsonNode link : links) {
                String linkTitle = link.path("title").asText();
                if (!linkTitle.isEmpty() && relatedTitles.size() < 8) {
                    relatedTitles.add(linkTitle);
                }
            }

            for (int i = 0; i < relatedTitles.size(); i++) {
                result.append(String.format("**%d. %s**\n", i + 1, relatedTitles.get(i)));
                result.append(String.format("   🔗 https://en.wikipedia.org/wiki/%s\n\n",
                        URLEncoder.encode(relatedTitles.get(i).replace(" ", "_"), StandardCharsets.UTF_8)));
            }

            result.append("💡 **Tip:** Use 'wikipedia_summary [title]' to learn more about any related topic.");

            return result.toString();

        } catch (Exception e) {
            log.error("Failed to parse related articles: {}", e.getMessage());
            return String.format("❌ Could not parse related articles for '%s'.", title);
        }
    }
}

