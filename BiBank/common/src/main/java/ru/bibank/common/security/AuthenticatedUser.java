package ru.bibank.common.security;

/**
 * Пользователь, извлечённый из проверенного JWT access token.
 */
public record AuthenticatedUser(Long id, String email, String role, String name) {

    public boolean isAdmin() {
        return "ROLE_ADMIN".equals(role);
    }
}
