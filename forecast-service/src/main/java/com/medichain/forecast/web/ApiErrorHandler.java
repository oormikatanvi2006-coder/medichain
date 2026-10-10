package com.medichain.forecast.web;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;

/** Turns inventory-service failures into readable JSON errors for the UI. */
@RestControllerAdvice
public class ApiErrorHandler {
    private static final Pattern MESSAGE = Pattern.compile("\"message\"\\s*:\\s*\"([^\"]*)\"");

    @ExceptionHandler(ResourceAccessException.class)
    public ResponseEntity<Map<String, String>> inventoryDown(ResourceAccessException e) {
        return ResponseEntity.status(503).body(Map.of("message",
                "inventory-service is not reachable. Start it on port 8081, then try again."));
    }

    @ExceptionHandler(RestClientResponseException.class)
    public ResponseEntity<Map<String, String>> inventoryError(RestClientResponseException e) {
        Matcher m = MESSAGE.matcher(e.getResponseBodyAsString());
        String message = m.find() ? m.group(1) : "inventory-service returned " + e.getStatusCode().value();
        return ResponseEntity.status(e.getStatusCode()).body(Map.of("message", message));
    }
}
