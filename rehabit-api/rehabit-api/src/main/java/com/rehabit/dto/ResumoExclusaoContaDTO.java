package com.rehabit.dto;

/**
 * O tamanho do que a exclusão da conta vai apagar, para a tela mostrar antes
 * de a clínica confirmar. Profissionais conta só os ativos, que são os que a
 * clínica enxerga.
 */
public record ResumoExclusaoContaDTO(long profissionais, long pacientes, long sessoes,
                                     long agendamentos, long dispositivos) {
}
