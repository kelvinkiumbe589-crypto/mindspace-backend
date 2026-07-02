package com.mindspace.controller;

import com.mindspace.service.MpesaService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Public M-Pesa payment endpoints for the booking flow.
 * - POST /api/payments/stk    : trigger an STK push to the phone
 * - POST /api/payments/query  : poll the status of a push
 * - POST /api/payments/callback : Safaricom posts the final result here
 */
@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private final MpesaService mpesaService;

    public PaymentController(MpesaService mpesaService) {
        this.mpesaService = mpesaService;
    }

    @PostMapping("/stk")
    public ResponseEntity<Map<String, Object>> stk(@RequestBody Map<String, Object> req) {
        String phone = String.valueOf(req.getOrDefault("phone", ""));
        int amount = parseAmount(req.get("amount"));
        String accountRef = req.get("accountReference") != null ? String.valueOf(req.get("accountReference")) : null;
        String desc = req.get("description") != null ? String.valueOf(req.get("description")) : null;
        return ResponseEntity.ok(mpesaService.stkPush(phone, amount, accountRef, desc));
    }

    @PostMapping("/query")
    public ResponseEntity<Map<String, Object>> query(@RequestBody Map<String, Object> req) {
        String checkoutRequestId = String.valueOf(req.getOrDefault("checkoutRequestId", ""));
        return ResponseEntity.ok(mpesaService.stkQuery(checkoutRequestId));
    }

    // Safaricom posts here after the user acts on the prompt. Must return 200.
    @PostMapping("/callback")
    public ResponseEntity<Map<String, Object>> callback(@RequestBody(required = false) Map<String, Object> payload) {
        System.out.println("M-Pesa STK callback: " + payload);
        return ResponseEntity.ok(Map.of("ResultCode", 0, "ResultDesc", "Accepted"));
    }

    // B2C payout: send the paid money out to a phone (e.g. the therapist).
    @PostMapping("/b2c")
    public ResponseEntity<Map<String, Object>> b2c(@RequestBody Map<String, Object> req) {
        String phone = String.valueOf(req.getOrDefault("phone", ""));
        int amount = parseAmount(req.get("amount"));
        String remarks = req.get("remarks") != null ? String.valueOf(req.get("remarks")) : null;
        return ResponseEntity.ok(mpesaService.b2cPayment(phone, amount, remarks));
    }

    @PostMapping("/b2c-result")
    public ResponseEntity<Map<String, Object>> b2cResult(@RequestBody(required = false) Map<String, Object> payload) {
        System.out.println("M-Pesa B2C result: " + payload);
        return ResponseEntity.ok(Map.of("ResultCode", 0, "ResultDesc", "Accepted"));
    }

    @PostMapping("/b2c-timeout")
    public ResponseEntity<Map<String, Object>> b2cTimeout(@RequestBody(required = false) Map<String, Object> payload) {
        System.out.println("M-Pesa B2C timeout: " + payload);
        return ResponseEntity.ok(Map.of("ResultCode", 0, "ResultDesc", "Accepted"));
    }

    private int parseAmount(Object raw) {
        if (raw == null) return 1;
        try {
            return (int) Math.round(Double.parseDouble(String.valueOf(raw).replaceAll("[^0-9.]", "")));
        } catch (Exception e) {
            return 1;
        }
    }
}
