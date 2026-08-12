package br.unipar.foodservice.services;

import br.unipar.foodservice.entities.Usuario;
import br.unipar.foodservice.enums.Perfil;
import br.unipar.foodservice.exceptions.BusinessException;
import br.unipar.foodservice.repositories.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Encapsula a busca do {@link Usuario} autenticado para uso pelos services
 * que precisam registrar auditoria (Sprint 2 — Comanda/ItemComanda).
 *
 * <p>Centraliza:
 * <ul>
 *   <li>Resolução do {@code login} a partir do {@code SecurityContextHolder}.</li>
 *   <li>Carregamento da entidade {@code Usuario} para enriquecer eventos.</li>
 *   <li>Helpers de perfil (é admin? é caixa? é garçom?).</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class UsuarioAutenticadoService {

    private final UsuarioRepository usuarioRepository;

    /**
     * Login (claim {@code sub} do JWT) do usuário corrente.
     *
     * @return login ou {@code null} se não houver autenticação ativa.
     */
    public String loginCorrente() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth == null ? null : auth.getName();
    }

    /**
     * Carrega a entidade {@code Usuario} do banco a partir do login do
     * SecurityContext. Lança 401 quando não há autenticação ou o usuário
     * referenciado não existe mais.
     */
    @Transactional(readOnly = true)
    public Usuario usuarioCorrente() {
        String login = loginCorrente();
        if (login == null || login.isBlank()) {
            throw new AccessDeniedException("Não há usuário autenticado.");
        }
        return usuarioRepository.findByLogin(login)
                .orElseThrow(() -> new AccessDeniedException(
                        "Usuário autenticado não foi encontrado no banco: " + login));
    }

    /**
     * Carrega o {@link Usuario} cujo {@code id} foi informado, validando a
     * existência. Usado por serviços que precisam atribuir um usuário a
     * uma entidade (ex.: {@code garcomId} de uma Comanda).
     */
    @Transactional(readOnly = true)
    public Usuario carregar(Long id) {
        return usuarioRepository.findById(id)
                .orElseThrow(() -> new BusinessException("Usuário não encontrado: " + id));
    }

    public boolean ehAdministrador(Usuario u) {
        return u != null && u.getPerfil() == Perfil.ADMINISTRADOR;
    }

    public boolean ehCaixa(Usuario u) {
        return u != null && u.getPerfil() == Perfil.CAIXA;
    }

    public boolean ehGarcom(Usuario u) {
        return u != null && u.getPerfil() == Perfil.GARCOM;
    }

    public boolean ehAdminOuCaixa(Usuario u) {
        return ehAdministrador(u) || ehCaixa(u);
    }
}
