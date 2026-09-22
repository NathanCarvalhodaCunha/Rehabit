package com.rehabit.repository;

import com.rehabit.model.Goniometro;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface GoniometroRepository extends JpaRepository<Goniometro, Integer> {

    Optional<Goniometro> findFirstByIdClinicaOrderByIdDesc(Integer idClinica);

    /** Um aparelho por número de série: é assim que a mesma placa reencontra o próprio cadastro. */
    Optional<Goniometro> findFirstByIdClinicaAndNumeroSerieOrderByIdDesc(Integer idClinica, String numeroSerie);

    @Modifying
    @Query("delete from Goniometro g where g.idClinica = :idClinica")
    void deleteByIdClinica(@Param("idClinica") Integer idClinica);
}
