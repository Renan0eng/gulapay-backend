package br.unipar.foodservice.controllers;

import br.unipar.foodservice.dtos.ComandaCreateRequest;
import br.unipar.foodservice.dtos.ComandaPatchRequest;
import br.unipar.foodservice.dtos.ComandaResponse;
import br.unipar.foodservice.entities.Comanda;
import br.unipar.foodservice.enums.StatusComanda;
import br.unipar.foodservice.enums.TipoOrigemComanda;
import br.unipar.foodservice.services.ComandaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Endpoints da Comanda (Sprint 2). Estrutura espelha o BACKLOG seção 6:
 *
 * <ul>
 *   <li>{@code POST /comandas}                 — criar (ADM/CAIXA/GARCOM)</li>
 *   <li>{@code GET /comandas}                  — listar com filtros</li>
 *   <li>{@code GET /comandas/{id}}             — buscar por id</li>
 *   <li>{@code PATCH /comandas/{id}}           — editar parcial</li>
 *   <li>{@code POST /comandas/{id}/fechar}     — fecha + SAIDA_VENDA UNITARIO (ADM/CAIXA)</li>
 *   <li>{@code POST /comandas/{id}/cancelar}   — cancela (ADM/CAIXA)</li>
 *   <li>{@code POST /comandas/{id}/reabrir}    — reabre AGUARDANDO_PAGAMENTO (ADM)</li>
 * </ul>
 *
 * <p>Filtros aplicáveis em {@code GET /comandas}:
 * {@code status, tipoOrigem, mesaId, clienteId, garcomId, dataInicio, dataFim}.
 * Garçom só visualiza as próprias comandas (filtro automático no service).
 */
@RestController
@RequestMapping("/comandas")
@RequiredArgsConstructor
@Tag(name = "Comandas", description = "Unidade de venda nos 3 canais (MESA, BALCAO, DELIVERY) — Sprint 2")
public class ComandaController {

    private final ComandaService service;

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMINISTRADOR','CAIXA','GARCOM')")
    @Operation(summary = "Cria uma comanda. Telefone do cliente obrigatório (gera link wa.me). " +
            "MESA exige mesaId + garcomId; DELIVERY exige enderecoEntregaId pertencente ao cliente.")
    public ResponseEntity<ComandaResponse> criar(@Valid @RequestBody ComandaCreateRequest req) {
        Comanda nova = service.criar(req);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(nova.getId()).toUri();
        return ResponseEntity.created(location).body(ComandaResponse.from(nova));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMINISTRADOR','CAIXA','GARCOM')")
    @Operation(summary = "Lista comandas com filtros opcionais. Garçom só vê as próprias.")
    public ResponseEntity<List<ComandaResponse>> listar(
            @RequestParam(required = false) StatusComanda status,
            @RequestParam(required = false) TipoOrigemComanda tipoOrigem,
            @RequestParam(required = false) Long mesaId,
            @RequestParam(required = false) Long clienteId,
            @RequestParam(required = false) Long garcomId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            LocalDateTime dataInicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            LocalDateTime dataFim) {
        List<ComandaResponse> resposta = service
                .listar(status, tipoOrigem, mesaId, clienteId, garcomId, dataInicio, dataFim)
                .stream()
                .map(ComandaResponse::summary)
                .toList();
        return ResponseEntity.ok(resposta);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMINISTRADOR','CAIXA','GARCOM')")
    @Operation(summary = "Detalha uma comanda + itens. Garçom só consegue ver as próprias.")
    public ResponseEntity<ComandaResponse> buscar(@PathVariable Long id) {
        return ResponseEntity.ok(ComandaResponse.from(service.buscarPorId(id)));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMINISTRADOR','CAIXA','GARCOM')")
    @Operation(summary = "Atualização parcial — observação (garçom dono/caixa/admin), " +
            "garçom (apenas caixa/admin), endereço de entrega (apenas caixa/admin em DELIVERY).")
    public ResponseEntity<ComandaResponse> patch(@PathVariable Long id,
                                                 @Valid @RequestBody ComandaPatchRequest req) {
        return ResponseEntity.ok(ComandaResponse.from(service.patch(id, req)));
    }

    @PostMapping("/{id}/fechar")
    @PreAuthorize("hasAnyRole('ADMINISTRADOR','CAIXA')")
    @Operation(summary = "Fecha a comanda: dispara SAIDA_VENDA para UNITARIO entregue. " +
            "Bloqueia se houver item em EM_PREPARO ou COMPOSTO/COMBO (Sprint 5).")
    public ResponseEntity<ComandaResponse> fechar(@PathVariable Long id) {
        return ResponseEntity.ok(ComandaResponse.from(service.fechar(id)));
    }

    @PostMapping("/{id}/cancelar")
    @PreAuthorize("hasAnyRole('ADMINISTRADOR','CAIXA')")
    @Operation(summary = "Cancela a comanda (sem baixa de estoque). Apenas Caixa/Admin.")
    public ResponseEntity<ComandaResponse> cancelar(@PathVariable Long id) {
        return ResponseEntity.ok(ComandaResponse.from(service.cancelar(id)));
    }

    @PostMapping("/{id}/reabrir")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    @Operation(summary = "Reabre uma comanda em AGUARDANDO_PAGAMENTO (apenas Administrador).")
    public ResponseEntity<ComandaResponse> reabrir(@PathVariable Long id) {
        return ResponseEntity.ok(ComandaResponse.from(service.reabrir(id)));
    }
}
