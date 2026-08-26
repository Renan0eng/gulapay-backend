package br.unipar.foodservice.repositories;

import br.unipar.foodservice.entities.Cliente;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ClienteRepository extends JpaRepository<Cliente, Long> {

    Optional<Cliente> findByTelefone(String telefone);

    boolean existsByTelefone(String telefone);

    List<Cliente> findByAtivoTrue();

    /**
     * Busca "contendo" por nome, case-insensitive, para autocomplete de
     * cliente na abertura de comanda.
     *
     * <p>O teto de resultados vem do {@link Pageable} — JPQL não tem
     * {@code LIMIT}. O caller usa {@code PageRequest.of(0, N)}; ver
     * {@code ClienteService.buscarPorNome}.
     *
     * <p>{@code ESCAPE '!'}: o termo é escapado no service antes de chegar
     * aqui, para que {@code %} e {@code _} digitados pelo usuário sejam
     * tratados como texto literal e não como curingas do LIKE.
     */
    @Query("""
        SELECT c
          FROM Cliente c
         WHERE LOWER(c.nome) LIKE LOWER(CONCAT('%', :nome, '%')) ESCAPE '!'
           AND (:apenasAtivos = FALSE OR c.ativo = TRUE)
         ORDER BY c.nome ASC, c.id ASC
        """)
    List<Cliente> buscarPorNomeContendo(@Param("nome") String nome,
                                        @Param("apenasAtivos") boolean apenasAtivos,
                                        Pageable pageable);
}
