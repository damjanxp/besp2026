package com.bsep.pki.service;

import com.bsep.pki.exception.CaptchaException;
import com.bsep.pki.model.dto.AuthResponse;
import com.bsep.pki.model.dto.LoginRequest;
import com.bsep.pki.model.dto.RegisterRequest;
import com.bsep.pki.model.dto.ForgotPasswordRequest;
import com.bsep.pki.model.dto.ResetPasswordRequest;
import com.bsep.pki.model.entity.ActivationToken;
import com.bsep.pki.model.entity.TokenType;
import com.bsep.pki.model.entity.User;
import com.bsep.pki.model.entity.UserRole;
import com.bsep.pki.model.entity.UserSession;
import com.bsep.pki.repository.ActivationTokenRepository;
import com.bsep.pki.repository.UserRepository;
import com.bsep.pki.repository.UserSessionRepository;
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
import java.time.ZoneId;
import java.util.List;
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
    private final CaptchaService captchaService;
    private final UserSessionRepository userSessionRepository;

    @Value("${app.frontend.url}")
    private String frontendUrl;

    @Transactional
    public void register(RegisterRequest request) {
        if (!request.getPassword().equals(request.getConfirmPassword())) {
            throw new RuntimeException("Passwords do not match");
        }

        if (userRepository.existsByEmail(request.getEmail())) {
            // Generic message — ne otkrivamo da email vec postoji (OWASP)
            log.warn("Registration attempt with existing email: {}", request.getEmail());
            return;
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
        captchaService.verifyRecaptcha(request.getCaptchaToken());

        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (!user.isActive()) {
            log.warn("Login attempt on inactive account: {}", request.getEmail());
            throw new RuntimeException("Nalog nije aktiviran. Molimo proverite vaš email i kliknite na aktivacioni link.");
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            log.warn("Failed login attempt for: {}", request.getEmail());
            throw new RuntimeException("Invalid credentials");
        }

        UserDetails userDetails = userDetailsService.loadUserByUsername(user.getEmail());
        String token = jwtService.generateToken(userDetails);

        saveSession(user, token);

        return AuthResponse.builder()
                .token(token)
                .email(user.getEmail())
                .role(user.getRole().name())
                .build();
    }

    private void saveSession(User user, String token) {
        UserSession session = UserSession.builder()
                .user(user)
                .jti(jwtService.extractJti(token))
                .expiresAt(jwtService.extractExpiration(token).toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime())
                .build();
        userSessionRepository.save(session);
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

    private void sendPasswordResetEmail(String email, String tokenValue) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(email);
        message.setSubject("Reset your PKI password");
        message.setText("You requested a password reset. Click the link below to set a new password:\n\n"
                + frontendUrl + "/reset-password?token=" + tokenValue
                + "\n\nThis link expires in 1 hour.");
        mailSender.send(message);
    }

    @Transactional
    public void forgotPassword(ForgotPasswordRequest request) {
        userRepository.findByEmail(request.getEmail()).ifPresent(user -> {
            activationTokenRepository.deleteByUserAndType(user, TokenType.PASSWORD_RESET);

            String tokenValue = UUID.randomUUID().toString();
            ActivationToken resetToken = ActivationToken.builder()
                    .token(tokenValue)
                    .user(user)
                    .type(TokenType.PASSWORD_RESET)
                    .expiresAt(LocalDateTime.now().plusHours(1))
                    .build();

            activationTokenRepository.save(resetToken);

            try {
                sendPasswordResetEmail(user.getEmail(), tokenValue);
            } catch (Exception e) {
                log.warn("Could not send password reset email to {}: {}. Use token: {}", user.getEmail(), e.getMessage(), tokenValue);
            }
        });
    }

    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        ActivationToken resetToken = activationTokenRepository.findByToken(request.getToken())
                .orElseThrow(() -> new RuntimeException("Reset token not found"));

        if (resetToken.getType() != TokenType.PASSWORD_RESET) {
            throw new RuntimeException("Invalid reset token");
        }

        if (resetToken.isUsed()) {
            throw new RuntimeException("Reset token has already been used");
        }

        if (resetToken.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new RuntimeException("Reset token has expired");
        }

        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new RuntimeException("Passwords do not match");
        }

        User user = resetToken.getUser();
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        resetToken.setUsed(true);

        userRepository.save(user);
        activationTokenRepository.save(resetToken);

        List<UserSession> activeSessions = userSessionRepository
                .findByUserAndRevokedFalseAndExpiresAtAfter(user, LocalDateTime.now());
        activeSessions.forEach(session -> session.setRevoked(true));
        userSessionRepository.saveAll(activeSessions);
    }
}
