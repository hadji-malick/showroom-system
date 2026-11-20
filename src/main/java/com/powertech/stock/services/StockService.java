package com.powertech.stock.services;

import com.powertech.stock.entities.Produit;
import com.powertech.stock.entities.Vente;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.powertech.stock.repositories.ProduitRepository;
import com.powertech.stock.repositories.VenteRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import com.powertech.stock.entities.CommandeFournisseur;
import com.powertech.stock.entities.LigneCommande;
import com.powertech.stock.repositories.CommandeFournisseurRepository;
import com.powertech.stock.repositories.LigneCommandeRepository;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;

@Service
public class StockService {

    @Autowired
    private ProduitRepository produitRepository;

    @Autowired
    private CommandeFournisseurRepository commandeFournisseurRepository;

    @Autowired
    private LigneCommandeRepository ligneCommandeRepository;

    @Autowired
    private EmailService emailService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private String generateToken() {
        return UUID.randomUUID().toString();
    }

    @Transactional
    public void envoyerCommandeParEmail(Long commandeId) {
        CommandeFournisseur commande = commandeFournisseurRepository.findById(commandeId)
                .orElseThrow(() -> new RuntimeException("Commande non trouvée"));

        if (commande.getTokenConfirmation() == null) {
            commande.setTokenConfirmation(generateToken());
            commande = commandeFournisseurRepository.save(commande);
        }

        String emailFournisseur = commande.getFournisseur().getEmail();
        if (emailFournisseur == null || emailFournisseur.isEmpty()) {
            throw new RuntimeException("Ce fournisseur n'a pas d'adresse email");
        }

        List<LigneCommande> lignes = ligneCommandeRepository.findByCommandeId(commandeId);

        try {
            emailService.envoyerDemandeDevis(emailFournisseur, commande.getNumero(),
                    commande.getTokenConfirmation(), lignes);
        } catch (Exception e) {
            System.err.println("ERREUR lors de l'envoi de la commande par email: " + e.getMessage());
            throw new RuntimeException("Impossible d'envoyer l'email: " + e.getMessage(), e);
        }
    }
    @Transactional
    public CommandeFournisseur rejeterDemandeDevis(String token, String commentaire) {
        CommandeFournisseur commande = commandeFournisseurRepository.findByTokenConfirmation(token)
                .orElseThrow(() -> new RuntimeException("Token invalide"));

        commande.setStatut("DEMANDE_REJETEE");
        commande.setCommentaireModification(commentaire);
        commande = commandeFournisseurRepository.save(commande);

        // Notifier le service achats / stock manager du rejet
        try {
            String emailFournisseur = commande.getFournisseur().getEmail();
            emailService.envoyerNotificationRejetDemande(emailFournisseur, commande.getNumero(), commentaire);
        } catch (Exception e) {
            System.err.println("Erreur notification rejet: " + e.getMessage());
        }

        return commande;
    }
    @Transactional
    public CommandeFournisseur soumettreDevis(String token, Map<Long, Double> prix,
                                              Double fraisTransport, Integer delaiLivraison,
                                              String dateValidite, String conditionsPaiement,
                                              String incoterm) {
        CommandeFournisseur commande = commandeFournisseurRepository.findByTokenConfirmation(token)
                .orElseThrow(() -> new RuntimeException("Commande non trouvée"));

        System.out.println("✅ Commande trouvée: " + commande.getNumero());

        // Appliquer les prix aux lignes de commande
        List<LigneCommande> lignes = ligneCommandeRepository.findByCommandeId(commande.getId());
        double total = 0;
        for (LigneCommande ligne : lignes) {
            Long produitId = ligne.getProduit().getId();
            if (prix.containsKey(produitId)) {
                Double prixUnitaire = prix.get(produitId);
                ligne.setPrixUnitaire(prixUnitaire);
                ligne.setSousTotal(ligne.getQuantite() * prixUnitaire);
                total += ligne.getSousTotal();
                ligneCommandeRepository.save(ligne);
                System.out.println("💰 " + ligne.getProduit().getNom() + " -> " + prixUnitaire + " FCFA");
            }
        }

        // Mettre à jour la commande
        commande.setFraisTransport(fraisTransport);
        commande.setDelaiLivraisonPropose(delaiLivraison);
        if (dateValidite != null && !dateValidite.isEmpty()) {
            commande.setDateValidite(LocalDateTime.parse(dateValidite + "T00:00:00"));
        }
        commande.setConditionsPaiement(conditionsPaiement);
        commande.setIncoterm(incoterm);
        commande.setMontantTotal(total + (fraisTransport != null ? fraisTransport : 0));
        commande.setStatut("DEVIS_ENVOYE");

        return commandeFournisseurRepository.save(commande);
    }
    @Transactional
    public boolean confirmerCommande(String token) {
        CommandeFournisseur commande = commandeFournisseurRepository.findByTokenConfirmation(token)
                .orElseThrow(() -> new RuntimeException("Token invalide"));

        commande.setConfirmee(true);
        commande.setStatut(CommandeFournisseur.STATUT_CONFIRMEE);
        commandeFournisseurRepository.save(commande);

        return true;
    }


