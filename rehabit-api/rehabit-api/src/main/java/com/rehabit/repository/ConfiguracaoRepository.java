package com.rehabit.repository;

import com.rehabit.model.Configuracao;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.Optional;

public interface ConfiguracaoRepository extends JpaRepository<Configuracao, Integer> {

    Optional<Configuracao> findByTipoUsuarioAndIdUsuario(String tipoUsuario, Integer idUsuario);

    @Modifying
    @Query("delete from Configuracao c where c.tipoUsuario = :tipoUsuario and c.idUsuario in :idsUsuarios")
    void deleteByTipoUsuarioAndIdUsuarioIn(@Param("tipoUsuario") String tipoUsuario,
                                           @Param("idsUsuarios") Collection<Integer> idsUsuarios);
}
