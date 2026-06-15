package com.bsep.pki.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CertificateResponse {

    private Long id;
    private String serialNumber;
    private String type;
    private String commonName;
    private String organization;
    private String issuerCommonName;
    private String ownerEmail;
    private LocalDateTime validFrom;
    private LocalDateTime validTo;
    private String status;
    private String certificateData;
    private LocalDateTime createdAt;
    private String revocationReason;
    private LocalDateTime revokedAt;
}

