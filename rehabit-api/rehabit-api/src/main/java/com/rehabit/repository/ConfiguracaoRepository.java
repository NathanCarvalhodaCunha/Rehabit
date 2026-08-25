package com.rehabit.repository;

import com.rehabit.model.Configuracao;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ConfiguracaoRepository extends JpaRepository<Configuracao, Integer> {

    Optional<Configuracao> findByTipoUsuarioAndIdUsuario(String tipoUsuario, Integer idUsuario);
}
