package com.rehabit.repository;

import com.rehabit.model.RecuperacaoSenha;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface RecuperacaoSenhaRepository extends JpaRepository<RecuperacaoSenha, Integer> {

    Optional<RecuperacaoSenha> findByTokenHash(String tokenHash);

    /** Pedidos ainda válidos para um e-mail, do mais novo para o mais antigo. */
    List<RecuperacaoSenha> findByEmailAndUsadoEmIsNullAndExpiraEmAfterOrderByCriadoEmDesc(
            String email, LocalDateTime agora);

    @Modifying
    @Query("delete from RecuperacaoSenha r where r.email = :email")
    void deleteByEmail(@Param("email") String email);

    void deleteByExpiraEmBefore(LocalDateTime limite);
}
