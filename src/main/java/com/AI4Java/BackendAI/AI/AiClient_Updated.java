package com.AI4Java.BackendAI.AI;

import com.AI4Java.BackendAI.AI.tools.BasicTools;
import com.AI4Java.BackendAI.AI.tools.EmailTools;
import com.AI4Java.BackendAI.AI.tools.WebSearchTools;
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
import java.util.HashMap;
import java.util.Map;

@Service
public class AiClient_Updated {

    private static final Logger log = LoggerFactory.getLogger(AiClient_Updated.class);

    @Autowired
    private SessionServices sessionServices;

    @Autowired
    private UserServices userServices;

    @Autowired
    private BasicTools basicTools;

    @Autowired
    private EmailTools emailServiceTools;

    @Autowired
    private WebSearchTools webSearchTools;

    private final ChatMemory chatMemory;
    private final OpenAiApi openAiApi;
    private final String systemText;

    public AiClient_Updated(ChatMemory chatMemory,
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
                You are a English-Speaking Japanese Tour Assistant in Tokyo,
                Your name is Mitsubishi,
                YOU and this SYSTEM is made and trained by Mitsubishi Corporation.
                """;

        log.info("AiClient_Updated initialized successfully.");
    }

    public Flux<String> getAiResponse(ObjectId sessionId, String userPrompt, String username) {
        String convId = sessionId.toString();
        log.info("Getting AI response for session ID: {} (conversation ID: {}) for user: {}", sessionId, convId, username);
        log.debug("User prompt: {}", userPrompt);

        UserEntries userEntries = userServices.findByUserName(username);
        /*Map<String,String> userContext = new HashMap<>();
        userContext.put("userMail", userEntries.getGmail());*/

        SessionEntries sessionEntries = sessionServices.getById(sessionId)
                .orElseThrow(() -> new RuntimeException("Session not found with ID: " + sessionId));

        String model = sessionEntries.getModel();
        log.info("Using model: {}", model);

        OpenAiChatOptions openAiChatOptions = OpenAiChatOptions.builder()
                .model(model)
                .temperature(0.7)
                .maxTokens(256)
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
                .tools(basicTools, emailServiceTools, webSearchTools)
                .toolContext(Map.of("userMail", userEntries.getGmail()))
                .stream()
                .chatResponse()
                .doOnError(e -> log.error("Error during AI response streaming for session {}", convId, e))
                .map(response -> {
                    // Standard response format (e.g., OpenRouter)
                    if (response.getResult().getOutput().getText() != null) {
                        log.trace("Processing standard response format for session {}", convId);
                        return response.getResult().getOutput().getText();
                    }
                    // LM Studio specific format
                    else {
                        log.trace("Attempting to process LM Studio specific response format for session {}", convId);
                        if (response.getResult().getOutput().getMetadata().get("choices") instanceof java.util.List
                                choices) {
                            if (!choices.isEmpty() && choices.get(0) instanceof java.util.Map choiceMap) {
                                if (choiceMap.get("message") instanceof java.util.Map messageMap) {
                                    return (String) messageMap.get("content");
                                }
                            }
                        }
                    }
                    // Fallback for unknown formats
                    log.warn("No content found in AI response for session {}. Returning empty string.", convId);
                    return "";
                });


    }
}