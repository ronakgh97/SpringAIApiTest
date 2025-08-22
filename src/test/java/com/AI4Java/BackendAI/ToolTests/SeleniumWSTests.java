package com.AI4Java.BackendAI.ToolTests;

import com.AI4Java.BackendAI.AI.tools.Free.SeleniumWebScraperTools;
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
public class SeleniumWSTests {

    private static final Logger log = LoggerFactory.getLogger(SeleniumWSTests.class);

    @Autowired
    private SeleniumWebScraperTools seleniumWebScraperTools;

    @Test
    void scrape_webpage(){
        String response = seleniumWebScraperTools.scrape_webpage("https://www.nseindia.com/option-chain");
        log.info(response);
        assertFalse(response.contains("❌"));
    }

    @Test
    void extract_structured_data(){
        String response = seleniumWebScraperTools.extract_structured_data("https://www.tradingview.com/markets/","");
    }
}
