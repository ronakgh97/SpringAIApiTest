package com.AI4Java.BackendAI.AI;

import com.AI4Java.BackendAI.entries.MessageEntries;
import com.AI4Java.BackendAI.entries.SessionEntries;
import com.AI4Java.BackendAI.services.SessionServices;
import org.bson.types.ObjectId;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class EmbeddedMemory implements ChatMemory {
    private final SessionServices sessionServices;
    private final int maxMessages;

    public EmbeddedMemory(SessionServices sessionServices) {
        this.sessionServices = sessionServices;
        this.maxMessages = 8192;
    }

    @Override
    public void add(@NonNull String conversationId, Message message) {
        ObjectId sessionId = new ObjectId(conversationId);
        SessionEntries tempSession = sessionServices.checkIfExists(sessionId);

        String role = (message.getMessageType() == MessageType.USER) ? "user" : "assistant";
        MessageEntries newEntry = new MessageEntries(role, message.getText(), LocalDateTime.now());

        tempSession.getMessages().add(newEntry);
        sessionServices.simpleSave(tempSession); // Persists to DB
    }

    @Override
    public void add(@NonNull String conversationId, List<Message> messages) {
        if (messages.isEmpty()) return;

        ObjectId sessionId = new ObjectId(conversationId);
        SessionEntries tempSession = sessionServices.checkIfExists(sessionId);

        List<MessageEntries> newEntries = messages.stream()
                .map(msg -> {
                    String role = (msg.getMessageType() == MessageType.USER) ? "user" : "assistant";
                    return new MessageEntries(role, msg.getText(), LocalDateTime.now());
                })
                .toList();

        tempSession.getMessages().addAll(newEntries);
        sessionServices.simpleSave(tempSession);
    }

    @Override
    @NonNull
    public List<Message> get(@NonNull String conversationId) {
        ObjectId sessionId = new ObjectId(conversationId);
        SessionEntries tempSession = sessionServices.checkIfExists(sessionId);

        List<MessageEntries> embeddedMessages = tempSession.getMessages();
        if (embeddedMessages.isEmpty()) return new ArrayList<>();

        // Sort by timestamp and limit to maxMessages
        List<MessageEntries> limitedMessages = embeddedMessages.stream()
                .sorted(Comparator.comparing(MessageEntries::getTimestamp))
                .skip(Math.max(0, embeddedMessages.size() - maxMessages))
                .toList();

        // Map to Spring AI Message
        return limitedMessages.stream()
                .map(entry -> {
                    if (entry.getRole().equals("user")) {
                        return new UserMessage(entry.getContent());
                    } else {
                        return new AssistantMessage(entry.getContent());
                    }
                })
                .collect(Collectors.toList());

    }

    @Override
    public void clear(@NonNull String conversationId) {
        ObjectId sessionId = new ObjectId(conversationId);
        SessionEntries tempSession = sessionServices.checkIfExists(sessionId);

        tempSession.getMessages().clear();
        sessionServices.simpleSave(tempSession);
    }
}
