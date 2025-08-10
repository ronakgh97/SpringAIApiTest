package com.AI4Java.BackendAI.AI.tools;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Component;
import java.time.LocalDateTime;


@Component
public class BasicTools {

    @Tool(name = "get_CurrentDateTime", description = "Get the current date and time in the user's timezone")
    String get_CurrentDateTime() {
        return LocalDateTime.now().atZone(LocaleContextHolder.getTimeZone().toZoneId()).toString();
    }

}
