// Rehabit — área logada (Software)

const API_BASE_URL = "http://localhost:8080/api";
window.API_BASE_URL = API_BASE_URL;

function getSessao() {
  try {
    return JSON.parse(localStorage.getItem("rehabit_usuario"));
  } catch (err) {
    return null;
  }
}

async function apiGet(caminho) {
  const resposta = await fetch(`${API_BASE_URL}${caminho}`);
  const dados = await resposta.json().catch(() => ({}));
  if (!resposta.ok) {
    throw new Error(dados.mensagem || "Não foi possível carregar os dados.");
  }
  return dados;
}

async function apiPost(caminho, corpo) {
  const resposta = await fetch(`${API_BASE_URL}${caminho}`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(corpo),
  });
  const dados = await resposta.json().catch(() => ({}));
  if (!resposta.ok) {
    throw new Error(dados.mensagem || "Não foi possível salvar os dados.");
  }
  return dados;
}

async function apiPut(caminho, corpo) {
  const resposta = await fetch(`${API_BASE_URL}${caminho}`, {
    method: "PUT",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(corpo),
  });
  const dados = await resposta.json().catch(() => ({}));
  if (!resposta.ok) {
    throw new Error(dados.mensagem || "Não foi possível salvar os dados.");
  }
  return dados;
}

function urlFoto(caminhoFoto) {
  if (!caminhoFoto) return null;
  return API_BASE_URL.replace(/\/api$/, "") + caminhoFoto;
}

// Protege as páginas internas: sem sessão, volta para o login.
(function protegerPagina() {
  if (!getSessao()) {
    window.location.href = "../Login/login.html";
  }
})();

// Mostra a foto de perfil do usuário logado onde o avatar aparece
// (cabeçalho da instituição/profissional, topo mobile, tela de perfil).
(function aplicarAvatarSessao() {
  const sessao = getSessao();
  if (!sessao || !sessao.foto) return;
  const origem = API_BASE_URL.replace(/\/api$/, "");
  const url = origem + sessao.foto;
  document.querySelectorAll(".inst-avatar, .mobile-avatar, .avatar").forEach((el) => {
    el.style.backgroundImage = `url("${url}")`;
    el.style.backgroundSize = "cover";
    el.style.backgroundPosition = "center";
  });
})();

// Cadastrar Profissional — escolha da foto de perfil (mostra prévia
// e guarda o arquivo para enviar junto com o cadastro).
let arquivoFotoProfissional = null;
(function configurarSeletorFoto() {
  const pickers = document.querySelectorAll("[data-avatar-picker]");
  const inputFoto = document.getElementById("pfoto");
  if (!pickers.length || !inputFoto) return;

  pickers.forEach((btn) => {
    btn.addEventListener("click", () => inputFoto.click());
  });

  inputFoto.addEventListener("change", () => {
    const arquivo = inputFoto.files && inputFoto.files[0];
    if (!arquivo) return;
    arquivoFotoProfissional = arquivo;
    const url = URL.createObjectURL(arquivo);
    pickers.forEach((btn) => {
      btn.style.backgroundImage = `url("${url}")`;
      btn.style.backgroundSize = "cover";
      btn.style.backgroundPosition = "center";
    });
  });
})();

// Small interactive stubs — mirrors what buttons would do in the app
document.addEventListener("click", (e) => {
  const target = e.target.closest("[data-action]");
  if (!target) return;
  const action = target.getAttribute("data-action");
  switch (action) {
    case "edit-profile":
      alert("Editar perfil");
      break;
    case "go-list":
      alert("Ir para a lista");
      break;
    case "back":
      history.length > 1 ? history.back() : (window.location.href = "./");
      break;
    case "add-fisio":
      window.location.href = "./cadastrar-profissional.html";
      break;
    case "logout":
      e.preventDefault();
      localStorage.removeItem("rehabit_usuario");
      window.location.href = "../Login/login.html";
      break;
    case "add-patient":
      window.location.href = "./cadastrar-paciente.html";
      break;
    case "add-session":
      window.location.href = "./cadastrar-sessao.html";
      break;
    default:
      break;
  }
});

// Cadastro de profissional — envia os dados para a API vinculando
// o profissional à instituição atualmente logada.
const cadastrarProfissionalForm = document.getElementById("cadastrarProfissionalForm");
if (cadastrarProfissionalForm) {
  cadastrarProfissionalForm.addEventListener("submit", async (e) => {
    e.preventDefault();

    const sessao = getSessao();
    if (!sessao || sessao.tipo !== "CLINICA") {
      alert("Apenas uma instituição logada pode cadastrar profissionais.");
      return;
    }

    const coffito = document.getElementById("pid").value.trim();
    const nome = document.getElementById("pnome").value.trim();
    const telefone = document.getElementById("ptel").value.trim();
    const email = document.getElementById("pmail").value.trim();
    const senha = document.getElementById("psenha").value;
    const especialidade = document.getElementById("pesp").value.trim();
    const localidade = document.getElementById("ploc").value.trim();
    const descricao = document.getElementById("pdesc").value.trim();

    if (!coffito || !nome || !email || !senha) {
      alert("Preencha COFFITO, nome, e-mail e senha.");
      return;
    }
    if (senha.length < 6) {
      alert("A senha deve ter ao menos 6 caracteres.");
      return;
    }

    const submitBtn = cadastrarProfissionalForm.querySelector(".btn-primary");
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
          alert(uploadDados.mensagem || "Não foi possível enviar a foto de perfil.");
          return;
        }
        foto = uploadDados.url;
      }

      const response = await fetch(`${API_BASE_URL}/fisioterapeutas`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          idClinica: sessao.id,
          nome,
          coffito,
          email,
          senha,
          telefone: telefone || null,
          especialidade: especialidade || null,
          localidade: localidade || null,
          descricao: descricao || null,
          foto,
        }),
      });

      const dados = await response.json().catch(() => ({}));

      if (!response.ok) {
        alert(dados.mensagem || "Não foi possível cadastrar o profissional.");
        return;
      }

      alert("Profissional cadastrado com sucesso.");
      window.location.href = "./instituicao.html";
    } catch (err) {
      alert("Não foi possível conectar ao servidor. Verifique sua conexão e tente novamente.");
    } finally {
      submitBtn.disabled = false;
      submitBtn.textContent = textoOriginal;
    }
  });
}
