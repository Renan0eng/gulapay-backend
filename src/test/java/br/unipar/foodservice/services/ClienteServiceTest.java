package br.unipar.foodservice.services;

import br.unipar.foodservice.dtos.ClienteCreateRequest;
import br.unipar.foodservice.entities.Cliente;
import br.unipar.foodservice.exceptions.BusinessException;
import br.unipar.foodservice.exceptions.InvalidRequestException;
import br.unipar.foodservice.repositories.ClienteRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClienteServiceTest {

    @Mock
    private ClienteRepository repository;

    @Mock
    private EnderecoClienteService enderecoClienteService;

    @InjectMocks
    private ClienteService service;

    @Test
    void criar_deveNormalizarTelefoneRemovendoFormatacao() {
        ClienteCreateRequest req = new ClienteCreateRequest(
                "João Cliente", "+55 (44) 99999-1234", null, null);
        when(repository.existsByTelefone("5544999991234")).thenReturn(false);
        when(repository.save(any(Cliente.class))).thenAnswer(inv -> inv.getArgument(0));

        Cliente salvo = service.criar(req);

        assertThat(salvo.getTelefone()).isEqualTo("5544999991234");
        assertThat(salvo.getNome()).isEqualTo("João Cliente");
        assertThat(salvo.getAtivo()).isTrue();
    }

    @Test
    void criar_telefoneDuplicado_deveLancar() {
        ClienteCreateRequest req = new ClienteCreateRequest(
                "Maria", "44999991234", null, null);
        when(repository.existsByTelefone("44999991234")).thenReturn(true);

        assertThatThrownBy(() -> service.criar(req))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("44999991234");
    }

    @Test
    void criar_telefoneCurtoDemais_deveLancar() {
        ClienteCreateRequest req = new ClienteCreateRequest(
                "Curto", "1234", null, null);

        assertThatThrownBy(() -> service.criar(req))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Telefone inválido");
    }

    // ---------------------------------------------------------------
    // Busca por nome (containing, teto de 20)
    // ---------------------------------------------------------------

    @Test
    void buscarPorNome_deveLimitarA20ResultadosEPassarTermoTrimado() {
        Cliente ana = Cliente.builder().id(1L).nome("Ana").telefone("44999991234").ativo(true).build();
        when(repository.buscarPorNomeContendo(anyString(), anyBoolean(), any(Pageable.class)))
                .thenReturn(List.of(ana));

        List<Cliente> encontrados = service.buscarPorNome("  ana  ", true);

        ArgumentCaptor<String> termo = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(repository).buscarPorNomeContendo(termo.capture(), any(Boolean.class), pageable.capture());

        assertThat(termo.getValue()).isEqualTo("ana");
        assertThat(pageable.getValue().getPageNumber()).isZero();
        assertThat(pageable.getValue().getPageSize()).isEqualTo(ClienteService.LIMITE_BUSCA_POR_NOME);
        assertThat(encontrados).containsExactly(ana);
    }

    @Test
    void buscarPorNome_deveEscaparCuringasDoLike() {
        when(repository.buscarPorNomeContendo(anyString(), anyBoolean(), any(Pageable.class)))
                .thenReturn(List.of());

        service.buscarPorNome("100%_ok!", false);

        ArgumentCaptor<String> termo = ArgumentCaptor.forClass(String.class);
        verify(repository).buscarPorNomeContendo(termo.capture(), any(Boolean.class), any(Pageable.class));

        // '!' é escapado primeiro, senão quebraria os escapes de '%' e '_'.
        assertThat(termo.getValue()).isEqualTo("100!%!_ok!!");
    }

    @Test
    void buscarPorNome_termoEmBranco_deveLancar400() {
        assertThatThrownBy(() -> service.buscarPorNome("   ", true))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("nome");

        verify(repository, never()).buscarPorNomeContendo(anyString(), anyBoolean(), any(Pageable.class));
    }

}
