package com.schedy.repository;

import com.schedy.entity.Testimonial;
import com.schedy.entity.Testimonial.TestimonialStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
@Repository
public interface TestimonialRepository extends JpaRepository<Testimonial, String> {
    /** Public landing page — approved testimonials sorted by superadmin-set display order. */
    List<Testimonial> findByStatusOrderByDisplayOrderAsc(TestimonialStatus status);
    /** Public landing page — approved testimonials for a specific language. */
    List<Testimonial> findByStatusAndLanguageOrderByDisplayOrderAsc(TestimonialStatus status, String language);
    /** Superadmin list — most recent submissions first. */
    List<Testimonial> findAllByOrderByCreatedAtDesc();
    /** Org-scoped view — all testimonials submitted by a given organisation. */
    List<Testimonial> findByOrganisationIdOrderByCreatedAtDesc(String organisationId);

    /** V48 — R2 GC guard : vrai si au moins un temoignage snapshot reference ce logo. */
    boolean existsByLogoUrl(String logoUrl);

    /** V48 — R2 GC guard pour les photos d'auteur. */
    boolean existsByAuthorPhotoUrl(String authorPhotoUrl);
}
