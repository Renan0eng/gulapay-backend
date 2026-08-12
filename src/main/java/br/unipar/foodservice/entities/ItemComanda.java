package br.unipar.foodservice.entities;

import br.unipar.foodservice.enums.MotivoCancelamentoItem;
import br.unipar.foodservice.enums.StatusItemComanda;
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
 * Linha de uma {@link Comanda}. Preço congelado no momento do lançamento
 * (RN-ITEM-04 do BACKLOG).
 *
 * <p>Item nasce em {@link StatusItemComanda#EM_PREPARO} (seção 5.2.1 do
 * CLAUDE.md — decisão da Sessão 6 — sem etapa manual de envio à produção).
 *
 * <p>Quando o item é transferido para outra comanda, um novo registro é
 * criado no destino com {@link #itemOrigem} apontando para este, e este
 * passa a status {@link StatusItemComanda#TRANSFERIDO}.
 */
@Entity
@Table(name = "item_comanda")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ItemComanda extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "comanda_id", nullable = false)
    private Comanda comanda;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "produto_id", nullable = false)
    private Produto produto;

    @Column(nullable = false, precision = 10, scale = 3)
    private BigDecimal quantidade;

    /** Congelado no lançamento; alterações de tabela de preço não afetam. */
    @Column(name = "preco_unitario", nullable = false, precision = 10, scale = 2)
    private BigDecimal precoUnitario;

    @Column(name = "valor_desconto", nullable = false, precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal valorDesconto = BigDecimal.ZERO;

    @Column(name = "valor_acrescimo", nullable = false, precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal valorAcrescimo = BigDecimal.ZERO;

    /** quantidade * precoUnitario - valorDesconto + valorAcrescimo. */
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal subtotal;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    @Builder.Default
    private StatusItemComanda status = StatusItemComanda.EM_PREPARO;

    @Enumerated(EnumType.STRING)
    @Column(name = "motivo_cancelamento", length = 30)
    private MotivoCancelamentoItem motivoCancelamento;

    @Column(columnDefinition = "TEXT")
    private String observacao;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "item_origem_id")
    private ItemComanda itemOrigem;

    /** Garçom, caixa ou admin que lançou o item. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lancado_por", nullable = false)
    private Usuario lancadoPor;

    @Column(name = "data_lancamento", nullable = false)
    private LocalDateTime dataLancamento;

    /** Última mudança de status — útil para timeline e relatórios. */
    @Column(name = "data_status", nullable = false)
    private LocalDateTime dataStatus;

    @OneToMany(mappedBy = "itemComanda", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @OrderBy("dataHora DESC")
    @Builder.Default
    private List<EventoItemComanda> eventos = new ArrayList<>();
}
