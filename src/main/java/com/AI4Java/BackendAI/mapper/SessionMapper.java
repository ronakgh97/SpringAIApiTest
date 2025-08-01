package com.AI4Java.BackendAI.mapper;

import com.AI4Java.BackendAI.dto.message.MessageResponseDto;
import com.AI4Java.BackendAI.dto.session.SessionCreateDto;
import com.AI4Java.BackendAI.dto.session.SessionResponseDto;
import com.AI4Java.BackendAI.entries.MessageEntries;
import com.AI4Java.BackendAI.entries.SessionEntries;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class SessionMapper {

    public SessionEntries toEntity(SessionCreateDto dto) {
        SessionEntries session = new SessionEntries();
        session.setNameSession(dto.getNameSession());
        session.setModel(dto.getModel());
        session.setDateTime(LocalDateTime.now());
        session.setMessages(new ArrayList<>());
        return session;
    }

    public SessionResponseDto toResponseDto(SessionEntries entity) {
        List<MessageResponseDto> messageDtos = entity.getMessages() != null ?
            entity.getMessages().stream()
                .map(this::messageToDto)
                .collect(Collectors.toList()) :
            new ArrayList<>();

        return new SessionResponseDto(
            entity.getSessionId() != null ? entity.getSessionId().toString() : null,
            entity.getNameSession(),
            entity.getModel(),
            entity.getDateTime(),
            messageDtos,
            messageDtos.size()
        );
    }

    public SessionResponseDto toResponseDtoWithoutMessages(SessionEntries entity) {
        return new SessionResponseDto(
            entity.getSessionId() != null ? entity.getSessionId().toString() : null,
            entity.getNameSession(),
            entity.getModel(),
            entity.getDateTime(),
            null, // Don't include messages for list views
            entity.getMessages() != null ? entity.getMessages().size() : 0
        );
    }

    private MessageResponseDto messageToDto(MessageEntries message) {
        return new MessageResponseDto(
            message.getRole(),
            message.getContent(),
            message.getTimestamp()
        );
    }
}
