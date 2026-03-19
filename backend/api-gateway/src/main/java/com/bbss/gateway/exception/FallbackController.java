package com.bbss.gateway.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/fallback")
public class FallbackController {

    private ResponseEntity<Map<String, Object>> buildFallbackResponse(String requestId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", false);
        body.put("message", "Service temporarily unavailable. Please try again later.");
        body.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        if (requestId != null && !requestId.isBlank()) {
            body.put("requestId", requestId);
        }
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(body);
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> fallbackGet(
            @RequestHeader(value = "X-Request-ID", required = false) String requestId) {
        return buildFallbackResponse(requestId);
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> fallbackPost(
            @RequestHeader(value = "X-Request-ID", required = false) String requestId) {
        return buildFallbackResponse(requestId);
    }

    @PutMapping
    public ResponseEntity<Map<String, Object>> fallbackPut(
            @RequestHeader(value = "X-Request-ID", required = false) String requestId) {
        return buildFallbackResponse(requestId);
    }

    @DeleteMapping
    public ResponseEntity<Map<String, Object>> fallbackDelete(
            @RequestHeader(value = "X-Request-ID", required = false) String requestId) {
        return buildFallbackResponse(requestId);
    }

    @PatchMapping
    public ResponseEntity<Map<String, Object>> fallbackPatch(
            @RequestHeader(value = "X-Request-ID", required = false) String requestId) {
        return buildFallbackResponse(requestId);
    }
}
