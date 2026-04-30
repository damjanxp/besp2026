package com.bsep.pki.service;

import org.junit.jupiter.api.Test;

import javax.crypto.spec.SecretKeySpec;

import static org.junit.jupiter.api.Assertions.assertEquals;

class KeyEncryptionServiceTest {

    @Test
    void encryptAndDecryptRoundTrip() {
        KeyEncryptionService service = new KeyEncryptionService("test-master-secret-32chars-minimum");
        SecretKeySpec key = service.generateUserEncryptionKey(42L);

        String encrypted = service.encryptPassword("p@ssw0rd!", key);
        String decrypted = service.decryptPassword(encrypted, key);

        assertEquals("p@ssw0rd!", decrypted);
    }
}

