package com.bsep.pki.model.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CsrInfoDto {
    private String commonName;
    private String organization;
    private String organizationalUnit;
    private String country;
    private String state;
    private String locality;
    private String email;

    private String publicKeyAlgorithm;
    private int publicKeySize;
    private String signatureAlgorithm;

    private boolean isSignatureValid;
    private String rawPem;
}

