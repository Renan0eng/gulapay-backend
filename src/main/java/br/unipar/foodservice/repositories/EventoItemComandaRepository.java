package br.unipar.foodservice.repositories;

import br.unipar.foodservice.entities.EventoItemComanda;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EventoItemComandaRepository extends JpaRepository<EventoItemComanda, Long> {

    /** Linha do tempo de um item — ordem decrescente por {@code dataHora}. */
    @Query("""
        SELECT e
          FROM EventoItemComanda e
         WHERE e.itemComanda.id = :itemComandaId
         ORDER BY e.dataHora DESC, e.id DESC
        """)
    List<EventoItemComanda> findByItemComandaId(@Param("itemComandaId") Long itemComandaId);
}
