package com.mindspace.repository;

import com.mindspace.model.User;
import com.mindspace.model.UserBlock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserBlockRepository extends JpaRepository<UserBlock, UUID> {
    boolean existsByBlockerAndBlocked(User blocker, User blocked);
    Optional<UserBlock> findByBlockerAndBlocked(User blocker, User blocked);
    List<UserBlock> findByBlocker(User blocker);
    List<UserBlock> findByBlocked(User blocked);
}
