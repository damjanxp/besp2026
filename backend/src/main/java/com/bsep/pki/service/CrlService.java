package com.bsep.pki.service;

import com.bsep.pki.model.entity.Certificate;
import com.bsep.pki.model.entity.CertificateStatus;
import com.bsep.pki.model.entity.CertificateType;
import com.bsep.pki.model.entity.RevocationReason;
import com.bsep.pki.repository.CertificateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.asn1.x509.CRLNumber;
import org.bouncycastle.asn1.x509.CRLReason;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.cert.X509CRLHolder;
import org.bouncycastle.cert.X509v2CRLBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509ExtensionUtils;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class CrlService {

    private final CertificateRepository certificateRepository;
    private final KeystoreService keystoreService;
    private final KeyEncryptionService keyEncryptionService;

    @Value("${app.keystore.dir}")
    private String keystoreDir;

    @Transactional(readOnly = true)
    public byte[] generateCrl(String caSerialNumber) {
        Certificate caCert = certificateRepository.findBySerialNumber(caSerialNumber)
                .orElseThrow(() -> new RuntimeException("CA certificate not found: " + caSerialNumber));

        if (caCert.getType() == CertificateType.END_ENTITY) {
            throw new RuntimeException("End-entity certificates cannot issue CRLs");
        }

        if (caCert.getEncryptedKeystorePassword() == null || caCert.getKeystoreAlias() == null) {
            throw new RuntimeException("CA certificate has no accessible private key");
        }

        String password = keyEncryptionService.decryptPassword(
                caCert.getEncryptedKeystorePassword(),
                keyEncryptionService.generateUserEncryptionKey(caCert.getOwner().getId())
        );
        String keystorePath = resolveKeystorePath(caCert.getOwner().getId(), caCert.getKeystoreAlias());
        KeystoreService.KeystoreEntry entry = keystoreService.loadFromKeystore(
                caCert.getKeystoreAlias(), password, keystorePath
        );

        List<Certificate> revokedCerts = certificateRepository.findByIssuer(caCert).stream()
                .filter(c -> c.getStatus() == CertificateStatus.REVOKED)
                .toList();

        try {
            X509Certificate caX509 = parseCertificate(caCert.getCertificateData());
            Date now = new Date();
            Date nextUpdate = toDate(LocalDateTime.now().plusDays(1));

            X509v2CRLBuilder crlBuilder = new X509v2CRLBuilder(
                    new JcaX509CertificateHolder(caX509).getSubject(), now);
            crlBuilder.setNextUpdate(nextUpdate);

            for (Certificate revoked : revokedCerts) {
                BigInteger serial = new BigInteger(revoked.getSerialNumber());
                Date revokedAt = toDate(revoked.getRevokedAt());
                crlBuilder.addCRLEntry(serial, revokedAt, mapReason(revoked.getRevocationReason()));
            }

            JcaX509ExtensionUtils extUtils = new JcaX509ExtensionUtils();
            crlBuilder.addExtension(Extension.authorityKeyIdentifier, false,
                    extUtils.createAuthorityKeyIdentifier(caX509));
            crlBuilder.addExtension(Extension.cRLNumber, false,
                    new CRLNumber(BigInteger.valueOf(System.currentTimeMillis() / 1000)));

            ContentSigner signer = new JcaContentSignerBuilder("SHA256WithRSAEncryption")
                    .setProvider("BC")
                    .build(entry.privateKey());

            X509CRLHolder crlHolder = crlBuilder.build(signer);
            log.info("Generated CRL for CA {} with {} revoked entries", caSerialNumber, revokedCerts.size());
            return crlHolder.getEncoded();

        } catch (Exception e) {
            throw new RuntimeException("Failed to generate CRL: " + e.getMessage(), e);
        }
    }

    private int mapReason(RevocationReason reason) {
        if (reason == null) return CRLReason.unspecified;
        return switch (reason) {
            case KEY_COMPROMISE -> CRLReason.keyCompromise;
            case CA_COMPROMISE -> CRLReason.cACompromise;
            case AFFILIATION_CHANGED -> CRLReason.affiliationChanged;
            case SUPERSEDED -> CRLReason.superseded;
            case CESSATION_OF_OPERATION -> CRLReason.cessationOfOperation;
            case CERTIFICATE_HOLD -> CRLReason.certificateHold;
            case PRIVILEGE_WITHDRAWN -> CRLReason.privilegeWithdrawn;
            case AA_COMPROMISE -> CRLReason.aACompromise;
            default -> CRLReason.unspecified;
        };
    }

    private Date toDate(LocalDateTime ldt) {
        if (ldt == null) return new Date();
        return Date.from(ldt.atZone(ZoneId.systemDefault()).toInstant());
    }

    private String resolveKeystorePath(Long userId, String alias) {
        return java.nio.file.Paths.get(keystoreDir, String.valueOf(userId), alias + ".p12").toString();
    }

    private X509Certificate parseCertificate(String pemData) throws Exception {
        CertificateFactory factory = CertificateFactory.getInstance("X.509");
        return (X509Certificate) factory.generateCertificate(
                new ByteArrayInputStream(pemData.getBytes(StandardCharsets.US_ASCII))
        );
    }
}
