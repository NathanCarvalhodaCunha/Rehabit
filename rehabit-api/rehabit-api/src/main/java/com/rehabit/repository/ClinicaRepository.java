package com.rehabit.repository;

import com.rehabit.model.Clinica;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ClinicaRepository extends JpaRepository<Clinica, Integer> {

    Optional<Clinica> findByEmail(String email);

    boolean existsByEmail(String email);

    boolean existsByCnpj(String cnpj);

    Optional<Clinica> findByCnpj(String cnpj);

    /** Alguém ainda usa este arquivo? Veja FotosEmUso: só apaga o que ninguém usa. */
    boolean existsByFoto(String foto);
}
