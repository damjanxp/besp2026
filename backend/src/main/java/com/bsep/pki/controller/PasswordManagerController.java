package com.bsep.pki.controller;

import com.bsep.pki.model.dto.PasswordEntryResponse;
import com.bsep.pki.model.dto.SavePasswordRequest;
import com.bsep.pki.model.dto.SharePasswordRequest;
import com.bsep.pki.model.entity.User;
import com.bsep.pki.repository.UserRepository;
import com.bsep.pki.service.PasswordManagerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/password-manager")
@RequiredArgsConstructor
public class PasswordManagerController {

    private final PasswordManagerService passwordManagerService;
    private final UserRepository userRepository;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<PasswordEntryResponse>> getMyPasswords(Authentication authentication) {
        User currentUser = resolveUser(authentication);
        return ResponseEntity.ok(passwordManagerService.getMyPasswords(currentUser));
    }

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<PasswordEntryResponse> savePassword(
            @Valid @RequestBody SavePasswordRequest request,
            Authentication authentication
    ) {
        User currentUser = resolveUser(authentication);
        return ResponseEntity.ok(passwordManagerService.savePassword(request, currentUser));
    }

    @PostMapping("/{id}/share")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> sharePassword(
            @PathVariable Long id,
            @Valid @RequestBody SharePasswordRequest request,
            Authentication authentication
    ) {
        User currentUser = resolveUser(authentication);
        passwordManagerService.sharePassword(id, request, currentUser);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> deletePassword(
            @PathVariable Long id,
            Authentication authentication
    ) {
        User currentUser = resolveUser(authentication);
        passwordManagerService.deletePassword(id, currentUser);
        return ResponseEntity.noContent().build();
    }

    private User resolveUser(Authentication authentication) {
        return userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));
    }
}

