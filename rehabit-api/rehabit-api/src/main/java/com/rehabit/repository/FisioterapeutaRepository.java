package com.rehabit.repository;

import com.rehabit.model.Fisioterapeuta;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface FisioterapeutaRepository extends JpaRepository<Fisioterapeuta, Integer> {

    Optional<Fisioterapeuta> findByEmail(String email);

    boolean existsByEmail(String email);

    boolean existsByCoffito(String coffito);

    /**
     * Todos os profissionais da clínica, <strong>inclusive os excluídos</strong>.
     * É o que estatísticas e histórico precisam: as sessões de quem saiu
     * continuam contando e continuam assinadas por ele. Para listas e
     * seletores, use {@link #findByIdClinicaAndExcluidoEmIsNullOrderByNomeAsc}.
     *
     * O padrão inclui de propósito: se alguém esquecer de trocar uma lista, o
     * erro aparece na tela (um excluído listado); se o padrão escondesse e
     * alguém esquecesse uma estatística, as sessões sumiriam em silêncio.
     */
    List<Fisioterapeuta> findByIdClinicaOrderByNomeAsc(Integer idClinica);

    /** Só os ativos — para listas, seletores e busca. */
    List<Fisioterapeuta> findByIdClinicaAndExcluidoEmIsNullOrderByNomeAsc(Integer idClinica);

    long countByIdClinicaAndExcluidoEmIsNull(Integer idClinica);

    /** Apaga de verdade, inclusive os excluídos — só a exclusão da conta da clínica usa. */
    @Modifying
    @Query("delete from Fisioterapeuta f where f.idClinica = :idClinica")
    void deleteByIdClinica(@Param("idClinica") Integer idClinica);
}
