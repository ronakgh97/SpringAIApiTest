package com.AI4Java.BackendAI.AI.tools.Paid;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
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

@Service
public class SerpApiTools {

    private static final Logger log = LoggerFactory.getLogger(SerpApiTools.class);

    @Value("${serpapi.api.key}")
    private String apiKey;

    private final WebClient webClient = WebClient.builder().build();
    private final Gson gson = new Gson();

    @Tool(name = "search_google", description = "Search Google using SerpAPI for current, accurate results." +
                "Use this when you need information not in your training data." +
            "Parameters: query (required), location (optional), num_results (optional, default 10)")
    public String search_google(
            @ToolParam(description = "Search query") String query,
            @ToolParam(description = "Location hint", required = false) String location,
            @ToolParam(description = "Results 1-20", required = false) String num_results) {
        try {
            log.info("SerpAPI Google search: {}", query);

            if (apiKey.isEmpty()) {
                return "❌ SerpAPI not configured. Please add your API key.";
            }

            String url = buildSearchUrl(query, location, num_results, "google");

            String response = webClient.get()
                    .uri(url)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(15))
                    .block();

            return parseGoogleSearchResults(response, query);

        } catch (Exception e) {
            log.error("SerpAPI Google search failed: {}", e.getMessage());
            return String.format("❌ Failed to search Google for '%s'. Please try again.", query);
        }
    }

    @Tool(name = "search_news_withSerpAPI", description = "Search for latest news using SerpAPI Google News. " +
            "Parameters: query (required), location (optional)")
    public String search_news_withSerpAPI(
            @ToolParam(description = "News query") String query,
            @ToolParam(description = "Location hint", required = false) String location) {
        try {
            log.info("SerpAPI News search: {}", query);

            String url = buildNewsSearchUrl(query, location);

            String response = webClient.get()
                    .uri(url)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(15))
                    .block();

            return parseNewsResults(response, query);

        } catch (Exception e) {
            log.error("SerpAPI News search failed: {}", e.getMessage());
            return String.format("❌ Failed to search news for '%s'.", query);
        }
    }

    @Tool(name = "search_images", description = "Search for images using SerpAPI Google Images. " +
            "Parameters: query (required), safe_search (optional: active/off)")
    public String search_images(
            @ToolParam(description = "Image query") String query,
            @ToolParam(description = "Safe search flag", required = false) String safe_search) {
        try {
            log.info("SerpAPI Images search: {}", query);

            String url = buildImageSearchUrl(query, safe_search);

            String response = webClient.get()
                    .uri(url)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(15))
                    .block();

            return parseImageResults(response, query);

        } catch (Exception e) {
            log.error("SerpAPI Images search failed: {}", e.getMessage());
            return String.format("❌ Failed to search images for '%s'.", query);
        }
    }

    @Tool(name = "search_shopping", description = "Search for products and shopping results using SerpAPI. " +
            "Parameters: query (required), location (optional)")
    public String search_shopping(
            @ToolParam(description = "Product query") String query,
            @ToolParam(description = "Location hint",  required = false) String location) {
        try {
            log.info("SerpAPI Shopping search: {}", query);

            String url = buildShoppingSearchUrl(query, location);

            String response = webClient.get()
                    .uri(url)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(15))
                    .block();

            return parseShoppingResults(response, query);

        } catch (Exception e) {
            log.error("SerpAPI Shopping search failed: {}", e.getMessage());
            return String.format("❌ Failed to search shopping for '%s'.", query);
        }
    }

    // URL Building Methods
    private String buildSearchUrl(String query, String location, String numResults, String engine) {
        StringBuilder url = new StringBuilder("https://serpapi.com/search?");
        url.append("engine=").append(engine);
        url.append("&q=").append(URLEncoder.encode(query, StandardCharsets.UTF_8));
        url.append("&api_key=").append(apiKey);

        if (location != null && !location.isEmpty()) {
            url.append("&location=").append(URLEncoder.encode(location, StandardCharsets.UTF_8));
        }

        int num = 10;
        try {
            if (numResults != null && !numResults.isEmpty()) {
                num = Math.min(20, Math.max(1, Integer.parseInt(numResults)));
            }
        } catch (NumberFormatException ignored) {}

        url.append("&num=").append(num);

        return url.toString();
    }

    private String buildNewsSearchUrl(String query, String location) {
        StringBuilder url = new StringBuilder("https://serpapi.com/search?");
        url.append("engine=google_news");
        url.append("&q=").append(URLEncoder.encode(query, StandardCharsets.UTF_8));
        url.append("&api_key=").append(apiKey);

        if (location != null && !location.isEmpty()) {
            url.append("&location=").append(URLEncoder.encode(location, StandardCharsets.UTF_8));
        }

        return url.toString();
    }

    private String buildImageSearchUrl(String query, String safeSearch) {
        StringBuilder url = new StringBuilder("https://serpapi.com/search?");
        url.append("engine=google_images");
        url.append("&q=").append(URLEncoder.encode(query, StandardCharsets.UTF_8));
        url.append("&api_key=").append(apiKey);

        if (safeSearch != null && safeSearch.equals("off")) {
            url.append("&safe=off");
        } else {
            url.append("&safe=active");
        }

        return url.toString();
    }

    private String buildShoppingSearchUrl(String query, String location) {
        StringBuilder url = new StringBuilder("https://serpapi.com/search?");
        url.append("engine=google_shopping");
        url.append("&q=").append(URLEncoder.encode(query, StandardCharsets.UTF_8));
        url.append("&api_key=").append(apiKey);

        if (location != null && !location.isEmpty()) {
            url.append("&location=").append(URLEncoder.encode(location, StandardCharsets.UTF_8));
        }

        return url.toString();
    }

    // Result Parsing Methods
    private String parseGoogleSearchResults(String jsonResponse, String query) {
        try {
            JsonObject root = gson.fromJson(jsonResponse, JsonObject.class);
            StringBuilder result = new StringBuilder();

            result.append(String.format("🔍 **Google Search Results for: \"%s\"**\n\n", query));

            // Organic results
            if (root.has("organic_results")) {
                JsonArray organicResults = root.getAsJsonArray("organic_results");

                for (int i = 0; i < Math.min(5, organicResults.size()); i++) {
                    JsonObject item = organicResults.get(i).getAsJsonObject();

                    String title = getJsonString(item, "title");
                    String link = getJsonString(item, "link");
                    String snippet = getJsonString(item, "snippet");

                    result.append(String.format("**%d. %s**\n", i + 1, title));
                    if (!snippet.isEmpty()) {
                        result.append(String.format("   %s\n", snippet));
                    }
                    result.append(String.format("   🔗 %s\n\n", link));
                }
            }

            // Answer box
            if (root.has("answer_box")) {
                JsonObject answerBox = root.getAsJsonObject("answer_box");
                String answer = getJsonString(answerBox, "answer");
                if (!answer.isEmpty()) {
                    result.append(String.format("💡 **Quick Answer:** %s\n\n", answer));
                }
            }

            return result.toString();

        } catch (Exception e) {
            log.error("Failed to parse Google search results: {}", e.getMessage());
            return "❌ Received search results but couldn't parse them properly.";
        }
    }

    private String parseNewsResults(String jsonResponse, String query) {
        try {
            JsonObject root = gson.fromJson(jsonResponse, JsonObject.class);
            StringBuilder result = new StringBuilder();

            result.append(String.format("📰 **Latest News for: \"%s\"**\n\n", query));

            if (root.has("news_results")) {
                JsonArray newsResults = root.getAsJsonArray("news_results");

                for (int i = 0; i < Math.min(5, newsResults.size()); i++) {
                    JsonObject item = newsResults.get(i).getAsJsonObject();

                    String title = getJsonString(item, "title");
                    String source = getJsonString(item, "source");
                    String date = getJsonString(item, "date");
                    String link = getJsonString(item, "link");
                    String snippet = getJsonString(item, "snippet");

                    result.append(String.format("**%d. %s**\n", i + 1, title));
                    result.append(String.format("   📅 %s | 📰 %s\n", date, source));
                    if (!snippet.isEmpty()) {
                        result.append(String.format("   %s\n", snippet));
                    }
                    result.append(String.format("   🔗 %s\n\n", link));
                }
            }

            return result.toString();

        } catch (Exception e) {
            return "❌ Failed to parse news results.";
        }
    }

    private String parseImageResults(String jsonResponse, String query) {
        try {
            JsonObject root = gson.fromJson(jsonResponse, JsonObject.class);
            StringBuilder result = new StringBuilder();

            result.append(String.format("🖼️ **Image Results for: \"%s\"**\n\n", query));

            if (root.has("images_results")) {
                JsonArray imageResults = root.getAsJsonArray("images_results");

                for (int i = 0; i < Math.min(5, imageResults.size()); i++) {
                    JsonObject item = imageResults.get(i).getAsJsonObject();

                    String title = getJsonString(item, "title");
                    String thumbnail = getJsonString(item, "thumbnail");
                    String original = getJsonString(item, "original");
                    String source = getJsonString(item, "source");

                    result.append(String.format("**%d. %s**\n", i + 1, title));
                    result.append(String.format("   📷 Source: %s\n", source));
                    result.append(String.format("   🔗 Image: %s\n\n", original));
                }
            }

            return result.toString();

        } catch (Exception e) {
            return "❌ Failed to parse image results.";
        }
    }

    private String parseShoppingResults(String jsonResponse, String query) {
        try {
            JsonObject root = gson.fromJson(jsonResponse, JsonObject.class);
            StringBuilder result = new StringBuilder();

            result.append(String.format("🛒 **Shopping Results for: \"%s\"**\n\n", query));

            if (root.has("shopping_results")) {
                JsonArray shoppingResults = root.getAsJsonArray("shopping_results");

                for (int i = 0; i < Math.min(5, shoppingResults.size()); i++) {
                    JsonObject item = shoppingResults.get(i).getAsJsonObject();

                    String title = getJsonString(item, "title");
                    String price = getJsonString(item, "price");
                    String source = getJsonString(item, "source");
                    String link = getJsonString(item, "link");
                    String rating = getJsonString(item, "rating");

                    result.append(String.format("**%d. %s**\n", i + 1, title));
                    result.append(String.format("   💰 %s | 🏪 %s", price, source));
                    if (!rating.isEmpty()) {
                        result.append(String.format(" | ⭐ %s", rating));
                    }
                    result.append("\n");
                    result.append(String.format("   🔗 %s\n\n", link));
                }
            }

            return result.toString();

        } catch (Exception e) {
            return "❌ Failed to parse shopping results.";
        }
    }

    private String getJsonString(JsonObject obj, String key) {
        if (obj.has(key) && !obj.get(key).isJsonNull()) {
            return obj.get(key).getAsString();
        }
        return "";
    }
}

