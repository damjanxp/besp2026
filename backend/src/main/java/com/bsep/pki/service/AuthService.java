package com.bsep.pki.service;

import com.bsep.pki.model.dto.AuthResponse;
import com.bsep.pki.model.dto.LoginRequest;
import com.bsep.pki.model.dto.RegisterRequest;
import com.bsep.pki.model.entity.ActivationToken;
import com.bsep.pki.model.entity.TokenType;
import com.bsep.pki.model.entity.User;
import com.bsep.pki.model.entity.UserRole;
import com.bsep.pki.repository.ActivationTokenRepository;
import com.bsep.pki.repository.UserRepository;
import com.bsep.pki.security.JwtService;
import com.bsep.pki.security.UserDetailsServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserRepository userRepository;
    private final ActivationTokenRepository activationTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JavaMailSender mailSender;
    private final JwtService jwtService;
    private final UserDetailsServiceImpl userDetailsService;

    @Value("${app.frontend.url}")
    private String frontendUrl;

    @Transactional
    public void register(RegisterRequest request) {
        if (!request.getPassword().equals(request.getConfirmPassword())) {
            throw new RuntimeException("Passwords do not match");
        }

        if (userRepository.existsByEmail(request.getEmail())) {
            throw new RuntimeException("Email is already taken");
        }

        User user = User.builder()
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .organization(request.getOrganization())
                .role(UserRole.END_ENTITY)
                .isActive(false)
                .build();

        userRepository.save(user);

        String tokenValue = UUID.randomUUID().toString();

        ActivationToken activationToken = ActivationToken.builder()
                .token(tokenValue)
                .user(user)
                .type(TokenType.ACTIVATION)
                .expiresAt(LocalDateTime.now().plusHours(24))
                .build();

        activationTokenRepository.save(activationToken);

        try {
            sendActivationEmail(user.getEmail(), tokenValue);
        } catch (Exception e) {
            log.warn("Could not send activation email to {}: {}. Use token: {}", user.getEmail(), e.getMessage(), tokenValue);
        }
    }

    @Transactional
    public void activateAccount(String tokenValue) {
        ActivationToken activationToken = activationTokenRepository.findByToken(tokenValue)
                .orElseThrow(() -> new RuntimeException("Activation token not found"));

        if (activationToken.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new RuntimeException("Activation token has expired");
        }

        if (activationToken.isUsed()) {
            throw new RuntimeException("Activation token has already been used");
        }

        User user = activationToken.getUser();
        user.setActive(true);
        activationToken.setUsed(true);

        userRepository.save(user);
        activationTokenRepository.save(activationToken);
    }

    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (!user.isActive()) {
            throw new RuntimeException("Account not activated");
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new RuntimeException("Invalid password");
        }

        UserDetails userDetails = userDetailsService.loadUserByUsername(user.getEmail());
        String token = jwtService.generateToken(userDetails);

        return AuthResponse.builder()
                .token(token)
                .email(user.getEmail())
                .role(user.getRole().name())
                .build();
    }

    private void sendActivationEmail(String email, String tokenValue) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(email);
        message.setSubject("Activate your PKI account");
        message.setText("Please activate your account by clicking the link below:\n\n"
                + frontendUrl + "/activate?token=" + tokenValue
                + "\n\nThis link expires in 24 hours.");
        mailSender.send(message);
    }
}

