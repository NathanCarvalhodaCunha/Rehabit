package com.rehabit.repository;

import com.rehabit.model.Goniometro;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface GoniometroRepository extends JpaRepository<Goniometro, Integer> {

    Optional<Goniometro> findFirstByIdClinicaOrderByIdDesc(Integer idClinica);
}
