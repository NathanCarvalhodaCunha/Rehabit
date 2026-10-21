package com.rehabit.repository;

import com.rehabit.model.SenhaAplicada;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SenhaAplicadaRepository extends JpaRepository<SenhaAplicada, String> {
}
