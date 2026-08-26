package br.unipar.foodservice.repositories;

import br.unipar.foodservice.configs.JpaAuditingConfig;
import br.unipar.foodservice.entities.Cliente;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Teste de integração da JPQL de {@link ClienteRepository#buscarPorNomeContendo}.
 *
 * <p>O {@code ClienteServiceTest} mocka o repository e por isso não exercita
 * a query — este teste roda a JPQL de verdade contra o H2, cobrindo o
 * {@code LIKE ... ESCAPE}, o filtro booleano e o teto vindo do {@code Pageable}.
 *
 * <p>Flyway fica desabilitado aqui e o schema é gerado a partir das entidades
 * ({@code ddl-auto=create-drop}): a migration V13 usa índice parcial
 * ({@code CREATE INDEX ... WHERE ...}), sintaxe que o H2 não aceita — bug
 * pré-existente, registrado no CLAUDE.md seção 8, que também derruba o
 * {@code FoodServiceApplicationTests.contextLoads}.
 *
 * <p>{@code @AutoConfigureTestDatabase(replace = NONE)} + perfil {@code test}
 * mantêm o H2 do {@code application-test.yml} com {@code MODE=PostgreSQL} —
 * sem isso o {@code @DataJpaTest} troca por um H2 nativo e o Hibernate emite
 * {@code insert ... returning id}, que aquele modo não aceita.
 */
// @EnableJpaAuditing já vem da FoodServiceApplication (classe de configuração
// usada pelo @DataJpaTest); aqui só importamos o bean `auditorAware` que ela
// referencia por nome — repetir a anotação quebra o contexto com
// BeanDefinitionOverrideException em 'jpaAuditingHandler'.
@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JpaAuditingConfig.class)
@TestPropertySource(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class ClienteRepositoryTest {

    @Autowired
    private ClienteRepository repository;

    @BeforeEach
    void seed() {
        repository.saveAll(List.of(
                cliente("Ana Paula", "44999990001", true),
                cliente("Mariana Souza", "44999990002", true),
                cliente("Ana Carolina", "44999990003", false),
                cliente("Bruno Lima", "44999990004", true),
                cliente("Desconto 50% Cliente", "44999990005", true)
        ));
    }

    private Cliente cliente(String nome, String telefone, boolean ativo) {
        return Cliente.builder().nome(nome).telefone(telefone).ativo(ativo).build();
    }

    @Test
    void buscarPorNomeContendo_encontraNoMeioDoNome_ignorandoCaixa() {
        List<Cliente> encontrados = repository.buscarPorNomeContendo(
                "ana", false, PageRequest.of(0, 20));

        // "Ana Paula", "Mariana Souza" (match no meio) e "Ana Carolina" (inativa).
        assertThat(encontrados).extracting(Cliente::getNome)
                .containsExactly("Ana Carolina", "Ana Paula", "Mariana Souza");
    }

    @Test
    void buscarPorNomeContendo_comApenasAtivos_filtraInativos() {
        List<Cliente> encontrados = repository.buscarPorNomeContendo(
                "ana", true, PageRequest.of(0, 20));

        assertThat(encontrados).extracting(Cliente::getNome)
                .containsExactly("Ana Paula", "Mariana Souza")
                .doesNotContain("Ana Carolina");
    }

    @Test
    void buscarPorNomeContendo_respeitaOTetoDoPageable() {
        List<Cliente> encontrados = repository.buscarPorNomeContendo(
                "a", false, PageRequest.of(0, 2));

        assertThat(encontrados).hasSize(2);
    }

    @Test
    void buscarPorNomeContendo_curingaEscapadoEhTratadoComoTextoLiteral() {
        // '!%' = '%' literal (mesmo escape que o ClienteService aplica).
        List<Cliente> comEscape = repository.buscarPorNomeContendo(
                "50!%", false, PageRequest.of(0, 20));
        assertThat(comEscape).extracting(Cliente::getNome)
                .containsExactly("Desconto 50% Cliente");

        // Sem nenhum caractere literal correspondente, não casa ninguém.
        List<Cliente> semMatch = repository.buscarPorNomeContendo(
                "99!%", false, PageRequest.of(0, 20));
        assertThat(semMatch).isEmpty();
    }

    @Test
    void buscarPorNomeContendo_semResultado_retornaListaVazia() {
        List<Cliente> encontrados = repository.buscarPorNomeContendo(
                "zzzzz", false, PageRequest.of(0, 20));

        assertThat(encontrados).isEmpty();
    }
}
