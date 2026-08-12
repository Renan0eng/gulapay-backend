package br.unipar.foodservice.controllers;

import br.unipar.foodservice.dtos.EventoItemComandaResponse;
import br.unipar.foodservice.dtos.ItemComandaCancelRequest;
import br.unipar.foodservice.dtos.ItemComandaCreateRequest;
import br.unipar.foodservice.dtos.ItemComandaResponse;
import br.unipar.foodservice.dtos.ItemComandaTransferRequest;
import br.unipar.foodservice.dtos.ItemComandaUpdateRequest;
import br.unipar.foodservice.entities.ItemComanda;
import br.unipar.foodservice.services.ItemComandaService;
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
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

/**
 * Endpoints do {@code ItemComanda} (Sprint 2 — BACKLOG seção 6).
 *
 * <p>Rotas espelham a estrutura sugerida — criação é "filha" da comanda,
 * as demais operações usam o id do próprio item:
 *
 * <ul>
 *   <li>{@code POST   /comandas/{id}/itens}              — lança item</li>
 *   <li>{@code PATCH  /itens-comanda/{id}}               — edita (apenas EM_PREPARO)</li>
 *   <li>{@code POST   /itens-comanda/{id}/entregar}      — marca como ENTREGUE</li>
 *   <li>{@code POST   /itens-comanda/{id}/transferir}    — transfere (mesma mesa)</li>
 *   <li>{@code POST   /itens-comanda/{id}/cancelar}      — cancela com motivo</li>
 *   <li>{@code GET    /itens-comanda/{id}/eventos}       — timeline de auditoria</li>
 * </ul>
 *
 * <p>Permissões da matriz 5.2.1 são aplicadas dentro do service — todos os
 * endpoints aceitam ADM/CAIXA/GARCOM no {@code @PreAuthorize} e o
 * refinamento (garçom dono, ENTREGUE só caixa/admin, etc.) acontece em
 * tempo de execução.
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "Itens de Comanda",
     description = "Lançamento e correção de itens (edição, transferência, cancelamento) com auditoria — Sprint 2")
public class ItemComandaController {

    private final ItemComandaService service;

    @PostMapping("/comandas/{comandaId}/itens")
    @PreAuthorize("hasAnyRole('ADMINISTRADOR','CAIXA','GARCOM')")
    @Operation(summary = "Lança um novo item na comanda. Nasce em EM_PREPARO. " +
            "Garçom só lança em comandas próprias.")
    public ResponseEntity<ItemComandaResponse> criar(
            @PathVariable Long comandaId,
            @Valid @RequestBody ItemComandaCreateRequest req) {
        ItemComanda novo = service.criar(comandaId, req);
        URI location = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/itens-comanda/{id}").buildAndExpand(novo.getId()).toUri();
        return ResponseEntity.created(location).body(ItemComandaResponse.from(novo));
    }

    @PatchMapping("/itens-comanda/{id}")
    @PreAuthorize("hasAnyRole('ADMINISTRADOR','CAIXA','GARCOM')")
    @Operation(summary = "Atualiza quantidade, descontos ou observação. " +
            "Permitido apenas enquanto o item está em EM_PREPARO.")
    public ResponseEntity<ItemComandaResponse> editar(
            @PathVariable Long id,
            @Valid @RequestBody ItemComandaUpdateRequest req) {
        return ResponseEntity.ok(ItemComandaResponse.from(service.editar(id, req)));
    }

    @PostMapping("/itens-comanda/{id}/entregar")
    @PreAuthorize("hasAnyRole('ADMINISTRADOR','CAIXA','GARCOM')")
    @Operation(summary = "Marca o item como ENTREGUE. Apenas a partir de EM_PREPARO.")
    public ResponseEntity<ItemComandaResponse> marcarEntregue(@PathVariable Long id) {
        return ResponseEntity.ok(ItemComandaResponse.from(service.marcarEntregue(id)));
    }

    @PostMapping("/itens-comanda/{id}/transferir")
    @PreAuthorize("hasAnyRole('ADMINISTRADOR','CAIXA','GARCOM')")
    @Operation(summary = "Transfere o item para outra comanda da mesma mesa. " +
            "Em ENTREGUE, apenas Caixa/Admin. Sem impacto em estoque.")
    public ResponseEntity<ItemComandaResponse> transferir(
            @PathVariable Long id,
            @Valid @RequestBody ItemComandaTransferRequest req) {
        return ResponseEntity.ok(ItemComandaResponse.from(service.transferir(id, req)));
    }

    @PostMapping("/itens-comanda/{id}/cancelar")
    @PreAuthorize("hasAnyRole('ADMINISTRADOR','CAIXA','GARCOM')")
    @Operation(summary = "Cancela o item com motivo (obrigatório). " +
            "Em ENTREGUE, apenas Caixa/Admin. Sem impacto em estoque.")
    public ResponseEntity<ItemComandaResponse> cancelar(
            @PathVariable Long id,
            @Valid @RequestBody ItemComandaCancelRequest req) {
        return ResponseEntity.ok(ItemComandaResponse.from(service.cancelar(id, req)));
    }

    @GetMapping("/itens-comanda/{id}/eventos")
    @PreAuthorize("hasAnyRole('ADMINISTRADOR','CAIXA','GARCOM')")
    @Operation(summary = "Linha do tempo de eventos do item (RNF09 — auditoria). " +
            "Ordem decrescente por dataHora.")
    public ResponseEntity<List<EventoItemComandaResponse>> eventos(@PathVariable Long id) {
        List<EventoItemComandaResponse> resposta = service.listarEventos(id).stream()
                .map(EventoItemComandaResponse::from)
                .toList();
        return ResponseEntity.ok(resposta);
    }
}
