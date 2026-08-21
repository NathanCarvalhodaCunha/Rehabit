package com.rehabit.repository;

import com.rehabit.model.Medicao;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MedicaoRepository extends JpaRepository<Medicao, Integer> {

    Medicao findByIdSessao(Integer idSessao);
}
