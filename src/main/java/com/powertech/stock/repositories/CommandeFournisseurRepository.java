package com.powertech.stock.repositories;

import com.powertech.stock.entities.CommandeFournisseur;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CommandeFournisseurRepository extends JpaRepository<CommandeFournisseur, Long> {

    List<CommandeFournisseur> findByFournisseurIdOrderByDateCreationDesc(Long fournisseurId);

    List<CommandeFournisseur> findByStatut(String statut);

    Optional<CommandeFournisseur> findByTokenConfirmation(String token);
}