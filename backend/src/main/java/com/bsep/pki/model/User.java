package com.bsep.pki.model;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "users")
@Data
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(unique = true, nullable = false)
    private String email;

    @Column(nullable = false)
    private String passwordHash;

    @Column
    private String firstName;

    @Column
    private String lastName;

    @Column
    private String organization;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    @Column(nullable = false)
    private boolean isActive = false;

    @Column(nullable = false)
    private boolean mustChangePassword = false;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

}

