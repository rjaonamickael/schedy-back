package com.schedy.repository;

import com.schedy.entity.TotpRecoveryCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TotpRecoveryCodeRepository extends JpaRepository<TotpRecoveryCode, Long> {

    List<TotpRecoveryCode> findByUserId(Long userId);

    void deleteByUserId(Long userId);

    long countByUserIdAndUsedFalse(Long userId);

    /**
     * Finds a specific unused recovery code by its SHA-256 hash.
     * Used during recovery-code login to locate and invalidate the code.
     */
    Optional<TotpRecoveryCode> findByUserIdAndCodeHashAndUsedFalse(Long userId, String codeHash);
}
