package com.powertech.stock.repositories;

import com.powertech.stock.entities.RapportActivite;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.util.List;

public interface RapportActiviteRepository extends JpaRepository<RapportActivite, Long> {
    List<RapportActivite> findByOrderByDateDesc();
    List<RapportActivite> findByUtilisateurNomAndDate(String nom, LocalDate date);
}