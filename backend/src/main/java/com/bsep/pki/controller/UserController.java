package com.bsep.pki.controller;

import com.bsep.pki.model.dto.SavePublicKeyRequest;
import com.bsep.pki.model.dto.UserPublicProfileDto;
import com.bsep.pki.model.entity.User;
import com.bsep.pki.model.entity.UserRole;
import com.bsep.pki.repository.UserRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Endpoints for user profile management.
 *
 * Public key management:
 *   PUT  /api/users/me/public-key   — save/update the caller's RSA public key (SPKI PEM)
 *   GET  /api/users/me/public-key   — retrieve the caller's stored public key
 *   GET  /api/users                 — list all active users with their public keys
 *                                      (needed by the password manager sharing flow)
 *   GET  /api/users/{id}/public-key — retrieve a specific user's public key
 *
 * SECURITY: Only the PEM-encoded *public* key is stored.
 *           Private keys are never accepted, stored, or logged anywhere on the server.
 */
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserRepository userRepository;

    // -------------------------------------------------------------------------
    // Own public-key endpoints
    // -------------------------------------------------------------------------

    /**
     * Save or update the authenticated user's RSA public key.
     * Called once after the user generates a key pair in the browser.
     */
    @PutMapping("/me/public-key")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> saveMyPublicKey(
            @Valid @RequestBody SavePublicKeyRequest request,
            Authentication authentication
    ) {
        User user = resolveUser(authentication);
        user.setPublicKeySpki(request.getPublicKeySpki());
        userRepository.save(user);
        return ResponseEntity.ok().build();
    }

    /**
     * Retrieve the authenticated user's stored public key.
     */
    @GetMapping("/me/public-key")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<UserPublicProfileDto> getMyPublicKey(Authentication authentication) {
        User user = resolveUser(authentication);
        return ResponseEntity.ok(mapToProfile(user));
    }

    /**
     * List all active users with their public keys.
     * Used by the password manager to find recipients when sharing a password entry.
     */
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<UserPublicProfileDto>> listUsers() {
        List<UserPublicProfileDto> users = userRepository.findAll().stream()
                .filter(User::isActive)
                .map(this::mapToProfile)
                .collect(Collectors.toList());
        return ResponseEntity.ok(users);
    }

    /**
     * List all CA users — used by ADMIN when assigning an issued cert to a CA user.
     */
    @GetMapping("/ca-users")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<List<UserPublicProfileDto>> listCaUsers() {
        List<UserPublicProfileDto> caUsers = userRepository.findAll().stream()
                .filter(User::isActive)
                .filter(u -> u.getRole() == UserRole.CA_USER)
                .map(this::mapToProfile)
                .collect(Collectors.toList());
        return ResponseEntity.ok(caUsers);
    }

    /**
     * List all END_ENTITY users — used by ADMIN and CA_USER when issuing an END_ENTITY certificate to a user.
     */
    @GetMapping("/end-entity-users")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'CA_USER')")
    public ResponseEntity<List<UserPublicProfileDto>> listEndEntityUsers() {
        List<UserPublicProfileDto> users = userRepository.findAll().stream()
                .filter(User::isActive)
                .filter(u -> u.getRole() == UserRole.END_ENTITY)
                .map(this::mapToProfile)
                .collect(Collectors.toList());
        return ResponseEntity.ok(users);
    }

    /**
     * Retrieve a specific user's public key by their ID.
     * Used when encrypting a password for sharing with a target user.
     */
    @GetMapping("/{id}/public-key")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<UserPublicProfileDto> getUserPublicKey(@PathVariable Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found"));
        return ResponseEntity.ok(mapToProfile(user));
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private User resolveUser(Authentication authentication) {
        return userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));
    }

    private UserPublicProfileDto mapToProfile(User user) {
        return UserPublicProfileDto.builder()
                .id(user.getId())
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .role(user.getRole() != null ? user.getRole().name() : null)
                .publicKeySpki(user.getPublicKeySpki())
                .build();
    }
}

