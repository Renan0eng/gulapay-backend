package br.unipar.foodservice.services;

import br.unipar.foodservice.dtos.ItemComandaCancelRequest;
import br.unipar.foodservice.dtos.ItemComandaCreateRequest;
import br.unipar.foodservice.dtos.ItemComandaTransferRequest;
import br.unipar.foodservice.dtos.ItemComandaUpdateRequest;
import br.unipar.foodservice.entities.Comanda;
import br.unipar.foodservice.entities.EventoItemComanda;
import br.unipar.foodservice.entities.ItemComanda;
import br.unipar.foodservice.entities.Produto;
import br.unipar.foodservice.entities.Usuario;
import br.unipar.foodservice.enums.AcaoEventoItem;
import br.unipar.foodservice.enums.MotivoCancelamentoItem;
import br.unipar.foodservice.enums.StatusComanda;
import br.unipar.foodservice.enums.StatusItemComanda;
import br.unipar.foodservice.enums.TipoOrigemComanda;
import br.unipar.foodservice.exceptions.BusinessException;
import br.unipar.foodservice.exceptions.InvalidRequestException;
import br.unipar.foodservice.exceptions.ResourceNotFoundException;
import br.unipar.foodservice.repositories.ComandaRepository;
import br.unipar.foodservice.repositories.EventoItemComandaRepository;
import br.unipar.foodservice.repositories.ItemComandaRepository;
import br.unipar.foodservice.repositories.ProdutoRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Service do {@link ItemComanda}. Implementa as 5 operações da Sprint 2
 * (criar, editar, marcar entregue, transferir, cancelar) com auditoria
 * automática via {@link EventoItemComanda} e a matriz de permissões da
 * seção 5.2.1 do CLAUDE.md.
 *
 * <p>Item nasce em {@link StatusItemComanda#EM_PREPARO} (decisão da
 * Sessão 6 — sem etapa manual de envio à produção, impressão térmica
 * dispara automaticamente). Garçom marca como ENTREGUE ao servir.
 */
@Service
@RequiredArgsConstructor
public class ItemComandaService {

    private final ItemComandaRepository repository;
    private final EventoItemComandaRepository eventoRepository;
    private final ComandaRepository comandaRepository;
    private final ProdutoRepository produtoRepository;
    private final UsuarioAutenticadoService usuarioAutenticadoService;
    private final ComandaService comandaService;
    private final ObjectMapper objectMapper;

    // ---------------------------------------------------------------------
    // CRIAÇÃO
    // ---------------------------------------------------------------------

    @Transactional
    public ItemComanda criar(Long comandaId, ItemComandaCreateRequest req) {
        Comanda comanda = comandaRepository.findById(comandaId)
                .orElseThrow(() -> new ResourceNotFoundException("Comanda não encontrada: " + comandaId));
        if (comanda.getStatus() != StatusComanda.ABERTA) {
            throw new BusinessException(
                    "Só é possível adicionar itens em comandas ABERTAS (status atual: "
                            + comanda.getStatus() + ").");
        }
        Usuario corrente = usuarioAutenticadoService.usuarioCorrente();
        garantirAcessoAComanda(comanda, corrente, "lançar item");

        Produto produto = produtoRepository.findById(req.produtoId())
                .orElseThrow(() -> new InvalidRequestException(
                        "Produto referenciado não existe: " + req.produtoId()));
        if (!Boolean.TRUE.equals(produto.getAtivo())) {
            throw new BusinessException("Produto " + produto.getNome() + " está inativo.");
        }

        BigDecimal precoUnitario = produto.getPreco();
        BigDecimal desconto = req.valorDesconto() == null ? BigDecimal.ZERO : req.valorDesconto();
        BigDecimal acrescimo = req.valorAcrescimo() == null ? BigDecimal.ZERO : req.valorAcrescimo();
        BigDecimal subtotal = calcularSubtotal(req.quantidade(), precoUnitario, desconto, acrescimo);

        LocalDateTime agora = LocalDateTime.now();
        ItemComanda item = ItemComanda.builder()
                .comanda(comanda)
                .produto(produto)
                .quantidade(req.quantidade())
                .precoUnitario(precoUnitario)
                .valorDesconto(desconto)
                .valorAcrescimo(acrescimo)
                .subtotal(subtotal)
                .status(StatusItemComanda.EM_PREPARO)
                .observacao(req.observacao())
                .lancadoPor(corrente)
                .dataLancamento(agora)
                .dataStatus(agora)
                .build();
        item = repository.save(item);

        registrarEvento(item, AcaoEventoItem.CRIADO, corrente, null, null, snapshot(item));
        comandaService.recalcularTotais(comanda);
        return item;
    }

