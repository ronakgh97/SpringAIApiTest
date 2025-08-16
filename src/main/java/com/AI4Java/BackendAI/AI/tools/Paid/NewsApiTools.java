/*package com.AI4Java.BackendAI.AI.tools.Paid;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@Service
public class NewsApiTools {

    private static final Logger log = LoggerFactory.getLogger(NewsApiTools.class);

    @Value("${newsapi.api.key}")
    private String apiKey;

    private final WebClient webClient = WebClient.builder().build();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Tool(name = "get_breaking_news", description = "Get breaking news and top headlines from around the world. " +
            "Parameters: country (optional: us, in, uk, etc.), category (optional: business, technology, sports, etc.)")
    public String get_breaking_news(
        @ToolParam(description = "2-letter country code", required = false) String country,
        @ToolParam(description = "News category (business, tech…)", required = false) String category) {
        try {
            log.info("Breaking news – country={}, category={}", country, category);

            StringBuilder url = new StringBuilder("https://newsapi.org/v2/top-headlines?pageSize=8");

            if (country != null && !country.isBlank())
                url.append("&country=").append(country.toLowerCase());

            if (category != null && !category.isBlank())
                url.append("&category=").append(category.toLowerCase());

            String response = callNewsApi(url.toString());
            return parseNewsResponse(response, "Breaking News Headlines");

        } catch (Exception e) {
            log.error("Breaking news failed: {}", e.getMessage());
            return "❌ Failed to fetch breaking news.";
        }
    }

    @Tool(name = "search_news_withNewsAPI", description = "Search for news articles about specific topics, people, or events. " +
            "Parameters: query (required), from_date (optional: YYYY-MM-DD), to_date (optional: YYYY-MM-DD), language (optional: en, es, fr, etc.)")
    public String search_news_withNewsAPI(
            @ToolParam(description = "Search keywords") String query,
            @ToolParam(description = "Earliest date (YYYY-MM-DD)", required = false) String from_date,
            @ToolParam(description = "Latest date (YYYY-MM-DD)",   required = false) String to_date,
            @ToolParam(description = "2-letter language code",    required = false) String language) {
        try {
            log.info("News search: {}", query);

            StringBuilder url = new StringBuilder("https://newsapi.org/v2/everything?");
            url.append("q=").append(URLEncoder.encode(query, StandardCharsets.UTF_8));
            url.append("&pageSize=6&sortBy=publishedAt");

            if (language  != null && !language.isBlank()) url.append("&language=").append(language.toLowerCase());
            if (from_date != null && !from_date.isBlank()) url.append("&from=").append(from_date);
            if (to_date   != null && !to_date.isBlank())   url.append("&to=").append(to_date);

            String response = callNewsApi(url.toString());
            return parseNewsResponse(response, String.format("Search Results: \"%s\"", query));

        } catch (Exception e) {
            log.error("News search failed: {}", e.getMessage());
            return String.format("❌ Failed to search news for '%s'.", query);
        }
    }

    @Tool(name = "get_tech_news", description = "Get the latest technology news from top tech sources like TechCrunch, Engadget, Ars Technica, etc.")
    public String get_tech_news() {
        return simpleTopHeadlines("category=technology&language=en", "Latest Technology News");
    }

    @Tool(name = "get_business_news", description = "Get the latest business and financial news from major business publications.")
    public String get_business_news() {
        return simpleTopHeadlines("category=business&language=en", "Latest Business News");
    }

    @Tool(name = "get_source_news", description = "Get news from specific sources like BBC, CNN, TechCrunch, etc. " +
            "Parameter: sources (required) - comma-separated source IDs (e.g., 'bbc-news,cnn,techcrunch')")
    public String get_source_news(String sources) {
        try {
            log.info("Source news: {}", sources);
            String url = "https://newsapi.org/v2/top-headlines?sources="
                    + URLEncoder.encode(sources, StandardCharsets.UTF_8)
                    + "&pageSize=6";
            String response = callNewsApi(url);
            return parseNewsResponse(response, "News from: " + sources);

        } catch (Exception e) {
            log.error("Source news failed: {}", e.getMessage());
            return String.format("❌ Failed to fetch news from sources '%s'.", sources);
        }
    }

    @Tool(name = "get_local_news", description = "Get local news for specific countries. " +
            "Parameter: country (required) - country code (us, uk, in, ca, au, de, fr, etc.)")
    public String get_local_news(@ToolParam(description = "2-letter country code") String country) {
        try {
            log.info("Local news: {}", country);
            String url = "https://newsapi.org/v2/top-headlines?country="
                    + country.toLowerCase()
                    + "&pageSize=6";
            String response = callNewsApi(url);
            return parseNewsResponse(response, "Local News (" + country.toUpperCase() + ")");

        } catch (Exception e) {
            log.error("Local news failed: {}", e.getMessage());
            return String.format("❌ Failed to fetch news for country '%s'.", country);
        }
    }

    @Tool(name = "get_trending_news", description = "Get trending news from the past 24 hours, sorted by popularity and engagement.")
    public String get_trending_news() {
        try {
            log.info("Trending news");
            String from = LocalDate.now().minusDays(1).format(DateTimeFormatter.ISO_LOCAL_DATE);
            String url  = "https://newsapi.org/v2/everything?q=*"
                    + "&from=" + from
                    + "&sortBy=popularity&pageSize=6&language=en";
            String response = callNewsApi(url);
            return parseNewsResponse(response, "Trending News (24 h)");

        } catch (Exception e) {
            log.error("Trending news failed: {}", e.getMessage());
            return "❌ Failed to fetch trending news.";
        }
    }

    private String callNewsApi(String url) {
        return webClient.get()
                .uri(url)
                .header("X-Api-Key", apiKey)
                .header("User-Agent", "Spring-NewsBot/1.0")
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofSeconds(10))
                .block();
    }

    private String simpleTopHeadlines(String queryPart, String title) {
        try {
            String url = "https://newsapi.org/v2/top-headlines?" + queryPart + "&pageSize=6";
            String response = callNewsApi(url);
            return parseNewsResponse(response, title);
        } catch (Exception e) {
            log.error("{} failed: {}", title, e.getMessage());
            return "❌ Failed to fetch " + title.toLowerCase() + ".";
        }
    }

    // Parse news response into formatted string
    private String parseNewsResponse(String jsonResponse, String title) {
        try {
            JsonNode root = objectMapper.readTree(jsonResponse);

            if (!root.path("status").asText().equals("ok")) {
                return "❌ API Error: " + root.path("message").asText();
            }

            JsonNode articles = root.path("articles");
            int totalResults = root.path("totalResults").asInt();

            if (articles.isEmpty()) {
                return String.format("📰 **%s**\n\n❌ No articles found.", title);
            }

            StringBuilder result = new StringBuilder();
            result.append(String.format("📰 **%s**\n\n", title));
            result.append(String.format("📊 **Total Results:** %,d articles\n\n", totalResults));

            for (int i = 0; i < articles.size(); i++) {
                JsonNode article = articles.get(i);

                String headline = article.path("title").asText();
                String source = article.path("source").path("name").asText();
                String description = article.path("description").asText();
                String url = article.path("url").asText();
                String publishedAt = article.path("publishedAt").asText();
                String urlToImage = article.path("urlToImage").asText();

                // Format published date
                String formattedDate = "";
                if (!publishedAt.isEmpty()) {
                    try {
                        formattedDate = publishedAt.substring(0, 19).replace("T", " ");
                    } catch (Exception ignored) {
                        formattedDate = publishedAt;
                    }
                }

                result.append(String.format("**%d. %s**\n", i + 1, headline));
                result.append(String.format("📰 *%s* | 📅 %s\n", source, formattedDate));

                if (!description.isEmpty() && !description.equals("null")) {
                    String shortDesc = description.length() > 150 ?
                            description.substring(0, 150) + "..." : description;
                    result.append(String.format("📝 %s\n", shortDesc));
                }

                result.append(String.format("🔗 %s\n", url));

                if (!urlToImage.isEmpty() && !urlToImage.equals("null")) {
                    result.append(String.format("🖼️ %s\n", urlToImage));
                }

                result.append("\n");
            }

            result.append("💡 **Tip:** Use specific search terms or sources for more targeted news results.");

            return result.toString();

        } catch (Exception e) {
            log.error("Failed to parse news response: {}", e.getMessage());
            return "❌ Failed to parse news articles.";
        }
    }
}*/

