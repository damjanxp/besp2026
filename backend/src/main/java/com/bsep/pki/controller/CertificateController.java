package com.bsep.pki.controller;

import com.bsep.pki.model.dto.CertificateResponse;
import com.bsep.pki.model.dto.CreateRootCertificateRequest;
import com.bsep.pki.model.dto.IssueCertificateRequest;
import com.bsep.pki.model.dto.RevokeRequest;
import com.bsep.pki.model.entity.User;
import com.bsep.pki.repository.UserRepository;
import com.bsep.pki.service.CertificateService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/certificates")
@RequiredArgsConstructor
public class CertificateController {

    private final CertificateService certificateService;
    private final UserRepository userRepository;

    @PostMapping("/root")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<CertificateResponse> createRootCertificate(
            @Valid @RequestBody CreateRootCertificateRequest request,
            Authentication authentication
    ) {
        String email = authentication.getName();
        User adminUser = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        CertificateResponse response = certificateService.generateRootCertificate(request, adminUser);
        return ResponseEntity.ok(response);
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<CertificateResponse>> getAllCertificates() {
        return ResponseEntity.ok(certificateService.getAllCertificates());
    }

    @GetMapping("/issuers")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'CA_USER')")
    public ResponseEntity<List<CertificateResponse>> getAvailableIssuers(Authentication authentication) {
        String email = authentication.getName();
        User caller = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));
        return ResponseEntity.ok(certificateService.getAvailableIssuers(caller));
    }

    @PostMapping("/issue")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'CA_USER')")
    public ResponseEntity<CertificateResponse> issueCertificate(
            @Valid @RequestBody IssueCertificateRequest request,
            Authentication authentication
    ) {
        String email = authentication.getName();
        User caller = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));
        return ResponseEntity.ok(certificateService.issueCertificate(request, caller));
    }

    @PostMapping("/{id}/revoke")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> revokeCertificate(
            @PathVariable Long id,
            @RequestBody RevokeRequest request,
            Authentication authentication
    ) {
        String email = authentication.getName();
        User caller = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));
        certificateService.revokeCertificate(id, request.getReason(), caller);
        return ResponseEntity.ok().build();
    }
}



