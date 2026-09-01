package com.rehabit.service;

import com.rehabit.dto.VerificacaoEnviadaDTO;
import com.rehabit.email.EmailService;
import com.rehabit.email.TemplatesEmail;
import com.rehabit.email.ValidadorEmailService;
import com.rehabit.exception.AuthException;
import com.rehabit.model.VerificacaoEmail;
import com.rehabit.repository.ClinicaRepository;
import com.rehabit.repository.FisioterapeutaRepository;
import com.rehabit.repository.VerificacaoEmailRepository;
import com.rehabit.security.CodigoSeguro;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Confirmação do e-mail no cadastro: só cria a conta quem conseguir ler o
 * código de 6 dígitos que mandamos para o endereço informado. É a única
 * checagem que prova que a caixa de entrada existe mesmo — formato e DNS
 * (ValidadorEmailService) provam no máximo que o domínio existe.
 */
@Service
public class VerificacaoEmailService {

    private static final Logger log = LoggerFactory.getLogger(VerificacaoEmailService.class);

    /** Erra 5 vezes, precisa pedir um código novo. Trava a força bruta nos 10^6 códigos. */
    private static final int MAX_TENTATIVAS = 5;
    /** Intervalo mínimo entre dois envios para o mesmo e-mail. */
    private static final Duration ESPERA_REENVIO = Duration.ofSeconds(60);

    private final VerificacaoEmailRepository verificacaoRepository;
    private final ClinicaRepository clinicaRepository;
    private final FisioterapeutaRepository fisioterapeutaRepository;
    private final ValidadorEmailService validadorEmail;
    private final EmailService emailService;
    private final int expiracaoMinutos;
    private final boolean obrigatorio;

    public VerificacaoEmailService(VerificacaoEmailRepository verificacaoRepository,
                                   ClinicaRepository clinicaRepository,
                                   FisioterapeutaRepository fisioterapeutaRepository,
                                   ValidadorEmailService validadorEmail,
                                   EmailService emailService,
                                   @Value("${rehabit.verificacao.expiracao-minutos:15}") int expiracaoMinutos,
                                   @Value("${rehabit.email.confirmar-cadastro:}") String confirmarCadastro) {
        this.verificacaoRepository = verificacaoRepository;
        this.clinicaRepository = clinicaRepository;
        this.fisioterapeutaRepository = fisioterapeutaRepository;
        this.validadorEmail = validadorEmail;
        this.emailService = emailService;
        this.expiracaoMinutos = expiracaoMinutos;

        // Em branco = automático: exige o código quando há SMTP configurado.
        // Sem SMTP, exigir o código deixaria o cadastro impossível — a pessoa
        // não teria como receber os 6 dígitos.
        this.obrigatorio = (confirmarCadastro == null || confirmarCadastro.isBlank())
                ? emailService.isHabilitado()
                : Boolean.parseBoolean(confirmarCadastro.trim());

        if (!obrigatorio) {
            log.warn("Confirmação de e-mail no cadastro desligada: o endereço será checado só "
                    + "por formato, domínio descartável e DNS.");
        }
    }

    public boolean isObrigatorio() {
        return obrigatorio;
    }

