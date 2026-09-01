/* Rehabit — tela Dispositivo.

   Desenha o estado ao vivo que chega pelo RehabitGoniometro: mostrador de
   ângulo, gráfico dos últimos 60 s, telemetria do aparelho e os comandos que
   a tela pode mandar de volta (tara, identificar, captura, reiniciar).
*/
(function carregarDispositivo() {
  const cartaoVivo = document.querySelector("[data-live-card]");
  if (!cartaoVivo) return;

  const sessao = getSessao();
  if (!sessao) return;

  /* Faixa do mostrador. Vai um pouco abaixo de zero porque hiperextensão
     existe (joelho e cotovelo passam de 0°) e o ponteiro não pode "sumir"
     na ponta esquerda quando isso acontece. */
  const ANGULO_MIN = -30;
  const ANGULO_MAX = 180;
  const COMPRIMENTO_ARCO = 276.46;

  const CHART_LARGURA = 600;
  const CHART_ALTURA = 170;
  const CHART_MARGEM = 10;
  const JANELA_MS = 60000;
  /* Amplitude mínima do eixo Y: sem isto, um aparelho parado vira um gráfico
     de ruído de 0,2° ocupando a altura toda. */
  const FAIXA_MINIMA_GRAUS = 10;

  const arco = cartaoVivo.querySelector("[data-gauge-progress]");
  const ponteiro = cartaoVivo.querySelector("[data-gauge-needle]");
  const valorAngulo = cartaoVivo.querySelector("[data-angulo]");
  const dicaMostrador = cartaoVivo.querySelector("[data-gauge-hint]");
  const selo = cartaoVivo.querySelector(".conn-badge");
  const seloTexto = cartaoVivo.querySelector(".conn-text");
  const linhaGrafico = cartaoVivo.querySelector("[data-chart-line]");
  const areaGrafico = cartaoVivo.querySelector("[data-chart-area]");
  const graficoVazio = cartaoVivo.querySelector("[data-chart-vazio]");
  const escalaGrafico = cartaoVivo.querySelector("[data-chart-escala]");
  const legendaJanela = cartaoVivo.querySelector("[data-chart-janela]");
  const botaoCaptura = cartaoVivo.querySelector('[data-action="captura"]');
  const botaoTarar = cartaoVivo.querySelector('[data-action="tarar"]');
  const botaoIdentificar = cartaoVivo.querySelector('[data-action="identificar"]');
  const botaoReiniciar = document.querySelector('[data-action="reiniciar"]');
  const botoesAtualizar = document.querySelectorAll('[data-action="atualizar"]');

  let ultimoEstado = null;
  let capturando = false;
  /* Série do gráfico montada no cliente: o handshake do SSE traz a janela
     inteira uma vez e cada evento seguinte acrescenta um ponto. Reenviar as
     centenas de amostras da janela a cada pacote custaria caro à toa. */
  let serie = [];

  function acumularSerie(estado) {
    if (Array.isArray(estado.historico)) {
      serie = estado.historico.filter((a) => a && a.angulo != null);
    } else if (estado.angulo != null && estado.ultimoContato != null) {
      const ultimo = serie[serie.length - 1];
      if (!ultimo || ultimo.t !== estado.ultimoContato) {
        serie.push({ t: estado.ultimoContato, angulo: estado.angulo });
      }
    }
    const fim = serie.length ? serie[serie.length - 1].t : 0;
    serie = serie.filter((a) => a.t > fim - JANELA_MS);
  }

  // ---------------------------------------------------------------- formato

  function grau(valor) {
    return valor == null ? "–" : `${Number(valor).toFixed(1).replace(".", ",")}°`;
  }

  function textoSinal(rssi) {
    if (rssi == null) return "–";
    let qualidade = "Fraco";
    if (rssi >= -55) qualidade = "Excelente";
    else if (rssi >= -67) qualidade = "Bom";
    else if (rssi >= -78) qualidade = "Razoável";
    return `${qualidade} (${rssi} dBm)`;
  }

  function textoContato(segundos) {
    if (segundos == null) return "Nunca";
    if (segundos < 2) return "Agora mesmo";
    if (segundos < 60) return `Há ${Math.round(segundos)} s`;
    if (segundos < 3600) return `Há ${Math.round(segundos / 60)} min`;
    return `Há ${Math.round(segundos / 3600)} h`;
  }

  function definirInfo(chave, texto) {
    document.querySelectorAll(`[data-info="${chave}"]`).forEach((el) => {
      el.textContent = texto;
    });
  }

  // ---------------------------------------------------------------- desenho

  function desenharMostrador(angulo) {
    if (angulo == null) {
      valorAngulo.textContent = "–";
      arco.style.strokeDashoffset = COMPRIMENTO_ARCO;
      ponteiro.style.transform = "rotate(0deg)";
      return;
    }
    valorAngulo.textContent = Number(angulo).toFixed(1).replace(".", ",");
    const fracao = Math.min(1, Math.max(0, (angulo - ANGULO_MIN) / (ANGULO_MAX - ANGULO_MIN)));
    arco.style.strokeDashoffset = COMPRIMENTO_ARCO * (1 - fracao);
    ponteiro.style.transform = `rotate(${fracao * 180}deg)`;
  }

  function desenharGrafico(historico) {
    const amostras = Array.isArray(historico) ? historico.filter((a) => a && a.angulo != null) : [];
    if (amostras.length < 2) {
      linhaGrafico.setAttribute("points", "");
      areaGrafico.setAttribute("points", "");
      graficoVazio.hidden = false;
      escalaGrafico.textContent = "–";
      legendaJanela.textContent = "últimos 60 s";
      return;
    }
    graficoVazio.hidden = true;

    const fim = amostras[amostras.length - 1].t;
    /* Enquanto a janela de 60 s não encheu, o eixo X vale só o tempo que
       existe — um gráfico com metade em branco parece defeito, e a legenda
       diz quantos segundos estão sendo mostrados de verdade. */
    const duracao = Math.max(1000, Math.min(JANELA_MS, fim - amostras[0].t));
    const inicio = fim - duracao;
    const valores = amostras.map((a) => Number(a.angulo));
    let baixo = Math.min.apply(null, valores);
    let alto = Math.max.apply(null, valores);
    if (alto - baixo < FAIXA_MINIMA_GRAUS) {
      const meio = (alto + baixo) / 2;
      baixo = meio - FAIXA_MINIMA_GRAUS / 2;
      alto = meio + FAIXA_MINIMA_GRAUS / 2;
    }

    const alturaUtil = CHART_ALTURA - CHART_MARGEM * 2;
    const pontos = amostras.map((amostra) => {
      const x = ((amostra.t - inicio) / duracao) * CHART_LARGURA;
      const y = CHART_ALTURA - CHART_MARGEM - ((Number(amostra.angulo) - baixo) / (alto - baixo)) * alturaUtil;
      return `${x.toFixed(1)},${y.toFixed(1)}`;
    });

    linhaGrafico.setAttribute("points", pontos.join(" "));
    // A área é a mesma linha fechada contra a base do gráfico.
    const primeiroX = pontos[0].split(",")[0];
    const ultimoX = pontos[pontos.length - 1].split(",")[0];
    areaGrafico.setAttribute(
      "points",
      `${primeiroX},${CHART_ALTURA} ${pontos.join(" ")} ${ultimoX},${CHART_ALTURA}`
    );
    escalaGrafico.textContent = `${grau(baixo)} a ${grau(alto)}`;
    legendaJanela.textContent = `últimos ${Math.round(duracao / 1000)} s`;
  }

  function desenharSelo(estado) {
    if (estado.captura && estado.captura.ativa) {
      selo.dataset.conn = "capturando";
      seloTexto.textContent = "Gravando captura";
      return;
    }
    if (estado.conectado) {
      selo.dataset.conn = "conectado";
      seloTexto.textContent = "Conectado";
      return;
    }
    selo.dataset.conn = estado.ultimoContato ? "desconectado" : "aguardando";
    seloTexto.textContent = estado.ultimoContato ? "Desconectado" : "Aguardando o aparelho";
  }

  function desenharEstatisticas(estado) {
    const captura = estado.captura;
    let minimo = null;
    let maximo = null;
    let amplitude = null;

    if (captura && (captura.ativa || captura.amostras)) {
      minimo = captura.minimo;
      maximo = captura.maximo;
      amplitude = captura.amplitude;
    } else if (serie.length) {
      // Sem captura, as estatísticas descrevem a janela que está no gráfico —
      // é o que o profissional está vendo.
      const valores = serie.map((a) => Number(a.angulo));
      if (valores.length) {
        minimo = Math.min.apply(null, valores);
        maximo = Math.max.apply(null, valores);
        amplitude = maximo - minimo;
      }
    }

    cartaoVivo.querySelector('[data-stat="minimo"]').textContent = grau(minimo);
    cartaoVivo.querySelector('[data-stat="maximo"]').textContent = grau(maximo);
    cartaoVivo.querySelector('[data-stat="amplitude"]').textContent = grau(amplitude);
  }

  function desenharDica(estado) {
    const captura = estado.captura;
    if (captura && captura.ativa) {
      const segundos = captura.duracaoSegundos != null ? captura.duracaoSegundos : 0;
      dicaMostrador.textContent = `Gravando há ${segundos} s — peça o movimento completo`;
      return;
    }
    if (captura && captura.amostras) {
      dicaMostrador.textContent = `Última captura: amplitude de ${grau(captura.amplitude)}`;
      return;
    }
    if (!estado.conectado) {
      dicaMostrador.textContent = estado.ultimoContato
        ? "O aparelho parou de enviar leituras"
        : "Ligue o goniômetro e aguarde a conexão";
      return;
    }
    if (estado.calibrado === false) {
      dicaMostrador.textContent = "Calibrando o sensor — mantenha o aparelho parado";
      return;
    }
    dicaMostrador.textContent = "Leitura ao vivo · estatísticas dos últimos 60 s";
  }

  function desenharTelemetria(estado) {
    definirInfo("serie", estado.numeroSerie || "–");
    definirInfo("firmware", estado.firmware || "–");
    definirInfo("sinal", estado.conectado ? textoSinal(estado.rssi) : "–");
    definirInfo("ip", estado.ip || "–");
    definirInfo("contato", textoContato(estado.segundosDesdeContato));

    const bateria = estado.bateria;
    document.querySelectorAll(".battery-num").forEach((el) => {
      el.textContent = bateria != null ? `${bateria}%` : "–";
    });
    document.querySelectorAll(".battery-fill").forEach((el) => {
      el.style.width = `${bateria != null ? bateria : 0}%`;
      el.style.background = bateria == null ? "#9CA3AF" : bateria <= 15 ? "#DC2626" : bateria <= 40 ? "#F59E0B" : "#22C55E";
    });

    document.querySelectorAll("[data-device-name]").forEach((el) => {
      el.textContent = estado.conectado
        ? "Dispositivo conectado"
        : estado.ultimoContato
        ? "Dispositivo offline"
        : "Nenhum goniômetro pareado ainda";
    });
    document.querySelectorAll("[data-device-sub]").forEach((el) => {
      el.textContent = estado.numeroSerie ? `Rehabit Goniômetro · ${estado.numeroSerie}` : "Rehabit Goniômetro";
    });
  }

  function desenharBotoes(estado) {
    capturando = !!(estado.captura && estado.captura.ativa);
    if (botaoCaptura) {
      botaoCaptura.textContent = capturando ? "Parar captura" : "Iniciar captura";
      botaoCaptura.dataset.capturando = capturando ? "true" : "false";
      // Parar sempre pode (inclusive para fechar uma captura órfã); iniciar
      // só faz sentido com o aparelho falando.
      botaoCaptura.disabled = !estado.conectado && !capturando;
    }
    [botaoTarar, botaoIdentificar, botaoReiniciar].forEach((botao) => {
      if (botao) botao.disabled = !estado.conectado;
    });
  }

  function desenhar(estado) {
    ultimoEstado = estado;
    acumularSerie(estado);
    desenharSelo(estado);
    desenharMostrador(estado.angulo);
    desenharGrafico(serie);
    desenharEstatisticas(estado);
    desenharDica(estado);
    desenharTelemetria(estado);
    desenharBotoes(estado);
  }

  // ---------------------------------------------------------------- ações

  function comandoSimples(botao, comando, mensagem) {
    if (!botao) return;
    botao.addEventListener("click", async () => {
      botao.disabled = true;
      try {
        await RehabitGoniometro.comando(comando);
        RehabitToast.sucesso(mensagem);
      } catch (err) {
        RehabitToast.erro(err.message);
      } finally {
        // O próximo estado recalcula o disabled correto; reabilitar aqui só
        // evita que o botão fique preso se nenhum evento chegar.
        botao.disabled = false;
      }
    });
  }

  comandoSimples(botaoTarar, "TARAR", "Tara enviada — o ângulo atual virou o zero do aparelho.");
  comandoSimples(botaoIdentificar, "IDENTIFICAR", "O LED do aparelho vai piscar por alguns segundos.");
  comandoSimples(botaoReiniciar, "REINICIAR", "Comando de reinício enviado ao aparelho.");

  if (botaoCaptura) {
    botaoCaptura.addEventListener("click", async () => {
      botaoCaptura.disabled = true;
      try {
        if (capturando) {
          const estado = await RehabitGoniometro.pararCaptura();
          const amplitude = estado && estado.captura ? estado.captura.amplitude : null;
          RehabitToast.sucesso(
            amplitude != null ? `Captura encerrada — amplitude de ${grau(amplitude)}.` : "Captura encerrada."
          );
        } else {
          await RehabitGoniometro.iniciarCaptura();
          RehabitToast.info("Captura iniciada — peça o movimento completo da articulação.");
        }
      } catch (err) {
        RehabitToast.erro(err.message);
      } finally {
        botaoCaptura.disabled = false;
      }
    });
  }

  /* "Atualizar agora" relê o estado e a lista de pareados na hora. Não vai
     buscar nada no goniômetro — o aparelho não escuta em porta nenhuma, quem
     empurra os dados é ele. Serve para quando o canal ao vivo caiu ou algo
     mudou em outra aba. */
  botoesAtualizar.forEach((botao) => {
    botao.addEventListener("click", async () => {
      botao.disabled = true;
      try {
        await RehabitGoniometro.atualizar();
        // Cada módulo da tela recarrega o que é seu; assim este botão não
        // precisa conhecer a lista de aparelhos pareados.
        document.dispatchEvent(new CustomEvent("rehabit:atualizar"));
        RehabitToast.sucesso("Dados atualizados.");
      } catch (err) {
        RehabitToast.erro(err.message);
      } finally {
        botao.disabled = false;
      }
    });
  });

  /* O "há X s" e o relógio da captura envelhecem sozinhos entre um pacote e
     outro; sem este tique a tela ficaria congelada no último evento. */
  setInterval(() => {
    if (!ultimoEstado) return;
    if (ultimoEstado.segundosDesdeContato != null) {
      ultimoEstado.segundosDesdeContato += 1;
      definirInfo("contato", textoContato(ultimoEstado.segundosDesdeContato));
    }
    if (ultimoEstado.captura && ultimoEstado.captura.ativa && ultimoEstado.captura.duracaoSegundos != null) {
      ultimoEstado.captura.duracaoSegundos += 1;
      desenharDica(ultimoEstado);
    }
  }, 1000);

  RehabitGoniometro.conectar(desenhar).catch((err) => {
    RehabitToast.erro(err.message);
    desenhar({ conectado: false, historico: [] });
  });
})();
