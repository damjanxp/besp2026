package com.bsep.pki.model.dto;

import lombok.*;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TemplateResponse {
    private Long id;
    private String name;
    private Long caIssuerId;
    private String caIssuerCommonName;
    private String ownerEmail;
    private String cnRegex;
    private String sanRegex;
    private Integer maxTtlDays;
    private String defaultKeyUsage;
    private String defaultExtendedKeyUsage;
    private LocalDateTime createdAt;
}
