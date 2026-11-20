package com.powertech.stock.services;

import com.powertech.stock.entities.Fournisseur;
import com.powertech.stock.repositories.FournisseurRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class FournisseurService {

    @Autowired
    private FournisseurRepository fournisseurRepository;

    public List<Fournisseur> getAllFournisseurs() {
        return fournisseurRepository.findAll();
    }

    public Fournisseur getFournisseurById(Long id) {
        return fournisseurRepository.findById(id).orElse(null);
    }

    public Fournisseur createFournisseur(Fournisseur fournisseur) {
        if (fournisseurRepository.existsByNom(fournisseur.getNom())) {
            throw new RuntimeException("Un fournisseur avec ce nom existe déjà");
        }
        return fournisseurRepository.save(fournisseur);
    }

    public Fournisseur updateFournisseur(Long id, Fournisseur fournisseurDetails) {
        Fournisseur fournisseur = fournisseurRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Fournisseur non trouvé"));
        fournisseur.setNom(fournisseurDetails.getNom());
        fournisseur.setContact(fournisseurDetails.getContact());
        fournisseur.setTelephone(fournisseurDetails.getTelephone());
        fournisseur.setEmail(fournisseurDetails.getEmail());
        fournisseur.setAdresse(fournisseurDetails.getAdresse());
        return fournisseurRepository.save(fournisseur);
    }

    public void deleteFournisseur(Long id) {
        fournisseurRepository.deleteById(id);
    }
}