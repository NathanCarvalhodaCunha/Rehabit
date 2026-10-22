/* Rehabit — controle do loader
   API global:
     RehabitLoader.show("Carregando")  -> exibe (cria o overlay se não existir)
     RehabitLoader.hide()              -> esconde
     RehabitLoader.during(promise)     -> exibe enquanto a promise roda
   Marcação opcional já no HTML: <div class="rh-loader" data-rh-loader> ... </div>
   Atributos no <body> (opcional):
     data-rh-loader-dark="true"        -> força tema escuro
     data-rh-loader-icon="assets/..."  -> caminho do ícone
   Se a página começar a ir para outra enquanto o loader está na tela (login
   que deu certo, cadastro salvo que redireciona), ele fica de pé até a
   próxima tela abrir, mesmo que o hide() seja chamado.
*/
(function () {
  "use strict";

  var OVERLAY_SELECTOR = "[data-rh-loader]";

  function isDark() {
    if (document.body.dataset.rhLoaderDark === "true") return true;
    // heurística: telas "-escuro.html" ou body com classe dark
    return (
      document.body.classList.contains("dark") ||
      /-escuro\.html/i.test(location.pathname)
    );
  }

  function iconFile() {
    return isDark() ? "rehabit-icon-dark.png" : "rehabit-icon.png";
  }

  function iconPath() {
    if (document.body.dataset.rhLoaderIcon) {
      return document.body.dataset.rhLoaderIcon;
    }
    return "assets/" + iconFile();
  }

  // O texto entra como texto, nunca como HTML: quem chama show() pode um dia
  // passar algo vindo da API.
  function writeText(label, text) {
    var dots = document.createElement("span");
    dots.className = "rh-dots";
    label.textContent = text;
    label.appendChild(dots);
  }

  // Nasce escondido (display: none) e não no primeiro show(): o <img> do
  // bíceps começa a baixar já, e quando o loader aparece o anel não fica
  // vazio esperando a imagem chegar.
  function build() {
    var overlay = document.createElement("div");
    overlay.className = "rh-loader is-hidden" + (isDark() ? " rh-loader--dark" : "");
    overlay.hidden = true;
    overlay.setAttribute("data-rh-loader", "");
    overlay.setAttribute("role", "status");
    overlay.setAttribute("aria-live", "polite");
    overlay.innerHTML =
      '<div class="rh-loader__box">' +
      '<div class="rh-loader__ring">' +
      '<img class="rh-loader__icon" alt="">' +
      "</div>" +
      '<p class="rh-loader__text"></p>' +
      "</div>";
    overlay.querySelector(".rh-loader__icon").src = iconPath();
    writeText(overlay.querySelector(".rh-loader__text"), "Carregando");
    document.body.appendChild(overlay);
    return overlay;
  }

  function get() {
    return document.querySelector(OVERLAY_SELECTOR) || build();
  }

  // --- Aviso de espera longa -----------------------------------------------
  // A API roda no plano gratuito do Render, que derruba a instância depois de
  // ~15 min sem tráfego. A chamada que a acorda passa de dois minutos (146 s
  // medidos, contra 0,48 s com ela quente) e quem quase sempre cai nisso é o
  // aparelho novo: nos já usados a sessão está no localStorage e o login, a
  // única chamada que a tela de entrada faz, nunca acontece. Um loader parado
  // todo esse tempo sem dizer nada parece travado, e a pessoa fecha a aba
  // justo quando faltavam segundos.
  //
  // O aviso pode ser automático porque toda espera longa deste app é espera
  // de API: passou do tempo em que uma resposta normal já teria voltado, é
  // religamento de instância e não outra coisa.
  var AVISO_MS = 5000;
  var AVISO_LONGO_MS = 25000;
  var AVISO = "O servidor hiberna quando fica sem uso, e está acordando agora.";
  var AVISO_LONGO =
    "Isso acontece só no primeiro acesso depois de um tempo parado e pode " +
    "levar até 2 minutos. Pode deixar a página aberta.";

  var temporizadores = [];

  function escreverAviso(texto) {
    var overlay = document.querySelector(OVERLAY_SELECTOR);
    if (!overlay) return;
    var box = overlay.querySelector(".rh-loader__box");
    if (!box) return;
    var aviso = overlay.querySelector(".rh-loader__hint");
    if (!aviso) {
      aviso = document.createElement("p");
      aviso.className = "rh-loader__hint";
      box.appendChild(aviso);
    } else {
      // Troca do primeiro aviso pelo longo: refaz a entrada suave, em vez de
      // o texto mudar de uma vez.
      aviso.style.animation = "none";
      void aviso.offsetWidth;
      aviso.style.animation = "";
    }
    aviso.textContent = texto;
  }

  function agendarAvisos() {
    temporizadores.push(
      setTimeout(function () {
        escreverAviso(AVISO);
      }, AVISO_MS)
    );
    temporizadores.push(
      setTimeout(function () {
        escreverAviso(AVISO_LONGO);
      }, AVISO_LONGO_MS)
    );
  }

  function limparAvisos() {
    temporizadores.forEach(clearTimeout);
    temporizadores = [];
    var aviso = document.querySelector(".rh-loader__hint");
    if (aviso && aviso.parentNode) aviso.parentNode.removeChild(aviso);
  }

  // --- Entrar e sair de cena -----------------------------------------------
  // Mesmo tempo da transição de saída em loader.css.
  var FADE_OUT_MS = 350;
  // Espera entre o último hide() e o começo do fade. Junta chamadas em
  // sequência (uma termina e a seguinte começa logo depois) num loader só,
  // sem piscar, e dá tempo de a navegação disparada logo depois do hide()
  // avisar que a página está saindo: no Chrome o beforeunload dispara dentro
  // do próprio location.href = ..., mas depois do hide() que o apiPost chama
  // antes de quem redireciona, e outros navegadores podem dispará-lo só um
  // instante depois.
  var CARENCIA_MS = 150;
  // Se a navegação não acontecer (virou download, foi cancelada), o loader
  // não pode ficar preso na tela.
  var SAINDO_MAX_MS = 10000;
  // Marca, na sessionStorage, que a tela anterior saiu com o loader de pé.
  var CHAVE_CONTINUA = "rh-loader-continua";

  // Contador de chamadas simultâneas: com várias requisições em paralelo
  // (ex.: Promise.all de dois apiGet), o loader só some quando a última
  // delas terminar, em vez de sumir assim que a primeira resolve.
  var pendentes = 0;
  var saindo = false;
  var timerSaida = null;
  var timerApagar = null;
  var timerSaindo = null;

  function marcarSaida() {
    saindo = true;
    try {
      sessionStorage.setItem(CHAVE_CONTINUA, String(Date.now()));
    } catch (e) {
      // sem sessionStorage (modo privado antigo): a próxima tela só não emenda
    }
    clearTimeout(timerSaindo);
    timerSaindo = setTimeout(function () {
      desfazerSaida();
      if (pendentes === 0) esconder();
    }, SAINDO_MAX_MS);
  }

  // A página não saiu, afinal (chegou outra chamada, passou o tempo, voltou
  // pelo cache): nem este loader nem o da próxima tela devem contar com isso.
  function desfazerSaida() {
    saindo = false;
    clearTimeout(timerSaindo);
    try {
      sessionStorage.removeItem(CHAVE_CONTINUA);
    } catch (e) {
      // idem
    }
  }

  // Durante o carregamento da página o overlay aparece de uma vez, para já
  // estar na primeira pintura: a tela anterior deixou o loader dela de pé até
  // esta abrir, e um fade aqui mostraria a tela vazia entre os dois. Depois
  // disso (resposta a um clique), entra com fade.
  function exibir(overlay) {
    clearTimeout(timerApagar);
    if (overlay.hidden) {
      overlay.hidden = false;
      if (document.readyState !== "loading") void overlay.offsetWidth;
    }
    overlay.classList.remove("is-hidden");
    // Só enquanto o loader está na tela: um beforeunload fora disso não diz
    // nada ao loader, e um ouvinte permanente pode fazer o navegador deixar
    // de guardar a página no cache do botão "voltar".
    window.addEventListener("beforeunload", marcarSaida);
  }

  function esconder(semFade) {
    window.removeEventListener("beforeunload", marcarSaida);
    clearTimeout(timerSaindo);
    limparAvisos();
    var overlay = document.querySelector(OVERLAY_SELECTOR);
    if (!overlay) return;
    overlay.classList.add("is-hidden");
    clearTimeout(timerApagar);
    if (semFade) {
      overlay.hidden = true;
      return;
    }
    // Depois do fade, display: none — as animações infinitas param de rodar
    // escondidas e recomeçam do início na próxima vez.
    timerApagar = setTimeout(function () {
      overlay.hidden = true;
    }, FADE_OUT_MS + 50);
  }

  function sairSePuder() {
    timerSaida = null;
    if (pendentes > 0 || saindo) return;
    esconder();
  }

  var RehabitLoader = {
    show: function (text) {
      // show() sem texto numa espera nova é "Carregando", e não o texto da
      // espera anterior ("Gerando relatório"...); numa chamada que entra com
      // outra já em andamento, mantém o que está escrito.
      var novaEspera = pendentes === 0;
      pendentes++;
      if (saindo) desfazerSaida();
      clearTimeout(timerSaida);
      timerSaida = null;
      var overlay = get();
      var label = overlay.querySelector(".rh-loader__text");
      if (label && (text || novaEspera)) writeText(label, text || "Carregando");
      exibir(overlay);
      // Só o primeiro show agenda o aviso: com requisições em paralelo (ou uma
      // logo atrás da outra), o relógio que interessa é o da espera inteira,
      // não o da última chamada a entrar na fila.
      if (!temporizadores.length) agendarAvisos();
      return overlay;
    },
    hide: function () {
      pendentes = Math.max(0, pendentes - 1);
      if (pendentes > 0 || timerSaida) return;
      timerSaida = setTimeout(sairSePuder, CARENCIA_MS);
    },
    during: function (promise, text) {
      this.show(text);
      var self = this;
      return Promise.resolve(promise).finally(function () {
        self.hide();
      });
    },
  };

  window.RehabitLoader = RehabitLoader;

  // A tela anterior saiu com o loader de pé ("Entrando" do login, "Salvando"
  // de um cadastro que redireciona): ele já aparece aqui, antes da primeira
  // pintura, e fica até a página carregar ou as chamadas dela terminarem. Sem
  // isso, quando os scripts do fim do <body> demoram a chegar (rede lenta,
  // primeira visita), o navegador pinta a tela vazia entre os dois loaders.
  function continuarDaTelaAnterior() {
    var marca;
    try {
      marca = Number(sessionStorage.getItem(CHAVE_CONTINUA));
      sessionStorage.removeItem(CHAVE_CONTINUA);
    } catch (e) {
      return;
    }
    if (!marca || Date.now() - marca > SAINDO_MAX_MS) return;
    RehabitLoader.show();
    window.addEventListener("load", function () {
      RehabitLoader.hide();
    });
  }

  function iniciar() {
    get();
    continuarDaTelaAnterior();
  }

  if (document.body) iniciar();
  else document.addEventListener("DOMContentLoaded", iniciar);

  // Voltou pelo botão "voltar" e o navegador restaurou a página do cache como
  // ela estava ao sair — com o loader de pé. Recomeça do zero.
  window.addEventListener("pageshow", function (e) {
    if (!e.persisted) return;
    pendentes = 0;
    desfazerSaida();
    clearTimeout(timerSaida);
    timerSaida = null;
    esconder(true);
  });

  window.addEventListener("load", function () {
    // Se o overlay já existe no HTML, esconde quando a página carregar — se
    // nenhuma chamada estiver em andamento. O que o show() abriu durante o
    // carregamento some quando a última chamada terminar; antes, sumia aqui
    // com elas ainda pendentes, e numa API acordando a tela ficava vazia, sem
    // loader e sem o aviso, por até dois minutos.
    var overlay = document.querySelector(OVERLAY_SELECTOR);
    if (overlay && overlay.dataset.rhLoaderAuto !== "false") {
      setTimeout(function () {
        if (pendentes === 0 && !saindo && !overlay.hidden) esconder();
      }, 600);
    }

    // Quem entra pelo login vai para Software/: o ícone de lá já fica no
    // cache (a Vercel guarda os assets por uma semana), e o "Carregando" da
    // primeira tela do sistema não aparece com o anel vazio.
    if (/\/Login\//.test(location.pathname)) {
      new Image().src = "../Software/assets/" + iconFile();
    }
  });
})();
