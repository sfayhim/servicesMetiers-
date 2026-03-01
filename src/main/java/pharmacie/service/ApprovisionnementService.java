package pharmacie.service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sendgrid.Method;
import com.sendgrid.Request;
import com.sendgrid.Response;
import com.sendgrid.SendGrid;
import com.sendgrid.helpers.mail.Mail;
import com.sendgrid.helpers.mail.objects.Content;
import com.sendgrid.helpers.mail.objects.Email;

import lombok.extern.slf4j.Slf4j;
import pharmacie.dao.FournisseurRepository;
import pharmacie.dao.MedicamentRepository;
import pharmacie.entity.Categorie;
import pharmacie.entity.Fournisseur;
import pharmacie.entity.Medicament;

@Slf4j
@Service
public class ApprovisionnementService {

    private final MedicamentRepository medicamentRepository;
    private final FournisseurRepository fournisseurRepository;

    @Value("${sendgrid.api.key}")
    private String sendGridApiKey;

    @Value("${sendgrid.from.email}")
    private String fromEmail;

    @Value("${sendgrid.from.name:Pharmacie}")
    private String fromName;

    public ApprovisionnementService(MedicamentRepository medicamentRepository,
                                   FournisseurRepository fournisseurRepository) {
        this.medicamentRepository = medicamentRepository;
        this.fournisseurRepository = fournisseurRepository;
    }

    /**
     * Service métier : Envoie les demandes de réapprovisionnement aux fournisseurs
     * 
     * Règles métier :
     * - Identifie les médicaments à réapprovisionner (unitesEnStock < niveauDeReappro)
     * - Groupe les médicaments par catégorie
     * - Pour chaque fournisseur, envoie un email avec les médicaments de ses catégories à réapprovisionner
     * 
     * @return un rapport de l'envoi des emails
     */
    @Transactional(readOnly = true)
    public Map<String, Object> envoyerDemandesReapprovisionnement() {
        log.info("Service : Envoi des demandes de réapprovisionnement");

        // 1. Trouver les médicaments à réapprovisionner
        List<Medicament> medicamentsAReappro = medicamentRepository.findAll().stream()
            .filter(m -> m.getUnitesEnStock() < m.getNiveauDeReappro())
            .collect(Collectors.toList());

        log.info("Nombre de médicaments à réapprovisionner : {}", medicamentsAReappro.size());

        if (medicamentsAReappro.isEmpty()) {
            return Map.of(
                "success", true,
                "message", "Aucun médicament à réapprovisionner",
                "medicaments", 0,
                "emailsEnvoyes", 0
            );
        }

        // 2. Grouper les médicaments par catégorie
        Map<Categorie, List<Medicament>> medicamentsParCategorie = medicamentsAReappro.stream()
            .collect(Collectors.groupingBy(Medicament::getCategorie));

        // 3. Pour chaque fournisseur, préparer et envoyer l'email
        List<Fournisseur> tousLesFournisseurs = fournisseurRepository.findAll();
        int emailsEnvoyes = 0;
        List<String> erreurs = new ArrayList<>();

        for (Fournisseur fournisseur : tousLesFournisseurs) {
            // Collecter les médicaments à réapprovisionner pour ce fournisseur
            Map<Categorie, List<Medicament>> medicamentsPourFournisseur = new HashMap<>();
            
            for (Categorie categorie : fournisseur.getCategories()) {
                if (medicamentsParCategorie.containsKey(categorie)) {
                    medicamentsPourFournisseur.put(categorie, medicamentsParCategorie.get(categorie));
                }
            }

            // Si ce fournisseur peut fournir des médicaments à réapprovisionner
            if (!medicamentsPourFournisseur.isEmpty()) {
                try {
                    envoyerEmail(fournisseur, medicamentsPourFournisseur);
                    emailsEnvoyes++;
                    log.info("Email envoyé à {} ({})", fournisseur.getNom(), fournisseur.getEmail());
                } catch (Exception e) {
                    log.error("Erreur lors de l'envoi de l'email à " + fournisseur.getEmail(), e);
                    erreurs.add("Erreur pour " + fournisseur.getNom() + ": " + e.getMessage());
                }
            }
        }

        Map<String, Object> resultat = new HashMap<>();
        resultat.put("success", true);
        resultat.put("medicamentsAReapprovisionner", medicamentsAReappro.size());
        resultat.put("emailsEnvoyes", emailsEnvoyes);
        resultat.put("categories", medicamentsParCategorie.size());
        if (!erreurs.isEmpty()) {
            resultat.put("erreurs", erreurs);
        }

        return resultat;
    }

