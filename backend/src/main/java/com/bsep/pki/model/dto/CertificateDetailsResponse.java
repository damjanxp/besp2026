package com.bsep.pki.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CertificateDetailsResponse {

    private Long id;
    private String serialNumber;
    private String serialNumberFull;
    private String type;
    private String commonName;
    private String organization;
    private String organizationalUnit;
    private String country;
    private String state;
    private String locality;
    private String emailAddress;
    private LocalDateTime validFrom;
    private LocalDateTime validTo;
    private String status;
    private String issuerCommonName;
    private String keyAlgorithm;
    private boolean basicConstraints;
    private List<String> keyUsage;
}

