package ru.bibank.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(

        @NotBlank(message = "Укажите email")
        @Email(message = "Некорректный email")
        @Size(max = 255)
        String email,

        @NotBlank(message = "Укажите пароль")
        @Size(min = 8, max = 72, message = "Пароль должен содержать от 8 до 72 символов")
        @Pattern(regexp = "^(?=.*[A-Za-zА-Яа-я])(?=.*\\d).+$",
                message = "Пароль должен содержать буквы и цифры")
        String password,

        @NotBlank(message = "Укажите имя")
        @Size(min = 2, max = 255, message = "Имя должно содержать от 2 до 255 символов")
        String fullName

) {
}
