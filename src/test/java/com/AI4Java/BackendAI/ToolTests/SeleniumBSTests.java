package com.AI4Java.BackendAI.ToolTests;

import com.AI4Java.BackendAI.AI.tools.Free.SeleniumBrowserSearchTools;
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
public class SeleniumBSTests {

    private static final Logger log = LoggerFactory.getLogger(SeleniumBSTests.class);
    @Autowired
    private SeleniumBrowserSearchTools seleniumBrowserSearchTools;

    @Test
    void browserSearch(){
        String response = seleniumBrowserSearchTools.browserSearch("alaska summit 2025", "bing");
        log.info(response);
        assertFalse(response.contains("❌"));
    }
}
