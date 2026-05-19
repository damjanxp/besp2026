package com.bsep.pki.config;

import com.bsep.pki.model.entity.User;
import com.bsep.pki.model.entity.UserRole;
import com.bsep.pki.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements ApplicationRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(ApplicationArguments args) {
        if (userRepository.findByEmail("admin@pki.com").isEmpty()) {
            User admin = User.builder()
                    .email("admin@pki.com")
                    .password(passwordEncoder.encode("Admin1234!"))
                    .firstName("System")
                    .lastName("Admin")
                    .role(UserRole.ADMIN)
                    .isActive(true)
                    .build();

            userRepository.save(admin);
            log.info("Default admin user created: admin@pki.com");
        } else {
            log.info("Admin user already exists, skipping initialization.");
        }

        if (userRepository.findByEmail("user@pki.com").isEmpty()) {
            User endEntity = User.builder()
                    .email("user@pki.com")
                    .password(passwordEncoder.encode("User1234!"))
                    .firstName("Test")
                    .lastName("Korisnik")
                    .organization("Test Org")
                    .role(UserRole.END_ENTITY)
                    .isActive(true)
                    .build();

            userRepository.save(endEntity);
            log.info("Default end entity user created: user@pki.com");
        } else {
            log.info("End entity user already exists, skipping initialization.");
        }
    }
}

