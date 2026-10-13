package com.rehabit.dto;

/**
 * Resposta da troca de senha. A troca encerra todas as sessões da conta; o
 * token vem para o aparelho que trocou continuar logado.
 */
public record SenhaAlteradaDTO(String mensagem, String token) {
}
