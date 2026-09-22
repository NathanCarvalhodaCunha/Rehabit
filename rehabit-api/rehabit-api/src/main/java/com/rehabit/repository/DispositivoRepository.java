package com.rehabit.repository;

import com.rehabit.model.Dispositivo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface DispositivoRepository extends JpaRepository<Dispositivo, Integer> {

    List<Dispositivo> findByIdClinicaOrderByCriadoEmDesc(Integer idClinica);

    long countByIdClinica(Integer idClinica);

    @Modifying
    @Query("delete from Dispositivo d where d.idClinica = :idClinica")
    void deleteByIdClinica(@Param("idClinica") Integer idClinica);
}
