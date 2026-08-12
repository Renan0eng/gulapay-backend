package br.unipar.foodservice.repositories;

import br.unipar.foodservice.entities.ItemComanda;
import br.unipar.foodservice.enums.StatusItemComanda;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ItemComandaRepository extends JpaRepository<ItemComanda, Long> {

    /** Todos os itens de uma comanda (qualquer status). */
    @Query("""
        SELECT i
          FROM ItemComanda i
         WHERE i.comanda.id = :comandaId
         ORDER BY i.id ASC
        """)
    List<ItemComanda> findByComandaId(@Param("comandaId") Long comandaId);

    /** Itens de uma comanda em determinado status — útil para pré-fechamento. */
    @Query("""
        SELECT i
          FROM ItemComanda i
         WHERE i.comanda.id = :comandaId
           AND i.status     = :status
        """)
    List<ItemComanda> findByComandaIdAndStatus(@Param("comandaId") Long comandaId,
                                               @Param("status") StatusItemComanda status);

    /**
     * Existe pelo menos um item da comanda em {@code EM_PREPARO}?
     * Usado pelo service para bloquear o fechamento (RN-COM-FEC-02).
     */
    @Query("""
        SELECT (COUNT(i) > 0)
          FROM ItemComanda i
         WHERE i.comanda.id = :comandaId
           AND i.status     = br.unipar.foodservice.enums.StatusItemComanda.EM_PREPARO
        """)
    boolean existsEmPreparoNaComanda(@Param("comandaId") Long comandaId);

    /**
     * Itens entregues da comanda que apontam para um produto que ainda
     * não tem suporte ao fechamento (COMPOSTO/COMBO). Usado para o erro
     * "destravado na Sprint 5" (RN-COM-FEC-03).
     */
    @Query("""
        SELECT i
          FROM ItemComanda i
         WHERE i.comanda.id = :comandaId
           AND i.status     = br.unipar.foodservice.enums.StatusItemComanda.ENTREGUE
           AND i.produto.tipoProduto IN (br.unipar.foodservice.enums.TipoProduto.COMPOSTO,
                                          br.unipar.foodservice.enums.TipoProduto.COMBO)
        """)
    List<ItemComanda> findEntreguesCompostosOuCombos(@Param("comandaId") Long comandaId);
}
