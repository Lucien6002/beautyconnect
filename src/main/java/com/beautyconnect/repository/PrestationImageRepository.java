package com.beautyconnect.repository;

import com.beautyconnect.model.Prestation;
import com.beautyconnect.model.PrestationImage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PrestationImageRepository extends JpaRepository<PrestationImage, Long> {
    List<PrestationImage> findByPrestationOrderByDisplayOrderAsc(Prestation prestation);
    void deleteByPrestation(Prestation prestation);
    long countByPrestation(Prestation prestation);
}
