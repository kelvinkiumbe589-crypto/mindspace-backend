package com.mindspace.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class AuthDto {

    // Change messaging handle (Settings).
    public static class HandleRequest {
        @NotBlank(message = "Username is required")
        @Size(min = 3, max = 20, message = "Username must be 3-20 characters")
        private String handle;
        public String getHandle() { return handle; }
        public void setHandle(String handle) { this.handle = handle; }
    }

    public static class RegisterRequest {
        // Real/display name (shown in greetings, emails, non-anonymous forum posts).
        @NotBlank(message = "Username is required")
        @Size(min = 3, max = 50, message = "Username must be 3-50 characters")
        private String username;

        // Public messaging handle other members search by (grace_ke).
        @NotBlank(message = "Username is required")
        @Size(min = 3, max = 20, message = "Username must be 3-20 characters")
        private String handle;

        @Email(message = "Invalid email address")
        @NotBlank(message = "Email is required")
        private String email;

        @NotBlank(message = "Password is required")
        @Size(min = 6, message = "Password must be at least 6 characters")
        private String password;

        public String getUsername() { return username; }
        public String getHandle() { return handle; }
        public String getEmail() { return email; }
        public String getPassword() { return password; }
        public void setUsername(String username) { this.username = username; }
        public void setHandle(String handle) { this.handle = handle; }
        public void setEmail(String email) { this.email = email; }
        public void setPassword(String password) { this.password = password; }
    }

    public static class LoginRequest {
        @NotBlank(message = "Email is required")
        private String email;

        @NotBlank(message = "Password is required")
        private String password;

        // Optional: a previously-issued trusted-device token; lets a known
        // browser skip the OTP.
        private String deviceToken;

        public String getEmail() { return email; }
        public String getPassword() { return password; }
        public String getDeviceToken() { return deviceToken; }
        public void setEmail(String email) { this.email = email; }
        public void setPassword(String password) { this.password = password; }
        public void setDeviceToken(String deviceToken) { this.deviceToken = deviceToken; }
    }

    public static class VerifyRequest {
        @NotBlank(message = "Email is required")
        private String email;

        @NotBlank(message = "Verification code is required")
        private String code;

        @NotBlank(message = "Purpose is required")
        private String purpose; // REGISTER or LOGIN

        // Optional: when true, return a trusted-device token so this browser
        // can skip the OTP on future logins.
        private boolean trustDevice;

        public String getEmail() { return email; }
        public String getCode() { return code; }
        public String getPurpose() { return purpose; }
        public boolean isTrustDevice() { return trustDevice; }
        public void setEmail(String email) { this.email = email; }
        public void setCode(String code) { this.code = code; }
        public void setPurpose(String purpose) { this.purpose = purpose; }
        public void setTrustDevice(boolean trustDevice) { this.trustDevice = trustDevice; }
    }

    public static class ResendRequest {
        @NotBlank(message = "Email is required")
        private String email;

        @NotBlank(message = "Purpose is required")
        private String purpose; // REGISTER or LOGIN

        public String getEmail() { return email; }
        public String getPurpose() { return purpose; }
        public void setEmail(String email) { this.email = email; }
        public void setPurpose(String purpose) { this.purpose = purpose; }
    }

    public static class ForgotPasswordRequest {
        @Email(message = "Invalid email address")
        @NotBlank(message = "Email is required")
        private String email;

        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
    }

    public static class ResetPasswordRequest {
        @NotBlank(message = "Email is required")
        private String email;

        @NotBlank(message = "Verification code is required")
        private String code;

        @NotBlank(message = "New password is required")
        @Size(min = 6, message = "Password must be at least 6 characters")
        private String newPassword;

        public String getEmail() { return email; }
        public String getCode() { return code; }
        public String getNewPassword() { return newPassword; }
        public void setEmail(String email) { this.email = email; }
        public void setCode(String code) { this.code = code; }
        public void setNewPassword(String newPassword) { this.newPassword = newPassword; }
    }

    /** Returned by register/login when an email code has been sent and must be verified. */
    public static class PendingResponse {
        private final boolean otpRequired = true;
        private final String email;
        private final String message;

        public PendingResponse(String email, String message) {
            this.email = email;
            this.message = message;
        }

        public boolean isOtpRequired() { return otpRequired; }
        public String getEmail() { return email; }
        public String getMessage() { return message; }
    }

    public static class AuthResponse {
        private String token;
        private String username;
        private String handle;
        private String email;
        private String role;
        private String deviceToken; // present only when the user opted to trust this device

        public AuthResponse(String token, String username, String email, String role) {
            this.token = token;
            this.username = username;
            this.email = email;
            this.role = role;
        }

        public String getToken() { return token; }
        public String getUsername() { return username; }
        public String getHandle() { return handle; }
        public String getEmail() { return email; }
        public String getRole() { return role; }
        public String getDeviceToken() { return deviceToken; }
        public void setHandle(String handle) { this.handle = handle; }
        public void setDeviceToken(String deviceToken) { this.deviceToken = deviceToken; }
    }
}