    /** Gera e envia o código para o e-mail informado. */
    @Transactional
    public VerificacaoEnviadaDTO enviarCodigo(String emailBruto) {
        String email = validadorEmail.validarENormalizar(emailBruto);

        if (clinicaRepository.existsByEmail(email) || fisioterapeutaRepository.existsByEmail(email)) {
            throw new AuthException("Este e-mail já está cadastrado.", HttpStatus.CONFLICT);
        }

        LocalDateTime agora = LocalDateTime.now();
        verificacaoRepository.deleteByExpiraEmBefore(agora.minusDays(1));

        VerificacaoEmail verificacao = verificacaoRepository.findByEmail(email).orElseGet(VerificacaoEmail::new);

        if (verificacao.getId() != null
                && verificacao.getCriadoEm() != null
                && verificacao.getCriadoEm().plus(ESPERA_REENVIO).isAfter(agora)) {
            throw new AuthException("Aguarde um minuto antes de pedir um novo código.",
                    HttpStatus.TOO_MANY_REQUESTS);
        }

        String codigo = CodigoSeguro.gerarCodigoNumerico(6);
        verificacao.setEmail(email);
        verificacao.setCodigoHash(CodigoSeguro.hash(codigo));
        verificacao.setTentativas(0);
        verificacao.setCriadoEm(agora);
        verificacao.setExpiraEm(agora.plusMinutes(expiracaoMinutos));
        verificacao.setVerificadoEm(null);
        verificacaoRepository.save(verificacao);

        emailService.enviar(email,
                "Confirme seu e-mail — Rehabit",
                TemplatesEmail.verificacaoCadastroHtml(codigo, expiracaoMinutos),
                TemplatesEmail.verificacaoCadastroTexto(codigo, expiracaoMinutos));

        return new VerificacaoEnviadaDTO(EmailService.mascarar(email), expiracaoMinutos,
                "Enviamos um código de 6 dígitos para " + EmailService.mascarar(email) + ".");
    }

    /** Confere o código digitado e libera aquele e-mail para o cadastro. */
    @Transactional
    public void confirmarCodigo(String emailBruto, String codigo) {
        String email = validadorEmail.normalizar(emailBruto);
        VerificacaoEmail verificacao = verificacaoRepository.findByEmail(email)
                .orElseThrow(() -> new AuthException(
                        "Nenhum código pendente para este e-mail. Peça um novo código.",
                        HttpStatus.BAD_REQUEST));

        if (verificacao.getExpiraEm().isBefore(LocalDateTime.now())) {
            verificacaoRepository.delete(verificacao);
            throw new AuthException("O código expirou. Peça um novo código.", HttpStatus.BAD_REQUEST);
        }

        if (verificacao.getTentativas() >= MAX_TENTATIVAS) {
            verificacaoRepository.delete(verificacao);
            throw new AuthException("Muitas tentativas erradas. Peça um novo código.", HttpStatus.BAD_REQUEST);
        }

        if (!CodigoSeguro.confere(codigo == null ? "" : codigo.trim(), verificacao.getCodigoHash())) {
            verificacao.setTentativas(verificacao.getTentativas() + 1);
            verificacaoRepository.save(verificacao);
            int restantes = MAX_TENTATIVAS - verificacao.getTentativas();
            throw new AuthException(
                    restantes > 0
                            ? "Código incorreto. Você ainda tem " + restantes
                                    + (restantes == 1 ? " tentativa." : " tentativas.")
                            : "Código incorreto. Peça um novo código.",
                    HttpStatus.BAD_REQUEST);
        }

        verificacao.setVerificadoEm(LocalDateTime.now());
        verificacaoRepository.save(verificacao);
    }

    /**
     * Exige que o e-mail tenha sido confirmado antes de criar a conta.
     * Chamado no meio do cadastro: sem isso, bastaria chamar /register
     * direto e o código enviado por e-mail não protegeria nada.
     */
    @Transactional(readOnly = true)
    public void exigirConfirmado(String email) {
        if (!obrigatorio) {
            return;
        }
        Optional<VerificacaoEmail> verificacao = verificacaoRepository.findByEmail(email);
        boolean confirmado = verificacao
                .filter(v -> v.getVerificadoEm() != null)
                .filter(v -> v.getExpiraEm().isAfter(LocalDateTime.now()))
                .isPresent();

        if (!confirmado) {
            throw new AuthException("Confirme seu e-mail com o código que enviamos antes de concluir o cadastro.",
                    HttpStatus.FORBIDDEN);
        }
    }

    /** Apaga o registro depois que a conta foi criada — ele já cumpriu o papel. */
    @Transactional
    public void consumir(String email) {
        verificacaoRepository.deleteByEmail(email);
    }
}
