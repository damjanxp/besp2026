package com.bsep.pki.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.*;
import java.security.cert.X509Certificate;
import java.util.Base64;

@Service
@Slf4j
public class KeystoreService {

    public String saveRootToKeystore(String alias, PrivateKey privateKey, X509Certificate certificate, String keystoreDir) {
        try {
            // Generate random keystore password
            byte[] randomBytes = new byte[32];
            new SecureRandom().nextBytes(randomBytes);
            String keystorePassword = Base64.getEncoder().encodeToString(randomBytes);

            // Create PKCS12 keystore
            KeyStore ks = KeyStore.getInstance("PKCS12", "BC");
            ks.load(null, null);
            ks.setKeyEntry(alias, privateKey, keystorePassword.toCharArray(),
                    new java.security.cert.Certificate[]{certificate});

            // Create directory if not exists
            Path dirPath = Paths.get(keystoreDir);
            if (!Files.exists(dirPath)) {
                Files.createDirectories(dirPath);
            }

            // Save keystore to file
            Path keystorePath = Paths.get(keystoreDir, alias + ".p12");
            try (FileOutputStream fos = new FileOutputStream(keystorePath.toFile())) {
                ks.store(fos, keystorePassword.toCharArray());
            }

            log.info("Keystore saved to: {}", keystorePath.toAbsolutePath());
            return keystorePassword;

        } catch (Exception e) {
            throw new RuntimeException("Failed to save keystore: " + e.getMessage(), e);
        }
    }

    public KeystoreEntry loadPrivateKey(String alias, String keystorePassword, String keystoreDir) {
        try {
            Path keystorePath = Paths.get(keystoreDir, alias + ".p12");
            KeyStore ks = KeyStore.getInstance("PKCS12", "BC");

            try (FileInputStream fis = new FileInputStream(keystorePath.toFile())) {
                ks.load(fis, keystorePassword.toCharArray());
            }

            PrivateKey privateKey = (PrivateKey) ks.getKey(alias, keystorePassword.toCharArray());
            X509Certificate certificate = (X509Certificate) ks.getCertificate(alias);

            return new KeystoreEntry(privateKey, certificate);

        } catch (Exception e) {
            throw new RuntimeException("Failed to load keystore: " + e.getMessage(), e);
        }
    }

    public record KeystoreEntry(PrivateKey privateKey, X509Certificate certificate) {}
}

