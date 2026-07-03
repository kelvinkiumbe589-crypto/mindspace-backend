package com.mindspace.dto;

import java.util.List;

public class WalletDto {

    /** Therapist earnings snapshot. */
    public static class Earnings {
        public int pending;          // paid sessions not yet done
        public int available;        // done, not yet withdrawn
        public int totalEarned;      // all done
        public int withdrawn;        // net already paid out
        public int commissionPercent;
        public List<WithdrawalResponse> withdrawals;
    }

    public static class WithdrawalRequest {
        private int amount;   // gross; 0 or omitted = full available balance
        private String phone; // M-Pesa number to receive it

        public int getAmount() { return amount; }
        public String getPhone() { return phone; }
        public void setAmount(int amount) { this.amount = amount; }
        public void setPhone(String phone) { this.phone = phone; }
    }

    public static class WithdrawalResponse {
        public String id;
        public String therapistName;
        public String therapistEmail;
        public int grossAmount;
        public int commission;
        public int netAmount;
        public String phone;
        public String status;
        public String createdAt;
        public String paidAt;
    }
}
