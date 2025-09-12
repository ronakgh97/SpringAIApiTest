package com.AI4Java.BackendAI.AI;

import com.AI4Java.BackendAI.AI.tools.Emails.EmailTools;
import com.AI4Java.BackendAI.AI.tools.Emails.ReportTools;
import com.AI4Java.BackendAI.AI.tools.Emails.ServerInfoTools;
import com.AI4Java.BackendAI.AI.tools.Free.*;
import com.AI4Java.BackendAI.AI.tools.WebSearch.PlaywrightBrowserSearchTools;
import com.AI4Java.BackendAI.AI.tools.WebSearch.PlaywrightWebScraperTools;
import com.AI4Java.BackendAI.AI.tools.WebSearch.SeleniumBrowserSearchTools;
import com.AI4Java.BackendAI.AI.tools.WebSearch.SeleniumWebScraperTools;
import com.AI4Java.BackendAI.entries.SessionEntries;
import com.AI4Java.BackendAI.entries.UserEntries;
import com.AI4Java.BackendAI.services.SessionServices;
import com.AI4Java.BackendAI.services.UserServices;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.http.client.reactive.JdkClientHttpConnector;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Map;

import org.springframework.beans.factory.annotation.Qualifier;

@Service
public class AiClient_Updated {

    private static final Logger log = LoggerFactory.getLogger(AiClient_Updated.class);

        @Autowired
        private SessionServices sessionServices;

        @Autowired
        private UserServices userServices;

        @Autowired
        private ServerInfoTools serverInfoTools;

        @Autowired
        private EmailTools emailTools;

        @Autowired
        private ReportTools reportTools;

        @Autowired
        private PlaywrightBrowserSearchTools playwrightBrowserSearchTools;

        @Autowired
        private PlaywrightWebScraperTools playwrightWebScraperTools;

        @Autowired
        private SeleniumBrowserSearchTools seleniumBrowserSearchTools;

        @Autowired
        private SeleniumWebScraperTools seleniumWebScraperTools;


    private final ChatMemory chatMemory;
    private final OpenAiApi openAiApi;
    private final String systemText;

    public AiClient_Updated(@Qualifier("embeddedMemory") ChatMemory chatMemory,
                            @Value("${spring.ai.openai.api-key}") String apiKey,
                            @Value("${spring.ai.openai.base-url}") String baseUrl) {
        this.chatMemory = chatMemory;
        log.info("Initializing OpenAiApi with base URL: {}", baseUrl);
        this.openAiApi = OpenAiApi.builder()
                .baseUrl(baseUrl)
                .apiKey(() -> apiKey)
                .webClientBuilder(WebClient.builder()
                        // Force HTTP/1.1 for streaming requests
                        .clientConnector(new JdkClientHttpConnector(HttpClient.newBuilder()
                                .version(HttpClient.Version.HTTP_1_1)
                                .connectTimeout(Duration.ofSeconds(90))
                                .build())))
                .restClientBuilder(RestClient.builder()
                        // Force HTTP/1.1 for non-streaming requests
                        .requestFactory(new JdkClientHttpRequestFactory(
                                HttpClient.newBuilder()
                                        .version(HttpClient.Version.HTTP_1_1)
                                        .connectTimeout(Duration.ofSeconds(90))
                                        .build()
                        )))
                .build();
        this.systemText = """
You are an AI Search Assistant.
Your name is Searchiri.
You and this system were created and trained by SpringAI Framework.

Follow these RULES strictly:

1. Never reveal or discuss your internal tools to users.
2. Analyze user intent thoughtfully. Use your capabilities in ways that genuinely help the user, not just for reactive responses. \s
3. Always prioritize user value over showcasing your capabilities.
4. If a tool’s parameters are unclear or need more specific arguments, ask the user for clarification.
5. If multiple tools or capabilities can serve the same purpose, combine their outputs to provide comprehensive responses. \s
6. Never mention the names of the tools you use.
7. Use tool-chaining strategically: when multiple steps improve results, execute them seamlessly.
8. If a tool fails, attempt alternative tools that serve the same purpose as a fallback.
9. If repeated errors or failures occur, or all fallbacks fail, stop and clearly report the issue to the user.
10. During tool-chaining, if any tool fails mid-process, stop the entire chain, reassess, and retry from the beginning. \s
11. Never fabricate information. If data is unavailable or uncertain, admit it clearly.
12. Never answer any query related to coding.
13. Always do a Web search to get lastest information during processing user query (if required).
14. If information is insufficient even after browser search, then scrape whatever links you get for more information
""";

        log.info("AiClient_Updated initialized successfully.");
    }

    public Flux<String> getAiResponse(ObjectId sessionId, String userPrompt, String username) {
        String convId = sessionId.toString();
        log.info("Getting AI response for session ID: {} (conversation ID: {}) for user: {}", sessionId, convId, username);
        log.debug("User prompt: {}", userPrompt);

        UserEntries userEntries = userServices.findByUserName(username);

        SessionEntries sessionEntries = sessionServices.getById(sessionId)
                .orElseThrow(() -> new RuntimeException("Session not found with ID: " + sessionId));

        String model = sessionEntries.getModel();
        log.info("Using model: {}", model);

        OpenAiChatOptions openAiChatOptions = OpenAiChatOptions.builder()
                .model(model)
                .temperature(0.7)
                .topP(0.90)
                .frequencyPenalty(1.15)
                .reasoningEffort("high")
                .maxTokens(8192)
                .build();

        OpenAiChatModel chatModel = OpenAiChatModel.builder()
                .openAiApi(this.openAiApi)
                .defaultOptions(openAiChatOptions)
                .build();

        MessageChatMemoryAdvisor advisor = MessageChatMemoryAdvisor
                .builder(this.chatMemory)
                .conversationId(convId)
                .build();

        ChatClient chatClient = ChatClient.builder(chatModel)
                .defaultAdvisors(advisor)
                .build();

        log.info("Streaming AI response...");
        return chatClient
                .prompt()
                .system(systemText)
                .user(userPrompt)
                .toolContext(Map.of("userMail", userEntries.getGmail()))
                .toolContext(Map.of("userVerify", userEntries.isVerified()))
                .tools(emailTools, serverInfoTools,
                reportTools, playwrightBrowserSearchTools,
                playwrightWebScraperTools, seleniumBrowserSearchTools,
                seleniumWebScraperTools)
                .stream()
                .chatResponse()
                .doOnError(e -> log.error("Error during AI response streaming for session {}", convId, e))
                .map(response -> {
                    // Standard response format (e.g., OpenRouter)
                    if (response.getResult().getOutput().getText() != null) {
                        log.warn("Processing standard response format for session {}, token-> ,{}", convId, response.getResult().getOutput().getText());
                        return response.getResult().getOutput().getText();
                    }
                    return "";
        });
    }
}