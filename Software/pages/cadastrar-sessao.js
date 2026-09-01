(function carregarCadastrarSessao() {
  const header = document.querySelector(".patient-header");
  if (!header) return;

  const sessao = getSessao();
  if (!sessao || sessao.tipo !== "FISIOTERAPEUTA") return;

  /* Qual captura preencheu a amplitude. Fica no escopo da tela porque quem
     escreve é o painel do goniômetro e quem lê é o envio do formulário: vai
     junto no POST para o servidor anexar a curva daquele movimento, e só
     daquele. */
  let capturaUsada = null;

  /* Goniômetro ao vivo dentro do formulário: o profissional não precisa sair
     para a tela Dispositivo, anotar o número e voltar. O painel só aparece
     depois que o canal abre — sem aparelho na clínica, o formulário fica
     exatamente como era. */
  (function ligarGoniometro() {
    const painel = document.querySelector("[data-gonio-inline]");
    const campoAmplitude = document.getElementById("s-amp");
    if (!painel || !campoAmplitude || typeof RehabitGoniometro === "undefined") return;

    const selo = painel.querySelector(".conn-badge");
    const seloTexto = painel.querySelector(".conn-text");
    const valor = painel.querySelector("[data-gonio-angulo]");
    const campoMin = painel.querySelector("[data-gonio-min]");
    const campoMax = painel.querySelector("[data-gonio-max]");
    const campoAmp = painel.querySelector("[data-gonio-amp]");
    const dica = painel.querySelector("[data-gonio-dica]");
    const botaoUsar = painel.querySelector("[data-gonio-usar]");
    const botaoCaptura = painel.querySelector("[data-gonio-captura]");

    let anguloAtual = null;
    let capturando = false;

    function grau(v) {
      return v == null ? "–" : `${Number(v).toFixed(1).replace(".", ",")}°`;
    }

    function preencher(v) {
      // O campo é um <input type="number">: ponto decimal, sempre.
      campoAmplitude.value = Number(v).toFixed(1);
      campoAmplitude.dispatchEvent(new Event("input", { bubbles: true }));
    }

    function desenhar(estado) {
      painel.hidden = false;
      anguloAtual = estado.angulo;
      valor.textContent = estado.angulo != null ? Number(estado.angulo).toFixed(1).replace(".", ",") : "–";

      const captura = estado.captura;
      capturando = !!(captura && captura.ativa);

      if (captura && (captura.ativa || captura.amostras)) {
        campoMin.textContent = grau(captura.minimo);
        campoMax.textContent = grau(captura.maximo);
        campoAmp.textContent = grau(captura.amplitude);
      } else {
        campoMin.textContent = "–";
        campoMax.textContent = "–";
        campoAmp.textContent = "–";
      }

      if (capturando) {
        selo.dataset.conn = "capturando";
        seloTexto.textContent = "Gravando";
        dica.textContent = "Peça o movimento completo e clique em Parar para trazer a amplitude.";
      } else if (estado.conectado) {
        selo.dataset.conn = "conectado";
        seloTexto.textContent = "Conectado";
        dica.textContent = "Use o ângulo atual ou grave o movimento para calcular a amplitude.";
      } else {
        selo.dataset.conn = estado.ultimoContato ? "desconectado" : "aguardando";
        seloTexto.textContent = estado.ultimoContato ? "Desconectado" : "Aguardando o aparelho";
        dica.textContent = "Ligue o goniômetro para preencher a amplitude automaticamente.";
      }

      botaoCaptura.textContent = capturando ? "Parar gravação" : "Gravar movimento";
      botaoCaptura.disabled = !estado.conectado && !capturando;
      botaoUsar.disabled = estado.angulo == null;
    }

    botaoUsar.addEventListener("click", () => {
      if (anguloAtual == null) return;
      // Um ângulo solto não tem curva: é um instante, não um movimento.
      capturaUsada = null;
      preencher(anguloAtual);
      RehabitToast.sucesso(`Amplitude preenchida com ${grau(anguloAtual)}.`);
    });

    botaoCaptura.addEventListener("click", async () => {
      botaoCaptura.disabled = true;
      try {
        if (capturando) {
          const estado = await RehabitGoniometro.pararCaptura();
          const amplitude = estado && estado.captura ? estado.captura.amplitude : null;
          if (amplitude != null) {
            capturaUsada = estado.captura.iniciadaEm || null;
            preencher(amplitude);
            RehabitToast.sucesso(`Amplitude de ${grau(amplitude)} preenchida a partir da gravação.`);
          } else {
            RehabitToast.info("A gravação terminou sem leituras suficientes.");
          }
        } else {
          await RehabitGoniometro.iniciarCaptura();
          RehabitToast.info("Gravando — peça o movimento completo da articulação.");
        }
      } catch (err) {
        RehabitToast.erro(err.message);
      } finally {
        botaoCaptura.disabled = false;
      }
    });

    /* Editar o número na mão desfaz o vínculo com a captura: a curva mostraria
       um movimento que não bate com a amplitude registrada. */
    campoAmplitude.addEventListener("input", (e) => {
      if (e.isTrusted) capturaUsada = null;
    });

    RehabitGoniometro.conectar(desenhar).catch(() => {
      // Clínica sem goniômetro cadastrado ou API fora: o painel simplesmente
      // não aparece e o campo de amplitude segue manual, como antes.
    });
  })();

  const params = new URLSearchParams(window.location.search);
  const idPaciente = params.get("id");
  if (!idPaciente) {
    header.querySelector("h1").textContent = "Paciente não informado.";
    return;
  }

  document.querySelectorAll(".tabs a.tab").forEach((a) => {
    a.href = `${paginaTema("paciente")}?id=${idPaciente}`;
  });

  function formatarDataLonga(dataIso) {
    const [ano, mes, dia] = dataIso.split("-");
    return `${dia}/${mes}/${ano}`;
  }
  function formatarDataCurta(dataIso) {
    const [, mes, dia] = dataIso.split("-");
    return `${dia}/${mes}`;
  }

  function carregarPacienteEHistorico() {
    return Promise.all([apiGet(`/pacientes/${idPaciente}`), apiGet(`/pacientes/${idPaciente}/sessoes`)]).then(
      ([paciente, sessoes]) => {
        const idadeTexto = paciente.idade != null ? `${paciente.idade} anos` : "Idade não informada";
        const sexoTexto = paciente.sexo || "Não informado";
        const situacaoTexto = paciente.situacao || "Sem situação registrada";
        const inicioTexto = paciente.dataInicioTratamento ? formatarDataLonga(paciente.dataInicioTratamento) : "-";
        const fisioTexto = paciente.nomeFisioterapeuta || "-";

        // O cabeçalho desta tela é o mesmo da ficha do paciente, mas a foto
        // nunca era preenchida aqui: ficava sempre o círculo cinza vazio.
        const fotoPaciente = urlFoto(paciente.foto);
        const avatarEl = header.querySelector(".avatar-lg");
        if (fotoPaciente && avatarEl) {
          avatarEl.style.backgroundImage = `url("${fotoPaciente}")`;
          avatarEl.style.backgroundSize = "cover";
          avatarEl.style.backgroundPosition = "center";
        }

        header.querySelector("h1").textContent = paciente.nome;
        header.querySelector(".patient-meta.desktop-only").innerHTML =
          `${idadeTexto} – ${sexoTexto} – ${situacaoTexto}<br/>` +
          `Início do tratamento: <strong>${inicioTexto}</strong> – Fisioterapia <strong>${fisioTexto}</strong>`;
        header.querySelector(".patient-meta.mobile-only").innerHTML = `${idadeTexto} – ${sexoTexto}<br/>${situacaoTexto}`;

        const infoValores = document.querySelectorAll(".info-strip .v");
        if (infoValores[0]) infoValores[0].textContent = inicioTexto;
        if (infoValores[1]) infoValores[1].textContent = fisioTexto;

        const tbody = document.querySelector(".sessions-table tbody");
        if (!tbody) return;
        if (sessoes.length === 0) {
          tbody.innerHTML = '<tr><td colspan="4">Ainda não há sessões registradas.</td></tr>';
          return;
        }
        tbody.innerHTML = sessoes
          .map((s, i) => {
            const anterior = sessoes[i + 1];
            let avancoTexto = "0°";
            let avancoClasse = "";
            if (anterior && anterior.amplitudeMedia != null && s.amplitudeMedia != null) {
              const diferenca = Number(s.amplitudeMedia) - Number(anterior.amplitudeMedia);
              avancoTexto = `${diferenca >= 0 ? "+" : ""}${diferenca.toFixed(0)}°`;
              if (diferenca > 0) avancoClasse = "pos";
              else if (diferenca < 0) avancoClasse = "neg";
            }
            return `
              <tr>
                <td><span class="desktop-only">${formatarDataLonga(s.data)}</span><span class="mobile-only">${formatarDataCurta(s.data)}</span></td>
                <td>${s.duracao != null ? s.duracao + " min" : "-"}</td>
                <td>${s.amplitudeMedia != null ? s.amplitudeMedia + "°" : "-"}</td>
                <td class="${avancoClasse}">${avancoTexto}</td>
              </tr>`;
          })
          .join("");
        RehabitAnim.staggerList(tbody);
      }
    );
  }

  carregarPacienteEHistorico().catch((err) => {
    header.querySelector("h1").textContent = err.message;
  });

  const form = document.getElementById("cadastrarSessaoForm");
  if (!form) return;

  // Sessão é registro do que já foi atendido, então a data não pode ser
  // futura — o campo trava no dia de hoje e o envio confere de novo.
  const campoData = document.getElementById("s-data");
  const hojeIso = (() => {
    const agora = new Date();
    return (
      agora.getFullYear() +
      "-" +
      String(agora.getMonth() + 1).padStart(2, "0") +
      "-" +
      String(agora.getDate()).padStart(2, "0")
    );
  })();
  if (campoData) {
    campoData.max = hojeIso;
    if (!campoData.value) campoData.value = hojeIso;
  }

  // Espelha o valor do controle deslizante ao lado dele.
  (function ligarEscalaDeDor() {
    const controle = document.getElementById("s-dor");
    const saida = document.querySelector("[data-dor-valor]");
    if (!controle || !saida) return;
    const atualizar = () => {
      saida.textContent = controle.value;
      saida.dataset.nivel = controle.value >= 7 ? "alto" : controle.value >= 4 ? "medio" : "baixo";
    };
    controle.addEventListener("input", atualizar);
    atualizar();
  })();
  form.addEventListener("submit", async (e) => {
    e.preventDefault();

    const data = document.getElementById("s-data").value;
    const duracao = document.getElementById("s-dur").value;
    const amplitude = document.getElementById("s-amp").value;
    const campoObs = document.getElementById("s-obs");
    const observacoes = campoObs ? campoObs.value.trim() : "";
    const campoDor = document.getElementById("s-dor");

    if (!data || !duracao) {
      RehabitToast.erro("Preencha ao menos a data e a duração.");
      return;
    }
    if (data > hojeIso) {
      RehabitToast.erro("A sessão não pode ter uma data futura. Use a Agenda para marcar o que ainda vai acontecer.");
      return;
    }

    const submitBtn = form.querySelector(".btn-primary");
    submitBtn.disabled = true;
    const textoOriginal = submitBtn.textContent;
    submitBtn.textContent = "Salvando...";

    try {
      await apiPost(`/pacientes/${idPaciente}/sessoes`, {
        data,
        duracao: Number(duracao),
        amplitudeMedia: amplitude ? Number(amplitude) : null,
        observacoes: observacoes || null,
        dor: campoDor ? Number(campoDor.value) : null,
        capturaIniciadaEm: capturaUsada,
        idFisioterapeuta: sessao.id,
      });
      capturaUsada = null;
      form.reset();
      if (campoData) campoData.value = hojeIso;
      await carregarPacienteEHistorico();
      RehabitToast.sucesso("Sessão cadastrada com sucesso.");
    } catch (err) {
      RehabitToast.erro(err.message);
    } finally {
      submitBtn.disabled = false;
      submitBtn.textContent = textoOriginal;
    }
  });
})();
