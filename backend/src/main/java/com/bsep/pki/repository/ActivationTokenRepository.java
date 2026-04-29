package com.bsep.pki.repository;

import com.bsep.pki.model.entity.ActivationToken;
import com.bsep.pki.model.entity.TokenType;
import com.bsep.pki.model.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

public interface ActivationTokenRepository extends JpaRepository<ActivationToken, Long> {

    Optional<ActivationToken> findByToken(String token);

    @Transactional
    void deleteByUserAndType(User user, TokenType type);
}

