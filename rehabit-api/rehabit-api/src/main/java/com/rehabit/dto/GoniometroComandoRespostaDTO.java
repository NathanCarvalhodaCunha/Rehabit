package com.rehabit.dto;

/**
 * Resposta ao POST de telemetria. É por aqui que o servidor fala com o
 * aparelho: o ESP32 não abre porta nem escuta nada, ele só lê o que veio
 * de volta no próprio POST que acabou de fazer.
 */
public class GoniometroComandoRespostaDTO {

    /** "NENHUM", "TARAR", "IDENTIFICAR", "INICIAR_CAPTURA" ou "PARAR_CAPTURA". */
    private String comando;

    /** Intervalo entre amostras que o aparelho deve usar a partir de agora. */
    private int intervaloMs;

    /** true enquanto alguém está com a tela do dispositivo aberta ou capturando. */
    private boolean emUso;

    /**
     * Se há uma captura aberta no servidor. Quando a conexão cai no meio de
     * uma gravação, o servidor fecha a captura sozinho e não tem como mandar
     * o PARAR_CAPTURA; é por este campo que o aparelho fica sabendo, no
     * primeiro envio depois que a conexão volta, e para de dizer "gravando".
     */
    private boolean capturando;

    public GoniometroComandoRespostaDTO() {
    }

    public GoniometroComandoRespostaDTO(String comando, int intervaloMs, boolean emUso, boolean capturando) {
        this.comando = comando;
        this.intervaloMs = intervaloMs;
        this.emUso = emUso;
        this.capturando = capturando;
    }

    public String getComando() {
        return comando;
    }

    public void setComando(String comando) {
        this.comando = comando;
    }

    public int getIntervaloMs() {
        return intervaloMs;
    }

    public void setIntervaloMs(int intervaloMs) {
        this.intervaloMs = intervaloMs;
    }

    public boolean isEmUso() {
        return emUso;
    }

    public void setEmUso(boolean emUso) {
        this.emUso = emUso;
    }

    public boolean isCapturando() {
        return capturando;
    }

    public void setCapturando(boolean capturando) {
        this.capturando = capturando;
    }
}
