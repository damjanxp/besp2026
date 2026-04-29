package com.bsep.pki.service;

import com.bsep.pki.model.dto.PasswordEntryResponse;
import com.bsep.pki.model.dto.SavePasswordRequest;
import com.bsep.pki.model.dto.SharePasswordRequest;
import com.bsep.pki.model.entity.PasswordEntry;
import com.bsep.pki.model.entity.PasswordShare;
import com.bsep.pki.model.entity.User;
import com.bsep.pki.repository.PasswordEntryRepository;
import com.bsep.pki.repository.PasswordShareRepository;
import com.bsep.pki.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class PasswordManagerService {

    private final PasswordEntryRepository passwordEntryRepository;
    private final PasswordShareRepository passwordShareRepository;
    private final UserRepository userRepository;

    /**
     * Save a new encrypted password entry for the current user.
     * The password is encrypted on the frontend using the user's public key (RSA-OAEP).
     */
    @Transactional
    public PasswordEntryResponse savePassword(SavePasswordRequest request, User currentUser) {
        // TODO (KT2): Implement full save logic when Web Crypto API is integrated on frontend
        PasswordEntry entry = PasswordEntry.builder()
                .siteName(request.getSiteName())
                .username(request.getUsername())
                .owner(currentUser)
                .createdBy(currentUser)
                .build();

        entry = passwordEntryRepository.save(entry);

        // Save encrypted password as a share for the owner
        PasswordShare share = PasswordShare.builder()
                .entry(entry)
                .user(currentUser)
                .encryptedPassword(request.getEncryptedPassword())
                .sharedBy(currentUser)
                .build();

        passwordShareRepository.save(share);

        log.info("Password entry saved for user: {}, site: {}", currentUser.getEmail(), request.getSiteName());
        return mapToResponse(entry, share.getEncryptedPassword());
    }

    /**
     * Get all password entries for the current user (owned + shared).
     * The encrypted password for each entry corresponds to the user's own share.
     */
    public List<PasswordEntryResponse> getMyPasswords(User currentUser) {
        // TODO (KT2): Full implementation with Web Crypto decryption on frontend
        List<PasswordShare> shares = passwordShareRepository.findByUser(currentUser);
        return shares.stream()
                .map(share -> mapToResponse(share.getEntry(), share.getEncryptedPassword()))
                .collect(Collectors.toList());
    }

    /**
     * Share a password entry with another user.
     * The frontend must re-encrypt the password with the target user's public key.
     */
    @Transactional
    public void sharePassword(Long entryId, SharePasswordRequest request, User currentUser) {
        // TODO (KT2): Full implementation including public key retrieval
        PasswordEntry entry = passwordEntryRepository.findById(entryId)
                .orElseThrow(() -> new RuntimeException("Password entry not found"));

        User targetUser = userRepository.findById(request.getTargetUserId())
                .orElseThrow(() -> new RuntimeException("Target user not found"));

        // Check current user has access to this entry
        passwordShareRepository.findByEntryAndUser(entry, currentUser)
                .orElseThrow(() -> new RuntimeException("Access denied to this password entry"));

        PasswordShare share = PasswordShare.builder()
                .entry(entry)
                .user(targetUser)
                .encryptedPassword(request.getEncryptedPassword())
                .sharedBy(currentUser)
                .build();

        passwordShareRepository.save(share);
        log.info("Password entry {} shared by {} with {}", entryId, currentUser.getEmail(), targetUser.getEmail());
    }

    /**
     * Delete a password entry (owner only).
     */
    @Transactional
    public void deletePassword(Long entryId, User currentUser) {
        PasswordEntry entry = passwordEntryRepository.findById(entryId)
                .orElseThrow(() -> new RuntimeException("Password entry not found"));

        if (!entry.getOwner().getId().equals(currentUser.getId())) {
            throw new RuntimeException("Only the owner can delete a password entry");
        }

        // Delete all shares first
        passwordShareRepository.findByEntry(entry).forEach(passwordShareRepository::delete);
        passwordEntryRepository.delete(entry);
        log.info("Password entry {} deleted by {}", entryId, currentUser.getEmail());
    }

    private PasswordEntryResponse mapToResponse(PasswordEntry entry, String encryptedPassword) {
        return PasswordEntryResponse.builder()
                .id(entry.getId())
                .siteName(entry.getSiteName())
                .username(entry.getUsername())
                .ownerId(entry.getOwner().getId())
                .ownerEmail(entry.getOwner().getEmail())
                .encryptedPassword(encryptedPassword)
                .createdAt(entry.getCreatedAt())
                .updatedAt(entry.getUpdatedAt())
                .build();
    }
}

