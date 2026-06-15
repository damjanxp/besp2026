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
public class PasswordEntryResponse {

    private Long id;
    private String siteName;
    private String username;
    private Long ownerId;
    private String ownerEmail;
    private String encryptedPassword; // Included for the current user's share
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String createdByEmail;
}

