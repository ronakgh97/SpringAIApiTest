package com.AI4Java.BackendAI.ToolTests;

import com.AI4Java.BackendAI.AI.tools.Free.WebScraperTools;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
@AutoConfigureMockMvc
public class WebScraperToolTests {

    private static final Logger log = LoggerFactory.getLogger(WebScraperToolTests.class);
    @Autowired
    private WebScraperTools webScraperTools;

    @Test
    void scrape_webpage(){
        String response = webScraperTools.scrape_webpage("https://github.com/google-gemini/gemini-cli");
        log.info(response);
        assertFalse(response.contains("❌"));
    }

    @Test
    void extract_structured_data(){
        String response = webScraperTools.extract_structured_data("https://coinmarketcap.com/","");
        log.info(response);
        assertFalse(response.contains("❌"));
    }
}
