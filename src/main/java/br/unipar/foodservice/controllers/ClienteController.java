package br.unipar.foodservice.controllers;

import br.unipar.foodservice.dtos.ClienteCreateRequest;
import br.unipar.foodservice.dtos.ClientePatchRequest;
import br.unipar.foodservice.dtos.ClienteResponse;
import br.unipar.foodservice.dtos.ClienteUpdateRequest;
import br.unipar.foodservice.entities.Cliente;
import br.unipar.foodservice.services.ClienteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
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

@RestController
@RequestMapping("/clientes")
@RequiredArgsConstructor
@Tag(name = "Clientes", description = "Cadastro de clientes — telefone único é a chave de identificação online")
public class ClienteController {

    private final ClienteService service;

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMINISTRADOR','CAIXA','GARCOM')")
    @Operation(summary = "Cadastra um cliente. Telefone obrigatório e único.")
    public ResponseEntity<ClienteResponse> criar(@Valid @RequestBody ClienteCreateRequest request) {
        Cliente novo = service.criar(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(novo.getId()).toUri();
        return ResponseEntity.created(location).body(ClienteResponse.from(novo));
    }

    /*
     * A escolha da consulta é feita pelo roteamento do Spring (condição
     * `params` de cada @GetMapping), não por if dentro de um método: cada
     * combinação de filtros tem seu próprio handler, já tipado com o retorno
     * correto. O `!telefone` no handler de nome torna os mapeamentos
     * mutuamente exclusivos e deixa a precedência do telefone explícita.
     */

    @GetMapping(params = "telefone")
    @Operation(summary = "Busca exata por telefone (chave única do cliente). "
            + "Retorna o cliente ou 404. Aceita o telefone formatado — é normalizado antes da consulta.")
    public ResponseEntity<ClienteResponse> buscarPorTelefone(@RequestParam String telefone) {
        return ResponseEntity.ok(ClienteResponse.from(service.buscarPorTelefone(telefone)));
    }

    @GetMapping(params = {"nome", "!telefone"})
    @Operation(summary = "Busca parcial por nome (case-insensitive), no máximo 20 resultados, "
            + "ordenados por nome. '%' e '_' valem como texto literal.")
    public ResponseEntity<List<ClienteResponse>> buscarPorNome(
            @RequestParam String nome,
            @RequestParam(name = "apenasAtivos", defaultValue = "false") boolean apenasAtivos) {
        List<ClienteResponse> resposta = service.buscarPorNome(nome, apenasAtivos).stream()
                .map(ClienteResponse::from).toList();
        return ResponseEntity.ok(resposta);
    }

    @GetMapping
    @Operation(summary = "Lista clientes. Use 'telefone' para busca exata ou 'nome' para busca parcial.")
    public ResponseEntity<List<ClienteResponse>> listar(
            @RequestParam(name = "apenasAtivos", defaultValue = "false") boolean apenasAtivos) {
        List<ClienteResponse> resposta = service.listar(apenasAtivos).stream()
                .map(ClienteResponse::from).toList();
        return ResponseEntity.ok(resposta);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Busca cliente por id.")
    public ResponseEntity<ClienteResponse> buscar(@PathVariable Long id) {
        return ResponseEntity.ok(ClienteResponse.from(service.buscarPorId(id)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMINISTRADOR','CAIXA','GARCOM')")
    @Operation(summary = "Atualiza um cliente (PUT — substituição completa).")
    public ResponseEntity<ClienteResponse> atualizar(@PathVariable Long id,
                                                     @Valid @RequestBody ClienteUpdateRequest request) {
        return ResponseEntity.ok(ClienteResponse.from(service.atualizar(id, request)));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMINISTRADOR','CAIXA','GARCOM')")
    @Operation(summary = "Atualização parcial. Útil para reativar via { \"ativo\": true } ou ajustar só o endereço.")
    public ResponseEntity<ClienteResponse> patch(@PathVariable Long id,
                                                 @Valid @RequestBody ClientePatchRequest request) {
        return ResponseEntity.ok(ClienteResponse.from(service.patch(id, request)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMINISTRADOR','CAIXA')")
    @Operation(summary = "Inativa (soft delete) um cliente.")
    public ResponseEntity<Void> inativar(@PathVariable Long id) {
        service.inativar(id);
        return ResponseEntity.noContent().build();
    }
}
