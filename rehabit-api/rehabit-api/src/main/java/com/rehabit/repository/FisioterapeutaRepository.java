package com.rehabit.repository;

import com.rehabit.model.Fisioterapeuta;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FisioterapeutaRepository extends JpaRepository<Fisioterapeuta, Integer> {

    Optional<Fisioterapeuta> findByEmail(String email);

    boolean existsByEmail(String email);

    boolean existsByCoffito(String coffito);

    List<Fisioterapeuta> findByIdClinicaOrderByNomeAsc(Integer idClinica);

    long countByIdClinica(Integer idClinica);
}