    // ---------------------------------------------------------------------
    // EDIÇÃO (apenas em EM_PREPARO)
    // ---------------------------------------------------------------------

    @Transactional
    public ItemComanda editar(Long id, ItemComandaUpdateRequest req) {
        ItemComanda item = buscarPorId(id);
        if (item.getStatus() != StatusItemComanda.EM_PREPARO) {
            throw new BusinessException(
                    "Itens só podem ser editados enquanto em EM_PREPARO (status atual: "
                            + item.getStatus() + ").");
        }
        Usuario corrente = usuarioAutenticadoService.usuarioCorrente();
        garantirPodeAlterarItem(item, corrente, /* exigeAdminCaixa */ false);

        String antes = snapshot(item);
        if (req.quantidade() != null)     item.setQuantidade(req.quantidade());
        if (req.valorDesconto() != null)  item.setValorDesconto(req.valorDesconto());
        if (req.valorAcrescimo() != null) item.setValorAcrescimo(req.valorAcrescimo());
        if (req.observacao() != null)     item.setObservacao(req.observacao());

        item.setSubtotal(calcularSubtotal(item.getQuantidade(), item.getPrecoUnitario(),
                item.getValorDesconto(), item.getValorAcrescimo()));
        item.setDataStatus(LocalDateTime.now());

        registrarEvento(item, AcaoEventoItem.EDITADO, corrente, null, antes, snapshot(item));
        comandaService.recalcularTotais(item.getComanda());
        return item;
    }

    // ---------------------------------------------------------------------
    // MARCAR ENTREGUE
    // ---------------------------------------------------------------------

    @Transactional
    public ItemComanda marcarEntregue(Long id) {
        ItemComanda item = buscarPorId(id);
        if (item.getStatus() != StatusItemComanda.EM_PREPARO) {
            throw new BusinessException(
                    "Só itens em EM_PREPARO podem ser marcados como ENTREGUE (atual: "
                            + item.getStatus() + ").");
        }
        Usuario corrente = usuarioAutenticadoService.usuarioCorrente();
        garantirPodeAlterarItem(item, corrente, /* exigeAdminCaixa */ false);

        String antes = snapshot(item);
        item.setStatus(StatusItemComanda.ENTREGUE);
        item.setDataStatus(LocalDateTime.now());
        registrarEvento(item, AcaoEventoItem.ENTREGUE, corrente, null, antes, snapshot(item));
        return item;
    }

    // ---------------------------------------------------------------------
    // TRANSFERÊNCIA (mesma mesa, sem impacto em estoque)
    // ---------------------------------------------------------------------

