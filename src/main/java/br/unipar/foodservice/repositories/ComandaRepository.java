package br.unipar.foodservice.repositories;

import br.unipar.foodservice.entities.Comanda;
import br.unipar.foodservice.enums.EscopoComanda;
import br.unipar.foodservice.enums.StatusComanda;
import br.unipar.foodservice.enums.TipoOrigemComanda;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ComandaRepository extends JpaRepository<Comanda, Long> {

    boolean existsByCodigo(String codigo);

    /**
     * Existe uma {@code COMPARTILHADA} aberta para esta mesa?
     * Usado pelo service para impedir uma 2ª COMPARTILHADA simultânea
     * na mesma mesa (regra 5.2 do CLAUDE.md).
     */
    @Query("""
        SELECT (COUNT(c) > 0)
          FROM Comanda c
         WHERE c.mesa.id = :mesaId
           AND c.escopo = :escopo
           AND c.status IN (br.unipar.foodservice.enums.StatusComanda.ABERTA,
                            br.unipar.foodservice.enums.StatusComanda.AGUARDANDO_PAGAMENTO)
        """)
    boolean existsAbertaPorMesaEEscopo(@Param("mesaId") Long mesaId,
                                       @Param("escopo") EscopoComanda escopo);

    /** Comandas abertas/aguardando pagamento da mesa — usado para
     * decidir o status da Mesa quando há mutação. */
    @Query("""
        SELECT c
          FROM Comanda c
         WHERE c.mesa.id = :mesaId
           AND c.status IN (br.unipar.foodservice.enums.StatusComanda.ABERTA,
                            br.unipar.foodservice.enums.StatusComanda.AGUARDANDO_PAGAMENTO)
        """)
    List<Comanda> findAbertasOuAguardandoPorMesa(@Param("mesaId") Long mesaId);

    /** Comandas DELIVERY ativas que referenciam um endereço — usado pelo
     *  {@code EnderecoClienteService.removerEndereco} (TODO da Sprint 2). */
    @Query("""
        SELECT (COUNT(c) > 0)
          FROM Comanda c
         WHERE c.enderecoEntrega.id = :enderecoId
           AND c.status IN (br.unipar.foodservice.enums.StatusComanda.ABERTA,
                            br.unipar.foodservice.enums.StatusComanda.AGUARDANDO_PAGAMENTO)
        """)
    boolean existsAtivaPorEnderecoEntrega(@Param("enderecoId") Long enderecoId);

    /**
     * Filtro genérico para listagem. Qualquer parâmetro {@code null} é
     * ignorado. Usado pelo {@code GET /comandas} com query params.
     *
     * <p>Os parâmetros de data usam {@code CAST(... AS timestamp)} no teste
     * de nulidade: sem o cast, o PostgreSQL não consegue inferir o tipo do
     * bind quando o valor é {@code null} e falha com
     * {@code 42P18 — could not determine data type of parameter}.
     */
    @Query("""
        SELECT c
          FROM Comanda c
         WHERE (:status      IS NULL OR c.status     = :status)
           AND (:tipoOrigem  IS NULL OR c.tipoOrigem = :tipoOrigem)
           AND (:mesaId      IS NULL OR c.mesa.id    = :mesaId)
           AND (:clienteId   IS NULL OR c.cliente.id = :clienteId)
           AND (:garcomId    IS NULL OR c.garcom.id  = :garcomId)
           AND (CAST(:dataInicio AS timestamp) IS NULL OR c.dataAbertura >= :dataInicio)
           AND (CAST(:dataFim   AS timestamp) IS NULL OR c.dataAbertura <= :dataFim)
         ORDER BY c.dataAbertura DESC, c.id DESC
        """)
    List<Comanda> filtrar(@Param("status") StatusComanda status,
                          @Param("tipoOrigem") TipoOrigemComanda tipoOrigem,
                          @Param("mesaId") Long mesaId,
                          @Param("clienteId") Long clienteId,
                          @Param("garcomId") Long garcomId,
                          @Param("dataInicio") LocalDateTime dataInicio,
                          @Param("dataFim") LocalDateTime dataFim);

    /**
     * Próxima sequência diária para geração de código no canal MESA.
     * Conta quantas comandas MESA foram criadas nesta mesa hoje.
     */
    @Query("""
        SELECT COUNT(c)
          FROM Comanda c
         WHERE c.mesa.id      = :mesaId
           AND c.dataAbertura >= :inicioDoDia
           AND c.dataAbertura <  :inicioDoProximoDia
        """)
    long contarPorMesaNoDia(@Param("mesaId") Long mesaId,
                            @Param("inicioDoDia") LocalDateTime inicioDoDia,
                            @Param("inicioDoProximoDia") LocalDateTime inicioDoProximoDia);

    /**
     * Próxima sequência diária para BALCAO/DELIVERY (sem mesa).
     */
    @Query("""
        SELECT COUNT(c)
          FROM Comanda c
         WHERE c.tipoOrigem   = :tipoOrigem
           AND c.dataAbertura >= :inicioDoDia
           AND c.dataAbertura <  :inicioDoProximoDia
        """)
    long contarPorTipoNoDia(@Param("tipoOrigem") TipoOrigemComanda tipoOrigem,
                            @Param("inicioDoDia") LocalDateTime inicioDoDia,
                            @Param("inicioDoProximoDia") LocalDateTime inicioDoProximoDia);

    Optional<Comanda> findByCodigo(String codigo);
}
