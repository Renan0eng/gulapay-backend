package br.unipar.foodservice.services;

import br.unipar.foodservice.dtos.ComandaCreateRequest;
import br.unipar.foodservice.entities.Cliente;
import br.unipar.foodservice.entities.Comanda;
import br.unipar.foodservice.entities.EnderecoCliente;
import br.unipar.foodservice.entities.Insumo;
import br.unipar.foodservice.entities.ItemComanda;
import br.unipar.foodservice.entities.Mesa;
import br.unipar.foodservice.entities.Produto;
import br.unipar.foodservice.entities.UnidadeMedida;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Cobertura do {@link ComandaService}. Padrão Mockito + AssertJ.
 *
 * <p>Mantém o foco nos cenários do BACKLOG seção 11:
 * <ul>
 *   <li>Criação MESA atualiza Mesa.status para OCUPADA;</li>
 *   <li>BALCAO/DELIVERY forçam escopo INDIVIDUAL;</li>
 *   <li>2ª COMPARTILHADA na mesma mesa → 422;</li>
 *   <li>DELIVERY sem endereço → 422;</li>
 *   <li>DELIVERY com endereço de outro cliente → 422;</li>
 *   <li>Link WhatsApp gerado corretamente;</li>
 *   <li>Fechar com COMPOSTO/COMBO → 422 com mensagem Sprint 5;</li>
 *   <li>Fechar com UNITARIO → invoca {@code registrarSaidaVenda};</li>
 *   <li>Garçom só consegue ver próprias comandas.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class ComandaServiceTest {

    @Mock private ComandaRepository repository;
    @Mock private ClienteRepository clienteRepository;
    @Mock private MesaRepository mesaRepository;
    @Mock private EnderecoClienteRepository enderecoRepository;
    @Mock private ItemComandaRepository itemRepository;
    @Mock private UsuarioAutenticadoService usuarioAutenticadoService;
    @Mock private MovimentacaoEstoqueService movimentacaoEstoqueService;

    @InjectMocks
    private ComandaService service;

    private Cliente cliente;
    private Mesa mesa;
    private Usuario garcom;
    private Usuario caixa;

    @BeforeEach
    void setUp() {
        cliente = Cliente.builder().id(100L).nome("Ana").telefone("44999990000").ativo(true).build();

        mesa = Mesa.builder().id(12L).numero("12").capacidade(4)
                .status(StatusMesa.LIVRE).ativo(true).build();

        garcom = Usuario.builder().id(7L).login("garcom01")
                .nome("Garçom 01").perfil(Perfil.GARCOM).ativo(true).build();

        caixa = Usuario.builder().id(2L).login("caixa01")
                .nome("Caixa 01").perfil(Perfil.CAIXA).ativo(true).build();
    }

    // ---------------------------------------------------------------
    // Criação
    // ---------------------------------------------------------------

    @Test
    void criar_mesa_marcaMesaComoOcupada_eGeraLinkWhatsApp_eEscopoForcado() {
        ComandaCreateRequest req = new ComandaCreateRequest(
                TipoOrigemComanda.MESA, EscopoComanda.INDIVIDUAL,
                100L, 12L, 7L, null, null, null);

        when(clienteRepository.findById(100L)).thenReturn(Optional.of(cliente));
        when(mesaRepository.findById(12L)).thenReturn(Optional.of(mesa));
        when(usuarioAutenticadoService.carregar(7L)).thenReturn(garcom);
        when(repository.existsAbertaPorMesaEEscopo(eq(12L), eq(EscopoComanda.COMPARTILHADA)))
                .thenReturn(false);
        when(repository.contarPorMesaNoDia(eq(12L), any(), any())).thenReturn(0L);
        when(repository.existsByCodigo(any())).thenReturn(false);
        when(repository.save(any(Comanda.class))).thenAnswer(inv -> inv.getArgument(0));

        Comanda criada = service.criar(req);

        assertThat(criada.getTipoOrigem()).isEqualTo(TipoOrigemComanda.MESA);
        assertThat(criada.getEscopo()).isEqualTo(EscopoComanda.INDIVIDUAL);
        assertThat(criada.getStatus()).isEqualTo(StatusComanda.ABERTA);
        assertThat(criada.getLinkWhatsApp()).isEqualTo("https://wa.me/44999990000");
        assertThat(criada.getCodigo()).startsWith("M-12-");
        assertThat(mesa.getStatus()).isEqualTo(StatusMesa.OCUPADA);
    }

    @Test
    void criar_balcao_forcaEscopoIndividual_mesmoQuandoRequestPedeCompartilhada() {
        ComandaCreateRequest req = new ComandaCreateRequest(
                TipoOrigemComanda.BALCAO, EscopoComanda.COMPARTILHADA,
                100L, null, null, null, null, null);

        when(clienteRepository.findById(100L)).thenReturn(Optional.of(cliente));
        when(repository.contarPorTipoNoDia(eq(TipoOrigemComanda.BALCAO), any(), any())).thenReturn(0L);
        when(repository.existsByCodigo(any())).thenReturn(false);
        when(repository.save(any(Comanda.class))).thenAnswer(inv -> inv.getArgument(0));

        Comanda criada = service.criar(req);

        assertThat(criada.getEscopo()).isEqualTo(EscopoComanda.INDIVIDUAL);
        assertThat(criada.getCodigo()).startsWith("B-");
        assertThat(criada.getMesa()).isNull();
    }

    @Test
    void criar_segundaCompartilhadaNaMesmaMesa_deveLancar422() {
        ComandaCreateRequest req = new ComandaCreateRequest(
                TipoOrigemComanda.MESA, EscopoComanda.COMPARTILHADA,
                100L, 12L, 7L, null, null, null);

        when(clienteRepository.findById(100L)).thenReturn(Optional.of(cliente));
        when(mesaRepository.findById(12L)).thenReturn(Optional.of(mesa));
        when(usuarioAutenticadoService.carregar(7L)).thenReturn(garcom);
        when(repository.existsAbertaPorMesaEEscopo(eq(12L), eq(EscopoComanda.COMPARTILHADA)))
                .thenReturn(true);

        assertThatThrownBy(() -> service.criar(req))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("COMPARTILHADA");
        verify(repository, never()).save(any());
    }

    @Test
    void criar_delivery_semEnderecoEntrega_deveLancar422() {
        ComandaCreateRequest req = new ComandaCreateRequest(
                TipoOrigemComanda.DELIVERY, null, 100L, null, null,
                null, null, null);
        when(clienteRepository.findById(100L)).thenReturn(Optional.of(cliente));

        assertThatThrownBy(() -> service.criar(req))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("enderecoEntregaId");
    }

    @Test
    void criar_delivery_enderecoDeOutroCliente_deveLancar422() {
        Cliente outroDono = Cliente.builder().id(999L).telefone("44888880000").build();
        EnderecoCliente endereco = EnderecoCliente.builder()
                .id(50L).cliente(outroDono).logradouro("R. X")
                .cidade("Umuarama").uf("PR").ativo(true).build();
        ComandaCreateRequest req = new ComandaCreateRequest(
                TipoOrigemComanda.DELIVERY, null, 100L, null, null,
                50L, null, null);

        when(clienteRepository.findById(100L)).thenReturn(Optional.of(cliente));
        when(enderecoRepository.findById(50L)).thenReturn(Optional.of(endereco));

        assertThatThrownBy(() -> service.criar(req))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("não pertence ao cliente");
    }

    @Test
    void criar_mesa_semGarcom_deveLancar422() {
        ComandaCreateRequest req = new ComandaCreateRequest(
                TipoOrigemComanda.MESA, null, 100L, 12L, null,
                null, null, null);
        when(clienteRepository.findById(100L)).thenReturn(Optional.of(cliente));
        when(mesaRepository.findById(12L)).thenReturn(Optional.of(mesa));

        assertThatThrownBy(() -> service.criar(req))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("garcomId");
    }

    @Test
    void criar_clienteInexistente_deveLancar400() {
        ComandaCreateRequest req = new ComandaCreateRequest(
                TipoOrigemComanda.BALCAO, null, 999L, null, null, null, null, null);
        when(clienteRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.criar(req))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("999");
    }

    // ---------------------------------------------------------------
    // Leitura / permissão
    // ---------------------------------------------------------------

    @Test
    void buscarPorId_garcom_naoVeComandaDeOutroGarcom_lanca403() {
        Usuario outroGarcom = Usuario.builder().id(8L).perfil(Perfil.GARCOM).build();
        Comanda c = Comanda.builder().id(1L).garcom(outroGarcom).build();
        when(repository.findById(1L)).thenReturn(Optional.of(c));
        when(usuarioAutenticadoService.usuarioCorrente()).thenReturn(garcom);
        when(usuarioAutenticadoService.ehGarcom(garcom)).thenReturn(true);

        assertThatThrownBy(() -> service.buscarPorId(1L))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void buscarPorId_naoExiste_lanca404() {
        when(repository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.buscarPorId(999L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("999");
    }

    // ---------------------------------------------------------------
    // Fechamento
    // ---------------------------------------------------------------

    @Test
    void fechar_comItemEmPreparo_lanca422() {
        Comanda c = Comanda.builder().id(1L).status(StatusComanda.ABERTA).build();
        when(usuarioAutenticadoService.usuarioCorrente()).thenReturn(caixa);
        when(usuarioAutenticadoService.ehAdminOuCaixa(caixa)).thenReturn(true);
        when(repository.findById(1L)).thenReturn(Optional.of(c));
        when(itemRepository.existsEmPreparoNaComanda(1L)).thenReturn(true);

        assertThatThrownBy(() -> service.fechar(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("EM_PREPARO");
        verify(movimentacaoEstoqueService, never()).registrarSaidaVenda(any(), any(), any(), any());
    }

    @Test
    void fechar_comCompostoOuCombo_lanca422_comMensagemSprint5() {
        Comanda c = Comanda.builder().id(1L).status(StatusComanda.ABERTA).codigo("M-1-001").build();
        ItemComanda compostoEntregue = ItemComanda.builder().id(10L).build();
        when(usuarioAutenticadoService.usuarioCorrente()).thenReturn(caixa);
        when(usuarioAutenticadoService.ehAdminOuCaixa(caixa)).thenReturn(true);
        when(repository.findById(1L)).thenReturn(Optional.of(c));
        when(itemRepository.existsEmPreparoNaComanda(1L)).thenReturn(false);
        when(itemRepository.findEntreguesCompostosOuCombos(1L))
                .thenReturn(List.of(compostoEntregue));

        assertThatThrownBy(() -> service.fechar(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Sprint 5");
        verify(movimentacaoEstoqueService, never()).registrarSaidaVenda(any(), any(), any(), any());
    }

    @Test
    void fechar_unitario_disparaSaidaVenda_eMudaStatusParaAguardandoPagamento() {
        // Insumo simulando coca, unidade UN
        UnidadeMedida un = UnidadeMedida.builder().id(5L).simbolo("un").build();
        Insumo insumoCoca = Insumo.builder().id(70L).nome("Coca-Cola lata")
                .unidadePadrao(un).ativo(true).build();
        Produto coca = Produto.builder().id(40L).nome("Coca-Cola")
                .tipoProduto(TipoProduto.UNITARIO).insumo(insumoCoca)
                .preco(new BigDecimal("8.00")).ativo(true).build();

        Comanda c = Comanda.builder().id(1L).codigo("B-001")
                .status(StatusComanda.ABERTA).build();
        ItemComanda itemEntregue = ItemComanda.builder()
                .id(11L).comanda(c).produto(coca)
                .quantidade(new BigDecimal("2"))
                .status(StatusItemComanda.ENTREGUE).build();

        when(usuarioAutenticadoService.usuarioCorrente()).thenReturn(caixa);
        when(usuarioAutenticadoService.ehAdminOuCaixa(caixa)).thenReturn(true);
        when(repository.findById(1L)).thenReturn(Optional.of(c));
        when(itemRepository.existsEmPreparoNaComanda(1L)).thenReturn(false);
        when(itemRepository.findEntreguesCompostosOuCombos(1L)).thenReturn(List.of());
        when(itemRepository.findByComandaIdAndStatus(1L, StatusItemComanda.ENTREGUE))
                .thenReturn(List.of(itemEntregue));

        Comanda resultado = service.fechar(1L);

        verify(movimentacaoEstoqueService).registrarSaidaVenda(
                eq(70L), eq(5L), eq(new BigDecimal("2")), any());
        assertThat(resultado.getStatus()).isEqualTo(StatusComanda.AGUARDANDO_PAGAMENTO);
        assertThat(resultado.getDataFechamento()).isNotNull();
    }

    @Test
    void fechar_garcom_lanca403() {
        Comanda c = Comanda.builder().id(1L).status(StatusComanda.ABERTA).build();
        when(usuarioAutenticadoService.usuarioCorrente()).thenReturn(garcom);
        when(usuarioAutenticadoService.ehAdminOuCaixa(garcom)).thenReturn(false);

        assertThatThrownBy(() -> service.fechar(1L))
                .isInstanceOf(AccessDeniedException.class);
        verify(repository, never()).findById(anyLong());
    }
}
