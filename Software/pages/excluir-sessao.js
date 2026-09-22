/* Rehabit — exclusão de uma sessão já registrada.

   Sessão lançada errada (paciente trocado, medição do goniômetro que saiu
   absurda) ficava para sempre no histórico e puxava a evolução para baixo,
   porque não havia como apagá-la. O mesmo caminho serve à tabela de sessões
   (desktop) e à linha do tempo (celular), então mora aqui, num lugar só.

   Uso:
     RehabitSessao.excluir(idPaciente, idSessao, botao) */
window.RehabitSessao = (function () {
  "use strict";

  function excluir(idPaciente, idSessao, botao) {
    if (!idPaciente || !idSessao) return;

    var confirmado = window.confirm(
      "Excluir esta sessão? O registro e a medição dela saem do histórico do paciente, e isso não pode ser desfeito."
    );
    if (!confirmado) return;

    if (botao) botao.disabled = true;
    apiDelete("/pacientes/" + idPaciente + "/sessoes/" + idSessao)
      .then(function () {
        RehabitToast.sucesso("Sessão excluída.");
        // A tela monta gráficos, indicadores, tabela e linha do tempo a
        // partir da mesma lista, em scripts diferentes: recarregar é o que
        // mantém tudo contando a mesma história depois da exclusão.
        setTimeout(function () {
          window.location.reload();
        }, 900);
      })
      .catch(function (err) {
        RehabitToast.erro(err.message);
        if (botao) botao.disabled = false;
      });
  }

  return { excluir: excluir };
})();
