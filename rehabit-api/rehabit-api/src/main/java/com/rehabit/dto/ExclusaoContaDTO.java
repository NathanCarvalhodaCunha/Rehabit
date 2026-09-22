package com.rehabit.dto;

import jakarta.validation.constraints.NotBlank;

/** O pedido de exclusão da conta: a senha confirma que é mesmo a dona da conta. */
public record ExclusaoContaDTO(@NotBlank(message = "Digite sua senha para confirmar.") String senha) {
}
