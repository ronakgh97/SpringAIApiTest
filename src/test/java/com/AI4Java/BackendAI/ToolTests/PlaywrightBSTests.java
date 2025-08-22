package com.AI4Java.BackendAI.ToolTests;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.AI4Java.BackendAI.AI.tools.Free.PlaywrightBrowserSearchTools;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
@AutoConfigureMockMvc
public class PlaywrightBSTests {

    private static final Logger log = LoggerFactory.getLogger(PlaywrightBSTests.class);
    @Autowired
    private PlaywrightBrowserSearchTools playwrightBrowserSearchTools;

    @Test
    void playwrightSearch(){
        String response = playwrightBrowserSearchTools.playwrightSearch("AI Researcher", "duckduckgo");
        log.info(response);
        assertFalse(response.contains("❌"));
    }
}