    public CommandeFournisseur getCommandeByToken(String token) {
        return commandeFournisseurRepository.findByTokenConfirmation(token)
                .orElseThrow(() -> new RuntimeException("Commande non trouvee"));
    }

    @Transactional
    public void proposerModificationCommande(String token, Map<Long, Integer> nouvellesQuantites,
                                             Map<Long, Double> nouveauxPrix, String commentaire) {
        CommandeFournisseur commande = commandeFournisseurRepository.findByTokenConfirmation(token)
                .orElseThrow(() -> new RuntimeException("Commande non trouvée"));

        // Créer un objet JSON avec les deux informations
        Map<String, Object> modifications = new HashMap<>();
        modifications.put("quantites", nouvellesQuantites);
        modifications.put("prix", nouveauxPrix);
        modifications.put("commentaire", commentaire);

        try {
            ObjectMapper mapper = new ObjectMapper();
            commande.setQuantitesProposees(mapper.writeValueAsString(modifications));
        } catch (Exception e) {
            throw new RuntimeException("Erreur lors de la sauvegarde des modifications");
        }

        commande.setCommentaireModification(commentaire);
        commande.setStatut("MODIFICATION_ENVOYEE");
        commandeFournisseurRepository.save(commande);
    }

    @Transactional
    public CommandeFournisseur approuverModificationCommande(Long id) {
        CommandeFournisseur commande = getCommandeById(id);
        commande.setStatut("MODIFICATION_APPROUVEE");

        // ... (le reste du code d'application des modifications)

        commande = commandeFournisseurRepository.save(commande);

        // ===== ENVOI D'EMAIL AVEC LOGS =====
        System.out.println("=== DÉBUT ENVOI EMAIL APPROBATION ===");
        System.out.println("Commande ID: " + commande.getId());
        System.out.println("Numéro: " + commande.getNumero());

        // Vérifier le token
        if (commande.getTokenConfirmation() == null || commande.getTokenConfirmation().isEmpty()) {
            System.out.println("Token manquant, génération d'un nouveau...");
            commande.setTokenConfirmation(generateToken());
            commande = commandeFournisseurRepository.save(commande);
        }
        System.out.println("Token: " + commande.getTokenConfirmation());

        // Vérifier l'email du fournisseur
        String emailFournisseur = commande.getFournisseur().getEmail();
        System.out.println("Email fournisseur: '" + emailFournisseur + "'");

        if (emailFournisseur != null && !emailFournisseur.isEmpty()) {
            System.out.println("Envoi de l'email à: " + emailFournisseur);
            try {
                emailService.envoyerApprobationModification(emailFournisseur, commande.getNumero(), commande.getTokenConfirmation());
                System.out.println("Email envoyé avec succès !");
            } catch (Exception e) {
                System.err.println("ERREUR lors de l'envoi de l'email: " + e.getMessage());
                e.printStackTrace();
            }
        } else {
            System.err.println("⚠️ EMAIL DU FOURNISSEUR VIDE OU NULL !");
        }
        System.out.println("=== FIN ENVOI EMAIL APPROBATION ===");

        return commande;
    }
    @Transactional
    public CommandeFournisseur refuserModificationCommande(Long commandeId) {
        CommandeFournisseur commande = commandeFournisseurRepository.findById(commandeId)
                .orElseThrow(() -> new RuntimeException("Commande non trouvee"));

        commande.setStatut(CommandeFournisseur.STATUT_MODIFICATION_REFUSEE);
        return commandeFournisseurRepository.save(commande);
    }

    private String convertMapToJson(Map<?, ?> map) {
        try {
            return objectMapper.writeValueAsString(map);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Impossible d'enregistrer les modifications proposees", e);
        }
    }

