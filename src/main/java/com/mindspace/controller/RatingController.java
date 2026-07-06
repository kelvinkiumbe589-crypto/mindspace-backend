package com.mindspace.controller;

import com.mindspace.dto.RatingDto;
import com.mindspace.service.RatingService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

/** A logged-in user rates the MindSpace app. */
@RestController
@RequestMapping("/api/ratings")
public class RatingController {

    private final RatingService ratingService;

    public RatingController(RatingService ratingService) {
        this.ratingService = ratingService;
    }

    @PostMapping
    public RatingDto.Response submit(@AuthenticationPrincipal UserDetails user,
                                     @Valid @RequestBody RatingDto.SubmitRequest req) {
        return ratingService.submit(user.getUsername(), req);
    }

    @GetMapping("/me")
    public RatingDto.Response mine(@AuthenticationPrincipal UserDetails user) {
        return ratingService.mine(user.getUsername());
    }
}
