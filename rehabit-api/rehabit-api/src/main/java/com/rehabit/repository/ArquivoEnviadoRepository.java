package com.rehabit.repository;

import com.rehabit.model.ArquivoEnviado;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface ArquivoEnviadoRepository extends JpaRepository<ArquivoEnviado, Integer> {

    List<ArquivoEnviado> findTop500ByEnviadoEmBeforeOrderByEnviadoEmAsc(LocalDateTime limite);
}
