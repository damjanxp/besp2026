package com.bsep.pki.repository;

import com.bsep.pki.model.entity.Certificate;
import com.bsep.pki.model.entity.CertificateTemplate;
import com.bsep.pki.model.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface CertificateTemplateRepository extends JpaRepository<CertificateTemplate, Long> {
    List<CertificateTemplate> findByCaIssuer(Certificate caIssuer);
    List<CertificateTemplate> findByOwner(User owner);
}
