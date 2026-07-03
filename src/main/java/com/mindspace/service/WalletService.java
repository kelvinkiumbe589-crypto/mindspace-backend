package com.mindspace.service;

import com.mindspace.dto.WalletDto;
import com.mindspace.model.Booking;
import com.mindspace.model.TherapistProfile;
import com.mindspace.model.User;
import com.mindspace.model.Withdrawal;
import com.mindspace.repository.BookingRepository;
import com.mindspace.repository.TherapistProfileRepository;
import com.mindspace.repository.UserRepository;
import com.mindspace.repository.WithdrawalRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class WalletService {

    @Value("${app.commission.percent:15}")
    private int commissionPercent;

    private final BookingRepository bookingRepo;
    private final WithdrawalRepository withdrawalRepo;
    private final UserRepository userRepository;
    private final TherapistProfileRepository profileRepo;

    public WalletService(BookingRepository bookingRepo, WithdrawalRepository withdrawalRepo,
                         UserRepository userRepository, TherapistProfileRepository profileRepo) {
        this.bookingRepo = bookingRepo;
        this.withdrawalRepo = withdrawalRepo;
        this.userRepository = userRepository;
        this.profileRepo = profileRepo;
    }

    private User user(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + email));
    }

    public WalletDto.Earnings earnings(String email) {
        return earningsFor(user(email));
    }

    private WalletDto.Earnings earningsFor(User t) {
        List<Booking> bs = bookingRepo.findByTherapistOrderByScheduledAtAsc(t);
        int pending = bs.stream()
                .filter(b -> b.getStatus() == Booking.Status.AWAITING_APPROVAL || b.getStatus() == Booking.Status.APPROVED)
                .mapToInt(Booking::getAmount).sum();
        int totalEarned = bs.stream().filter(b -> b.getStatus() == Booking.Status.DONE).mapToInt(Booking::getAmount).sum();

        List<Withdrawal> ws = withdrawalRepo.findByTherapistOrderByCreatedAtDesc(t);
        int reserved = ws.stream()
                .filter(w -> w.getStatus() == Withdrawal.Status.REQUESTED || w.getStatus() == Withdrawal.Status.PAID)
                .mapToInt(Withdrawal::getGrossAmount).sum();
        int withdrawn = ws.stream().filter(w -> w.getStatus() == Withdrawal.Status.PAID).mapToInt(Withdrawal::getNetAmount).sum();

        WalletDto.Earnings e = new WalletDto.Earnings();
        e.pending = pending;
        e.available = Math.max(0, totalEarned - reserved);
        e.totalEarned = totalEarned;
        e.withdrawn = withdrawn;
        e.commissionPercent = commissionPercent;
        e.withdrawals = ws.stream().map(this::toResp).toList();
        return e;
    }

    public WalletDto.WithdrawalResponse requestWithdrawal(String email, WalletDto.WithdrawalRequest req) {
        User t = user(email);
        WalletDto.Earnings e = earningsFor(t);
        int gross = req.getAmount() > 0 ? req.getAmount() : e.available;
        if (gross <= 0) throw new IllegalArgumentException("You have no available balance to withdraw");
        if (gross > e.available) throw new IllegalArgumentException("Amount exceeds your available balance of KES " + e.available);
        int commission = (int) Math.round(gross * commissionPercent / 100.0);
        Withdrawal w = new Withdrawal();
        w.setTherapist(t);
        w.setGrossAmount(gross);
        w.setCommission(commission);
        w.setNetAmount(gross - commission);
        w.setPhone(req.getPhone());
        w.setStatus(Withdrawal.Status.REQUESTED);
        return toResp(withdrawalRepo.save(w));
    }

    public List<WalletDto.WithdrawalResponse> myWithdrawals(String email) {
        return withdrawalRepo.findByTherapistOrderByCreatedAtDesc(user(email)).stream().map(this::toResp).toList();
    }

    // ── Admin ──
    public List<WalletDto.WithdrawalResponse> allWithdrawals() {
        return withdrawalRepo.findAllByOrderByCreatedAtDesc().stream().map(this::toResp).toList();
    }

    public WalletDto.WithdrawalResponse markPaid(UUID id) {
        Withdrawal w = withdrawalRepo.findById(id).orElseThrow(() -> new IllegalArgumentException("Withdrawal not found"));
        w.setStatus(Withdrawal.Status.PAID);
        w.setPaidAt(LocalDateTime.now());
        return toResp(withdrawalRepo.save(w));
    }

    public WalletDto.WithdrawalResponse reject(UUID id) {
        Withdrawal w = withdrawalRepo.findById(id).orElseThrow(() -> new IllegalArgumentException("Withdrawal not found"));
        w.setStatus(Withdrawal.Status.REJECTED); // releases the reserved balance
        return toResp(withdrawalRepo.save(w));
    }

    private WalletDto.WithdrawalResponse toResp(Withdrawal w) {
        WalletDto.WithdrawalResponse r = new WalletDto.WithdrawalResponse();
        r.id = w.getId().toString();
        r.therapistName = profileRepo.findByUserId(w.getTherapist().getId())
                .map(TherapistProfile::getName).orElse(w.getTherapist().getUsername());
        r.therapistEmail = w.getTherapist().getEmail();
        r.grossAmount = w.getGrossAmount();
        r.commission = w.getCommission();
        r.netAmount = w.getNetAmount();
        r.phone = w.getPhone();
        r.status = w.getStatus().name();
        r.createdAt = w.getCreatedAt() == null ? null : w.getCreatedAt().toString();
        r.paidAt = w.getPaidAt() == null ? null : w.getPaidAt().toString();
        return r;
    }
}
