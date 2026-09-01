package com.rehabit.repository;

import com.rehabit.model.CodigoPareamento;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface CodigoPareamentoRepository extends JpaRepository<CodigoPareamento, Integer> {

    Optional<CodigoPareamento> findByCodigoAndUsadoFalse(String codigo);

    List<CodigoPareamento> findByIdClinicaAndUsadoFalseAndExpiraEmAfter(Integer idClinica, LocalDateTime agora);

    void deleteByExpiraEmBefore(LocalDateTime limite);
}
