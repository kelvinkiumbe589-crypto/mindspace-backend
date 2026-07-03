package com.mindspace.controller;

import com.mindspace.dto.TherapistDto;
import com.mindspace.service.TherapistService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Public therapist directory. */
@RestController
@RequestMapping("/api/therapists")
public class TherapistController {

    private final TherapistService therapistService;

    public TherapistController(TherapistService therapistService) {
        this.therapistService = therapistService;
    }

    @GetMapping
    public List<TherapistDto.Response> list() {
        return therapistService.list();
    }
}
