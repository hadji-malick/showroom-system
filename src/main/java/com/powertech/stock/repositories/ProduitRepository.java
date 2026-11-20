package com.powertech.stock.repositories;

import com.powertech.stock.entities.Produit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;

public interface ProduitRepository extends JpaRepository<Produit, Long> {

    // Trouver un produit par sa référence
    Produit findByReference(String reference);

    // Trouver les produits dont le stock est bas
    List<Produit> findByQuantiteStockLessThan(Integer seuil);

    // Rechercher des produits par nom (partiel, sans casse)
    List<Produit> findByNomContainingIgnoreCase(String nom);

    // Compter les produits en stock bas
    @Query("SELECT COUNT(p) FROM Produit p WHERE p.quantiteStock <= p.seuilAlerte")
    long countProduitsEnStockBas();

    // Valeur totale du stock (prix vente × quantité)
    @Query("SELECT SUM(p.quantiteStock * p.prixVente) FROM Produit p")
    Double valeurTotaleStock();
}