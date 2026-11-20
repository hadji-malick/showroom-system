package com.powertech.stock.repositories;

import com.powertech.stock.entities.LigneCommandeClient;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface LigneCommandeClientRepository extends JpaRepository<LigneCommandeClient, Long> {
    List<LigneCommandeClient> findByCommandeClientId(Long commandeClientId);
}