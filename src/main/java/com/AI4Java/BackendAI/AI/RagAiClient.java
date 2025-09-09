package com.AI4Java.BackendAI.AI;

import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Service
public class RagAiClient {

    private final ChatMemory chatMemory;

    public RagAiClient(@Qualifier("embeddedRagMemory") ChatMemory chatMemory) {
        this.chatMemory = chatMemory;
    }
}
