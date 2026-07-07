package com.mindspace.controller;

import com.mindspace.service.ReminderService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/reminders")
public class ReminderController {

    private final ReminderService reminderService;

    // Shared secret an external cron must present to trigger the batch. Blank = disabled.
    @Value("${app.reminder.trigger-key:}")
    private String triggerKey;

    public ReminderController(ReminderService reminderService) {
        this.reminderService = reminderService;
    }

    // ── Internal trigger (called by an external cron, e.g. cron-job.org) ──
    // POST /api/reminders/run?key=SECRET
    @PostMapping("/run")
    public ResponseEntity<Map<String, Object>> run(@RequestParam(required = false) String key) {
        if (triggerKey == null || triggerKey.isBlank()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("status", "disabled", "message", "Reminder trigger key is not configured"));
        }
        if (!triggerKey.equals(key)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("status", "forbidden"));
        }
        boolean started = reminderService.triggerAsync();
        return ResponseEntity.accepted()
                .body(Map.of("status", started ? "started" : "already-running"));
    }

    // ── One-click unsubscribe from the email link ──
    // GET /api/reminders/unsubscribe?token=...
    @GetMapping(value = "/unsubscribe", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> unsubscribe(@RequestParam String token) {
        boolean ok = reminderService.unsubscribe(token);
        String msg = ok
                ? "You've been unsubscribed from daily mood reminders. You can re-enable them anytime in Settings."
                : "This unsubscribe link is invalid or has expired.";
        String html = "<!doctype html><html><head><meta charset=\"utf-8\">" +
                "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">" +
                "<title>MindSpace reminders</title></head>" +
                "<body style=\"font-family:system-ui,sans-serif;background:#0d0d14;color:#e8e6ff;" +
                "display:flex;min-height:100vh;align-items:center;justify-content:center;margin:0;padding:24px\">" +
                "<div style=\"max-width:420px;text-align:center;background:rgba(255,255,255,0.04);" +
                "border:1px solid rgba(127,119,221,0.2);border-radius:18px;padding:32px\">" +
                "<div style=\"font-size:32px;margin-bottom:12px\">🧠</div>" +
                "<h1 style=\"font-size:20px;font-weight:600;margin:0 0 10px\">MindSpace</h1>" +
                "<p style=\"font-size:14px;color:#9d9bc4;line-height:1.6;margin:0\">" + msg + "</p>" +
                "</div></body></html>";
        return ResponseEntity.status(ok ? HttpStatus.OK : HttpStatus.BAD_REQUEST).body(html);
    }

    // ── Logged-in preference toggle (used by the Settings page) ──
    @GetMapping("/preference")
    public Map<String, Boolean> getPreference(@AuthenticationPrincipal UserDetails userDetails) {
        return Map.of("enabled", reminderService.getPreference(userDetails.getUsername()));
    }

    @PutMapping("/preference")
    public Map<String, Boolean> setPreference(@AuthenticationPrincipal UserDetails userDetails,
                                              @RequestBody Map<String, Boolean> body) {
        boolean enabled = Boolean.TRUE.equals(body.get("enabled"));
        reminderService.setPreference(userDetails.getUsername(), enabled);
        return Map.of("enabled", enabled);
    }
}
