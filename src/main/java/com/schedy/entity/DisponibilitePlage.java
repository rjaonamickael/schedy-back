package com.schedy.entity;

import jakarta.persistence.Embeddable;
import lombok.*;

@Embeddable
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class DisponibilitePlage {

    private int jour;
    private double heureDebut;
    private double heureFin;
}
