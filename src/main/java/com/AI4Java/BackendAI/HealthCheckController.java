package com.AI4Java.BackendAI;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("api/v1/health")
public class HealthCheckController {
    private static final Logger logger = LoggerFactory.getLogger(HealthCheckController.class);
    @GetMapping
    public ResponseEntity<Map<String, Object>> healthCheck() {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "UP");
        response.put("timestamp", LocalDateTime.now().atZone(LocaleContextHolder.getTimeZone().toZoneId()).toString());
        response.put("service", "SpringAIApiTest");
        response.put("version", "0.0.1-SNAPSHOT");
        logger.info("Status Report {}", response);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/check")
    public String check(){
        return "Everything is fine";
    }

}
