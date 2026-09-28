package com.rehabit.repository;

import com.rehabit.model.CodigoPareamento;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface CodigoPareamentoRepository extends JpaRepository<CodigoPareamento, Integer> {

    Optional<CodigoPareamento> findByCodigoAndUsadoFalse(String codigo);

    List<CodigoPareamento> findByIdClinicaAndUsadoFalseAndExpiraEmAfter(Integer idClinica, LocalDateTime agora);

    void deleteByExpiraEmBefore(LocalDateTime limite);

    @Modifying
    @Query("delete from CodigoPareamento c where c.idClinica = :idClinica")
    void deleteByIdClinica(@Param("idClinica") Integer idClinica);
}
