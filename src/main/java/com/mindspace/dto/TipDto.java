package com.mindspace.dto;

public class TipDto {

    public static class CreateRequest {
        private int amount;
        private String name;
        private String message;

        public int getAmount() { return amount; }
        public String getName() { return name; }
        public String getMessage() { return message; }
        public void setAmount(int amount) { this.amount = amount; }
        public void setName(String name) { this.name = name; }
        public void setMessage(String message) { this.message = message; }
    }

    public static class PaidRequest {
        private String orderTrackingId;
        public String getOrderTrackingId() { return orderTrackingId; }
        public void setOrderTrackingId(String orderTrackingId) { this.orderTrackingId = orderTrackingId; }
    }

    public static class Response {
        public String id;
        public String name;
        public String message;
        public int amount;
        public String status;
        public String createdAt;
    }
}
