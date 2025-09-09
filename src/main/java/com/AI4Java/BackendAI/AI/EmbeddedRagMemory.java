package com.AI4Java.BackendAI.AI;

import com.AI4Java.BackendAI.services.DropBoxSessionServices;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.stereotype.Component;

import java.util.List;

import org.springframework.beans.factory.annotation.Qualifier;

@Component
@Qualifier("embeddedRagMemory")
public class EmbeddedRagMemory implements ChatMemory {
    private final DropBoxSessionServices dropBoxSessionServices;
    private final int maxMessages;

    public EmbeddedRagMemory(DropBoxSessionServices dropBoxSessionServices){
        this.dropBoxSessionServices = dropBoxSessionServices;
        this.maxMessages = 8192;
    }

    @Override
    public void add(String conversationId, Message message) {
        ChatMemory.super.add(conversationId, message);
    }

    @Override
    public void add(String conversationId, List<Message> messages) {

    }

    @Override
    public List<Message> get(String conversationId) {
        return List.of();
    }

    @Override
    public void clear(String conversationId) {

    }
}
