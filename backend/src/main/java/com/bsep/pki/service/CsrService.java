package com.bsep.pki.service;

import com.bsep.pki.exception.CsrException;
import com.bsep.pki.model.dto.CsrInfoDto;
import com.bsep.pki.model.entity.Certificate;
import com.bsep.pki.model.entity.CertificateStatus;
import com.bsep.pki.model.entity.CertificateType;
import com.bsep.pki.model.entity.User;
import com.bsep.pki.repository.CertificateRepository;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers;
import org.bouncycastle.asn1.x500.RDN;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x500.style.BCStyle;
import org.bouncycastle.asn1.x509.AlgorithmIdentifier;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.KeyUsage;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509ExtensionUtils;
import org.bouncycastle.crypto.params.RSAKeyParameters;
import org.bouncycastle.crypto.util.PublicKeyFactory;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.ContentVerifierProvider;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.operator.jcajce.JcaContentVerifierProviderBuilder;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;
import org.bouncycastle.pkcs.PKCS10CertificationRequest;
import org.bouncycastle.util.io.pem.PemObject;
import org.bouncycastle.util.io.pem.PemWriter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.StringReader;
import java.io.StringWriter;
import java.math.BigInteger;
import java.nio.file.Paths;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Date;

@Service
@Slf4j
public class CsrService {

    private static final long MAX_SIZE = 10 * 1024; // 10 KB

