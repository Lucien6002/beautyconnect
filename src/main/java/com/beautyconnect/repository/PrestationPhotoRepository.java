package com.beautyconnect.repository;

import com.beautyconnect.model.Prestation;
import com.beautyconnect.model.PrestationPhoto;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PrestationPhotoRepository extends JpaRepository<PrestationPhoto, Long> {

    List<PrestationPhoto> findByPrestationOrderByDisplayOrderAsc(Prestation prestation);

    long countByPrestation(Prestation prestation);

    Optional<PrestationPhoto> findByPrestationAndDisplayOrder(Prestation prestation, Integer displayOrder);
}
