package com.mindspace.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/**
 * M-Pesa Daraja integration (Lipa Na M-Pesa Online / STK push).
 * Flow: get OAuth token -> send STK push -> (Safaricom prompts the phone) ->
 * result arrives via callback and/or we poll with STK Query.
 */
@Service
public class MpesaService {

    @Value("${app.mpesa.base-url}") private String baseUrl;
    @Value("${app.mpesa.consumer-key}") private String consumerKey;
    @Value("${app.mpesa.consumer-secret}") private String consumerSecret;
    @Value("${app.mpesa.shortcode}") private String shortcode;
    @Value("${app.mpesa.passkey}") private String passkey;
    @Value("${app.mpesa.callback-url}") private String callbackUrl;

    private final RestClient rest = RestClient.create();
    private final ObjectMapper mapper = new ObjectMapper();

    private String getAccessToken() {
        String creds = Base64.getEncoder()
                .encodeToString((consumerKey + ":" + consumerSecret).getBytes());
        Map<?, ?> res = rest.get()
                .uri(baseUrl + "/oauth/v1/generate?grant_type=client_credentials")
                .header("Authorization", "Basic " + creds)
                .retrieve()
                .body(Map.class);
        return res != null ? (String) res.get("access_token") : null;
    }

    private String timestamp() {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
    }

    private String password(String ts) {
        return Base64.getEncoder().encodeToString((shortcode + passkey + ts).getBytes());
    }

    /** Normalise Kenyan numbers to the 2547XXXXXXXX / 2541XXXXXXXX format Daraja expects. */
    public String normalizePhone(String phone) {
        String p = phone == null ? "" : phone.replaceAll("[^0-9]", "");
        if (p.startsWith("0")) return "254" + p.substring(1);
        if (p.startsWith("254")) return p;
        if (p.startsWith("7") || p.startsWith("1")) return "254" + p;
        return p;
    }

    public Map<String, Object> stkPush(String phone, int amount, String accountRef, String desc) {
        String token = getAccessToken();
        String ts = timestamp();
        String msisdn = normalizePhone(phone);

        Map<String, Object> body = new HashMap<>();
        body.put("BusinessShortCode", shortcode);
        body.put("Password", password(ts));
        body.put("Timestamp", ts);
        body.put("TransactionType", "CustomerPayBillOnline");
        body.put("Amount", Math.max(1, amount));
        body.put("PartyA", msisdn);
        body.put("PartyB", shortcode);
        body.put("PhoneNumber", msisdn);
        body.put("CallBackURL", callbackUrl);
        body.put("AccountReference", accountRef != null && !accountRef.isBlank() ? accountRef : "MindSpace");
        body.put("TransactionDesc", desc != null && !desc.isBlank() ? desc : "Therapy session");

        try {
            return rest.post()
                    .uri(baseUrl + "/mpesa/stkpush/v1/processrequest")
                    .header("Authorization", "Bearer " + token)
                    .header("Content-Type", "application/json")
                    .body(body)
                    .retrieve()
                    .body(Map.class);
        } catch (RestClientResponseException e) {
            return parseError(e);
        }
    }

    public Map<String, Object> stkQuery(String checkoutRequestId) {
        String token = getAccessToken();
        String ts = timestamp();

        Map<String, Object> body = new HashMap<>();
        body.put("BusinessShortCode", shortcode);
        body.put("Password", password(ts));
        body.put("Timestamp", ts);
        body.put("CheckoutRequestID", checkoutRequestId);

        try {
            return rest.post()
                    .uri(baseUrl + "/mpesa/stkpushquery/v1/query")
                    .header("Authorization", "Bearer " + token)
                    .header("Content-Type", "application/json")
                    .body(body)
                    .retrieve()
                    .body(Map.class);
        } catch (RestClientResponseException e) {
            return parseError(e);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseError(RestClientResponseException e) {
        try {
            return mapper.readValue(e.getResponseBodyAsString(), Map.class);
        } catch (Exception ex) {
            Map<String, Object> m = new HashMap<>();
            m.put("errorCode", "unknown");
            m.put("errorMessage", e.getMessage());
            return m;
        }
    }
}
