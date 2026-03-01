package pharmacie.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import jakarta.validation.ConstraintViolationException;
import pharmacie.dao.CommandeRepository;
import pharmacie.dao.LigneRepository;
import pharmacie.dao.MedicamentRepository;

import java.util.NoSuchElementException;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
// Ce test est basé sur le jeu de données dans "test_data.sql"
class CommandeServiceTest {
    // Constantes pour les tests
    private static final int COMMANDE_EN_COURS = 99998;
    private static final int COMMANDE_ENVOYEE = 99999;
    private static final int MEDICAMENT_DISPONIBLE_93 = 93;
    private static final int MEDICAMENT_DISPONIBLE_94 = 94;
    private static final int MEDICAMENT_INDISPONIBLE = 97;
    private static final int MEDICAMENT_EN_COMMANDE = 98;
    private static final String DISPENSAIRE_AVEC_COMMANDES = "2COM";

    @Autowired
    private CommandeService service;

    @Autowired
    private CommandeRepository commandeDao;

    @Autowired
    private MedicamentRepository medicamentDao;

    @Autowired
    private LigneRepository ligneDao;

    // ========== Tests pour ajouterLigne ==========

    @Test
    void testAjouterLigneAvecSucces() {
        // Given: Une commande en cours et un médicament disponible
        var quantiteAvant = medicamentDao.findById(MEDICAMENT_DISPONIBLE_93).orElseThrow().getUnitesCommandees();
        var nbLignesAvant = ligneDao.findByCommandeNumero(COMMANDE_EN_COURS).size();

        // When: On ajoute une ligne
        var ligne = service.ajouterLigne(COMMANDE_EN_COURS, MEDICAMENT_DISPONIBLE_93, 10);

        // Then: La ligne est créée et le médicament a ses unités commandées incrémentées
        assertNotNull(ligne.getId());
        assertEquals(10, ligne.getQuantite());
        assertEquals(MEDICAMENT_DISPONIBLE_93, ligne.getMedicament().getReference());
        assertEquals(COMMANDE_EN_COURS, ligne.getCommande().getNumero());

        var medicament = medicamentDao.findById(MEDICAMENT_DISPONIBLE_93).orElseThrow();
        assertEquals(quantiteAvant + 10, medicament.getUnitesCommandees());

        var nbLignesApres = ligneDao.findByCommandeNumero(COMMANDE_EN_COURS).size();
        assertEquals(nbLignesAvant + 1, nbLignesApres);
    }

    @Test
    void testAjouterLigneMedicamentDejaPresent() {
        // Given: Le médicament 98 est déjà dans la commande 99998 avec quantité 16
        var medicamentRef = MEDICAMENT_EN_COMMANDE;
        var ligneExistante = ligneDao.findByCommandeAndMedicament(
            commandeDao.findById(COMMANDE_EN_COURS).orElseThrow(),
            medicamentDao.findById(medicamentRef).orElseThrow()
        ).orElseThrow();
        var quantiteInitiale = ligneExistante.getQuantite();
        var unitesCommandeesAvant = medicamentDao.findById(medicamentRef).orElseThrow().getUnitesCommandees();
        var nbLignesAvant = ligneDao.findByCommandeNumero(COMMANDE_EN_COURS).size();

        // When: On ajoute à nouveau ce médicament
        var ligne = service.ajouterLigne(COMMANDE_EN_COURS, medicamentRef, 5);

        // Then: La quantité de la ligne existante est augmentée
        assertEquals(ligneExistante.getId(), ligne.getId());
        assertEquals(quantiteInitiale + 5, ligne.getQuantite());

        var medicament = medicamentDao.findById(medicamentRef).orElseThrow();
        assertEquals(unitesCommandeesAvant + 5, medicament.getUnitesCommandees());

        // Le nombre de lignes n'a pas changé
        var nbLignesApres = ligneDao.findByCommandeNumero(COMMANDE_EN_COURS).size();
        assertEquals(nbLignesAvant, nbLignesApres);
    }

