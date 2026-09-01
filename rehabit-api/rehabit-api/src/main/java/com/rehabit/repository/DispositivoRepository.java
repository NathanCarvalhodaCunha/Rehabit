package com.rehabit.repository;

import com.rehabit.model.Dispositivo;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DispositivoRepository extends JpaRepository<Dispositivo, Integer> {

    List<Dispositivo> findByIdClinicaOrderByCriadoEmDesc(Integer idClinica);

    long countByIdClinica(Integer idClinica);
}
