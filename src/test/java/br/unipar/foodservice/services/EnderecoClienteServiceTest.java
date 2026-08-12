package br.unipar.foodservice.services;

import br.unipar.foodservice.dtos.EnderecoClienteRequest;
import br.unipar.foodservice.entities.Cliente;
import br.unipar.foodservice.entities.EnderecoCliente;
import br.unipar.foodservice.exceptions.BusinessException;
import br.unipar.foodservice.exceptions.InvalidRequestException;
import br.unipar.foodservice.exceptions.ResourceNotFoundException;
import br.unipar.foodservice.repositories.ClienteRepository;
import br.unipar.foodservice.repositories.ComandaRepository;
import br.unipar.foodservice.repositories.EnderecoClienteRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Cobertura do {@link EnderecoClienteService}. Padrão Mockito + AssertJ,
 * mesma estrutura dos demais services (ver {@link ClienteServiceTest},
 * {@code MovimentacaoEstoqueServiceTest}, {@code ProdutoServiceTest}).
 *
 * <p>Cenários cobertos:
 * <ol>
 *   <li>Primeiro endereço de um cliente é automaticamente principal.</li>
 *   <li>Marcar um endereço como principal desmarca os demais (via repo).</li>
 *   <li>Adicionar para cliente inexistente → 400 (InvalidRequestException).</li>
 *   <li>Marcar endereço inativo como principal → 422 (BusinessException).</li>
 *   <li>Soft delete não tenta apagar fisicamente — apenas {@code ativo=false}.</li>
 *   <li>Buscar por id inexistente → 404 (ResourceNotFoundException).</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
class EnderecoClienteServiceTest {

    @Mock
    private EnderecoClienteRepository repository;

    @Mock
    private ClienteRepository clienteRepository;

    @Mock
    private ComandaRepository comandaRepository;

    @InjectMocks
    private EnderecoClienteService service;

    @Test
    void adicionar_primeiroEnderecoDoCliente_marcaComoPrincipal() {
        Cliente cliente = Cliente.builder().id(10L).nome("Ana").telefone("44999990001").build();
        when(clienteRepository.findById(10L)).thenReturn(Optional.of(cliente));
        when(repository.findAtivosByClienteId(10L)).thenReturn(List.of());
        when(repository.save(any(EnderecoCliente.class))).thenAnswer(inv -> inv.getArgument(0));

        EnderecoClienteRequest req = new EnderecoClienteRequest(
                "Casa", "Rua A", "100", null, "Centro",
                "Umuarama", "PR", "87500000", null, /* principal */ null);

        EnderecoCliente salvo = service.adicionar(10L, req);

        assertThat(salvo.getPrincipal()).isTrue();
        assertThat(salvo.getCliente()).isSameAs(cliente);
        // Mesmo sem principal=true no request, como é o primeiro, desmarcamos
        // antes de salvar para garantir a invariante.
        verify(repository).desmarcarPrincipalDoCliente(10L);
    }

    @Test
    void adicionar_marcandoPrincipal_desmarcaAnteriores() {
        Cliente cliente = Cliente.builder().id(11L).nome("Bruno").telefone("44999990002").build();
        EnderecoCliente existente = EnderecoCliente.builder()
                .id(1L).cliente(cliente).logradouro("Rua antiga")
                .cidade("Umuarama").uf("PR").principal(true).ativo(true).build();
        when(clienteRepository.findById(11L)).thenReturn(Optional.of(cliente));
        when(repository.findAtivosByClienteId(11L)).thenReturn(List.of(existente));
        when(repository.save(any(EnderecoCliente.class))).thenAnswer(inv -> inv.getArgument(0));

        EnderecoClienteRequest req = new EnderecoClienteRequest(
                "Trabalho", "Av B", "200", null, "Indústrias",
                "Umuarama", "pr", "87500001", null, /* principal */ true);

        EnderecoCliente salvo = service.adicionar(11L, req);

        assertThat(salvo.getPrincipal()).isTrue();
        // UF normalizada para uppercase
        assertThat(salvo.getUf()).isEqualTo("PR");
        verify(repository).desmarcarPrincipalDoCliente(11L);
    }

    @Test
    void adicionar_clienteInexistente_deveLancarInvalidRequest() {
        when(clienteRepository.findById(99L)).thenReturn(Optional.empty());

        EnderecoClienteRequest req = new EnderecoClienteRequest(
                null, "Rua X", null, null, null, "Cidade", "PR", null, null, null);

        assertThatThrownBy(() -> service.adicionar(99L, req))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("Cliente referenciado não existe: 99");
        verify(repository, never()).save(any());
    }

