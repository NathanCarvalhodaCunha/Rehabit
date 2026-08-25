/* Rehabit — adiciona o botão de mostrar/ocultar em todo input[type="password"] */
(function () {
  "use strict";

  var ICONE_OLHO =
    '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8Z"/><circle cx="12" cy="12" r="3"/></svg>';
  var ICONE_OLHO_FECHADO =
    '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M17.94 17.94A10.94 10.94 0 0 1 12 20c-7 0-11-8-11-8a20.42 20.42 0 0 1 5.06-5.94M9.9 4.24A10.6 10.6 0 0 1 12 4c7 0 11 8 11 8a20.32 20.32 0 0 1-3.22 4.5M14.12 14.12a3 3 0 1 1-4.24-4.24"/><line x1="1" y1="1" x2="23" y2="23"/></svg>';

  function aplicar(input) {
    if (input.dataset.rhPwdWrapped) return;
    input.dataset.rhPwdWrapped = "1";

    var wrap = document.createElement("div");
    wrap.className = "rh-pwd-wrap";
    input.parentNode.insertBefore(wrap, input);
    wrap.appendChild(input);

    var btn = document.createElement("button");
    btn.type = "button";
    btn.className = "rh-pwd-toggle";
    btn.setAttribute("aria-label", "Mostrar senha");
    btn.innerHTML = ICONE_OLHO;
    wrap.appendChild(btn);

    btn.addEventListener("click", function () {
      var visivel = input.type === "text";
      input.type = visivel ? "password" : "text";
      btn.innerHTML = visivel ? ICONE_OLHO : ICONE_OLHO_FECHADO;
      btn.setAttribute("aria-label", visivel ? "Mostrar senha" : "Ocultar senha");
    });
  }

  document.querySelectorAll('input[type="password"]').forEach(aplicar);
})();
