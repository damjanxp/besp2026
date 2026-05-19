package com.bsep.pki.controller;

import com.bsep.pki.model.dto.CsrInfoDto;
import com.bsep.pki.service.CsrService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/csr")
@RequiredArgsConstructor
public class CsrController {

    private final CsrService csrService;

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
     * KT2: Parse and return CsrInfoDto only.
     * TODO: implement signing for final defense
     */
    @PostMapping("/upload")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<CsrInfoDto> uploadCsr(
            @RequestParam("csrFile") MultipartFile csrFile,
            @RequestParam("caId") Long caId,
            @RequestParam("validDays") int validDays) throws Exception {

        csrService.validateCsrFile(csrFile);
        CsrInfoDto info = csrService.parseCsr(csrFile.getBytes());
        // TODO: implement signing for final defense
        return ResponseEntity.ok(info);
    }
}

