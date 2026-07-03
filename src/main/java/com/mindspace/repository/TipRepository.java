package com.mindspace.repository;

import com.mindspace.model.Tip;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface TipRepository extends JpaRepository<Tip, UUID> {
    List<Tip> findAllByOrderByCreatedAtDesc();
}
