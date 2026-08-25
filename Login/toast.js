/* Rehabit — notificações via Toastify (substitui alert() nativo)
   API global:
     RehabitToast.sucesso(mensagem)
     RehabitToast.erro(mensagem)
     RehabitToast.info(mensagem)
*/
(function () {
  "use strict";

  function mostrar(mensagem, cor) {
    Toastify({
      text: mensagem,
      duration: 4000,
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
