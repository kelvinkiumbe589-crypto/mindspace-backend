package com.mindspace.controller;

import com.mindspace.service.AnalyticsService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Public endpoint the web app pings on each page view. Unauthenticated on purpose —
 * logged-out visitors count too — and it stores nothing that identifies the person.
 */
@RestController
@RequestMapping("/api/analytics")
public class AnalyticsController {

    private final AnalyticsService analytics;

    public AnalyticsController(AnalyticsService analytics) {
        this.analytics = analytics;
    }

    @PostMapping("/pageview")
    public ResponseEntity<Void> pageview(@RequestBody(required = false) Map<String, String> body) {
        if (body != null) {
            analytics.record(body.get("path"), body.get("ref"), body.get("sessionId"));
        }
        return ResponseEntity.noContent().build();
    }
}
