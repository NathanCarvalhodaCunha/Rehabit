package com.rehabit.service;

import com.rehabit.dto.PacienteCreateDTO;
import com.rehabit.dto.PacienteDetalheDTO;
import com.rehabit.dto.PacienteResumoDTO;
import com.rehabit.exception.AuthException;
import com.rehabit.model.Fisioterapeuta;
import com.rehabit.model.Medicao;
import com.rehabit.model.Paciente;
import com.rehabit.model.Sessao;
import com.rehabit.repository.FisioterapeutaRepository;
import com.rehabit.repository.MedicaoRepository;
import com.rehabit.repository.PacienteRepository;
import com.rehabit.repository.SessaoRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.Period;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class PacienteService {

    private static final DateTimeFormatter FORMATO_DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final PacienteRepository pacienteRepository;
    private final FisioterapeutaRepository fisioterapeutaRepository;
    private final SessaoRepository sessaoRepository;
    private final MedicaoRepository medicaoRepository;
    private final NotificacaoService notificacaoService;

    public PacienteService(PacienteRepository pacienteRepository,
                            FisioterapeutaRepository fisioterapeutaRepository,
                            SessaoRepository sessaoRepository,
                            MedicaoRepository medicaoRepository,
                            NotificacaoService notificacaoService) {
        this.pacienteRepository = pacienteRepository;
        this.fisioterapeutaRepository = fisioterapeutaRepository;
        this.sessaoRepository = sessaoRepository;
        this.medicaoRepository = medicaoRepository;
        this.notificacaoService = notificacaoService;
    }

    @Transactional
    public PacienteDetalheDTO cadastrar(PacienteCreateDTO dados) {
        Fisioterapeuta fisioterapeuta = fisioterapeutaRepository.findById(dados.getIdFisioterapeuta())
                .orElseThrow(() -> new AuthException("Profissional não encontrado.", HttpStatus.BAD_REQUEST));

        if (pacienteRepository.existsByCpf(dados.getCpf())) {
            throw new AuthException("Este CPF já está cadastrado.", HttpStatus.CONFLICT);
        }

        Paciente paciente = new Paciente();
        paciente.setNome(dados.getNome());
        paciente.setCpf(dados.getCpf());
        paciente.setTelefone(vazioParaNulo(dados.getTelefone()));
        paciente.setEmail(vazioParaNulo(dados.getEmail()));
        paciente.setDataNascimento(dados.getDataNascimento());
        paciente.setSexo(vazioParaNulo(dados.getSexo()));
        paciente.setDataInicioTratamento(
                dados.getDataInicioTratamento() != null ? dados.getDataInicioTratamento() : LocalDate.now());
        paciente.setSituacao(vazioParaNulo(dados.getSituacao()));
        paciente.setStatus("Ativo");
        paciente.setIdClinica(fisioterapeuta.getIdClinica());
        paciente.setIdFisioterapeuta(fisioterapeuta.getId());

        Paciente salvo = pacienteRepository.save(paciente);
        notificacaoService.criar(fisioterapeuta.getIdClinica(), "NOVO_PACIENTE",
                "Novo paciente cadastrado: " + salvo.getNome() + " (por " + fisioterapeuta.getNome() + ")");
        return paraDetalheDTO(salvo, fisioterapeuta.getNome());
    }

    public List<PacienteResumoDTO> listarPorFisioterapeuta(Integer idFisioterapeuta) {
        return pacienteRepository.findByIdFisioterapeutaOrderByNomeAsc(idFisioterapeuta).stream()
                .map(this::paraResumoDTO)
                .collect(Collectors.toList());
    }

    public PacienteDetalheDTO buscar(Integer id) {
        Paciente paciente = pacienteRepository.findById(id)
                .orElseThrow(() -> new AuthException("Paciente não encontrado.", HttpStatus.NOT_FOUND));
        String nomeFisioterapeuta = fisioterapeutaRepository.findById(paciente.getIdFisioterapeuta())
                .map(Fisioterapeuta::getNome)
                .orElse(null);
        return paraDetalheDTO(paciente, nomeFisioterapeuta);
    }

    private PacienteResumoDTO paraResumoDTO(Paciente paciente) {
        List<Sessao> sessoes = sessaoRepository.findByIdPacienteOrderByDataSessaoDescHoraSessaoDesc(paciente.getId());
        String ultimaSessao = !sessoes.isEmpty() && sessoes.get(0).getDataSessao() != null
                ? sessoes.get(0).getDataSessao().format(FORMATO_DATA)
                : null;
        return new PacienteResumoDTO(paciente.getId(), paciente.getNome(), paciente.getSituacao(),
                ultimaSessao, calcularSelo(sessoes));
    }

    private String calcularSelo(List<Sessao> sessoesRecentesPrimeiro) {
        if (sessoesRecentesPrimeiro.isEmpty()) {
            return null;
        }
        if (sessoesRecentesPrimeiro.size() < 2) {
            return "Estavel";
        }
        Medicao ultima = medicaoRepository.findByIdSessao(sessoesRecentesPrimeiro.get(0).getId());
        Medicao anterior = medicaoRepository.findByIdSessao(sessoesRecentesPrimeiro.get(1).getId());
        if (ultima == null || anterior == null
                || ultima.getAmplitudeMedia() == null || anterior.getAmplitudeMedia() == null) {
            return "Estavel";
        }
        int comparacao = ultima.getAmplitudeMedia().compareTo(anterior.getAmplitudeMedia());
        if (comparacao > 0) {
            return "Evoluindo";
        }
        if (comparacao < 0) {
            return "Instavel";
        }
        return "Estavel";
    }

    private PacienteDetalheDTO paraDetalheDTO(Paciente p, String nomeFisioterapeuta) {
        Integer idade = p.getDataNascimento() != null
                ? Period.between(p.getDataNascimento(), LocalDate.now()).getYears()
                : null;
        return new PacienteDetalheDTO(p.getId(), p.getNome(), p.getCpf(), p.getTelefone(), p.getEmail(),
                p.getDataNascimento(), idade, p.getSexo(), p.getDataInicioTratamento(), p.getSituacao(),
                p.getStatus(), p.getIdFisioterapeuta(), nomeFisioterapeuta);
    }

    private String vazioParaNulo(String valor) {
        return (valor == null || valor.isBlank()) ? null : valor.trim();
    }
}
