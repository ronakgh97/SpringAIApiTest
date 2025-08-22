package com.AI4Java.BackendAI.ToolTests;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.AI4Java.BackendAI.AI.tools.Free.WeatherTools;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;


@SpringBootTest
@AutoConfigureMockMvc
public class WeatherToolTests {

    private static final Logger log = LoggerFactory.getLogger(WeatherToolTests.class);

    @Autowired
    private WeatherTools weatherTools;

    @Test
    void get_current_weather(){
        String response = weatherTools.get_current_weather("Kyoto", "jp");
        log.info(response);
        assertFalse(response.contains("❌"));
    }

    @Test
    void get_weather_forecast(){
        String response = weatherTools.get_weather_forecast("Tokyo", "jp", "5");
        log.info(response);
        assertFalse(response.contains("❌"));;
    }

    @Test
    void compare_weather(){
        String response = weatherTools.compare_weather("Tokyo", "Mumbai");
        log.info(response);
        assertFalse(response.contains("❌"));
    }
}
