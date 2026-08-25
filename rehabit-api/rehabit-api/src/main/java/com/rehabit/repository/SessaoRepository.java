package com.rehabit.repository;

import com.rehabit.model.Sessao;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface SessaoRepository extends JpaRepository<Sessao, Integer> {

    List<Sessao> findByIdPacienteOrderByDataSessaoDescHoraSessaoDesc(Integer idPaciente);

    List<Sessao> findByIdFisioterapeuta(Integer idFisioterapeuta);

    List<Sessao> findByIdFisioterapeutaIn(List<Integer> idsFisioterapeuta);

    List<Sessao> findByIdFisioterapeutaInAndDataSessaoBetween(
            List<Integer> idsFisioterapeuta, LocalDate inicio, LocalDate fim);

    List<Sessao> findByIdFisioterapeutaInOrderByDataSessaoDescHoraSessaoDesc(List<Integer> idsFisioterapeuta);
}
