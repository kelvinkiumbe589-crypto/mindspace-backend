package com.mindspace.service;

import com.mindspace.dto.TherapistDto;
import com.mindspace.model.TherapistProfile;
import com.mindspace.model.User;
import com.mindspace.repository.BookingRepository;
import com.mindspace.repository.TherapistProfileRepository;
import com.mindspace.repository.UserRepository;
import com.mindspace.repository.WithdrawalRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class TherapistService {

    private static final String[] PALETTE = {"#534AB7", "#1D9E75", "#C2410C", "#2563EB", "#9333EA", "#DB2777"};

    private final TherapistProfileRepository profileRepo;
    private final UserRepository userRepository;
    private final BookingRepository bookingRepository;
    private final WithdrawalRepository withdrawalRepository;
    private final PasswordEncoder passwordEncoder;

    public TherapistService(TherapistProfileRepository profileRepo, UserRepository userRepository,
                            BookingRepository bookingRepository, WithdrawalRepository withdrawalRepository,
                            PasswordEncoder passwordEncoder) {
        this.profileRepo = profileRepo;
        this.userRepository = userRepository;
        this.bookingRepository = bookingRepository;
        this.withdrawalRepository = withdrawalRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public TherapistDto.Response update(UUID profileId, TherapistDto.UpdateRequest req) {
        TherapistProfile p = profileRepo.findById(profileId)
                .orElseThrow(() -> new IllegalArgumentException("Therapist not found"));
        User u = p.getUser();

        p.setName(req.getName());
        p.setInitials(initials(req.getName()));
        p.setTitle(req.getTitle());
        p.setSpecialties(req.getSpecialties());
        if (req.getPriceOnline() > 0) p.setPriceOnline(req.getPriceOnline());
        p.setBio(req.getBio());
        if (req.getAvailable() != null) p.setAvailable(req.getAvailable());

        if (req.getEmail() != null && !req.getEmail().isBlank() && !req.getEmail().equalsIgnoreCase(u.getEmail())) {
            if (userRepository.existsByEmail(req.getEmail())) {
                throw new IllegalArgumentException("Email is already in use");
            }
            u.setEmail(req.getEmail());
        }
        if (req.getPassword() != null && !req.getPassword().isBlank()) {
            u.setPasswordHash(passwordEncoder.encode(req.getPassword()));
        }
        userRepository.save(u);
        return toResponse(profileRepo.save(p));
    }

    public void delete(UUID profileId) {
        TherapistProfile p = profileRepo.findById(profileId)
                .orElseThrow(() -> new IllegalArgumentException("Therapist not found"));
        User u = p.getUser();
        withdrawalRepository.deleteByTherapist(u); // remove payout records
        bookingRepository.deleteByTherapist(u);    // remove their session records too
        profileRepo.delete(p);
        userRepository.delete(u);
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
        r.availableDays = parseInts(p.getAvailableDays());
        r.availableSlots = parseStrs(p.getAvailableSlots());
        return r;
    }

    // ── Therapist self-service ──
    public TherapistDto.Response getOwnProfile(String email) {
        return toResponse(ownProfile(email));
    }

    public TherapistDto.Response updateOwnProfile(String email, TherapistDto.SelfUpdateRequest req) {
        TherapistProfile p = ownProfile(email);
        if (req.getPriceOnline() > 0) p.setPriceOnline(req.getPriceOnline());
        if (req.getTitle() != null) p.setTitle(req.getTitle());
        if (req.getSpecialties() != null) p.setSpecialties(req.getSpecialties());
        if (req.getBio() != null) p.setBio(req.getBio());
        if (req.getAvailable() != null) p.setAvailable(req.getAvailable());
        if (req.getAvailableDays() != null) {
            p.setAvailableDays(req.getAvailableDays().stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(",")));
        }
        if (req.getAvailableSlots() != null) {
            p.setAvailableSlots(String.join(",", req.getAvailableSlots()));
        }
        return toResponse(profileRepo.save(p));
    }

    private TherapistProfile ownProfile(String email) {
        User u = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Account not found"));
        return profileRepo.findByUserId(u.getId())
                .orElseThrow(() -> new IllegalArgumentException("Therapist profile not found"));
    }

    private List<Integer> parseInts(String csv) {
        if (csv == null || csv.isBlank()) return List.of();
        return Arrays.stream(csv.split(",")).map(String::trim).filter(s -> !s.isEmpty()).map(Integer::parseInt).toList();
    }

    private List<String> parseStrs(String csv) {
        if (csv == null || csv.isBlank()) return List.of();
        return Arrays.stream(csv.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
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
