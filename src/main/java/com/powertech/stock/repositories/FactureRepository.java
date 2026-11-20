package com.powertech.stock.repositories;

import com.powertech.stock.entities.Facture;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface FactureRepository extends JpaRepository<Facture, Long> {
    List<Facture> findByOrderByDateFactureDesc();
}