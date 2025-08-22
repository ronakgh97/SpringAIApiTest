package com.AI4Java.BackendAI.AI.tools.Free;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.w3c.dom.*;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.StringReader;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import java.util.*;
import java.util.concurrent.TimeoutException;

@Service
public class ArxivApiTools {

    private static final Logger logger = LoggerFactory.getLogger(ArxivApiTools.class);

    // Constants
    private static final String ARXIV_API_BASE_URL = "https://export.arxiv.org/api/query";
    private static final String ATOM_NAMESPACE = "http://www.w3.org/2005/Atom";
    private static final int REQUEST_TIMEOUT_SECONDS = 60;
    private static final int MAX_MEMORY_SIZE = 8192 * 8192;
    private static final int DEFAULT_MAX_RESULTS = 10;
    private static final int MAX_ALLOWED_RESULTS = 100;
    private static final int SUMMARY_MAX_LENGTH = 1800;
    private static final int SUMMARY_TRUNCATE_LENGTH = 1795;
    private static final String PDF_CONTENT_TYPE = "application/pdf";

    private WebClient webClient;
    private DocumentBuilderFactory documentBuilderFactory;

    @PostConstruct
    public void initialize() {
        logger.info("Initializing Arxiv Tools service");

        this.webClient = createWebClient();
        this.documentBuilderFactory = createDocumentBuilderFactory();

        logger.info("Arxiv Tools service initialized successfully");
    }

    @PreDestroy
    public void cleanup() {
        logger.info("Shutting down Arxiv Tools service");
    }

