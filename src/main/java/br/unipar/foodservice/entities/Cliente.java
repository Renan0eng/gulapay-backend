package br.unipar.foodservice.entities;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * Cliente do estabelecimento — identificado pelo telefone (chave única).
 *
 * <p>Desde a Sprint 2 (Sessão 17 / migration V13), o endereço deixou de ser
 * embutido e passou a ser uma entidade {@link EnderecoCliente} em
 * relacionamento 1:N. Cliente pode ter 0..N endereços; quando tem ≥1,
 * exatamente um precisa estar marcado como
 * {@code EnderecoCliente.principal=true} (regra garantida pelo service).
 */
@Entity
@Table(name = "cliente")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Cliente extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String nome;

    /** Apenas dígitos (DDI+DDD+número), normalizado pelo service. */
    @Column(nullable = false, unique = true, length = 20)
    private String telefone;

    @Column(length = 120)
    private String email;

    @Column(nullable = false)
    @Builder.Default
    private Boolean ativo = true;

    /**
     * Endereços cadastrados. Lista navegada por conveniência interna —
     * para CRUD use {@code EnderecoClienteService} e os endpoints
     * {@code /clientes/{id}/enderecos} / {@code /enderecos/{id}}.
     */
    @OneToMany(mappedBy = "cliente", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @OrderBy("principal DESC, id ASC")
    @Builder.Default
    private List<EnderecoCliente> enderecos = new ArrayList<>();
}
