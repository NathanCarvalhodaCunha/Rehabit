(function carregarDispositivo() {
  const temCartaoDispositivo = document.querySelector(".device-card") || document.querySelector(".device-card-mobile");
  if (!temCartaoDispositivo) return;

  const sessao = getSessao();
  if (!sessao) return;

  function formatarData(dataIso) {
    const [ano, mes, dia] = dataIso.split("-");
    return `${dia}/${mes}/${ano}`;
  }

  function definirValorPorRotulo(rotulo, valor) {
    document.querySelectorAll(".info-row").forEach((row) => {
      const k = row.querySelector(".k");
      if (k && k.textContent.trim().toLowerCase().startsWith(rotulo)) {
        const v = row.querySelector(".v");
        if (v && !v.classList.contains("battery")) v.textContent = valor;
      }
    });
  }

  function aplicar(goniometro) {
    const bateria = goniometro.bateria != null ? goniometro.bateria : 0;
    const sincronizacao =
      goniometro.dataSincronizacao && goniometro.horaSincronizacao
        ? `${formatarData(goniometro.dataSincronizacao)} ${goniometro.horaSincronizacao}`
        : "-";
    document.querySelectorAll(".battery-num").forEach((el) => (el.textContent = `${bateria}%`));
    document.querySelectorAll(".battery-fill").forEach((el) => (el.style.width = `${bateria}%`));
    definirValorPorRotulo("última sincronização", sincronizacao);
    document.querySelectorAll(".device-card .device-name").forEach((el) => (el.textContent = "Dispositivo conectado"));
    document.querySelectorAll(".device-card-mobile .status-ok").forEach((el) => (el.textContent = "Conectado"));
  }

  function semDados() {
    document.querySelectorAll(".device-card .device-name").forEach(
      (el) => (el.textContent = "Nenhum dispositivo sincronizado ainda")
    );
    document.querySelectorAll(".device-card-mobile .status-ok").forEach((el) => (el.textContent = "Desconectado"));
    document.querySelectorAll(".battery-num").forEach((el) => (el.textContent = "-"));
    document.querySelectorAll(".battery-fill").forEach((el) => (el.style.width = "0%"));
    definirValorPorRotulo("última sincronização", "-");
  }

  const idClinicaPromise =
    sessao.tipo === "CLINICA"
      ? Promise.resolve(sessao.id)
      : apiGet(`/fisioterapeutas/${sessao.id}`).then((f) => f.idClinica);

  idClinicaPromise.then((idClinica) => {
    apiGet(`/goniometro?idClinica=${idClinica}`).then(aplicar).catch(semDados);

    document.querySelectorAll('[data-action="sync"]').forEach((btn) => {
      btn.addEventListener("click", async () => {
        btn.disabled = true;
        try {
          const goniometro = await apiPost("/goniometro/sincronizar", { idClinica });
          aplicar(goniometro);
        } catch (err) {
          RehabitToast.erro(err.message);
        } finally {
          btn.disabled = false;
        }
      });
    });
  }).catch((err) => {
    semDados();
    RehabitToast.erro(err.message);
  });
})();
