package com.bsep.pki.service;

import com.bsep.pki.model.dto.CertificateResponse;
import com.bsep.pki.model.dto.CreateRootCertificateRequest;
import com.bsep.pki.model.entity.Certificate;
import com.bsep.pki.model.entity.CertificateStatus;
import com.bsep.pki.model.entity.CertificateType;
import com.bsep.pki.model.entity.User;
import com.bsep.pki.repository.CertificateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x500.X500NameBuilder;
import org.bouncycastle.asn1.x500.style.BCStyle;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.KeyUsage;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509ExtensionUtils;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.StringWriter;
import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class CertificateService {

    private final CertificateRepository certificateRepository;
    private final KeystoreService keystoreService;
    private final KeyEncryptionService keyEncryptionService;
    private final com.bsep.pki.repository.UserRepository userRepository;

    @Value("${app.keystore.dir}")
    private String keystoreDir;

    @Transactional
    public CertificateResponse generateRootCertificate(CreateRootCertificateRequest request, User adminUser) {
        try {
            // a) Generate key pair
            KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA", "BC");
            kpg.initialize(request.getKeySize(), new SecureRandom());
            KeyPair keyPair = kpg.generateKeyPair();

            // b) Build X500Name
            X500NameBuilder nameBuilder = new X500NameBuilder(BCStyle.INSTANCE);
            nameBuilder.addRDN(BCStyle.CN, request.getCommonName());
            nameBuilder.addRDN(BCStyle.O, request.getOrganization());
            if (request.getOrganizationalUnit() != null && !request.getOrganizationalUnit().isBlank()) {
                nameBuilder.addRDN(BCStyle.OU, request.getOrganizationalUnit());
            }
            if (request.getCountry() != null && !request.getCountry().isBlank()) {
                nameBuilder.addRDN(BCStyle.C, request.getCountry());
            }
            if (request.getState() != null && !request.getState().isBlank()) {
                nameBuilder.addRDN(BCStyle.ST, request.getState());
            }
            if (request.getLocality() != null && !request.getLocality().isBlank()) {
                nameBuilder.addRDN(BCStyle.L, request.getLocality());
            }
            if (request.getEmail() != null && !request.getEmail().isBlank()) {
                nameBuilder.addRDN(BCStyle.EmailAddress, request.getEmail());
            }
            X500Name subject = nameBuilder.build();

            // c) Serial number
            BigInteger serialNumber = new BigInteger(64, new SecureRandom());

            // d) Validity dates
            Date notBefore = new Date();
            Date notAfter = Date.from(
                    LocalDateTime.now().plusDays(request.getValidDays())
                            .toInstant(ZoneOffset.UTC)
            );

            // e) Build certificate
            SubjectPublicKeyInfo spki = SubjectPublicKeyInfo.getInstance(
                    keyPair.getPublic().getEncoded()
            );
            X509v3CertificateBuilder certBuilder = new X509v3CertificateBuilder(
                    subject, serialNumber, notBefore, notAfter, subject, spki
            );

            // f) Add extensions
            JcaX509ExtensionUtils extUtils = new JcaX509ExtensionUtils();

            certBuilder.addExtension(Extension.basicConstraints, true,
                    new BasicConstraints(true));
            certBuilder.addExtension(Extension.keyUsage, true,
                    new KeyUsage(KeyUsage.keyCertSign | KeyUsage.cRLSign));
            certBuilder.addExtension(Extension.subjectKeyIdentifier, false,
                    extUtils.createSubjectKeyIdentifier(keyPair.getPublic()));
            certBuilder.addExtension(Extension.authorityKeyIdentifier, false,
                    extUtils.createAuthorityKeyIdentifier(keyPair.getPublic()));

            // g) Sign
            ContentSigner signer = new JcaContentSignerBuilder("SHA256WithRSAEncryption")
                    .setProvider("BC")
                    .build(keyPair.getPrivate());
            X509CertificateHolder certHolder = certBuilder.build(signer);
            X509Certificate certificate = new JcaX509CertificateConverter()
                    .setProvider("BC")
                    .getCertificate(certHolder);

            // h) Convert to PEM
            StringWriter sw = new StringWriter();
            try (JcaPEMWriter pw = new JcaPEMWriter(sw)) {
                pw.writeObject(certificate);
            }
            String pemData = sw.toString();

            // i) Save to keystore
            String alias = "root-" + serialNumber.toString(16).substring(0, 8);
            String keystorePassword = generateKeystorePassword();
            String keystorePath = resolveKeystorePath(adminUser.getId(), alias);
            keystoreService.saveToKeystore(
                    alias, keyPair.getPrivate(), certificate, new java.security.cert.Certificate[]{certificate},
                    keystorePassword, keystorePath
            );

            String encryptedKeystorePassword = keyEncryptionService.encryptPassword(
                    keystorePassword,
                    keyEncryptionService.generateUserEncryptionKey(adminUser.getId())
            );
            adminUser.setKeystorePasswordEncrypted(encryptedKeystorePassword);
            userRepository.save(adminUser);

            // j) Save to database
            Certificate cert = Certificate.builder()
                    .serialNumber(serialNumber.toString())
                    .type(CertificateType.ROOT)
                    .commonName(request.getCommonName())
                    .organization(request.getOrganization())
                    .issuer(null)
                    .owner(adminUser)
                    .validFrom(LocalDateTime.now())
                    .validTo(LocalDateTime.now().plusDays(request.getValidDays()))
                    .status(CertificateStatus.ACTIVE)
                    .keystoreAlias(alias)
                    .certificateData(pemData)
                    .build();

            Certificate savedCert = certificateRepository.save(cert);

            // k) Return response
            return mapToResponse(savedCert);

        } catch (Exception e) {
            throw new RuntimeException("Failed to generate root certificate: " + e.getMessage(), e);
        }
    }

    private String resolveKeystorePath(Long userId, String alias) {
        return java.nio.file.Paths.get(keystoreDir, String.valueOf(userId), alias + ".p12").toString();
    }

    private String generateKeystorePassword() {
        byte[] randomBytes = new byte[32];
        new java.security.SecureRandom().nextBytes(randomBytes);
        return java.util.Base64.getEncoder().encodeToString(randomBytes);
    }

    public List<CertificateResponse> getAllCertificates() {
        return certificateRepository.findAll().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    private CertificateResponse mapToResponse(Certificate cert) {
        return CertificateResponse.builder()
                .id(cert.getId())
                .serialNumber(cert.getSerialNumber())
                .type(cert.getType().name())
                .commonName(cert.getCommonName())
                .organization(cert.getOrganization())
                .issuerCommonName(cert.getIssuer() != null ? cert.getIssuer().getCommonName() : null)
                .ownerEmail(cert.getOwner() != null ? cert.getOwner().getEmail() : null)
                .validFrom(cert.getValidFrom())
                .validTo(cert.getValidTo())
                .status(cert.getStatus().name())
                .certificateData(cert.getCertificateData())
                .createdAt(cert.getCreatedAt())
                .build();
    }
}
