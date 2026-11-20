package com.powertech.stock.repositories;

import com.powertech.stock.entities.Paiement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PaiementRepository extends JpaRepository<Paiement, Long> {
    List<Paiement> findByFactureId(Long factureId);
}
