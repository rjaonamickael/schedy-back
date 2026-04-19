package com.schedy.repository;

import com.schedy.entity.UserSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserSessionRepository extends JpaRepository<UserSession, Long> {

    Optional<UserSession> findByTokenHash(String tokenHash);

    /** Oldest-first so the service layer can FIFO-evict when the cap is reached. */
    List<UserSession> findByUserIdOrderByIdAsc(Long userId);

    long countByUserId(Long userId);

    /** Logout-all — used by password change/reset to kill every active session. */
    @Modifying
    @Transactional
    void deleteByUserId(Long userId);

    /** Scheduled cleanup — removes sessions whose refresh JWT has expired. */
    @Modifying
    @Transactional
    @Query("DELETE FROM UserSession s WHERE s.expiresAt < :now")
    int deleteExpiredSessions(@Param("now") Instant now);
}
