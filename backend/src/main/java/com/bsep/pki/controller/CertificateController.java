package com.bsep.pki.controller;

import com.bsep.pki.model.dto.CertificateDetailsResponse;
import com.bsep.pki.model.dto.CertificateResponse;
import com.bsep.pki.model.dto.CreateRootCertificateRequest;
import com.bsep.pki.model.entity.User;
import com.bsep.pki.repository.UserRepository;
import com.bsep.pki.service.CertificateService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.io.ByteArrayInputStream;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;

import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.web.server.ResponseStatusException;

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
    public ResponseEntity<List<CertificateResponse>> getCertificates(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            Authentication authentication
    ) {
        User user = resolveUser(authentication);
        return ResponseEntity.ok(certificateService.getCertificatesForUser(user, pageable));
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<CertificateDetailsResponse> getCertificateDetails(
            @PathVariable Long id,
            Authentication authentication
    ) {
        User user = resolveUser(authentication);
        return ResponseEntity.ok(certificateService.getCertificateDetails(id, user));
    }

    @GetMapping("/{id}/download")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<byte[]> downloadCertificate(
            @PathVariable Long id,
            @RequestParam(defaultValue = "PEM") String format,
            Authentication authentication
    ) {
        User user = resolveUser(authentication);
        var cert = certificateService.requireAccessibleCertificate(id, user);
        String serial = cert.getSerialNumber();
        String pemData = cert.getCertificateData();
        if (pemData == null || pemData.isBlank()) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "Certificate data not found");
        }

        if ("PEM".equalsIgnoreCase(format)) {
            return ResponseEntity.ok()
                    .contentType(MediaType.TEXT_PLAIN)
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=cert_" + serial + ".pem")
                    .body(pemData.getBytes(StandardCharsets.US_ASCII));
        }

        if ("DER".equalsIgnoreCase(format)) {
            byte[] derBytes = decodePemToDer(pemData);
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=cert_" + serial + ".der")
                    .body(derBytes);
        }

        throw new ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "Unsupported format");
    }

    @GetMapping("/my/public-key")
    @PreAuthorize("hasAuthority('END_ENTITY')")
    public ResponseEntity<String> getMyPublicKey(Authentication authentication) {
        User user = resolveUser(authentication);
        var cert = certificateService.getLatestActiveEndEntityCertificate(user)
                .orElseThrow(() -> new ResponseStatusException(
                        org.springframework.http.HttpStatus.NOT_FOUND,
                        "Nema aktivnog EE sertifikata za ovog korisnika"
                ));

        String pemData = cert.getCertificateData();
        if (pemData == null || pemData.isBlank()) {
            throw new ResponseStatusException(
                    org.springframework.http.HttpStatus.NOT_FOUND,
                    "Nema aktivnog EE sertifikata za ovog korisnika"
            );
        }

        X509Certificate x509 = parseCertificate(pemData);
        byte[] encoded = x509.getPublicKey().getEncoded();
        String base64 = Base64.getMimeEncoder(64, new byte[]{'\n'}).encodeToString(encoded);
        String pem = "-----BEGIN PUBLIC KEY-----\n" + base64 + "\n-----END PUBLIC KEY-----";

        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_PLAIN)
                .body(pem);
    }

    @GetMapping("/available-cas")
    @PreAuthorize("hasAuthority('END_ENTITY')")
    public ResponseEntity<List<CertificateResponse>> getAvailableCas() {
        return ResponseEntity.ok(certificateService.getAvailableCaCertificates());
    }

    private X509Certificate parseCertificate(String pemData) {
        try {
            CertificateFactory factory = CertificateFactory.getInstance("X.509");
            return (X509Certificate) factory.generateCertificate(
                    new ByteArrayInputStream(pemData.getBytes(StandardCharsets.US_ASCII))
            );
        } catch (Exception e) {
            throw new ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST,
                    "Neispravan sertifikat"
            );
        }
    }

    private User resolveUser(Authentication authentication) {
        return userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));
    }

    private byte[] decodePemToDer(String pemData) {
        String normalized = pemData
                .replace("-----BEGIN CERTIFICATE-----", "")
                .replace("-----END CERTIFICATE-----", "")
                .replaceAll("\\s", "");
        return Base64.getDecoder().decode(normalized);
    }

}
