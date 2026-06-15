package com.bsep.pki.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class TemplateRequest {

    @NotBlank
    private String name;

    @NotNull
    private Long caIssuerId;

    private String cnRegex;
    private String sanRegex;
    private Integer maxTtlDays;
    private String defaultKeyUsage;
    private String defaultExtendedKeyUsage;
}
