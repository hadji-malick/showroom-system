package com.powertech.stock.repositories;

import com.powertech.stock.entities.ClotureCaisse;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ClotureCaisseRepository extends JpaRepository<ClotureCaisse, Long> {
    Optional<ClotureCaisse> findByDate(LocalDate date);
    List<ClotureCaisse> findByOrderByDateDesc();
    List<ClotureCaisse> findByDateBetween(LocalDate debut, LocalDate fin);
}