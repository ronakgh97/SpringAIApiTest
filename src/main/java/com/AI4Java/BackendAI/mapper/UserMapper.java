package com.AI4Java.BackendAI.mapper;

import com.AI4Java.BackendAI.dto.user.UserRegistrationDto;
import com.AI4Java.BackendAI.dto.user.UserResponseDto;
import com.AI4Java.BackendAI.entries.UserEntries;
import org.springframework.stereotype.Component;

import java.util.ArrayList;

@Component
public class UserMapper {

    public UserEntries toEntity(UserRegistrationDto dto) {
        UserEntries user = new UserEntries();
        user.setUserName(dto.getUserName());
        user.setPassword(dto.getPassword()); // Will be encoded in service layer
        user.setGmail(dto.getGmail());
        user.setSessionEntries(new ArrayList<>());
        user.setRoles(new ArrayList<>());
        return user;
    }

    public UserResponseDto toResponseDto(UserEntries entity) {
        return new UserResponseDto(
            entity.getUserId() != null ? entity.getUserId().toString() : null,
            entity.getUserName(),
            entity.getGmail(),
            entity.getRoles(),
            entity.getSessionEntries() != null ? entity.getSessionEntries().size() : 0
        );
    }
}