    /**
     * Envoie un email à un fournisseur avec la liste des médicaments à réapprovisionner
     */
    private void envoyerEmail(Fournisseur fournisseur, Map<Categorie, List<Medicament>> medicaments) 
            throws IOException {
        
        // Construire le contenu de l'email
        StringBuilder htmlContent = new StringBuilder();
        htmlContent.append("<html><body>");
        htmlContent.append("<h2>Demande de devis de réapprovisionnement</h2>");
        htmlContent.append("<p>Bonjour ").append(fournisseur.getNom()).append(",</p>");
        htmlContent.append("<p>Nous avons besoin de réapprovisionner les médicaments suivants :</p>");

        for (Map.Entry<Categorie, List<Medicament>> entry : medicaments.entrySet()) {
            Categorie categorie = entry.getKey();
            List<Medicament> medicamentsList = entry.getValue();

            htmlContent.append("<h3>Catégorie : ").append(categorie.getLibelle()).append("</h3>");
            htmlContent.append("<table border='1' cellpadding='5' cellspacing='0'>");
            htmlContent.append("<tr><th>Médicament</th><th>Stock actuel</th><th>Niveau de réappro</th><th>Quantité suggérée</th></tr>");

            for (Medicament medicament : medicamentsList) {
                int quantiteSuggeree = medicament.getNiveauDeReappro() * 2 - medicament.getUnitesEnStock();
                htmlContent.append("<tr>");
                htmlContent.append("<td>").append(medicament.getNom()).append("</td>");
                htmlContent.append("<td>").append(medicament.getUnitesEnStock()).append("</td>");
                htmlContent.append("<td>").append(medicament.getNiveauDeReappro()).append("</td>");
                htmlContent.append("<td>").append(quantiteSuggeree).append("</td>");
                htmlContent.append("</tr>");
            }

            htmlContent.append("</table><br>");
        }

        htmlContent.append("<p>Merci de nous faire parvenir votre devis dans les meilleurs délais.</p>");
        htmlContent.append("<p>Cordialement,<br>La Pharmacie</p>");
        htmlContent.append("</body></html>");

        // Envoyer l'email via SendGrid
        Email from = new Email(fromEmail, fromName);
        String subject = "Demande de devis de réapprovisionnement";
        Email to = new Email(fournisseur.getEmail(), fournisseur.getNom());
        Content content = new Content("text/html", htmlContent.toString());
        Mail mail = new Mail(from, subject, to, content);

        SendGrid sg = new SendGrid(sendGridApiKey);
        Request request = new Request();
        
        try {
            request.setMethod(Method.POST);
            request.setEndpoint("mail/send");
            request.setBody(mail.build());
            Response response = sg.api(request);
            
            log.info("SendGrid response status: {}", response.getStatusCode());
            
            if (response.getStatusCode() >= 400) {
                throw new IOException("SendGrid error: " + response.getBody());
            }
        } catch (IOException ex) {
            log.error("Erreur lors de l'envoi via SendGrid", ex);
            throw ex;
        }
    }

    /**
     * Retourne la liste des médicaments à réapprovisionner
     */
    @Transactional(readOnly = true)
    public List<Medicament> getMedicamentsAReapprovisionner() {
        return medicamentRepository.findAll().stream()
            .filter(m -> m.getUnitesEnStock() < m.getNiveauDeReappro())
            .collect(Collectors.toList());
    }
}
