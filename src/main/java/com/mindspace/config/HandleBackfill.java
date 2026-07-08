package com.mindspace.config;

import com.mindspace.model.User;
import com.mindspace.repository.UserRepository;
import com.mindspace.util.HandleUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * One-time (idempotent) backfill: give every pre-existing account a messaging handle
 * generated from their name, so nobody is left unsearchable when the feature ships.
 * Runs on each startup but only touches rows where handle is still null.
 */
@Component
@Order(20) // after SchemaMigrations
public class HandleBackfill implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(HandleBackfill.class);

    private final UserRepository userRepository;

    public HandleBackfill(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public void run(ApplicationArguments args) {
        List<User> missing;
        try {
            missing = userRepository.findByHandleIsNull();
        } catch (Exception e) {
            log.warn("Handle backfill skipped: {}", e.getMessage());
            return;
        }
        int done = 0;
        for (User u : missing) {
            try {
                String base = HandleUtil.fromName(u.getUsername(), u.getEmail());
                u.setHandle(HandleUtil.makeUnique(base, userRepository::existsByHandle));
                userRepository.save(u);
                done++;
            } catch (Exception e) {
                log.warn("Handle backfill failed for {}: {}", u.getEmail(), e.getMessage());
            }
        }
        if (done > 0) log.info("Handle backfill assigned {} handles.", done);
    }
}
