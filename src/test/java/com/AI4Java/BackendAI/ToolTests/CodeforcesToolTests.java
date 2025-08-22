package com.AI4Java.BackendAI.ToolTests;

import com.AI4Java.BackendAI.AI.tools.Free.CodeforcesProblemSetTools;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
@AutoConfigureMockMvc
public class CodeforcesToolTests {

    private static final Logger log = LoggerFactory.getLogger(CodeforcesToolTests.class);
    @Autowired
    private CodeforcesProblemSetTools codeforcesProblemSetTools;

    @Test
    void cf_problem_info(){
        String response = codeforcesProblemSetTools.cf_problem_info("2000","B");
        log.info(response);
        assertFalse(response.contains("❌"));
    }
    @Test
    void cf_problem_search(){
        String response = codeforcesProblemSetTools.cf_problem_search("brute force",1200);
        log.info(response);
        assertFalse(response.contains("❌"));
    }
}
