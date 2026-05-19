package com.bsep.pki.service;

import com.bsep.pki.exception.CsrException;
import com.bsep.pki.model.dto.CsrInfoDto;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers;
import org.bouncycastle.asn1.x500.RDN;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x500.style.BCStyle;
import org.bouncycastle.asn1.x509.AlgorithmIdentifier;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.crypto.params.RSAKeyParameters;
import org.bouncycastle.crypto.util.PublicKeyFactory;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.operator.ContentVerifierProvider;
import org.bouncycastle.operator.jcajce.JcaContentVerifierProviderBuilder;
import org.bouncycastle.pkcs.PKCS10CertificationRequest;
import org.bouncycastle.util.io.pem.PemWriter;
import org.bouncycastle.util.io.pem.PemObject;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.StringReader;
import java.io.StringWriter;

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
}

