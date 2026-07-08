package com.mindspace.repository;

import com.mindspace.model.PushSubscription;
import com.mindspace.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PushSubscriptionRepository extends JpaRepository<PushSubscription, UUID> {
    List<PushSubscription> findByUser(User user);
    Optional<PushSubscription> findByEndpoint(String endpoint);
}
