package com.bsep.pki.config;

import com.bsep.pki.model.entity.Certificate;
import com.bsep.pki.model.entity.CertificateStatus;
import com.bsep.pki.model.entity.CertificateType;
import com.bsep.pki.model.entity.User;
import com.bsep.pki.model.entity.UserRole;
import com.bsep.pki.repository.CertificateRepository;
import com.bsep.pki.repository.UserRepository;
import com.bsep.pki.service.KeyEncryptionService;
import com.bsep.pki.service.KeystoreService;
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
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.StringWriter;
import java.math.BigInteger;
import java.nio.file.Paths;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Date;

@Component
@Profile("dev")
@Order(2)
@RequiredArgsConstructor
@Slf4j
public class CertificateDataSeeder implements ApplicationRunner {

    private final CertificateRepository certificateRepository;
    private final UserRepository userRepository;
    private final KeystoreService keystoreService;
    private final KeyEncryptionService keyEncryptionService;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.keystore.dir}")
    private String keystoreDir;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        // Skip if certificates already exist
        if (certificateRepository.count() > 0) {
            log.info("Certificates already exist, skipping seeding.");
            return;
        }

        try {
            // 1. Create ROOT certificate (admin)
            User admin = userRepository.findByEmail("admin@pki.com")
                    .orElseThrow(() -> new RuntimeException("Admin user not found"));

            Certificate adminRootCert = createRootCertificate(admin, "Admin Root CA");
            if (adminRootCert == null) {
                log.error("Failed to create admin root certificate");
                return;
            }
            log.info("Created ROOT certificate: {}", adminRootCert.getCommonName());

            // 2. Create CA_USER's own ROOT certificate
            User caUser = findOrCreateUser("causer@pki.com", "CA", "User", UserRole.CA_USER,
                    "Test Org", "CAUser1234!");

            Certificate caRootCert = createRootCertificate(caUser, "CA User Root CA");
            if (caRootCert == null) {
                log.error("Failed to create ca user root certificate");
                return;
            }
            log.info("Created ROOT certificate: {}", caRootCert.getCommonName());

            // 3. Create INTERMEDIATE certificate (owned by CA_USER, signed by their root)
            Certificate intermediateCert = createIntermediateCertificate(caRootCert, caUser, caUser);
            if (intermediateCert == null) {
                log.error("Failed to create intermediate certificate");
                return;
            }
            log.info("Created INTERMEDIATE certificate: {}", intermediateCert.getCommonName());

            // 4. Create END_ENTITY certificates
            User endUser = findOrCreateUser("enduser@pki.com", "End", "User", UserRole.END_ENTITY,
                    "Test Org", "EndUser1234!");

            Certificate endEntity1 = createEndEntityCertificate(
                    "test.user@example.com", "Test User", intermediateCert, caUser);
            if (endEntity1 != null) {
                log.info("Created END_ENTITY certificate: {}", endEntity1.getCommonName());
            }

            Certificate endEntity2 = createEndEntityCertificate(
                    "user2.example.com", "User 2", intermediateCert, endUser);
            if (endEntity2 != null) {
                log.info("Created END_ENTITY certificate: {}", endEntity2.getCommonName());
            }

            log.info("Test certificate data seeded: 5 certificates created");

        } catch (Exception e) {
            log.error("Error seeding certificate data", e);
        }
    }

    private Certificate createRootCertificate(User admin, String commonName) {
        try {
            // 1. Generate key pair
            KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA", "BC");
            kpg.initialize(2048, new SecureRandom());
            KeyPair keyPair = kpg.generateKeyPair();

            // 2. Build subject for root
            X500NameBuilder nameBuilder = new X500NameBuilder(BCStyle.INSTANCE);
            nameBuilder.addRDN(BCStyle.CN, commonName);
            nameBuilder.addRDN(BCStyle.O, "Test Org");
            nameBuilder.addRDN(BCStyle.C, "RS");
            X500Name subject = nameBuilder.build();

            // 3. Serial number and validity dates
            BigInteger serialNumber = new BigInteger(64, new SecureRandom());
            LocalDateTime validFrom = LocalDateTime.now();
            LocalDateTime validTo = validFrom.plusDays(365 * 10); // 10 years
            Date notBefore = new Date();
            Date notAfter = Date.from(validTo.toInstant(ZoneOffset.UTC));

            // 4. Build certificate (self-signed)
            SubjectPublicKeyInfo spki = SubjectPublicKeyInfo.getInstance(
                    keyPair.getPublic().getEncoded());
            X509v3CertificateBuilder certBuilder = new X509v3CertificateBuilder(
                    subject, serialNumber, notBefore, notAfter, subject, spki);

            // 5. Add extensions (root CA certificate)
            JcaX509ExtensionUtils extUtils = new JcaX509ExtensionUtils();
            certBuilder.addExtension(Extension.basicConstraints, true,
                    new BasicConstraints(true)); // CA=true, self-signed
            certBuilder.addExtension(Extension.keyUsage, true,
                    new KeyUsage(KeyUsage.keyCertSign | KeyUsage.cRLSign));
            certBuilder.addExtension(Extension.subjectKeyIdentifier, false,
                    extUtils.createSubjectKeyIdentifier(keyPair.getPublic()));
            certBuilder.addExtension(Extension.authorityKeyIdentifier, false,
                    extUtils.createAuthorityKeyIdentifier(keyPair.getPublic()));

            // 6. Sign with its own private key (self-signed)
            ContentSigner signer = new JcaContentSignerBuilder("SHA256WithRSAEncryption")
                    .setProvider("BC")
                    .build(keyPair.getPrivate());
            X509CertificateHolder certHolder = certBuilder.build(signer);
            X509Certificate certificate = new JcaX509CertificateConverter()
                    .setProvider("BC")
                    .getCertificate(certHolder);

            // 7. Convert to PEM
            StringWriter sw = new StringWriter();
            try (JcaPEMWriter pw = new JcaPEMWriter(sw)) {
                pw.writeObject(certificate);
            }
            String pemData = sw.toString();

            // 8. Save to keystore
            String alias = "root-" + serialNumber.toString(16).substring(0, 8);
            String keystorePassword = getOrCreateUserKeystorePassword(admin);
            String keystorePath = resolveKeystorePath(admin.getId(), alias);
            keystoreService.saveToKeystore(
                    alias, keyPair.getPrivate(), certificate,
                    new java.security.cert.Certificate[]{certificate},
                    keystorePassword, keystorePath
            );

            // 9. Persist user keystore password if needed
            ensureUserKeystorePasswordStored(admin, keystorePassword);

            // 10. Save to database
            Certificate rootCert = Certificate.builder()
                    .serialNumber(serialNumber.toString())
                    .type(CertificateType.ROOT)
                    .commonName(commonName)
                    .organization("Test Org")
                    .issuer(null)
                    .owner(admin)
                    .validFrom(validFrom)
                    .validTo(validTo)
                    .status(CertificateStatus.ACTIVE)
                    .keystoreAlias(alias)
                    .certificateData(pemData)
                    .build();

            return certificateRepository.save(rootCert);

        } catch (Exception e) {
            log.error("Failed to create root certificate", e);
            return null;
        }
    }

    private Certificate createIntermediateCertificate(Certificate rootCert, User admin, User caUser) {
        try {
            // 1. Generate keypair for intermediate
            KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA", "BC");
            kpg.initialize(2048, new SecureRandom());
            KeyPair keyPair = kpg.generateKeyPair();

            // 2. Load root's private key (signed by admin's root)
            String rootKeystorePassword = resolveKeystorePassword(admin);
            PrivateKey rootPrivateKey = (PrivateKey) keystoreService.loadFromKeystore(
                    rootCert.getKeystoreAlias(),
                    rootKeystorePassword,
                    resolveKeystorePath(admin.getId(), rootCert.getKeystoreAlias())
            ).privateKey();

            X509Certificate rootX509 = keystoreService.loadFromKeystore(
                    rootCert.getKeystoreAlias(),
                    rootKeystorePassword,
                    resolveKeystorePath(admin.getId(), rootCert.getKeystoreAlias())
            ).certificate();

            if (rootPrivateKey == null || rootX509 == null) {
                log.error("Failed to load root certificate private key or certificate");
                return null;
            }

            // 3. Build subject for intermediate
            X500NameBuilder nameBuilder = new X500NameBuilder(BCStyle.INSTANCE);
            nameBuilder.addRDN(BCStyle.CN, "Test Intermediate CA");
            nameBuilder.addRDN(BCStyle.O, "Test Org");
            nameBuilder.addRDN(BCStyle.C, "RS");
            X500Name subject = nameBuilder.build();

            // 4. Serial number and validity dates
            BigInteger serialNumber = new BigInteger(64, new SecureRandom());
            LocalDateTime validFrom = LocalDateTime.now();
            LocalDateTime validTo = validFrom.plusDays(365 * 5); // 5 years
            Date notBefore = new Date();
            Date notAfter = Date.from(validTo.toInstant(ZoneOffset.UTC));

            // 5. Build certificate
            SubjectPublicKeyInfo spki = SubjectPublicKeyInfo.getInstance(
                    keyPair.getPublic().getEncoded());
            X509v3CertificateBuilder certBuilder = new X509v3CertificateBuilder(
                    new X500Name(rootX509.getSubjectX500Principal().getName()),
                    serialNumber,
                    notBefore,
                    notAfter,
                    subject,
                    spki
            );

            // 6. Add extensions (intermediate cert can sign other certs)
            JcaX509ExtensionUtils extUtils = new JcaX509ExtensionUtils();
            certBuilder.addExtension(Extension.basicConstraints, true,
                    new BasicConstraints(true)); // CA=true
            certBuilder.addExtension(Extension.keyUsage, true,
                    new KeyUsage(KeyUsage.keyCertSign | KeyUsage.cRLSign));
            certBuilder.addExtension(Extension.subjectKeyIdentifier, false,
                    extUtils.createSubjectKeyIdentifier(keyPair.getPublic()));
            certBuilder.addExtension(Extension.authorityKeyIdentifier, false,
                    extUtils.createAuthorityKeyIdentifier(rootX509.getPublicKey()));

            // 7. Sign with root's private key
            ContentSigner signer = new JcaContentSignerBuilder("SHA256WithRSAEncryption")
                    .setProvider("BC")
                    .build(rootPrivateKey);
            X509CertificateHolder certHolder = certBuilder.build(signer);
            X509Certificate certificate = new JcaX509CertificateConverter()
                    .setProvider("BC")
                    .getCertificate(certHolder);

            // 8. Convert to PEM
            StringWriter sw = new StringWriter();
            try (JcaPEMWriter pw = new JcaPEMWriter(sw)) {
                pw.writeObject(certificate);
            }
            String pemData = sw.toString();

            // 9. Save to keystore (owned by caUser)
            String alias = "intermediate-" + serialNumber.toString(16).substring(0, 8);
            String keystorePassword = getOrCreateUserKeystorePassword(caUser);
            String keystorePath = resolveKeystorePath(caUser.getId(), alias);
            keystoreService.saveToKeystore(
                    alias, keyPair.getPrivate(), certificate,
                    new java.security.cert.Certificate[]{certificate},
                    keystorePassword, keystorePath
            );

            // 10. Persist user keystore password if needed
            ensureUserKeystorePasswordStored(caUser, keystorePassword);

            // 11. Save to database (owned by caUser so they can see it in their chain)
            Certificate intermediaCert = Certificate.builder()
                    .serialNumber(serialNumber.toString())
                    .type(CertificateType.INTERMEDIATE)
                    .commonName("Test Intermediate CA")
                    .organization("Test Org")
                    .issuer(rootCert)
                    .owner(caUser)
                    .validFrom(validFrom)
                    .validTo(validTo)
                    .status(CertificateStatus.ACTIVE)
                    .keystoreAlias(alias)
                    .certificateData(pemData)
                    .build();

            return certificateRepository.save(intermediaCert);

        } catch (Exception e) {
            log.error("Failed to create intermediate certificate", e);
            return null;
        }
    }

    private Certificate createEndEntityCertificate(String commonName, String friendlyName,
                                                    Certificate issuer, User owner) {
        try {
            // 1. Generate keypair for end entity
            KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA", "BC");
            kpg.initialize(2048, new SecureRandom());
            KeyPair keyPair = kpg.generateKeyPair();

            // 2. Load issuer's private key from keystore
            User issuerOwner = issuer.getOwner();
            String issuerKeystorePassword = resolveKeystorePassword(issuerOwner);
            PrivateKey issuerPrivateKey = (PrivateKey) keystoreService.loadFromKeystore(
                    issuer.getKeystoreAlias(),
                    issuerKeystorePassword,
                    resolveKeystorePath(issuerOwner.getId(), issuer.getKeystoreAlias())
            ).privateKey();

            X509Certificate issuerX509 = keystoreService.loadFromKeystore(
                    issuer.getKeystoreAlias(),
                    issuerKeystorePassword,
                    resolveKeystorePath(issuerOwner.getId(), issuer.getKeystoreAlias())
            ).certificate();

            if (issuerPrivateKey == null || issuerX509 == null) {
                log.error("Failed to load issuer certificate private key or certificate");
                return null;
            }

            // 3. Build subject for end entity
            X500NameBuilder nameBuilder = new X500NameBuilder(BCStyle.INSTANCE);
            nameBuilder.addRDN(BCStyle.CN, commonName);
            nameBuilder.addRDN(BCStyle.O, "Test Org");
            nameBuilder.addRDN(BCStyle.C, "RS");
            X500Name subject = nameBuilder.build();

            // 4. Serial number and validity dates
            BigInteger serialNumber = new BigInteger(64, new SecureRandom());
            LocalDateTime validFrom = LocalDateTime.now();
            LocalDateTime validTo = validFrom.plusDays(365); // 1 year
            Date notBefore = new Date();
            Date notAfter = Date.from(validTo.toInstant(ZoneOffset.UTC));

            // 5. Build certificate
            SubjectPublicKeyInfo spki = SubjectPublicKeyInfo.getInstance(
                    keyPair.getPublic().getEncoded());
            X509v3CertificateBuilder certBuilder = new X509v3CertificateBuilder(
                    new X500Name(issuerX509.getSubjectX500Principal().getName()),
                    serialNumber,
                    notBefore,
                    notAfter,
                    subject,
                    spki
            );

            // 6. Add extensions (end entity certificate - CA=false)
            JcaX509ExtensionUtils extUtils = new JcaX509ExtensionUtils();
            certBuilder.addExtension(Extension.basicConstraints, true,
                    new BasicConstraints(false)); // CA=false
            certBuilder.addExtension(Extension.keyUsage, true,
                    new KeyUsage(KeyUsage.digitalSignature | KeyUsage.dataEncipherment));
            certBuilder.addExtension(Extension.subjectKeyIdentifier, false,
                    extUtils.createSubjectKeyIdentifier(keyPair.getPublic()));
            certBuilder.addExtension(Extension.authorityKeyIdentifier, false,
                    extUtils.createAuthorityKeyIdentifier(issuerX509.getPublicKey()));

            // 7. Sign with issuer's private key
            ContentSigner signer = new JcaContentSignerBuilder("SHA256WithRSAEncryption")
                    .setProvider("BC")
                    .build(issuerPrivateKey);
            X509CertificateHolder certHolder = certBuilder.build(signer);
            X509Certificate certificate = new JcaX509CertificateConverter()
                    .setProvider("BC")
                    .getCertificate(certHolder);

            // 8. Convert to PEM
            StringWriter sw = new StringWriter();
            try (JcaPEMWriter pw = new JcaPEMWriter(sw)) {
                pw.writeObject(certificate);
            }
            String pemData = sw.toString();

            // 9. Save to database
            Certificate endEntityCert = Certificate.builder()
                    .serialNumber(serialNumber.toString())
                    .type(CertificateType.END_ENTITY)
                    .commonName(commonName)
                    .organization("Test Org")
                    .issuer(issuer)
                    .owner(owner)
                    .validFrom(validFrom)
                    .validTo(validTo)
                    .status(CertificateStatus.ACTIVE)
                    .keystoreAlias(null) // End-entity certs not stored in keystore
                    .certificateData(pemData)
                    .build();

            return certificateRepository.save(endEntityCert);

        } catch (Exception e) {
            log.error("Failed to create end entity certificate: {}", commonName, e);
            return null;
        }
    }

    private User findOrCreateUser(String email, String firstName, String lastName, UserRole role,
                                  String organization, String plaintextPassword) {
        return userRepository.findByEmail(email).orElseGet(() -> {
            User user = User.builder()
                    .email(email)
                    .password(passwordEncoder.encode(plaintextPassword))
                    .firstName(firstName)
                    .lastName(lastName)
                    .organization(organization)
                    .role(role)
                    .isActive(true)
                    .build();
            return userRepository.save(user);
        });
    }

    private String resolveKeystorePath(Long userId, String alias) {
        return Paths.get(keystoreDir, String.valueOf(userId), alias + ".p12").toString();
    }

    private String generateKeystorePassword() {
        byte[] randomBytes = new byte[32];
        new SecureRandom().nextBytes(randomBytes);
        return java.util.Base64.getEncoder().encodeToString(randomBytes);
    }

    private String getOrCreateUserKeystorePassword(User owner) {
        if (owner.getKeystorePasswordEncrypted() == null || owner.getKeystorePasswordEncrypted().isBlank()) {
            return generateKeystorePassword();
        }
        return keyEncryptionService.decryptPassword(
                owner.getKeystorePasswordEncrypted(),
                keyEncryptionService.generateUserEncryptionKey(owner.getId())
        );
    }

    private void ensureUserKeystorePasswordStored(User owner, String plaintextPassword) {
        if (owner.getKeystorePasswordEncrypted() != null && !owner.getKeystorePasswordEncrypted().isBlank()) {
            return;
        }
        String encryptedKeystorePassword = keyEncryptionService.encryptPassword(
                plaintextPassword,
                keyEncryptionService.generateUserEncryptionKey(owner.getId())
        );
        owner.setKeystorePasswordEncrypted(encryptedKeystorePassword);
        userRepository.save(owner);
    }

    private String resolveKeystorePassword(User owner) {
        if (owner == null || owner.getKeystorePasswordEncrypted() == null) {
            throw new IllegalStateException("Keystore password is not available for user");
        }
        return keyEncryptionService.decryptPassword(
                owner.getKeystorePasswordEncrypted(),
                keyEncryptionService.generateUserEncryptionKey(owner.getId())
        );
    }

    private X509Certificate parseX509Certificate(String pemData) {
        if (pemData == null || pemData.isBlank()) {
            return null;
        }
        try {
            java.security.cert.CertificateFactory factory =
                    java.security.cert.CertificateFactory.getInstance("X.509");
            return (X509Certificate) factory.generateCertificate(
                    new java.io.ByteArrayInputStream(
                            pemData.getBytes(java.nio.charset.StandardCharsets.US_ASCII)
                    )
            );
        } catch (Exception e) {
            log.warn("Failed to parse certificate data", e);
            return null;
        }
    }
}

