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
public class CsrSignResponse {

    private Long id;
    private String serialNumber;
    private String commonName;
    private String organization;
    private String type;
    private LocalDateTime validFrom;
    private LocalDateTime validTo;
    private String status;
    private String issuerCommonName;
    private String certificateData; // PEM format for download
    private String message;
}

