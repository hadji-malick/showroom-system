package com.powertech.stock.repositories;

import com.powertech.stock.entities.CommandeClient;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface CommandeClientRepository extends JpaRepository<CommandeClient, Long> {
    Optional<CommandeClient> findByCode(String code);
    List<CommandeClient> findByTechnicoCommercialNomAndDateCreationBetween(String nom, LocalDateTime debut, LocalDateTime fin);
    List<CommandeClient> findByStatut(String statut);
}