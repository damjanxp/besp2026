package com.bsep.pki.model.dto;

import com.bsep.pki.validation.ValidPassword;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ResetPasswordRequest {

    @NotBlank
    private String token;

    @NotBlank
    @ValidPassword
    private String newPassword;

    @NotBlank
    private String confirmPassword;
}

