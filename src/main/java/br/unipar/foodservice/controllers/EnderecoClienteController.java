package br.unipar.foodservice.controllers;

import br.unipar.foodservice.dtos.EnderecoClienteRequest;
import br.unipar.foodservice.dtos.EnderecoClienteResponse;
import br.unipar.foodservice.entities.EnderecoCliente;
import br.unipar.foodservice.services.EnderecoClienteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

/**
 * Endpoints para gerenciar os endereços de um Cliente (entidade 1:N).
 *
 * <p>Padrão de rotas:
 * <ul>
 *   <li>{@code /clientes/{clienteId}/enderecos} para criação/listagem
 *       (recurso filho).</li>
 *   <li>{@code /enderecos/{id}} para operações sobre um endereço específico
 *       (atualizar, marcar como principal, soft delete).</li>
 * </ul>
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "Endereços de Cliente",
     description = "Cadastro 1:N de endereços por cliente — usado em comandas DELIVERY (Sprint 2)")
public class EnderecoClienteController {

    private final EnderecoClienteService service;

    @PostMapping("/clientes/{clienteId}/enderecos")
    @PreAuthorize("hasAnyRole('ADMINISTRADOR','CAIXA')")
    @Operation(summary = "Cadastra um endereço para o cliente. " +
            "Se principal=true (ou for o primeiro endereço), desmarca os demais.")
    public ResponseEntity<EnderecoClienteResponse> adicionar(
            @PathVariable Long clienteId,
            @Valid @RequestBody EnderecoClienteRequest request) {
        EnderecoCliente novo = service.adicionar(clienteId, request);
        URI location = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/enderecos/{id}").buildAndExpand(novo.getId()).toUri();
        return ResponseEntity.created(location).body(EnderecoClienteResponse.from(novo));
    }

    @GetMapping("/clientes/{clienteId}/enderecos")
    @PreAuthorize("hasAnyRole('ADMINISTRADOR','CAIXA','GARCOM')")
    @Operation(summary = "Lista endereços de um cliente. Por padrão retorna só os ativos.")
    public ResponseEntity<List<EnderecoClienteResponse>> listar(
            @PathVariable Long clienteId,
            @RequestParam(name = "apenasAtivos", defaultValue = "true") boolean apenasAtivos) {
        List<EnderecoClienteResponse> resposta = service.listarPorCliente(clienteId, apenasAtivos).stream()
                .map(EnderecoClienteResponse::from)
                .toList();
        return ResponseEntity.ok(resposta);
    }

    @GetMapping("/enderecos/{id}")
    @PreAuthorize("hasAnyRole('ADMINISTRADOR','CAIXA','GARCOM')")
    @Operation(summary = "Busca um endereço pelo id.")
    public ResponseEntity<EnderecoClienteResponse> buscar(@PathVariable Long id) {
        return ResponseEntity.ok(EnderecoClienteResponse.from(service.buscarPorId(id)));
    }

    @PutMapping("/enderecos/{id}")
    @PreAuthorize("hasAnyRole('ADMINISTRADOR','CAIXA')")
    @Operation(summary = "Atualiza um endereço (PUT — substitui todos os campos editáveis).")
    public ResponseEntity<EnderecoClienteResponse> atualizar(
            @PathVariable Long id,
            @Valid @RequestBody EnderecoClienteRequest request) {
        return ResponseEntity.ok(EnderecoClienteResponse.from(service.atualizar(id, request)));
    }

    @PostMapping("/enderecos/{id}/principal")
    @PreAuthorize("hasAnyRole('ADMINISTRADOR','CAIXA')")
    @Operation(summary = "Marca este endereço como principal do cliente, " +
            "desmarcando os demais. O endereço precisa estar ativo.")
    public ResponseEntity<EnderecoClienteResponse> marcarPrincipal(@PathVariable Long id) {
        return ResponseEntity.ok(EnderecoClienteResponse.from(service.marcarComoPrincipal(id)));
    }

    @DeleteMapping("/enderecos/{id}")
    @PreAuthorize("hasAnyRole('ADMINISTRADOR','CAIXA')")
    @Operation(summary = "Soft delete do endereço (ativo=false). " +
            "Bloqueado se houver comanda DELIVERY ativa referenciando " +
            "(verificação completa na Sprint 2, após criação da entidade Comanda).")
    public ResponseEntity<Void> remover(@PathVariable Long id) {
        service.removerEndereco(id);
        return ResponseEntity.noContent().build();
    }
}
