package com.mindspace.controller;

import com.mindspace.service.MpesaService;
import com.mindspace.service.PesapalService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Public payment endpoints for the booking flow.
 * Pesapal (aggregator — M-Pesa/card/bank in one hosted checkout):
 * - POST /api/payments/pesapal/order  : create an order, get the checkout URL
 * - GET  /api/payments/pesapal/status : poll the order status
 * - POST /api/payments/pesapal/ipn    : Pesapal posts payment notifications here
 * Legacy direct M-Pesa (STK push + B2C) endpoints are kept below.
 */
@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private final MpesaService mpesaService;
    private final PesapalService pesapalService;

    public PaymentController(MpesaService mpesaService, PesapalService pesapalService) {
        this.mpesaService = mpesaService;
        this.pesapalService = pesapalService;
    }

    // ── Pesapal ──

    @PostMapping("/pesapal/order")
    public ResponseEntity<Map<String, Object>> pesapalOrder(@RequestBody Map<String, Object> req) {
        if (!pesapalService.isConfigured()) {
            return ResponseEntity.status(503).body(Map.of("error", "Pesapal is not configured on the server."));
        }
        double amount = parseAmountDouble(req.get("amount"));
        String desc = str(req.get("description"));
        String email = str(req.get("email"));
        String phone = str(req.get("phone"));
        String firstName = str(req.get("firstName"));
        String lastName = str(req.get("lastName"));
        try {
            return ResponseEntity.ok(pesapalService.submitOrder(amount, desc, email, phone, firstName, lastName));
        } catch (Exception e) {
            return ResponseEntity.status(502).body(Map.of("error", "Could not start Pesapal checkout: " + e.getMessage()));
        }
    }

    @GetMapping("/pesapal/status")
    public ResponseEntity<Map<String, Object>> pesapalStatus(@RequestParam String orderTrackingId) {
        try {
            return ResponseEntity.ok(pesapalService.getStatus(orderTrackingId));
        } catch (Exception e) {
            return ResponseEntity.status(502).body(Map.of("error", e.getMessage()));
        }
    }

    // Pesapal posts an IPN here when a payment's status changes. Must return 200.
    @RequestMapping(value = "/pesapal/ipn", method = {RequestMethod.POST, RequestMethod.GET})
    public ResponseEntity<Map<String, Object>> pesapalIpn(@RequestParam(required = false) String OrderTrackingId,
                                                          @RequestParam(required = false) String OrderNotificationType,
                                                          @RequestParam(required = false) String OrderMerchantReference) {
        System.out.println("Pesapal IPN: tracking=" + OrderTrackingId + " type=" + OrderNotificationType + " ref=" + OrderMerchantReference);
        Map<String, Object> ack = Map.of(
                "orderNotificationType", OrderNotificationType == null ? "" : OrderNotificationType,
                "orderTrackingId", OrderTrackingId == null ? "" : OrderTrackingId,
                "orderMerchantReference", OrderMerchantReference == null ? "" : OrderMerchantReference,
                "status", 200);
        return ResponseEntity.ok(ack);
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

    private double parseAmountDouble(Object raw) {
        if (raw == null) return 1;
        try {
            return Double.parseDouble(String.valueOf(raw).replaceAll("[^0-9.]", ""));
        } catch (Exception e) {
            return 1;
        }
    }

    private String str(Object raw) {
        return raw == null ? "" : String.valueOf(raw);
    }
}
