package com.AI4Java.BackendAI;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("api/v1/health")
public class HealthCheckController {

    @GetMapping
    public ResponseEntity<Map<String, Object>> healthCheck() {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "UP");
        response.put("timestamp", LocalDateTime.now());
        response.put("service", "BackendAI");
        response.put("version", "0.0.1-SNAPSHOT");
        
        return ResponseEntity.ok(response);
    }

    @GetMapping("/check")
    public String check(){
        return "Everything is fine";
    }

}
