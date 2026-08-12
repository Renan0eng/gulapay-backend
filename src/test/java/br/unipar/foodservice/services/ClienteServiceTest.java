package br.unipar.foodservice.services;

import br.unipar.foodservice.dtos.ClienteCreateRequest;
import br.unipar.foodservice.entities.Cliente;
import br.unipar.foodservice.exceptions.BusinessException;
import br.unipar.foodservice.repositories.ClienteRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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
}
