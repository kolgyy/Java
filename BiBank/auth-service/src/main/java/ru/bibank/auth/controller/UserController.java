package ru.bibank.auth.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import ru.bibank.auth.dto.UpdateProfileRequest;
import ru.bibank.auth.dto.UserResponse;
import ru.bibank.auth.service.UserService;
import ru.bibank.common.security.CurrentUser;

@RestController
@RequestMapping("/api/users/me")
@Tag(name = "Профиль")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    @Operation(summary = "Профиль текущего пользователя")
    public UserResponse me() {
        return userService.get(CurrentUser.id());
    }

    @PatchMapping
    @Operation(summary = "Изменение имени")
    public UserResponse update(@Valid @RequestBody UpdateProfileRequest request) {
        return userService.updateProfile(CurrentUser.id(), request);
    }
}
