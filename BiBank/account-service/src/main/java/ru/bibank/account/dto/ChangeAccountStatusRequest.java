package ru.bibank.account.dto;

import jakarta.validation.constraints.NotNull;
import ru.bibank.account.entity.AccountStatus;

public record ChangeAccountStatusRequest(@NotNull AccountStatus status) {
}
