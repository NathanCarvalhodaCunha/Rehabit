package com.rehabit.service;

import com.rehabit.dto.NotificacaoDTO;
import com.rehabit.model.Notificacao;
import com.rehabit.repository.NotificacaoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class NotificacaoService {

    private final NotificacaoRepository notificacaoRepository;

    public NotificacaoService(NotificacaoRepository notificacaoRepository) {
        this.notificacaoRepository = notificacaoRepository;
    }

    @Transactional
    public void criar(Integer idClinica, String tipo, String mensagem) {
        Notificacao notificacao = new Notificacao();
        notificacao.setIdClinica(idClinica);
        notificacao.setTipo(tipo);
        notificacao.setMensagem(mensagem);
        notificacao.setLida(false);
        notificacao.setCriadaEm(LocalDateTime.now());
        notificacaoRepository.save(notificacao);
    }

    public List<NotificacaoDTO> listarPorClinica(Integer idClinica) {
        return notificacaoRepository.findByIdClinicaOrderByCriadaEmDesc(idClinica).stream()
                .map(n -> new NotificacaoDTO(n.getId(), n.getTipo(), n.getMensagem(), n.isLida(), n.getCriadaEm()))
                .collect(Collectors.toList());
    }

    @Transactional
    public void marcarTodasComoLidas(Integer idClinica) {
        List<Notificacao> naoLidas = notificacaoRepository.findByIdClinicaOrderByCriadaEmDesc(idClinica).stream()
                .filter(n -> !n.isLida())
                .collect(Collectors.toList());
        naoLidas.forEach(n -> n.setLida(true));
        notificacaoRepository.saveAll(naoLidas);
    }
}
