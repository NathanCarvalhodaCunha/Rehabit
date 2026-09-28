package com.rehabit.repository;

import com.rehabit.model.Paciente;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PacienteRepository extends JpaRepository<Paciente, Integer> {

    List<Paciente> findByIdFisioterapeutaOrderByNomeAsc(Integer idFisioterapeuta);

    boolean existsByCpf(String cpf);

    long countByIdFisioterapeutaAndStatus(Integer idFisioterapeuta, String status);

    long countByIdFisioterapeuta(Integer idFisioterapeuta);

    long countByIdClinica(Integer idClinica);

    long countByIdClinicaAndStatus(Integer idClinica, String status);

    List<Paciente> findByIdClinica(Integer idClinica);

    @Modifying
    @Query("delete from Paciente p where p.idClinica = :idClinica")
    void deleteByIdClinica(@Param("idClinica") Integer idClinica);
}
