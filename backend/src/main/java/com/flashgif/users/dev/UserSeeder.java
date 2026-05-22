package com.flashgif.users.dev;

import com.flashgif.users.domain.UserRepository;
import com.flashgif.users.domain.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Idempotent dev user on first {@code local}-profile boot. Lets tests skip
 * registration and go straight to /auth/login.
 *
 * <p>Email:    {@code dev@flashgif.example}
 * <p>Password: {@code dev-password}
 */
@Component
@Profile("local")
@RequiredArgsConstructor
@Slf4j
class UserSeeder implements ApplicationRunner {

    private static final String EMAIL    = "dev@flashgif.example";
    private static final String USERNAME = "dev";
    private static final String PASSWORD = "dev-password";
    private static final String NAME     = "Dev User";

    private final UserRepository userRepository;
    private final UserService userService;

    @Override
    public void run(org.springframework.boot.ApplicationArguments args) {
        if (userRepository.existsByEmail(EMAIL)) {
            log.info("Dev user '{}' already present — skipping seed", EMAIL);
            return;
        }
        userService.register(EMAIL, USERNAME, PASSWORD, NAME);
        log.info("Seeded dev user '{}' / '{}'", EMAIL, PASSWORD);
    }
}
