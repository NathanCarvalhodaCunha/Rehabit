package com.rehabit.repository;

import com.rehabit.model.Sessao;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SessaoRepository extends JpaRepository<Sessao, Integer> {

    List<Sessao> findByIdPacienteOrderByDataSessaoDescHoraSessaoDesc(Integer idPaciente);

    List<Sessao> findByIdFisioterapeuta(Integer idFisioterapeuta);

    List<Sessao> findByIdFisioterapeutaIn(List<Integer> idsFisioterapeuta);
}
