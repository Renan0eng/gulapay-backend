package br.unipar.foodservice.entities;

import br.unipar.foodservice.enums.EscopoComanda;
import br.unipar.foodservice.enums.StatusComanda;
import br.unipar.foodservice.enums.TipoOrigemComanda;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Comanda — unidade de venda nos canais MESA, BALCAO e DELIVERY
 * (Modelo A, seção 5.2 do CLAUDE.md).
 *
 * <p>Itens, pagamentos (Sprint 4) e auditoria são filhos desta entidade —
 * nunca de Mesa. Quando uma comanda fecha, ela <i>é</i> a venda.
 *
 * <p>Hierarquia: uma {@code COMPARTILHADA} pode ter N {@code INDIVIDUAL}
 * filhas via {@link #individuais}. O rateio entre elas é responsabilidade
 * da Sprint 3.
 */
@Entity
@Table(name = "comanda")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Comanda extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Código humano gerado pelo service (ex.: "M-12-001"). */
    @Column(nullable = false, unique = true, length = 20)
    private String codigo;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_origem", nullable = false, length = 10)
    private TipoOrigemComanda tipoOrigem;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    @Builder.Default
    private EscopoComanda escopo = EscopoComanda.INDIVIDUAL;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 25)
    @Builder.Default
    private StatusComanda status = StatusComanda.ABERTA;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cliente_id", nullable = false)
    private Cliente cliente;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "mesa_id")
    private Mesa mesa;

    /** Quando esta é INDIVIDUAL, aponta para a COMPARTILHADA da mesa. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "comanda_pai_id")
    private Comanda comandaPai;

    /** Lista de individuais filhas (válido para COMPARTILHADA). */
    @OneToMany(mappedBy = "comandaPai", fetch = FetchType.LAZY)
    @Builder.Default
    private List<Comanda> individuais = new ArrayList<>();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "garcom_id")
    private Usuario garcom;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "entregador_id")
    private Entregador entregador;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "endereco_entrega_id")
    private EnderecoCliente enderecoEntrega;

    @Column(columnDefinition = "TEXT")
    private String observacao;

    /** {@code https://wa.me/<somente_digitos(cliente.telefone)>}. */
    @Column(name = "link_whatsapp", length = 200)
    private String linkWhatsApp;

    @OneToMany(mappedBy = "comanda", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @OrderBy("id ASC")
    @Builder.Default
    private List<ItemComanda> itens = new ArrayList<>();

    @Column(name = "total_bruto", nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal totalBruto = BigDecimal.ZERO;

    @Column(name = "total_descontos", nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal totalDescontos = BigDecimal.ZERO;

    @Column(name = "total_acrescimos", nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal totalAcrescimos = BigDecimal.ZERO;

    @Column(name = "total_liquido", nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal totalLiquido = BigDecimal.ZERO;

    @Column(name = "data_abertura", nullable = false)
    private LocalDateTime dataAbertura;

    @Column(name = "data_fechamento")
    private LocalDateTime dataFechamento;
}
