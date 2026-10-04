package com.mariageplus.controller;

import com.mariageplus.service.StorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class HealthController {

    private final StorageService storageService;

    @GetMapping("/health")
    public Map<String, String> health() {
        Map<String, String> body = new LinkedHashMap<>();
        body.put("status", "UP");
        body.put("service", "mariageplus-backend");
        body.put("storage", storageService.isEnabled() ? "s3" : "database");
        return body;
    }
}