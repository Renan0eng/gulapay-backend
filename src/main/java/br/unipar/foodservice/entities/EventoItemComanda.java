package br.unipar.foodservice.entities;

import br.unipar.foodservice.enums.AcaoEventoItem;
import br.unipar.foodservice.enums.MotivoCancelamentoItem;
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
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Auditoria de mutações em {@link ItemComanda} (RNF09).
 *
 * <p>Toda alteração relevante em um item (criação, edição, transferência,
 * cancelamento, marcação como entregue) cria um registro aqui com:
 *
 * <ul>
 *   <li>{@link #acao} — qual operação ocorreu;</li>
 *   <li>{@link #usuario} — quem executou (extraído do SecurityContext);</li>
 *   <li>{@link #motivo} — preenchido apenas em cancelamentos;</li>
 *   <li>{@link #valorAntes} / {@link #valorDepois} — snapshots JSON
 *       serializados como String (compatível com H2 e PostgreSQL — ver V16).</li>
 * </ul>
 *
 * <p>Acessível via {@code GET /itens-comanda/{id}/eventos} (Sprint 2).
 */
@Entity
@Table(name = "evento_item_comanda")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EventoItemComanda extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "item_comanda_id", nullable = false)
    private ItemComanda itemComanda;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AcaoEventoItem acao;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private MotivoCancelamentoItem motivo;

    /** Snapshot JSON serializado como string (TEXT no banco). */
    @Column(name = "valor_antes", columnDefinition = "TEXT")
    private String valorAntes;

    /** Snapshot JSON serializado como string (TEXT no banco). */
    @Column(name = "valor_depois", columnDefinition = "TEXT")
    private String valorDepois;

    @Column(name = "data_hora", nullable = false)
    private LocalDateTime dataHora;
}
