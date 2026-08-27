package com.rehabit.service;

import com.rehabit.dto.CodigoPareamentoDTO;
import com.rehabit.dto.DispositivoDTO;
import com.rehabit.dto.PareamentoRequestDTO;
import com.rehabit.dto.PareamentoRespostaDTO;
import com.rehabit.exception.AuthException;
import com.rehabit.model.Clinica;
import com.rehabit.model.CodigoPareamento;
import com.rehabit.model.Dispositivo;
import com.rehabit.repository.ClinicaRepository;
import com.rehabit.repository.CodigoPareamentoRepository;
import com.rehabit.repository.DispositivoRepository;
import com.rehabit.security.JwtService;
import com.rehabit.security.PosseChecker;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class DispositivoService {

    private static final Duration VALIDADE_CODIGO = Duration.ofMinutes(10);
    /** Um ano: ninguém vai refazer login num aparelho pendurado na parede. */
    private static final long VALIDADE_TOKEN_MS = Duration.ofDays(365).toMillis();
    private static final int MAX_TENTATIVAS_CODIGO = 20;

    private final DispositivoRepository dispositivoRepository;
    private final CodigoPareamentoRepository codigoRepository;
    private final ClinicaRepository clinicaRepository;
    private final JwtService jwtService;
    private final SecureRandom aleatorio = new SecureRandom();

    public DispositivoService(DispositivoRepository dispositivoRepository,
                               CodigoPareamentoRepository codigoRepository,
                               ClinicaRepository clinicaRepository,
                               JwtService jwtService) {
        this.dispositivoRepository = dispositivoRepository;
        this.codigoRepository = codigoRepository;
        this.clinicaRepository = clinicaRepository;
        this.jwtService = jwtService;
    }

    /** Gera o código que a clínica mostra na tela para digitar no aparelho. */
    @Transactional
    public CodigoPareamentoDTO gerarCodigo(Integer idClinica, Integer usuarioId, String usuarioTipo) {
        PosseChecker.exigirClinicaDona(idClinica, usuarioId, usuarioTipo);

        LocalDateTime agora = LocalDateTime.now();
        limparExpirados(agora);

        // Um código por vez: pedir um novo invalida o anterior, senão sobrariam
        // vários códigos válidos soltos por aí.
        codigoRepository.findByIdClinicaAndUsadoFalseAndExpiraEmAfter(idClinica, agora)
                .forEach(c -> {
                    c.setUsado(true);
                    codigoRepository.save(c);
                });

        CodigoPareamento codigo = new CodigoPareamento();
        codigo.setCodigo(sortearCodigoLivre());
        codigo.setIdClinica(idClinica);
        codigo.setExpiraEm(agora.plus(VALIDADE_CODIGO));
        codigo.setUsado(false);
        CodigoPareamento salvo = codigoRepository.save(codigo);

        return new CodigoPareamentoDTO(salvo.getCodigo(), salvo.getExpiraEm(), VALIDADE_CODIGO.toSeconds());
    }

    /**
     * Chamado pelo próprio goniômetro, sem autenticação — é aqui que ele
     * ganha credencial. A proteção é o código: 6 dígitos, uso único e 10
     * minutos de validade.
     */
    @Transactional
    public PareamentoRespostaDTO parear(PareamentoRequestDTO dados) {
        String informado = dados.getCodigo() == null ? "" : dados.getCodigo().trim();

        CodigoPareamento codigo = codigoRepository.findByCodigoAndUsadoFalse(informado)
                .orElseThrow(() -> new AuthException(
                        "Código inválido ou já utilizado. Gere um novo na tela Dispositivo.",
                        HttpStatus.BAD_REQUEST));

        if (codigo.getExpiraEm().isBefore(LocalDateTime.now())) {
            throw new AuthException("Código expirado. Gere um novo na tela Dispositivo.",
                    HttpStatus.BAD_REQUEST);
        }

        Clinica clinica = clinicaRepository.findById(codigo.getIdClinica())
                .orElseThrow(() -> new AuthException("Clínica não encontrada.", HttpStatus.BAD_REQUEST));

        Dispositivo dispositivo = new Dispositivo();
        dispositivo.setIdClinica(clinica.getId());
        dispositivo.setNome(nomeOuPadrao(dados.getNome(), clinica.getId()));
        dispositivo.setAtivo(true);
        dispositivo.setCriadoEm(LocalDateTime.now());
        Dispositivo salvo = dispositivoRepository.save(dispositivo);

        codigo.setUsado(true);
        codigoRepository.save(codigo);

        String token = jwtService.gerarTokenDeDispositivo(salvo.getId(), clinica.getId(), VALIDADE_TOKEN_MS);
        return new PareamentoRespostaDTO(salvo.getId(), clinica.getId(), clinica.getNome(), token);
    }

    public List<DispositivoDTO> listar(Integer idClinica, Integer usuarioId, String usuarioTipo) {
        PosseChecker.exigirClinicaDona(idClinica, usuarioId, usuarioTipo);
        return dispositivoRepository.findByIdClinicaOrderByCriadoEmDesc(idClinica).stream()
                .map(d -> new DispositivoDTO(d.getId(), d.getNome(), d.isAtivo(),
                        d.getCriadoEm(), d.getUltimoContato()))
                .collect(Collectors.toList());
    }

    @Transactional
    public void revogar(Integer idDispositivo, Integer usuarioId, String usuarioTipo) {
        Dispositivo dispositivo = dispositivoRepository.findById(idDispositivo)
                .orElseThrow(() -> new AuthException("Dispositivo não encontrado.", HttpStatus.NOT_FOUND));
        PosseChecker.exigirClinicaDona(dispositivo.getIdClinica(), usuarioId, usuarioTipo);

        // Desativa em vez de apagar: o histórico de qual aparelho mandou o quê
        // continua fazendo sentido depois da revogação.
        dispositivo.setAtivo(false);
        dispositivoRepository.save(dispositivo);
    }

    /**
     * Confere, a cada leitura, se o aparelho ainda pode enviar. É o que dá
     * efeito imediato ao botão "Revogar", já que o token em si não expira.
     */
    @Transactional
    public Integer exigirDispositivoAtivo(Integer idDispositivo) {
        Dispositivo dispositivo = dispositivoRepository.findById(idDispositivo)
                .orElseThrow(() -> new AuthException("Dispositivo não encontrado.", HttpStatus.UNAUTHORIZED));
        if (!dispositivo.isAtivo()) {
            throw new AuthException("Este dispositivo foi revogado pela clínica.", HttpStatus.FORBIDDEN);
        }
        dispositivo.setUltimoContato(LocalDateTime.now());
        dispositivoRepository.save(dispositivo);
        return dispositivo.getIdClinica();
    }

    private String nomeOuPadrao(String informado, Integer idClinica) {
        if (informado != null && !informado.isBlank()) {
            return informado.trim().length() > 80 ? informado.trim().substring(0, 80) : informado.trim();
        }
        return "Goniômetro " + (dispositivoRepository.countByIdClinica(idClinica) + 1);
    }

    /** Sorteia até achar um código que não esteja valendo agora. */
    private String sortearCodigoLivre() {
        for (int i = 0; i < MAX_TENTATIVAS_CODIGO; i++) {
            String candidato = String.format("%06d", aleatorio.nextInt(1_000_000));
            if (codigoRepository.findByCodigoAndUsadoFalse(candidato).isEmpty()) {
                return candidato;
            }
        }
        throw new AuthException("Não foi possível gerar um código agora. Tente de novo.",
                HttpStatus.INTERNAL_SERVER_ERROR);
    }

    private void limparExpirados(LocalDateTime agora) {
        codigoRepository.deleteByExpiraEmBefore(agora.minusHours(1));
    }
}
