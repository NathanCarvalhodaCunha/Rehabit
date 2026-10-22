package com.rehabit.service;

import com.rehabit.config.RelogioConfig;
import com.rehabit.dto.NotificacaoDTO;
import com.rehabit.model.Notificacao;
import com.rehabit.repository.NotificacaoRepository;
import com.rehabit.security.PosseChecker;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class NotificacaoService {

    private final NotificacaoRepository notificacaoRepository;
    private final Clock relogio;

    public NotificacaoService(NotificacaoRepository notificacaoRepository, Clock relogio) {
        this.notificacaoRepository = notificacaoRepository;
        this.relogio = relogio;
    }

    @Transactional
    public void criar(Integer idClinica, String tipo, String mensagem) {
        Notificacao notificacao = new Notificacao();
        notificacao.setIdClinica(idClinica);
        notificacao.setTipo(tipo);
        notificacao.setMensagem(mensagem);
        notificacao.setLida(false);
        // Hora de Brasília: o sino lê o carimbo como hora local de quem olha,
        // e com a do servidor (UTC) toda notificação ficava "agora" por três horas.
        notificacao.setCriadaEm(LocalDateTime.now(relogio.withZone(RelogioConfig.FUSO)));
        notificacaoRepository.save(notificacao);
    }

    public List<NotificacaoDTO> listarPorClinica(Integer idClinica, Integer usuarioId, String usuarioTipo) {
        PosseChecker.exigirClinicaDona(idClinica, usuarioId, usuarioTipo);
        return notificacaoRepository.findByIdClinicaOrderByCriadaEmDesc(idClinica).stream()
                .map(n -> new NotificacaoDTO(n.getId(), n.getTipo(), n.getMensagem(), n.isLida(), n.getCriadaEm()))
                .collect(Collectors.toList());
    }

    @Transactional
    public void marcarTodasComoLidas(Integer idClinica, Integer usuarioId, String usuarioTipo) {
        PosseChecker.exigirClinicaDona(idClinica, usuarioId, usuarioTipo);
        List<Notificacao> naoLidas = notificacaoRepository.findByIdClinicaOrderByCriadaEmDesc(idClinica).stream()
                .filter(n -> !n.isLida())
                .collect(Collectors.toList());
        naoLidas.forEach(n -> n.setLida(true));
        notificacaoRepository.saveAll(naoLidas);
    }
}
