package com.schedy.repository;

import com.schedy.entity.FeatureFlag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FeatureFlagRepository extends JpaRepository<FeatureFlag, String> {
    List<FeatureFlag> findByOrganisationId(String organisationId);
    Optional<FeatureFlag> findByOrganisationIdAndFeatureKey(String organisationId, String featureKey);
    void deleteByOrganisationId(String organisationId);
}