    @Test
    void testAjouterLigneCommandeDejaEnvoyee() {
        // Given: Une commande déjà envoyée
        // When/Then: On ne peut pas ajouter de ligne
        assertThrows(IllegalStateException.class, () -> {
            service.ajouterLigne(COMMANDE_ENVOYEE, MEDICAMENT_DISPONIBLE_93, 10);
        });
    }

    @Test
    void testAjouterLigneMedicamentIndisponible() {
        // Given: Un médicament indisponible
        // When/Then: On ne peut pas l'ajouter à une commande
        assertThrows(IllegalStateException.class, () -> {
            service.ajouterLigne(COMMANDE_EN_COURS, MEDICAMENT_INDISPONIBLE, 10);
        });
    }

    @Test
    void testAjouterLigneStockInsuffisant() {
        // Given: Un médicament avec stock de 100, commandées = 0
        var medicament = medicamentDao.findById(MEDICAMENT_DISPONIBLE_93).orElseThrow();
        assertEquals(100, medicament.getUnitesEnStock());

        // When/Then: On ne peut pas commander plus que le stock
        assertThrows(IllegalStateException.class, () -> {
            service.ajouterLigne(COMMANDE_EN_COURS, MEDICAMENT_DISPONIBLE_93, 101);
        });
    }

    @Test
    void testAjouterLigneQuantiteNegative() {
        // When/Then: Une quantité négative doit être rejetée
        assertThrows(ConstraintViolationException.class, () -> {
            service.ajouterLigne(COMMANDE_EN_COURS, MEDICAMENT_DISPONIBLE_93, -10);
        });
    }

    @Test
    void testAjouterLigneCommandeInexistante() {
        // When/Then: Une commande inexistante doit être rejetée
        assertThrows(NoSuchElementException.class, () -> {
            service.ajouterLigne(99999999, MEDICAMENT_DISPONIBLE_93, 10);
        });
    }

    @Test
    void testAjouterLigneMedicamentInexistant() {
        // When/Then: Un médicament inexistant doit être rejeté
        assertThrows(NoSuchElementException.class, () -> {
            service.ajouterLigne(COMMANDE_EN_COURS, 99999999, 10);
        });
    }

    // ========== Tests pour supprimerLigne ==========

    @Test
    void testSupprimerLigneAvecSucces() {
        // Given: On crée une ligne de commande
        var ligne = service.ajouterLigne(COMMANDE_EN_COURS, MEDICAMENT_DISPONIBLE_94, 10);
        var ligneId = ligne.getId();
        var medicamentRef = ligne.getMedicament().getReference();
        var unitesCommandeesAvant = medicamentDao.findById(medicamentRef).orElseThrow().getUnitesCommandees();

        // When: On supprime la ligne
        service.supprimerLigne(ligneId);

        // Then: La ligne n'existe plus et les unités commandées sont décrémentées
        assertFalse(ligneDao.findById(ligneId).isPresent());

        var medicament = medicamentDao.findById(medicamentRef).orElseThrow();
        assertEquals(unitesCommandeesAvant - 10, medicament.getUnitesCommandees());
    }

    @Test
    void testSupprimerLigneCommandeDejaEnvoyee() {
        // Given: Une ligne d'une commande déjà envoyée
        var lignes = ligneDao.findByCommandeNumero(COMMANDE_ENVOYEE);
        assertFalse(lignes.isEmpty());
        var ligneId = lignes.get(0).getId();

        // When/Then: On ne peut pas supprimer la ligne
        assertThrows(IllegalStateException.class, () -> {
            service.supprimerLigne(ligneId);
        });
    }

    @Test
    void testSupprimerLigneInexistante() {
        // When/Then: Une ligne inexistante doit être rejetée
        assertThrows(NoSuchElementException.class, () -> {
            service.supprimerLigne(99999999);
        });
    }

