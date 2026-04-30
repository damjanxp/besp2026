package com.bsep.pki.service;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class KeystoreServiceTest {

    @Test
    void saveAndLoadPkcs12Entry() throws Exception {
        KeystoreService service = new KeystoreService();

        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048, new SecureRandom());
        KeyPair keyPair = kpg.generateKeyPair();

        X509Certificate cert = SelfSignedTestCertificate.generate("CN=Test", keyPair);

        Path tempDir = Files.createTempDirectory("keystore-test");
        Path keystorePath = tempDir.resolve("test.p12");

        service.saveToKeystore(
                "test",
                keyPair.getPrivate(),
                cert,
                new java.security.cert.Certificate[]{cert},
                "changeit",
                keystorePath.toString()
        );

        KeystoreService.KeystoreEntry entry = service.loadFromKeystore(
                "test",
                "changeit",
                keystorePath.toString()
        );

        assertNotNull(entry.privateKey());
        assertNotNull(entry.certificate());
    }
}

