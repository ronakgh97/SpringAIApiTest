package com.AI4Java.BackendAI.AI.tools.Free;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;

@Service
public class ServerInfoTools {
    private static final Logger log = LoggerFactory.getLogger(ServerInfoTools.class);

    @Tool(name = "get_CurrentDateTime(SERVER)", description = "Get the current date and time in the server's timezone")
    String getCurrentDateTime() {
        log.info("Using get_CurrentDateTimeSERVER");
        return LocalDateTime.now().atZone(LocaleContextHolder.getTimeZone().toZoneId()).toString();
    }
}
