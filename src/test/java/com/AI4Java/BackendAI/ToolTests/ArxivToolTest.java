package com.AI4Java.BackendAI.ToolTests;


import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import com.AI4Java.BackendAI.AI.tools.Free.ArxivApiTools;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@AutoConfigureMockMvc
public class ArxivToolTest {

    private static final Logger log = LoggerFactory.getLogger(ArxivToolTest.class);

    @Autowired
    private ArxivApiTools arxivApiTools;

    @Test
    void arxiv_search() {
        String response = arxivApiTools.arxiv_search("Machine learning", 0, 15);
        log.info(response);
        assertFalse(response.contains("❌"));
    }
}
