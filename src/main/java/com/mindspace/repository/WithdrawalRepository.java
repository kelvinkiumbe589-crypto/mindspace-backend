package com.mindspace.repository;

import com.mindspace.model.User;
import com.mindspace.model.Withdrawal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface WithdrawalRepository extends JpaRepository<Withdrawal, UUID> {
    List<Withdrawal> findByTherapistOrderByCreatedAtDesc(User therapist);
    List<Withdrawal> findAllByOrderByCreatedAtDesc();
    void deleteByTherapist(User therapist);
}
