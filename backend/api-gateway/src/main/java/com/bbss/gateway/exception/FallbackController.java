package com.bbss.gateway.exception;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/fallback")
public class FallbackController {

    private final ObjectMapper objectMapper;

    public FallbackController(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @RequestMapping
    public Mono<Void> fallback(
            ServerWebExchange exchange,
            @RequestHeader(value = "X-Request-ID", required = false) String requestId) {
        ServerHttpResponse response = exchange.getResponse();

        if (!response.isCommitted()) {
            response.setStatusCode(HttpStatus.SERVICE_UNAVAILABLE);
            response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        }

        DataBuffer buffer = response.bufferFactory().wrap(buildBody(requestId));
        return response.writeWith(Mono.just(buffer));
    }

    private byte[] buildBody(String requestId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", false);
        body.put("message", "Service temporarily unavailable. Please try again later.");
        body.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        if (requestId != null && !requestId.isBlank()) {
            body.put("requestId", requestId);
        }

        try {
            return objectMapper.writeValueAsBytes(body);
        } catch (JsonProcessingException ex) {
            return ("{\"success\":false,\"message\":\"Service temporarily unavailable. Please try again later.\"}")
                    .getBytes(StandardCharsets.UTF_8);
        }
    }
}
