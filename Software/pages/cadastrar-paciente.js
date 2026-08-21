(function cadastrarPaciente() {
  const form = document.getElementById("cadastrarPacienteForm");
  if (!form) return;

  const sessao = getSessao();
  if (!sessao || sessao.tipo !== "FISIOTERAPEUTA") {
    alert("Apenas um profissional logado pode cadastrar pacientes.");
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
      alert("Preencha nome e CPF.");
      return;
    }

    const submitBtn = form.querySelector(".btn-primary");
    submitBtn.disabled = true;
    const textoOriginal = submitBtn.textContent;
    submitBtn.textContent = "Cadastrando...";

    try {
      const paciente = await apiPost("/pacientes", {
        nome,
        cpf,
        dataNascimento,
        sexo,
        situacao,
        idFisioterapeuta: sessao.id,
      });
      alert("Paciente cadastrado com sucesso.");
      window.location.href = `${paginaTema("paciente")}?id=${paciente.id}`;
    } catch (err) {
      alert(err.message);
    } finally {
      submitBtn.disabled = false;
      submitBtn.textContent = textoOriginal;
    }
  });
})();
