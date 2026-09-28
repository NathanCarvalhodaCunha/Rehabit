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

  /**
   * Um profissional não atende fora do horário da clínica, então o que ele
   * grava aqui só aperta essa faixa. Sem dizer isso, um horário salvo que
   * cai fora da janela da instituição parece ter sido ignorado.
   */
  function mostrarLimiteDaClinica() {
    const aviso = document.querySelector("[data-limite-clinica]");
    if (!aviso || sessao.tipo !== "FISIOTERAPEUTA") return;
    apiGet("/configuracoes/atendimento")
      .then((janela) => {
        const abertura = apenasHoraMinuto(janela.horaAbertura);
        const fechamento = apenasHoraMinuto(janela.horaFechamento);
        if (!abertura || !fechamento) return;
        aviso.textContent =
          `A agenda aceita marcações das ${abertura} às ${fechamento} — o horário da ` +
          "instituição limita o seu, e o que você definir aqui só pode apertá-lo.";
        aviso.hidden = false;
      })
      .catch(() => {});
  }

  if (formAtendimento) {
    apiGet("/configuracoes")
      .then((cfg) => {
        campoAbertura.value = apenasHoraMinuto(cfg.horaAbertura);
        campoFechamento.value = apenasHoraMinuto(cfg.horaFechamento);
        campoDuracao.value = cfg.duracaoPadraoMin != null ? cfg.duracaoPadraoMin : "";
        campoConflito.checked = !!cfg.avisarConflito;
      })
      .then(mostrarLimiteDaClinica)
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
        mostrarLimiteDaClinica();
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

  ligarExclusaoDeConta();

  /* Só a instituição exclui a própria conta, e isso apaga de verdade tudo que
     é dela — profissionais, pacientes, prontuários. Por isso o diálogo mostra
     o tamanho do que vai sumir e pede a senha antes. */
  function ligarExclusaoDeConta() {
    const painel = document.querySelector('[data-panel="seguranca"]');
    if (!painel || sessao.tipo !== "CLINICA" || typeof RehabitConfirmarExclusao === "undefined") return;

    const subtitulo = document.querySelector('[data-toggle="seguranca"] .settings-sub');
    if (subtitulo) subtitulo.textContent = "Senha de acesso e exclusão da conta";

    const bloco = document.createElement("div");
    bloco.className = "settings-perigo";
    bloco.innerHTML =
      '<p class="t">Excluir conta</p>' +
      '<p class="s">Apaga para sempre a instituição, os profissionais, os pacientes e todo o histórico.</p>' +
      '<button type="button" class="btn-danger">Excluir conta</button>';
    painel.appendChild(bloco);
    bloco.querySelector(".btn-danger").addEventListener("click", abrirExclusaoDeConta);
  }

  function plural(n, singular, varios) {
    return `${n} ${n === 1 ? singular : varios}`;
  }

  async function abrirExclusaoDeConta() {
    let resumo;
    try {
      resumo = await apiGet(`/clinicas/${sessao.id}/exclusao`);
    } catch (err) {
      RehabitToast.erro(err.message);
      return;
    }

    RehabitConfirmarExclusao.abrir({
      titulo: "Excluir conta da instituição",
      corpo:
        '<p class="dialogo-texto">Isso apaga <strong>para sempre</strong>, sem como desfazer:</p>' +
        '<ul class="dialogo-lista">' +
        `<li>${plural(resumo.profissionais, "profissional", "profissionais")}</li>` +
        `<li>${plural(resumo.pacientes, "paciente", "pacientes")} e ${plural(resumo.sessoes, "sessão", "sessões")}</li>` +
        `<li>${plural(resumo.agendamentos, "consulta na agenda", "consultas na agenda")} e ${plural(resumo.dispositivos, "aparelho", "aparelhos")}</li>` +
        "<li>as fotos, notificações e configurações</li>" +
        "</ul>" +
        '<div class="field">' +
        '<label for="dlg-senha">Digite sua senha para confirmar</label>' +
        '<input id="dlg-senha" type="password" autocomplete="current-password" required />' +
        "</div>",
      rotuloConfirmar: "Excluir conta",
      podeConfirmar: (caixa) => !!caixa.querySelector("#dlg-senha").value,
      aoConfirmar: async (caixa) => {
        await apiPost(`/clinicas/${sessao.id}/exclusao`, { senha: caixa.querySelector("#dlg-senha").value });
        // A conta não existe mais: a sessão guardada no navegador também não
        // pode continuar. Outros aparelhos logados caem sozinhos na próxima
        // chamada, porque a API passa a recusar o token deles.
        localStorage.removeItem("rehabit_usuario");
        RehabitToast.sucesso("Conta excluída.");
        setTimeout(() => {
          window.location.href = paginaLogin();
        }, 1500);
      },
    });
  }
})();
