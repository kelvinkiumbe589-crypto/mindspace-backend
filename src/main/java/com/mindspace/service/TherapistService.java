package com.mindspace.service;

import com.mindspace.dto.TherapistDto;
import com.mindspace.model.TherapistProfile;
import com.mindspace.model.User;
import com.mindspace.repository.TherapistProfileRepository;
import com.mindspace.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Service
@Transactional
public class TherapistService {

    private static final String[] PALETTE = {"#534AB7", "#1D9E75", "#C2410C", "#2563EB", "#9333EA", "#DB2777"};

    private final TherapistProfileRepository profileRepo;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public TherapistService(TherapistProfileRepository profileRepo, UserRepository userRepository,
                            PasswordEncoder passwordEncoder) {
        this.profileRepo = profileRepo;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public TherapistDto.Response create(TherapistDto.CreateRequest req) {
        if (userRepository.existsByEmail(req.getEmail())) {
            throw new IllegalArgumentException("Email is already registered");
        }
        User user = User.builder()
                .username(uniqueUsername(req.getName(), req.getEmail()))
                .email(req.getEmail())
                .passwordHash(passwordEncoder.encode(req.getPassword()))
                .role(User.Role.THERAPIST)
                .build();
        userRepository.save(user);

        TherapistProfile p = new TherapistProfile();
        p.setUser(user);
        p.setName(req.getName());
        p.setTitle(req.getTitle());
        p.setSpecialties(req.getSpecialties());
        p.setPriceOnline(req.getPriceOnline() > 0 ? req.getPriceOnline() : 2000);
        p.setBio(req.getBio());
        p.setInitials(initials(req.getName()));
        p.setColor(PALETTE[Math.floorMod(req.getName().hashCode(), PALETTE.length)]);
        profileRepo.save(p);
        return toResponse(p);
    }

    public List<TherapistDto.Response> list() {
        List<TherapistDto.Response> out = new ArrayList<>();
        for (TherapistProfile p : profileRepo.findAllByOrderByNameAsc()) out.add(toResponse(p));
        return out;
    }

    public TherapistDto.Response toResponse(TherapistProfile p) {
        TherapistDto.Response r = new TherapistDto.Response();
        r.id = p.getId().toString();
        r.userId = p.getUser().getId().toString();
        r.name = p.getName();
        r.title = p.getTitle();
        r.specialties = p.getSpecialties() == null || p.getSpecialties().isBlank()
                ? List.of()
                : Arrays.stream(p.getSpecialties().split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
        r.priceOnline = p.getPriceOnline();
        r.pricePhysical = (int) Math.round(p.getPriceOnline() * 1.5);
        r.initials = p.getInitials();
        r.color = p.getColor();
        r.bio = p.getBio();
        r.available = p.isAvailable();
        r.rating = p.getRating();
        r.reviews = p.getReviews();
        r.email = p.getUser().getEmail();
        return r;
    }

    private String initials(String name) {
        String[] parts = name.trim().split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (!part.isEmpty() && Character.isLetter(part.charAt(0))) sb.append(Character.toUpperCase(part.charAt(0)));
            if (sb.length() == 2) break;
        }
        return sb.length() == 0 ? "DR" : sb.toString();
    }

    private String uniqueUsername(String name, String email) {
        String base = name.trim().toLowerCase().replaceAll("[^a-z0-9]+", "").trim();
        if (base.isEmpty()) base = email.split("@")[0].replaceAll("[^a-z0-9]+", "");
        if (base.length() > 40) base = base.substring(0, 40);
        String candidate = base;
        int n = 1;
        while (userRepository.existsByUsername(candidate)) {
            candidate = base + n++;
        }
        return candidate;
    }
}