    @Test
    void marcarComoPrincipal_enderecoInativo_deveLancarBusiness() {
        Cliente cliente = Cliente.builder().id(20L).telefone("44999991111").build();
        EnderecoCliente endereco = EnderecoCliente.builder()
                .id(50L).cliente(cliente).logradouro("Rua Z").cidade("Umuarama").uf("PR")
                .principal(false).ativo(false).build();
        when(repository.findById(50L)).thenReturn(Optional.of(endereco));

        assertThatThrownBy(() -> service.marcarComoPrincipal(50L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("inativo");
        verify(repository, never()).desmarcarPrincipalDoCliente(any());
    }

    @Test
    void marcarComoPrincipal_enderecoAtivo_desmarcaOutrosEPromove() {
        Cliente cliente = Cliente.builder().id(21L).telefone("44999992222").build();
        EnderecoCliente endereco = EnderecoCliente.builder()
                .id(51L).cliente(cliente).logradouro("Rua Y").cidade("Umuarama").uf("PR")
                .principal(false).ativo(true).build();
        when(repository.findById(51L)).thenReturn(Optional.of(endereco));

        EnderecoCliente resultado = service.marcarComoPrincipal(51L);

        assertThat(resultado.getPrincipal()).isTrue();
        verify(repository).desmarcarPrincipalDoCliente(21L);
    }

    @Test
    void removerEndereco_aplicaSoftDelete_NaoApagaFisicamente() {
        Cliente cliente = Cliente.builder().id(30L).telefone("44999993333").build();
        EnderecoCliente endereco = EnderecoCliente.builder()
                .id(60L).cliente(cliente).logradouro("Rua W").cidade("Umuarama").uf("PR")
                .principal(true).ativo(true).build();
        when(repository.findById(60L)).thenReturn(Optional.of(endereco));
        when(comandaRepository.existsAtivaPorEnderecoEntrega(60L)).thenReturn(false);

        service.removerEndereco(60L);

        assertThat(endereco.getAtivo()).isFalse();
        // Como era o principal, é desmarcado também — cliente fica sem principal.
        assertThat(endereco.getPrincipal()).isFalse();
        verify(repository, never()).delete(any());
        verify(repository, never()).deleteById(any());
    }

    @Test
    void removerEndereco_comComandaDeliveryAtiva_deveLancarBusiness() {
        Cliente cliente = Cliente.builder().id(31L).telefone("44999994444").build();
        EnderecoCliente endereco = EnderecoCliente.builder()
                .id(61L).cliente(cliente).logradouro("Rua V").cidade("Umuarama").uf("PR")
                .principal(true).ativo(true).build();
        when(repository.findById(61L)).thenReturn(Optional.of(endereco));
        when(comandaRepository.existsAtivaPorEnderecoEntrega(61L)).thenReturn(true);

        assertThatThrownBy(() -> service.removerEndereco(61L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("DELIVERY");

        // Não deve ter inativado nada (rollback semântico — exception lançada antes do set).
        assertThat(endereco.getAtivo()).isTrue();
        assertThat(endereco.getPrincipal()).isTrue();
    }

    @Test
    void buscarPorId_inexistente_deveLancarNotFound() {
        when(repository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.buscarPorId(999L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("999");
    }

    @Test
    void atualizar_promovendoParaPrincipal_desmarcaOutros() {
        Cliente cliente = Cliente.builder().id(40L).telefone("44999994444").build();
        EnderecoCliente endereco = EnderecoCliente.builder()
                .id(70L).cliente(cliente).rotulo("Casa")
                .logradouro("Rua antiga").cidade("Umuarama").uf("PR")
                .principal(false).ativo(true).build();
        when(repository.findById(70L)).thenReturn(Optional.of(endereco));

        EnderecoClienteRequest req = new EnderecoClienteRequest(
                "Casa", "Rua nova", "10", null, "Jardim", "Umuarama", "PR",
                "87500999", "Próximo ao mercado", true);

        EnderecoCliente atualizado = service.atualizar(70L, req);

        assertThat(atualizado.getLogradouro()).isEqualTo("Rua nova");
        assertThat(atualizado.getPrincipal()).isTrue();
        ArgumentCaptor<Long> captor = ArgumentCaptor.forClass(Long.class);
        verify(repository, times(1)).desmarcarPrincipalDoCliente(captor.capture());
        assertThat(captor.getValue()).isEqualTo(40L);
    }

    @Test
    void atualizar_jaEraPrincipal_naoChamaDesmarcar() {
        Cliente cliente = Cliente.builder().id(41L).telefone("44999995555").build();
        EnderecoCliente endereco = EnderecoCliente.builder()
                .id(71L).cliente(cliente).rotulo("Casa")
                .logradouro("Rua antiga").cidade("Umuarama").uf("PR")
                .principal(true).ativo(true).build();
        when(repository.findById(71L)).thenReturn(Optional.of(endereco));

        EnderecoClienteRequest req = new EnderecoClienteRequest(
                null, "Rua nova", null, null, null, "Umuarama", "PR", null, null, true);

        EnderecoCliente atualizado = service.atualizar(71L, req);

        assertThat(atualizado.getPrincipal()).isTrue();
        // Já era principal — não precisa desmarcar nada.
        verify(repository, never()).desmarcarPrincipalDoCliente(eq(41L));
    }
}
