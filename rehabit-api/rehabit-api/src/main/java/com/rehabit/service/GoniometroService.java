package com.rehabit.service;

import com.rehabit.dto.GoniometroAmostraDTO;
import com.rehabit.dto.GoniometroCapturaDTO;
import com.rehabit.dto.GoniometroComandoRespostaDTO;
import com.rehabit.dto.GoniometroDTO;
import com.rehabit.dto.GoniometroEstadoDTO;
import com.rehabit.dto.GoniometroTelemetriaDTO;
import com.rehabit.exception.AuthException;
import com.rehabit.model.Fisioterapeuta;
import com.rehabit.model.Goniometro;
import com.rehabit.repository.FisioterapeutaRepository;
import com.rehabit.repository.GoniometroRepository;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Estado ao vivo do goniômetro de cada clínica.
 *
 * O fluxo inteiro passa por aqui: o ESP32 chega com POST /telemetria, o
 * estado é atualizado em memória, o evento é empurrado para as telas pelo
 * {@link GoniometroStream} e a resposta do POST leva de volta o próximo
 * comando pendente (tara, captura, identificar) e o intervalo de amostragem
 * que o aparelho deve usar.
 *
 * As leituras vivem em memória de propósito: são centenas por minuto e só
 * interessam enquanto o profissional está com a tela aberta. O que vai para
 * o banco é o cadastro do aparelho (bateria, sinal, último contato) e, no
 * fim, a amplitude escolhida pelo profissional — que é gravada como Medicao
 * junto da sessão, pelo SessaoService.
 */
@Service
public class GoniometroService {

    /** Sem pacote nesse tempo, o aparelho é considerado offline pela tela. */
    private static final long TIMEOUT_CONEXAO_SEGUNDOS = 8;

    /** Janela do gráfico ao vivo. */
    private static final long JANELA_HISTORICO_SEGUNDOS = 60;
    private static final int MAX_AMOSTRAS = 900;

    /** Intervalos de amostragem sugeridos ao aparelho, em ms. */
    private static final int INTERVALO_CAPTURA_MS = 100;
    private static final int INTERVALO_ATIVO_MS = 400;
    private static final int INTERVALO_OCIOSO_MS = 2000;

    /** Depois desse tempo sem ninguém pedir dados, o aparelho volta ao ritmo lento. */
    private static final long INTERESSE_VALIDO_SEGUNDOS = 20;

    /**
     * Teto de pontos guardados na curva de uma captura. A 10 Hz dá 2 minutos
     * de movimento; passando disso a série é decimada pela metade (e o passo
     * dobra), então uma captura longa continua cobrindo o movimento INTEIRO,
     * com menos resolução, em vez de ser cortada no meio.
     */
    private static final int MAX_PONTOS_CURVA = 1200;

    /** O cadastro no banco só é reescrito de tempos em tempos — não a cada amostra. */
    private static final long INTERVALO_PERSISTENCIA_SEGUNDOS = 20;

    private static final Set<String> COMANDOS_VALIDOS =
            Set.of("TARAR", "IDENTIFICAR", "INICIAR_CAPTURA", "PARAR_CAPTURA", "REINICIAR");

    private static final DateTimeFormatter FORMATO_SINCRONIZACAO =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

    private record Amostra(long instante, BigDecimal angulo) {
    }

    /** Tudo que se sabe do aparelho de uma clínica agora. Só existe em memória. */
    private static final class EstadoVivo {
        volatile BigDecimal angulo;
        volatile BigDecimal anguloBruto;
        volatile Integer bateria;
        volatile Integer rssi;
        volatile String numeroSerie;
        volatile String firmware;
        volatile String ip;
        volatile Boolean calibrado;
        volatile Instant ultimoContato;
        volatile Instant ultimaPersistencia;
        volatile Instant ultimoInteresse;
        volatile boolean conectadoNaUltimaChecagem;
        /** Preenchido uma única vez a partir do banco, para o estado não custar um SELECT por evento. */
        volatile boolean cadastroCarregado;
        volatile String sincronizadoEm;

