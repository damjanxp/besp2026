package com.bsep.pki.repository;

import com.bsep.pki.model.entity.User;
import com.bsep.pki.model.entity.UserSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface UserSessionRepository extends JpaRepository<UserSession, Long> {

    Optional<UserSession> findByJti(String jti);

    List<UserSession> findByUserAndRevokedFalseAndExpiresAtAfter(User user, LocalDateTime now);
}

