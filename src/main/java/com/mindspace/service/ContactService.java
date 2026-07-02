package com.mindspace.service;

import com.mindspace.dto.ContactDto;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * Sends support-form submissions to the configured recipient inbox.
 * Mail is optional: if SMTP isn't configured (no JavaMailSender bean, which
 * happens when spring.mail.host is absent), {@link #isConfigured()} returns
 * false and the controller tells the frontend to fall back to a mailto: link.
 */
@Service
public class ContactService {

    private final JavaMailSender mailSender; // null when spring.mail.* is not configured

    @Value("${app.contact.recipient:kelvinkiumbe589@gmail.com}")
    private String recipient;

    @Value("${spring.mail.username:}")
    private String fromAddress;

    public ContactService(ObjectProvider<JavaMailSender> mailSenderProvider) {
        this.mailSender = mailSenderProvider.getIfAvailable();
    }

    public boolean isConfigured() {
        return mailSender != null && fromAddress != null && !fromAddress.isBlank();
    }

    public void sendContactMessage(ContactDto.ContactRequest req) {
        if (!isConfigured()) {
            throw new IllegalStateException("Mail is not configured on the server");
        }
        SimpleMailMessage mail = new SimpleMailMessage();
        mail.setTo(recipient);
        mail.setFrom(fromAddress);
        mail.setReplyTo(req.getEmail());
        mail.setSubject("MindSpace support request from " + req.getName());
        mail.setText(
                "You received a new support message via MindSpace.\n\n" +
                "Name:  " + req.getName() + "\n" +
                "Email: " + req.getEmail() + "\n" +
                "Phone: " + (req.getPhone() == null || req.getPhone().isBlank() ? "(not provided)" : req.getPhone()) + "\n\n" +
                "Message:\n" + req.getMessage() + "\n"
        );
        mailSender.send(mail);
    }
}
