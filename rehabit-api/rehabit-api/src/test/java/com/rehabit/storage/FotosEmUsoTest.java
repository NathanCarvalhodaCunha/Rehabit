package com.rehabit.storage;

import com.rehabit.repository.ClinicaRepository;
import com.rehabit.repository.FisioterapeutaRepository;
import com.rehabit.repository.PacienteRepository;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A foto trocada é apagada depois do commit, quando o cadastro já está salvo
 * com a nova: uma falha ali não pode chegar à resposta como erro 500.
 */
class FotosEmUsoTest {

    private final FileStorageService armazenamento = mock(FileStorageService.class);
    private final ClinicaRepository clinicas = mock(ClinicaRepository.class);
    private final FisioterapeutaRepository fisioterapeutas = mock(FisioterapeutaRepository.class);
    private final PacienteRepository pacientes = mock(PacienteRepository.class);
    private final FotosEmUso fotos = new FotosEmUso(armazenamento, clinicas, fisioterapeutas, pacientes);

    @Test
    void falhaAoApagarAFotoAntigaNaoDerrubaATroca() {
        doThrow(new IllegalStateException("armazenamento fora do ar")).when(armazenamento).excluir("/uploads/a.png");

        // Sem transação ativa, apaga na hora: é o mesmo caminho do afterCommit.
        assertThatCode(() -> fotos.descartarDepoisDoCommit(List.of("/uploads/a.png", "/uploads/b.png")))
                .doesNotThrowAnyException();
        verify(armazenamento).excluir("/uploads/b.png");
    }

    @Test
    void falhaDoBancoAoConferirSeAFotoEstaEmUsoNaoDerrubaATroca() {
        when(clinicas.existsByFoto("/uploads/a.png")).thenThrow(new DataAccessResourceFailureException("sem conexão"));

        assertThatCode(() -> fotos.descartarDepoisDoCommit("/uploads/a.png")).doesNotThrowAnyException();
    }
}
