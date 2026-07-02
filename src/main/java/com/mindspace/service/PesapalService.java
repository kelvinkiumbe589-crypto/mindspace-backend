package com.mindspace.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Pesapal API v3 integration (hosted checkout / aggregator).
 * Flow: RequestToken -> RegisterIPN (once) -> SubmitOrderRequest (returns a
 * redirect_url we embed in an iframe) -> the user pays via M-Pesa/card/bank ->
 * we confirm with GetTransactionStatus. Collected funds settle to the merchant's
 * Pesapal account.
 */
@Service
public class PesapalService {

    @Value("${app.pesapal.base-url:https://pay.pesapal.com/v3}") private String baseUrl;
    @Value("${app.pesapal.consumer-key:}") private String consumerKey;
    @Value("${app.pesapal.consumer-secret:}") private String consumerSecret;
    @Value("${app.pesapal.callback-url:}") private String callbackUrl;
    @Value("${app.pesapal.ipn-url:}") private String ipnUrl;
    @Value("${app.pesapal.currency:KES}") private String currency;

    private final RestClient rest = RestClient.create();
    private volatile String cachedIpnId; // registered once, reused

    public boolean isConfigured() {
        return !consumerKey.isBlank() && !consumerSecret.isBlank();
    }

    private String getToken() {
        Map<String, String> body = Map.of(
                "consumer_key", consumerKey,
                "consumer_secret", consumerSecret);
        Map<?, ?> res = rest.post()
                .uri(baseUrl + "/api/Auth/RequestToken")
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .body(body)
                .retrieve()
                .body(Map.class);
        Object token = res != null ? res.get("token") : null;
        if (token == null || String.valueOf(token).isBlank()) {
            String msg = res != null ? String.valueOf(res.get("message")) : "no response";
            throw new IllegalStateException("Pesapal auth failed: " + msg);
        }
        return String.valueOf(token);
    }

    private String ensureIpnId(String token) {
        if (cachedIpnId != null) return cachedIpnId;
        Map<String, String> body = Map.of(
                "url", ipnUrl,
                "ipn_notification_type", "POST");
        Map<?, ?> res = rest.post()
                .uri(baseUrl + "/api/URLSetup/RegisterIPN")
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .body(body)
                .retrieve()
                .body(Map.class);
        Object id = res != null ? res.get("ipn_id") : null;
        if (id == null) {
            throw new IllegalStateException("Pesapal IPN registration failed: " + res);
        }
        cachedIpnId = String.valueOf(id);
        return cachedIpnId;
    }

    /**
     * Create an order and return the redirect_url (for the iframe) plus the
     * order_tracking_id (for polling status).
     */
    public Map<String, Object> submitOrder(double amount, String description, String email,
                                           String phone, String firstName, String lastName) {
        String token = getToken();
        String ipnId = ensureIpnId(token);
        String merchantRef = "MS-" + UUID.randomUUID();

        Map<String, Object> billing = new LinkedHashMap<>();
        billing.put("email_address", email == null ? "" : email);
        billing.put("phone_number", phone == null ? "" : phone);
        billing.put("first_name", firstName == null ? "" : firstName);
        billing.put("last_name", lastName == null ? "" : lastName);

        Map<String, Object> order = new LinkedHashMap<>();
        order.put("id", merchantRef);
        order.put("currency", currency);
        order.put("amount", amount);
        order.put("description", description == null ? "MindSpace session" : description);
        order.put("callback_url", callbackUrl);
        order.put("notification_id", ipnId);
        order.put("billing_address", billing);

        Map<?, ?> res = rest.post()
                .uri(baseUrl + "/api/Transactions/SubmitOrderRequest")
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .body(order)
                .retrieve()
                .body(Map.class);

        Map<String, Object> out = new HashMap<>();
        if (res != null) {
            out.put("orderTrackingId", res.get("order_tracking_id"));
            out.put("merchantReference", res.get("merchant_reference"));
            out.put("redirectUrl", res.get("redirect_url"));
            out.put("error", res.get("error"));
            out.put("status", res.get("status"));
        }
        return out;
    }

    /** Look up the current status of an order (Completed / Failed / Invalid / Reversed / Pending). */
    public Map<String, Object> getStatus(String orderTrackingId) {
        String token = getToken();
        Map<?, ?> res = rest.get()
                .uri(baseUrl + "/api/Transactions/GetTransactionStatus?orderTrackingId=" + orderTrackingId)
                .header("Authorization", "Bearer " + token)
                .header("Accept", "application/json")
                .retrieve()
                .body(Map.class);

        Map<String, Object> out = new HashMap<>();
        if (res != null) {
            out.put("orderTrackingId", orderTrackingId);
            out.put("paymentStatus", res.get("payment_status_description")); // e.g. "Completed"
            out.put("statusCode", res.get("status_code"));                   // 1=Completed 2=Failed 0=Invalid 3=Reversed
            out.put("paymentMethod", res.get("payment_method"));
            out.put("amount", res.get("amount"));
            out.put("confirmationCode", res.get("confirmation_code"));
            out.put("description", res.get("description"));
        }
        return out;
    }
}
