package com.taskhub.entity;

import com.taskhub.enums.Role;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "users")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class User extends AuditableEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String email;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false)
    private String fullName;

    @Column(length = 100)
    private String university;

    @Column(length = 100)
    private String major;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    @Column(precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal walletBalance = BigDecimal.ZERO;

    @Column(length = 500)
    private String bio;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "user_skills", joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "skill")
    @Builder.Default
    private List<String> skills = new ArrayList<>();

    @Column(columnDefinition = "TEXT")
    private String experience;

    @Column(length = 500)
    private String portfolioUrl;

    @Column(length = 20)
    private String phone;

    @Column(length = 200)
    private String title;

    @Column(length = 20)
    private String hourlyRate;

    @Column(length = 50)
    private String availability;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "user_languages", joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "language")
    @Builder.Default
    private List<String> languages = new ArrayList<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "user_certifications", joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "certification")
    @Builder.Default
    private List<String> certifications = new ArrayList<>();

    @Column(length = 500)
    private String avatarUrl;

    private LocalDate dateOfBirth;

    @Column(name = "is_verified", nullable = false, columnDefinition = "boolean default false")
    @Builder.Default
    private Boolean isVerified = false;

    @Column(name = "is_available", nullable = false, columnDefinition = "boolean default true")
    @Builder.Default
    private Boolean isAvailable = true;

    @Column(name = "is_banned", nullable = false, columnDefinition = "boolean default false")
    @Builder.Default
    private Boolean isBanned = false;

    @Column(nullable = false)
    @Builder.Default
    private boolean emailVerified = false;

    @Column(name = "auth_provider", nullable = false, length = 20)
    @Builder.Default
    private String authProvider = "LOCAL";

    @Column(name = "provider_subject", unique = true, length = 255)
    private String providerSubject;
}
