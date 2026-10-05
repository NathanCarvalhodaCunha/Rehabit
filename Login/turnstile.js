/* Rehabit — verificação anti-robô (Cloudflare Turnstile)
   API global:
     RehabitTurnstile.cabecalho()  -> Promise com { "X-Turnstile-Token": token },
                                      ou {} quando não há verificação nesta página

   Uso, antes de chamar o login, o envio do código de cadastro ou o e-mail de
   recuperação de senha (as rotas em que a API confere o token):

     const antiRobo = await RehabitTurnstile.cabecalho();
     fetch(url, { headers: { 'Content-Type': 'application/json', ...antiRobo }, ... });

   O widget começa a trabalhar assim que a página abre, para o token já estar
   pronto quando a pessoa clicar. Ele é invisível: só aparece, num cartão no
   pé da tela, quando a Cloudflare desconfia e pede um clique. O cartão fica
   por cima de tudo, porque a pergunta pode chegar com o pop-up do cadastro
   aberto. Peça o token antes de mostrar o loader: com ele na tela, a espera
   pelo clique viraria o aviso de "servidor acordando".

   Cada token vale uma vez só. Depois de entregar um, o widget já pede o
   próximo — é o que deixa o "Enviar de novo" funcionar.

   Quando a verificação não roda (script da Cloudflare bloqueado, rede caída,
   navegador sem suporte), a chamada segue sem token e quem decide é a API:
   com a TURNSTILE_SECRET_KEY definida ela recusa com uma mensagem própria.
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
  // antes de seguir sem ele. Enquanto o cartão pede o clique não conta: aí
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
  var widgetId = null;
  var tokenPronto = null;
  var desligado = !chave;
  var pedidos = []; // quem chamou cabecalho() e espera o próximo token
  var cartao = null;

  /* ---- Token ---- */

  function consumir() {
    var token = tokenPronto;
    tokenPronto = null;
    window.turnstile.reset(widgetId);
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

  function token() {
    if (desligado) return Promise.resolve(null);
    if (tokenPronto) return Promise.resolve(consumir());
    return new Promise(function (resolver) {
      var pedido = { resolver: resolver, relogio: null };
      pedidos.push(pedido);
      if (!cartaoVisivel()) aguardarAteDesistir(pedido);
    });
  }

  function chegouToken(novo) {
    tokenPronto = novo;
    if (pedidos.length) atender(pedidos[0], consumir());
  }

  /* ---- Cartão do desafio ---- */

  function injetarEstilo() {
    var estilo = document.createElement("style");
    estilo.textContent =
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

  // Fica no DOM desde o início, só transparente e fora da tela: escondido
  // com display:none o widget não consegue rodar o desafio.
  function criarCartao() {
    injetarEstilo();
    cartao = document.createElement("div");
    cartao.className = "rh-turnstile";
    cartao.setAttribute("role", "region");
    cartao.setAttribute("aria-label", "Verificação anti-robô");
    cartao.innerHTML = '<p aria-live="polite"></p><div></div>';
    document.body.appendChild(cartao);
    return cartao.lastChild;
  }

  function cartaoVisivel() {
    return !!cartao && cartao.classList.contains("is-visivel");
  }

  function mostrarCartao() {
    cartao.firstChild.textContent = "Confirme que você não é um robô para continuar.";
    cartao.classList.add("is-visivel");
    // A pessoa está respondendo: ninguém desiste por tempo enquanto isso.
    pedidos.forEach(function (pedido) {
      clearTimeout(pedido.relogio);
    });
  }

  function esconderCartao() {
    cartao.classList.remove("is-visivel");
    cartao.firstChild.textContent = "";
    pedidos.forEach(aguardarAteDesistir);
  }

  /* ---- Widget ---- */

  function renderizar() {
    widgetId = window.turnstile.render(criarCartao(), {
      sitekey: chave,
      theme: document.body.classList.contains("dark") ? "dark" : "light",
      language: "pt-br",
      appearance: "interaction-only",
      callback: chegouToken,
      "expired-callback": function () {
        tokenPronto = null; // a Cloudflare renova sozinha e chama o callback de novo
      },
      // Sem este callback o erro vira exceção na página. O true diz que já
      // foi registrado aqui; a Cloudflare tenta de novo sozinha.
      "error-callback": function (codigo) {
        tokenPronto = null;
        console.warn("[Turnstile] erro " + codigo + "; a Cloudflare vai tentar de novo.");
        return true;
      },
      "before-interactive-callback": mostrarCartao,
      "after-interactive-callback": esconderCartao,
      // Desafio exibido e não respondido a tempo: quem estava esperando
      // segue sem token e recebe a recusa da API, em vez de ficar travado.
      "timeout-callback": seguirSemToken,
      "unsupported-callback": function () {
        desligado = true;
        seguirSemToken();
      },
    });
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
      desligado = true;
      seguirSemToken();
    };
    document.head.appendChild(script);
  }

  if (!desligado) carregarCloudflare();

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