    @Transactional
    public ItemComanda transferir(Long id, ItemComandaTransferRequest req) {
        ItemComanda origem = buscarPorId(id);
        if (origem.getStatus() == StatusItemComanda.CANCELADO
                || origem.getStatus() == StatusItemComanda.TRANSFERIDO) {
            throw new BusinessException(
                    "Item em " + origem.getStatus() + " não pode ser transferido.");
        }
        Comanda comandaOrigem = origem.getComanda();
        Comanda comandaDestino = comandaRepository.findById(req.comandaDestinoId())
                .orElseThrow(() -> new InvalidRequestException(
                        "Comanda destino não existe: " + req.comandaDestinoId()));

        // Apenas dentro de MESA e mesmas mesas (RN-ITEM-01).
        if (comandaOrigem.getTipoOrigem() != TipoOrigemComanda.MESA
                || comandaDestino.getTipoOrigem() != TipoOrigemComanda.MESA) {
            throw new BusinessException(
                    "Transferência só é suportada entre comandas MESA na Sprint 2.");
        }
        if (comandaOrigem.getMesa() == null || comandaDestino.getMesa() == null
                || !comandaOrigem.getMesa().getId().equals(comandaDestino.getMesa().getId())) {
            throw new BusinessException(
                    "Origem e destino precisam ser da mesma mesa.");
        }
        if (comandaDestino.getStatus() != StatusComanda.ABERTA) {
            throw new BusinessException(
                    "Comanda destino precisa estar ABERTA (status atual: "
                            + comandaDestino.getStatus() + ").");
        }

        Usuario corrente = usuarioAutenticadoService.usuarioCorrente();
        // Em ENTREGUE só Caixa/Admin podem transferir.
        boolean exigeAdminCaixa = origem.getStatus() == StatusItemComanda.ENTREGUE;
        garantirPodeAlterarItem(origem, corrente, exigeAdminCaixa);

        // Cria o novo item no destino, copiando os atributos relevantes.
        LocalDateTime agora = LocalDateTime.now();
        ItemComanda destino = ItemComanda.builder()
                .comanda(comandaDestino)
                .produto(origem.getProduto())
                .quantidade(origem.getQuantidade())
                .precoUnitario(origem.getPrecoUnitario())
                .valorDesconto(origem.getValorDesconto())
                .valorAcrescimo(origem.getValorAcrescimo())
                .subtotal(origem.getSubtotal())
                .status(origem.getStatus()) // preserva o status (EM_PREPARO ou ENTREGUE)
                .observacao(origem.getObservacao())
                .itemOrigem(origem)
                .lancadoPor(corrente)
                .dataLancamento(agora)
                .dataStatus(agora)
                .build();
        destino = repository.save(destino);

        String antesOrigem = snapshot(origem);
        origem.setStatus(StatusItemComanda.TRANSFERIDO);
        origem.setDataStatus(agora);

        // Eventos: TRANSFERIDO na origem, CRIADO no destino (encadeados).
        registrarEvento(origem, AcaoEventoItem.TRANSFERIDO, corrente, null,
                antesOrigem, snapshotComDestino(origem, destino));
        registrarEvento(destino, AcaoEventoItem.CRIADO, corrente, null,
                null, snapshot(destino));

        comandaService.recalcularTotais(comandaOrigem);
        comandaService.recalcularTotais(comandaDestino);
        return destino;
    }

    // ---------------------------------------------------------------------
    // CANCELAMENTO (com motivo obrigatório)
    // ---------------------------------------------------------------------

    @Transactional
    public ItemComanda cancelar(Long id, ItemComandaCancelRequest req) {
        ItemComanda item = buscarPorId(id);
        if (item.getStatus() == StatusItemComanda.CANCELADO
                || item.getStatus() == StatusItemComanda.TRANSFERIDO) {
            throw new BusinessException(
                    "Item em " + item.getStatus() + " não pode ser cancelado novamente.");
        }
        if (req.motivo() == null) {
            throw new BusinessException("motivo é obrigatório no cancelamento.");
        }
        Usuario corrente = usuarioAutenticadoService.usuarioCorrente();

        // Matriz 5.2.1 — em ENTREGUE só Caixa/Admin podem cancelar.
        boolean exigeAdminCaixa = item.getStatus() == StatusItemComanda.ENTREGUE;
        garantirPodeAlterarItem(item, corrente, exigeAdminCaixa);

        String antes = snapshot(item);
        item.setStatus(StatusItemComanda.CANCELADO);
        item.setMotivoCancelamento(req.motivo());
        item.setDataStatus(LocalDateTime.now());

        registrarEvento(item, AcaoEventoItem.CANCELADO, corrente, req.motivo(),
                antes, snapshot(item));
        comandaService.recalcularTotais(item.getComanda());
        return item;
    }

    // ---------------------------------------------------------------------
    // LEITURA
    // ---------------------------------------------------------------------

    @Transactional(readOnly = true)
    public ItemComanda buscarPorId(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Item de comanda não encontrado: " + id));
    }

    @Transactional(readOnly = true)
    public List<EventoItemComanda> listarEventos(Long itemId) {
        // Garante 404 caso o item não exista.
        buscarPorId(itemId);
        return eventoRepository.findByItemComandaId(itemId);
    }

    // ---------------------------------------------------------------------
    // PERMISSÕES
    // ---------------------------------------------------------------------

