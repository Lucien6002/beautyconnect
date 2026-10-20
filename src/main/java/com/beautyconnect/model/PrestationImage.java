package com.beautyconnect.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Image associee a une prestation specifique.
 * Permet au professionnel d'illustrer chaque prestation avec plusieurs photos.
 */
@Entity
@Table(name = "prestation_images")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PrestationImage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "prestation_id", nullable = false)
    private Prestation prestation;

    @Column(nullable = false)
    private String publicId; // ID Cloudinary pour suppression

    @Column(nullable = false)
    private String secureUrl; // URL HTTPS publique

    @Column(nullable = false)
    private Integer displayOrder;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
