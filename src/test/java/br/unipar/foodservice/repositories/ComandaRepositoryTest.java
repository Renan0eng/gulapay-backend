package br.unipar.foodservice.repositories;

import br.unipar.foodservice.configs.JpaAuditingConfig;
import br.unipar.foodservice.enums.StatusComanda;
import br.unipar.foodservice.enums.TipoOrigemComanda;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regressão da query {@code ComandaRepository.filtrar}.
 *
 * <p>No PostgreSQL, o padrão {@code (:param IS NULL OR ...)} com parâmetro
 * {@code LocalDateTime} nulo falhava com
 * {@code 42P18 — could not determine data type of parameter}, porque o
 * driver envia o null sem tipo. A query passou a usar
 * {@code CAST(:param AS timestamp)} no teste de nulidade; estes cenários
 * garantem que a HQL continua parseando e executando com e sem filtros.
 *
 * <p>Flyway fica desligado aqui (o schema sai das entidades via
 * {@code create-drop}) porque a migration V13 usa índice parcial,
 * suportado pelo PostgreSQL mas não pelo H2.
 */
@DataJpaTest
@Import(JpaAuditingConfig.class)
@TestPropertySource(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class ComandaRepositoryTest {

    @Autowired
    private ComandaRepository repository;

    @Test
    void filtrar_comTodosOsFiltrosNulos_executaSemErro() {
        assertThat(repository.filtrar(null, null, null, null, null, null, null))
                .isEmpty();
    }

    @Test
    void filtrar_comDatasPreenchidas_executaSemErro() {
        LocalDateTime inicio = LocalDateTime.now().minusDays(1);
        LocalDateTime fim = LocalDateTime.now();

        assertThat(repository.filtrar(
                StatusComanda.ABERTA, TipoOrigemComanda.MESA,
                1L, 1L, 1L, inicio, fim))
                .isEmpty();
    }

    @Test
    void filtrar_apenasComDataInicio_executaSemErro() {
        assertThat(repository.filtrar(
                null, null, null, null, null,
                LocalDateTime.now().minusDays(30), null))
                .isEmpty();
    }
}
