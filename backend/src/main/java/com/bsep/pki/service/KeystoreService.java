package com.bsep.pki.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;

@Service
@Slf4j
public class KeystoreService {

    public void saveToKeystore(String alias,
                               PrivateKey privateKey,
                               X509Certificate certificate,
                               java.security.cert.Certificate[] chain,
                               String keystorePassword,
                               String keystorePath) {
        try {
            KeyStore ks = KeyStore.getInstance("PKCS12");
            ks.load(null, null);

            java.security.cert.Certificate[] effectiveChain = chain != null && chain.length > 0
                    ? chain
                    : new java.security.cert.Certificate[]{certificate};
            ks.setKeyEntry(alias, privateKey, keystorePassword.toCharArray(), effectiveChain);

            Path filePath = Paths.get(keystorePath);
            Path parentDir = filePath.getParent();
            if (parentDir != null && !Files.exists(parentDir)) {
                Files.createDirectories(parentDir);
            }

            try (FileOutputStream fos = new FileOutputStream(filePath.toFile())) {
                ks.store(fos, keystorePassword.toCharArray());
            }

            log.info("Keystore saved to: {}", filePath.toAbsolutePath());
        } catch (Exception e) {
            throw new RuntimeException("Failed to save keystore: " + e.getMessage(), e);
        }
    }

    public KeystoreEntry loadFromKeystore(String alias, String keystorePassword, String keystorePath) {
        try {
            KeyStore ks = KeyStore.getInstance("PKCS12");
            Path filePath = Paths.get(keystorePath);

            try (FileInputStream fis = new FileInputStream(filePath.toFile())) {
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