    public Map<Long, Integer> convertJsonToIntegerMap(String json) {
        Map<Long, Integer> result = new HashMap<>();
        if (json == null || json.isBlank()) {
            return result;
        }

        try {
            Map<?, ?> raw = objectMapper.readValue(json, Map.class);
            for (Map.Entry<?, ?> entry : raw.entrySet()) {
                Long ligneId = Long.valueOf(entry.getKey().toString());
                Integer quantite = Integer.valueOf(entry.getValue().toString());
                result.put(ligneId, quantite);
            }
            return result;
        } catch (Exception e) {
            throw new RuntimeException("Quantites proposees invalides", e);
        }
    }

    public Map<Long, Double> convertJsonToDoubleMap(String json) {
        Map<Long, Double> result = new HashMap<>();
        if (json == null || json.isBlank()) {
            return result;
        }

        try {
            Map<?, ?> raw = objectMapper.readValue(json, Map.class);
            for (Map.Entry<?, ?> entry : raw.entrySet()) {
                Long ligneId = Long.valueOf(entry.getKey().toString());
                Double prix = Double.valueOf(entry.getValue().toString());
                result.put(ligneId, prix);
            }
            return result;
        } catch (Exception e) {
            throw new RuntimeException("Prix proposes invalides", e);
        }
    }

    private <T> T getProposition(Map<Long, T> propositions, LigneCommande ligne) {
        T proposition = propositions.get(ligne.getId());
        if (proposition == null && ligne.getProduit() != null) {
            proposition = propositions.get(ligne.getProduit().getId());
        }
        return proposition;
    }