    private void garantirAcessoAComanda(Comanda comanda, Usuario corrente, String operacao) {
        if (usuarioAutenticadoService.ehAdminOuCaixa(corrente)) return;
        // Garçom: precisa ser dono da comanda.
        if (comanda.getGarcom() == null
                || !comanda.getGarcom().getId().equals(corrente.getId())) {
            throw new AccessDeniedException(
                    "Garçom só pode " + operacao + " em comandas próprias.");
        }
    }

    /**
     * Aplica a matriz de permissões (seção 5.2.1 do CLAUDE.md):
     * <pre>
     * EM_PREPARO: Garçom dono / Caixa / Admin
     * ENTREGUE:   Caixa / Admin (exigeAdminCaixa=true)
     * </pre>
     */
    private void garantirPodeAlterarItem(ItemComanda item, Usuario corrente, boolean exigeAdminCaixa) {
        if (usuarioAutenticadoService.ehAdminOuCaixa(corrente)) return;
        if (exigeAdminCaixa) {
            throw new AccessDeniedException(
                    "Operação requer perfil Caixa ou Administrador (item em "
                            + item.getStatus() + ").");
        }
        // Garçom: só na própria comanda.
        Comanda c = item.getComanda();
        if (c.getGarcom() == null || !c.getGarcom().getId().equals(corrente.getId())) {
            throw new AccessDeniedException(
                    "Garçom só pode operar itens das próprias comandas.");
        }
    }

    // ---------------------------------------------------------------------
    // CÁLCULO E AUDITORIA
    // ---------------------------------------------------------------------

    private BigDecimal calcularSubtotal(BigDecimal qtd, BigDecimal preco,
                                        BigDecimal desc, BigDecimal acr) {
        BigDecimal d = desc == null ? BigDecimal.ZERO : desc;
        BigDecimal a = acr  == null ? BigDecimal.ZERO : acr;
        return qtd.multiply(preco).subtract(d).add(a);
    }

    private void registrarEvento(ItemComanda item, AcaoEventoItem acao, Usuario usuario,
                                 MotivoCancelamentoItem motivo,
                                 String antes, String depois) {
        EventoItemComanda evento = EventoItemComanda.builder()
                .itemComanda(item)
                .acao(acao)
                .usuario(usuario)
                .motivo(motivo)
                .valorAntes(antes)
                .valorDepois(depois)
                .dataHora(LocalDateTime.now())
                .build();
        eventoRepository.save(evento);
    }

    /** Serializa os campos relevantes do item como string JSON. */
    private String snapshot(ItemComanda item) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id",              item.getId());
        data.put("comandaId",       item.getComanda() == null ? null : item.getComanda().getId());
        data.put("produtoId",       item.getProduto() == null ? null : item.getProduto().getId());
        data.put("quantidade",      item.getQuantidade());
        data.put("precoUnitario",   item.getPrecoUnitario());
        data.put("valorDesconto",   item.getValorDesconto());
        data.put("valorAcrescimo",  item.getValorAcrescimo());
        data.put("subtotal",        item.getSubtotal());
        data.put("status",          item.getStatus());
        data.put("observacao",      item.getObservacao());
        return serializar(data);
    }

    private String snapshotComDestino(ItemComanda origem, ItemComanda destino) {
        Map<String, Object> origemMap = new LinkedHashMap<>();
        origemMap.put("id",        origem.getId());
        origemMap.put("comandaId", origem.getComanda().getId());
        origemMap.put("status",    StatusItemComanda.TRANSFERIDO);

        Map<String, Object> destinoMap = new LinkedHashMap<>();
        destinoMap.put("id",        destino.getId());
        destinoMap.put("comandaId", destino.getComanda().getId());
        destinoMap.put("status",    destino.getStatus());

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("origem",  origemMap);
        data.put("destino", destinoMap);
        return serializar(data);
    }

    private String serializar(Object data) {
        try {
            return objectMapper.writeValueAsString(data);
        } catch (JsonProcessingException e) {
            // Em vez de falhar a operação, registramos um marcador de erro
            // — a auditoria é importante mas não pode quebrar o fluxo de venda.
            return "{\"_serializationError\":\"" + e.getMessage().replace("\"", "'") + "\"}";
        }
    }
}
