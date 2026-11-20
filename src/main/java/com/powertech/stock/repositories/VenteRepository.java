package com.powertech.stock.repositories;

import com.powertech.stock.entities.Vente;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDateTime;
import java.util.List;

public interface VenteRepository extends JpaRepository<Vente, Long> {

    // Récupérer toutes les ventes d'un produit
    List<Vente> findByProduitIdOrderByDateVenteDesc(Long produitId);

    // Récupérer les ventes sur une période
    List<Vente> findByDateVenteBetween(LocalDateTime debut, LocalDateTime fin);

    // Récupérer les dernières ventes
    // ✅ AJOUTER : toutes les ventes triées par date décroissante
    List<Vente> findAllByOrderByDateVenteDesc();
    List<Vente> findAll();

}