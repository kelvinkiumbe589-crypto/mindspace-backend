package com.mindspace.repository;

import com.mindspace.model.AppRating;
import com.mindspace.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AppRatingRepository extends JpaRepository<AppRating, UUID> {
    Optional<AppRating> findByUser(User user);
    List<AppRating> findAllByOrderByUpdatedAtDesc();
}
