package com.rehabit.dto;

/**
 * O que a exclusão de um profissional moveu, para a tela contar à clínica.
 *
 * {@code colisoesDeAgenda} são consultas transferidas que caíram em horário
 * já ocupado na agenda de quem recebeu. A transferência não barra por causa
 * delas — a clínica decide o que fazer —, mas precisa ficar sabendo.
 */
public record ExclusaoProfissionalDTO(int pacientesTransferidos, int consultasTransferidas, int colisoesDeAgenda) {
}
