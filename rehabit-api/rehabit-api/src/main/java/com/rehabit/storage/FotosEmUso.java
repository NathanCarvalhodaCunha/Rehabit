package com.rehabit.storage;

import com.rehabit.exception.AuthException;
import com.rehabit.repository.ClinicaRepository;
import com.rehabit.repository.FisioterapeutaRepository;
import com.rehabit.repository.PacienteRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * O campo "foto" de clínica, profissional e paciente: o que pode ser gravado
 * nele e quando o arquivo pode ser apagado.
 *
 * Gravar: só uma URL que o próprio armazenamento devolveu (veja
 * FileStorageService#guardou). Antes ia qualquer texto, e as telas o
 * colocavam num style="background-image:url(...)" — com aspas no meio, virava
 * HTML e script na sessão de quem abrisse a lista (inclusive a clínica).
 *
 * Apagar: só quando nenhum cadastro aponta mais para o arquivo. Antes a
 * exclusão de conta apagava toda foto citada nas linhas da conta, e bastava
 * pôr na própria foto a URL da foto de outra clínica e excluir a conta para
 * sumir com a dela.
 */
@Component
public class FotosEmUso {

    private final FileStorageService armazenamento;
    private final ClinicaRepository clinicaRepository;
    private final FisioterapeutaRepository fisioterapeutaRepository;
    private final PacienteRepository pacienteRepository;

    public FotosEmUso(FileStorageService armazenamento,
                      ClinicaRepository clinicaRepository,
                      FisioterapeutaRepository fisioterapeutaRepository,
                      PacienteRepository pacienteRepository) {
        this.armazenamento = armazenamento;
        this.clinicaRepository = clinicaRepository;
        this.fisioterapeutaRepository = fisioterapeutaRepository;
        this.pacienteRepository = pacienteRepository;
    }

    /**
     * O valor a gravar no campo. Em branco tira a foto; igual ao atual mantém
     * (inclusive uma foto antiga, de antes desta regra); o resto precisa ser
     * um arquivo enviado pelo /api/uploads.
     */
    public String aceitar(String nova, String atual) {
        if (nova == null || nova.isBlank()) {
            return null;
        }
        String valor = nova.trim();
        if (valor.equals(atual)) {
            return atual;
        }
        if (!armazenamento.guardou(valor)) {
            throw new AuthException("Foto inválida. Envie a imagem pelo botão de foto.", HttpStatus.BAD_REQUEST);
        }
        return valor;
    }

    public void descartarDepoisDoCommit(String foto) {
        descartarDepoisDoCommit(foto == null ? List.of() : List.of(foto));
    }

    /**
     * Depois que o banco confirmar, apaga do armazenamento cada uma destas
     * fotos que nenhum cadastro usa mais. Só depois do commit: se o banco
     * falhar, a foto continua lá para a linha que ainda aponta para ela.
     */
    public void descartarDepoisDoCommit(Collection<String> fotos) {
        Set<String> candidatas = new LinkedHashSet<>();
        fotos.stream().filter(Objects::nonNull).filter(f -> !f.isBlank()).forEach(candidatas::add);
        if (candidatas.isEmpty()) {
            return;
        }
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            apagarAsQueSobraram(candidatas);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                apagarAsQueSobraram(candidatas);
            }
        });
    }

    /** Algum cadastro (clínica, profissional ou paciente) aponta para este arquivo? */
    public boolean emUso(String foto) {
        return clinicaRepository.existsByFoto(foto)
                || fisioterapeutaRepository.existsByFoto(foto)
                || pacienteRepository.existsByFoto(foto);
    }

    private void apagarAsQueSobraram(Set<String> fotos) {
        for (String foto : fotos) {
            if (!emUso(foto)) {
                armazenamento.excluir(foto);
            }
        }
    }
}