        volatile boolean capturando;
        volatile Instant capturaInicio;
        volatile BigDecimal capturaMinimo;
        volatile BigDecimal capturaMaximo;
        volatile BigDecimal capturaSoma = BigDecimal.ZERO;
        volatile int capturaAmostras;
        volatile GoniometroCapturaDTO ultimaCaptura;

        /** A curva em si: pares (ms desde o início, ângulo), sempre sob synchronized(estado). */
        final List<Amostra> capturaSerie = new ArrayList<>();
        /** Guarda 1 a cada N amostras; dobra sempre que a série bate no teto. */
        int capturaPasso = 1;
        int capturaVistas;
        /** JSON da última captura encerrada, esperando ser anexado a uma sessão. */
        volatile String ultimaCurva;
        volatile Long ultimaCurvaIniciadaEm;

        final Deque<Amostra> historico = new ArrayDeque<>();
        final Queue<String> comandos = new ConcurrentLinkedQueue<>();
    }

    private final GoniometroRepository goniometroRepository;
    private final FisioterapeutaRepository fisioterapeutaRepository;
    private final GoniometroStream stream;
    private final Map<Integer, EstadoVivo> estados = new ConcurrentHashMap<>();

    public GoniometroService(GoniometroRepository goniometroRepository,
                              FisioterapeutaRepository fisioterapeutaRepository,
                              GoniometroStream stream) {
        this.goniometroRepository = goniometroRepository;
        this.fisioterapeutaRepository = fisioterapeutaRepository;
        this.stream = stream;
    }

    // ------------------------------------------------------------------
    // Aparelho -> servidor
    // ------------------------------------------------------------------

    /**
     * Recebe um pacote do ESP32, atualiza o estado ao vivo, empurra o evento
     * para as telas e devolve o próximo comando pendente.
     */
    public GoniometroComandoRespostaDTO registrarTelemetria(GoniometroTelemetriaDTO dados, Integer usuarioId,
                                                             String usuarioTipo) {
        verificarPosseClinica(dados.getIdClinica(), usuarioId, usuarioTipo);
        return aplicarTelemetria(dados.getIdClinica(), dados);
    }

    /**
     * Telemetria vinda de um goniômetro pareado. A clínica sai do token do
     * próprio aparelho e a posse já foi conferida ao validar o dispositivo, por
     * isso aqui não há o que checar — e o corpo do pacote nem carrega clínica,
     * justamente para um aparelho não conseguir escrever na clínica de outro.
     */
    public GoniometroComandoRespostaDTO registrarTelemetriaDeDispositivo(Integer idClinica,
                                                                          GoniometroTelemetriaDTO dados) {
        return aplicarTelemetria(idClinica, dados);
    }

    /** Leitura simples de um aparelho pareado (endpoint antigo, sem telemetria). */
    public void registrarLeituraDeDispositivo(Integer idClinica, BigDecimal angulo) {
        GoniometroTelemetriaDTO dados = new GoniometroTelemetriaDTO();
        dados.setAngulo(angulo);
        aplicarTelemetria(idClinica, dados);
    }

