package com.bsep.pki.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents a user's public profile — id, email, and their stored public key.
 * Used by the password manager when sharing entries with other users.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserPublicProfileDto {
    private Long id;
    private String email;
    private String firstName;
    private String lastName;
    /** RSA public key in SPKI PEM format. Null if the user has not uploaded their key yet. */
    private String publicKeySpki;
}