    // ========== Tests pour enregistreExpedition ==========

    @Test
    void testEnregistreExpeditionAvecSucces() {
        // Given: Une commande en cours avec des lignes
        var commande = commandeDao.findById(COMMANDE_EN_COURS).orElseThrow();
        assertNull(commande.getEnvoyeele());

        // Récupérer les lignes et leurs informations via le DAO pour éviter LazyInitializationException
        var lignesAvant = ligneDao.findByCommandeNumero(COMMANDE_EN_COURS);
        var stocks = lignesAvant.stream()
            .collect(java.util.stream.Collectors.toMap(
                l -> l.getMedicament().getReference(),
                l -> new int[]{
                    l.getMedicament().getUnitesEnStock(),
                    l.getMedicament().getUnitesCommandees(),
                    l.getQuantite()
                }
            ));

        // When: On enregistre l'expédition
        var commandeExpediee = service.enregistreExpedition(COMMANDE_EN_COURS);

        // Then: La date d'expédition est renseignée
        assertNotNull(commandeExpediee.getEnvoyeele());

        // Et les stocks sont mis à jour
        var lignesApres = ligneDao.findByCommandeNumero(COMMANDE_EN_COURS);
        for (var ligne : lignesApres) {
            var medicament = medicamentDao.findById(ligne.getMedicament().getReference()).orElseThrow();
            var avant = stocks.get(medicament.getReference());
            assertEquals(avant[0] - avant[2], medicament.getUnitesEnStock(), 
                "Le stock doit être décrémenté");
            assertEquals(avant[1] - avant[2], medicament.getUnitesCommandees(),
                "Les unités commandées doivent être décrémentées");
        }
    }

    @Test
    void testEnregistreExpeditionCommandeDejaEnvoyee() {
        // Given: Une commande déjà envoyée
        var commande = commandeDao.findById(COMMANDE_ENVOYEE).orElseThrow();
        assertNotNull(commande.getEnvoyeele());

        // When/Then: On ne peut pas enregistrer l'expédition à nouveau
        assertThrows(IllegalStateException.class, () -> {
            service.enregistreExpedition(COMMANDE_ENVOYEE);
        });
    }

    @Test
    void testEnregistreExpeditionCommandeInexistante() {
        // When/Then: Une commande inexistante doit être rejetée
        assertThrows(NoSuchElementException.class, () -> {
            service.enregistreExpedition(99999999);
        });
    }

    // ========== Tests d'intégration ==========

    @Test
    void testScenarioComplet() {
        // Given: On crée une nouvelle commande
        var commande = service.creerCommande(DISPENSAIRE_AVEC_COMMANDES);
        var commandeNum = commande.getNumero();

        // When: On ajoute plusieurs lignes
        var ligne1 = service.ajouterLigne(commandeNum, MEDICAMENT_DISPONIBLE_93, 5);
        var ligne2 = service.ajouterLigne(commandeNum, MEDICAMENT_DISPONIBLE_94, 10);

        // Then: La commande a 2 lignes
        assertEquals(2, ligneDao.findByCommandeNumero(commandeNum).size());

        // When: On supprime une ligne
        service.supprimerLigne(ligne1.getId());

        // Then: La commande n'a plus qu'une ligne
        assertEquals(1, ligneDao.findByCommandeNumero(commandeNum).size());

        // When: On enregistre l'expédition
        var commandeExpediee = service.enregistreExpedition(commandeNum);

        // Then: La commande est marquée comme expédiée
        assertNotNull(commandeExpediee.getEnvoyeele());

        // Et on ne peut plus ajouter de lignes
        assertThrows(IllegalStateException.class, () -> {
            service.ajouterLigne(commandeNum, MEDICAMENT_DISPONIBLE_93, 5);
        });

        // Et on ne peut plus supprimer de lignes
        assertThrows(IllegalStateException.class, () -> {
            service.supprimerLigne(ligne2.getId());
        });
    }
}
