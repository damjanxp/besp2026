package com.bsep.pki.repository;

import com.bsep.pki.model.entity.PasswordEntry;
import com.bsep.pki.model.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PasswordEntryRepository extends JpaRepository<PasswordEntry, Long> {
    List<PasswordEntry> findByOwner(User owner);
}

