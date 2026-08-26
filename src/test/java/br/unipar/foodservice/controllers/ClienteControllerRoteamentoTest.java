package br.unipar.foodservice.controllers;

import br.unipar.foodservice.entities.Cliente;
import br.unipar.foodservice.exceptions.GlobalExceptionHandler;
import br.unipar.foodservice.exceptions.ResourceNotFoundException;
import br.unipar.foodservice.services.ClienteService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Garante que o roteamento por {@code params} do {@code GET /clientes} manda
 * cada combinação de filtros para o handler certo.
 *
 * <p>Esse roteamento substituiu os {@code if} que existiam dentro do
 * controller: a escolha da consulta virou condição de mapeamento. Handler
 * errado e ambiguidade de mapeamento só aparecem em runtime — os testes de
 * service não pegariam.
 *
 * <p>Usa {@code standaloneSetup} em vez de {@code @WebMvcTest}: o roteamento
 * é resolvido pelo mesmo {@code RequestMappingHandlerMapping}, sem precisar
 * subir contexto Spring — que hoje falharia, porque o {@code @EnableJpaAuditing}
 * da {@code FoodServiceApplication} exige um metamodelo JPA que a camada web
 * sozinha não tem.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ClienteControllerRoteamentoTest {

    @Mock
    private ClienteService service;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new ClienteController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private Cliente cliente(long id, String nome, String telefone) {
        return Cliente.builder().id(id).nome(nome).telefone(telefone).ativo(true).build();
    }

    @Test
    void comTelefone_vaiParaBuscaExata_eRetornaObjetoUnico() throws Exception {
        when(service.buscarPorTelefone("44999991234")).thenReturn(cliente(1L, "Ana", "44999991234"));

        mockMvc.perform(get("/clientes").param("telefone", "44999991234"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.nome").value("Ana"));

        verify(service, never()).buscarPorNome(anyString(), anyBoolean());
        verify(service, never()).listar(anyBoolean());
    }

    @Test
    void telefoneInexistente_continuaRetornando404() throws Exception {
        when(service.buscarPorTelefone("44900000000"))
                .thenThrow(new ResourceNotFoundException("Cliente não encontrado para o telefone: 44900000000"));

        mockMvc.perform(get("/clientes").param("telefone", "44900000000"))
                .andExpect(status().isNotFound());
    }

    @Test
    void comNome_vaiParaBuscaParcial_eRetornaArray() throws Exception {
        when(service.buscarPorNome("ana", true))
                .thenReturn(List.of(cliente(1L, "Ana", "44999991234"), cliente(2L, "Mariana", "44999995678")));

        mockMvc.perform(get("/clientes").param("nome", "ana").param("apenasAtivos", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(2));

        verify(service, never()).buscarPorTelefone(anyString());
        verify(service, never()).listar(anyBoolean());
    }

    @Test
    void comTelefoneENome_telefoneTemPrecedencia() throws Exception {
        when(service.buscarPorTelefone("44999991234")).thenReturn(cliente(1L, "Ana", "44999991234"));

        mockMvc.perform(get("/clientes")
                        .param("telefone", "44999991234")
                        .param("nome", "bruno"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));

        verify(service, never()).buscarPorNome(anyString(), anyBoolean());
    }

    @Test
    void semFiltros_vaiParaListagem() throws Exception {
        when(service.listar(true)).thenReturn(List.of(cliente(1L, "Ana", "44999991234")));

        mockMvc.perform(get("/clientes").param("apenasAtivos", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(1));

        verify(service).listar(eq(true));
        verify(service, never()).buscarPorTelefone(anyString());
        verify(service, never()).buscarPorNome(anyString(), anyBoolean());
    }
}
