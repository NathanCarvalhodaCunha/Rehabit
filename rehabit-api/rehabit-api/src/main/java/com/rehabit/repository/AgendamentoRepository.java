package com.rehabit.repository;

import com.rehabit.model.Agendamento;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

public interface AgendamentoRepository extends JpaRepository<Agendamento, Integer> {

    List<Agendamento> findByIdFisioterapeutaAndDataAgendamentoGreaterThanEqualOrderByDataAgendamentoAscHoraAgendamentoAsc(
            Integer idFisioterapeuta, LocalDate dataAgendamento);

    List<Agendamento> findByIdFisioterapeutaInAndDataAgendamentoBetweenOrderByDataAgendamentoAscHoraAgendamentoAsc(
            List<Integer> idsFisioterapeuta, LocalDate inicio, LocalDate fim);

    List<Agendamento> findByIdFisioterapeutaInOrderByDataAgendamentoDescHoraAgendamentoDesc(
            List<Integer> idsFisioterapeuta);

    /** Agendamentos do profissional num dia — base da checagem de conflito de horário. */
    List<Agendamento> findByIdFisioterapeutaAndDataAgendamento(Integer idFisioterapeuta, LocalDate data);

    /** Consultas já passadas de um profissional, da mais recente para a mais antiga. */
    List<Agendamento> findByIdFisioterapeutaAndDataAgendamentoLessThanOrderByDataAgendamentoDescHoraAgendamentoDesc(
            Integer idFisioterapeuta, LocalDate dataAgendamento);

    long countByIdPacienteIn(Collection<Integer> idsPacientes);

    @Modifying
    @Query("delete from Agendamento a where a.idPaciente in :idsPacientes")
    void deleteByIdPacienteIn(@Param("idsPacientes") Collection<Integer> idsPacientes);
}
