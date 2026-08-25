package com.rehabit.service;

import com.rehabit.dto.ConfiguracaoDTO;
import com.rehabit.dto.TrocarSenhaDTO;
import com.rehabit.exception.AuthException;
import com.rehabit.model.Clinica;
import com.rehabit.model.Configuracao;
import com.rehabit.model.Fisioterapeuta;
import com.rehabit.repository.ClinicaRepository;
import com.rehabit.repository.ConfiguracaoRepository;
import com.rehabit.repository.FisioterapeutaRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;

@Service
public class ConfiguracaoService {

    private static final int DURACAO_PADRAO_MIN = 45;

    private final ConfiguracaoRepository configuracaoRepository;
    private final ClinicaRepository clinicaRepository;
    private final FisioterapeutaRepository fisioterapeutaRepository;
    private final PasswordEncoder passwordEncoder;

    public ConfiguracaoService(ConfiguracaoRepository configuracaoRepository,
                                ClinicaRepository clinicaRepository,
                                FisioterapeutaRepository fisioterapeutaRepository,
                                PasswordEncoder passwordEncoder) {
        this.configuracaoRepository = configuracaoRepository;
        this.clinicaRepository = clinicaRepository;
        this.fisioterapeutaRepository = fisioterapeutaRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /** Nunca devolve vazio: quem ainda não configurou nada recebe os padrões. */
    public ConfiguracaoDTO buscar(Integer usuarioId, String usuarioTipo) {
        exigirSessao(usuarioId, usuarioTipo);
        return configuracaoRepository.findByTipoUsuarioAndIdUsuario(usuarioTipo, usuarioId)
                .map(c -> new ConfiguracaoDTO(c.getHoraAbertura(), c.getHoraFechamento(),
                        c.getDuracaoPadraoMin(), c.isAvisarConflito()))
                .orElseGet(() -> new ConfiguracaoDTO(LocalTime.of(8, 0), LocalTime.of(18, 0),
                        DURACAO_PADRAO_MIN, true));
    }

    @Transactional
    public ConfiguracaoDTO salvar(ConfiguracaoDTO dados, Integer usuarioId, String usuarioTipo) {
        exigirSessao(usuarioId, usuarioTipo);

        if (dados.getHoraAbertura() != null && dados.getHoraFechamento() != null
                && !dados.getHoraFechamento().isAfter(dados.getHoraAbertura())) {
            throw new AuthException("O horário de fechamento precisa ser depois do de abertura.",
                    HttpStatus.BAD_REQUEST);
        }
        if (dados.getDuracaoPadraoMin() != null
                && (dados.getDuracaoPadraoMin() < 5 || dados.getDuracaoPadraoMin() > 480)) {
            throw new AuthException("A duração padrão deve ficar entre 5 e 480 minutos.",
                    HttpStatus.BAD_REQUEST);
        }

        Configuracao config = configuracaoRepository
                .findByTipoUsuarioAndIdUsuario(usuarioTipo, usuarioId)
                .orElseGet(() -> {
                    Configuracao nova = new Configuracao();
                    nova.setTipoUsuario(usuarioTipo);
                    nova.setIdUsuario(usuarioId);
                    return nova;
                });

        config.setHoraAbertura(dados.getHoraAbertura());
        config.setHoraFechamento(dados.getHoraFechamento());
        config.setDuracaoPadraoMin(dados.getDuracaoPadraoMin());
        config.setAvisarConflito(dados.isAvisarConflito());

        Configuracao salva = configuracaoRepository.save(config);
        return new ConfiguracaoDTO(salva.getHoraAbertura(), salva.getHoraFechamento(),
                salva.getDuracaoPadraoMin(), salva.isAvisarConflito());
    }

    /** Usado pelo agendamento para saber quanto tempo cada sessão ocupa. */
    public int duracaoPadrao(String tipoUsuario, Integer idUsuario) {
        return configuracaoRepository.findByTipoUsuarioAndIdUsuario(tipoUsuario, idUsuario)
                .map(Configuracao::getDuracaoPadraoMin)
                .filter(d -> d != null && d > 0)
                .orElse(DURACAO_PADRAO_MIN);
    }

    public boolean deveAvisarConflito(String tipoUsuario, Integer idUsuario) {
        return configuracaoRepository.findByTipoUsuarioAndIdUsuario(tipoUsuario, idUsuario)
                .map(Configuracao::isAvisarConflito)
                .orElse(true);
    }

    @Transactional
    public void trocarSenha(TrocarSenhaDTO dados, Integer usuarioId, String usuarioTipo) {
        exigirSessao(usuarioId, usuarioTipo);

        if ("CLINICA".equals(usuarioTipo)) {
            Clinica clinica = clinicaRepository.findById(usuarioId)
                    .orElseThrow(() -> new AuthException("Conta não encontrada.", HttpStatus.NOT_FOUND));
            conferirSenhaAtual(dados.getSenhaAtual(), clinica.getSenha());
            clinica.setSenha(passwordEncoder.encode(dados.getNovaSenha()));
            clinicaRepository.save(clinica);
            return;
        }

        Fisioterapeuta fisioterapeuta = fisioterapeutaRepository.findById(usuarioId)
                .orElseThrow(() -> new AuthException("Conta não encontrada.", HttpStatus.NOT_FOUND));
        conferirSenhaAtual(dados.getSenhaAtual(), fisioterapeuta.getSenha());
        fisioterapeuta.setSenha(passwordEncoder.encode(dados.getNovaSenha()));
        fisioterapeutaRepository.save(fisioterapeuta);
    }

    private void conferirSenhaAtual(String informada, String armazenada) {
        if (!passwordEncoder.matches(informada, armazenada)) {
            throw new AuthException("A senha atual está incorreta.", HttpStatus.BAD_REQUEST);
        }
    }

    private void exigirSessao(Integer usuarioId, String usuarioTipo) {
        if (usuarioId == null || usuarioTipo == null) {
            throw new AuthException("Sessão inválida.", HttpStatus.UNAUTHORIZED);
        }
    }
}
