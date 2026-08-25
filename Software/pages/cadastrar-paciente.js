(function cadastrarPaciente() {
  const form = document.getElementById("cadastrarPacienteForm");
  if (!form) return;

  const sessao = getSessao();
  if (!sessao || sessao.tipo !== "FISIOTERAPEUTA") {
    RehabitToast.erro("Apenas um profissional logado pode cadastrar pacientes.");
    return;
  }

  form.addEventListener("submit", async (e) => {
    e.preventDefault();

    const nome = document.getElementById("nome").value.trim();
    const cpf = document.getElementById("cpf").value.trim();
    const dataNascimento = document.getElementById("nasc").value || null;
    const sexo = document.getElementById("sexo").value || null;
    const situacao = document.getElementById("situacao").value.trim() || null;

    if (!nome || !cpf) {
      RehabitToast.erro("Preencha nome e CPF.");
      return;
    }

    const submitBtn = form.querySelector(".btn-primary");
    submitBtn.disabled = true;
    const textoOriginal = submitBtn.textContent;
    submitBtn.textContent = "Cadastrando...";

    try {
      let foto = null;
      if (arquivoFotoProfissional) {
        const formData = new FormData();
        formData.append("arquivo", arquivoFotoProfissional);
        const uploadResponse = await fetch(`${API_BASE_URL}/uploads`, {
          method: "POST",
          body: formData,
        });
        const uploadDados = await uploadResponse.json().catch(() => ({}));
        if (!uploadResponse.ok) {
          RehabitToast.erro(uploadDados.mensagem || "Não foi possível enviar a foto de perfil.");
          return;
        }
        foto = uploadDados.url;
      }

      const paciente = await apiPost("/pacientes", {
        nome,
        cpf,
        dataNascimento,
        sexo,
        situacao,
        foto,
        idFisioterapeuta: sessao.id,
      });
      RehabitToast.sucesso("Paciente cadastrado com sucesso.");
      setTimeout(() => {
        window.location.href = `${paginaTema("paciente")}?id=${paciente.id}`;
      }, 1200);
    } catch (err) {
      RehabitToast.erro(err.message);
    } finally {
      submitBtn.disabled = false;
      submitBtn.textContent = textoOriginal;
    }
  });
})();
