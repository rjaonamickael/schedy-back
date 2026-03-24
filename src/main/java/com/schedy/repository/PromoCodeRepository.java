package com.schedy.repository;

import com.schedy.entity.PromoCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PromoCodeRepository extends JpaRepository<PromoCode, String> {
    Optional<PromoCode> findByCode(String code);
    List<PromoCode> findByActiveTrue();
    boolean existsByCode(String code);
}
