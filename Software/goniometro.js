/* Rehabit — cliente do goniômetro em tempo real.

   Um só lugar que sabe conversar com a API do aparelho, usado tanto pela tela
   Dispositivo quanto pelo formulário de sessão.

   API global:
     RehabitGoniometro.conectar(aoAtualizar)  -> abre o canal e chama aoAtualizar(estado) a cada evento
     RehabitGoniometro.desconectar()
     RehabitGoniometro.comando("TARAR")
     RehabitGoniometro.iniciarCaptura() / pararCaptura()
     RehabitGoniometro.idClinica()            -> Promise com o id da clínica da sessão

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

  function conectar(aoAtualizar) {
    if (typeof aoAtualizar === "function") {
      ouvintes.push(aoAtualizar);
      if (ultimoEstado) aoAtualizar(ultimoEstado);
    }
    if (ativo) return idClinica();
    ativo = true;
    return idClinica().then(function (id) {
      abrirSse(id);
      return id;
    });
  }

  function desconectar() {
    ativo = false;
    ouvintes = [];
    geracao++;
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
    if (!ativo) return;
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
  };
})();
