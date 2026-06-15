package com.bsep.pki.controller;

import com.bsep.pki.service.CrlService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/crl")
@RequiredArgsConstructor
public class CrlController {

    private final CrlService crlService;

    @GetMapping(value = "/{serialNumber}", produces = "application/pkix-crl")
    public ResponseEntity<byte[]> getCrl(@PathVariable String serialNumber) {
        byte[] crlData = crlService.generateCrl(serialNumber);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/pkix-crl"))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"crl_" + serialNumber + ".crl\"")
                .body(crlData);
    }
}
