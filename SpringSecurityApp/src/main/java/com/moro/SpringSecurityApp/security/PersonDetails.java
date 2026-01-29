package com.moro.SpringSecurityApp.security;

import com.moro.SpringSecurityApp.models.Person;
import org.jspecify.annotations.Nullable;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.Collections;

public class PersonDetails implements UserDetails {
    private final Person person;

    public PersonDetails(Person person) {
        this.person = person;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true; // Возвращаем true, аккаунт действителен
    }

    @Override
    public boolean isAccountNonLocked() {
        return true; // Аккаунт не заблокирован
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true; // Пароль не просрочен
    }

    @Override
    public boolean isEnabled() {
        return true; // Аккаунт включён, работает
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return Collections.singletonList(new SimpleGrantedAuthority(person.getRole()));
    }

    @Override
    public @Nullable String getPassword() {
        return this.person.getPassword(); // Возвращаем пароль
    }

    @Override
    public String getUsername() {
        return this.person.getUsername(); // Возвращаем username
    }
    // Нужно, чтобы получать данные аутентифицированного пользователя.
    public Person getPerson() {
        return this.person;
    }
}