    private GoniometroComandoRespostaDTO aplicarTelemetria(Integer idClinica, GoniometroTelemetriaDTO dados) {
        EstadoVivo estado = estados.computeIfAbsent(idClinica, k -> new EstadoVivo());
        carregarCadastroSePreciso(idClinica, estado);
        Instant agora = Instant.now();
        BigDecimal angulo = arredondar(dados.getAngulo());

        estado.angulo = angulo;
        estado.anguloBruto = arredondar(dados.getAnguloBruto());
        if (dados.getBateria() != null) estado.bateria = limitarBateria(dados.getBateria());
        if (dados.getRssi() != null) estado.rssi = dados.getRssi();
        if (temTexto(dados.getNumeroSerie())) estado.numeroSerie = dados.getNumeroSerie().trim();
        if (temTexto(dados.getFirmware())) estado.firmware = dados.getFirmware().trim();
        if (temTexto(dados.getIp())) estado.ip = dados.getIp().trim();
        if (dados.getCalibrado() != null) estado.calibrado = dados.getCalibrado();

        boolean estavaOffline = estado.ultimoContato == null
                || estado.ultimoContato.isBefore(agora.minusSeconds(TIMEOUT_CONEXAO_SEGUNDOS));
        estado.ultimoContato = agora;
        estado.conectadoNaUltimaChecagem = true;

        registrarAmostra(estado, agora, angulo);
        acumularCaptura(estado, angulo, agora);
        persistirSePreciso(idClinica, estado, agora, estavaOffline);

        stream.publicar(idClinica, "estado", montarEstado(idClinica, estado, false));

        String comando = estado.comandos.poll();
        return new GoniometroComandoRespostaDTO(comando != null ? comando : "NENHUM",
                intervaloSugerido(idClinica, estado), interessados(idClinica, estado));
    }

    private void registrarAmostra(EstadoVivo estado, Instant agora, BigDecimal angulo) {
        if (angulo == null) {
            return;
        }
        long corte = agora.toEpochMilli() - JANELA_HISTORICO_SEGUNDOS * 1000;
        synchronized (estado.historico) {
            estado.historico.addLast(new Amostra(agora.toEpochMilli(), angulo));
            while (!estado.historico.isEmpty()
                    && (estado.historico.peekFirst().instante() < corte || estado.historico.size() > MAX_AMOSTRAS)) {
                estado.historico.removeFirst();
            }
        }
    }

    private void acumularCaptura(EstadoVivo estado, BigDecimal angulo, Instant agora) {
        if (!estado.capturando || angulo == null) {
            return;
        }
        synchronized (estado) {
            if (!estado.capturando) {
                return;
            }
            estado.capturaMinimo = estado.capturaMinimo == null ? angulo : estado.capturaMinimo.min(angulo);
            estado.capturaMaximo = estado.capturaMaximo == null ? angulo : estado.capturaMaximo.max(angulo);
            estado.capturaSoma = estado.capturaSoma.add(angulo);
            estado.capturaAmostras++;

            // Mín/máx/média veem TODAS as amostras (é o número que vai para a
            // sessão); só a curva é rala, e apenas quando fica longa demais.
            estado.capturaVistas++;
            if (estado.capturaVistas % estado.capturaPasso != 0) {
                return;
            }
            long desdeInicio = estado.capturaInicio == null ? 0
                    : Duration.between(estado.capturaInicio, agora).toMillis();
            estado.capturaSerie.add(new Amostra(desdeInicio, angulo));
            if (estado.capturaSerie.size() >= MAX_PONTOS_CURVA) {
                decimar(estado);
            }
        }
    }

    /** Joga fora um ponto sim, um não, e passa a guardar metade das amostras. */
    private void decimar(EstadoVivo estado) {
        List<Amostra> ralo = new ArrayList<>(estado.capturaSerie.size() / 2 + 1);
        for (int i = 0; i < estado.capturaSerie.size(); i += 2) {
            ralo.add(estado.capturaSerie.get(i));
        }
        estado.capturaSerie.clear();
        estado.capturaSerie.addAll(ralo);
        estado.capturaPasso *= 2;
    }

    /**
     * Serializa a curva como JSON: [[msDesdeOInicio, angulo], ...]. É montado
     * na mão porque só há números — nada para escapar — e assim a coluna do
     * banco fica com o formato exato que o gráfico do site consome.
     */
    private static String serializarCurva(List<Amostra> serie) {
        if (serie.isEmpty()) {
            return null;
        }
        StringBuilder json = new StringBuilder(serie.size() * 12).append('[');
        for (int i = 0; i < serie.size(); i++) {
            Amostra a = serie.get(i);
            if (i > 0) json.append(',');
            json.append('[').append(a.instante()).append(',').append(a.angulo().toPlainString()).append(']');
        }
        return json.append(']').toString();
    }

