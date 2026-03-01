package pharmacie.entity;

import java.util.LinkedHashSet;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

@Entity
@Getter
@Setter
@NoArgsConstructor
@RequiredArgsConstructor
@ToString
public class Fournisseur {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Setter(AccessLevel.NONE)
    private Integer id;

    @NonNull
    @NotBlank
    @Size(min = 1, max = 100)
    @Column(unique = true, nullable = false, length = 100)
    private String nom;

    @NonNull
    @NotBlank
    @Email
    @Size(max = 100)
    @Column(nullable = false, length = 100)
    private String email;

    @ToString.Exclude
    @ManyToMany(mappedBy = "fournisseurs")
    @JsonIgnoreProperties({"fournisseurs", "medicaments"})
    private Set<Categorie> categories = new LinkedHashSet<>();
}
