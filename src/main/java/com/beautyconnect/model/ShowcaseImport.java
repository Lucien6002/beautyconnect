package com.beautyconnect.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/** Mémorise un import de présentation, même si son élément est ensuite renommé ou supprimé. */
@Entity
@Table(name = "showcase_imports")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class ShowcaseImport {
    @Id
    @Column(name = "catalog_key", length = 160)
    private String catalogKey;

    @Column(nullable = false)
    private Long targetId;

    @Column(nullable = false)
    private LocalDateTime importedAt;
}
