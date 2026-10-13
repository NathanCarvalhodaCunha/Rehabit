/* Rehabit — cliente do goniômetro em tempo real.

   Um só lugar que sabe conversar com a API do aparelho, usado tanto pela tela
   Dispositivo quanto pelo formulário de sessão.

   API global:
     RehabitGoniometro.conectar(aoAtualizar)  -> abre o canal e chama aoAtualizar(estado) a cada evento
     RehabitGoniometro.desconectar()
     RehabitGoniometro.comando("TARAR")
     RehabitGoniometro.iniciarCaptura() / pararCaptura()
     RehabitGoniometro.idClinica()            -> Promise com o id da clínica da sessão
     RehabitGoniometro.simulado()             -> true na demonstração pública (aparelho simulado)

   Transporte: SSE (EventSource) como caminho principal — o servidor empurra
   cada leitura assim que ela chega do ESP32. Se o SSE não subir ou cair
   repetidamente (proxy que corta streaming, rede corporativa), o módulo cai
   sozinho para polling do /estado e continua funcionando, só que menos fluido.
*/
(function () {
  "use strict";

  var INTERVALO_POLLING_MS = 1000;
  // Reconexão do SSE com espera que dobra a cada queda seguida (3 s, 6 s,
  // 12 s), até um minuto. Sem isso, um stream que o servidor fechasse logo
  // depois de abrir virava um pedido de ingresso a cada 3 s, para sempre.
  var ESPERA_RECONEXAO_MS = 3000;
  var ESPERA_MAXIMA_MS = 60000;
  // Só uma conexão que ficou de pé esse tempo conta como "funcionou" e zera a
  // contagem de quedas — abrir e cair logo em seguida é queda.
  var CONEXAO_ESTAVEL_MS = 30000;
  // Quedas seguidas até desconfiar do transporte (proxy que corta streaming)
  // e passar para o polling.
  var FALHAS_ATE_DESISTIR_DO_SSE = 4;

  var idClinicaCache = null;
  var fonte = null;
  var timerPolling = null;
  var timerReconexao = null;
  var ouvintes = [];
  var falhasSeguidas = 0;
  var usandoPolling = false;
  var ativo = false;
  var ultimoEstado = null;
  // Cada abertura do SSE ganha um número. A resposta do ingresso de uma
  // tentativa que já não é a última (a aba foi escondida, outra começou) é
  // descartada: antes, idas e vindas da aba deixavam streams órfãos abertos.
  var geracao = 0;

  function sessao() {
    return typeof getSessao === "function" ? getSessao() : null;
  }

  /* A clínica não está na sessão do fisioterapeuta — só o id dele —, então a
     primeira chamada resolve isso na API e guarda para as próximas. */
  function idClinica() {
    if (idClinicaCache != null) return Promise.resolve(idClinicaCache);
    var s = sessao();
    if (!s) return Promise.reject(new Error("Sessão não encontrada."));
    if (s.tipo === "CLINICA") {
      idClinicaCache = s.id;
      return Promise.resolve(idClinicaCache);
    }
    return apiGet("/fisioterapeutas/" + s.id).then(function (fisioterapeuta) {
      idClinicaCache = fisioterapeuta.idClinica;
      return idClinicaCache;
    });
  }

  function avisar(estado) {
    ultimoEstado = estado;
    ouvintes.forEach(function (ouvinte) {
      try {
        ouvinte(estado);
      } catch (err) {
        // Um ouvinte quebrado não pode derrubar os outros nem o canal.
        console.error("Falha ao processar estado do goniômetro:", err);
      }
    });
  }

  /* fetch cru, sem o loader global: são dezenas de chamadas por minuto e o
     overlay de carregamento piscando o tempo todo tornaria a tela inusável. */
  function buscarSilencioso(caminho, opcoes) {
    var s = sessao();
    if (!s) return Promise.reject(new Error("Sessão não encontrada."));
    var config = opcoes || {};
    var cabecalhos = Object.assign({ Authorization: "Bearer " + s.token }, config.headers || {});
    return fetch(API_BASE_URL + caminho, Object.assign({}, config, { headers: cabecalhos })).then(function (r) {
      if (!r.ok) {
        return r
          .json()
          .catch(function () {
            return {};
          })
          .then(function (corpo) {
            var erro = new Error(corpo.mensagem || "Não foi possível falar com o goniômetro.");
            erro.status = r.status;
            throw erro;
          });
      }
      return r.status === 204 ? null : r.json();
    });
  }

  /* Sessão vencida (401): para tudo e vai para o login, como as outras telas.
     Sem acesso a esta clínica (403): para tudo e não insiste. Antes, um 401
     virava um polling por segundo que nunca acabava. */
  function tratarRecusa(erro) {
    if (!erro || (erro.status !== 401 && erro.status !== 403)) return false;
    desconectar();
    if (erro.status === 401) {
      localStorage.removeItem("rehabit_usuario");
      window.location.href = typeof paginaLogin === "function" ? paginaLogin() : "../Login/login.html";
    }
    return true;
  }

  /* O EventSource não manda cabeçalho, e o token de sessão na URL ficava
     gravado em log de proxy e no histórico. Cada conexão pede antes um
     ingresso à API (com o token no cabeçalho, como as outras chamadas): ele
     só abre o stream desta clínica, uma vez, no próximo minuto. */
  function abrirSse(id) {
    var s = sessao();
    if (!s || typeof EventSource === "undefined") {
      iniciarPolling(id);
      return;
    }
    fecharSse();
    var minha = ++geracao;
    if (document.hidden) return; // a volta da aba reabre

    buscarSilencioso("/goniometro/stream/ingresso?idClinica=" + id, { method: "POST" })
      .then(function (resposta) {
        if (!ativo || document.hidden || minha !== geracao) return;
        fonte = new EventSource(
          API_BASE_URL + "/goniometro/stream?idClinica=" + id + "&ingresso=" + encodeURIComponent(resposta.ingresso)
        );
        ligarEventos(fonte, id);
      })
      .catch(function (erro) {
        if (!ativo || minha !== geracao) return;
        if (tratarRecusa(erro)) return;
        // API sem a rota do ingresso (a antiga, enquanto o Render não publica
        // a nova): não adianta insistir no SSE, vai direto para o polling.
        if (erro && (erro.status === 404 || erro.status === 405)) {
          iniciarPolling(id);
          return;
        }
        falhou(id);
      });
  }

  function ligarEventos(origem, id) {
    var abertaEm = 0;

    origem.addEventListener("open", function () {
      abertaEm = Date.now();
    });

    origem.addEventListener("estado", function (evento) {
      try {
        avisar(JSON.parse(evento.data));
      } catch (err) {
        console.error("Evento do goniômetro veio malformado:", err);
      }
    });

    origem.addEventListener("error", function () {
      if (!ativo || origem !== fonte) return;
      // A reconexão automática do EventSource repetiria a mesma URL, com o
      // ingresso já gasto. Fecha e abre de novo com um ingresso novo.
      fecharSse();
      if (abertaEm && Date.now() - abertaEm >= CONEXAO_ESTAVEL_MS) falhasSeguidas = 0;
      falhou(id);
    });
  }

  function falhou(id) {
    falhasSeguidas++;
    if (falhasSeguidas >= FALHAS_ATE_DESISTIR_DO_SSE) {
      iniciarPolling(id);
      return;
    }
    var espera = Math.min(ESPERA_MAXIMA_MS, ESPERA_RECONEXAO_MS * Math.pow(2, falhasSeguidas - 1));
    if (timerReconexao) clearTimeout(timerReconexao);
    timerReconexao = setTimeout(function () {
      timerReconexao = null;
      if (ativo) abrirSse(id);
    }, espera);
  }

  function fecharSse() {
    if (fonte) {
      fonte.close();
      fonte = null;
    }
  }

  function iniciarPolling(id) {
    if (timerPolling) return;
    usandoPolling = true;
    var buscar = function () {
      buscarSilencioso("/goniometro/estado?idClinica=" + id)
        .then(avisar)
        .catch(function (erro) {
          // Sessão vencida ou sem acesso: para (veja tratarRecusa). Servidor
          // fora do ar: a tela mostra "desconectado" pelo próprio estado
          // anterior; insistir em silêncio é o comportamento certo.
          tratarRecusa(erro);
        });
    };
    buscar();
    timerPolling = setInterval(buscar, INTERVALO_POLLING_MS);
  }

  function pararPolling() {
    if (timerPolling) {
      clearInterval(timerPolling);
      timerPolling = null;
    }
    usandoPolling = false;
  }

  /* --------------------------------------------------------------------
     Goniômetro simulado da demonstração pública.

     A senha da Clínica Movimento está na página inicial, então ela não
     pareia aparelho nenhum: com um goniômetro de verdade ali, qualquer
     visitante mandaria Zerar ou Reiniciar para ele. Para a tela não ficar
     parada à espera de um aparelho que nunca vem, o aparelho é simulado aqui
     mesmo, no navegador, no formato de estado que a API manda e no ritmo que
     ela pede ao aparelho real (2,5 leituras por segundo com a tela aberta,
     10 durante a captura). Os botões agem sobre a simulação, sem chamar a
     API — que recusaria: a conta é só de leitura.
     -------------------------------------------------------------------- */
  var Simulado = (function () {
    var PASSO_NORMAL_MS = 400;
    var PASSO_CAPTURA_MS = 100;
    var JANELA_MS = 60000;
    var CICLO_S = 7; // um movimento: descansa, sobe, segura no alto e desce
    var REPOUSO = 4; // o braço pendurado não fica em 0 cravado

    var relogio = null;
    var inicio = 0;
    var historico = [];
    var tara = 0;
    var brutoAtual = REPOUSO;
    var ultimoContato = null;
    var captura = null;
    var desligadoAte = 0;
    var calibrandoAte = 0;
    var bateria = 87;
    var ultimoAviso = 0;

    function suave(x) {
      return x * x * (3 - 2 * x);
    }

    function arredondar(v) {
      return Math.round(v * 10) / 10;
    }

    // Abdução do ombro: do braço pendurado a ~140°, com o alvo variando um
    // pouco a cada repetição, como num paciente de verdade.
    function anguloBruto(segundos) {
      var ciclo = Math.floor(segundos / CICLO_S);
      var fase = (segundos % CICLO_S) / CICLO_S;
      var alvo = 136 + 12 * Math.sin(ciclo * 1.7);
      var a = 0;
      if (fase >= 0.18 && fase < 0.5) a = suave((fase - 0.18) / 0.32);
      else if (fase >= 0.5 && fase < 0.62) a = 1;
      else if (fase >= 0.62 && fase < 0.92) a = 1 - suave((fase - 0.62) / 0.3);
      var ruido = Math.sin(segundos * 23) * 0.5 + Math.sin(segundos * 7.3) * 0.35;
      return REPOUSO + a * (alvo - REPOUSO) + ruido;
    }

    function amostrar(t) {
      if (t < desligadoAte) return; // reiniciando: nenhum pacote
      // Calibrando, o aparelho fica parado (é o que o firmware pede) e o
      // movimento recomeça do braço pendurado quando a calibração acaba.
      brutoAtual = t < calibrandoAte ? REPOUSO : anguloBruto((t - inicio) / 1000);
      var angulo = arredondar(Math.min(180, Math.abs(brutoAtual - tara)));
      ultimoContato = t;
      historico.push({ t: t, angulo: angulo });
      if (captura && captura.ativa) {
        captura.amostras++;
        captura.soma += angulo;
        captura.minimo = captura.minimo == null ? angulo : Math.min(captura.minimo, angulo);
        captura.maximo = captura.maximo == null ? angulo : Math.max(captura.maximo, angulo);
      }
      bateria = Math.max(20, bateria - 0.0005);
    }

    function retratoDaCaptura(agora) {
      if (!captura) return null;
      return {
        ativa: captura.ativa,
        iniciadaEm: captura.iniciadaEm,
        duracaoSegundos: Math.floor(((captura.ativa ? agora : captura.fim) - captura.iniciadaEm) / 1000),
        amostras: captura.amostras,
        minimo: captura.minimo,
        maximo: captura.maximo,
        amplitude: captura.minimo == null ? null : arredondar(captura.maximo - captura.minimo),
        media: captura.amostras ? arredondar(captura.soma / captura.amostras) : null,
      };
    }

    function retrato() {
      var agora = Date.now();
      var ligado = agora >= desligadoAte;
      var ultimo = historico.length ? historico[historico.length - 1] : null;
      return {
        simulado: true,
        conectado: ligado,
        angulo: ligado && ultimo ? ultimo.angulo : null,
        anguloBruto: ligado ? arredondar(brutoAtual) : null,
        bateria: Math.round(bateria),
        rssi: -51 - Math.round(3 + 3 * Math.sin(agora / 9000)),
        numeroSerie: "SIMULADO",
        firmware: "2.4",
        ip: "192.168.0.42",
        calibrado: agora >= calibrandoAte,
        ultimoContato: ultimoContato,
        segundosDesdeContato: ultimoContato == null ? null : Math.max(0, Math.round((agora - ultimoContato) / 1000)),
        captura: retratoDaCaptura(agora),
        historico: historico.slice(),
      };
    }

    // Tique de 100 ms: amostra no ritmo do momento e completa o que faltou
    // (aba escondida, o navegador segura os timers), para o gráfico não ter
    // buraco. Avisa a tela quando há leitura nova, ou a cada segundo.
    function tique() {
      var agora = Date.now();
      var passo = captura && captura.ativa ? PASSO_CAPTURA_MS : PASSO_NORMAL_MS;
      var t = historico.length ? historico[historico.length - 1].t + passo : agora;
      t = Math.max(t, agora - JANELA_MS, desligadoAte);
      var novas = false;
      for (; t <= agora; t += passo) {
        amostrar(t);
        novas = true;
      }
      while (historico.length && historico[0].t < agora - JANELA_MS) historico.shift();
      if (novas || agora - ultimoAviso >= 1000) {
        ultimoAviso = agora;
        avisar(retrato());
      }
    }

    function ligar() {
      if (relogio) return;
      if (!inicio) {
        // Começa no meio de uma sessão, com 20 s de leituras no gráfico.
        inicio = Date.now() - 20000 - 2500;
        for (var t = Date.now() - 20000; t <= Date.now(); t += PASSO_NORMAL_MS) amostrar(t);
      }
      tique();
      relogio = setInterval(tique, 100);
    }

    function desligar() {
      if (relogio) clearInterval(relogio);
      relogio = null;
    }

    function exigirLigado() {
      if (Date.now() < desligadoAte) {
        throw new Error("O goniômetro não está conectado. Ligue o aparelho e tente de novo.");
      }
    }

    function responder(fazer) {
      return new Promise(function (resolver) {
        fazer();
        var estado = retrato();
        avisar(estado);
        resolver(estado);
      });
    }

    return {
      ligar: ligar,
      desligar: desligar,
      atualizar: function () {
        return responder(function () {});
      },
      comando: function (nome) {
        return responder(function () {
          exigirLigado();
          if (nome === "TARAR") {
            tara = brutoAtual;
          } else if (nome === "REINICIAR") {
            // Como o aparelho: some por uns segundos, calibra parado e volta
            // com a tara guardada. Uma captura aberta morre junto.
            var agora = Date.now();
            captura = null;
            desligadoAte = agora + 4000;
            calibrandoAte = desligadoAte + 2500;
            inicio = calibrandoAte;
          }
          // IDENTIFICAR só pisca a luz, e quem desenha a luz é a tela.
        });
      },
      captura: function (acao) {
        return responder(function () {
          var agora = Date.now();
          if (acao === "iniciar") {
            exigirLigado();
            captura = { ativa: true, iniciadaEm: agora, fim: null, amostras: 0, soma: 0, minimo: null, maximo: null };
          } else if (captura && captura.ativa) {
            captura.ativa = false;
            captura.fim = agora;
          }
        });
      },
    };
  })();

  var modoSimulado = false;

  function conectar(aoAtualizar) {
    if (typeof aoAtualizar === "function") {
      ouvintes.push(aoAtualizar);
      if (ultimoEstado) aoAtualizar(ultimoEstado);
    }
    if (ativo) return modoSimulado ? Promise.resolve(null) : idClinica();
    ativo = true;
    if (typeof ehContaDeDemonstracao === "function" && ehContaDeDemonstracao()) {
      modoSimulado = true;
      Simulado.ligar();
      return Promise.resolve(null);
    }
    return idClinica().then(function (id) {
      abrirSse(id);
      return id;
    });
  }

  function desconectar() {
    ativo = false;
    ouvintes = [];
    geracao++;
    Simulado.desligar();
    fecharSse();
    pararPolling();
    if (timerReconexao) {
      clearTimeout(timerReconexao);
      timerReconexao = null;
    }
  }

  /* Releitura sob demanda do estado, para o botão "Atualizar agora": o canal
     ao vivo já traz tudo sozinho, mas quando ele cai (ou o profissional
     simplesmente quer confirmar) uma busca direta responde na hora. */
  function atualizar() {
    if (modoSimulado) return Simulado.atualizar();
    return idClinica()
      .then(function (id) {
        return buscarSilencioso("/goniometro/estado?idClinica=" + id);
      })
      .then(function (estado) {
        if (estado) avisar(estado);
        return estado;
      });
  }

  function comando(nome) {
    if (modoSimulado) return Simulado.comando(nome);
    return idClinica().then(function (id) {
      return buscarSilencioso("/goniometro/comando", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ idClinica: id, comando: nome }),
      }).then(function (estado) {
        if (estado) avisar(estado);
        return estado;
      });
    });
  }

  function captura(acao) {
    if (modoSimulado) return Simulado.captura(acao);
    return idClinica().then(function (id) {
      return buscarSilencioso("/goniometro/captura/" + acao, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ idClinica: id }),
      }).then(function (estado) {
        if (estado) avisar(estado);
        return estado;
      });
    });
  }

  // Aba escondida não precisa de leitura ao vivo: fecha o canal e reabre
  // quando o profissional volta, economizando bateria do aparelho (o servidor
  // manda o ESP32 desacelerar quando ninguém está ouvindo).
  document.addEventListener("visibilitychange", function () {
    // A simulação completa sozinha o tempo em que a aba ficou escondida.
    if (!ativo || modoSimulado) return;
    if (document.hidden) {
      geracao++;
      fecharSse();
      pararPolling();
      if (timerReconexao) {
        clearTimeout(timerReconexao);
        timerReconexao = null;
      }
    } else if (idClinicaCache != null) {
      if (usandoPolling) iniciarPolling(idClinicaCache);
      else abrirSse(idClinicaCache);
    }
  });

  window.addEventListener("pagehide", desconectar);

  window.RehabitGoniometro = {
    conectar: conectar,
    desconectar: desconectar,
    atualizar: atualizar,
    comando: comando,
    iniciarCaptura: function () {
      return captura("iniciar");
    },
    pararCaptura: function () {
      return captura("parar");
    },
    idClinica: idClinica,
    estadoAtual: function () {
      return ultimoEstado;
    },
    usandoPolling: function () {
      return usandoPolling;
    },
    // Conta de demonstração pública: o aparelho é o simulado acima.
    simulado: function () {
      return modoSimulado;
    },
  };
})();
