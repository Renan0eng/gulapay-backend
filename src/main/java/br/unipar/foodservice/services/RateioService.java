package br.unipar.foodservice.services;

import br.unipar.foodservice.dtos.RateioComandaRequest;
import br.unipar.foodservice.dtos.RateioComandaResponse;
import br.unipar.foodservice.dtos.RateioConsumidoresItem;
import br.unipar.foodservice.dtos.RateioParticipantesRequest;
import br.unipar.foodservice.dtos.RateioValorManualItem;
import br.unipar.foodservice.entities.Comanda;
import br.unipar.foodservice.entities.ItemComanda;
import br.unipar.foodservice.entities.Usuario;
import br.unipar.foodservice.enums.EscopoComanda;
import br.unipar.foodservice.enums.EstrategiaRateio;
import br.unipar.foodservice.enums.StatusComanda;
import br.unipar.foodservice.enums.StatusItemComanda;
import br.unipar.foodservice.exceptions.BusinessException;
import br.unipar.foodservice.exceptions.ResourceNotFoundException;
import br.unipar.foodservice.repositories.ComandaRepository;
import br.unipar.foodservice.repositories.ItemComandaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Service do rateio de {@link Comanda} {@code COMPARTILHADA} (Sprint 3 —
 * seção 5.2 do CLAUDE.md, BACKLOG seção 6). Implementa as 5 estratégias
 * na ordem definida: {@code SEM_RATEIO} → {@code IGUALITARIO} →
 * {@code MANUAL} → {@code PROPORCIONAL} → {@code POR_ITEM}.
 *
 * <p>Fluxo esperado: a {@code COMPARTILHADA} acumula os itens consumidos
 * em conjunto; {@code fechar()} (Sprint 2) some com o total em
 * {@link StatusComanda#AGUARDANDO_PAGAMENTO}; o Caixa então marca quem
 * participa ({@link #definirParticipantes}) e aplica o rateio
 * ({@link #aplicarRateio}), distribuindo o total entre as
 * {@code INDIVIDUAIS} vinculadas. Cada individual paga seu total
 * (itens próprios + rateio recebido) na Sprint 4.
 */
@Service
@RequiredArgsConstructor
public class RateioService {

    private final ComandaRepository comandaRepository;
    private final ItemComandaRepository itemComandaRepository;
    private final ComandaService comandaService;
    private final UsuarioAutenticadoService usuarioAutenticadoService;

    // ---------------------------------------------------------------------
    // PARTICIPANTES
    // ---------------------------------------------------------------------

    @Transactional
    public RateioComandaResponse definirParticipantes(Long comandaId, RateioParticipantesRequest req) {
        Comanda compartilhada = carregarCompartilhadaParaEscrita(comandaId);
        if (compartilhada.getEstrategiaRateio() != null) {
            throw new BusinessException(
                    "Rateio já aplicado nesta comanda — reverta antes de alterar participantes.");
        }
        List<Long> selecionados = req.comandaIndividualIds() == null ? List.of() : req.comandaIndividualIds();
        for (Comanda individual : compartilhada.getIndividuais()) {
            individual.setParticipanteRateio(selecionados.contains(individual.getId()));
        }
        return RateioComandaResponse.from(compartilhada);
    }

    // ---------------------------------------------------------------------
    // APLICAR
    // ---------------------------------------------------------------------

    @Transactional
    public RateioComandaResponse aplicarRateio(Long comandaId, RateioComandaRequest req) {
        Comanda compartilhada = carregarCompartilhadaParaEscrita(comandaId);
        if (compartilhada.getStatus() != StatusComanda.AGUARDANDO_PAGAMENTO) {
            throw new BusinessException(
                    "Feche a comanda (POST /comandas/{id}/fechar) antes de aplicar o rateio "
                            + "(status atual: " + compartilhada.getStatus() + ").");
        }
        if (compartilhada.getEstrategiaRateio() != null) {
            throw new BusinessException(
                    "Rateio já aplicado nesta comanda — reverta (POST .../rateio/reverter) antes de reaplicar.");
        }

        List<Comanda> participantes = compartilhada.getIndividuais().stream()
                .filter(i -> Boolean.TRUE.equals(i.getParticipanteRateio()))
                .toList();
        if (participantes.isEmpty()) {
            throw new BusinessException(
                    "Nenhum participante selecionado — use PATCH /comandas/{id}/participantes antes do rateio.");
        }

        BigDecimal total = compartilhada.getTotalLiquido() == null ? BigDecimal.ZERO : compartilhada.getTotalLiquido();

        Map<Long, BigDecimal> valores = switch (req.estrategia()) {
            case SEM_RATEIO -> calcularSemRateio(total, participantes);
            case IGUALITARIO -> calcularIgualitario(total, participantes);
            case MANUAL -> calcularManual(total, participantes, req.valoresManuais());
            case PROPORCIONAL -> calcularProporcional(total, participantes);
            case POR_ITEM -> calcularPorItem(compartilhada, participantes, req.itensConsumidores());
        };

        for (Comanda individual : compartilhada.getIndividuais()) {
            BigDecimal valor = valores.getOrDefault(individual.getId(), BigDecimal.ZERO);
            individual.setValorRateio(valor);
        }
        compartilhada.setEstrategiaRateio(req.estrategia());

        return RateioComandaResponse.from(compartilhada);
    }

    private Map<Long, BigDecimal> calcularSemRateio(BigDecimal total, List<Comanda> participantes) {
        if (participantes.size() != 1) {
            throw new BusinessException(
                    "SEM_RATEIO exige exatamente 1 participante selecionado (encontrados: "
                            + participantes.size() + ").");
        }
        Map<Long, BigDecimal> valores = new HashMap<>();
        valores.put(participantes.get(0).getId(), total);
        return valores;
    }

    private Map<Long, BigDecimal> calcularIgualitario(BigDecimal total, List<Comanda> participantes) {
        List<BigDecimal> partes = dividirIgualmente(total, participantes.size());
        Map<Long, BigDecimal> valores = new HashMap<>();
        for (int i = 0; i < participantes.size(); i++) {
            valores.put(participantes.get(i).getId(), partes.get(i));
        }
        return valores;
    }

    private Map<Long, BigDecimal> calcularManual(BigDecimal total, List<Comanda> participantes,
                                                  List<RateioValorManualItem> valoresManuais) {
        if (valoresManuais == null || valoresManuais.isEmpty()) {
            throw new BusinessException("MANUAL exige a lista valoresManuais com um valor por participante.");
        }
        List<Long> idsParticipantes = participantes.stream().map(Comanda::getId).toList();
        List<Long> idsInformados = valoresManuais.stream().map(RateioValorManualItem::comandaIndividualId).toList();

        for (Long idInformado : idsInformados) {
            if (!idsParticipantes.contains(idInformado)) {
                throw new BusinessException(
                        "Comanda " + idInformado + " não é uma participante selecionada do rateio.");
            }
        }
        for (Long idParticipante : idsParticipantes) {
            if (!idsInformados.contains(idParticipante)) {
                throw new BusinessException(
                        "Falta o valor manual da comanda participante " + idParticipante + ".");
            }
        }

        Map<Long, BigDecimal> valores = new HashMap<>();
        BigDecimal soma = BigDecimal.ZERO;
        for (RateioValorManualItem item : valoresManuais) {
            valores.put(item.comandaIndividualId(), item.valor());
            soma = soma.add(item.valor());
        }
        if (soma.compareTo(total) != 0) {
            throw new BusinessException(
                    "Soma dos valores manuais (" + soma + ") é diferente do total da comanda (" + total + ").");
        }
        return valores;
    }

    private Map<Long, BigDecimal> calcularProporcional(BigDecimal total, List<Comanda> participantes) {
        List<BigDecimal> pesos = participantes.stream()
                .map(p -> p.getTotalLiquido() == null ? BigDecimal.ZERO : p.getTotalLiquido())
                .toList();
        BigDecimal somaPesos = pesos.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        if (somaPesos.compareTo(BigDecimal.ZERO) == 0) {
            throw new BusinessException(
                    "PROPORCIONAL exige que ao menos um participante tenha itens próprios lançados.");
        }
        List<BigDecimal> partes = dividirProporcional(total, pesos);
        Map<Long, BigDecimal> valores = new HashMap<>();
        for (int i = 0; i < participantes.size(); i++) {
            valores.put(participantes.get(i).getId(), partes.get(i));
        }
        return valores;
    }

    private Map<Long, BigDecimal> calcularPorItem(Comanda compartilhada, List<Comanda> participantes,
                                                   List<RateioConsumidoresItem> itensConsumidores) {
        if (itensConsumidores == null || itensConsumidores.isEmpty()) {
            throw new BusinessException("POR_ITEM exige a lista itensConsumidores com todos os itens ativos.");
        }
        List<Long> idsParticipantes = participantes.stream().map(Comanda::getId).toList();

        List<ItemComanda> itensAtivos = itemComandaRepository.findByComandaId(compartilhada.getId()).stream()
                .filter(i -> i.getStatus() != StatusItemComanda.CANCELADO
                        && i.getStatus() != StatusItemComanda.TRANSFERIDO)
                .toList();
        Map<Long, ItemComanda> itensPorId = new HashMap<>();
        itensAtivos.forEach(i -> itensPorId.put(i.getId(), i));

        List<Long> idsInformados = itensConsumidores.stream().map(RateioConsumidoresItem::itemComandaId).toList();
        for (ItemComanda item : itensAtivos) {
            if (!idsInformados.contains(item.getId())) {
                throw new BusinessException(
                        "Falta marcar os consumidores do item " + item.getId() + " (" + item.getProduto().getNome() + ").");
            }
        }

        Map<Long, BigDecimal> valores = new HashMap<>();
        participantes.forEach(p -> valores.put(p.getId(), BigDecimal.ZERO));

        for (RateioConsumidoresItem consumo : itensConsumidores) {
            ItemComanda item = itensPorId.get(consumo.itemComandaId());
            if (item == null) {
                throw new BusinessException(
                        "Item " + consumo.itemComandaId() + " não é um item ativo desta comanda.");
            }
            for (Long consumidorId : consumo.comandaIndividualIds()) {
                if (!idsParticipantes.contains(consumidorId)) {
                    throw new BusinessException(
                            "Item " + item.getId() + " aponta para a comanda " + consumidorId
                                    + ", que não é participante selecionada do rateio.");
                }
            }
            List<BigDecimal> partes = dividirIgualmente(item.getSubtotal(), consumo.comandaIndividualIds().size());
            for (int i = 0; i < consumo.comandaIndividualIds().size(); i++) {
                Long consumidorId = consumo.comandaIndividualIds().get(i);
                valores.merge(consumidorId, partes.get(i), BigDecimal::add);
            }
        }
        return valores;
    }

    // ---------------------------------------------------------------------
    // CONSULTAR / REVERTER
    // ---------------------------------------------------------------------

    @Transactional(readOnly = true)
    public RateioComandaResponse buscarRateio(Long comandaId) {
        // buscarPorId já aplica a regra "garçom só vê as próprias comandas".
        Comanda comanda = comandaService.buscarPorId(comandaId);
        validarEhCompartilhada(comanda);
        return RateioComandaResponse.from(comanda);
    }

    @Transactional
    public RateioComandaResponse reverterRateio(Long comandaId) {
        Comanda compartilhada = carregarCompartilhadaParaEscrita(comandaId);
        if (compartilhada.getEstrategiaRateio() == null) {
            throw new BusinessException("Nenhum rateio aplicado nesta comanda.");
        }
        for (Comanda individual : compartilhada.getIndividuais()) {
            individual.setValorRateio(BigDecimal.ZERO);
        }
        compartilhada.setEstrategiaRateio(null);
        return RateioComandaResponse.from(compartilhada);
    }

    // ---------------------------------------------------------------------
    // HELPERS
    // ---------------------------------------------------------------------

    private Comanda carregarCompartilhadaParaEscrita(Long comandaId) {
        Usuario corrente = usuarioAutenticadoService.usuarioCorrente();
        if (!usuarioAutenticadoService.ehAdminOuCaixa(corrente)) {
            throw new AccessDeniedException("Apenas Caixa/Admin podem gerenciar o rateio de uma comanda.");
        }
        Comanda comanda = comandaRepository.findById(comandaId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Comanda não encontrada: " + comandaId));
        validarEhCompartilhada(comanda);
        return comanda;
    }

    private void validarEhCompartilhada(Comanda comanda) {
        if (comanda.getEscopo() != EscopoComanda.COMPARTILHADA) {
            throw new BusinessException(
                    "Rateio só se aplica a comandas COMPARTILHADA (comanda " + comanda.getId()
                            + " é " + comanda.getEscopo() + ").");
        }
    }

    /** Divide {@code total} em {@code n} partes de 2 casas decimais cuja soma bate exatamente com o total. */
    private List<BigDecimal> dividirIgualmente(BigDecimal total, int n) {
        BigDecimal base = total.divide(BigDecimal.valueOf(n), 2, RoundingMode.DOWN);
        BigDecimal somaBase = base.multiply(BigDecimal.valueOf(n));
        int centavosResto = total.subtract(somaBase).movePointRight(2).intValue();

        List<BigDecimal> partes = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            partes.add(i < centavosResto ? base.add(new BigDecimal("0.01")) : base);
        }
        return partes;
    }

    /** Divide {@code total} proporcionalmente aos {@code pesos}, fechando a soma com o total. */
    private List<BigDecimal> dividirProporcional(BigDecimal total, List<BigDecimal> pesos) {
        BigDecimal somaPesos = pesos.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        List<BigDecimal> partes = new ArrayList<>();
        for (BigDecimal peso : pesos) {
            partes.add(total.multiply(peso).divide(somaPesos, 2, RoundingMode.DOWN));
        }
        BigDecimal somaPartes = partes.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        int centavosResto = total.subtract(somaPartes).movePointRight(2).intValue();
        for (int i = 0; i < centavosResto && i < partes.size(); i++) {
            partes.set(i, partes.get(i).add(new BigDecimal("0.01")));
        }
        return partes;
    }
}
