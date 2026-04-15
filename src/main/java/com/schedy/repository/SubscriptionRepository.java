package com.schedy.repository;

import com.schedy.entity.Subscription;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
@Repository
public interface SubscriptionRepository extends JpaRepository<Subscription, String> {
    Optional<Subscription> findByOrganisationId(String organisationId);
    Optional<Subscription> findByStripeSubscriptionId(String stripeSubscriptionId);
    List<Subscription> findByOrganisationIdIn(Collection<String> organisationIds);
    List<Subscription> findByStatus(Subscription.SubscriptionStatus status);
    List<Subscription> findByPlanTier(Subscription.PlanTier planTier);
    @Modifying @Transactional
    void deleteByOrganisationId(String organisationId);
    @Query("SELECT s.planTier, COUNT(s) FROM Subscription s GROUP BY s.planTier")
    List<Object[]> countByPlanTierGrouped();
    @Query("SELECT s.status, COUNT(s) FROM Subscription s GROUP BY s.status")
    List<Object[]> countByStatusGrouped();
}
