package com.mindspace.service;

import com.mindspace.dto.RatingDto;
import com.mindspace.model.AppRating;
import com.mindspace.model.User;
import com.mindspace.repository.AppRatingRepository;
import com.mindspace.repository.UserRepository;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class RatingService {

    private final AppRatingRepository repo;
    private final UserRepository userRepository;

    public RatingService(AppRatingRepository repo, UserRepository userRepository) {
        this.repo = repo;
        this.userRepository = userRepository;
    }

    private User getUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + email));
    }

    /** Create or update the current user's rating (one per user). */
    public RatingDto.Response submit(String email, RatingDto.SubmitRequest req) {
        if (req.getStars() < 1 || req.getStars() > 5) {
            throw new IllegalArgumentException("Please choose 1 to 5 stars");
        }
        User user = getUser(email);
        AppRating rating = repo.findByUser(user).orElseGet(AppRating::new);
        rating.setUser(user);
        rating.setStars(req.getStars());
        rating.setComment(req.getComment() == null ? null : req.getComment().trim());
        return toResponse(repo.save(rating));
    }

    /** The current user's existing rating, or null if they haven't rated yet. */
    public RatingDto.Response mine(String email) {
        return repo.findByUser(getUser(email)).map(this::toResponse).orElse(null);
    }

    // ── Admin ──
    public List<RatingDto.Response> all() {
        return repo.findAllByOrderByUpdatedAtDesc().stream().map(this::toResponse).toList();
    }

    public RatingDto.Summary summary() {
        List<AppRating> all = repo.findAllByOrderByUpdatedAtDesc();
        RatingDto.Summary s = new RatingDto.Summary();
        s.count = all.size();
        if (all.isEmpty()) return s;
        int total = 0;
        for (AppRating r : all) {
            total += r.getStars();
            int idx = Math.min(5, Math.max(1, r.getStars())) - 1;
            s.distribution[idx]++;
        }
        s.average = Math.round((total / (double) all.size()) * 10) / 10.0;
        return s;
    }

    private RatingDto.Response toResponse(AppRating r) {
        RatingDto.Response resp = new RatingDto.Response();
        resp.id = r.getId();
        resp.stars = r.getStars();
        resp.comment = r.getComment();
        resp.userName = r.getUser() != null ? r.getUser().getUsername() : "User";
        resp.userEmail = r.getUser() != null ? r.getUser().getEmail() : null;
        resp.createdAt = r.getUpdatedAt() != null ? r.getUpdatedAt() : r.getCreatedAt();
        return resp;
    }
}
