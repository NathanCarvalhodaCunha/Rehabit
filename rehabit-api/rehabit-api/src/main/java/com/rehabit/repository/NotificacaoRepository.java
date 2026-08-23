package com.rehabit.repository;

import com.rehabit.model.Notificacao;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NotificacaoRepository extends JpaRepository<Notificacao, Integer> {

    List<Notificacao> findByIdClinicaOrderByCriadaEmDesc(Integer idClinica);
}
