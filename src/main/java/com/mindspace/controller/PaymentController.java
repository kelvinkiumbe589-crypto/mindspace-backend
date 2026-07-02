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

    private int parseAmount(Object raw) {
        if (raw == null) return 1;
        try {
            return (int) Math.round(Double.parseDouble(String.valueOf(raw).replaceAll("[^0-9.]", "")));
        } catch (Exception e) {
            return 1;
        }
    }
}
