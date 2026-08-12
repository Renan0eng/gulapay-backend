package br.unipar.foodservice.repositories;

import br.unipar.foodservice.entities.EnderecoCliente;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EnderecoClienteRepository extends JpaRepository<EnderecoCliente, Long> {

    /** Lista todos (inclui inativos) de um cliente, ordenados pelo principal primeiro. */
    @Query("""
        SELECT e
          FROM EnderecoCliente e
         WHERE e.cliente.id = :clienteId
         ORDER BY e.principal DESC, e.id ASC
        """)
    List<EnderecoCliente> findByClienteId(@Param("clienteId") Long clienteId);

    /** Lista apenas os endereços ativos de um cliente. */
    @Query("""
        SELECT e
          FROM EnderecoCliente e
         WHERE e.cliente.id = :clienteId
           AND e.ativo = TRUE
         ORDER BY e.principal DESC, e.id ASC
        """)
    List<EnderecoCliente> findAtivosByClienteId(@Param("clienteId") Long clienteId);

    /** Retorna o endereço principal ativo de um cliente, se existir. */
    @Query("""
        SELECT e
          FROM EnderecoCliente e
         WHERE e.cliente.id = :clienteId
           AND e.principal = TRUE
           AND e.ativo = TRUE
        """)
    Optional<EnderecoCliente> findPrincipalByClienteId(@Param("clienteId") Long clienteId);

    /**
     * Desmarca o flag {@code principal} de todos os endereços ativos de um
     * cliente. Usado antes de marcar um novo como principal — garante a
     * invariante "no máximo 1 principal ativo por cliente".
     */
    @Modifying
    @Query("""
        UPDATE EnderecoCliente e
           SET e.principal = FALSE
         WHERE e.cliente.id = :clienteId
           AND e.principal = TRUE
        """)
    int desmarcarPrincipalDoCliente(@Param("clienteId") Long clienteId);
}
