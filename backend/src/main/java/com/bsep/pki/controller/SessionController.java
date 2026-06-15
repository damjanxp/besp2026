package com.bsep.pki.controller;

import com.bsep.pki.model.dto.SessionResponse;
import com.bsep.pki.model.entity.User;
import com.bsep.pki.repository.UserRepository;
import com.bsep.pki.security.JwtAuthenticationFilter;
import com.bsep.pki.service.SessionService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Lets a user see and manage their own active JWT sessions
 * (devices/browsers they are currently logged in on).
 */
@RestController
@RequestMapping("/api/sessions")
@RequiredArgsConstructor
public class SessionController {

    private final SessionService sessionService;
    private final UserRepository userRepository;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<SessionResponse>> mySessions(Authentication authentication,
                                                            HttpServletRequest request) {
        User user = resolveUser(authentication);
        String currentJti = (String) request.getAttribute(JwtAuthenticationFilter.CURRENT_JTI_ATTRIBUTE);
        return ResponseEntity.ok(sessionService.listForUser(user, currentJti));
    }

    @DeleteMapping("/{jti}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> revokeSession(@PathVariable String jti, Authentication authentication) {
        User user = resolveUser(authentication);
        sessionService.revoke(jti, user);
        return ResponseEntity.noContent().build();
    }

    private User resolveUser(Authentication authentication) {
        return userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));
    }
}
