package com.mindspace.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Sends WhatsApp messages via the Meta (WhatsApp Business) Cloud API. Business-
 * initiated messages must use a pre-approved template, so we send template
 * messages with text body parameters. Dormant until token + phoneNumberId are set.
 */
@Service
public class WhatsAppService {

    private static final Logger log = LoggerFactory.getLogger(WhatsAppService.class);

    @Value("${app.whatsapp.token:}")
    private String token;

    @Value("${app.whatsapp.phone-number-id:}")
    private String phoneNumberId;

    @Value("${app.whatsapp.api-version:v21.0}")
    private String apiVersion;

    @Value("${app.whatsapp.lang:en_US}")
    private String lang;

    private final RestClient rest;

    public WhatsAppService() {
        SimpleClientHttpRequestFactory f = new SimpleClientHttpRequestFactory();
        f.setConnectTimeout((int) Duration.ofSeconds(6).toMillis());
        f.setReadTimeout((int) Duration.ofSeconds(10).toMillis());
        this.rest = RestClient.builder().requestFactory(f).build();
    }

    public boolean isConfigured() {
        return token != null && !token.isBlank() && phoneNumberId != null && !phoneNumberId.isBlank();
    }

    /** Fire-and-forget template send. Never throws; safe to call from request threads. */
    public void sendTemplateAsync(String toPhone, String templateName, String... bodyParams) {
        if (!isConfigured() || toPhone == null || toPhone.isBlank() || templateName == null || templateName.isBlank()) return;
        String to = normalize(toPhone);
        if (to.isEmpty()) return;
        Thread t = new Thread(() -> {
            try {
                sendTemplate(to, templateName, bodyParams);
            } catch (Exception e) {
                log.warn("WhatsApp send failed to {}: {}", to, e.getMessage());
            }
        }, "whatsapp-send");
        t.setDaemon(true);
        t.start();
    }

    private void sendTemplate(String to, String templateName, String... bodyParams) {
        List<Map<String, Object>> params = new ArrayList<>();
        for (String p : bodyParams) {
            params.add(Map.of("type", "text", "text", p == null ? "" : p));
        }
        Map<String, Object> template = new java.util.LinkedHashMap<>();
        template.put("name", templateName);
        template.put("language", Map.of("code", lang));
        if (!params.isEmpty()) {
            template.put("components", List.of(Map.of("type", "body", "parameters", params)));
        }
        Map<String, Object> body = Map.of(
                "messaging_product", "whatsapp",
                "to", to,
                "type", "template",
                "template", template);
        rest.post()
                .uri("https://graph.facebook.com/" + apiVersion + "/" + phoneNumberId + "/messages")
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json")
                .body(body)
                .retrieve()
                .toBodilessEntity();
        log.info("WhatsApp template '{}' sent to {}", templateName, to);
    }

    // WhatsApp wants an international number in digits only (no +, spaces or leading 0).
    // Best-effort normalization with a Kenya default for local 07/01 numbers.
    private String normalize(String raw) {
        String digits = raw.replaceAll("[^0-9]", "");
        if (digits.startsWith("0") && digits.length() == 10) digits = "254" + digits.substring(1);
        return digits;
    }
}
