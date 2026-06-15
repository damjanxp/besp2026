package com.bsep.pki.config;

import com.bsep.pki.model.entity.*;
import com.bsep.pki.repository.CertificateRepository;
import com.bsep.pki.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.math.BigInteger;
import java.security.SecureRandom;
import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements ApplicationRunner {

    private final UserRepository userRepository;
    private final CertificateRepository certificateRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(ApplicationArguments args) {
        User admin  = seedUser("admin@pki.com", "Petar",  "Admin",    null,           UserRole.ADMIN);
        User caUser = seedUser("ca@pki.com",    "Marija", "Certic",   "FTN Novi Sad", UserRole.CA_USER);
        User endEnt = seedUser("user@pki.com",  "Nikola", "Korisnik", "Test Org",     UserRole.END_ENTITY);

        seedCertificates(admin, caUser, endEnt);
    }

    private User seedUser(String email, String firstName, String lastName, String organization, UserRole role) {
        User user = userRepository.findByEmail(email).orElseGet(() -> User.builder()
                .email(email)
                .role(role)
                .isActive(true)
                .build());
        user.setFirstName(firstName);
        user.setLastName(lastName);
        user.setOrganization(organization);
        user.setRole(role);
        user.setActive(true);
        user.setPassword(passwordEncoder.encode("password"));
        userRepository.save(user);
        log.info("Seed user ready: {} ({})", email, role);
        return user;
    }

    private void seedCertificates(User admin, User caUser, User endEnt) {
        // Samo ako nema nijednog sertifikata — ne dupliramo na svakom restartu
        if (certificateRepository.count() > 0) {
            log.info("Certificates already seeded, skipping.");
            return;
        }

        // ROOT — vlasnik admin
        Certificate root = certificateRepository.save(Certificate.builder()
                .serialNumber(randomSerial())
                .type(CertificateType.ROOT)
                .commonName("Test Root CA")
                .organization("PKI Test")
                .issuer(null)
                .owner(admin)
                .validFrom(LocalDateTime.now())
                .validTo(LocalDateTime.now().plusYears(10))
                .status(CertificateStatus.ACTIVE)
                .keystoreAlias("root-seed")
                .certificateData("(seed — no real PEM)")
                .build());

        // INTERMEDIATE — izdat od ROOT, vlasnik ca korisnik
        Certificate intermediate = certificateRepository.save(Certificate.builder()
                .serialNumber(randomSerial())
                .type(CertificateType.INTERMEDIATE)
                .commonName("Test Intermediate CA")
                .organization("FTN Novi Sad")
                .issuer(root)
                .owner(caUser)
                .validFrom(LocalDateTime.now())
                .validTo(LocalDateTime.now().plusYears(5))
                .status(CertificateStatus.ACTIVE)
                .keystoreAlias("intermediate-seed")
                .certificateData("(seed — no real PEM)")
                .build());

        // END_ENTITY — izdat od INTERMEDIATE, vlasnik end entity korisnik
        certificateRepository.save(Certificate.builder()
                .serialNumber(randomSerial())
                .type(CertificateType.END_ENTITY)
                .commonName("Nikola Korisnik")
                .organization("Test Org")
                .issuer(intermediate)
                .owner(endEnt)
                .validFrom(LocalDateTime.now())
                .validTo(LocalDateTime.now().plusYears(1))
                .status(CertificateStatus.ACTIVE)
                .keystoreAlias("ee-seed")
                .certificateData("(seed — no real PEM)")
                .build());

        log.info("Seed certificates created: ROOT → INTERMEDIATE → END_ENTITY");
    }

    private String randomSerial() {
        return new BigInteger(64, new SecureRandom()).toString();
    }
}
