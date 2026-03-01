package pharmacie.dao;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import pharmacie.entity.Fournisseur;

public interface FournisseurRepository extends JpaRepository<Fournisseur, Integer> {
    
    /**
     * Trouve les fournisseurs pour une catégorie donnée
     * @param codeCategorie le code de la catégorie
     * @return la liste des fournisseurs
     */
    @Query("""
        SELECT DISTINCT f FROM Fournisseur f
        JOIN f.categories c
        WHERE c.code = :codeCategorie
        """)
    List<Fournisseur> findByCategorie(Integer codeCategorie);
}
