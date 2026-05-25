package com.bsep.pki.controller;

import com.bsep.pki.model.dto.CsrInfoDto;
import com.bsep.pki.model.dto.CsrSignResponse;
import com.bsep.pki.model.entity.Certificate;
import com.bsep.pki.model.entity.User;
import com.bsep.pki.repository.UserRepository;
import com.bsep.pki.service.CsrService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/csr")
@RequiredArgsConstructor
public class CsrController {

    private final CsrService csrService;
    private final UserRepository userRepository;

    /**
     * KT2: Parse CSR and return preview info. Signing is NOT performed.
     */
    @PostMapping("/preview")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<CsrInfoDto> previewCsr(@RequestParam("csrFile") MultipartFile csrFile) throws Exception {
        csrService.validateCsrFile(csrFile);
        CsrInfoDto info = csrService.parseCsr(csrFile.getBytes());
        return ResponseEntity.ok(info);
    }

    /**
     * Sign CSR with selected CA and create certificate
     */
    @PostMapping("/upload")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<CsrSignResponse> uploadCsr(
            @RequestParam("csrFile") MultipartFile csrFile,
            @RequestParam("caId") Long caId,
            @RequestParam("validDays") int validDays) throws Exception {

        csrService.validateCsrFile(csrFile);

        // Get current authenticated user
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        User requester = userRepository.findByEmail(auth.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));

        // Sign the CSR
        Certificate signedCert = csrService.signCsr(csrFile.getBytes(), caId, validDays, requester);

        // Build response
        CsrSignResponse response = CsrSignResponse.builder()
                .id(signedCert.getId())
                .serialNumber(signedCert.getSerialNumber())
                .commonName(signedCert.getCommonName())
                .organization(signedCert.getOrganization())
                .type(signedCert.getType().toString())
                .validFrom(signedCert.getValidFrom())
                .validTo(signedCert.getValidTo())
                .status(signedCert.getStatus().toString())
                .issuerCommonName(signedCert.getIssuer() != null ? signedCert.getIssuer().getCommonName() : "N/A")
                .certificateData(signedCert.getCertificateData())
                .message("CSR je uspješno potpisano i sertifikat je kreiран")
                .build();

        return ResponseEntity.ok(response);
    }
}

