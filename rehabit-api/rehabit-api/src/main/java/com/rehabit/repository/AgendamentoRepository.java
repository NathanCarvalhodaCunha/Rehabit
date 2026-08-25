package com.rehabit.repository;

import com.rehabit.model.Agendamento;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface AgendamentoRepository extends JpaRepository<Agendamento, Integer> {

    List<Agendamento> findByIdFisioterapeutaAndDataAgendamentoGreaterThanEqualOrderByDataAgendamentoAscHoraAgendamentoAsc(
            Integer idFisioterapeuta, LocalDate dataAgendamento);
}
