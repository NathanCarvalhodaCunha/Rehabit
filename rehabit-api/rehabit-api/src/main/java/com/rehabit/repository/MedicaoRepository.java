package com.rehabit.repository;

import com.rehabit.model.Medicao;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;

public interface MedicaoRepository extends JpaRepository<Medicao, Integer> {

    Medicao findByIdSessao(Integer idSessao);

    @Modifying
    @Query("delete from Medicao m where m.idSessao in :idsSessoes")
    void deleteByIdSessaoIn(@Param("idsSessoes") Collection<Integer> idsSessoes);
}
