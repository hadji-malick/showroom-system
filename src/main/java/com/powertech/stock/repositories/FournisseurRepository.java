package com.powertech.stock.repositories;

import com.powertech.stock.entities.Fournisseur;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface FournisseurRepository extends JpaRepository<Fournisseur, Long> {
    Optional<Fournisseur> findByNom(String nom);
    boolean existsByNom(String nom);
}