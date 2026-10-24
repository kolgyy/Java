package ru.bibank.auth.dto;

import jakarta.validation.constraints.NotNull;
import ru.bibank.auth.entity.UserStatus;

public record ChangeStatusRequest(@NotNull UserStatus status) {
}
