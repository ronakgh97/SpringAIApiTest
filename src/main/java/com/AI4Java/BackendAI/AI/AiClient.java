package com.AI4Java.BackendAI.AI;

import com.AI4Java.BackendAI.entries.SessionEntries;
import com.AI4Java.BackendAI.repository.SessionRepo;
import com.AI4Java.BackendAI.services.SessionServices;
import org.bson.types.ObjectId;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.*;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Autowired;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;

@Deprecated
public class AiClient {

    private final OpenAiChatModel chatModel;
    private final SessionRepo sessionRepo;
    private final SessionServices sessionServices;
    private final ChatMemory chatMemory; // Custom embedded memory

    @Autowired
    public AiClient(OpenAiChatModel chatModel, SessionRepo sessionRepo, SessionServices sessionServices, ChatMemory chatMemory) {
        this.chatModel = chatModel;
        this.sessionRepo = sessionRepo;
        this.sessionServices = sessionServices;
        this.chatMemory = chatMemory;
    }

    public Flux<String> getAiResponse(ObjectId sessionId, String userPrompt, String modelName) {
        String convId = sessionId.toString();

        chatMemory.add(convId, new UserMessage(userPrompt));

        List<org.springframework.ai.chat.messages.Message> rawHistory = chatMemory.get(convId);

        List<org.springframework.ai.chat.messages.Message> chatHistory = new ArrayList<>(rawHistory.stream()
                .map(msg -> {
                    if (msg.getMessageType() == MessageType.USER) {
                        return new UserMessage(msg.getText());
                    } else if (msg.getMessageType() == MessageType.SYSTEM) {
                        return new SystemMessage(msg.getText());
                    } else {
                        return new AssistantMessage(msg.getText());
                    }
                })
                .toList());

        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .model(modelName)
                .temperature(0.7)
                .maxTokens(512)
                .build();

        String systemText = """
            You are a Japanese AI Assistant.
            Your name is Mitsubishi.
            """;
        Prompt prompt = new Prompt(chatHistory, options);
        StringBuilder responseBuilder = new StringBuilder();

        return chatModel
                .stream(prompt)
                .map(response -> {
                    String chunk = response.getResult().getOutput().getText() != null ? response.getResult().getOutput().getText(): "";
                    responseBuilder.append(chunk);
                    return chunk;
                })
                .doOnComplete(() -> {
                    String fullAiContent = responseBuilder.toString();
                    chatMemory.add(convId, new AssistantMessage(fullAiContent)); // Saves to embedded list

                    SessionEntries session = sessionServices.getById(sessionId).orElseThrow();
                    session.setModel(modelName);
                    sessionRepo.save(session);
                });
    }
}