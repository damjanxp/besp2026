package com.bsep.pki.service;

import com.bsep.pki.model.dto.TemplateRequest;
import com.bsep.pki.model.dto.TemplateResponse;
import com.bsep.pki.model.entity.*;
import com.bsep.pki.repository.CertificateRepository;
import com.bsep.pki.repository.CertificateTemplateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TemplateService {

    private final CertificateTemplateRepository templateRepository;
    private final CertificateRepository certificateRepository;

    @Transactional
    public TemplateResponse createTemplate(TemplateRequest request, User caller) {
        Certificate caIssuer = certificateRepository.findById(request.getCaIssuerId())
                .orElseThrow(() -> new RuntimeException("CA certificate not found"));

        if (caIssuer.getType() == CertificateType.END_ENTITY) {
            throw new RuntimeException("Templates can only be created for CA certificates (ROOT or INTERMEDIATE)");
        }
        if (caIssuer.getStatus() != CertificateStatus.ACTIVE) {
            throw new RuntimeException("CA certificate is not active");
        }

        // CA_USER može da napravi šablon samo za svoja CA
        if (caller.getRole() != UserRole.ADMIN) {
            if (caIssuer.getOwner() == null || !caIssuer.getOwner().getId().equals(caller.getId())) {
                throw new RuntimeException("Access denied: you can only create templates for your own CA certificates");
            }
        }

        CertificateTemplate template = CertificateTemplate.builder()
                .name(request.getName())
                .caIssuer(caIssuer)
                .owner(caller)
                .cnRegex(request.getCnRegex())
                .sanRegex(request.getSanRegex())
                .maxTtlDays(request.getMaxTtlDays())
                .defaultKeyUsage(request.getDefaultKeyUsage())
                .defaultExtendedKeyUsage(request.getDefaultExtendedKeyUsage())
                .build();

        return mapToResponse(templateRepository.save(template));
    }

    public List<TemplateResponse> getMyTemplates(User caller) {
        // ADMIN vidi sve šablone, CA_USER samo svoje
        List<CertificateTemplate> templates = caller.getRole() == UserRole.ADMIN
                ? templateRepository.findAll()
                : templateRepository.findByOwner(caller);

        return templates.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    public List<TemplateResponse> getTemplatesForCa(Long caId) {
        Certificate ca = certificateRepository.findById(caId)
                .orElseThrow(() -> new RuntimeException("CA certificate not found"));
        return templateRepository.findByCaIssuer(ca).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    public TemplateResponse getTemplate(Long id, User caller) {
        CertificateTemplate template = templateRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Template not found"));
        if (caller.getRole() != UserRole.ADMIN && !template.getOwner().getId().equals(caller.getId())) {
            throw new RuntimeException("Access denied");
        }
        return mapToResponse(template);
    }

    @Transactional
    public TemplateResponse updateTemplate(Long id, TemplateRequest request, User caller) {
        CertificateTemplate template = templateRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Template not found"));
        if (caller.getRole() != UserRole.ADMIN && !template.getOwner().getId().equals(caller.getId())) {
            throw new RuntimeException("Access denied");
        }
        // caIssuer se ne mijenja — template ostaje vezan za isti CA
        template.setName(request.getName());
        template.setCnRegex(request.getCnRegex());
        template.setSanRegex(request.getSanRegex());
        template.setMaxTtlDays(request.getMaxTtlDays());
        template.setDefaultKeyUsage(request.getDefaultKeyUsage());
        template.setDefaultExtendedKeyUsage(request.getDefaultExtendedKeyUsage());
        return mapToResponse(templateRepository.save(template));
    }

    @Transactional
    public void deleteTemplate(Long id, User caller) {
        CertificateTemplate template = templateRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Template not found"));
        if (caller.getRole() != UserRole.ADMIN && !template.getOwner().getId().equals(caller.getId())) {
            throw new RuntimeException("Access denied");
        }
        templateRepository.delete(template);
    }

    private TemplateResponse mapToResponse(CertificateTemplate t) {
        return TemplateResponse.builder()
                .id(t.getId())
                .name(t.getName())
                .caIssuerId(t.getCaIssuer().getId())
                .caIssuerCommonName(t.getCaIssuer().getCommonName())
                .ownerEmail(t.getOwner().getEmail())
                .cnRegex(t.getCnRegex())
                .sanRegex(t.getSanRegex())
                .maxTtlDays(t.getMaxTtlDays())
                .defaultKeyUsage(t.getDefaultKeyUsage())
                .defaultExtendedKeyUsage(t.getDefaultExtendedKeyUsage())
                .createdAt(t.getCreatedAt())
                .build();
    }
}
