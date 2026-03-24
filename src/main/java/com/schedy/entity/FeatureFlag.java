package com.schedy.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "feature_flag", indexes = {
    @Index(name = "idx_feature_flag_org", columnList = "organisation_id")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class FeatureFlag {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(name = "organisation_id", nullable = false)
    private String organisationId;

    @Column(name = "feature_key", nullable = false, length = 100)
    private String featureKey;

    @Column(nullable = false)
    @Builder.Default
    private boolean enabled = true;
}
