package br.unipar.foodservice.services;

import br.unipar.foodservice.dtos.RateioComandaRequest;
import br.unipar.foodservice.dtos.RateioComandaResponse;
import br.unipar.foodservice.dtos.RateioConsumidoresItem;
import br.unipar.foodservice.dtos.RateioParticipantesRequest;
import br.unipar.foodservice.dtos.RateioValorManualItem;
import br.unipar.foodservice.entities.Comanda;
import br.unipar.foodservice.entities.ItemComanda;
import br.unipar.foodservice.entities.Produto;
import br.unipar.foodservice.entities.Usuario;
import br.unipar.foodservice.enums.EscopoComanda;
import br.unipar.foodservice.enums.EstrategiaRateio;
import br.unipar.foodservice.enums.Perfil;
import br.unipar.foodservice.enums.StatusComanda;
import br.unipar.foodservice.enums.StatusItemComanda;
import br.unipar.foodservice.exceptions.BusinessException;
import br.unipar.foodservice.repositories.ComandaRepository;
import br.unipar.foodservice.repositories.ItemComandaRepository;
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
import static org.mockito.Mockito.when;

/**
 * Cobertura do {@link RateioService} (Sprint 3). Padrão Mockito + AssertJ,
 * espelhando {@code ComandaServiceTest}.
 */
@ExtendWith(MockitoExtension.class)
class RateioServiceTest {

    @Mock private ComandaRepository comandaRepository;
    @Mock private ItemComandaRepository itemComandaRepository;
    @Mock private ComandaService comandaService;
    @Mock private UsuarioAutenticadoService usuarioAutenticadoService;

    @InjectMocks
    private RateioService service;

    private Usuario caixa;
    private Usuario garcom;

    @BeforeEach
    void setUp() {
        caixa = Usuario.builder().id(2L).login("caixa01").perfil(Perfil.CAIXA).ativo(true).build();
        garcom = Usuario.builder().id(7L).login("garcom01").perfil(Perfil.GARCOM).ativo(true).build();
    }

    private Comanda individual(Long id, BigDecimal totalLiquidoProprio, boolean participante) {
        return Comanda.builder()
                .id(id)
                .codigo("M-1-00" + id)
                .escopo(EscopoComanda.INDIVIDUAL)
                .totalLiquido(totalLiquidoProprio)
                .valorRateio(BigDecimal.ZERO)
                .participanteRateio(participante)
                .build();
    }

    private Comanda compartilhada(StatusComanda status, BigDecimal total, List<Comanda> individuais) {
        return Comanda.builder()
                .id(1L)
                .codigo("M-1-001")
                .escopo(EscopoComanda.COMPARTILHADA)
                .status(status)
                .totalLiquido(total)
                .individuais(individuais)
                .build();
    }

    private void permitirAdminOuCaixa() {
        when(usuarioAutenticadoService.usuarioCorrente()).thenReturn(caixa);
        when(usuarioAutenticadoService.ehAdminOuCaixa(caixa)).thenReturn(true);
    }

    // ---------------------------------------------------------------
    // Participantes
    // ---------------------------------------------------------------

    @Test
    void definirParticipantes_marcaSelecionados_eDesmarcaOsDemais() {
        Comanda p10 = individual(10L, BigDecimal.ZERO, true);
        Comanda p11 = individual(11L, BigDecimal.ZERO, true);
        Comanda c = compartilhada(StatusComanda.AGUARDANDO_PAGAMENTO, new BigDecimal("10.00"), List.of(p10, p11));

        permitirAdminOuCaixa();
        when(comandaRepository.findById(1L)).thenReturn(Optional.of(c));

        RateioComandaResponse resposta = service.definirParticipantes(1L, new RateioParticipantesRequest(List.of(10L)));

        assertThat(p10.getParticipanteRateio()).isTrue();
        assertThat(p11.getParticipanteRateio()).isFalse();
        assertThat(resposta.participantes()).hasSize(2);
    }

