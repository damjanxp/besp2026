package com.bsep.pki.repository;

import com.bsep.pki.model.entity.User;
import com.bsep.pki.model.entity.UserSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserSessionRepository extends JpaRepository<UserSession, Long> {

    Optional<UserSession> findByJti(String jti);

    List<UserSession> findByUserAndRevokedFalseOrderByLastActivityAtDesc(User user);

    List<UserSession> findByExpiresAtBefore(LocalDateTime time);

    List<UserSession> findByUserAndRevokedFalseAndExpiresAtAfter(User user, LocalDateTime now);
}
