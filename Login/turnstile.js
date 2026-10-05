/* Rehabit — verificação anti-robô (Cloudflare Turnstile)
   API global:
     RehabitTurnstile.cabecalho()  -> Promise com { "X-Turnstile-Token": token },
                                      ou {} quando não há verificação nesta página

   Uso, antes de chamar o login, o envio do código de cadastro ou o e-mail de
   recuperação de senha (as rotas em que a API confere o token):

     const antiRobo = await RehabitTurnstile.cabecalho();
     fetch(url, { headers: { 'Content-Type': 'application/json', ...antiRobo }, ... });

   Caixinha visível: a página põe um <div data-rh-captcha> acima do botão do
   formulário, e ali aparece o "Confirme que é humano" da Cloudflare. O botão
   fica apagado até a caixinha ficar verde. Quem decide se precisa de clique
   é a Cloudflare (modo Managed): quase sempre ela se marca sozinha.

   Cartão flutuante: quando o token é pedido com a caixinha fora de alcance
   ("Enviar de novo" com o formulário escondido ou coberto pelo pop-up do
   cadastro), ou numa página sem caixinha, entra um widget invisível que só
   aparece, num cartão no pé da tela, se a Cloudflare pedir o clique.

   Peça o token antes de mostrar o loader: com ele na tela, a espera pelo
   clique viraria o aviso de "servidor acordando".

   Cada token vale uma vez só. Depois de entregar um, o widget já pede o
   próximo — é o que deixa o "Enviar de novo" funcionar.

   Quando a verificação não roda (script da Cloudflare bloqueado, rede caída,
   navegador sem suporte, chave recusada), o botão é liberado e a chamada
   segue sem token: quem decide é a API, que com a TURNSTILE_SECRET_KEY
   definida recusa com uma mensagem própria. Um botão morto, sem explicação,
   seria pior.
*/
(function () {
  "use strict";

  // Site key do widget (Cloudflare > Turnstile). É pública: só funciona nos
  // domínios cadastrados no painel. A chave secreta fica só no Render.
  // Vazia = sem verificação no site publicado.
  var CHAVE_DO_SITE = "0x4AAAAAAFOvDhcmrqyK7llH";

  // Chave de teste da Cloudflare que aprova sempre, sem pedir clique. Em
  // localhost a API roda sem TURNSTILE_SECRET_KEY e ignora o token, mas o
  // caminho no navegador é o mesmo do site publicado.
  var CHAVE_DE_TESTE = "1x00000000000000000000AA";

  var CABECALHO = "X-Turnstile-Token";

  // Quanto esperar um token que não chega (Cloudflare lenta, erro repetido)
  // antes de seguir sem ele. Enquanto um desafio pede o clique não conta: aí
  // quem decide o tempo é a pessoa (e o tempo limite do próprio desafio).
  var ESPERA_MAXIMA_MS = 30000;

  // Mesma regra do API_BASE_URL em script.js: aberto como arquivo não há
  // endereço, e o Turnstile não roda; em localhost a API é a local.
  function chaveDestaPagina() {
    if (location.protocol === "file:") return null;
    if (location.hostname === "localhost") return CHAVE_DE_TESTE;
    return CHAVE_DO_SITE || null;
  }

  var chave = chaveDestaPagina();
  var desligado = !chave;
  var pedidos = []; // quem chamou cabecalho() e espera o próximo token
  var lugarDaCaixa = document.querySelector("[data-rh-captcha]");
  var caixa = null; // widget visível, no formulário
  var flutuante = null; // widget invisível, no cartão do pé da tela
  var cartao = null;

  /* ---- Token ---- */

  function consumir(widget) {
    var token = widget.token;
    widget.token = null;
    window.turnstile.reset(widget.id);
    atualizarBotao();
    return token;
  }

  function atender(pedido, token) {
    clearTimeout(pedido.relogio);
    pedidos.splice(pedidos.indexOf(pedido), 1);
    pedido.resolver(token);
  }

  function seguirSemToken() {
    pedidos.slice().forEach(function (pedido) {
      atender(pedido, null);
    });
  }

  function aguardarAteDesistir(pedido) {
    clearTimeout(pedido.relogio);
    pedido.relogio = setTimeout(function () {
      console.warn("[Turnstile] o token não chegou; seguindo sem ele.");
      atender(pedido, null);
    }, ESPERA_MAXIMA_MS);
  }

  function alguemPedindoClique() {
    return !!((caixa && caixa.interativo) || (flutuante && flutuante.interativo));
  }

  // Na tela e sem nada por cima: é onde a pessoa consegue responder.
  function caixaAoAlcance() {
    if (!caixa) return false;
    var r = lugarDaCaixa.getBoundingClientRect();
    if (!r.width || !r.height) return false;
    var x = Math.min(Math.max(r.left + r.width / 2, 0), innerWidth - 1);
    var y = Math.min(Math.max(r.top + r.height / 2, 0), innerHeight - 1);
    var noPonto = document.elementFromPoint(x, y);
    return !!noPonto && lugarDaCaixa.contains(noPonto);
  }

  function token() {
    if (desligado) return Promise.resolve(null);
    if (caixa && caixa.token) return Promise.resolve(consumir(caixa));
    if (flutuante && flutuante.token) return Promise.resolve(consumir(flutuante));
    if (!caixaAoAlcance()) garantirFlutuante();
    return new Promise(function (resolver) {
      var pedido = { resolver: resolver, relogio: null };
      pedidos.push(pedido);
      if (!alguemPedindoClique()) aguardarAteDesistir(pedido);
    });
  }

  function chegouToken(widget) {
    if (pedidos.length) atender(pedidos[0], consumir(widget));
  }

  /* ---- Botão do formulário ---- */

  // Apagado enquanto a caixinha não fica verde (ou nem carregou ainda). Com
  // classe e aria-disabled, e não com disabled, porque os formulários já
  // usam o disabled para o "Entrando..." e o devolveriam ao fim da chamada.
  function atualizarBotao() {
    if (!lugarDaCaixa) return;
    var form = lugarDaCaixa.closest("form");
    var botao = form && form.querySelector('[type="submit"]');
    if (!botao) return;
    var esperando = !desligado && (!caixa || (!caixa.token && !caixa.erro));
    botao.classList.toggle("rh-captcha-pendente", esperando);
    if (esperando) botao.setAttribute("aria-disabled", "true");
    else botao.removeAttribute("aria-disabled");
  }

  /* ---- Estilo ---- */

  function injetarEstilo() {
    var estilo = document.createElement("style");
    estilo.textContent =
      ".rh-captcha{width:100%;min-height:65px;margin-top:24px}" +
      ".rh-captcha+.btn-primary{margin-top:16px}" +
      // filter, e não opacity: a entrada da página (animations.js) anima o
      // opacity do botão até o valor que ele tem ao carregar — que seria o
      // apagado — e o deixa gravado no style depois que a classe sai.
      ".rh-captcha-pendente{filter:opacity(.55);pointer-events:none}" +
      ".rh-turnstile{position:fixed;left:50%;bottom:16px;z-index:10000;" +
      "max-width:calc(100vw - 16px);box-sizing:border-box;padding:12px 12px 8px;" +
      "border-radius:12px;background:#fff;color:#000;" +
      "box-shadow:0 10px 30px rgba(6,13,34,.18),0 0 0 1px rgba(6,13,34,.06);" +
      "font:500 14px/1.4 system-ui,-apple-system,'Segoe UI',Roboto,sans-serif;" +
      "opacity:0;pointer-events:none;transform:translate(-50%,calc(100% + 24px));" +
      "transition:opacity .2s ease,transform .2s ease}" +
      ".rh-turnstile.is-visivel{opacity:1;pointer-events:auto;transform:translate(-50%,0)}" +
      ".rh-turnstile p{margin:0 4px 8px}" +
      "body.dark .rh-turnstile{background:#1A2247;color:#fff;" +
      "box-shadow:0 10px 30px rgba(0,0,0,.45),0 0 0 1px #24305C}" +
      "@media (prefers-reduced-motion:reduce){.rh-turnstile{transition:none}}";
    document.head.appendChild(estilo);
  }

  /* ---- Cartão flutuante ---- */

  // Fica no DOM desde o início, só transparente e fora da tela: escondido
  // com display:none o widget não consegue rodar o desafio.
  function criarCartao() {
    cartao = document.createElement("div");
    cartao.className = "rh-turnstile";
    cartao.setAttribute("role", "region");
    cartao.setAttribute("aria-label", "Verificação anti-robô");
    cartao.innerHTML = '<p aria-live="polite"></p><div></div>';
    document.body.appendChild(cartao);
    return cartao.lastChild;
  }

  function mostrarCartao() {
    cartao.firstChild.textContent = "Confirme que você não é um robô para continuar.";
    cartao.classList.add("is-visivel");
  }

  function esconderCartao() {
    cartao.classList.remove("is-visivel");
    cartao.firstChild.textContent = "";
  }

  // Só nasce quando precisa: numa página com caixinha, é a reserva para o
  // token pedido com ela fora de alcance.
  function garantirFlutuante() {
    if (flutuante || !window.turnstile) return;
    flutuante = criarWidget(criarCartao(), {
      appearance: "interaction-only",
      aoPedirClique: mostrarCartao,
      aoSairDoClique: esconderCartao,
    });
  }

  /* ---- Widget ---- */

  function criarWidget(alvo, opcoes) {
    var widget = { id: null, token: null, erro: false, interativo: false };

    function mudouDesafio(interativo) {
      widget.interativo = interativo;
      if (interativo) {
        // A pessoa está respondendo: ninguém desiste por tempo enquanto isso.
        pedidos.forEach(function (pedido) {
          clearTimeout(pedido.relogio);
        });
      } else if (!alguemPedindoClique()) {
        pedidos.forEach(aguardarAteDesistir);
      }
    }

    widget.id = window.turnstile.render(alvo, {
      sitekey: chave,
      theme: document.body.classList.contains("dark") ? "dark" : "light",
      language: "pt-br",
      appearance: opcoes.appearance,
      size: opcoes.size || "normal",
      callback: function (novo) {
        widget.token = novo;
        widget.erro = false;
        atualizarBotao();
        chegouToken(widget);
      },
      "expired-callback": function () {
        widget.token = null; // a Cloudflare renova sozinha e chama o callback de novo
        atualizarBotao();
      },
      // Sem este callback o erro vira exceção na página. O true diz que já
      // foi registrado aqui; a Cloudflare tenta de novo sozinha.
      //
      // 600xxx é o veredito "parece robô": o botão continua apagado. Os
      // outros (chave, domínio, rede) são falha de configuração ou de
      // conexão, e aí o botão é liberado e quem responde é a API.
      "error-callback": function (codigo) {
        widget.token = null;
        widget.erro = !/^600/.test(String(codigo));
        atualizarBotao();
        console.warn("[Turnstile] erro " + codigo + "; a Cloudflare vai tentar de novo.");
        return true;
      },
      "before-interactive-callback": function () {
        mudouDesafio(true);
        if (opcoes.aoPedirClique) opcoes.aoPedirClique();
      },
      "after-interactive-callback": function () {
        mudouDesafio(false);
        if (opcoes.aoSairDoClique) opcoes.aoSairDoClique();
      },
      // Desafio exibido e não respondido a tempo: quem estava esperando
      // segue sem token e recebe a recusa da API, em vez de ficar travado.
      "timeout-callback": seguirSemToken,
      "unsupported-callback": desligar,
    });
    return widget;
  }

  function desligar() {
    desligado = true;
    if (lugarDaCaixa) lugarDaCaixa.hidden = true;
    atualizarBotao();
    seguirSemToken();
  }

  function renderizar() {
    if (lugarDaCaixa) {
      caixa = criarWidget(lugarDaCaixa, { appearance: "always", size: "flexible" });
      atualizarBotao();
    } else {
      garantirFlutuante();
    }
  }

  function carregarCloudflare() {
    window.rehabitTurnstileCarregou = renderizar;
    var script = document.createElement("script");
    script.src =
      "https://challenges.cloudflare.com/turnstile/v0/api.js?render=explicit&onload=rehabitTurnstileCarregou";
    script.async = true;
    script.defer = true;
    script.onerror = function () {
      console.warn("[Turnstile] não consegui carregar o script da Cloudflare.");
      desligar();
    };
    document.head.appendChild(script);
  }

  if (desligado) {
    // Sem verificação (aberto como arquivo): nada de espaço vazio no form.
    if (lugarDaCaixa) lugarDaCaixa.hidden = true;
  } else {
    injetarEstilo();
    atualizarBotao(); // já nasce apagado: a caixinha ainda não carregou
    carregarCloudflare();
  }

  window.RehabitTurnstile = {
    cabecalho: function () {
      return token().then(function (valor) {
        var cabecalho = {};
        if (valor) cabecalho[CABECALHO] = valor;
        return cabecalho;
      });
    },
  };
})();
