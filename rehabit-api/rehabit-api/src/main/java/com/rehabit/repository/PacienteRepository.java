package com.rehabit.repository;

import com.rehabit.model.Paciente;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PacienteRepository extends JpaRepository<Paciente, Integer> {

    List<Paciente> findByIdFisioterapeutaOrderByNomeAsc(Integer idFisioterapeuta);

    boolean existsByCpf(String cpf);

    long countByIdFisioterapeutaAndStatus(Integer idFisioterapeuta, String status);

    long countByIdClinica(Integer idClinica);
}
