package com.koscom.kopilot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@SpringBootApplication
public class KopilotApplication {
    public static void main(String[] args) {
        SpringApplication.run(KopilotApplication.class, args);
    }

    @RestController
    static class HealthController {
        @GetMapping("/api/health")
        public String health() { return "ok"; }
    }
}
