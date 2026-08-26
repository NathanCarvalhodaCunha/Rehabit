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
  var ESPERA_RECONEXAO_MS = 3000;
  // Duas quedas seguidas do SSE já bastam para desconfiar do transporte: a
  // terceira tentativa não vale a pena quando o polling resolve na hora.
  var FALHAS_ATE_DESISTIR_DO_SSE = 2;

  var idClinicaCache = null;
  var fonte = null;
  var timerPolling = null;
  var timerReconexao = null;
  var ouvintes = [];
  var falhasSeguidas = 0;
  var usandoPolling = false;
  var ativo = false;
  var ultimoEstado = null;

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
            throw new Error(corpo.mensagem || "Não foi possível falar com o goniômetro.");
          });
      }
      return r.status === 204 ? null : r.json();
    });
  }

  function abrirSse(id) {
    var s = sessao();
    if (!s || typeof EventSource === "undefined") {
      iniciarPolling(id);
      return;
    }

    fonte = new EventSource(
      API_BASE_URL + "/goniometro/stream?idClinica=" + id + "&token=" + encodeURIComponent(s.token)
    );

    fonte.addEventListener("estado", function (evento) {
      falhasSeguidas = 0;
      try {
        avisar(JSON.parse(evento.data));
      } catch (err) {
        console.error("Evento do goniômetro veio malformado:", err);
      }
    });

    fonte.addEventListener("open", function () {
      falhasSeguidas = 0;
    });

    fonte.addEventListener("error", function () {
      if (!ativo) return;
      // O EventSource reconecta sozinho, mas se ele estiver batendo na trave
      // (readyState CLOSED) é sinal de que esse transporte não vai vingar
      // aqui — melhor trocar por polling do que ficar tentando em silêncio.
      falhasSeguidas++;
      if (fonte && fonte.readyState === EventSource.CLOSED) {
        fecharSse();
        if (falhasSeguidas >= FALHAS_ATE_DESISTIR_DO_SSE) {
          iniciarPolling(id);
        } else {
          timerReconexao = setTimeout(function () {
            if (ativo) abrirSse(id);
          }, ESPERA_RECONEXAO_MS);
        }
      }
    });
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
        .catch(function () {
          // Servidor fora do ar: a tela mostra "desconectado" pelo próprio
          // estado anterior; insistir em silêncio é o comportamento certo.
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
    fecharSse();
    pararPolling();
    if (timerReconexao) {
      clearTimeout(timerReconexao);
      timerReconexao = null;
    }
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
      fecharSse();
      pararPolling();
    } else if (idClinicaCache != null) {
      if (usandoPolling) iniciarPolling(idClinicaCache);
      else abrirSse(idClinicaCache);
    }
  });

  window.addEventListener("pagehide", desconectar);

  window.RehabitGoniometro = {
    conectar: conectar,
    desconectar: desconectar,
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