    /**
     * Grava o cadastro do aparelho no banco de vez em quando (e sempre que ele
     * volta do offline ou troca de identidade). A 10 amostras por segundo,
     * salvar a cada pacote seria escrita à toa em cima da mesma linha.
     */
    private void persistirSePreciso(Integer idClinica, EstadoVivo estado, Instant agora, boolean estavaOffline) {
        Goniometro cadastro = localizarCadastro(idClinica, estado.numeroSerie);
        boolean identidadeMudou = cadastro != null
                && ((estado.numeroSerie != null && !estado.numeroSerie.equals(cadastro.getNumeroSerie()))
                    || (estado.firmware != null && !estado.firmware.equals(cadastro.getFirmware())));
        boolean venceu = estado.ultimaPersistencia == null
                || estado.ultimaPersistencia.isBefore(agora.minusSeconds(INTERVALO_PERSISTENCIA_SEGUNDOS));

        if (!estavaOffline && !identidadeMudou && !venceu) {
            return;
        }

        Goniometro alvo = cadastro != null ? cadastro : new Goniometro();
        alvo.setIdClinica(idClinica);
        if (estado.numeroSerie != null) alvo.setNumeroSerie(estado.numeroSerie);
        if (estado.firmware != null) alvo.setFirmware(estado.firmware);
        if (estado.bateria != null) alvo.setBateria(estado.bateria);
        if (estado.rssi != null) alvo.setRssi(estado.rssi);
        if (estado.ip != null) alvo.setIp(estado.ip);
        alvo.setUltimoContato(LocalDateTime.ofInstant(agora, ZoneId.systemDefault()).withNano(0));
        if (alvo.getDataSincronizacao() == null) {
            alvo.setDataSincronizacao(LocalDate.now());
            alvo.setHoraSincronizacao(LocalTime.now().withNano(0));
        }
        goniometroRepository.save(alvo);
        estado.ultimaPersistencia = agora;
    }

    private Goniometro localizarCadastro(Integer idClinica, String numeroSerie) {
        if (temTexto(numeroSerie)) {
            Goniometro porSerie = goniometroRepository
                    .findFirstByIdClinicaAndNumeroSerieOrderByIdDesc(idClinica, numeroSerie).orElse(null);
            if (porSerie != null) {
                return porSerie;
            }
        }
        return goniometroRepository.findFirstByIdClinicaOrderByIdDesc(idClinica).orElse(null);
    }

    // ------------------------------------------------------------------
    // Site -> servidor
    // ------------------------------------------------------------------

    /** Retrato atual para a tela. Também conta como "tem gente olhando". */
    public GoniometroEstadoDTO estado(Integer idClinica, Integer usuarioId, String usuarioTipo) {
        verificarPosseClinica(idClinica, usuarioId, usuarioTipo);
        EstadoVivo estado = estados.computeIfAbsent(idClinica, k -> new EstadoVivo());
        estado.ultimoInteresse = Instant.now();
        carregarCadastroSePreciso(idClinica, estado);
        return montarEstado(idClinica, estado, true);
    }

    /**
     * Traz do banco o que o aparelho ainda não contou nesta execução do
     * servidor — número de série, firmware, última bateria e o carimbo da
     * última sincronização. Roda uma vez por clínica: o estado é montado
     * dezenas de vezes por minuto e não pode custar um SELECT cada vez.
     */
    private void carregarCadastroSePreciso(Integer idClinica, EstadoVivo estado) {
        if (estado.cadastroCarregado) {
            return;
        }
        estado.cadastroCarregado = true;
        goniometroRepository.findFirstByIdClinicaOrderByIdDesc(idClinica)
                .ifPresent(cadastro -> copiarCadastro(estado, cadastro));
    }

