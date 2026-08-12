package br.unipar.foodservice.services;

import br.unipar.foodservice.dtos.ItemComandaCancelRequest;
import br.unipar.foodservice.dtos.ItemComandaCreateRequest;
import br.unipar.foodservice.dtos.ItemComandaTransferRequest;
import br.unipar.foodservice.dtos.ItemComandaUpdateRequest;
import br.unipar.foodservice.entities.Comanda;
import br.unipar.foodservice.entities.EventoItemComanda;
import br.unipar.foodservice.entities.ItemComanda;
import br.unipar.foodservice.entities.Mesa;
import br.unipar.foodservice.entities.Produto;
import br.unipar.foodservice.entities.Usuario;
import br.unipar.foodservice.enums.AcaoEventoItem;
import br.unipar.foodservice.enums.MotivoCancelamentoItem;
import br.unipar.foodservice.enums.Perfil;
import br.unipar.foodservice.enums.StatusComanda;
import br.unipar.foodservice.enums.StatusItemComanda;
import br.unipar.foodservice.enums.TipoOrigemComanda;
import br.unipar.foodservice.enums.TipoProduto;
import br.unipar.foodservice.exceptions.BusinessException;
import br.unipar.foodservice.repositories.ComandaRepository;
import br.unipar.foodservice.repositories.EventoItemComandaRepository;
import br.unipar.foodservice.repositories.ItemComandaRepository;
import br.unipar.foodservice.repositories.ProdutoRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Cobertura do {@link ItemComandaService}. Foco no BACKLOG seção 11:
 * <ul>
 *   <li>criar dispara {@code CRIADO};</li>
 *   <li>editar em EM_PREPARO funciona, em ENTREGUE → 422;</li>
 *   <li>transferir mesma mesa ok, mesas diferentes → 422;</li>
 *   <li>garçom cancelando em ENTREGUE → 403;</li>
 *   <li>cancelar sem motivo é bloqueado pelo {@code @NotNull} no DTO; aqui
 *       testamos o fluxo via {@link BusinessException} quando o motivo é
 *       nulo no service (defesa em profundidade);</li>
 *   <li>snapshots JSON populados em valor_antes/valor_depois.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class ItemComandaServiceTest {

    @Mock private ItemComandaRepository repository;
    @Mock private EventoItemComandaRepository eventoRepository;
    @Mock private ComandaRepository comandaRepository;
    @Mock private ProdutoRepository produtoRepository;
    @Mock private UsuarioAutenticadoService usuarioAutenticadoService;
    @Mock private ComandaService comandaService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private ItemComandaService service;

    private Usuario garcom;
    private Usuario outroGarcom;
    private Usuario caixa;
    private Mesa mesa12;
    private Mesa mesa13;
    private Comanda comandaA;
    private Comanda comandaB;
    private Produto cocaUnitario;

    @BeforeEach
    void setUp() {
        // Não usamos @InjectMocks porque o ObjectMapper precisa ser instanciado real,
        // não mockado. Construímos manualmente.
        service = new ItemComandaService(
                repository, eventoRepository, comandaRepository, produtoRepository,
                usuarioAutenticadoService, comandaService, objectMapper);

        garcom = Usuario.builder().id(7L).login("garcom01")
                .nome("Garçom 01").perfil(Perfil.GARCOM).build();
        outroGarcom = Usuario.builder().id(8L).login("garcom02")
                .nome("Garçom 02").perfil(Perfil.GARCOM).build();
        caixa = Usuario.builder().id(2L).login("caixa01")
                .nome("Caixa").perfil(Perfil.CAIXA).build();

        mesa12 = Mesa.builder().id(12L).numero("12").build();
        mesa13 = Mesa.builder().id(13L).numero("13").build();

        comandaA = Comanda.builder().id(100L).codigo("M-12-001")
                .tipoOrigem(TipoOrigemComanda.MESA).mesa(mesa12)
                .garcom(garcom).status(StatusComanda.ABERTA).build();
        comandaB = Comanda.builder().id(101L).codigo("M-12-002")
                .tipoOrigem(TipoOrigemComanda.MESA).mesa(mesa12)
                .garcom(garcom).status(StatusComanda.ABERTA).build();

        cocaUnitario = Produto.builder().id(40L).nome("Coca-Cola")
                .tipoProduto(TipoProduto.UNITARIO)
                .preco(new BigDecimal("8.00")).ativo(true).build();
    }

    // ---------------------------------------------------------------
    // Criação
    // ---------------------------------------------------------------

    @Test
    void criar_garcomDono_disparaEventoCRIADO_eCalculaSubtotal() {
        ItemComandaCreateRequest req = new ItemComandaCreateRequest(
                40L, new BigDecimal("2"),
                new BigDecimal("1.00"), new BigDecimal("0.50"), "sem gás");

        when(comandaRepository.findById(100L)).thenReturn(Optional.of(comandaA));
        when(usuarioAutenticadoService.usuarioCorrente()).thenReturn(garcom);
        when(usuarioAutenticadoService.ehAdminOuCaixa(garcom)).thenReturn(false);
        when(produtoRepository.findById(40L)).thenReturn(Optional.of(cocaUnitario));
        when(repository.save(any(ItemComanda.class))).thenAnswer(inv -> {
            ItemComanda i = inv.getArgument(0);
            i.setId(500L);
            return i;
        });

        ItemComanda criado = service.criar(100L, req);

        // 2 * 8.00 - 1.00 + 0.50 = 15.50
        assertThat(criado.getSubtotal()).isEqualByComparingTo("15.50");
        assertThat(criado.getStatus()).isEqualTo(StatusItemComanda.EM_PREPARO);
        assertThat(criado.getLancadoPor()).isSameAs(garcom);

        ArgumentCaptor<EventoItemComanda> captor = ArgumentCaptor.forClass(EventoItemComanda.class);
        verify(eventoRepository).save(captor.capture());
        EventoItemComanda evento = captor.getValue();
        assertThat(evento.getAcao()).isEqualTo(AcaoEventoItem.CRIADO);
        assertThat(evento.getUsuario()).isSameAs(garcom);
        assertThat(evento.getValorAntes()).isNull();
        assertThat(evento.getValorDepois()).contains("\"status\":\"EM_PREPARO\"");
        verify(comandaService).recalcularTotais(comandaA);
    }

    @Test
    void criar_garcomEmComandaAlheia_lanca403() {
        ItemComandaCreateRequest req = new ItemComandaCreateRequest(
                40L, new BigDecimal("1"), null, null, null);
        Comanda comandaDeOutro = Comanda.builder().id(200L)
                .status(StatusComanda.ABERTA).garcom(outroGarcom).build();
        when(comandaRepository.findById(200L)).thenReturn(Optional.of(comandaDeOutro));
        when(usuarioAutenticadoService.usuarioCorrente()).thenReturn(garcom);
        when(usuarioAutenticadoService.ehAdminOuCaixa(garcom)).thenReturn(false);

        assertThatThrownBy(() -> service.criar(200L, req))
                .isInstanceOf(AccessDeniedException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void criar_comandaFechada_lanca422() {
        comandaA.setStatus(StatusComanda.AGUARDANDO_PAGAMENTO);
        ItemComandaCreateRequest req = new ItemComandaCreateRequest(
                40L, new BigDecimal("1"), null, null, null);
        when(comandaRepository.findById(100L)).thenReturn(Optional.of(comandaA));

        assertThatThrownBy(() -> service.criar(100L, req))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("ABERTAS");
    }

    // ---------------------------------------------------------------
    // Edição
    // ---------------------------------------------------------------

    @Test
    void editar_emPreparo_recalculaSubtotal_eRegistraEditado() {
        ItemComanda item = ItemComanda.builder()
                .id(500L).comanda(comandaA).produto(cocaUnitario)
                .quantidade(new BigDecimal("1")).precoUnitario(new BigDecimal("8.00"))
                .valorDesconto(BigDecimal.ZERO).valorAcrescimo(BigDecimal.ZERO)
                .subtotal(new BigDecimal("8.00"))
                .status(StatusItemComanda.EM_PREPARO).build();
        when(repository.findById(500L)).thenReturn(Optional.of(item));
        when(usuarioAutenticadoService.usuarioCorrente()).thenReturn(garcom);
        when(usuarioAutenticadoService.ehAdminOuCaixa(garcom)).thenReturn(false);

        ItemComandaUpdateRequest req = new ItemComandaUpdateRequest(
                new BigDecimal("3"), null, null, null);

        ItemComanda atualizado = service.editar(500L, req);

        assertThat(atualizado.getSubtotal()).isEqualByComparingTo("24.00");
        ArgumentCaptor<EventoItemComanda> captor = ArgumentCaptor.forClass(EventoItemComanda.class);
        verify(eventoRepository).save(captor.capture());
        assertThat(captor.getValue().getAcao()).isEqualTo(AcaoEventoItem.EDITADO);
        assertThat(captor.getValue().getValorAntes()).contains("\"quantidade\":1");
    }

    @Test
    void editar_itemEntregue_lanca422() {
        ItemComanda item = ItemComanda.builder()
                .id(500L).comanda(comandaA).produto(cocaUnitario)
                .quantidade(new BigDecimal("1")).precoUnitario(new BigDecimal("8.00"))
                .status(StatusItemComanda.ENTREGUE).build();
        when(repository.findById(500L)).thenReturn(Optional.of(item));

        assertThatThrownBy(() -> service.editar(500L,
                new ItemComandaUpdateRequest(new BigDecimal("2"), null, null, null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("EM_PREPARO");
    }

    // ---------------------------------------------------------------
    // Transferência
    // ---------------------------------------------------------------

    @Test
    void transferir_mesmaMesa_criaItemDestino_eMarcaOrigemTransferido() {
        ItemComanda origem = ItemComanda.builder()
                .id(500L).comanda(comandaA).produto(cocaUnitario)
                .quantidade(new BigDecimal("2")).precoUnitario(new BigDecimal("8.00"))
                .valorDesconto(BigDecimal.ZERO).valorAcrescimo(BigDecimal.ZERO)
                .subtotal(new BigDecimal("16.00"))
                .status(StatusItemComanda.EM_PREPARO).build();
        when(repository.findById(500L)).thenReturn(Optional.of(origem));
        when(comandaRepository.findById(101L)).thenReturn(Optional.of(comandaB));
        when(usuarioAutenticadoService.usuarioCorrente()).thenReturn(garcom);
        when(usuarioAutenticadoService.ehAdminOuCaixa(garcom)).thenReturn(false);
        when(repository.save(any(ItemComanda.class))).thenAnswer(inv -> {
            ItemComanda i = inv.getArgument(0);
            if (i.getId() == null) i.setId(700L); // destino
            return i;
        });

        ItemComanda destino = service.transferir(500L,
                new ItemComandaTransferRequest(101L));

        assertThat(destino.getComanda().getId()).isEqualTo(101L);
        assertThat(destino.getItemOrigem()).isSameAs(origem);
        assertThat(destino.getStatus()).isEqualTo(StatusItemComanda.EM_PREPARO);
        assertThat(origem.getStatus()).isEqualTo(StatusItemComanda.TRANSFERIDO);

        // 2 eventos: TRANSFERIDO na origem, CRIADO no destino
        verify(eventoRepository, times(2)).save(any(EventoItemComanda.class));
        verify(comandaService).recalcularTotais(comandaA);
        verify(comandaService).recalcularTotais(comandaB);
    }

    @Test
    void transferir_mesasDiferentes_lanca422() {
        Comanda comandaEmOutraMesa = Comanda.builder().id(102L)
                .tipoOrigem(TipoOrigemComanda.MESA).mesa(mesa13)
                .status(StatusComanda.ABERTA).garcom(garcom).build();
        ItemComanda origem = ItemComanda.builder()
                .id(500L).comanda(comandaA).produto(cocaUnitario)
                .quantidade(new BigDecimal("1")).precoUnitario(new BigDecimal("8.00"))
                .status(StatusItemComanda.EM_PREPARO).build();
        when(repository.findById(500L)).thenReturn(Optional.of(origem));
        when(comandaRepository.findById(102L)).thenReturn(Optional.of(comandaEmOutraMesa));

        assertThatThrownBy(() -> service.transferir(500L,
                new ItemComandaTransferRequest(102L)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("mesma mesa");
    }

    @Test
    void transferir_balcao_lanca422() {
        Comanda balcao = Comanda.builder().id(300L)
                .tipoOrigem(TipoOrigemComanda.BALCAO)
                .status(StatusComanda.ABERTA).build();
        ItemComanda origem = ItemComanda.builder()
                .id(500L).comanda(balcao).produto(cocaUnitario)
                .quantidade(new BigDecimal("1")).precoUnitario(new BigDecimal("8.00"))
                .status(StatusItemComanda.EM_PREPARO).build();
        when(repository.findById(500L)).thenReturn(Optional.of(origem));
        when(comandaRepository.findById(101L)).thenReturn(Optional.of(comandaB));

        assertThatThrownBy(() -> service.transferir(500L,
                new ItemComandaTransferRequest(101L)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("MESA");
    }

    // ---------------------------------------------------------------
    // Cancelamento
    // ---------------------------------------------------------------

    @Test
    void cancelar_garcomEmEntregue_lanca403() {
        ItemComanda item = ItemComanda.builder()
                .id(500L).comanda(comandaA).produto(cocaUnitario)
                .quantidade(new BigDecimal("1")).precoUnitario(new BigDecimal("8.00"))
                .status(StatusItemComanda.ENTREGUE).build();
        when(repository.findById(500L)).thenReturn(Optional.of(item));
        when(usuarioAutenticadoService.usuarioCorrente()).thenReturn(garcom);
        when(usuarioAutenticadoService.ehAdminOuCaixa(garcom)).thenReturn(false);

        assertThatThrownBy(() -> service.cancelar(500L,
                new ItemComandaCancelRequest(MotivoCancelamentoItem.CLIENTE_DESISTIU)))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void cancelar_caixaEmEntregue_persisteMotivo_eDispararEvento() {
        ItemComanda item = ItemComanda.builder()
                .id(500L).comanda(comandaA).produto(cocaUnitario)
                .quantidade(new BigDecimal("1")).precoUnitario(new BigDecimal("8.00"))
                .valorDesconto(BigDecimal.ZERO).valorAcrescimo(BigDecimal.ZERO)
                .subtotal(new BigDecimal("8.00"))
                .status(StatusItemComanda.ENTREGUE).build();
        when(repository.findById(500L)).thenReturn(Optional.of(item));
        when(usuarioAutenticadoService.usuarioCorrente()).thenReturn(caixa);
        when(usuarioAutenticadoService.ehAdminOuCaixa(caixa)).thenReturn(true);

        ItemComanda cancelado = service.cancelar(500L,
                new ItemComandaCancelRequest(MotivoCancelamentoItem.CORTESIA));

        assertThat(cancelado.getStatus()).isEqualTo(StatusItemComanda.CANCELADO);
        assertThat(cancelado.getMotivoCancelamento()).isEqualTo(MotivoCancelamentoItem.CORTESIA);
        ArgumentCaptor<EventoItemComanda> captor = ArgumentCaptor.forClass(EventoItemComanda.class);
        verify(eventoRepository).save(captor.capture());
        assertThat(captor.getValue().getAcao()).isEqualTo(AcaoEventoItem.CANCELADO);
        assertThat(captor.getValue().getMotivo()).isEqualTo(MotivoCancelamentoItem.CORTESIA);
        verify(comandaService).recalcularTotais(comandaA);
    }

    @Test
    void cancelar_semMotivo_lanca422() {
        ItemComanda item = ItemComanda.builder()
                .id(500L).comanda(comandaA).produto(cocaUnitario)
                .quantidade(new BigDecimal("1")).precoUnitario(new BigDecimal("8.00"))
                .status(StatusItemComanda.EM_PREPARO).build();
        when(repository.findById(500L)).thenReturn(Optional.of(item));

        assertThatThrownBy(() -> service.cancelar(500L,
                new ItemComandaCancelRequest(null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("motivo");
    }

    // ---------------------------------------------------------------
    // Marcar entregue
    // ---------------------------------------------------------------

    @Test
    void marcarEntregue_emPreparo_atualizaStatus_eDispararEvento() {
        ItemComanda item = ItemComanda.builder()
                .id(500L).comanda(comandaA).produto(cocaUnitario)
                .quantidade(new BigDecimal("1")).precoUnitario(new BigDecimal("8.00"))
                .valorDesconto(BigDecimal.ZERO).valorAcrescimo(BigDecimal.ZERO)
                .subtotal(new BigDecimal("8.00"))
                .status(StatusItemComanda.EM_PREPARO).build();
        when(repository.findById(500L)).thenReturn(Optional.of(item));
        when(usuarioAutenticadoService.usuarioCorrente()).thenReturn(garcom);
        when(usuarioAutenticadoService.ehAdminOuCaixa(garcom)).thenReturn(false);

        ItemComanda atualizado = service.marcarEntregue(500L);

        assertThat(atualizado.getStatus()).isEqualTo(StatusItemComanda.ENTREGUE);
        ArgumentCaptor<EventoItemComanda> captor = ArgumentCaptor.forClass(EventoItemComanda.class);
        verify(eventoRepository).save(captor.capture());
        assertThat(captor.getValue().getAcao()).isEqualTo(AcaoEventoItem.ENTREGUE);
    }
}
