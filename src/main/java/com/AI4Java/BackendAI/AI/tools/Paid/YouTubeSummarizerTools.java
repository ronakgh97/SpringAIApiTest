package com.AI4Java.BackendAI.AI.tools.Paid;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import java.util.Arrays;

@Service
public class YouTubeSummarizerTools {

    private static final Logger log = LoggerFactory.getLogger(YouTubeSummarizerTools.class);

    @Value("${supadata.api.key}")
    private String supadataApiKey;

    private final WebClient webClient = WebClient.builder().build();

    private static final int MAX_WORDS = 1000; // Set your desired word limit

    @Tool(name = "youtube_summarize", description = "Fetches transcript from a YouTube video URL and summarizes it with a word limit.")
    public String youtube_summarize(@ToolParam(description = "Full YouTube URL or 11-character video ID") String videoUrl) {
        try {
            log.info("Fetching transcript for YouTube URL: {}", videoUrl);

            // Extract video ID from URL or expect the ID directly
            String videoId = extractVideoId(videoUrl);
            if (videoId == null || videoId.isEmpty()) {
                return "Invalid YouTube URL or video ID.";
            }

            String apiEndpoint = String.format(
                    "https://api.supadata.ai/v1/youtube/transcript?videoId=%s&text=true",
                    videoId);

            // Call Supadata API with API key header
            String response = webClient.get()
                    .uri(apiEndpoint)
                    .header("x-api-key", supadataApiKey)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            // Parse JSON to get 'content' field (transcript text)
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(response);
            if (root.has("content")) {
                String transcript = root.get("content").asText();

                // Summarize with word limit and notify user if truncated
                String summary = summarizeTranscriptWithLimit(transcript);

                return summary;
            } else {
                return "Transcript not available for this video.";
            }
        } catch (Exception e) {
            log.error("Error fetching or summarizing YouTube transcript", e);
            return "Failed to fetch or summarize YouTube transcript: " + e.getMessage();
        }
    }

    private String extractVideoId(String url) {
        // Basic patterns to extract video ID from YouTube URL
        try {
            if (url.contains("youtube.com/watch?v=")) {
                int start = url.indexOf("v=") + 2;
                int end = start + 11;
                if (end <= url.length()) {
                    return url.substring(start, end);
                }
            } else if (url.contains("youtu.be/")) {
                int start = url.indexOf("youtu.be/") + 9;
                int end = start + 11;
                if (end <= url.length()) {
                    return url.substring(start, end);
                }
            } else if (url.length() == 11) { // Assume direct ID input
                return url;
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    private String summarizeTranscriptWithLimit(String transcript) {
        String[] words = transcript.split("\\s+");

        if (words.length <= MAX_WORDS) {
            // Within limit, return full transcript
            return transcript;
        } else {
            // Exceeds limit, truncate and notify user
            String summary = String.join(" ", Arrays.copyOfRange(words, 0, MAX_WORDS));
            return summary + "... [SUMMARY TRUNCATED: Transcript exceeds " + MAX_WORDS + " words]";
        }
    }
}