    private void copiarCadastro(EstadoVivo estado, Goniometro cadastro) {
        if (estado.numeroSerie == null) estado.numeroSerie = cadastro.getNumeroSerie();
        if (estado.firmware == null) estado.firmware = cadastro.getFirmware();
        if (estado.bateria == null) estado.bateria = cadastro.getBateria();
        if (estado.rssi == null) estado.rssi = cadastro.getRssi();
        if (estado.ip == null) estado.ip = cadastro.getIp();
        if (cadastro.getDataSincronizacao() != null && cadastro.getHoraSincronizacao() != null) {
            estado.sincronizadoEm = LocalDateTime
                    .of(cadastro.getDataSincronizacao(), cadastro.getHoraSincronizacao())
                    .format(FORMATO_SINCRONIZACAO);
        }
    }

    /** Marca interesse sem montar o DTO — usado quando a tela abre o SSE. */
    public void marcarInteresse(Integer idClinica) {
        estados.computeIfAbsent(idClinica, k -> new EstadoVivo()).ultimoInteresse = Instant.now();
    }

    public GoniometroEstadoDTO enfileirarComando(Integer idClinica, String comando, Integer usuarioId,
                                                  String usuarioTipo) {
        verificarPosseClinica(idClinica, usuarioId, usuarioTipo);
        String normalizado = comando == null ? "" : comando.trim().toUpperCase();
        if (!COMANDOS_VALIDOS.contains(normalizado)) {
            throw new AuthException("Comando desconhecido para o goniômetro.", HttpStatus.BAD_REQUEST);
        }
        EstadoVivo estado = estados.computeIfAbsent(idClinica, k -> new EstadoVivo());
        estado.ultimoInteresse = Instant.now();
        if (!conectado(estado)) {
            throw new AuthException("O goniômetro não está conectado. Ligue o aparelho e tente de novo.",
                    HttpStatus.CONFLICT);
        }
        // A fila é curta de propósito: se o aparelho está offline os comandos
        // não têm para onde ir, e reenviar tudo quando ele voltar seria pior
        // que perder (imagine três tarás enfileirados chegando de uma vez).
        while (estado.comandos.size() >= 5) {
            estado.comandos.poll();
        }
        estado.comandos.add(normalizado);
        return montarEstado(idClinica, estado, true);
    }

    public GoniometroEstadoDTO iniciarCaptura(Integer idClinica, Integer usuarioId, String usuarioTipo) {
        verificarPosseClinica(idClinica, usuarioId, usuarioTipo);
        EstadoVivo estado = estados.computeIfAbsent(idClinica, k -> new EstadoVivo());
        estado.ultimoInteresse = Instant.now();
        if (!conectado(estado)) {
            throw new AuthException("O goniômetro não está conectado. Ligue o aparelho e tente de novo.",
                    HttpStatus.CONFLICT);
        }
        synchronized (estado) {
            estado.capturando = true;
            estado.capturaInicio = Instant.now();
            estado.capturaMinimo = estado.angulo;
            estado.capturaMaximo = estado.angulo;
            estado.capturaSoma = estado.angulo != null ? estado.angulo : BigDecimal.ZERO;
            estado.capturaAmostras = estado.angulo != null ? 1 : 0;
            estado.ultimaCaptura = null;
            estado.capturaSerie.clear();
            estado.capturaPasso = 1;
            estado.capturaVistas = 0;
            if (estado.angulo != null) {
                estado.capturaSerie.add(new Amostra(0, estado.angulo));
            }
            estado.ultimaCurva = null;
            estado.ultimaCurvaIniciadaEm = null;
        }
        estado.comandos.add("INICIAR_CAPTURA");
        GoniometroEstadoDTO dto = montarEstado(idClinica, estado, true);
        stream.publicar(idClinica, "estado", dto);
        return dto;
    }

    public GoniometroEstadoDTO pararCaptura(Integer idClinica, Integer usuarioId, String usuarioTipo) {
        verificarPosseClinica(idClinica, usuarioId, usuarioTipo);
        EstadoVivo estado = estados.computeIfAbsent(idClinica, k -> new EstadoVivo());
        estado.ultimoInteresse = Instant.now();
        synchronized (estado) {
            if (estado.capturando) {
                estado.ultimaCaptura = montarCaptura(estado, false);
                guardarCurva(estado);
            }
            estado.capturando = false;
        }
        estado.comandos.add("PARAR_CAPTURA");
        GoniometroEstadoDTO dto = montarEstado(idClinica, estado, true);
        stream.publicar(idClinica, "estado", dto);
        return dto;
    }

