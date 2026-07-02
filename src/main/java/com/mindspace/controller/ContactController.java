package com.mindspace.controller;

import com.mindspace.dto.ContactDto;
import com.mindspace.service.ContactService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Public support-form endpoint. Emails submissions to the configured recipient.
 * Returns 503 with status "unavailable" when server mail isn't configured so
 * the frontend can fall back to opening a mailto: link.
 */
@RestController
@RequestMapping("/api/contact")
public class ContactController {

    private final ContactService contactService;

    public ContactController(ContactService contactService) {
        this.contactService = contactService;
    }

    @PostMapping
    public ResponseEntity<ContactDto.ContactResponse> submit(
            @Valid @RequestBody ContactDto.ContactRequest request) {

        if (!contactService.isConfigured()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(new ContactDto.ContactResponse("unavailable", "Server email is not configured"));
        }
        try {
            contactService.sendContactMessage(request);
            return ResponseEntity.ok(new ContactDto.ContactResponse("sent", "Message delivered"));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(new ContactDto.ContactResponse("unavailable", "Could not send message"));
        }
    }
}
