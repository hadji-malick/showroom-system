package com.powertech.stock.repositories;

import com.powertech.stock.entities.ZoneLivraison;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ZoneLivraisonRepository extends JpaRepository<ZoneLivraison, Long> {
    List<ZoneLivraison> findByActifTrue();
    List<ZoneLivraison> findByNomIgnoreCase(String nom);
}
