package com.beautyconnect.repository;

import com.beautyconnect.model.User;
import com.beautyconnect.model.VerificationToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface VerificationTokenRepository extends JpaRepository<VerificationToken, Long> {
    Optional<VerificationToken> findByToken(String token);
    Optional<VerificationToken> findByUser(User user);
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("SELECT t FROM VerificationToken t WHERE t.token = :token")
    Optional<VerificationToken> findByTokenForUpdate(@org.springframework.data.repository.query.Param("token") String token);
    void deleteByUser(User user);
}