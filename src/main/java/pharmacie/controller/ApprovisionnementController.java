package pharmacie.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import pharmacie.entity.Medicament;
import pharmacie.service.ApprovisionnementService;

@Slf4j
@RestController
@RequestMapping("/api/approvisionnement")
@Tag(name = "Approvisionnement", description = "API de gestion des approvisionnements")
public class ApprovisionnementController {

    private final ApprovisionnementService approvisionnementService;

    public ApprovisionnementController(ApprovisionnementService approvisionnementService) {
        this.approvisionnementService = approvisionnementService;
    }

    /**
     * Récupère la liste des médicaments à réapprovisionner
     */
    @GetMapping("/medicaments")
    @Operation(summary = "Liste des médicaments à réapprovisionner", 
               description = "Retourne la liste des médicaments dont le stock est inférieur au niveau de réapprovisionnement")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Liste récupérée avec succès")
    })
    public ResponseEntity<List<Medicament>> getMedicamentsAReapprovisionner() {
        log.info("API : Récupération des médicaments à réapprovisionner");
        List<Medicament> medicaments = approvisionnementService.getMedicamentsAReapprovisionner();
        return ResponseEntity.ok(medicaments);
    }

    /**
     * Envoie les demandes de réapprovisionnement aux fournisseurs
     */
    @PostMapping("/envoyer-demandes")
    @Operation(summary = "Envoyer les demandes de réapprovisionnement", 
               description = "Envoie un email personnalisé à chaque fournisseur avec les médicaments à réapprovisionner")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Emails envoyés avec succès"),
        @ApiResponse(responseCode = "500", description = "Erreur lors de l'envoi des emails")
    })
    public ResponseEntity<Map<String, Object>> envoyerDemandesReapprovisionnement() {
        log.info("API : Envoi des demandes de réapprovisionnement");
        try {
            Map<String, Object> resultat = approvisionnementService.envoyerDemandesReapprovisionnement();
            return ResponseEntity.ok(resultat);
        } catch (Exception e) {
            log.error("Erreur lors de l'envoi des demandes", e);
            return ResponseEntity.status(500).body(
                Map.of(
                    "success", false,
                    "error", e.getMessage()
                )
            );
        }
    }
}