    private Map<Long, Integer> toLongIntegerMap(Object value) {
        Map<Long, Integer> result = new HashMap<>();
        if (!(value instanceof Map<?, ?> rawMap)) {
            return result;
        }

        for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
            result.put(Long.valueOf(entry.getKey().toString()), Integer.valueOf(entry.getValue().toString()));
        }
        return result;
    }

    private Map<Long, Double> toLongDoubleMap(Object value) {
        Map<Long, Double> result = new HashMap<>();
        if (!(value instanceof Map<?, ?> rawMap)) {
            return result;
        }

        for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
            result.put(Long.valueOf(entry.getKey().toString()), Double.valueOf(entry.getValue().toString()));
        }
        return result;
    }
    // Récupérer tous les produits
    public List<Produit> getAllProduits() {
        return produitRepository.findAll();
    }

    // Récupérer un produit par son ID
    public Produit getProduitById(Long id) {
        return produitRepository.findById(id).orElse(null);
    }

    // Créer un nouveau produit
    public Produit createProduit(Produit produit) {
        // Vérifier que la référence n'existe pas déjà
        Produit existant = produitRepository.findByReference(produit.getReference());
        if (existant != null) {
            throw new RuntimeException("Un produit avec la référence " + produit.getReference() + " existe déjà");
        }
        return produitRepository.save(produit);
    }

    // Mettre à jour un produit
    // Mettre à jour un produit
    public Produit updateProduit(Long id, Produit produitDetails) {
        Produit produit = produitRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Produit non trouvé avec l'id: " + id));

        produit.setNom(produitDetails.getNom());
        produit.setDescription(produitDetails.getDescription());
        produit.setPrixAchat(produitDetails.getPrixAchat());
        produit.setPrixVente(produitDetails.getPrixVente());
        produit.setSeuilAlerte(produitDetails.getSeuilAlerte());
        produit.setEmplacement(produitDetails.getEmplacement());

        return produitRepository.save(produit);
    }

    // Supprimer un produit
    public void deleteProduit(Long id) {
        produitRepository.deleteById(id);
    }

    // Produits en stock bas
    public List<Produit> getProduitsEnStockBas() {
        return produitRepository.findByQuantiteStockLessThan(5);
    }

    // Statistiques pour le dashboard
    public Map<String, Object> getDashboardStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalProduits", produitRepository.count());
        stats.put("produitsStockBas", produitRepository.countProduitsEnStockBas());
        Double valeurTotale = produitRepository.valeurTotaleStock();
        stats.put("valeurTotaleStock", valeurTotale != null ? valeurTotale : 0);
        return stats;
    }
    @Autowired
    private VenteRepository venteRepository;

    // VENTE (sortie de stock) AVEC HISTORIQUE
    public Produit vendreProduit(Long produitId, int quantiteVendue, String vendeur) {
        Produit produit = produitRepository.findById(produitId)
                .orElseThrow(() -> new RuntimeException("Produit non trouvé"));

        // Vérifier que le stock est suffisant
        if (produit.getQuantiteStock() < quantiteVendue) {
            throw new RuntimeException("Stock insuffisant ! Stock actuel: " + produit.getQuantiteStock());
        }

        // Enregistrer la vente dans l'historique AVANT de modifier le stock
        Vente vente = new Vente(produit, quantiteVendue, produit.getPrixVente(), vendeur);
        venteRepository.save(vente);

        // Diminuer le stock
        produit.setQuantiteStock(produit.getQuantiteStock() - quantiteVendue);

        return produitRepository.save(produit);
    }
    // ENTRÉE DE STOCK (réapprovisionnement)
    public Produit entreeStock(Long produitId, int quantite, String fournisseur, String note) {
        Produit produit = produitRepository.findById(produitId)
                .orElseThrow(() -> new RuntimeException("Produit non trouvé"));

        // Augmenter le stock
        produit.setQuantiteStock(produit.getQuantiteStock() + quantite);

        return produitRepository.save(produit);
    }
    // ==================== COMMANDES FOURNISSEURS ====================

    @Transactional
    public CommandeFournisseur createCommande(CommandeFournisseur commande, List<LigneCommande> lignes) {
        commande = commandeFournisseurRepository.save(commande);
        double total = 0.0;
        for (LigneCommande ligne : lignes) {
            ligne.setCommande(commande);
            if (ligne.getSousTotal() == null || ligne.getSousTotal() <= 0) {
                double prixUnitaire = ligne.getPrixUnitaire();
                if (prixUnitaire <= 0 && ligne.getProduit() != null && ligne.getProduit().getPrixVente() != null) {
                    prixUnitaire = ligne.getProduit().getPrixVente();
                    ligne.setPrixUnitaire(prixUnitaire);
                }
                ligne.setSousTotal((double) ligne.getQuantite() * prixUnitaire);
            }
            ligneCommandeRepository.save(ligne);
            total += ligne.getSousTotal();
        }
        commande.setMontantTotal(total);
        return commandeFournisseurRepository.save(commande);
    }

    public List<CommandeFournisseur> getAllCommandes() {
        return commandeFournisseurRepository.findAll();
    }

    public List<CommandeFournisseur> getCommandesByStatut(String statut) {
        return commandeFournisseurRepository.findByStatut(statut);
    }

    @Transactional
    public CommandeFournisseur updateStatutCommande(Long commandeId, String nouveauStatut) {
        CommandeFournisseur commande = commandeFournisseurRepository.findById(commandeId)
                .orElseThrow(() -> new RuntimeException("Commande non trouvée"));

        commande.setStatut(nouveauStatut);

        // Si la commande est livrée, on augmente le stock des produits
        if (CommandeFournisseur.STATUT_LIVREE.equals(nouveauStatut)) {
            List<LigneCommande> lignes = ligneCommandeRepository.findByCommandeId(commandeId);
            for (LigneCommande ligne : lignes) {
                Produit produit = ligne.getProduit();
                produit.setQuantiteStock(produit.getQuantiteStock() + ligne.getQuantite());
                produitRepository.save(produit);
            }
        }

        return commandeFournisseurRepository.save(commande);
    }
    public CommandeFournisseur getCommandeById(Long id) {
        return commandeFournisseurRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Commande non trouvée"));
    }
    @Transactional
    public void accepterModificationCommande(String token) {
        CommandeFournisseur commande = commandeFournisseurRepository.findByTokenConfirmation(token)
                .orElseThrow(() -> new RuntimeException("Commande non trouvée"));

        // If already accepted, do nothing (idempotent)
        if ("MODIFICATION_ACCEPTEE".equals(commande.getStatut())) {
            return;
        }

        if (!"MODIFICATION_APPROUVEE".equals(commande.getStatut())) {
            throw new RuntimeException("Cette commande n'est pas en attente d'acceptation");
        }

        // Appliquer les modifications
        appliquerModifications(commande);
        // Marquer comme confirmée pour réutiliser le flux normal (expédition / livraison)
        commande.setStatut(CommandeFournisseur.STATUT_CONFIRMEE);
        commande.setConfirmee(true);
        commandeFournisseurRepository.save(commande);

        // Envoyer email de confirmation
        String emailFournisseur = commande.getFournisseur().getEmail();
        if (emailFournisseur != null && !emailFournisseur.isEmpty()) {
            try {
                emailService.envoyerAcceptationFinale(emailFournisseur, commande.getNumero());
            } catch (Exception e) {
                System.err.println("ERREUR lors de l'envoi de l'email d'acceptation finale: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }


    private void appliquerModifications(CommandeFournisseur commande) {
        if (commande.getQuantitesProposees() == null) return;

        try {
            ObjectMapper mapper = new ObjectMapper();
            Map<String, Object> modifications = mapper.readValue(commande.getQuantitesProposees(),
                    new TypeReference<Map<String, Object>>() {});

            Map<Long, Integer> nouvellesQuantites = new HashMap<>();
            Map<Long, Double> nouveauxPrix = new HashMap<>();

            if (modifications.containsKey("quantites")) {
                Map<String, Integer> q = (Map<String, Integer>) modifications.get("quantites");
                for (Map.Entry<String, Integer> entry : q.entrySet()) {
                    nouvellesQuantites.put(Long.parseLong(entry.getKey()), entry.getValue());
                }
            }
            if (modifications.containsKey("prix")) {
                Map<String, Double> p = (Map<String, Double>) modifications.get("prix");
                for (Map.Entry<String, Double> entry : p.entrySet()) {
                    nouveauxPrix.put(Long.parseLong(entry.getKey()), entry.getValue());
                }
            }

            List<LigneCommande> lignes = ligneCommandeRepository.findByCommandeId(commande.getId());
            for (LigneCommande ligne : lignes) {
                Long produitId = ligne.getProduit().getId();
                if (nouvellesQuantites.containsKey(produitId)) {
                    ligne.setQuantite(nouvellesQuantites.get(produitId));
                }
                if (nouveauxPrix.containsKey(produitId)) {
                    ligne.setPrixUnitaire(nouveauxPrix.get(produitId));
                    ligne.setSousTotal(ligne.getQuantite() * ligne.getPrixUnitaire());
                }
                ligneCommandeRepository.save(ligne);
            }

            double nouveauTotal = lignes.stream().mapToDouble(LigneCommande::getSousTotal).sum();
            commande.setMontantTotal(nouveauTotal);
        } catch (Exception e) {
            throw new RuntimeException("Erreur lors de l'application des modifications");
        }
    }
    @Transactional
    public CommandeFournisseur validerDevis(Long id) {
        CommandeFournisseur commande = getCommandeById(id);
        if (commande.getTokenConfirmation() == null || commande.getTokenConfirmation().isBlank()) {
            commande.setTokenConfirmation(generateToken());
        }
        commande.setStatut(CommandeFournisseur.STATUT_DEVIS_VALIDE);
        // Appliquer les prix du devis aux lignes de commande
        // ...
        return commandeFournisseurRepository.save(commande);
    }

    @Transactional
    public CommandeFournisseur refuserDevis(Long id) {
        CommandeFournisseur commande = getCommandeById(id);
        commande.setStatut(CommandeFournisseur.STATUT_DEVIS_REFUSE);
        return commandeFournisseurRepository.save(commande);
    }
    public CommandeFournisseur saveCommande(CommandeFournisseur commande) {
        return commandeFournisseurRepository.save(commande);
    }
    @Transactional
    public CommandeFournisseur proposerDateExpedition(Long id, LocalDateTime date) {
        CommandeFournisseur commande = getCommandeById(id);
        commande.setDateExpeditionProposee(date);
        return commandeFournisseurRepository.save(commande);
    }

    @Transactional
    public CommandeFournisseur confirmerDateExpedition(String token, LocalDateTime date) {
        CommandeFournisseur commande = commandeFournisseurRepository.findByTokenConfirmation(token)
                .orElseThrow(() -> new RuntimeException("Commande non trouvée"));

        commande.setDateExpeditionProposee(date);
        commande.setStatut(CommandeFournisseur.STATUT_COMMANDE_CONFIRMEE);
        return commandeFournisseurRepository.save(commande);
    }
    @Autowired
    private NotificationService notificationService;

    public void verifierStockBas() {
        List<Produit> produits = produitRepository.findAll();
        for (Produit p : produits) {
            if (p.getQuantiteStock() <= p.getSeuilAlerte()) {
                notificationService.envoyerNotification(
                        "⚠️ Stock bas",
                        "STOCK_BAS",
                        "Le produit " + p.getNom() + " est en stock bas (" + p.getQuantiteStock() + " unités)",
                        "/stocks"
                );
            }
        }
    }
}
