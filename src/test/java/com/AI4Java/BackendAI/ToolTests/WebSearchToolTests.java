package com.AI4Java.BackendAI.ToolTests;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.AI4Java.BackendAI.AI.tools.Free.WebSearchTools;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
@AutoConfigureMockMvc
public class WebSearchToolTests {

    private static final Logger log = LoggerFactory.getLogger(WebSearchToolTests.class);
    @Autowired
    private WebSearchTools webSearchTools;

    @Test
    void webSearch(){
        String response = webSearchTools.webSearch("GenAI");
        log.info(response);
        assertFalse(response.contains("❌"));
    }
}