    private WebClient createWebClient() {
        return WebClient.builder()
                .defaultHeader("User-Agent", "AI4Java-ArxivTool/1.0")
                .defaultHeader("Accept", "application/atom+xml")
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(MAX_MEMORY_SIZE))
                .build();
    }

    private DocumentBuilderFactory createDocumentBuilderFactory() {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        try {
            // Security: Prevent XXE attacks
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        } catch (ParserConfigurationException e) {
            logger.warn("Could not configure XML security features", e);
        }
        return factory;
    }

    @Tool(name = "arxiv_search",
            description = "Search arXiv papers and return formatted results. " +
                    "Supports queries like 'all:quantum computing', 'cat:cs.AI', 'au:Einstein', etc.")
    @Cacheable(
            value = "arxivCache",
            key = "#query + '-' + (#start != null ? #start : 0) + '-' + (#max != null ? #max : 10)"
    )
    public String arxiv_search(
            @ToolParam(description = "Search query (e.g., 'all:quantum computing', 'cat:cs.AI')") String query,
            @ToolParam(description = "Starting index for pagination (default: 0)", required = false) Integer start,
            @ToolParam(description = "Maximum number of results (1-100, default: 10)", required = false) Integer max
    ) {
        logger.debug("ArXiv search initiated with query: '{}', start: {}, max: {}", query, start, max);

        // Input validation
        ValidationResult validation = validateInput(query, start, max);
        if (!validation.isValid()) {
            logger.warn("Invalid input for ArXiv search: {}", validation.getErrorMessage());
            return "❌ " + validation.getErrorMessage();
        }

        int startIndex = validation.getStartIndex();
        int maxResults = validation.getMaxResults();

        try {
            String xmlResponse = fetchArxivData(query, startIndex, maxResults);
            String formattedResults = parseAndFormatResults(xmlResponse, maxResults);

            logger.info("ArXiv search completed successfully. Query: '{}', Results returned: {}",
                    query, extractResultCount(formattedResults));

            return formattedResults;

        } catch (ArxivApiException e) {
            logger.error("ArXiv API error for query '{}': {}", query, e.getMessage());
            return "❌ " + e.getMessage();
        } catch (Exception e) {
            logger.error("Unexpected error during ArXiv search for query '{}'", query, e);
            return "❌ An unexpected error occurred while searching arXiv.";
        }
    }

    private ValidationResult validateInput(String query, Integer start, Integer max) {
        if (query == null || query.trim().isEmpty()) {
            return ValidationResult.invalid("Search query cannot be empty.");
        }

        int startIndex = (start != null && start >= 0) ? start : 0;
        int maxResults = DEFAULT_MAX_RESULTS;

        if (max != null) {
            if (max < 1) {
                return ValidationResult.invalid("Maximum results must be at least 1.");
            }
            if (max > MAX_ALLOWED_RESULTS) {
                return ValidationResult.invalid("Maximum results cannot exceed " + MAX_ALLOWED_RESULTS + ".");
            }
            maxResults = max;
        }

        return ValidationResult.valid(startIndex, maxResults);
    }

    private String fetchArxivData(String query, int start, int maxResults) throws ArxivApiException {
        String encodedQuery = URLEncoder.encode(query.trim(), StandardCharsets.UTF_8);
        String url = String.format("%s?search_query=%s&start=%d&max_results=%d",
                ARXIV_API_BASE_URL, encodedQuery, start, maxResults);

        logger.debug("Fetching data from ArXiv API: {}", url);

        try {
            String response = webClient.get()
                    .uri(url)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(REQUEST_TIMEOUT_SECONDS))
                    .block();

            if (response == null || response.trim().isEmpty()) {
                throw new ArxivApiException("Empty response received from arXiv API.");
            }

            logger.debug("Successfully received response from ArXiv API ({} characters)", response.length());
            return response;

        } catch (WebClientRequestException e) {
            throw new ArxivApiException("Network error while contacting arXiv: " + e.getMessage());
        } catch (WebClientResponseException e) {
            throw new ArxivApiException("arXiv API returned error " + e.getStatusCode() + ": " + e.getResponseBodyAsString());
        } catch (Exception e) {
            if (e.getCause() instanceof TimeoutException) {
                throw new ArxivApiException("Request to arXiv API timed out after " + REQUEST_TIMEOUT_SECONDS + " seconds.");
            }
            throw new ArxivApiException("Failed to fetch data from arXiv: " + e.getMessage());
        }
    }

    private String parseAndFormatResults(String xmlResponse, int maxResults) throws ArxivApiException {
        try {
            DocumentBuilder builder = documentBuilderFactory.newDocumentBuilder();
            Document document = builder.parse(new InputSource(new StringReader(xmlResponse)));

            NodeList entries = document.getElementsByTagNameNS(ATOM_NAMESPACE, "entry");

            if (entries.getLength() == 0) {
                logger.debug("No results found in ArXiv response");
                return "❌ No papers found matching your search criteria.";
            }

            return formatSearchResults(entries, maxResults);

        } catch (Exception e) {
            logger.error("Failed to parse ArXiv XML response", e);
            throw new ArxivApiException("Could not parse response from arXiv API.");
        }
    }

    private String formatSearchResults(NodeList entries, int maxResults) {
        StringBuilder results = new StringBuilder("📚 **ArXiv Search Results**\n\n");
        int resultCount = Math.min(entries.getLength(), maxResults);

        for (int i = 0; i < resultCount; i++) {
            try {
                Element entry = (Element) entries.item(i);
                ArxivPaper paper = extractPaperDetails(entry);
                results.append(formatPaper(i + 1, paper));
            } catch (Exception e) {
                logger.warn("Failed to process entry {}: {}", i + 1, e.getMessage());
                // Continue with other entries
            }
        }

        logger.debug("Formatted {} ArXiv results", resultCount);
        return results.toString();
    }

    private ArxivPaper extractPaperDetails(Element entry) {
        String title = getElementText(entry, "title");
        String publishedDate = extractPublishedDate(entry);
        String pdfUrl = extractPdfLink(entry);
        List<String> authors = extractAuthors(entry);
        String summary = extractAndTruncateSummary(entry);

        return new ArxivPaper(title, authors, publishedDate, pdfUrl, summary);
    }

    private String extractPublishedDate(Element entry) {
        String published = getElementText(entry, "published");
        if (published.length() >= 10) {
            return published.substring(0, 10); // Extract YYYY-MM-DD
        }
        return published;
    }

    private String extractAndTruncateSummary(Element entry) {
        String summary = getElementText(entry, "summary")
                .replaceAll("\\s+", " ")
                .trim();

        if (summary.length() > SUMMARY_MAX_LENGTH) {
            return summary.substring(0, SUMMARY_TRUNCATE_LENGTH) + "...";
        }
        return summary;
    }

    private String formatPaper(int index, ArxivPaper paper) {
        StringBuilder formatted = new StringBuilder();
        formatted.append(index).append(". **").append(paper.getTitle()).append("**\n");
        formatted.append("   📝 Authors: ").append(String.join(", ", paper.getAuthors())).append("\n");
        formatted.append("   📅 Published: ").append(paper.getPublishedDate()).append("\n");

        if (paper.getPdfUrl() != null) {
            formatted.append("   📄 [PDF](").append(paper.getPdfUrl()).append(")\n");
        }

        formatted.append("   📋 ").append(paper.getSummary()).append("\n\n");
        return formatted.toString();
    }

    // Helper methods for DOM navigation
    private String getElementText(Element parent, String tagName) {
        NodeList elements = parent.getElementsByTagNameNS("*", tagName);
        return elements.getLength() > 0 ? elements.item(0).getTextContent().trim() : "";
    }

    private List<String> extractAuthors(Element entry) {
        List<String> authors = new ArrayList<>();
        NodeList authorNodes = entry.getElementsByTagNameNS("*", "author");

        for (int i = 0; i < authorNodes.getLength(); i++) {
            Element authorElement = (Element) authorNodes.item(i);
            NodeList nameNodes = authorElement.getElementsByTagNameNS("*", "name");
            if (nameNodes.getLength() > 0) {
                authors.add(nameNodes.item(0).getTextContent().trim());
            }
        }
        return authors;
    }

    private String extractPdfLink(Element entry) {
        NodeList linkNodes = entry.getElementsByTagNameNS("*", "link");
        for (int i = 0; i < linkNodes.getLength(); i++) {
            Element linkElement = (Element) linkNodes.item(i);
            if (PDF_CONTENT_TYPE.equals(linkElement.getAttribute("type"))) {
                return linkElement.getAttribute("href");
            }
        }
        return null;
    }

    private int extractResultCount(String formattedResults) {
        // Simple heuristic to count results for logging
        return (int) formattedResults.lines().filter(line -> line.matches("^\\d+\\. \\*\\*.*")).count();
    }

    // Inner classes and exceptions
    private static class ValidationResult {
        private final boolean valid;
        private final String errorMessage;
        private final int startIndex;
        private final int maxResults;

        private ValidationResult(boolean valid, String errorMessage, int startIndex, int maxResults) {
            this.valid = valid;
            this.errorMessage = errorMessage;
            this.startIndex = startIndex;
            this.maxResults = maxResults;
        }

        static ValidationResult valid(int startIndex, int maxResults) {
            return new ValidationResult(true, null, startIndex, maxResults);
        }

        static ValidationResult invalid(String errorMessage) {
            return new ValidationResult(false, errorMessage, 0, 0);
        }

        boolean isValid() {
            return valid;
        }

        String getErrorMessage() {
            return errorMessage;
        }

        int getStartIndex() {
            return startIndex;
        }

        int getMaxResults() {
            return maxResults;
        }
    }

    private static class ArxivPaper {
        private final String title;
        private final List<String> authors;
        private final String publishedDate;
        private final String pdfUrl;
        private final String summary;

        ArxivPaper(String title, List<String> authors, String publishedDate, String pdfUrl, String summary) {
            this.title = title;
            this.authors = authors;
            this.publishedDate = publishedDate;
            this.pdfUrl = pdfUrl;
            this.summary = summary;
        }

        String getTitle() {
            return title;
        }

        List<String> getAuthors() {
            return authors;
        }

        String getPublishedDate() {
            return publishedDate;
        }

        String getPdfUrl() {
            return pdfUrl;
        }

        String getSummary() {
            return summary;
        }
    }

    private static class ArxivApiException extends Exception {
        ArxivApiException(String message) {
            super(message);
        }
    }
}





