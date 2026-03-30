package com.schedy.repository;

import com.schedy.entity.Subscription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface SubscriptionRepository extends JpaRepository<Subscription, String> {
    Optional<Subscription> findByOrganisationId(String organisationId);
    List<Subscription> findByOrganisationIdIn(Collection<String> organisationIds);
    List<Subscription> findByStatus(Subscription.SubscriptionStatus status);
    List<Subscription> findByPlanTier(Subscription.PlanTier planTier);
    void deleteByOrganisationId(String organisationId);
}
