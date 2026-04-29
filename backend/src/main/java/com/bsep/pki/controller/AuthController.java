package com.bsep.pki.controller;

import com.bsep.pki.model.dto.AuthResponse;
import com.bsep.pki.model.dto.LoginRequest;
import com.bsep.pki.model.dto.RegisterRequest;
import com.bsep.pki.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public ResponseEntity<Map<String, String>> register(@Valid @RequestBody RegisterRequest request) {
        authService.register(request);
        return ResponseEntity.ok(Map.of("message", "Registration successful. Please check your email."));
    }

    @GetMapping("/activate")
    public ResponseEntity<Map<String, String>> activate(@RequestParam String token) {
        authService.activateAccount(token);
        return ResponseEntity.ok(Map.of("message", "Account activated successfully."));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }
}

