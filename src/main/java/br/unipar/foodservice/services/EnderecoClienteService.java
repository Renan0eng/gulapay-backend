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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Gerencia o cadastro de endereços de cliente (entidade 1:N).
 *
 * <p>Garante a invariante "no máximo 1 endereço {@code principal=true} ativo
 * por cliente": ao salvar um endereço marcado como principal, todos os
 * outros do mesmo cliente são desmarcados na mesma transação.
 *
 * <p>Soft delete (RNF08) bloqueia exclusão se houver comanda DELIVERY ativa
 * (ABERTA ou AGUARDANDO_PAGAMENTO) referenciando o endereço — verificado
 * via {@code ComandaRepository.existsAtivaPorEnderecoEntrega} a partir da
 * Sprint 2 (rodada de DTOs+Services).
 */
@Service
@RequiredArgsConstructor
public class EnderecoClienteService {

    private final EnderecoClienteRepository repository;
    private final ClienteRepository clienteRepository;
    private final ComandaRepository comandaRepository;

    /**
     * Adiciona um novo endereço a um cliente existente.
     *
     * <p>Se o request marca {@code principal=true} (ou se for o primeiro
     * endereço do cliente), aplica a invariante de unicidade de principal.
     */
    @Transactional
    public EnderecoCliente adicionar(Long clienteId, EnderecoClienteRequest req) {
        Cliente cliente = clienteRepository.findById(clienteId)
                .orElseThrow(() -> new InvalidRequestException(
                        "Cliente referenciado não existe: " + clienteId));

        boolean primeiroDoCliente = repository.findAtivosByClienteId(clienteId).isEmpty();
        boolean ehPrincipal = Boolean.TRUE.equals(req.principal()) || primeiroDoCliente;

        if (ehPrincipal) {
            repository.desmarcarPrincipalDoCliente(clienteId);
        }

        EnderecoCliente endereco = EnderecoCliente.builder()
                .cliente(cliente)
                .rotulo(req.rotulo() == null || req.rotulo().isBlank() ? "Principal" : req.rotulo())
                .logradouro(req.logradouro())
                .numero(req.numero())
                .complemento(req.complemento())
                .bairro(req.bairro())
                .cidade(req.cidade())
                .uf(normalizarUf(req.uf()))
                .cep(req.cep())
                .referencia(req.referencia())
                .principal(ehPrincipal)
                .ativo(true)
                .build();
        return repository.save(endereco);
    }

    /**
     * Versão para uso interno pelo {@link ClienteService} quando o cliente é
     * criado com endereços embutidos no payload. Não toca em
     * {@link ClienteRepository}: o cliente já vem persistido em memória.
     */
    @Transactional
    public EnderecoCliente adicionarParaClienteExistente(Cliente cliente,
                                                          EnderecoClienteRequest req,
                                                          boolean forcarPrincipal) {
        boolean ehPrincipal = forcarPrincipal || Boolean.TRUE.equals(req.principal());

        if (ehPrincipal) {
            repository.desmarcarPrincipalDoCliente(cliente.getId());
        }

        EnderecoCliente endereco = EnderecoCliente.builder()
                .cliente(cliente)
                .rotulo(req.rotulo() == null || req.rotulo().isBlank() ? "Principal" : req.rotulo())
                .logradouro(req.logradouro())
                .numero(req.numero())
                .complemento(req.complemento())
                .bairro(req.bairro())
                .cidade(req.cidade())
                .uf(normalizarUf(req.uf()))
                .cep(req.cep())
                .referencia(req.referencia())
                .principal(ehPrincipal)
                .ativo(true)
                .build();
        return repository.save(endereco);
    }

    @Transactional(readOnly = true)
    public List<EnderecoCliente> listarPorCliente(Long clienteId, boolean apenasAtivos) {
        garantirClienteExiste(clienteId);
        return apenasAtivos
                ? repository.findAtivosByClienteId(clienteId)
                : repository.findByClienteId(clienteId);
    }

    @Transactional(readOnly = true)
    public EnderecoCliente buscarPorId(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Endereço não encontrado: " + id));
    }

    @Transactional
    public EnderecoCliente atualizar(Long id, EnderecoClienteRequest req) {
        EnderecoCliente endereco = buscarPorId(id);

        boolean queremosPrincipal = Boolean.TRUE.equals(req.principal());
        if (queremosPrincipal && !Boolean.TRUE.equals(endereco.getPrincipal())) {
            repository.desmarcarPrincipalDoCliente(endereco.getCliente().getId());
        }

        endereco.setRotulo(req.rotulo() == null || req.rotulo().isBlank() ? endereco.getRotulo() : req.rotulo());
        endereco.setLogradouro(req.logradouro());
        endereco.setNumero(req.numero());
        endereco.setComplemento(req.complemento());
        endereco.setBairro(req.bairro());
        endereco.setCidade(req.cidade());
        endereco.setUf(normalizarUf(req.uf()));
        endereco.setCep(req.cep());
        endereco.setReferencia(req.referencia());
        if (queremosPrincipal) {
            endereco.setPrincipal(true);
        }
        return endereco;
    }

    /**
     * Marca explicitamente um endereço como principal, desmarcando os demais
     * do mesmo cliente.
     */
    @Transactional
    public EnderecoCliente marcarComoPrincipal(Long id) {
        EnderecoCliente endereco = buscarPorId(id);
        if (!Boolean.TRUE.equals(endereco.getAtivo())) {
            throw new BusinessException("Não é possível marcar um endereço inativo como principal.");
        }
        repository.desmarcarPrincipalDoCliente(endereco.getCliente().getId());
        endereco.setPrincipal(true);
        return endereco;
    }

    /**
     * Soft delete (RNF08). Bloqueia se houver comanda ativa (ABERTA ou
     * AGUARDANDO_PAGAMENTO) referenciando este endereço — preserva a
     * integridade da auditoria/relatórios da Sprint 2.
     */
    @Transactional
    public void removerEndereco(Long id) {
        EnderecoCliente endereco = buscarPorId(id);
        if (comandaRepository.existsAtivaPorEnderecoEntrega(id)) {
            throw new BusinessException(
                    "Não é possível remover este endereço — há comanda(s) DELIVERY ativa(s) "
                            + "(ABERTA ou AGUARDANDO_PAGAMENTO) referenciando.");
        }
        endereco.setAtivo(false);
        // Se era o principal, deixa o cliente sem principal — outro precisa
        // ser marcado manualmente via PUT/PATCH ou novo cadastro.
        if (Boolean.TRUE.equals(endereco.getPrincipal())) {
            endereco.setPrincipal(false);
        }
    }

    private void garantirClienteExiste(Long clienteId) {
        if (!clienteRepository.existsById(clienteId)) {
            throw new ResourceNotFoundException("Cliente não encontrado: " + clienteId);
        }
    }

    private String normalizarUf(String uf) {
        return uf == null ? null : uf.trim().toUpperCase();
    }
}
