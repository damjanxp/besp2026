package com.bsep.pki.service;

import com.bsep.pki.model.dto.CreateRootCertificateRequest;
import com.bsep.pki.model.dto.CertificateDetailsResponse;
import com.bsep.pki.model.dto.CertificateResponse;
import com.bsep.pki.model.entity.Certificate;
import com.bsep.pki.model.entity.CertificateStatus;
import com.bsep.pki.model.entity.CertificateType;
import com.bsep.pki.model.entity.User;
import com.bsep.pki.model.entity.UserRole;
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

import java.io.ByteArrayInputStream;
import java.io.StringWriter;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;

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

    @Transactional(readOnly = true)
    public List<CertificateResponse> getCertificatesForUser(User user, Pageable pageable) {
        if (user.getRole() == UserRole.ADMIN) {
            Page<Certificate> page = certificateRepository.findAll(pageable);
            return page.stream().map(this::mapToResponse).collect(Collectors.toList());
        }

        if (user.getRole() == UserRole.END_ENTITY) {
            Page<Certificate> page = certificateRepository.findByOwner(user, pageable);
            return page.stream().map(this::mapToResponse).collect(Collectors.toList());
        }

        List<Certificate> sorted = certificateRepository.findAll(resolveSort(pageable));
        List<Certificate> allowed = filterCertificatesForCaUser(user, sorted);
        List<Certificate> paged = applyPage(allowed, pageable);
        return paged.stream().map(this::mapToResponse).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public CertificateDetailsResponse getCertificateDetails(Long id, User user) {
        Certificate cert = requireAccessibleCertificate(id, user);
        return mapToDetailsResponse(cert);
    }

    @Transactional(readOnly = true)
    public Certificate requireAccessibleCertificate(Long id, User user) {
        Certificate cert = certificateRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Certificate not found"));
        if (!canAccessCertificate(user, cert)) {
            throw new AccessDeniedException("Not authorized to access this certificate");
        }
        return cert;
    }

    private boolean canAccessCertificate(User user, Certificate cert) {
        if (user.getRole() == UserRole.ADMIN) {
            return true;
        }
        if (user.getRole() == UserRole.END_ENTITY) {
            return cert.getOwner() != null && cert.getOwner().getId().equals(user.getId());
        }
        if (user.getRole() == UserRole.CA_USER) {
            List<Certificate> roots = certificateRepository.findByOwnerAndType(user, CertificateType.ROOT);
            if (roots.isEmpty()) {
                return false;
            }
            Set<Long> rootIds = roots.stream().map(Certificate::getId).collect(Collectors.toSet());
            return isInCaChain(cert, rootIds);
        }
        return false;
    }

    private List<Certificate> filterCertificatesForCaUser(User user, List<Certificate> certificates) {
        List<Certificate> roots = certificateRepository.findByOwnerAndType(user, CertificateType.ROOT);
        if (roots.isEmpty()) {
            return Collections.emptyList();
        }
        Set<Long> rootIds = roots.stream().map(Certificate::getId).collect(Collectors.toSet());
        return certificates.stream()
                .filter(cert -> isInCaChain(cert, rootIds))
                .collect(Collectors.toList());
    }

    private boolean isInCaChain(Certificate cert, Set<Long> rootIds) {
        Set<Long> visited = new HashSet<>();
        Certificate current = cert;
        while (current != null && current.getId() != null && visited.add(current.getId())) {
            if (rootIds.contains(current.getId())) {
                return true;
            }
            current = current.getIssuer();
        }
        return false;
    }

    private Sort resolveSort(Pageable pageable) {
        return pageable.getSort().isUnsorted() ? Sort.by(Sort.Direction.DESC, "createdAt") : pageable.getSort();
    }

    private List<Certificate> applyPage(List<Certificate> certificates, Pageable pageable) {
        if (pageable.isUnpaged()) {
            return certificates;
        }
        int start = (int) pageable.getOffset();
        if (start >= certificates.size()) {
            return Collections.emptyList();
        }
        int end = Math.min(start + pageable.getPageSize(), certificates.size());
        return certificates.subList(start, end);
    }

    public List<CertificateResponse> getAllCertificates() {
        return certificateRepository.findAll().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    private CertificateResponse mapToResponse(Certificate cert) {
        X509Certificate x509 = parseX509Certificate(cert.getCertificateData());
        Map<String, String> subject = parseSubjectAttributes(x509);
        return CertificateResponse.builder()
                .id(cert.getId())
                .serialNumber(cert.getSerialNumber())
                .type(cert.getType().name())
                .commonName(cert.getCommonName())
                .organization(cert.getOrganization())
                .country(subject.get("C"))
                .issuerCommonName(cert.getIssuer() != null ? cert.getIssuer().getCommonName() : null)
                .validFrom(cert.getValidFrom())
                .validTo(cert.getValidTo())
                .status(cert.getStatus().name())
                .keyAlgorithm(x509 != null ? x509.getPublicKey().getAlgorithm() : null)
                .build();
    }

    private CertificateDetailsResponse mapToDetailsResponse(Certificate cert) {
        X509Certificate x509 = parseX509Certificate(cert.getCertificateData());
        Map<String, String> subject = parseSubjectAttributes(x509);
        return CertificateDetailsResponse.builder()
                .id(cert.getId())
                .serialNumber(cert.getSerialNumber())
                .serialNumberFull(x509 != null ? x509.getSerialNumber().toString() : cert.getSerialNumber())
                .type(cert.getType().name())
                .commonName(cert.getCommonName())
                .organization(cert.getOrganization())
                .organizationalUnit(subject.get("OU"))
                .country(subject.get("C"))
                .state(subject.get("ST"))
                .locality(subject.get("L"))
                .emailAddress(subject.get("EMAILADDRESS"))
                .issuerCommonName(cert.getIssuer() != null ? cert.getIssuer().getCommonName() : null)
                .validFrom(cert.getValidFrom())
                .validTo(cert.getValidTo())
                .status(cert.getStatus().name())
                .keyAlgorithm(x509 != null ? x509.getPublicKey().getAlgorithm() : null)
                .basicConstraints(x509 != null && x509.getBasicConstraints() >= 0)
                .keyUsage(resolveKeyUsage(x509))
                .build();
    }

    private X509Certificate parseX509Certificate(String pemData) {
        if (pemData == null || pemData.isBlank()) {
            return null;
        }
        try {
            CertificateFactory factory = CertificateFactory.getInstance("X.509");
            return (X509Certificate) factory.generateCertificate(
                    new ByteArrayInputStream(pemData.getBytes(StandardCharsets.US_ASCII))
            );
        } catch (Exception e) {
            log.warn("Failed to parse certificate data", e);
            return null;
        }
    }

    private Map<String, String> parseSubjectAttributes(X509Certificate x509) {
        if (x509 == null) {
            return Collections.emptyMap();
        }
        Map<String, String> attributes = new HashMap<>();
        try {
            javax.naming.ldap.LdapName ldapName = new javax.naming.ldap.LdapName(
                    x509.getSubjectX500Principal().getName(javax.security.auth.x500.X500Principal.RFC2253)
            );
            for (javax.naming.ldap.Rdn rdn : ldapName.getRdns()) {
                String type = rdn.getType().toUpperCase();
                attributes.put(type, String.valueOf(rdn.getValue()));
                if ("E".equals(type) && !attributes.containsKey("EMAILADDRESS")) {
                    attributes.put("EMAILADDRESS", String.valueOf(rdn.getValue()));
                }
            }
        } catch (Exception e) {
            log.warn("Failed to parse subject attributes", e);
        }
        return attributes;
    }

    private List<String> resolveKeyUsage(X509Certificate x509) {
        if (x509 == null) {
            return Collections.emptyList();
        }
        boolean[] usage = x509.getKeyUsage();
        if (usage == null) {
            return Collections.emptyList();
        }
        List<String> names = new ArrayList<>();
        String[] labels = new String[] {
                "digitalSignature",
                "nonRepudiation",
                "keyEncipherment",
                "dataEncipherment",
                "keyAgreement",
                "keyCertSign",
                "cRLSign",
                "encipherOnly",
                "decipherOnly"
        };
        for (int i = 0; i < usage.length && i < labels.length; i++) {
            if (usage[i]) {
                names.add(labels[i]);
            }
        }
        return names;
    }

    @Transactional(readOnly = true)
    public java.util.Optional<Certificate> getLatestActiveEndEntityCertificate(User user) {
        return certificateRepository.findFirstByOwnerAndStatusAndTypeOrderByCreatedAtDesc(
                user,
                CertificateStatus.ACTIVE,
                CertificateType.END_ENTITY
        );
    }

    @Transactional(readOnly = true)
    public List<CertificateResponse> getAvailableCaCertificates() {
        Set<CertificateType> types = Set.of(CertificateType.ROOT, CertificateType.INTERMEDIATE);
        LocalDateTime now = LocalDateTime.now();
        return certificateRepository.findByTypeInAndStatus(types, CertificateStatus.ACTIVE).stream()
                .filter(cert -> cert.getValidTo() != null && cert.getValidTo().isAfter(now))
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

}
