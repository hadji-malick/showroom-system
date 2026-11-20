package com.powertech.stock.controllers;

import com.powertech.stock.entities.Fournisseur;
import com.powertech.stock.entities.Produit;
import com.powertech.stock.repositories.FournisseurRepository;
import com.powertech.stock.repositories.ProduitRepository;
import com.powertech.stock.services.StockService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProduitControllerEmplacementTest {

    @Mock
    private FournisseurRepository fournisseurRepository;

    @Mock
    private StockService stockService;

    @Mock
    private ProduitRepository produitRepository;

    @InjectMocks
    private ProduitController produitController;

    @Test
    void createProduit_shouldMapEmplacementFromPayload() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("reference", "REF-001");
        payload.put("nom", "Produit test");
        payload.put("prixVente", 125.0);
        payload.put("quantiteStock", 10);
        payload.put("emplacement", "A1-03");

        when(stockService.createProduit(any(Produit.class))).thenAnswer(invocation -> {
            Produit produit = invocation.getArgument(0);
            assertThat(produit.getEmplacement()).isEqualTo("A1-03");
            return produit;
        });

        ResponseEntity<?> response = produitController.createProduit(payload);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Produit created = (Produit) response.getBody();
        assertThat(created).isNotNull();
        assertThat(created.getEmplacement()).isEqualTo("A1-03");
    }

    @Test
    void updateProduit_shouldMapEmplacementFromPayload() {
        Produit existingProduit = new Produit();
        existingProduit.setReference("REF-001");
        existingProduit.setNom("Ancien nom");
        existingProduit.setPrixVente(99.0);
        existingProduit.setQuantiteStock(5);

        Map<String, Object> updates = new HashMap<>();
        updates.put("nom", "Produit modifié");
        updates.put("prixVente", 150.0);
        updates.put("emplacement", "B2-07");

        when(produitRepository.findById(1L)).thenReturn(Optional.of(existingProduit));
        when(produitRepository.save(any(Produit.class))).thenAnswer(invocation -> {
            Produit produit = invocation.getArgument(0);
            assertThat(produit.getEmplacement()).isEqualTo("B2-07");
            return produit;
        });

        ResponseEntity<?> response = produitController.updateProduit(1L, updates);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Produit updated = (Produit) response.getBody();
        assertThat(updated).isNotNull();
        assertThat(updated.getEmplacement()).isEqualTo("B2-07");
    }
}
