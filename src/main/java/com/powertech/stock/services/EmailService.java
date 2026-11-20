package com.powertech.stock.services;

import com.powertech.stock.entities.LigneCommande;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class EmailService {

    private static final Logger logger = LoggerFactory.getLogger(EmailService.class);

    @Autowired
    private JavaMailSender mailSender;

    @Value("${app.frontend-url:${app.base-url:http://localhost:3000}}")
    private String frontendUrl;
    @Value("${brevo.sender.email}")
    private String senderEmail;

    @Value("${brevo.sender.name}")
    private String senderName;
    @Value("${spring.mail.host}")
    private String mailHost;
    @Value("${spring.mail.port}")
    private int mailPort;
    @Value("${brevo.api.key}")
    private String brevoApiKey;

    // Email initial de commande (avec 2 boutons)
    public void envoyerCommandeFournisseur(String emailFournisseur, String numeroCommande, String token, List<LigneCommande> lignes) throws Exception {
            String lienConfirmation = frontendUrl + "/commande/confirmer/" + token;
            String lienModification = frontendUrl + "/commande/modifier/" + token;
            String dateCommande = LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"));

            StringBuilder produitsHtml = new StringBuilder();
            produitsHtml.append("""
                <table style="width:100%; border-collapse:collapse; margin:15px 0;">
                    <thead>
                        <tr style="background:#f8fafc;">
                            <th style="padding:10px;">Produit</th>
                            <th style="padding:10px;">Quantité</th>
                            <th style="padding:10px;">Prix unitaire</th>
                            <th style="padding:10px;">Total</th>
                        </tr>
                    </thead>
                    <tbody>
            """);

            double total = 0;
            for (LigneCommande ligne : lignes) {
                double sousTotal = ligne.getQuantite() * ligne.getPrixUnitaire();
                total += sousTotal;
                produitsHtml.append(String.format("""
                    <tr style="border-bottom:1px solid #e2e8f0;">
                        <td style="padding:8px;">%s</td>
                        <td style="padding:8px; text-align:center;">%d</td>
                        <td style="padding:8px; text-align:right;">%,.0f</td>
                        <td style="padding:8px; text-align:right;">%,.0f</td>
                    </tr>
                """, ligne.getProduit().getNom(), ligne.getQuantite(), ligne.getPrixUnitaire(), sousTotal));
            }
            produitsHtml.append("</tbody></table>");

            String corpsHtml = String.format("""
                <!DOCTYPE html>
                <html>
                <head><meta charset="UTF-8"><title>Commande Powertech</title>
                <style>
                    .container { max-width: 650px; margin: 0 auto; font-family: Arial; }
                    .header { background: #f97316; padding: 20px; text-align: center; color: white; border-radius: 12px 12px 0 0; }
                    .content { padding: 25px; background: white; border: 1px solid #e2e8f0; border-top: none; border-radius: 0 0 12px 12px; }
                    .btn-confirmer { background: #10b981; color: white; padding: 12px 25px; text-decoration: none; border-radius: 8px; display: inline-block; margin-right: 15px; }
                    .btn-modifier { background: #f59e0b; color: white; padding: 12px 25px; text-decoration: none; border-radius: 8px; display: inline-block; }
                    .footer { text-align: center; padding: 15px; font-size: 11px; color: #64748b; }
                </style>
                </head>
                <body>
                <div class="container">
                    <div class="header"><h1>⚡ POWERTECH</h1><p>ENGINEERING GROUP</p></div>
                    <div class="content">
                        <h2>📦 Nouvelle commande</h2>
                        <p><strong>N° Commande :</strong> %s</p>
                        <p><strong>Date :</strong> %s</p>
                        %s
                        <div style="text-align:right; font-size:18px; font-weight:bold;">Total : <span style="color:#f97316;">%,.0f FCFA</span></div>
                        <div style="text-align:center; margin:30px 0;">
                            <a href="%s" class="btn-confirmer">✅ Confirmer toute la commande</a>
                            <a href="%s" class="btn-modifier">✏️ Proposer des modifications</a>
                        </div>
                        <p>Cordialement,<br>L'équipe Powertech</p>
                    </div>
                    <div class="footer"><p>Powertech Engineering Group - Dakar, Sénégal - 📞 (+221) 766432045</p></div>
                </div>
                </body>
                </html>
            """, numeroCommande, dateCommande, produitsHtml.toString(), total, lienConfirmation, lienModification);

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(senderEmail, senderName);
            helper.setReplyTo(senderEmail);
            helper.setTo(emailFournisseur);
            helper.setSubject("📦 Nouvelle commande Powertech - " + numeroCommande);
            helper.setText(corpsHtml, true);
            String subject = "📦 Nouvelle commande Powertech - " + numeroCommande;
            helper.setSubject(subject);
            if (!trySendWithRetry(message, 3)) {
                logger.warn("SMTP failed after retries, falling back to Brevo API for {}", emailFournisseur);
                sendViaBrevo(emailFournisseur, subject, corpsHtml);
            }
    }

    // Email quand le stock manager approuve (avec lien d'acceptation)
    public void envoyerApprobationModification(String emailFournisseur, String numeroCommande, String token) throws Exception {
            String lienAcceptation = frontendUrl + "/commande/accepter-modification/" + token;
            System.out.println("Lien d'acceptation: " + lienAcceptation);

            String sujet = "✅ Modification approuvée - Powertech - " + numeroCommande;
            String corps = String.format("""
            <!DOCTYPE html>
            <html>
            <head><meta charset="UTF-8"><title>Modification approuvée</title></head>
            <body style="font-family:Arial; text-align:center;">
                <div style="max-width:500px; margin:auto; padding:20px; border:1px solid #e2e8f0; border-radius:12px;">
                    <h2 style="color:#10b981;">✅ Modification approuvée</h2>
                    <p>Votre proposition de modification pour la commande <strong>%s</strong> a été approuvée.</p>
                    <p><strong>Pour valider définitivement, cliquez sur le lien :</strong></p>
                    <p><a href="%s" style="background:#f97316; color:white; padding:10px 20px; text-decoration:none; border-radius:8px; display:inline-block;">✅ Accepter les modifications</a></p>
                    <p>Cordialement,<br>L'équipe Powertech</p>
                </div>
            </body>
            </html>
        """, numeroCommande, lienAcceptation);

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(senderEmail, senderName);
            helper.setReplyTo(senderEmail);
            helper.setTo(emailFournisseur);
            helper.setSubject(sujet);
            helper.setText(corps, true);
            if (!trySendWithRetry(message, 3)) {
                logger.warn("SMTP failed after retries, falling back to Brevo API for {}", emailFournisseur);
                sendViaBrevo(emailFournisseur, sujet, corps);
            } else {
                logger.info("Email envoyé avec succès à {} via SMTP", emailFournisseur);
            }
    }

    // Email quand le stock manager refuse
    public void envoyerRefusModification(String emailFournisseur, String numeroCommande) throws Exception {
            String corps = """
                <!DOCTYPE html>
                <html>
                <head><meta charset="UTF-8"><title>Modification refusée</title></head>
                <body style="font-family:Arial; text-align:center;">
                    <div style="max-width:500px; margin:auto; padding:20px; border:1px solid #e2e8f0; border-radius:12px;">
                        <h2 style="color:#ef4444;">❌ Modification refusée</h2>
                        <p>Votre proposition de modification pour la commande <strong>%s</strong> a été <strong style="color:red;">REFUSÉE</strong>.</p>
                        <p>La commande reste en l'état avec les quantités initiales.</p>
                        <p>Cordialement,<br>L'équipe Powertech</p>
                    </div>
                </body>
                </html>
            """.formatted(numeroCommande);

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(senderEmail, senderName);
            helper.setReplyTo(senderEmail);
            helper.setTo(emailFournisseur);
            helper.setSubject("❌ Modification refusée - Powertech - " + numeroCommande);
            helper.setText(corps, true);
            String sujet = "❌ Modification refusée - Powertech - " + numeroCommande;
            helper.setSubject(sujet);
            if (!trySendWithRetry(message, 3)) {
                logger.warn("SMTP failed after retries, falling back to Brevo API for {}", emailFournisseur);
                sendViaBrevo(emailFournisseur, sujet, corps);
            }
    }

    // Email confirmation d'acceptation finale
    public void envoyerAcceptationFinale(String emailFournisseur, String numeroCommande) throws Exception {
            String corps = """
                <!DOCTYPE html>
                <html>
                <head><meta charset="UTF-8"><title>Acceptation finale</title></head>
                <body style="font-family:Arial; text-align:center;">
                    <div style="max-width:500px; margin:auto; padding:20px; border:1px solid #e2e8f0; border-radius:12px;">
                        <h2 style="color:#10b981;">✅ Modification acceptée</h2>
                        <p>Vous avez accepté les modifications pour la commande <strong>%s</strong>.</p>
                        <p>La commande sera traitée par nos services.</p>
                        <p>Merci de votre collaboration.</p>
                    </div>
                </body>
                </html>
            """.formatted(numeroCommande);

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(senderEmail, senderName);
            helper.setReplyTo(senderEmail);
            helper.setTo(emailFournisseur);
            helper.setSubject("✅ Modification acceptée - Powertech - " + numeroCommande);
            helper.setText(corps, true);
            String sujet = "✅ Modification acceptée - Powertech - " + numeroCommande;
            helper.setSubject(sujet);
            if (!trySendWithRetry(message, 3)) {
                logger.warn("SMTP failed after retries, falling back to Brevo API for {}", emailFournisseur);
                sendViaBrevo(emailFournisseur, sujet, corps);
            }
    }

    public boolean testerSmtp() {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(mailHost, mailPort), 5000);
            logger.info("Connexion SMTP OK: {}:{}", mailHost, mailPort);
            return true;
        } catch (Exception e) {
            logger.error("Impossible de se connecter au serveur SMTP {}:{} — {}", mailHost, mailPort, e.getMessage());
            return false;
        }
    }

    private boolean trySendWithRetry(MimeMessage message, int maxAttempts) {
        int attempt = 0;
        while (attempt < maxAttempts) {
            try {
                mailSender.send(message);
                return true;
            } catch (Exception e) {
                attempt++;
                logger.warn("SMTP send attempt {}/{} failed: {}", attempt, maxAttempts, e.getMessage());
                try {
                    Thread.sleep(1000L * attempt);
                } catch (InterruptedException ignored) {}
            }
        }
        return false;
    }


    private void sendViaBrevo(String to, String subject, String htmlContent) throws Exception {
        try {
            org.springframework.web.client.RestTemplate rt = new org.springframework.web.client.RestTemplate();
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            java.util.Map<String, Object> payload = new java.util.HashMap<>();
            java.util.Map<String, String> sender = new java.util.HashMap<>();
            sender.put("email", senderEmail);
            sender.put("name", senderName);
            payload.put("sender", sender);
            java.util.List<java.util.Map<String, String>> toList = new java.util.ArrayList<>();
            java.util.Map<String, String> toItem = new java.util.HashMap<>();
            toItem.put("email", to);
            toList.add(toItem);
            payload.put("to", toList);
            payload.put("subject", subject);
            payload.put("htmlContent", htmlContent);

            String url = "https://api.brevo.com/v3/smtp/email";
            org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
            headers.set("api-key", brevoApiKey);
            headers.set(org.springframework.http.HttpHeaders.CONTENT_TYPE, org.springframework.http.MediaType.APPLICATION_JSON_VALUE);
            org.springframework.http.HttpEntity<String> entity = new org.springframework.http.HttpEntity<>(mapper.writeValueAsString(payload), headers);
            org.springframework.http.ResponseEntity<String> resp = rt.postForEntity(url, entity, String.class);
            if (!resp.getStatusCode().is2xxSuccessful()) {
                throw new RuntimeException("Brevo API returned " + resp.getStatusCodeValue() + ": " + resp.getBody());
            }
            logger.info("Email envoyé via Brevo à {} (status {})", to, resp.getStatusCodeValue());
        } catch (Exception e) {
            logger.error("Erreur envoi via Brevo: {}", e.getMessage());
            throw e;
        }
    }
    // Email : Devis soumis par le fournisseur (au stock manager)
    public void envoyerDevisSoumis(String emailStockManager, String numeroCommande, String fournisseurNom) {
        try {
            String sujet = "📄 Nouveau devis reçu - Powertech - " + numeroCommande;
            String corps = String.format("""
            <!DOCTYPE html>
            <html>
            <head><meta charset="UTF-8"><title>Nouveau devis</title></head>
            <body style="font-family:Arial;">
                <div style="max-width:600px;margin:auto;padding:20px;border:1px solid #e2e8f0;border-radius:12px;">
                    <h2 style="color:#f97316;">📄 Nouveau devis reçu</h2>
                    <p><strong>Commande N° :</strong> %s</p>
                    <p><strong>Fournisseur :</strong> %s</p>
                    <p>Un nouveau devis a été soumis par le fournisseur.</p>
                    <p>Connectez-vous à l'application pour le consulter.</p>
                    <p>Cordialement,<br>L'équipe Powertech</p>
                </div>
            </body>
            </html>
        """, numeroCommande, fournisseurNom);

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setTo(emailStockManager);
            helper.setSubject(sujet);
            helper.setText(corps, true);
            mailSender.send(message);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Email : Devis approuvé (au fournisseur)
    public void envoyerDevisApprouve(String emailFournisseur, String numeroCommande) {
        try {
            String sujet = "✅ Devis approuvé - Powertech - " + numeroCommande;
            String corps = String.format("""
            <!DOCTYPE html>
            <html>
            <head><meta charset="UTF-8"><title>Devis approuvé</title></head>
            <body style="font-family:Arial;">
                <div style="max-width:600px;margin:auto;padding:20px;border:1px solid #e2e8f0;border-radius:12px;">
                    <h2 style="color:#10b981;">✅ Devis approuvé</h2>
                    <p><strong>Commande N° :</strong> %s</p>
                    <p>Votre devis a été <strong style="color:green;">APPROUVÉ</strong> par notre service achats.</p>
                    <p>Merci de confirmer la date d'expédition exacte.</p>
                    <p>Cordialement,<br>L'équipe Powertech</p>
                </div>
            </body>
            </html>
        """, numeroCommande);

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setTo(emailFournisseur);
            helper.setSubject(sujet);
            helper.setText(corps, true);
            mailSender.send(message);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Email : Date d'expédition proposée (au stock manager)
    public void envoyerDateExpeditionProposee(String emailStockManager, String numeroCommande, String dateExpedition) {
        try {
            String sujet = "📅 Date d'expédition proposée - Powertech - " + numeroCommande;
            String corps = String.format("""
            <!DOCTYPE html>
            <html>
            <head><meta charset="UTF-8"><title>Date d'expédition</title></head>
            <body style="font-family:Arial;">
                <div style="max-width:600px;margin:auto;padding:20px;border:1px solid #e2e8f0;border-radius:12px;">
                    <h2 style="color:#f97316;">📅 Date d'expédition proposée</h2>
                    <p><strong>Commande N° :</strong> %s</p>
                    <p>Le fournisseur a proposé la date d'expédition suivante :</p>
                    <p style="font-size:20px;font-weight:bold;color:#f97316;">%s</p>
                    <p>Connectez-vous à l'application pour valider.</p>
                    <p>Cordialement,<br>L'équipe Powertech</p>
                </div>
            </body>
            </html>
        """, numeroCommande, dateExpedition);

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setTo(emailStockManager);
            helper.setSubject(sujet);
            helper.setText(corps, true);
            mailSender.send(message);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    public void envoyerConfirmationDateExpedition(String emailFournisseur, String numeroCommande, String token) throws Exception {
        String lienDate = frontendUrl + "/commande/confirmer-date/" + token;

        String corps = String.format("""
        <!DOCTYPE html>
        <html>
        <head>
            <meta charset="UTF-8">
            <title>Confirmation d'expédition</title>
            <style>
                .container { max-width: 650px; margin: 0 auto; font-family: Arial; }
                .header { background: #f97316; padding: 20px; text-align: center; color: white; border-radius: 12px 12px 0 0; }
                .content { padding: 25px; background: white; border: 1px solid #e2e8f0; border-top: none; border-radius: 0 0 12px 12px; }
                .btn-primary { background: #f97316; color: white; padding: 12px 25px; text-decoration: none; border-radius: 8px; display: inline-block; }
            </style>
        </head>
        <body>
            <div class="container">
                <div class="header">
                    <h1>⚡ POWERTECH</h1>
                    <p>ENGINEERING GROUP</p>
                </div>
                <div class="content">
                    <h2>✅ Devis validé</h2>
                    <p>Votre devis pour la commande <strong>%s</strong> a été approuvé.</p>
                    <p><strong>Merci de confirmer la date d'expédition exacte :</strong></p>
                    <div style="text-align:center; margin:30px 0;">
                        <a href="%s" class="btn-primary">📅 Confirmer la date d'expédition</a>
                    </div>
                    <p>Cordialement,<br><strong>L'équipe Powertech</strong></p>
                </div>
            </div>
        </body>
        </html>
    """, numeroCommande, lienDate);

        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
        helper.setFrom(senderEmail, senderName);
        helper.setReplyTo(senderEmail);
        helper.setTo(emailFournisseur);
        String sujet = "✅ Devis approuvé - Confirmez la date d'expédition - " + numeroCommande;
        helper.setSubject(sujet);
        helper.setText(corps, true);

        if (!trySendWithRetry(message, 3)) {
            logger.warn("SMTP failed after retries, falling back to Brevo API for {}", emailFournisseur);
            sendViaBrevo(emailFournisseur, sujet, corps);
        }
    }

    public void envoyerDevisRefuse(String emailFournisseur, String numeroCommande) throws Exception {
        String sujet = "Devis refuse - Powertech - " + numeroCommande;
        String corps = String.format("""
        <!DOCTYPE html>
        <html>
        <head>
            <meta charset="UTF-8">
            <title>Devis refuse</title>
            <style>
                .container { max-width: 650px; margin: 0 auto; font-family: Arial; }
                .header { background: #ef4444; padding: 20px; text-align: center; color: white; border-radius: 12px 12px 0 0; }
                .content { padding: 25px; background: white; border: 1px solid #e2e8f0; border-top: none; border-radius: 0 0 12px 12px; }
            </style>
        </head>
        <body>
            <div class="container">
                <div class="header">
                    <h1>POWERTECH</h1>
                    <p>ENGINEERING GROUP</p>
                </div>
                <div class="content">
                    <h2>Devis refuse</h2>
                    <p>Votre devis pour la commande <strong>%s</strong> a ete refuse par notre service achats.</p>
                    <p>Merci pour votre retour. Vous serez contacte si une nouvelle demande est necessaire.</p>
                    <p>Cordialement,<br><strong>L'equipe Powertech</strong></p>
                </div>
            </div>
        </body>
        </html>
    """, numeroCommande);

        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
        helper.setFrom(senderEmail, senderName);
        helper.setReplyTo(senderEmail);
        helper.setTo(emailFournisseur);
        helper.setSubject(sujet);
        helper.setText(corps, true);

        if (!trySendWithRetry(message, 3)) {
            logger.warn("SMTP failed after retries, falling back to Brevo API for {}", emailFournisseur);
            sendViaBrevo(emailFournisseur, sujet, corps);
        }
    }
    public void envoyerDemandeDevis(String emailFournisseur, String numeroCommande, String token, List<LigneCommande> lignes) {
        try {
            String lienDevis = frontendUrl + "/commande/devis/" + token;
            String lienRejet = frontendUrl + "/commande/rejeter-demande/" + token;
            String dateCommande = LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"));

            StringBuilder produitsHtml = new StringBuilder();
            produitsHtml.append("""
            <table style="width:100%; border-collapse:collapse; margin:15px 0;">
                <thead>
                    <tr style="background:#f8fafc;">
                        <th style="padding:10px;">Produit</th>
                        <th style="padding:10px;">Marque</th>
                        <th style="padding:10px;">Quantité</th>
                    </tr>
                </thead>
                <tbody>
        """);

            for (LigneCommande ligne : lignes) {
                String marque = ligne.getMarque() != null && !ligne.getMarque().isBlank() ? ligne.getMarque() : "Non spécifiée";
                produitsHtml.append(String.format("""
    <tr style="border-bottom:1px solid #e2e8f0;">
        <td style="padding:8px;">%s</td>
        <td style="padding:8px;"><strong>%s</strong></td>
        <td style="padding:8px; text-align:center;">%d</td>
    </tr>
""", ligne.getProduit().getNom(), marque, ligne.getQuantite()));
            }
            produitsHtml.append("</tbody></table>");

            String corps = String.format("""
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <title>Demande de devis - Powertech</title>
                <style>
                    .container { max-width: 650px; margin: 0 auto; font-family: Arial; }
                    .header { background: #f97316; padding: 20px; text-align: center; color: white; border-radius: 12px 12px 0 0; }
                    .content { padding: 25px; background: white; border: 1px solid #e2e8f0; border-top: none; border-radius: 0 0 12px 12px; }
                    .btn-primary { background: #f97316; color: white; padding: 12px 25px; text-decoration: none; border-radius: 8px; display: inline-block; margin-right:15px; }
                    .btn-reject { background: #ef4444; color: white; padding: 12px 25px; text-decoration: none; border-radius: 8px; display: inline-block; }
                    .footer { text-align: center; padding: 15px; font-size: 11px; color: #64748b; }
                </style>
            </head>
            <body>
                <div class="container">
                    <div class="header">
                        <h1>⚡ POWERTECH</h1>
                        <p>ENGINEERING GROUP</p>
                    </div>
                    <div class="content">
                        <h2>📋 Demande de devis</h2>
                        <p><strong>N° Demande :</strong> %s</p>
                        <p><strong>Date :</strong> %s</p>

                        <p><strong>Nous souhaitons connaître votre offre pour les produits suivants :</strong></p>
                        %s

                        <div style="text-align:center; margin:30px 0;">
                            <a href="%s" class="btn-primary">📄 Créer mon devis</a>
                            <a href="%s" class="btn-reject">❌ Rejeter la demande</a>
                        </div>

                        <p>Cordialement,<br><strong>L'équipe Powertech</strong></p>
                    </div>
                    <div class="footer">
                        <p>Powertech Engineering Group - Dakar, Sénégal</p>
                        <p>📞 (+221) 766432045</p>
                    </div>
                </div>
            </body>
            </html>
        """, numeroCommande, dateCommande, produitsHtml.toString(), lienDevis, lienRejet);

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(senderEmail, senderName);
            helper.setReplyTo(senderEmail);
            helper.setTo(emailFournisseur);
            helper.setSubject("📋 Demande de devis - Powertech - " + numeroCommande);
            helper.setText(corps, true);
            mailSender.send(message);

            System.out.println("✅ Email de demande de devis envoyé à: " + emailFournisseur);
        } catch (Exception e) {
            System.err.println("❌ Erreur envoi email demande de devis: " + e.getMessage());
            e.printStackTrace();
        }
    }
    public void envoyerNotificationRejetDemande(String emailFournisseur, String numeroCommande, String commentaire) {
        try {
            String corps = String.format("""
            <!DOCTYPE html>
            <html>
            <head><meta charset="UTF-8"><title>Demande rejetée</title></head>
            <body style="font-family:Arial; text-align:center;">
                <div style="max-width:500px; margin:auto; padding:20px; border:1px solid #e2e8f0; border-radius:12px;">
                    <h2 style="color:#ef4444;">❌ Demande de devis rejetée</h2>
                    <p>Le fournisseur a rejeté la demande de devis pour la commande <strong>%s</strong>.</p>
                    <p><strong>Raison :</strong> %s</p>
                </div>
            </body>
            </html>
        """, numeroCommande, (commentaire == null || commentaire.isBlank()) ? "Aucune raison fournie" : commentaire);

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(senderEmail, senderName);
            helper.setTo(emailFournisseur); // à remplacer par l'email du stock manager si différent
            helper.setSubject("❌ Demande de devis rejetée - " + numeroCommande);
            helper.setText(corps, true);
            mailSender.send(message);
        } catch (Exception e) {
            System.err.println("Erreur envoi notification rejet: " + e.getMessage());
        }
    }

}