    // ------------------------------------------------------------------
    // Cadastro do aparelho (telas antigas continuam funcionando)
    // ------------------------------------------------------------------

    public GoniometroDTO buscarUltimo(Integer idClinica, Integer usuarioId, String usuarioTipo) {
        verificarPosseClinica(idClinica, usuarioId, usuarioTipo);
        Goniometro goniometro = goniometroRepository.findFirstByIdClinicaOrderByIdDesc(idClinica)
                .orElseThrow(() -> new AuthException("Nenhum dispositivo sincronizado ainda.", HttpStatus.NOT_FOUND));
        return paraDTO(goniometro);
    }

    /**
     * "Sincronizar" na tela: carimba data/hora e traz para o cadastro o que o
     * aparelho já mandou por telemetria. Se ele nunca falou com o servidor, a
     * linha nasce mesmo assim — a tela precisa de algo para mostrar.
     */
    public GoniometroDTO sincronizar(Integer idClinica, Integer usuarioId, String usuarioTipo) {
        verificarPosseClinica(idClinica, usuarioId, usuarioTipo);
        EstadoVivo estado = estados.get(idClinica);
        Goniometro goniometro = localizarCadastro(idClinica, estado != null ? estado.numeroSerie : null);
        if (goniometro == null) {
            goniometro = new Goniometro();
            goniometro.setIdClinica(idClinica);
        }
        if (estado != null) {
            if (estado.numeroSerie != null) goniometro.setNumeroSerie(estado.numeroSerie);
            if (estado.firmware != null) goniometro.setFirmware(estado.firmware);
            if (estado.bateria != null) goniometro.setBateria(estado.bateria);
            if (estado.rssi != null) goniometro.setRssi(estado.rssi);
            if (estado.ip != null) goniometro.setIp(estado.ip);
            if (estado.ultimoContato != null) {
                goniometro.setUltimoContato(
                        LocalDateTime.ofInstant(estado.ultimoContato, ZoneId.systemDefault()).withNano(0));
            }
        }
        goniometro.setDataSincronizacao(LocalDate.now());
        goniometro.setHoraSincronizacao(LocalTime.now().withNano(0));
        Goniometro salvo = goniometroRepository.save(goniometro);
        EstadoVivo paraCarimbar = estados.computeIfAbsent(idClinica, k -> new EstadoVivo());
        paraCarimbar.cadastroCarregado = true;
        paraCarimbar.sincronizadoEm = LocalDateTime
                .of(salvo.getDataSincronizacao(), salvo.getHoraSincronizacao()).format(FORMATO_SINCRONIZACAO);
        stream.publicar(idClinica, "estado", montarEstado(idClinica, paraCarimbar, false));
        return paraDTO(salvo);
    }

    /** Compatibilidade: firmware antigo manda só {idClinica, angulo} aqui. */
    public void registrarLeitura(Integer idClinica, Integer usuarioId, String usuarioTipo, BigDecimal angulo) {
        GoniometroTelemetriaDTO dados = new GoniometroTelemetriaDTO();
        dados.setIdClinica(idClinica);
        dados.setAngulo(angulo);
        registrarTelemetria(dados, usuarioId, usuarioTipo);
    }

    /** Compatibilidade: telas antigas leem só o ângulo, sem o resto do estado. */
    public BigDecimal buscarLeituraAtual(Integer idClinica, Integer usuarioId, String usuarioTipo) {
        verificarPosseClinica(idClinica, usuarioId, usuarioTipo);
        EstadoVivo estado = estados.get(idClinica);
        if (estado == null) {
            return null;
        }
        estado.ultimoInteresse = Instant.now();
        return conectado(estado) ? estado.angulo : null;
    }

