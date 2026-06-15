package com.bsep.pki.repository;

import com.bsep.pki.model.entity.Certificate;
import com.bsep.pki.model.entity.CertificateStatus;
import com.bsep.pki.model.entity.CertificateType;
import com.bsep.pki.model.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CertificateRepository extends JpaRepository<Certificate, Long> {

    Optional<Certificate> findBySerialNumber(String serialNumber);

    List<Certificate> findByOwner(User owner);

    List<Certificate> findByType(CertificateType type);

    List<Certificate> findByStatus(CertificateStatus status);

    List<Certificate> findByIssuer(Certificate issuer);
}

