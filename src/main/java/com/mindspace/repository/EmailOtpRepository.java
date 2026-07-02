package com.mindspace.repository;

import com.mindspace.model.EmailOtp;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface EmailOtpRepository extends JpaRepository<EmailOtp, UUID> {
    Optional<EmailOtp> findFirstByEmailAndPurposeOrderByCreatedAtDesc(String email, EmailOtp.Purpose purpose);
    void deleteByEmailAndPurpose(String email, EmailOtp.Purpose purpose);
}
