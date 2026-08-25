/* Rehabit — tela de Configurações: preferências de atendimento e troca de senha. */
(function carregarConfiguracoes() {
  const formAtendimento = document.getElementById("configAtendimentoForm");
  const formSenha = document.getElementById("trocarSenhaForm");
  if (!formAtendimento && !formSenha) return;

  const sessao = getSessao();
  if (!sessao) return;

  const campoAbertura = document.getElementById("cfg-abertura");
  const campoFechamento = document.getElementById("cfg-fechamento");
  const campoDuracao = document.getElementById("cfg-duracao");
  const campoConflito = document.getElementById("cfg-conflito");

  function apenasHoraMinuto(valor) {
    return valor ? valor.slice(0, 5) : "";
  }

  if (formAtendimento) {
    apiGet("/configuracoes")
      .then((cfg) => {
        campoAbertura.value = apenasHoraMinuto(cfg.horaAbertura);
        campoFechamento.value = apenasHoraMinuto(cfg.horaFechamento);
        campoDuracao.value = cfg.duracaoPadraoMin != null ? cfg.duracaoPadraoMin : "";
        campoConflito.checked = !!cfg.avisarConflito;
      })
      .catch((err) => RehabitToast.erro(err.message));

    formAtendimento.addEventListener("submit", async (e) => {
      e.preventDefault();

      const botao = formAtendimento.querySelector(".btn-primary");
      botao.disabled = true;
      const textoOriginal = botao.textContent;
      botao.textContent = "Salvando...";

      try {
        await apiPut("/configuracoes", {
          horaAbertura: campoAbertura.value || null,
          horaFechamento: campoFechamento.value || null,
          duracaoPadraoMin: campoDuracao.value ? Number(campoDuracao.value) : null,
          avisarConflito: campoConflito.checked,
        });
        RehabitToast.sucesso("Configurações salvas.");
      } catch (err) {
        RehabitToast.erro(err.message);
      } finally {
        botao.disabled = false;
        botao.textContent = textoOriginal;
      }
    });
  }

  if (formSenha) {
    formSenha.addEventListener("submit", async (e) => {
      e.preventDefault();

      const atual = document.getElementById("cfg-senha-atual").value;
      const nova = document.getElementById("cfg-senha-nova").value;

      if (!atual || !nova) {
        RehabitToast.erro("Preencha a senha atual e a nova senha.");
        return;
      }
      if (nova.length < 6) {
        RehabitToast.erro("A nova senha deve ter ao menos 6 caracteres.");
        return;
      }

      const botao = formSenha.querySelector(".btn-primary");
      botao.disabled = true;
      const textoOriginal = botao.textContent;
      botao.textContent = "Alterando...";

      try {
        await apiPut("/configuracoes/senha", { senhaAtual: atual, novaSenha: nova });
        RehabitToast.sucesso("Senha alterada com sucesso.");
        formSenha.reset();
      } catch (err) {
        RehabitToast.erro(err.message);
      } finally {
        botao.disabled = false;
        botao.textContent = textoOriginal;
      }
    });
  }
})();
