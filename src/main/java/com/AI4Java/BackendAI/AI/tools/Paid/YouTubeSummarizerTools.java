package com.AI4Java.BackendAI.AI.tools.Paid;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.util.retry.Retry;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicLong;
import java.time.Duration;

@Service
public class YouTubeSummarizerTools {

    private static final Logger logger = LoggerFactory.getLogger(YouTubeSummarizerTools.class);

    @Value("${supadata.api.key}")
    private String supadataApiKey;

    private static final int MAX_WORDS  = 1000;
    private static final int MAX_RETRY  = 2;
    private static final Duration TIMEOUT = Duration.ofSeconds(12);

    private WebClient web;
    private final ObjectMapper mapper = new ObjectMapper();
    private final AtomicLong requestCnt = new AtomicLong(0);

    @PostConstruct
    public void init() {
        web = WebClient.builder()
                .defaultHeader("User-Agent", "SpringAI-YouTubeSummarizer/1.0")
                .build();
        logger.info("YouTubeSummarizerTools initialised.");
    }

    @PreDestroy
    public void shutdown() {
        logger.info("YouTubeSummarizerTools shutting down - requests handled: {}", requestCnt.get());
    }

    @Tool(
            name = "youtube_summarize",
            description = "Fetch transcript from a YouTube video URL or ID and summarize with a word limit."
    )
    public String youtube_summarize(
            @ToolParam(description = "Full YouTube URL or 11-char video ID") String videoUrlOrId) {

        long id = requestCnt.incrementAndGet();
        logger.debug("YouTube transcript fetch #{}: '{}'", id, videoUrlOrId);

        try {
            String videoId = extractVideoId(videoUrlOrId);
            if (videoId == null) {
                logger.warn("Invalid YouTube URL or ID: {}", videoUrlOrId);
                return "❌ Invalid YouTube URL or video ID.";
            }

            String apiEndpoint = String.format(
                    "https://api.supadata.ai/v1/youtube/transcript?videoId=%s&text=true",
                    videoId);

            String response = web.get()
                    .uri(apiEndpoint)
                    .header("x-api-key", supadataApiKey)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(TIMEOUT)
                    .retryWhen(Retry.fixedDelay(MAX_RETRY, Duration.ofSeconds(1))
                            .filter(t -> t instanceof WebClientRequestException))
                    .block();

            JsonNode root = mapper.readTree(response);
            if (root.has("content")) {
                String transcript = root.get("content").asText();
                String summary = summarizeTranscriptWithLimit(transcript);
                return "🎬 **YouTube Transcript Summary**\n\n" + summary;
            } else if (root.has("error")) {
                String msg = root.get("error").asText();
                return "❌ SupaData error: " + msg;
            } else {
                return "❌ Transcript not available for this video.";
            }
        } catch (WebClientResponseException e) {
            if (e.getStatusCode().value() == 429)
                return "❌ SupaData rate-limit exceeded.";
            logger.error("YouTube API call failed: HTTP {}", e.getStatusCode());
            return "❌ SupaData API error: HTTP " + e.getRawStatusCode();
        } catch (Exception e) {
            logger.error("Transcript fetch/summarize failed", e);
            return "❌ Failed to fetch or summarize YouTube transcript.";
        }
    }

    /** Extracts 11-char video ID from YouTube URL or uses as-is if valid. */
    private String extractVideoId(String urlOrId) {
        try {
            if (urlOrId == null) return null;
            String url = urlOrId.trim();
            if (url.length() == 11 && url.matches("^[a-zA-Z0-9_-]+$")) {
                return url; // already a clean ID
            }
            if (url.contains("youtube.com/watch?v=")) {
                int idx = url.indexOf("v=") + 2;
                return url.substring(idx, Math.min(idx + 11, url.length()));
            }
            if (url.contains("youtu.be/")) {
                int idx = url.indexOf("youtu.be/") + 9;
                return url.substring(idx, Math.min(idx + 11, url.length()));
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    private String summarizeTranscriptWithLimit(String transcript) {
        if (transcript == null || transcript.isBlank()) return "No transcript available.";
        String[] words = transcript.split("\\s+");
        if (words.length <= MAX_WORDS) return transcript;
        return String.join(" ", Arrays.copyOfRange(words, 0, MAX_WORDS))
                + "... [Summary truncated: transcript exceeds " + MAX_WORDS + " words]";
    }
}

