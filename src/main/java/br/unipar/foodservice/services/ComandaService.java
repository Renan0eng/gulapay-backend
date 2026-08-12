package br.unipar.foodservice.services;

import br.unipar.foodservice.dtos.ComandaCreateRequest;
import br.unipar.foodservice.dtos.ComandaPatchRequest;
import br.unipar.foodservice.entities.Cliente;
import br.unipar.foodservice.entities.Comanda;
import br.unipar.foodservice.entities.EnderecoCliente;
import br.unipar.foodservice.entities.ItemComanda;
import br.unipar.foodservice.entities.Mesa;
import br.unipar.foodservice.entities.Produto;
import br.unipar.foodservice.entities.Usuario;
import br.unipar.foodservice.enums.EscopoComanda;
import br.unipar.foodservice.enums.Perfil;
import br.unipar.foodservice.enums.StatusComanda;
import br.unipar.foodservice.enums.StatusItemComanda;
import br.unipar.foodservice.enums.StatusMesa;
import br.unipar.foodservice.enums.TipoOrigemComanda;
import br.unipar.foodservice.enums.TipoProduto;
import br.unipar.foodservice.exceptions.BusinessException;
import br.unipar.foodservice.exceptions.InvalidRequestException;
import br.unipar.foodservice.exceptions.ResourceNotFoundException;
import br.unipar.foodservice.repositories.ClienteRepository;
import br.unipar.foodservice.repositories.ComandaRepository;
import br.unipar.foodservice.repositories.EnderecoClienteRepository;
import br.unipar.foodservice.repositories.ItemComandaRepository;
import br.unipar.foodservice.repositories.MesaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Service da {@link Comanda} — orquestra todo o fluxo de venda da Sprint 2.
 *
 * <p>Responsabilidades:
 * <ul>
 *   <li>Validar regras de criação (RN-COM-01..07 do BACKLOG).</li>
 *   <li>Gerar {@code codigo} humano e {@code link_whatsapp}.</li>
 *   <li>Transicionar {@link StatusMesa} conforme as comandas vinculadas
 *       à mesa (regra 5.3 do CLAUDE.md).</li>
 *   <li>Recalcular os totais da comanda quando itens mudam.</li>
 *   <li>Fechar a comanda — bloqueia COMPOSTO/COMBO (Sprint 5) e dispara
 *       {@code SAIDA_VENDA} via {@link MovimentacaoEstoqueService} para
 *       cada item UNITARIO entregue.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class ComandaService {

    private final ComandaRepository repository;
    private final ClienteRepository clienteRepository;
    private final MesaRepository mesaRepository;
    private final EnderecoClienteRepository enderecoRepository;
    private final ItemComandaRepository itemRepository;
    private final UsuarioAutenticadoService usuarioAutenticadoService;
    private final MovimentacaoEstoqueService movimentacaoEstoqueService;

    // ---------------------------------------------------------------------
    // CRIAÇÃO
    // ---------------------------------------------------------------------

    @Transactional
    public Comanda criar(ComandaCreateRequest req) {
        if (req.tipoOrigem() == null) {
            throw new InvalidRequestException("tipoOrigem é obrigatório.");
        }
        Cliente cliente = clienteRepository.findById(req.clienteId())
                .orElseThrow(() -> new InvalidRequestException(
                        "Cliente referenciado não existe: " + req.clienteId()));
        if (cliente.getTelefone() == null || cliente.getTelefone().isBlank()) {
            // O cadastro já valida isso, mas reforçamos: telefone é a chave online.
            throw new BusinessException("Cliente sem telefone — obrigatório para criar comanda (RF20).");
        }

        Mesa mesa          = carregarMesaSeNecessario(req);
        Usuario garcom     = carregarGarcomSeNecessario(req);
        EnderecoCliente end = carregarEnderecoSeNecessario(req, cliente);
        Comanda pai        = carregarComandaPaiSeNecessario(req, mesa);

        EscopoComanda escopo = resolverEscopo(req);

        if (escopo == EscopoComanda.COMPARTILHADA
                && repository.existsAbertaPorMesaEEscopo(mesa.getId(), EscopoComanda.COMPARTILHADA)) {
            throw new BusinessException(
                    "Já existe uma comanda COMPARTILHADA aberta na mesa " + mesa.getNumero() + ".");
        }

        LocalDateTime agora = LocalDateTime.now();
        String codigo = gerarCodigo(req.tipoOrigem(), mesa, agora);
        String linkWhatsApp = "https://wa.me/" + cliente.getTelefone();

        Comanda comanda = Comanda.builder()
                .codigo(codigo)
                .tipoOrigem(req.tipoOrigem())
                .escopo(escopo)
                .status(StatusComanda.ABERTA)
                .cliente(cliente)
                .mesa(mesa)
                .comandaPai(pai)
                .garcom(garcom)
                .enderecoEntrega(end)
                .observacao(req.observacao())
                .linkWhatsApp(linkWhatsApp)
                .totalBruto(BigDecimal.ZERO)
                .totalDescontos(BigDecimal.ZERO)
                .totalAcrescimos(BigDecimal.ZERO)
                .totalLiquido(BigDecimal.ZERO)
                .dataAbertura(agora)
                .build();
        comanda = repository.save(comanda);

        // Mesa LIVRE → OCUPADA quando a primeira comanda da mesa é criada.
        if (mesa != null && mesa.getStatus() == StatusMesa.LIVRE) {
            mesa.setStatus(StatusMesa.OCUPADA);
        }
        return comanda;
    }

    private Mesa carregarMesaSeNecessario(ComandaCreateRequest req) {
        if (req.tipoOrigem() == TipoOrigemComanda.MESA) {
            if (req.mesaId() == null) {
                throw new BusinessException("mesaId é obrigatório quando tipoOrigem=MESA.");
            }
            Mesa mesa = mesaRepository.findById(req.mesaId())
                    .orElseThrow(() -> new InvalidRequestException(
                            "Mesa referenciada não existe: " + req.mesaId()));
            if (!Boolean.TRUE.equals(mesa.getAtivo())) {
                throw new BusinessException("Mesa " + mesa.getNumero() + " está inativa.");
            }
            return mesa;
        }
        if (req.mesaId() != null) {
            throw new BusinessException("mesaId só é permitido quando tipoOrigem=MESA.");
        }
        return null;
    }

    private Usuario carregarGarcomSeNecessario(ComandaCreateRequest req) {
        if (req.tipoOrigem() == TipoOrigemComanda.MESA) {
            if (req.garcomId() == null) {
                throw new BusinessException("garcomId é obrigatório quando tipoOrigem=MESA.");
            }
            Usuario garcom = usuarioAutenticadoService.carregar(req.garcomId());
            if (garcom.getPerfil() != Perfil.GARCOM) {
                throw new BusinessException(
                        "Usuário " + garcom.getLogin() + " não é GARCOM (perfil atual: "
                                + garcom.getPerfil() + ").");
            }
            return garcom;
        }
        return req.garcomId() == null ? null : usuarioAutenticadoService.carregar(req.garcomId());
    }

    private EnderecoCliente carregarEnderecoSeNecessario(ComandaCreateRequest req, Cliente cliente) {
        if (req.tipoOrigem() == TipoOrigemComanda.DELIVERY) {
            if (req.enderecoEntregaId() == null) {
                throw new BusinessException(
                        "enderecoEntregaId é obrigatório quando tipoOrigem=DELIVERY.");
            }
            EnderecoCliente end = enderecoRepository.findById(req.enderecoEntregaId())
                    .orElseThrow(() -> new InvalidRequestException(
                            "Endereço referenciado não existe: " + req.enderecoEntregaId()));
            if (!end.getCliente().getId().equals(cliente.getId())) {
                throw new BusinessException(
                        "Endereço " + end.getId() + " não pertence ao cliente " + cliente.getId() + ".");
            }
            if (!Boolean.TRUE.equals(end.getAtivo())) {
                throw new BusinessException("Endereço " + end.getId() + " está inativo.");
            }
            return end;
        }
        if (req.enderecoEntregaId() != null) {
            throw new BusinessException(
                    "enderecoEntregaId só é permitido quando tipoOrigem=DELIVERY.");
        }
        return null;
    }

    private Comanda carregarComandaPaiSeNecessario(ComandaCreateRequest req, Mesa mesa) {
        if (req.comandaPaiId() == null) return null;
        if (req.tipoOrigem() != TipoOrigemComanda.MESA) {
            throw new BusinessException("comandaPaiId só faz sentido em tipoOrigem=MESA.");
        }
        Comanda pai = repository.findById(req.comandaPaiId())
                .orElseThrow(() -> new InvalidRequestException(
                        "Comanda pai não existe: " + req.comandaPaiId()));
        if (pai.getEscopo() != EscopoComanda.COMPARTILHADA) {
            throw new BusinessException(
                    "Comanda pai " + pai.getCodigo() + " precisa ser COMPARTILHADA.");
        }
        if (mesa == null || pai.getMesa() == null
                || !pai.getMesa().getId().equals(mesa.getId())) {
            throw new BusinessException(
                    "Comanda pai precisa estar na mesma mesa da filha.");
        }
        return pai;
    }

    private EscopoComanda resolverEscopo(ComandaCreateRequest req) {
        // BALCAO/DELIVERY: força INDIVIDUAL (RN-COM-03). Qualquer valor enviado é ignorado.
        if (req.tipoOrigem() != TipoOrigemComanda.MESA) return EscopoComanda.INDIVIDUAL;
        return req.escopo() == null ? EscopoComanda.INDIVIDUAL : req.escopo();
    }

    // ---------------------------------------------------------------------
    // LEITURA
    // ---------------------------------------------------------------------

    @Transactional(readOnly = true)
    public Comanda buscarPorId(Long id) {
        Comanda c = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Comanda não encontrada: " + id));
        Usuario corrente = usuarioAutenticadoService.usuarioCorrente();
        if (usuarioAutenticadoService.ehGarcom(corrente)
                && (c.getGarcom() == null || !c.getGarcom().getId().equals(corrente.getId()))) {
            throw new AccessDeniedException(
                    "Garçom só pode visualizar as próprias comandas.");
        }
        return c;
    }

    @Transactional(readOnly = true)
    public List<Comanda> listar(StatusComanda status,
                                TipoOrigemComanda tipoOrigem,
                                Long mesaId,
                                Long clienteId,
                                Long garcomId,
                                LocalDateTime dataInicio,
                                LocalDateTime dataFim) {
        Usuario corrente = usuarioAutenticadoService.usuarioCorrente();
        // Garçom só lista as próprias — qualquer outro garcomId no filtro é sobrescrito.
        Long garcomFiltro = usuarioAutenticadoService.ehGarcom(corrente) ? corrente.getId() : garcomId;
        return repository.filtrar(status, tipoOrigem, mesaId, clienteId, garcomFiltro,
                dataInicio, dataFim);
    }

    // ---------------------------------------------------------------------
    // EDIÇÃO PARCIAL
    // ---------------------------------------------------------------------

    @Transactional
    public Comanda patch(Long id, ComandaPatchRequest req) {
        Comanda comanda = buscarPorId(id);
        if (comanda.getStatus() != StatusComanda.ABERTA) {
            throw new BusinessException(
                    "Apenas comandas ABERTAS podem ser editadas (status atual: "
                            + comanda.getStatus() + ").");
        }
        Usuario corrente = usuarioAutenticadoService.usuarioCorrente();

        if (req.observacao() != null) {
            // Garçom dono, caixa, admin podem editar observação.
            if (usuarioAutenticadoService.ehGarcom(corrente)
                    && (comanda.getGarcom() == null
                        || !comanda.getGarcom().getId().equals(corrente.getId()))) {
                throw new AccessDeniedException(
                        "Garçom só pode editar comandas próprias.");
            }
            comanda.setObservacao(req.observacao());
        }
        if (req.garcomId() != null) {
            if (!usuarioAutenticadoService.ehAdminOuCaixa(corrente)) {
                throw new AccessDeniedException(
                        "Apenas Caixa/Admin podem reatribuir o garçom da comanda.");
            }
            Usuario novoGarcom = usuarioAutenticadoService.carregar(req.garcomId());
            if (novoGarcom.getPerfil() != Perfil.GARCOM) {
                throw new BusinessException(
                        "Usuário " + novoGarcom.getLogin() + " não é GARCOM.");
            }
            comanda.setGarcom(novoGarcom);
        }
        if (req.enderecoEntregaId() != null) {
            if (!usuarioAutenticadoService.ehAdminOuCaixa(corrente)) {
                throw new AccessDeniedException(
                        "Apenas Caixa/Admin podem alterar o endereço de entrega.");
            }
            if (comanda.getTipoOrigem() != TipoOrigemComanda.DELIVERY) {
                throw new BusinessException(
                        "Endereço de entrega só se aplica a tipoOrigem=DELIVERY.");
            }
            EnderecoCliente novoEnd = enderecoRepository.findById(req.enderecoEntregaId())
                    .orElseThrow(() -> new InvalidRequestException(
                            "Endereço não encontrado: " + req.enderecoEntregaId()));
            if (!novoEnd.getCliente().getId().equals(comanda.getCliente().getId())) {
                throw new BusinessException(
                        "Endereço " + novoEnd.getId() + " não pertence ao cliente da comanda.");
            }
            comanda.setEnderecoEntrega(novoEnd);
        }
        return comanda;
    }

    // ---------------------------------------------------------------------
    // CICLO DE VIDA — CANCELAR / REABRIR / FECHAR
    // ---------------------------------------------------------------------

    @Transactional
    public Comanda cancelar(Long id) {
        Usuario corrente = usuarioAutenticadoService.usuarioCorrente();
        if (!usuarioAutenticadoService.ehAdminOuCaixa(corrente)) {
            throw new AccessDeniedException(
                    "Apenas Caixa/Admin podem cancelar comandas.");
        }
        Comanda comanda = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Comanda não encontrada: " + id));
        if (comanda.getStatus() != StatusComanda.ABERTA
                && comanda.getStatus() != StatusComanda.AGUARDANDO_PAGAMENTO) {
            throw new BusinessException(
                    "Comanda em status " + comanda.getStatus() + " não pode ser cancelada.");
        }
        comanda.setStatus(StatusComanda.CANCELADA);
        comanda.setDataFechamento(LocalDateTime.now());
        atualizarStatusMesa(comanda.getMesa());
        return comanda;
    }

    @Transactional
    public Comanda reabrir(Long id) {
        Usuario corrente = usuarioAutenticadoService.usuarioCorrente();
        if (!usuarioAutenticadoService.ehAdministrador(corrente)) {
            throw new AccessDeniedException(
                    "Apenas Administradores podem reabrir uma comanda.");
        }
        Comanda comanda = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Comanda não encontrada: " + id));
        if (comanda.getStatus() != StatusComanda.AGUARDANDO_PAGAMENTO) {
            throw new BusinessException(
                    "Só é possível reabrir uma comanda em AGUARDANDO_PAGAMENTO.");
        }
        comanda.setStatus(StatusComanda.ABERTA);
        comanda.setDataFechamento(null);
        // Mesa não muda — continua OCUPADA enquanto há comanda ABERTA.
        if (comanda.getMesa() != null) {
            comanda.getMesa().setStatus(StatusMesa.OCUPADA);
        }
        return comanda;
    }

    /**
     * Fecha a comanda (RN-COM-FEC-01..05).
     *
     * <p>Bloqueia se há item em {@code EM_PREPARO} ou se algum item entregue
     * é COMPOSTO/COMBO (Sprint 5). Para cada item UNITARIO entregue,
     * dispara {@code SAIDA_VENDA} via {@link MovimentacaoEstoqueService}.
     */
    @Transactional
    public Comanda fechar(Long id) {
        Usuario corrente = usuarioAutenticadoService.usuarioCorrente();
        if (!usuarioAutenticadoService.ehAdminOuCaixa(corrente)) {
            throw new AccessDeniedException(
                    "Apenas Caixa/Admin podem fechar uma comanda.");
        }
        Comanda comanda = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Comanda não encontrada: " + id));
        if (comanda.getStatus() != StatusComanda.ABERTA) {
            throw new BusinessException(
                    "Só é possível fechar comandas ABERTAS (status atual: "
                            + comanda.getStatus() + ").");
        }
        if (itemRepository.existsEmPreparoNaComanda(comanda.getId())) {
            throw new BusinessException(
                    "Há itens em EM_PREPARO — marque como ENTREGUE ou cancele antes de fechar.");
        }
        List<ItemComanda> compostos = itemRepository.findEntreguesCompostosOuCombos(comanda.getId());
        if (!compostos.isEmpty()) {
            throw new BusinessException(
                    "Itens compostos/combos serão suportados na Sprint 5 (Ficha Técnica). "
                            + "Bloqueio em " + compostos.size() + " item(ns) da comanda.");
        }

        // Dispara SAIDA_VENDA para cada item UNITARIO entregue.
        List<ItemComanda> entregues = itemRepository.findByComandaIdAndStatus(
                comanda.getId(), StatusItemComanda.ENTREGUE);
        for (ItemComanda item : entregues) {
            Produto produto = item.getProduto();
            if (produto.getTipoProduto() != TipoProduto.UNITARIO) continue; // já bloqueado acima
            if (produto.getInsumo() == null) {
                throw new BusinessException(
                        "Produto " + produto.getNome()
                                + " (UNITARIO) está sem insumo — não é possível dar baixa.");
            }
            String justificativa = "SAIDA_VENDA — comanda " + comanda.getCodigo()
                    + " — item #" + item.getId();
            movimentacaoEstoqueService.registrarSaidaVenda(
                    produto.getInsumo().getId(),
                    produto.getInsumo().getUnidadePadrao().getId(),
                    item.getQuantidade(),
                    justificativa);
        }

        comanda.setStatus(StatusComanda.AGUARDANDO_PAGAMENTO);
        comanda.setDataFechamento(LocalDateTime.now());
        atualizarStatusMesa(comanda.getMesa());
        return comanda;
    }

    // ---------------------------------------------------------------------
    // HELPERS DE INTEGRAÇÃO (chamados pelo ItemComandaService)
    // ---------------------------------------------------------------------

    /**
     * Recalcula os 4 totais da comanda a partir dos itens não-cancelados e
     * não-transferidos. Chamado pelo {@link ItemComandaService} sempre que
     * um item é criado, editado ou cancelado.
     */
    @Transactional
    public void recalcularTotais(Comanda comanda) {
        BigDecimal bruto = BigDecimal.ZERO;
        BigDecimal desc  = BigDecimal.ZERO;
        BigDecimal acr   = BigDecimal.ZERO;
        BigDecimal liq   = BigDecimal.ZERO;

        for (ItemComanda i : itemRepository.findByComandaId(comanda.getId())) {
            if (i.getStatus() == StatusItemComanda.CANCELADO
                    || i.getStatus() == StatusItemComanda.TRANSFERIDO) continue;
            BigDecimal brutoItem = i.getQuantidade().multiply(i.getPrecoUnitario());
            bruto = bruto.add(brutoItem);
            desc  = desc.add(i.getValorDesconto() == null ? BigDecimal.ZERO : i.getValorDesconto());
            acr   = acr.add(i.getValorAcrescimo() == null ? BigDecimal.ZERO : i.getValorAcrescimo());
            liq   = liq.add(i.getSubtotal());
        }
        comanda.setTotalBruto(bruto);
        comanda.setTotalDescontos(desc);
        comanda.setTotalAcrescimos(acr);
        comanda.setTotalLiquido(liq);
    }

    /**
     * Mantém o {@link StatusMesa} consistente com as comandas vinculadas
     * (regra 5.3 do CLAUDE.md). Chamado em qualquer transição de status
     * de Comanda em canal MESA.
     */
    @Transactional
    public void atualizarStatusMesa(Mesa mesa) {
        if (mesa == null) return;
        List<Comanda> abertasOuAguardando = repository.findAbertasOuAguardandoPorMesa(mesa.getId());
        boolean temAberta = abertasOuAguardando.stream()
                .anyMatch(c -> c.getStatus() == StatusComanda.ABERTA);
        boolean temAguardando = abertasOuAguardando.stream()
                .anyMatch(c -> c.getStatus() == StatusComanda.AGUARDANDO_PAGAMENTO);

        if (temAberta) {
            mesa.setStatus(StatusMesa.OCUPADA);
        } else if (temAguardando) {
            mesa.setStatus(StatusMesa.AGUARDANDO_PAGAMENTO);
        } else {
            mesa.setStatus(StatusMesa.LIVRE);
        }
    }

    // ---------------------------------------------------------------------
    // GERAÇÃO DE CÓDIGO HUMANO
    // ---------------------------------------------------------------------

    /**
     * Gera o código humano da comanda. Padrões:
     * <ul>
     *   <li>MESA: {@code M-<mesa>-<seq_dia>} — ex.: {@code M-12-001}.</li>
     *   <li>BALCAO: {@code B-<seq_dia>} — ex.: {@code B-0001}.</li>
     *   <li>DELIVERY: {@code D-<seq_dia>} — ex.: {@code D-0001}.</li>
     * </ul>
     *
     * <p>Para evitar colisão sob concorrência, em caso de duplicidade
     * (raro) o método tenta sequências subsequentes até encontrar uma
     * livre — preferimos isso a usar {@code @Retryable} por conta da
     * simplicidade.
     */
    private String gerarCodigo(TipoOrigemComanda tipo, Mesa mesa, LocalDateTime agora) {
        LocalDateTime inicioDia = agora.toLocalDate().atStartOfDay();
        LocalDateTime inicioProximoDia = inicioDia.plusDays(1);

        long seq;
        String prefixo;
        if (tipo == TipoOrigemComanda.MESA) {
            seq = repository.contarPorMesaNoDia(mesa.getId(), inicioDia, inicioProximoDia);
            prefixo = "M-" + sanear(mesa.getNumero());
        } else if (tipo == TipoOrigemComanda.BALCAO) {
            seq = repository.contarPorTipoNoDia(TipoOrigemComanda.BALCAO, inicioDia, inicioProximoDia);
            prefixo = "B";
        } else { // DELIVERY
            seq = repository.contarPorTipoNoDia(TipoOrigemComanda.DELIVERY, inicioDia, inicioProximoDia);
            prefixo = "D";
        }
        // Tenta até encontrar um código livre (caso 2 comandas sejam criadas no mesmo ms).
        for (int tentativa = 0; tentativa < 100; tentativa++) {
            String candidato = prefixo + "-" + formatarSeq(seq + 1 + tentativa);
            if (!repository.existsByCodigo(candidato)) return candidato;
        }
        // Fallback paranoico — usa timestamp.
        return prefixo + "-" + LocalDate.now() + "-" + System.nanoTime();
    }

    private String sanear(String numero) {
        // Mantém só dígitos/letras — códigos não devem ter espaço ou hífen no meio.
        return numero == null ? "0" : numero.replaceAll("[^a-zA-Z0-9]", "");
    }

    private String formatarSeq(long seq) {
        return String.format("%03d", seq);
    }
}