    // ------------------------------------------------------------------
    // Vigia de conexão
    // ------------------------------------------------------------------

    /**
     * O aparelho não avisa quando cai — só para de falar. Este vigia percebe
     * o silêncio e avisa as telas na hora, para o status não ficar "conectado"
     * até alguém dar F5.
     */
    @Scheduled(fixedRate = 2000)
    public void vigiarConexoes() {
        estados.forEach((idClinica, estado) -> {
            boolean conectadoAgora = conectado(estado);
            if (conectadoAgora == estado.conectadoNaUltimaChecagem) {
                return;
            }
            estado.conectadoNaUltimaChecagem = conectadoAgora;
            if (!conectadoAgora) {
                // Uma captura em andamento não sobrevive à queda do aparelho:
                // fecha com o que deu tempo de medir em vez de ficar "gravando"
                // para sempre.
                synchronized (estado) {
                    if (estado.capturando) {
                        estado.ultimaCaptura = montarCaptura(estado, false);
                        guardarCurva(estado);
                        estado.capturando = false;
                    }
                }
            }
            stream.publicar(idClinica, "estado", montarEstado(idClinica, estado, true));
        });
    }

    // ------------------------------------------------------------------
    // Montagem dos DTOs
    // ------------------------------------------------------------------

    private GoniometroEstadoDTO montarEstado(Integer idClinica, EstadoVivo estado, boolean incluirHistorico) {
        GoniometroEstadoDTO dto = new GoniometroEstadoDTO();
        boolean online = conectado(estado);
        dto.setConectado(online);
        // Um ângulo velho na tela é pior que nenhum: se o aparelho caiu, o
        // número desaparece em vez de congelar no último valor recebido.
        dto.setAngulo(online ? estado.angulo : null);
        dto.setAnguloBruto(online ? estado.anguloBruto : null);
        dto.setBateria(estado.bateria);
        dto.setRssi(estado.rssi);
        dto.setNumeroSerie(estado.numeroSerie);
        dto.setFirmware(estado.firmware);
        dto.setIp(estado.ip);
        dto.setCalibrado(estado.calibrado);
        if (estado.ultimoContato != null) {
            dto.setUltimoContato(estado.ultimoContato.toEpochMilli());
            dto.setSegundosDesdeContato(Duration.between(estado.ultimoContato, Instant.now()).getSeconds());
        }
        dto.setCaptura(estado.capturando ? montarCaptura(estado, true) : estado.ultimaCaptura);
        dto.setSincronizadoEm(estado.sincronizadoEm);

        if (incluirHistorico) {
            List<GoniometroAmostraDTO> amostras = new ArrayList<>();
            synchronized (estado.historico) {
                for (Amostra amostra : estado.historico) {
                    amostras.add(new GoniometroAmostraDTO(amostra.instante(), amostra.angulo()));
                }
            }
            dto.setHistorico(amostras);
        }
        return dto;
    }

    /** Chamado sempre sob synchronized(estado). */
    private void guardarCurva(EstadoVivo estado) {
        estado.ultimaCurva = serializarCurva(estado.capturaSerie);
        estado.ultimaCurvaIniciadaEm = estado.capturaInicio == null ? null : estado.capturaInicio.toEpochMilli();
    }

    /**
     * A curva da captura que o formulário de sessão diz ter usado, ou null.
     *
     * O id é o instante em que a captura começou: se o servidor reiniciou, se
     * outra captura rodou por cima ou se a tela está com dado velho, os ids não
     * batem e a sessão é salva sem curva — melhor perder o gráfico do que
     * pendurar no paciente a curva de outro movimento.
     */
    public String curvaDaCaptura(Integer idClinica, Long iniciadaEm) {
        if (idClinica == null || iniciadaEm == null) {
            return null;
        }
        EstadoVivo estado = estados.get(idClinica);
        if (estado == null) {
            return null;
        }
        synchronized (estado) {
            return iniciadaEm.equals(estado.ultimaCurvaIniciadaEm) ? estado.ultimaCurva : null;
        }
    }

