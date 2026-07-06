package com.mindspace.service;

import com.mindspace.dto.ContactDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Sends support-form submissions to the configured recipient inbox via MailService
 * (Brevo HTTPS API in the cloud, SMTP locally).
 */
@Service
public class ContactService {

    private final MailService mailService;

    @Value("${app.contact.recipient:kelvinkiumbe589@gmail.com}")
    private String recipient;

    public ContactService(MailService mailService) {
        this.mailService = mailService;
    }

    public boolean isConfigured() {
        return mailService.isConfigured();
    }

    public void sendContactMessage(ContactDto.ContactRequest req) {
        if (!isConfigured()) {
            throw new IllegalStateException("Mail is not configured on the server");
        }
        String text =
                "You received a new support message via MindSpace.\n\n" +
                "Name:  " + req.getName() + "\n" +
                "Email: " + req.getEmail() + "\n" +
                "Phone: " + (req.getPhone() == null || req.getPhone().isBlank() ? "(not provided)" : req.getPhone()) + "\n\n" +
                "Message:\n" + req.getMessage() + "\n";
        mailService.send(recipient, "MindSpace support request from " + req.getName(), text);
    }
}
