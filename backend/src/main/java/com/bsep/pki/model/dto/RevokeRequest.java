package com.bsep.pki.model.dto;

import com.bsep.pki.model.entity.RevocationReason;
import lombok.Data;

@Data
public class RevokeRequest {
    private RevocationReason reason;
}