    private GoniometroCapturaDTO montarCaptura(EstadoVivo estado, boolean ativa) {
        GoniometroCapturaDTO dto = new GoniometroCapturaDTO();
        dto.setAtiva(ativa);
        dto.setAmostras(estado.capturaAmostras);
        dto.setMinimo(estado.capturaMinimo);
        dto.setMaximo(estado.capturaMaximo);
        if (estado.capturaMinimo != null && estado.capturaMaximo != null) {
            dto.setAmplitude(estado.capturaMaximo.subtract(estado.capturaMinimo).setScale(1, RoundingMode.HALF_UP));
        }
        if (estado.capturaAmostras > 0) {
            dto.setMedia(estado.capturaSoma.divide(BigDecimal.valueOf(estado.capturaAmostras), 1,
                    RoundingMode.HALF_UP));
        }
        if (estado.capturaInicio != null) {
            dto.setIniciadaEm(estado.capturaInicio.toEpochMilli());
            dto.setDuracaoSegundos(Duration.between(estado.capturaInicio, Instant.now()).getSeconds());
        }
        return dto;
    }

    private GoniometroDTO paraDTO(Goniometro g) {
        GoniometroDTO dto = new GoniometroDTO(g.getId(), g.getBateria(), g.getDataSincronizacao(),
                g.getHoraSincronizacao(), g.getIdClinica());
        dto.setNumeroSerie(g.getNumeroSerie());
        dto.setFirmware(g.getFirmware());
        dto.setRssi(g.getRssi());
        return dto;
    }

    // ------------------------------------------------------------------
    // Apoio
    // ------------------------------------------------------------------

    private boolean conectado(EstadoVivo estado) {
        return estado.ultimoContato != null
                && estado.ultimoContato.isAfter(Instant.now().minusSeconds(TIMEOUT_CONEXAO_SEGUNDOS));
    }

    /**
     * Ritmo que o aparelho deve usar. Gravando: o mais rápido possível, para
     * não perder o pico do movimento. Com alguém olhando: rápido o bastante
     * para parecer instantâneo. Sem ninguém: devagar, que é bateria.
     */
    private int intervaloSugerido(Integer idClinica, EstadoVivo estado) {
        if (estado.capturando) {
            return INTERVALO_CAPTURA_MS;
        }
        return interessados(idClinica, estado) ? INTERVALO_ATIVO_MS : INTERVALO_OCIOSO_MS;
    }

    private boolean interessados(Integer idClinica, EstadoVivo estado) {
        if (stream.temOuvintes(idClinica)) {
            return true;
        }
        return estado.ultimoInteresse != null
                && estado.ultimoInteresse.isAfter(Instant.now().minusSeconds(INTERESSE_VALIDO_SEGUNDOS));
    }

    private static BigDecimal arredondar(BigDecimal valor) {
        return valor == null ? null : valor.setScale(1, RoundingMode.HALF_UP);
    }

    private static Integer limitarBateria(Integer bateria) {
        return Math.max(0, Math.min(100, bateria));
    }

    private static boolean temTexto(String valor) {
        return valor != null && !valor.isBlank();
    }

    /** A própria clínica, ou um fisioterapeuta que pertence a ela (dispositivo é compartilhado pela clínica toda). */
    private void verificarPosseClinica(Integer idClinica, Integer usuarioId, String usuarioTipo) {
        if ("CLINICA".equals(usuarioTipo) && idClinica.equals(usuarioId)) {
            return;
        }
        if ("FISIOTERAPEUTA".equals(usuarioTipo)) {
            Fisioterapeuta fisioterapeuta = fisioterapeutaRepository.findById(usuarioId).orElse(null);
            if (fisioterapeuta != null && idClinica.equals(fisioterapeuta.getIdClinica())) {
                return;
            }
        }
        throw new AuthException("Você não tem acesso a este recurso.", HttpStatus.FORBIDDEN);
    }
}
