package com.bsep.pki.controller;

import com.bsep.pki.model.dto.TemplateRequest;
import com.bsep.pki.model.dto.TemplateResponse;
import com.bsep.pki.model.entity.User;
import com.bsep.pki.repository.UserRepository;
import com.bsep.pki.service.TemplateService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/templates")
@RequiredArgsConstructor
public class TemplateController {

    private final TemplateService templateService;
    private final UserRepository userRepository;

    @PostMapping
    @PreAuthorize("hasAnyAuthority('ADMIN', 'CA_USER')")
    public ResponseEntity<TemplateResponse> createTemplate(
            @Valid @RequestBody TemplateRequest request,
            Authentication authentication) {
        return ResponseEntity.ok(templateService.createTemplate(request, resolveUser(authentication)));
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('ADMIN', 'CA_USER')")
    public ResponseEntity<List<TemplateResponse>> getMyTemplates(Authentication authentication) {
        return ResponseEntity.ok(templateService.getMyTemplates(resolveUser(authentication)));
    }

    @GetMapping("/by-ca/{caId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<TemplateResponse>> getTemplatesForCa(@PathVariable Long caId) {
        return ResponseEntity.ok(templateService.getTemplatesForCa(caId));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'CA_USER')")
    public ResponseEntity<TemplateResponse> getTemplate(
            @PathVariable Long id,
            Authentication authentication) {
        return ResponseEntity.ok(templateService.getTemplate(id, resolveUser(authentication)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'CA_USER')")
    public ResponseEntity<TemplateResponse> updateTemplate(
            @PathVariable Long id,
            @Valid @RequestBody TemplateRequest request,
            Authentication authentication) {
        return ResponseEntity.ok(templateService.updateTemplate(id, request, resolveUser(authentication)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'CA_USER')")
    public ResponseEntity<Void> deleteTemplate(
            @PathVariable Long id,
            Authentication authentication) {
        templateService.deleteTemplate(id, resolveUser(authentication));
        return ResponseEntity.noContent().build();
    }

    private User resolveUser(Authentication auth) {
        return userRepository.findByEmail(auth.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));
    }
}