    public void validateCsrFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new CsrException("CSR fajl je obavezan i ne smije biti prazan.");
        }
        String filename = file.getOriginalFilename();
        if (filename == null || (!filename.toLowerCase().endsWith(".pem") && !filename.toLowerCase().endsWith(".csr"))) {
            throw new CsrException("Fajl mora imati .pem ili .csr ekstenziju.");
        }
        if (file.getSize() > MAX_SIZE) {
            throw new CsrException("Fajl je prevelik. Maksimalna veličina je 10KB.");
        }
    }

    public CsrInfoDto parseCsr(byte[] csrBytes) {
        PKCS10CertificationRequest csr = readCsr(csrBytes);

        X500Name subject = csr.getSubject();

        String cn = getRdn(subject, BCStyle.CN);
        String o  = getRdn(subject, BCStyle.O);
        String ou = getRdn(subject, BCStyle.OU);
        String c  = getRdn(subject, BCStyle.C);
        String st = getRdn(subject, BCStyle.ST);
        String l  = getRdn(subject, BCStyle.L);
        String e  = getRdn(subject, BCStyle.E);

        SubjectPublicKeyInfo spki = csr.getSubjectPublicKeyInfo();
        AlgorithmIdentifier algId = spki.getAlgorithm();
        String keyAlgorithm = resolveKeyAlgorithm(algId.getAlgorithm());
        int keySize = resolveKeySize(spki, keyAlgorithm);

        String sigAlg = csr.getSignatureAlgorithm().getAlgorithm().getId();
        sigAlg = resolveSignatureAlgorithm(sigAlg);

        boolean signatureValid = false;
        try {
            ContentVerifierProvider verifier = new JcaContentVerifierProviderBuilder()
                    .setProvider("BC")
                    .build(spki);
            signatureValid = csr.isSignatureValid(verifier);
        } catch (Exception ex) {
            log.warn("Could not verify CSR signature: {}", ex.getMessage());
        }

        String rawPem = toPem(csrBytes, csr);

        return CsrInfoDto.builder()
                .commonName(cn)
                .organization(o)
                .organizationalUnit(ou)
                .country(c)
                .state(st)
                .locality(l)
                .email(e)
                .publicKeyAlgorithm(keyAlgorithm)
                .publicKeySize(keySize)
                .signatureAlgorithm(sigAlg)
                .isSignatureValid(signatureValid)
                .rawPem(rawPem)
                .build();
    }

    private PKCS10CertificationRequest readCsr(byte[] csrBytes) {
        String content = new String(csrBytes).trim();
        try {
            if (content.startsWith("-----")) {
                // PEM format
                try (PEMParser parser = new PEMParser(new StringReader(content))) {
                    Object obj = parser.readObject();
                    if (obj instanceof PKCS10CertificationRequest) {
                        return (PKCS10CertificationRequest) obj;
                    }
                    throw new CsrException("PEM fajl ne sadrži validan CSR.");
                }
            } else {
                // DER format
                return new PKCS10CertificationRequest(csrBytes);
            }
        } catch (CsrException ce) {
            throw ce;
        } catch (Exception ex) {
            throw new CsrException("Greška pri parsiranju CSR fajla: " + ex.getMessage(), ex);
        }
    }

    private String getRdn(X500Name name, ASN1ObjectIdentifier oid) {
        RDN[] rdns = name.getRDNs(oid);
        if (rdns == null || rdns.length == 0) return null;
        return rdns[0].getFirst().getValue().toString();
    }

    private String resolveKeyAlgorithm(ASN1ObjectIdentifier oid) {
        String id = oid.getId();
        // RSA
        if (PKCSObjectIdentifiers.rsaEncryption.getId().equals(id)) return "RSA";
        // EC
        if ("1.2.840.10045.2.1".equals(id)) return "EC";
        return id;
    }

    private int resolveKeySize(SubjectPublicKeyInfo spki, String keyAlgorithm) {
        try {
            if ("RSA".equals(keyAlgorithm)) {
                RSAKeyParameters params = (RSAKeyParameters) PublicKeyFactory.createKey(spki);
                return params.getModulus().bitLength();
            }
        } catch (Exception ex) {
            log.warn("Could not determine key size: {}", ex.getMessage());
        }
        return 0;
    }

    private String resolveSignatureAlgorithm(String oid) {
        return switch (oid) {
            case "1.2.840.113549.1.1.11" -> "SHA256withRSA";
            case "1.2.840.113549.1.1.12" -> "SHA384withRSA";
            case "1.2.840.113549.1.1.13" -> "SHA512withRSA";
            case "1.2.840.113549.1.1.5"  -> "SHA1withRSA";
            case "1.2.840.10045.4.3.2"   -> "SHA256withECDSA";
            case "1.2.840.10045.4.3.3"   -> "SHA384withECDSA";
            case "1.2.840.10045.4.3.4"   -> "SHA512withECDSA";
            default -> oid;
        };
    }

    private String toPem(byte[] originalBytes, PKCS10CertificationRequest csr) {
        String content = new String(originalBytes).trim();
        if (content.startsWith("-----")) {
            return content;
        }
        // DER → convert to PEM
        try {
            StringWriter sw = new StringWriter();
            try (PemWriter pw = new PemWriter(sw)) {
                pw.writeObject(new PemObject("CERTIFICATE REQUEST", csr.getEncoded()));
            }
            return sw.toString();
        } catch (Exception ex) {
            log.warn("Could not convert DER to PEM: {}", ex.getMessage());
            return null;
        }
    }

    @Autowired
    private CertificateRepository certificateRepository;

    @Autowired
    private KeystoreService keystoreService;

    @Transactional
    public Certificate signCsr(byte[] csrBytes, Long caId, int validDays, User requester) {
        try {
            // 1. Load the CA certificate
            Certificate caCert = certificateRepository.findById(caId)
                    .orElseThrow(() -> new CsrException("CA sertifikat nije pronađen"));

            if (caCert.getStatus() != CertificateStatus.ACTIVE) {
                throw new CsrException("CA sertifikat nije aktivan");
            }

            User caOwner = caCert.getOwner();
            if (caOwner == null) {
                throw new CsrException("CA sertifikat nema vlasnika");
            }

            // 2. Parse CSR
            PKCS10CertificationRequest csr = readCsr(csrBytes);
            X500Name subject = csr.getSubject();
            SubjectPublicKeyInfo subjectPublicKeyInfo = csr.getSubjectPublicKeyInfo();

            // 3. Generate serial number and validity period
            BigInteger serialNumber = new BigInteger(64, new SecureRandom());
            LocalDateTime validFrom = LocalDateTime.now();
            LocalDateTime validTo = validFrom.plusDays(validDays);
            Date notBefore = new Date();
            Date notAfter = Date.from(validTo.toInstant(ZoneOffset.UTC));

            // 4. Build keystore path and load CA's private key
            String keystorePath = Paths.get(System.getProperty("user.home"), ".keystores",
                    String.valueOf(caOwner.getId()), caCert.getKeystoreAlias() + ".p12").toString();

            KeystoreService.KeystoreEntry keystoreEntry = keystoreService.loadFromKeystore(
                    caCert.getKeystoreAlias(),
                    caOwner.getKeystorePasswordEncrypted(),
                    keystorePath
            );

            PrivateKey caPrivateKey = keystoreEntry.privateKey();
            X509Certificate caCertX509 = keystoreEntry.certificate();

            if (caPrivateKey == null || caCertX509 == null) {
                throw new CsrException("Ne mogu učitati privatni ključ ili sertifikat CA");
            }

            // 5. Build and sign the new certificate
            X509v3CertificateBuilder certBuilder = new X509v3CertificateBuilder(
                    new X500Name(caCertX509.getSubjectX500Principal().getName()),
                    serialNumber,
                    notBefore,
                    notAfter,
                    subject,
                    subjectPublicKeyInfo
            );

            JcaX509ExtensionUtils extUtils = new JcaX509ExtensionUtils();

            // End-entity certificate constraints
            certBuilder.addExtension(Extension.basicConstraints, true,
                    new BasicConstraints(false));
            certBuilder.addExtension(Extension.keyUsage, true,
                    new KeyUsage(KeyUsage.digitalSignature | KeyUsage.dataEncipherment));

            certBuilder.addExtension(Extension.subjectKeyIdentifier, false,
                    extUtils.createSubjectKeyIdentifier(subjectPublicKeyInfo));
            certBuilder.addExtension(Extension.authorityKeyIdentifier, false,
                    extUtils.createAuthorityKeyIdentifier(caCertX509.getPublicKey()));

            ContentSigner signer = new JcaContentSignerBuilder("SHA256WithRSAEncryption")
                    .setProvider("BC")
                    .build(caPrivateKey);

            X509CertificateHolder certHolder = certBuilder.build(signer);
            X509Certificate signedCert = new JcaX509CertificateConverter()
                    .setProvider("BC")
                    .getCertificate(certHolder);

            // 6. Convert to PEM
            StringWriter sw = new StringWriter();
            try (JcaPEMWriter pw = new JcaPEMWriter(sw)) {
                pw.writeObject(signedCert);
            }
            String pemData = sw.toString();

            // 7. Extract CN from subject for display
            String commonName = getRdn(subject, BCStyle.CN);
            String organization = getRdn(subject, BCStyle.O);

            // 8. Save to database
            Certificate newCert = Certificate.builder()
                    .serialNumber(serialNumber.toString())
                    .type(CertificateType.END_ENTITY)
                    .commonName(commonName != null ? commonName : "Unknown")
                    .organization(organization != null ? organization : "Unknown")
                    .issuer(caCert)
                    .owner(requester)
                    .validFrom(validFrom)
                    .validTo(validTo)
                    .status(CertificateStatus.ACTIVE)
                    .keystoreAlias(null) // End-entity certs aren't stored in keystores
                    .certificateData(pemData)
                    .build();

            return certificateRepository.save(newCert);

        } catch (CsrException ce) {
            throw ce;
        } catch (Exception ex) {
            log.error("Error signing CSR", ex);
            throw new CsrException("Greška pri potpisivanju CSR: " + ex.getMessage(), ex);
        }
    }
}
