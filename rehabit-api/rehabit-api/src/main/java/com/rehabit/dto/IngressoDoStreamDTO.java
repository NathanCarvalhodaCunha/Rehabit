package com.rehabit.dto;

/** Resposta de POST /api/goniometro/stream/ingresso: vai na URL do EventSource, uma vez só. */
public record IngressoDoStreamDTO(String ingresso, long validadeSegundos) {
}
