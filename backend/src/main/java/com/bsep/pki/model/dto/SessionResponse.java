package com.bsep.pki.model.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class SessionResponse {
    private String jti;
    private String ipAddress;
    private String deviceLabel;
    private LocalDateTime issuedAt;
    private LocalDateTime lastActivityAt;
    private boolean current;
}
