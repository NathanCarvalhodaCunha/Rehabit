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
    private static final LocalTime ABERTURA_PADRAO = LocalTime.of(8, 0);
    private static final LocalTime FECHAMENTO_PADRAO = LocalTime.of(18, 0);

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
                .orElseGet(() -> new ConfiguracaoDTO(ABERTURA_PADRAO, FECHAMENTO_PADRAO,
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

    /**
     * Janela em que um profissional pode atender de fato.
     *
     * Cada conta guarda a própria configuração, então o horário que a clínica
     * define ficava só no registro dela e não alcançava a agenda de ninguém:
     * dava para marcar consulta com a clínica fechada. Aqui o horário da
     * clínica é o limite de fora, e o do profissional só pode apertá-lo —
     * nunca esticar. Se o profissional configurou uma faixa que não encosta
     * na da clínica, vale a da clínica, que é quem abre as portas.
     */
    public ConfiguracaoDTO janelaDeAtendimento(Integer idFisioterapeuta) {
        Configuracao doProfissional = configuracaoRepository
                .findByTipoUsuarioAndIdUsuario("FISIOTERAPEUTA", idFisioterapeuta)
                .orElse(null);
        Configuracao daClinica = fisioterapeutaRepository.findById(idFisioterapeuta)
                .flatMap(f -> configuracaoRepository.findByTipoUsuarioAndIdUsuario("CLINICA", f.getIdClinica()))
                .orElse(null);

        LocalTime abertura = maisTarde(valorOu(daClinica, Configuracao::getHoraAbertura, ABERTURA_PADRAO),
                valorOu(doProfissional, Configuracao::getHoraAbertura, null));
        LocalTime fechamento = maisCedo(valorOu(daClinica, Configuracao::getHoraFechamento, FECHAMENTO_PADRAO),
                valorOu(doProfissional, Configuracao::getHoraFechamento, null));

        if (!abertura.isBefore(fechamento)) {
            abertura = valorOu(daClinica, Configuracao::getHoraAbertura, ABERTURA_PADRAO);
            fechamento = valorOu(daClinica, Configuracao::getHoraFechamento, FECHAMENTO_PADRAO);
        }

        Integer duracao = duracaoDe(doProfissional);
        if (duracao == null) {
            duracao = duracaoDe(daClinica);
        }

        boolean avisarConflito = doProfissional != null ? doProfissional.isAvisarConflito()
                : daClinica == null || daClinica.isAvisarConflito();

        return new ConfiguracaoDTO(abertura, fechamento,
                duracao == null ? DURACAO_PADRAO_MIN : duracao, avisarConflito);
    }

    private static Integer duracaoDe(Configuracao config) {
        if (config == null || config.getDuracaoPadraoMin() == null || config.getDuracaoPadraoMin() <= 0) {
            return null;
        }
        return config.getDuracaoPadraoMin();
    }

    private static LocalTime valorOu(Configuracao config, java.util.function.Function<Configuracao, LocalTime> campo,
                                      LocalTime padrao) {
        LocalTime valor = config == null ? null : campo.apply(config);
        return valor == null ? padrao : valor;
    }

    private static LocalTime maisTarde(LocalTime a, LocalTime b) {
        if (a == null) return b;
        if (b == null) return a;
        return a.isAfter(b) ? a : b;
    }

    private static LocalTime maisCedo(LocalTime a, LocalTime b) {
        if (a == null) return b;
        if (b == null) return a;
        return a.isBefore(b) ? a : b;
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
