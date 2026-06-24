package com.taskhub.config;

import com.taskhub.entity.User;
import com.taskhub.enums.Role;
import com.taskhub.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class AdminDataSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.admin.email:admin@taskhub.com}")
    private String adminEmail;

    @Value("${app.admin.password:Admin@TaskHub2026}")
    private String adminPassword;

    @Value("${app.admin.full-name:TaskHub Admin}")
    private String adminFullName;

    @Value("${app.admin.enabled:true}")
    private boolean seederEnabled;

    @Override
    public void run(String... args) {
        if (!seederEnabled) {
            log.info("[SEEDER] Admin seeder is disabled (app.admin.enabled=false)");
            return;
        }

        if (userRepository.existsByEmail(adminEmail)) {
            log.info("[SEEDER] Admin account already exists: {}", adminEmail);
            return;
        }

        User admin = User.builder()
                .email(adminEmail)
                .password(passwordEncoder.encode(adminPassword))
                .fullName(adminFullName)
                .role(Role.ADMIN)
                .isVerified(true)
                .isAvailable(true)
                .build();

        userRepository.save(admin);
        log.warn("[SEEDER] ============================================================");
        log.warn("[SEEDER] Default admin account created:");
        log.warn("[SEEDER]   Email:    {}", adminEmail);
        log.warn("[SEEDER]   Password: {}", adminPassword);
        log.warn("[SEEDER] Login URL (FE): /login (use the admin credentials above)");
        log.warn("[SEEDER] Admin URL  (FE): /admin  (hidden route — no nav link)");
        log.warn("[SEEDER] CHANGE THE PASSWORD IMMEDIATELY IN PRODUCTION.");
        log.warn("[SEEDER] ============================================================");
    }
}
