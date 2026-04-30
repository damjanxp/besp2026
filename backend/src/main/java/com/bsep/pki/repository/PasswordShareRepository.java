package com.bsep.pki.repository;

import java.util.Optional;
import java.util.List;

import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.JpaRepository;
import com.bsep.pki.model.entity.User;
import com.bsep.pki.model.entity.PasswordShare;
import com.bsep.pki.model.entity.PasswordEntry;

@Repository
public interface PasswordShareRepository extends JpaRepository<PasswordShare, Long> {
    List<PasswordShare> findByEntry(PasswordEntry entry);
    Optional<PasswordShare> findByEntryAndUser(PasswordEntry entry, User user);
    List<PasswordShare> findByUser(User user);
}
