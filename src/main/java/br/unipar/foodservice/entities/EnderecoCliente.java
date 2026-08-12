package br.unipar.foodservice.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Endereço cadastrável de um {@link Cliente}. Substitui o endereço embutido
 * que existia originalmente em {@code cliente} (refatorado na migration V13).
 *
 * <p>Um cliente pode ter 0..N endereços. Quando há pelo menos um endereço,
 * exatamente um precisa estar marcado como {@code principal=true} (regra
 * garantida pelo service — ver
 * {@code EnderecoClienteService.marcarComoPrincipal}).
 *
 * <p>Comandas do tipo {@code DELIVERY} referenciam um endereço aqui (campo
 * obrigatório no fluxo da Sprint 2).
 */
@Entity
@Table(name = "endereco_cliente")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EnderecoCliente extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cliente_id", nullable = false)
    private Cliente cliente;

    /** Rótulo livre (ex.: "Casa", "Trabalho"). Default "Principal" no banco. */
    @Column(nullable = false, length = 40)
    @Builder.Default
    private String rotulo = "Principal";

    @Column(nullable = false, length = 150)
    private String logradouro;

    @Column(length = 20)
    private String numero;

    @Column(length = 80)
    private String complemento;

    @Column(length = 80)
    private String bairro;

    @Column(nullable = false, length = 80)
    private String cidade;

    @Column(nullable = false, length = 2)
    private String uf;

    @Column(length = 10)
    private String cep;

    /** Ponto de referência (informativo, opcional). */
    @Column(columnDefinition = "TEXT")
    private String referencia;

    /**
     * Endereço principal do cliente. Quando o cliente tem ≥1 endereço,
     * exatamente um precisa estar com {@code principal=true} e
     * {@code ativo=true}.
     */
    @Column(nullable = false)
    @Builder.Default
    private Boolean principal = false;

    /** Soft delete (RNF08). */
    @Column(nullable = false)
    @Builder.Default
    private Boolean ativo = true;
}
