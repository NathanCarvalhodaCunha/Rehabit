package com.rehabit.repository;

import com.rehabit.model.VerificacaoEmail;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Optional;

public interface VerificacaoEmailRepository extends JpaRepository<VerificacaoEmail, Integer> {

    Optional<VerificacaoEmail> findByEmail(String email);

    void deleteByEmail(String email);

    void deleteByExpiraEmBefore(LocalDateTime limite);
}
