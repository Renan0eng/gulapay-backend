package br.unipar.foodservice.services;

import br.unipar.foodservice.dtos.ClienteCreateRequest;
import br.unipar.foodservice.dtos.ClientePatchRequest;
import br.unipar.foodservice.dtos.ClienteUpdateRequest;
import br.unipar.foodservice.dtos.EnderecoClienteRequest;
import br.unipar.foodservice.entities.Cliente;
import br.unipar.foodservice.exceptions.BusinessException;
import br.unipar.foodservice.exceptions.InvalidRequestException;
import br.unipar.foodservice.exceptions.ResourceNotFoundException;
import br.unipar.foodservice.repositories.ClienteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Service de Cliente.
 *
 * <p>Desde a Sprint 2 (Sessão 17), Cliente não tem mais endereço embutido —
 * a entidade {@code EnderecoCliente} é 1:N e gerenciada pelo
 * {@link EnderecoClienteService}. Para conveniência do caller (e seguindo o
 * padrão "embutido" da Sessão 16), o {@link #criar(ClienteCreateRequest)}
 * ainda aceita uma lista opcional de endereços no payload — eles são
 * persistidos na mesma transação.
 */
@Service
@RequiredArgsConstructor
public class ClienteService {

    /** Teto de resultados da busca por nome — protege o autocomplete do app. */
    public static final int LIMITE_BUSCA_POR_NOME = 20;

    private final ClienteRepository repository;
    private final EnderecoClienteService enderecoClienteService;

    @Transactional
    public Cliente criar(ClienteCreateRequest request) {
        String telefone = normalizarTelefone(request.telefone());
        if (repository.existsByTelefone(telefone)) {
            throw new BusinessException("Já existe um cliente com o telefone " + telefone + ".");
        }
        Cliente cliente = Cliente.builder()
                .nome(request.nome())
                .telefone(telefone)
                .email(request.email())
                .ativo(true)
                .build();
        cliente = repository.save(cliente);

        List<EnderecoClienteRequest> enderecos = request.enderecos();
        if (enderecos != null && !enderecos.isEmpty()) {
            adicionarEnderecosEmbutidos(cliente, enderecos);
        }
        return cliente;
    }

    @Transactional(readOnly = true)
    public List<Cliente> listar(boolean apenasAtivos) {
        return apenasAtivos ? repository.findByAtivoTrue() : repository.findAll();
    }

    @Transactional(readOnly = true)
    public Cliente buscarPorId(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Cliente não encontrado: " + id));
    }

    /**
     * Busca clientes cujo nome contenha o termo informado (case-insensitive),
     * retornando no máximo {@value #LIMITE_BUSCA_POR_NOME} registros por
     * requisição. Pensado para o autocomplete de cliente na abertura de
     * comanda — por isso o teto fixo, sem paginação.
     *
     * @param termoBusca trecho do nome; {@code %} e {@code _} são tratados
     *                   como texto literal (escapados antes da query).
     */
    @Transactional(readOnly = true)
    public List<Cliente> buscarPorNome(String termoBusca, boolean apenasAtivos) {
        String termo = termoBusca == null ? "" : termoBusca.trim();
        if (termo.isEmpty()) {
            throw new InvalidRequestException(
                    "Informe ao menos 1 caractere em 'nome' para buscar clientes.");
        }
        return repository.buscarPorNomeContendo(
                escaparCuringasLike(termo),
                apenasAtivos,
                PageRequest.of(0, LIMITE_BUSCA_POR_NOME));
    }

    /**
     * Lookup por identidade: telefone é a chave de identificação do cliente
     * (CLAUDE.md seção 3.6), então a ausência é tratada como recurso
     * inexistente (404), simétrico a {@link #buscarPorId(Long)}.
     */
    @Transactional(readOnly = true)
    public Cliente buscarPorTelefone(String telefone) {
        String normalizado = normalizarTelefone(telefone);
        return repository.findByTelefone(normalizado)
                .orElseThrow(() -> new ResourceNotFoundException("Cliente não encontrado para o telefone: " + normalizado));
    }

    @Transactional
    public Cliente atualizar(Long id, ClienteUpdateRequest request) {
        Cliente cliente = buscarPorId(id);
        String telefone = normalizarTelefone(request.telefone());
        if (!cliente.getTelefone().equals(telefone) && repository.existsByTelefone(telefone)) {
            throw new BusinessException("Já existe outro cliente com o telefone " + telefone + ".");
        }
        cliente.setNome(request.nome());
        cliente.setTelefone(telefone);
        cliente.setEmail(request.email());
        cliente.setAtivo(request.ativo());
        return cliente;
    }

    @Transactional
    public void inativar(Long id) {
        Cliente cliente = buscarPorId(id);
        cliente.setAtivo(false);
    }

    /**
     * Atualização parcial. Telefone informado é re-normalizado e revalidado
     * quanto à unicidade. Desde a Sprint 2 (Sessão 17), não trata mais
     * endereço — usar os endpoints {@code /clientes/{id}/enderecos} e
     * {@code /enderecos/{id}}.
     */
    @Transactional
    public Cliente patch(Long id, ClientePatchRequest req) {
        Cliente cliente = buscarPorId(id);
        if (req.nome() != null) cliente.setNome(req.nome());
        if (req.telefone() != null) {
            String tel = normalizarTelefone(req.telefone());
            if (!cliente.getTelefone().equals(tel) && repository.existsByTelefone(tel)) {
                throw new BusinessException("Já existe outro cliente com o telefone " + tel + ".");
            }
            cliente.setTelefone(tel);
        }
        if (req.email() != null) cliente.setEmail(req.email());
        if (req.ativo() != null) cliente.setAtivo(req.ativo());
        return cliente;
    }

    /**
     * Persiste a lista de endereços vinda no payload de criação. Regras:
     * <ul>
     *   <li>1 endereço: vira automaticamente o principal.</li>
     *   <li>≥2 endereços: exatamente um precisa ter {@code principal=true}.
     *       Se nenhum vier marcado, o primeiro é promovido. Se mais de um
     *       vier marcado, {@link BusinessException}.</li>
     * </ul>
     */
    private void adicionarEnderecosEmbutidos(Cliente cliente, List<EnderecoClienteRequest> enderecos) {
        long marcadosPrincipal = enderecos.stream()
                .filter(e -> Boolean.TRUE.equals(e.principal()))
                .count();
        if (marcadosPrincipal > 1) {
            throw new BusinessException(
                    "Apenas um endereço pode ser marcado como principal por cliente.");
        }

        int indicePrincipal = -1;
        if (marcadosPrincipal == 1) {
            for (int i = 0; i < enderecos.size(); i++) {
                if (Boolean.TRUE.equals(enderecos.get(i).principal())) {
                    indicePrincipal = i;
                    break;
                }
            }
        } else {
            // Nenhum marcado explicitamente → promove o primeiro.
            indicePrincipal = 0;
        }

        for (int i = 0; i < enderecos.size(); i++) {
            EnderecoClienteRequest req = enderecos.get(i);
            boolean forcarPrincipal = (i == indicePrincipal);
            enderecoClienteService.adicionarParaClienteExistente(cliente, req, forcarPrincipal);
        }
    }

    /**
     * Neutraliza os curingas do LIKE ({@code %} e {@code _}) para que sejam
     * casados como texto literal. Usa {@code !} como caractere de escape —
     * o mesmo declarado no {@code ESCAPE} da query do repository. O próprio
     * {@code !} é escapado primeiro, senão um termo com {@code !} quebraria
     * os escapes seguintes.
     */
    private String escaparCuringasLike(String termo) {
        return termo.replace("!", "!!")
                .replace("%", "!%")
                .replace("_", "!_");
    }

    /**
     * Remove tudo que não for dígito do telefone para uso canônico (chave única,
     * link wa.me). O usuário pode digitar com formatação; armazenamos limpo.
     */
    private String normalizarTelefone(String entrada) {
        String somenteDigitos = entrada == null ? "" : entrada.replaceAll("\\D", "");
        if (somenteDigitos.length() < 8 || somenteDigitos.length() > 15) {
            throw new BusinessException("Telefone inválido após normalização: '" + somenteDigitos + "'.");
        }
        return somenteDigitos;
    }
}