    @Test
    void definirParticipantes_naoAdminOuCaixa_lanca403() {
        when(usuarioAutenticadoService.usuarioCorrente()).thenReturn(garcom);
        when(usuarioAutenticadoService.ehAdminOuCaixa(garcom)).thenReturn(false);

        assertThatThrownBy(() -> service.definirParticipantes(1L, new RateioParticipantesRequest(List.of(10L))))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void definirParticipantes_comandaIndividual_lanca422() {
        Comanda individual = individual(10L, BigDecimal.ZERO, true);
        permitirAdminOuCaixa();
        when(comandaRepository.findById(10L)).thenReturn(Optional.of(individual));

        assertThatThrownBy(() -> service.definirParticipantes(10L, new RateioParticipantesRequest(List.of())))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("COMPARTILHADA");
    }

    @Test
    void definirParticipantes_rateioJaAplicado_lanca422() {
        Comanda p10 = individual(10L, BigDecimal.ZERO, true);
        Comanda c = compartilhada(StatusComanda.AGUARDANDO_PAGAMENTO, new BigDecimal("10.00"), List.of(p10));
        c.setEstrategiaRateio(EstrategiaRateio.IGUALITARIO);

        permitirAdminOuCaixa();
        when(comandaRepository.findById(1L)).thenReturn(Optional.of(c));

        assertThatThrownBy(() -> service.definirParticipantes(1L, new RateioParticipantesRequest(List.of(10L))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("já aplicado");
    }

    // ---------------------------------------------------------------
    // Aplicar — validações comuns
    // ---------------------------------------------------------------

    @Test
    void aplicarRateio_comandaAberta_lanca422() {
        Comanda c = compartilhada(StatusComanda.ABERTA, new BigDecimal("10.00"), List.of());
        permitirAdminOuCaixa();
        when(comandaRepository.findById(1L)).thenReturn(Optional.of(c));

        RateioComandaRequest req = new RateioComandaRequest(EstrategiaRateio.IGUALITARIO, null, null);

        assertThatThrownBy(() -> service.aplicarRateio(1L, req))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Feche a comanda");
    }

    @Test
    void aplicarRateio_semParticipantes_lanca422() {
        Comanda p10 = individual(10L, BigDecimal.ZERO, false);
        Comanda c = compartilhada(StatusComanda.AGUARDANDO_PAGAMENTO, new BigDecimal("10.00"), List.of(p10));
        permitirAdminOuCaixa();
        when(comandaRepository.findById(1L)).thenReturn(Optional.of(c));

        RateioComandaRequest req = new RateioComandaRequest(EstrategiaRateio.IGUALITARIO, null, null);

        assertThatThrownBy(() -> service.aplicarRateio(1L, req))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Nenhum participante");
    }

    // ---------------------------------------------------------------
    // SEM_RATEIO
    // ---------------------------------------------------------------

    @Test
    void aplicarRateio_semRateio_comMaisDeUmParticipante_lanca422() {
        Comanda p10 = individual(10L, BigDecimal.ZERO, true);
        Comanda p11 = individual(11L, BigDecimal.ZERO, true);
        Comanda c = compartilhada(StatusComanda.AGUARDANDO_PAGAMENTO, new BigDecimal("10.00"), List.of(p10, p11));
        permitirAdminOuCaixa();
        when(comandaRepository.findById(1L)).thenReturn(Optional.of(c));

        RateioComandaRequest req = new RateioComandaRequest(EstrategiaRateio.SEM_RATEIO, null, null);

        assertThatThrownBy(() -> service.aplicarRateio(1L, req))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("SEM_RATEIO");
    }

    @Test
    void aplicarRateio_semRateio_umaIndividualQuitaOTotal() {
        Comanda p10 = individual(10L, BigDecimal.ZERO, true);
        Comanda p11 = individual(11L, BigDecimal.ZERO, false);
        Comanda c = compartilhada(StatusComanda.AGUARDANDO_PAGAMENTO, new BigDecimal("87.50"), List.of(p10, p11));
        permitirAdminOuCaixa();
        when(comandaRepository.findById(1L)).thenReturn(Optional.of(c));

        RateioComandaRequest req = new RateioComandaRequest(EstrategiaRateio.SEM_RATEIO, null, null);
        service.aplicarRateio(1L, req);

        assertThat(p10.getValorRateio()).isEqualByComparingTo("87.50");
        assertThat(p11.getValorRateio()).isEqualByComparingTo("0.00");
        assertThat(c.getEstrategiaRateio()).isEqualTo(EstrategiaRateio.SEM_RATEIO);
    }

    // ---------------------------------------------------------------
    // IGUALITARIO
    // ---------------------------------------------------------------

    @Test
    void aplicarRateio_igualitario_distribuiRestoDeCentavosNosPrimeiros() {
        Comanda p10 = individual(10L, BigDecimal.ZERO, true);
        Comanda p11 = individual(11L, BigDecimal.ZERO, true);
        Comanda p12 = individual(12L, BigDecimal.ZERO, true);
        Comanda c = compartilhada(StatusComanda.AGUARDANDO_PAGAMENTO, new BigDecimal("10.00"),
                List.of(p10, p11, p12));
        permitirAdminOuCaixa();
        when(comandaRepository.findById(1L)).thenReturn(Optional.of(c));

        RateioComandaRequest req = new RateioComandaRequest(EstrategiaRateio.IGUALITARIO, null, null);
        service.aplicarRateio(1L, req);

        assertThat(p10.getValorRateio()).isEqualByComparingTo("3.34");
        assertThat(p11.getValorRateio()).isEqualByComparingTo("3.33");
        assertThat(p12.getValorRateio()).isEqualByComparingTo("3.33");
        BigDecimal soma = p10.getValorRateio().add(p11.getValorRateio()).add(p12.getValorRateio());
        assertThat(soma).isEqualByComparingTo("10.00");
    }

    // ---------------------------------------------------------------
    // MANUAL
    // ---------------------------------------------------------------

    @Test
    void aplicarRateio_manual_somaDiferenteDoTotal_lanca422() {
        Comanda p10 = individual(10L, BigDecimal.ZERO, true);
        Comanda p11 = individual(11L, BigDecimal.ZERO, true);
        Comanda c = compartilhada(StatusComanda.AGUARDANDO_PAGAMENTO, new BigDecimal("100.00"), List.of(p10, p11));
        permitirAdminOuCaixa();
        when(comandaRepository.findById(1L)).thenReturn(Optional.of(c));

        List<RateioValorManualItem> valores = List.of(
                new RateioValorManualItem(10L, new BigDecimal("50.00")),
                new RateioValorManualItem(11L, new BigDecimal("40.00")));
        RateioComandaRequest req = new RateioComandaRequest(EstrategiaRateio.MANUAL, valores, null);

        assertThatThrownBy(() -> service.aplicarRateio(1L, req))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("diferente");
    }

    @Test
    void aplicarRateio_manual_aplicaValoresInformados() {
        Comanda p10 = individual(10L, BigDecimal.ZERO, true);
        Comanda p11 = individual(11L, BigDecimal.ZERO, true);
        Comanda c = compartilhada(StatusComanda.AGUARDANDO_PAGAMENTO, new BigDecimal("100.00"), List.of(p10, p11));
        permitirAdminOuCaixa();
        when(comandaRepository.findById(1L)).thenReturn(Optional.of(c));

        List<RateioValorManualItem> valores = List.of(
                new RateioValorManualItem(10L, new BigDecimal("60.00")),
                new RateioValorManualItem(11L, new BigDecimal("40.00")));
        RateioComandaRequest req = new RateioComandaRequest(EstrategiaRateio.MANUAL, valores, null);
        service.aplicarRateio(1L, req);

        assertThat(p10.getValorRateio()).isEqualByComparingTo("60.00");
        assertThat(p11.getValorRateio()).isEqualByComparingTo("40.00");
    }

    // ---------------------------------------------------------------
    // PROPORCIONAL
    // ---------------------------------------------------------------

    @Test
    void aplicarRateio_proporcional_semPesos_lanca422() {
        Comanda p10 = individual(10L, BigDecimal.ZERO, true);
        Comanda p11 = individual(11L, BigDecimal.ZERO, true);
        Comanda c = compartilhada(StatusComanda.AGUARDANDO_PAGAMENTO, new BigDecimal("20.00"), List.of(p10, p11));
        permitirAdminOuCaixa();
        when(comandaRepository.findById(1L)).thenReturn(Optional.of(c));

        RateioComandaRequest req = new RateioComandaRequest(EstrategiaRateio.PROPORCIONAL, null, null);

        assertThatThrownBy(() -> service.aplicarRateio(1L, req))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("PROPORCIONAL");
    }

    @Test
    void aplicarRateio_proporcional_ponderaPeloConsumoProprio() {
        Comanda p10 = individual(10L, new BigDecimal("30.00"), true);
        Comanda p11 = individual(11L, new BigDecimal("10.00"), true);
        Comanda c = compartilhada(StatusComanda.AGUARDANDO_PAGAMENTO, new BigDecimal("20.00"), List.of(p10, p11));
        permitirAdminOuCaixa();
        when(comandaRepository.findById(1L)).thenReturn(Optional.of(c));

        RateioComandaRequest req = new RateioComandaRequest(EstrategiaRateio.PROPORCIONAL, null, null);
        service.aplicarRateio(1L, req);

        assertThat(p10.getValorRateio()).isEqualByComparingTo("15.00");
        assertThat(p11.getValorRateio()).isEqualByComparingTo("5.00");
    }

    // ---------------------------------------------------------------
    // POR_ITEM
    // ---------------------------------------------------------------

    @Test
    void aplicarRateio_porItem_semNenhumItemMarcado_lanca422() {
        Comanda p10 = individual(10L, BigDecimal.ZERO, true);
        Comanda c = compartilhada(StatusComanda.AGUARDANDO_PAGAMENTO, new BigDecimal("30.00"), List.of(p10));
        Produto pizza = Produto.builder().id(1L).nome("Pizza").build();
        ItemComanda item = ItemComanda.builder().id(100L).produto(pizza)
                .status(StatusItemComanda.ENTREGUE).subtotal(new BigDecimal("30.00")).build();

        permitirAdminOuCaixa();
        when(comandaRepository.findById(1L)).thenReturn(Optional.of(c));

        RateioComandaRequest req = new RateioComandaRequest(EstrategiaRateio.POR_ITEM, null, List.of());

        assertThatThrownBy(() -> service.aplicarRateio(1L, req))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("itensConsumidores");
    }

    @Test
    void aplicarRateio_porItem_faltaUmItemNaLista_lanca422() {
        Comanda p10 = individual(10L, BigDecimal.ZERO, true);
        Comanda c = compartilhada(StatusComanda.AGUARDANDO_PAGAMENTO, new BigDecimal("40.00"), List.of(p10));
        Produto pizza = Produto.builder().id(1L).nome("Pizza").build();
        Produto refri = Produto.builder().id(2L).nome("Refrigerante").build();
        ItemComanda itemPizza = ItemComanda.builder().id(100L).produto(pizza)
                .status(StatusItemComanda.ENTREGUE).subtotal(new BigDecimal("30.00")).build();
        ItemComanda itemRefri = ItemComanda.builder().id(101L).produto(refri)
                .status(StatusItemComanda.ENTREGUE).subtotal(new BigDecimal("10.00")).build();

        permitirAdminOuCaixa();
        when(comandaRepository.findById(1L)).thenReturn(Optional.of(c));
        when(itemComandaRepository.findByComandaId(1L)).thenReturn(List.of(itemPizza, itemRefri));

        // Só marca a pizza — falta o refrigerante.
        List<RateioConsumidoresItem> consumo = List.of(new RateioConsumidoresItem(100L, List.of(10L)));
        RateioComandaRequest req = new RateioComandaRequest(EstrategiaRateio.POR_ITEM, null, consumo);

        assertThatThrownBy(() -> service.aplicarRateio(1L, req))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("consumidores do item 101");
    }

    @Test
    void aplicarRateio_porItem_rateiaCadaItemEntreSeusConsumidores() {
        Comanda p10 = individual(10L, BigDecimal.ZERO, true);
        Comanda p11 = individual(11L, BigDecimal.ZERO, true);
        Comanda c = compartilhada(StatusComanda.AGUARDANDO_PAGAMENTO, new BigDecimal("40.00"), List.of(p10, p11));

        Produto pizza = Produto.builder().id(1L).nome("Pizza").build();
        Produto refri = Produto.builder().id(2L).nome("Refrigerante").build();
        ItemComanda itemPizza = ItemComanda.builder().id(100L).produto(pizza)
                .status(StatusItemComanda.ENTREGUE).subtotal(new BigDecimal("30.00")).build();
        ItemComanda itemRefri = ItemComanda.builder().id(101L).produto(refri)
                .status(StatusItemComanda.ENTREGUE).subtotal(new BigDecimal("10.00")).build();

        permitirAdminOuCaixa();
        when(comandaRepository.findById(1L)).thenReturn(Optional.of(c));
        when(itemComandaRepository.findByComandaId(1L)).thenReturn(List.of(itemPizza, itemRefri));

        List<RateioConsumidoresItem> consumo = List.of(
                new RateioConsumidoresItem(100L, List.of(10L, 11L)),
                new RateioConsumidoresItem(101L, List.of(10L)));
        RateioComandaRequest req = new RateioComandaRequest(EstrategiaRateio.POR_ITEM, null, consumo);
        service.aplicarRateio(1L, req);

        assertThat(p10.getValorRateio()).isEqualByComparingTo("25.00"); // 15 (pizza) + 10 (refri)
        assertThat(p11.getValorRateio()).isEqualByComparingTo("15.00"); // 15 (pizza)
    }

    // ---------------------------------------------------------------
    // Consultar / Reverter
    // ---------------------------------------------------------------

    @Test
    void buscarRateio_comandaIndividual_lanca422() {
        Comanda individual = individual(10L, BigDecimal.ZERO, true);
        when(comandaService.buscarPorId(10L)).thenReturn(individual);

        assertThatThrownBy(() -> service.buscarRateio(10L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("COMPARTILHADA");
    }

    @Test
    void reverterRateio_semRateioAplicado_lanca422() {
        Comanda c = compartilhada(StatusComanda.AGUARDANDO_PAGAMENTO, new BigDecimal("10.00"), List.of());
        permitirAdminOuCaixa();
        when(comandaRepository.findById(1L)).thenReturn(Optional.of(c));

        assertThatThrownBy(() -> service.reverterRateio(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Nenhum rateio");
    }

    @Test
    void reverterRateio_zeraValoresDeTodasAsIndividuais() {
        Comanda p10 = individual(10L, BigDecimal.ZERO, true);
        p10.setValorRateio(new BigDecimal("20.00"));
        Comanda c = compartilhada(StatusComanda.AGUARDANDO_PAGAMENTO, new BigDecimal("20.00"), List.of(p10));
        c.setEstrategiaRateio(EstrategiaRateio.SEM_RATEIO);

        permitirAdminOuCaixa();
        when(comandaRepository.findById(1L)).thenReturn(Optional.of(c));

        service.reverterRateio(1L);

        assertThat(p10.getValorRateio()).isEqualByComparingTo("0.00");
        assertThat(c.getEstrategiaRateio()).isNull();
    }
}
