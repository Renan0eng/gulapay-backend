package br.unipar.foodservice.controllers;

import br.unipar.foodservice.dtos.RateioComandaRequest;
import br.unipar.foodservice.dtos.RateioComandaResponse;
import br.unipar.foodservice.dtos.RateioParticipantesRequest;
import br.unipar.foodservice.services.RateioService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints do rateio de {@code Comanda COMPARTILHADA} (Sprint 3 —
 * BACKLOG seção 6, PLANO.md seção 7.1):
 *
 * <ul>
 *   <li>{@code PATCH POST /comandas/{id}/participantes}    — marca participantes</li>
 *   <li>{@code POST  /comandas/{id}/rateio}                — calcula e aplica</li>
 *   <li>{@code GET   /comandas/{id}/rateio}                — consulta</li>
 *   <li>{@code POST  /comandas/{id}/rateio/reverter}       — desfaz</li>
 * </ul>
 *
 * <p>A comanda precisa estar {@code AGUARDANDO_PAGAMENTO} (já fechada via
 * {@code POST /comandas/{id}/fechar}, Sprint 2) para o rateio ser aplicado.
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "Rateio de Comanda",
     description = "Divide o total de uma comanda COMPARTILHADA entre as INDIVIDUAIS vinculadas — Sprint 3")
public class RateioController {

    private final RateioService service;

    @PatchMapping("/comandas/{id}/participantes")
    @PreAuthorize("hasAnyRole('ADMINISTRADOR','CAIXA')")
    @Operation(summary = "Define quais comandas INDIVIDUAIS participam do rateio da COMPARTILHADA. "
            + "Ids ausentes na lista são desmarcados.")
    public ResponseEntity<RateioComandaResponse> definirParticipantes(
            @PathVariable Long id,
            @Valid @RequestBody RateioParticipantesRequest req) {
        return ResponseEntity.ok(service.definirParticipantes(id, req));
    }

    @PostMapping("/comandas/{id}/rateio")
    @PreAuthorize("hasAnyRole('ADMINISTRADOR','CAIXA')")
    @Operation(summary = "Calcula e aplica o rateio (SEM_RATEIO/IGUALITARIO/MANUAL/PROPORCIONAL/POR_ITEM). "
            + "Exige a comanda AGUARDANDO_PAGAMENTO e ao menos 1 participante selecionado.")
    public ResponseEntity<RateioComandaResponse> aplicar(
            @PathVariable Long id,
            @Valid @RequestBody RateioComandaRequest req) {
        return ResponseEntity.ok(service.aplicarRateio(id, req));
    }

    @GetMapping("/comandas/{id}/rateio")
    @PreAuthorize("hasAnyRole('ADMINISTRADOR','CAIXA','GARCOM')")
    @Operation(summary = "Consulta o rateio já aplicado (ou os participantes marcados, se ainda não aplicado). "
            + "Garçom só consegue ver as próprias comandas.")
    public ResponseEntity<RateioComandaResponse> buscar(@PathVariable Long id) {
        return ResponseEntity.ok(service.buscarRateio(id));
    }

    @PostMapping("/comandas/{id}/rateio/reverter")
    @PreAuthorize("hasAnyRole('ADMINISTRADOR','CAIXA')")
    @Operation(summary = "Desfaz o rateio aplicado (zera os valores recebidos pelas individuais) "
            + "e permite recalcular com outra estratégia.")
    public ResponseEntity<RateioComandaResponse> reverter(@PathVariable Long id) {
        return ResponseEntity.ok(service.reverterRateio(id));
    }
}
