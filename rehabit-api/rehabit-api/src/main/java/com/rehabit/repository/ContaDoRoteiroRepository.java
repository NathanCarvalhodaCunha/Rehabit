package com.rehabit.repository;

import com.rehabit.model.ContaDoRoteiro;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ContaDoRoteiroRepository extends JpaRepository<ContaDoRoteiro, String> {

    @Modifying
    @Query("delete from ContaDoRoteiro c where c.idClinica = :idClinica")
    void deleteByIdClinica(@Param("idClinica") Integer idClinica);
}
