package com.beautyconnect.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Prestation proposee par un professionnel (coiffure, onglerie, soin...).
 * La duree est associee a chaque prestation ; pour toute specificite
 * supplementaire, le client contacte directement le professionnel
 * (cf. specifications : pas de configuration fine des creneaux).
 */
@Entity
@Table(name = "prestations")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Prestation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // @ManyToOne : plusieurs Prestation peuvent pointer vers le meme
    // ProfessionalProfile (un professionnel propose souvent plusieurs prestations).
    // optional = false : une prestation doit toujours avoir un professionnel.
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "professional_id", nullable = false)
    private ProfessionalProfile professional;

    @Column(nullable = false)
    private String name;

    @Column(length = 1000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ServiceType type;

    // BigDecimal est utilise plutot que double/float pour representer un prix :
    // un type a virgule flottante classique peut introduire des erreurs
    // d'arrondi (ex: 0.1 + 0.2 != 0.3 en double), ce qui est inacceptable pour
    // de l'argent. precision = 8, scale = 2 -> jusqu'a 8 chiffres dont 2 apres la virgule.
    @Column(nullable = false, precision = 8, scale = 2)
    private BigDecimal price;

    @Column(name = "duration_minutes", nullable = false)
    private Integer durationMinutes;

    // Permet de "retirer" une prestation du catalogue sans la supprimer de la
    // base (et donc sans casser l'historique des rendez-vous deja pris dessus).
    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;
}
