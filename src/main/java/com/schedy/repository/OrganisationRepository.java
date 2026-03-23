package com.schedy.repository;

import com.schedy.entity.Organisation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface OrganisationRepository extends JpaRepository<Organisation, String> {
    Optional<Organisation> findByDomaine(String domaine);
    Optional<Organisation> findByNom(String nom);
    boolean existsByNom(String nom);
}
