/* Rehabit — notificações via Toastify (substitui alert() nativo)
   API global:
     RehabitToast.sucesso(mensagem)
     RehabitToast.erro(mensagem)
     RehabitToast.info(mensagem)

   O Toastify vem de CDN, e CDN falha: rede de clínica bloqueando domínio,
   internet caindo no meio do uso. Antes, quando isso acontecia, a primeira
   chamada estourava "Toastify is not defined" e derrubava a ação inteira —
   o cadastro chegava a criar a conta no servidor e não redirecionava,
   porque o aviso de sucesso vinha antes do redirecionamento.

   Agora a biblioteca é opcional em duas frentes: sem o script, o aviso é
   montado aqui mesmo; sem a folha de estilo dela (que é um segundo pedido
   ao CDN, e pode falhar sozinho), entra um estilo local equivalente.
*/
(function () {
  "use strict";

  var DURACAO_MS = 4000;
  var estiloLocalInjetado = false;
  var cssDoToastify = null; // null = ainda não conferido

  /* ---- Aviso próprio, usado quando o Toastify não está disponível ---- */

  function injetarEstiloLocal() {
    if (estiloLocalInjetado) return;
    estiloLocalInjetado = true;

    var estilo = document.createElement("style");
    estilo.textContent = [
      ".rh-toasts{position:fixed;top:15px;right:15px;z-index:10000;display:flex;",
      "flex-direction:column;gap:10px;align-items:flex-end;pointer-events:none;",
      "max-width:min(420px,calc(100vw - 30px))}",
      ".rh-toast{position:relative;display:flex;align-items:center;gap:12px;",
      "padding:12px 20px;border-radius:10px;color:#fff;font-weight:600;",
      "font-size:15px;line-height:1.35;font-family:inherit;text-align:left;",
      "box-shadow:0 8px 24px rgba(0,0,0,.18);pointer-events:auto;",
      "opacity:0;transform:translateY(-8px);transition:opacity .3s,transform .3s;",
      "word-break:break-word}",
      ".rh-toast.is-visivel{opacity:1;transform:none}",
      ".rh-toast__fechar{flex:0 0 auto;background:none;border:none;color:#fff;",
      "font-size:20px;line-height:1;padding:0;cursor:pointer;opacity:.85;",
      "font-family:inherit}",
      ".rh-toast__fechar:hover{opacity:1}",
    ].join("");
    (document.head || document.documentElement).appendChild(estilo);
  }

  function mostrarLocal(mensagem, cor) {
    injetarEstiloLocal();

    var raiz = document.body || document.documentElement;
    var caixa = document.querySelector(".rh-toasts");
    if (!caixa) {
      caixa = document.createElement("div");
      caixa.className = "rh-toasts";
      raiz.appendChild(caixa);
    }

    var aviso = document.createElement("div");
    aviso.className = "rh-toast";
    aviso.setAttribute("role", "status");
    aviso.style.background = cor;

    var texto = document.createElement("span");
    texto.textContent = mensagem;
    aviso.appendChild(texto);

    var fechar = document.createElement("button");
    fechar.type = "button";
    fechar.className = "rh-toast__fechar";
    fechar.setAttribute("aria-label", "Fechar");
    fechar.textContent = "×";
    aviso.appendChild(fechar);

    caixa.appendChild(aviso);
    requestAnimationFrame(function () {
      aviso.classList.add("is-visivel");
    });

    var relogio = setTimeout(remover, DURACAO_MS);
    fechar.addEventListener("click", function () {
      clearTimeout(relogio);
      remover();
    });

    function remover() {
      aviso.classList.remove("is-visivel");
      setTimeout(function () {
        aviso.remove();
        if (caixa && !caixa.childElementCount) caixa.remove();
      }, 300);
    }
  }

  /* ---- Escolha entre o Toastify e o aviso próprio ---- */

  /**
   * O CSS do Toastify é quem posiciona o aviso na tela. Sem ele, o toast
   * cai no meio do conteúdo em vez de flutuar no canto — visualmente
   * quebrado. Confere uma vez, criando um elemento de sonda com a classe
   * da biblioteca e vendo se alguma regra chegou a aplicar.
   */
  function temCssDoToastify() {
    if (cssDoToastify !== null) return cssDoToastify;

    var sonda = document.createElement("div");
    sonda.className = "toastify";
    (document.body || document.documentElement).appendChild(sonda);
    cssDoToastify = window.getComputedStyle(sonda).position === "fixed";
    sonda.remove();
    return cssDoToastify;
  }

  function mostrar(mensagem, cor) {
    if (typeof Toastify === "function" && temCssDoToastify()) {
      Toastify({
        text: mensagem,
        duration: DURACAO_MS,
        close: true,
        gravity: "top",
        position: "right",
        stopOnFocus: true,
        style: {
          background: cor,
          borderRadius: "10px",
          fontWeight: "600",
          boxShadow: "0 8px 24px rgba(0,0,0,.18)",
        },
      }).showToast();
      return;
    }
    mostrarLocal(mensagem, cor);
  }

  window.RehabitToast = {
    sucesso: function (mensagem) {
      mostrar(mensagem, "#16A34A");
    },
    erro: function (mensagem) {
      mostrar(mensagem, "#DC2626");
    },
    info: function (mensagem) {
      mostrar(mensagem, "#1565D8");
    },
  };
})();
