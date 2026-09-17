package com.beautyconnect.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Creneau de disponibilite propose par un professionnel.
 * Reserve (available = false) des qu'un rendez-vous y est associe.
 */
@Entity
@Table(name = "time_slots")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TimeSlot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "professional_id", nullable = false)
    private ProfessionalProfile professional;

    @Column(name = "start_date_time", nullable = false)
    private LocalDateTime startDateTime;

    // true tant que personne n'a pris rendez-vous sur ce creneau. Passe a
    // false des qu'un Appointment lui est associe (voir AppointmentService),
    // ce qui empeche un double-booking du meme creneau par deux clients.
    @Column(nullable = false)
    @Builder.Default
    private boolean available = true;
}
