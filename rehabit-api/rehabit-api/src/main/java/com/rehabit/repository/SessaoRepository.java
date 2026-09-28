package com.rehabit.repository;

import com.rehabit.model.Sessao;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

public interface SessaoRepository extends JpaRepository<Sessao, Integer> {

    List<Sessao> findByIdPacienteOrderByDataSessaoDescHoraSessaoDesc(Integer idPaciente);

    List<Sessao> findByIdFisioterapeuta(Integer idFisioterapeuta);

    List<Sessao> findByIdFisioterapeutaIn(List<Integer> idsFisioterapeuta);

    List<Sessao> findByIdFisioterapeutaInAndDataSessaoBetween(
            List<Integer> idsFisioterapeuta, LocalDate inicio, LocalDate fim);

    List<Sessao> findByIdFisioterapeutaInOrderByDataSessaoDescHoraSessaoDesc(List<Integer> idsFisioterapeuta);

    List<Sessao> findByIdFisioterapeutaOrderByDataSessaoDescHoraSessaoDesc(Integer idFisioterapeuta);

    List<Sessao> findByIdPacienteIn(Collection<Integer> idsPacientes);

    long countByIdPacienteIn(Collection<Integer> idsPacientes);

    @Modifying
    @Query("delete from Sessao s where s.idPaciente in :idsPacientes")
    void deleteByIdPacienteIn(@Param("idsPacientes") Collection<Integer> idsPacientes);
}
